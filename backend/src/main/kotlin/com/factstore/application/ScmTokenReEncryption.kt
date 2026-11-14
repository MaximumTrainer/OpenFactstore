package com.factstore.application

import com.factstore.core.port.outbound.IScmIntegrationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * The one-off step that moves SCM tokens encrypted with a pre-#242 key to the current key.
 *
 * Run it by starting the backend once with `SCM_LEGACY_ENCRYPTION_KEY` (the old passphrase) and
 * `FACTSTORE_SCM_REENCRYPT_LEGACY_TOKENS=true`; afterwards unset both. See
 * docs/security-hardening.md.
 */
@Service
class ScmTokenReEncryption(
    private val integrations: IScmIntegrationRepository,
    private val encryption: ScmTokenEncryptionService
) {
    private val log = LoggerFactory.getLogger(ScmTokenReEncryption::class.java)

    data class Result(val reEncrypted: Int, val alreadyCurrent: Int)

    @Transactional
    fun reEncryptLegacyTokens(): Result {
        check(encryption.hasLegacyKey) {
            "Re-encrypting SCM tokens needs the old key: set SCM_LEGACY_ENCRYPTION_KEY to the previous SCM_ENCRYPTION_KEY value."
        }
        var moved = 0
        var current = 0
        for (integration in integrations.findAll()) {
            if (!encryption.isLegacy(integration.encryptedToken)) {
                current++
                continue
            }
            integration.encryptedToken = encryption.encrypt(encryption.decrypt(integration.encryptedToken))
            integrations.save(integration)
            moved++
        }
        log.info("Re-encrypted {} SCM token(s) with the current key; {} already used it", moved, current)
        return Result(moved, current)
    }
}
