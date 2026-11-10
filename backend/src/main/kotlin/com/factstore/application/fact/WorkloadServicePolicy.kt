package com.factstore.application.fact

import com.factstore.core.domain.FactIdentity
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Strict ABAC for fact ingestion (#190): a CI workload may emit facts only for its own service.
 *
 * The service a workload belongs to is an attribute of its verified identity (#191), never of
 * the request:
 *
 * - a GitHub Actions or GitLab CI token: its `repository` or `project_path` claim;
 * - a SPIFFE workload: the service bound to its SPIFFE ID in `factstore.abac.spiffe-services`
 *   (`<spiffe-id>=<repository>`, comma-separated). An ID with no binding belongs to no service.
 *
 * A workload fact is allowed only when `subject.repository` equals that service exactly. Static
 * credentials and signed-in people are not workloads and are not bound to a service; to stop
 * them ingesting at all, use `factstore.ingest.require-workload-identity` (#189).
 */
@Component
class WorkloadServicePolicy(
    @Value("\${factstore.abac.spiffe-services:}") spiffeServices: String
) {
    private val spiffeBindings: Map<String, String> = spiffeServices.split(",")
        .map { it.trim() }.filter { it.isNotEmpty() }
        .associate { entry ->
            val parts = entry.split("=")
            require(parts.size == 2 && parts.all { it.isNotBlank() }) {
                "factstore.abac.spiffe-services entries are <spiffe-id>=<repository>, got '$entry'"
            }
            parts[0].trim() to parts[1].trim()
        }

    /** The service the identity may emit facts for, or null when it is not bound to one. */
    fun serviceOf(identity: FactIdentity): String? =
        identity.repository ?: identity.spiffeId?.let { spiffeBindings[it] }

    /** Null when allowed; otherwise why the fact is cross-workload injection. */
    fun denial(identity: FactIdentity, subjectRepository: String?): String? {
        if (identity.kind != FactIdentity.WORKLOAD) return null
        val service = serviceOf(identity) ?: return "the workload's identity is not bound to a service"
        if (subjectRepository == null) return "a workload's fact must name subject.repository"
        if (subjectRepository != service) return "workload for $service cannot emit facts for $subjectRepository"
        return null
    }
}
