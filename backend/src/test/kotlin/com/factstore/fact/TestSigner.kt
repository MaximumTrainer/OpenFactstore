package com.factstore.fact

import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/**
 * A CI runner's Ed25519 signing key, for tests (#187). Signs the exact request body and sets the
 * `X-Signature` and `X-Signature-Key-Id` headers a runner sends.
 */
class TestSigner(val keyId: String = "runner-${System.nanoTime()}") {
    private val keyPair: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    /** The public key as PEM, as `openssl pkey -pubout` prints it. */
    val publicKeyPem: String
        get() = "-----BEGIN PUBLIC KEY-----\n" +
            Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(keyPair.public.encoded) +
            "\n-----END PUBLIC KEY-----\n"

    fun sign(body: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        Signature.getInstance("Ed25519").run {
            initSign(keyPair.private)
            update(body.toByteArray(Charsets.UTF_8))
            sign()
        }
    )

    /** Sets the body and signs it. */
    fun signed(request: MockHttpServletRequestDsl, body: String) {
        request.content = body
        request.header("X-Signature", sign(body))
        request.header("X-Signature-Key-Id", keyId)
    }
}
