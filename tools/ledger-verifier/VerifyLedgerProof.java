import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies an OpenFactstore ledger proof without trusting the server (#188).
 *
 * This file shares no code with Factstore and uses only the JDK. Run it with any JDK 17+:
 *
 *   java VerifyLedgerProof.java proof.json keys.json [expected-root-hash]
 *
 * proof.json is the response of GET /api/v1/queries/ledger/proof/{fact_id}. keys.json is the list
 * of event-signing public keys (GET /api/v1/queries/event-signing-keys), ideally obtained out of
 * band, from Vault or your own copy. expected-root-hash is the checkpoint root as published outside
 * Factstore; pass it so the proof is tied to a root the server could not have changed afterwards.
 *
 * It checks that:
 *   1. the record's canonical bytes hash to its record_hash, and name this fact and ledger index;
 *   2. the record's signature verifies under the named public key;
 *   3. the record_hash is included, at leaf_index of a tree of tree_size, under root_hash
 *      (RFC 9162 inclusion proof);
 *   4. the checkpoint statement names that root and tree size, and its signature verifies;
 *   5. if given, the root equals the externally published root.
 *
 * Prints VERIFIED and exits 0, or prints FAILED with the reason and exits 1.
 */
public class VerifyLedgerProof {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("usage: java VerifyLedgerProof.java proof.json keys.json [expected-root-hash]");
            System.exit(2);
        }
        try {
            Map<String, Object> proof = obj(new Json(Files.readString(Path.of(args[0]))).parse());
            List<Object> keys = list(new Json(Files.readString(Path.of(args[1]))).parse());
            verify(proof, keys, args.length > 2 ? args[2] : null);
            System.out.println("VERIFIED fact " + proof.get("fact_id") + " at ledger index " + num(proof.get("ledger_index")));
        } catch (Failure f) {
            System.out.println("FAILED: " + f.getMessage());
            System.exit(1);
        }
    }

    static void verify(Map<String, Object> proof, List<Object> keys, String expectedRoot) throws Exception {
        String factId = str(proof.get("fact_id"));
        long ledgerIndex = num(proof.get("ledger_index"));
        Map<String, Object> record = obj(proof.get("record"));
        Map<String, Object> merkle = obj(proof.get("merkle_proof"));
        Map<String, Object> checkpoint = obj(proof.get("checkpoint"));

        // 1. The record's bytes, and what they say.
        byte[] canonical = Base64.getDecoder().decode(str(record.get("canonical")));
        String recordHash = str(record.get("record_hash"));
        require(hex(sha256(canonical)).equals(recordHash), "the record's bytes do not hash to its record_hash");
        String text = new String(canonical, StandardCharsets.UTF_8);
        require(text.contains("\"aggregate_id\":\"" + factId + "\""), "the record is not about fact " + factId);
        require(text.contains("\"sequence_number\":" + ledgerIndex + "}"), "the record is not at ledger index " + ledgerIndex);

        // 2. The record's signature.
        require(signatureVerifies(keys, str(record.get("signing_key_id")), canonical, str(record.get("signature"))),
            "the record's signature does not verify under " + record.get("signing_key_id"));

        // 3. Inclusion in the checkpoint's tree.
        String root = str(merkle.get("root_hash"));
        List<byte[]> siblings = new ArrayList<>();
        for (Object s : list(merkle.get("siblings"))) siblings.add(HexFormat.of().parseHex(str(s)));
        int treeSize = (int) num(merkle.get("tree_size"));
        require(included(HexFormat.of().parseHex(recordHash), (int) num(merkle.get("leaf_index")), treeSize, siblings,
            HexFormat.of().parseHex(root)), "the record is not included under root " + root);

        // 4. The signed checkpoint.
        String statement = str(checkpoint.get("statement"));
        Map<String, Object> claims = obj(new Json(statement).parse());
        require(root.equals(claims.get("root_hash")), "the checkpoint statement names a different root");
        require(num(claims.get("leaf_count")) == treeSize, "the checkpoint statement names a different tree size");
        require(signatureVerifies(keys, str(checkpoint.get("signing_key_id")), statement.getBytes(StandardCharsets.UTF_8),
            str(checkpoint.get("signature"))), "the checkpoint signature does not verify");

        // 5. The root an auditor holds independently.
        if (expectedRoot != null) require(root.equalsIgnoreCase(expectedRoot), "the root differs from the published root");
    }

    /** RFC 9162 section 2.1.3.2. */
    static boolean included(byte[] leaf, int index, int size, List<byte[]> path, byte[] root) throws Exception {
        if (index < 0 || index >= size) return false;
        long fn = index, sn = size - 1L;
        byte[] r = sha256(new byte[] {0}, leaf);
        for (byte[] p : path) {
            if (sn == 0) return false;
            if ((fn & 1) == 1 || fn == sn) {
                r = sha256(new byte[] {1}, p, r);
                while ((fn & 1) == 0 && fn != 0) { fn >>= 1; sn >>= 1; }
            } else {
                r = sha256(new byte[] {1}, r, p);
            }
            fn >>= 1;
            sn >>= 1;
        }
        return sn == 0 && MessageDigest.isEqual(r, root);
    }

    static boolean signatureVerifies(List<Object> keys, String keyId, byte[] data, String signature) {
        for (Object k : keys) {
            Map<String, Object> key = obj(k);
            if (!keyId.equals(key.get("keyId"))) continue;
            try {
                String algorithm = str(key.get("algorithm"));
                PublicKey publicKey = KeyFactory.getInstance(algorithm)
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(str(key.get("publicKey")))));
                Signature verifier = Signature.getInstance(algorithm);
                verifier.initVerify(publicKey);
                verifier.update(data);
                return verifier.verify(Base64.getDecoder().decode(signature));
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    static byte[] sha256(byte[]... parts) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (byte[] part : parts) digest.update(part);
        return digest.digest();
    }

    static String hex(byte[] bytes) { return HexFormat.of().formatHex(bytes); }

    static void require(boolean ok, String reason) { if (!ok) throw new Failure(reason); }

    static class Failure extends RuntimeException { Failure(String m) { super(m); } }

    @SuppressWarnings("unchecked")
    static Map<String, Object> obj(Object o) {
        if (!(o instanceof Map)) throw new Failure("malformed proof: expected an object");
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Object o) {
        if (!(o instanceof List)) throw new Failure("malformed proof: expected a list");
        return (List<Object>) o;
    }

    static String str(Object o) {
        if (!(o instanceof String)) throw new Failure("malformed proof: expected a string");
        return (String) o;
    }

    static long num(Object o) {
        if (!(o instanceof Number)) throw new Failure("malformed proof: expected a number");
        return ((Number) o).longValue();
    }

    /** A minimal JSON reader: objects, arrays, strings, integers, booleans and null. */
    static class Json {
        private final String s;
        private int i;

        Json(String s) { this.s = s; }

        Object parse() {
            Object v = value();
            ws();
            if (i != s.length()) throw new Failure("malformed JSON");
            return v;
        }

        private Object value() {
            ws();
            if (i >= s.length()) throw new Failure("malformed JSON");
            char c = s.charAt(i);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (start == i) throw new Failure("malformed JSON");
            String n = s.substring(start, i);
            return n.matches("-?\\d+") ? (Object) Long.parseLong(n) : (Object) Double.parseDouble(n);
        }

        private Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            ws();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) {
                ws();
                String key = string();
                ws();
                expect(':');
                m.put(key, value());
                ws();
                if (s.charAt(i) == ',') { i++; continue; }
                expect('}');
                return m;
            }
        }

        private List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++;
            ws();
            if (s.charAt(i) == ']') { i++; return l; }
            while (true) {
                l.add(value());
                ws();
                if (s.charAt(i) == ',') { i++; continue; }
                expect(']');
                return l;
            }
        }

        private String string() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c != '\\') { b.append(c); continue; }
                char e = s.charAt(i++);
                switch (e) {
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'n': b.append('\n'); break;
                    case 'r': b.append('\r'); break;
                    case 't': b.append('\t'); break;
                    case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                    default: b.append(e);
                }
            }
        }

        private void expect(char c) {
            if (i >= s.length() || s.charAt(i) != c) throw new Failure("malformed JSON");
            i++;
        }

        private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
    }
}
