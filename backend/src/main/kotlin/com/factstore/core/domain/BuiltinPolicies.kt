package com.factstore.core.domain

object BuiltinPolicies {

    val SECURITY_GATE = """
package compliance

default allow = false

allow {
    input.attestations[_].type == "SECURITY_SCAN"
    input.attestations[_].status == "PASSED"
}
""".trimIndent()

    val APPROVAL_GATE = """
package compliance

default allow = false

allow {
    input.approvalStatus == "APPROVED"
}
""".trimIndent()

    val VULNERABILITY_GATE = """
package compliance

default allow = false

allow {
    input.criticalVulnerabilities == 0
}
""".trimIndent()

    val FULL_COMPLIANCE_GATE = """
package compliance

default allow = false

allow {
    input.approvalStatus == "APPROVED"
    input.criticalVulnerabilities == 0
    input.attestations[_].type == "SECURITY_SCAN"
    input.attestations[_].status == "PASSED"
}
""".trimIndent()
}
