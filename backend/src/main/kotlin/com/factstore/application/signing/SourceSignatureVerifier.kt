package com.factstore.application.signing

import com.factstore.core.domain.SigningKeyStatus
import com.factstore.exception.SignatureRejectedException
import org.springframework.stereotype.Component
import java.security.Signature
import java.util.Base64

/** A verified source signature (#187): which registry key made it, and the signature itself. */
data class SourceSignature(val keyId: String, val signature: String)

/**
 * Verifies a fact's source signature (#187): an Ed25519 signature, made by the CI runner, over
 * the exact request body bytes, sent as `X-Signature` (base64url or base64) with the registry key
 * id in `X-Signature-Key-Id`. Anything unverifiable is a [SignatureRejectedException].
 */
@Component
class SourceSignatureVerifier(private val registry: SigningKeyRegistryService) {

    fun verify(body: ByteArray, signatureHeader: String?, keyId: String?): SourceSignature {
        if (signatureHeader.isNullOrBlank() || keyId.isNullOrBlank()) {
            throw SignatureRejectedException(
                SignatureRejectedException.MISSING,
                "Facts must be signed at the source: send X-Signature and X-Signature-Key-Id"
            )
        }
        val signature = decode(signatureHeader.trim())
            ?: throw SignatureRejectedException(SignatureRejectedException.MALFORMED, "X-Signature is not a base64 Ed25519 signature")

        val key = registry.find(keyId.trim())
            ?: throw SignatureRejectedException(SignatureRejectedException.UNKNOWN_KEY, "Signing key '$keyId' is not in the trusted registry")
        if (key.status != SigningKeyStatus.ACTIVE) {
            throw SignatureRejectedException(SignatureRejectedException.REVOKED_KEY, "Signing key '$keyId' has been revoked")
        }

        val valid = runCatching {
            Signature.getInstance(SigningKeyRegistryService.ALGORITHM).run {
                initVerify(key.publicKey)
                update(body)
                verify(signature)
            }
        }.getOrDefault(false)
        if (!valid) {
            throw SignatureRejectedException(SignatureRejectedException.INVALID, "X-Signature does not verify over the request body")
        }
        return SourceSignature(key.keyId, signatureHeader.trim())
    }

    /** Base64url (preferred) or standard base64, decoding to exactly 64 bytes. */
    private fun decode(value: String): ByteArray? =
        (runCatching { Base64.getUrlDecoder().decode(value) }.getOrNull()
            ?: runCatching { Base64.getDecoder().decode(value) }.getOrNull())
            ?.takeIf { it.size == ED25519_SIGNATURE_BYTES }

    companion object {
        private const val ED25519_SIGNATURE_BYTES = 64
    }
}
