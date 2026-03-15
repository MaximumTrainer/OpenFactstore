package com.factstore.adapter.outbound.signature

import com.factstore.core.port.outbound.ISignatureVerifier
import com.factstore.core.port.outbound.SignatureVerificationResult
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["cosign.enabled"], havingValue = "false", matchIfMissing = true)
class NoopSignatureVerifier : ISignatureVerifier {
    override fun verify(imageRef: String, publicKey: String?): SignatureVerificationResult =
        SignatureVerificationResult(verified = false, message = "Cosign disabled (cosign.enabled=false)")
}
