package com.factstore.core.domain

data class FlowTemplate(
    val version: Int = 1,
    val trail: TrailTemplate = TrailTemplate(),
    val artifacts: List<ArtifactTemplate> = emptyList()
)

data class TrailTemplate(
    val attestations: List<TemplateAttestation> = emptyList()
)

data class ArtifactTemplate(
    val name: String,
    val attestations: List<TemplateAttestation> = emptyList()
)

data class TemplateAttestation(
    val name: String,
    val type: String
)
