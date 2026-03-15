package com.factstore.core.port.outbound

data class SignatureVerificationResult(
    val verified: Boolean,
    val rekorLogId: String? = null,
    val message: String
)

interface ISignatureVerifier {
    fun verify(imageRef: String, publicKey: String? = null): SignatureVerificationResult
}
