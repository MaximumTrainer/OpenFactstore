package com.factstore.application

import com.factstore.core.domain.AttestationStatus

data class WebhookEvent(
    val eventType: String,
    val commitSha: String?,
    val branch: String?,
    val actor: String?,
    val actorEmail: String?,
    val attestationType: String?,
    val attestationStatus: AttestationStatus?,
    val details: String?,
    val trailId: java.util.UUID?
)
