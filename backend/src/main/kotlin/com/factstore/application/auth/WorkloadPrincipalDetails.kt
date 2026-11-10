package com.factstore.application.auth

/** The verified workload behind a request (#189), with its token's verified claims (#191). */
data class WorkloadPrincipalDetails(val subject: String, val spiffeId: String?, val claims: Map<String, Any?> = emptyMap())
