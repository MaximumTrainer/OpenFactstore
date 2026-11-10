package com.factstore.application.signing

import com.factstore.application.ActorResolver
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.SigningKey
import com.factstore.core.domain.SigningKeyStatus
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.outbound.ISigningKeyRepository
import com.factstore.exception.BadRequestException
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64

data class SigningKeyResponse(
    val keyId: String,
    val algorithm: String,
    val status: SigningKeyStatus,
    val createdBy: String,
    val createdAt: Instant,
    val revokedAt: Instant?
)

/**
 * The trusted registry of CI runners' public signing keys (#187).
 *
 * Keys are Ed25519 only. A key is registered once under a unique key id and is never deleted or
 * replaced: rotation is registering a new key and later revoking the old one, so facts signed
 * with either keep verifying until the old key is revoked, and every stored signature can still
 * be checked against the key that made it.
 */
@Service
@Transactional
class SigningKeyRegistryService(
    private val repository: ISigningKeyRepository,
    private val auditService: IAuditService,
    private val actorResolver: ActorResolver
) {

    fun register(keyId: String, publicKeyPem: String): SigningKeyResponse {
        if (!KEY_ID.matches(keyId)) {
            throw BadRequestException("A key id is 1 to 128 letters, digits, '.', '_', ':' or '-' (got '$keyId')")
        }
        val encoded = parseEd25519(publicKeyPem)
        if (repository.findByKeyId(keyId) != null) {
            throw ConflictException("Signing key '$keyId' is already registered; register a new key id to rotate")
        }
        val actor = actorResolver.current()
        val saved = repository.save(SigningKey(keyId = keyId, publicKey = encoded, createdBy = actor))
        auditService.record(AuditEventType.SIGNING_KEY_REGISTERED, actor, mapOf("keyId" to keyId))
        return saved.toResponse()
    }

    fun revoke(keyId: String): SigningKeyResponse {
        val key = repository.findByKeyId(keyId) ?: throw NotFoundException("No signing key '$keyId'")
        if (key.status == SigningKeyStatus.REVOKED) return key.toResponse()
        key.status = SigningKeyStatus.REVOKED
        key.revokedAt = Instant.now()
        val saved = repository.save(key)
        auditService.record(AuditEventType.SIGNING_KEY_REVOKED, actorResolver.current(), mapOf("keyId" to keyId))
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    fun list(): List<SigningKeyResponse> = repository.findAll().map { it.toResponse() }

    /** The key as registered, or null when the registry has never seen this id. */
    @Transactional(readOnly = true)
    fun find(keyId: String): RegisteredKey? = repository.findByKeyId(keyId)?.let {
        RegisteredKey(it.keyId, it.status, decode(it.publicKey))
    }

    data class RegisteredKey(val keyId: String, val status: SigningKeyStatus, val publicKey: PublicKey)

    private fun parseEd25519(pem: String): String {
        val body = pem.trim()
            .removePrefix("-----BEGIN PUBLIC KEY-----")
            .removeSuffix("-----END PUBLIC KEY-----")
            .filterNot { it.isWhitespace() }
        val der = runCatching { Base64.getDecoder().decode(body) }.getOrNull()
            ?: throw BadRequestException("publicKey must be a PEM-encoded public key")
        runCatching { KeyFactory.getInstance(ALGORITHM).generatePublic(X509EncodedKeySpec(der)) }.getOrNull()
            ?: throw BadRequestException("publicKey must be an Ed25519 public key")
        return Base64.getEncoder().encodeToString(der)
    }

    private fun decode(encoded: String): PublicKey =
        KeyFactory.getInstance(ALGORITHM).generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(encoded)))

    private fun SigningKey.toResponse() = SigningKeyResponse(keyId, ALGORITHM, status, createdBy, createdAt, revokedAt)

    companion object {
        const val ALGORITHM = "Ed25519"
        private val KEY_ID = Regex("^[A-Za-z0-9._:-]{1,128}$")
    }
}
