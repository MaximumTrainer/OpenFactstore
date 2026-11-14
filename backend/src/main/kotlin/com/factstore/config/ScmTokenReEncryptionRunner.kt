package com.factstore.config

import com.factstore.application.ScmTokenReEncryption
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/** Runs the one-off SCM token re-encryption at startup when asked to (#242). */
@Component
@ConditionalOnProperty("factstore.scm.reencrypt-legacy-tokens", havingValue = "true")
class ScmTokenReEncryptionRunner(private val reEncryption: ScmTokenReEncryption) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        reEncryption.reEncryptLegacyTokens()
    }
}
