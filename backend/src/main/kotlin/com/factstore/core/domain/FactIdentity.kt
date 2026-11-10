package com.factstore.core.domain

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * Who sent a fact, as verified when it arrived (#191). Set by the server only, never read from
 * the request, and stored with the fact so the lineage cannot be lost or changed afterwards.
 *
 * - `kind = "workload"`: a verified OIDC token or SPIFFE JWT-SVID (#189). The CI run's claims are
 *   recorded: repository, run, attempt, actor, workflow, ref and commit.
 * - `kind = "credential"`: a static API key or a signed-in person; [subject] is the actor
 *   (`api-key:<owner>` or an email) and the run fields are empty.
 */
data class FactIdentity(
    val kind: String,
    val subject: String,
    val issuer: String? = null,
    val spiffeId: String? = null,
    val repository: String? = null,
    val runId: String? = null,
    val runAttempt: String? = null,
    val actor: String? = null,
    val workflowRef: String? = null,
    val ref: String? = null,
    val sha: String? = null
) {
    companion object {
        const val WORKLOAD = "workload"
        const val CREDENTIAL = "credential"

        /** Lineage from a verified GitHub Actions, GitLab CI or SPIFFE token's claims. */
        fun fromWorkloadClaims(claims: Map<String, Any?>): FactIdentity {
            fun claim(vararg names: String): String? = names.firstNotNullOfOrNull { claims[it]?.toString()?.takeIf(String::isNotBlank) }
            val subject = claim("sub") ?: throw IllegalArgumentException("a workload identity needs a subject")
            return FactIdentity(
                kind = WORKLOAD,
                subject = subject,
                issuer = claim("iss"),
                spiffeId = subject.takeIf { it.startsWith(SpiffeId.SCHEME) },
                repository = claim("repository", "project_path"),
                runId = claim("run_id", "pipeline_id"),
                runAttempt = claim("run_attempt"),
                actor = claim("actor", "user_login"),
                workflowRef = claim("workflow_ref", "ci_config_ref_uri"),
                ref = claim("ref"),
                sha = claim("sha")
            )
        }

        fun credential(actor: String) = FactIdentity(kind = CREDENTIAL, subject = actor)
    }
}

@Converter
class FactIdentityConverter : AttributeConverter<FactIdentity, String> {
    private val mapper = jacksonObjectMapper()
    override fun convertToDatabaseColumn(attribute: FactIdentity): String = mapper.writeValueAsString(attribute)
    override fun convertToEntityAttribute(dbData: String): FactIdentity = mapper.readValue(dbData)
}
