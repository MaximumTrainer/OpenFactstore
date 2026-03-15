package com.factstore.adapter.outbound.signature

import com.factstore.core.port.outbound.ISignatureVerifier
import com.factstore.core.port.outbound.SignatureVerificationResult
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.io.File
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnProperty(name = ["cosign.enabled"], havingValue = "true")
class CosignCliSignatureVerifier : ISignatureVerifier {
    private val log = LoggerFactory.getLogger(CosignCliSignatureVerifier::class.java)

    override fun verify(imageRef: String, publicKey: String?): SignatureVerificationResult {
        return try {
            val cmd = buildCommand(imageRef, publicKey)
            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
            val exited = process.waitFor(30, TimeUnit.SECONDS)
            if (!exited) {
                process.destroyForcibly()
                return SignatureVerificationResult(verified = false, message = "Cosign verification timed out")
            }
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.exitValue()
            if (exitCode == 0) {
                val logId = Regex("tlog index: (\\d+)").find(output)?.groupValues?.get(1)
                SignatureVerificationResult(verified = true, rekorLogId = logId, message = "Signature verified")
            } else {
                SignatureVerificationResult(verified = false, message = output.take(500))
            }
        } catch (e: Exception) {
            log.error("Cosign verification failed for {}: {}", imageRef, e.message)
            SignatureVerificationResult(verified = false, message = "Cosign error: ${e.message}")
        }
    }

    private fun buildCommand(imageRef: String, publicKey: String?): List<String> {
        return if (publicKey != null) {
            val keyFile = File.createTempFile("cosign-key-", ".pub").apply {
                writeText(publicKey)
                deleteOnExit()
            }
            listOf("cosign", "verify", "--key", keyFile.absolutePath, imageRef)
        } else {
            listOf("cosign", "verify", "--certificate-identity-regexp", ".*", "--certificate-oidc-issuer-regexp", ".*", imageRef)
        }
    }
}
