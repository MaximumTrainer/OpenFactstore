package com.factstore.core.domain.security

import com.factstore.core.domain.MemberRole

/**
 * The permission vocabulary, as `resource:action` scopes (#155 FR-3.2).
 *
 * This is the single model both credential types resolve into: a **user** gets permissions from
 * their [MemberRole] in the organisation they are acting in (#156 FR-5), and a **service account**
 * gets them from the scopes on its API key. Nothing else grants access.
 *
 * The [scope] string is the wire form — what an API key stores and what
 * `@PreAuthorize("hasAuthority('SCOPE_flows:write')")` matches.
 */
enum class Permission(val scope: String) {
    FLOWS_READ("flows:read"),
    FLOWS_WRITE("flows:write"),
    TRAILS_READ("trails:read"),
    TRAILS_WRITE("trails:write"),
    /** The CI-pipeline permission: post attestations to a trail. */
    ATTESTATIONS_WRITE("attestations:write"),
    ARTIFACTS_WRITE("artifacts:write"),
    EVIDENCE_READ("evidence:read"),
    EVIDENCE_WRITE("evidence:write"),
    ASSERT_EXECUTE("assert:execute"),
    POLICIES_READ("policies:read"),
    POLICIES_WRITE("policies:write"),
    APPROVALS_WRITE("approvals:write"),
    /**
     * Scrape `/actuator/prometheus` and nothing else (#205). What the monitoring stack's key
     * carries, so the secret Prometheus holds cannot read or change a single fact.
     */
    METRICS_READ("metrics:read"),
    /**
     * Read across organisations (#207, #155 FR-5.4). Only meaningful on a credential bound to no
     * organisation; every such read is audited as `CROSS_TENANT_READ`. Required for it once
     * `security.tenancy.unbound-requires-operator=true`.
     */
    OPERATOR("operator"),
    /**
     * Full access, including key management, service accounts, organisation and SSO
     * configuration. It is not a narrower privilege than the rest: a holder may mint a key
     * carrying any scope, so it expands to every permission — see
     * [RoleModel.authoritiesForScopes].
     */
    ADMIN("admin");

    /** The Spring Security authority this permission grants. */
    val authority: String get() = "$AUTHORITY_PREFIX$scope"

    companion object {
        const val AUTHORITY_PREFIX = "SCOPE_"

        private val byScope = entries.associateBy { it.scope }

        fun fromScope(scope: String): Permission? = byScope[scope.trim().lowercase()]

        /** Parses a scope set, ignoring blanks. Unknown scopes are returned separately. */
        fun parse(scopes: Collection<String>): ParsedScopes {
            val known = linkedSetOf<Permission>()
            val unknown = linkedSetOf<String>()
            scopes.filter { it.isNotBlank() }.forEach { raw ->
                val permission = fromScope(raw)
                if (permission != null) known += permission else unknown += raw.trim()
            }
            return ParsedScopes(known, unknown)
        }

        /**
         * The preset for the common CI case, so a pipeline credential needs no scope
         * expertise (#155 FR-3.5).
         */
        val CI_PIPELINE_PRESET: Set<Permission> = setOf(
            TRAILS_WRITE, ATTESTATIONS_WRITE, ARTIFACTS_WRITE, EVIDENCE_WRITE, ASSERT_EXECUTE,
            // A pipeline reads as well as writes: the CLI and setup-factstore look up flows by
            // name, trails by commit, gate results and environments (#206).
            FLOWS_READ, TRAILS_READ
        )

        /** What a credential gets when none is requested: read-only, never full access. */
        val DEFAULT_MINIMAL: Set<Permission> = setOf(FLOWS_READ, TRAILS_READ)
    }
}

data class ParsedScopes(val permissions: Set<Permission>, val unknown: Set<String>)

/**
 * Maps a role onto the permissions it carries (#155 FR-4.2).
 *
 * `SERVICE_ACCOUNT` deliberately carries **no** permissions from its role: a service account's
 * capability comes solely from the scopes on the key it presents, which is what makes least
 * privilege possible for machine credentials.
 */
object RoleModel {

    private val VIEWER_PERMISSIONS: Set<Permission> = setOf(
        Permission.FLOWS_READ,
        Permission.TRAILS_READ,
        Permission.EVIDENCE_READ,
        Permission.POLICIES_READ
    )

    private val MEMBER_PERMISSIONS: Set<Permission> = VIEWER_PERMISSIONS + setOf(
        Permission.TRAILS_WRITE,
        Permission.ATTESTATIONS_WRITE,
        Permission.ARTIFACTS_WRITE,
        Permission.EVIDENCE_WRITE,
        Permission.ASSERT_EXECUTE,
        Permission.APPROVALS_WRITE
    )

    private val ADMIN_PERMISSIONS: Set<Permission> = MEMBER_PERMISSIONS + setOf(
        Permission.FLOWS_WRITE,
        Permission.POLICIES_WRITE,
        Permission.METRICS_READ,
        Permission.OPERATOR,
        Permission.ADMIN
    )

    fun permissionsFor(role: MemberRole): Set<Permission> = when (role) {
        MemberRole.VIEWER -> VIEWER_PERMISSIONS
        MemberRole.MEMBER -> MEMBER_PERMISSIONS
        MemberRole.ADMIN -> ADMIN_PERMISSIONS
        MemberRole.SERVICE_ACCOUNT -> emptySet()
    }

    /**
     * Authorities for a user acting in an organisation: the role itself, so `hasRole('ADMIN')`
     * works, plus one authority per permission.
     */
    fun authoritiesFor(role: MemberRole): Set<String> =
        setOf("ROLE_${role.name}") + permissionsFor(role).map { it.authority }

    /**
     * Authorities for a service-account credential, derived from its scopes alone.
     *
     * `admin` expands to every permission, because that is what the word already means
     * everywhere else: [ADMIN_PERMISSIONS] is the full set, and a caller holding `admin` may
     * mint a key carrying any scope at all. Treating it as one narrow key-management scope here
     * made the same word mean "everything" for a user and "key management only" for a key —
     * which left the bootstrap credential, the only credential a fresh enforced deployment has,
     * unable to create a flow.
     */
    fun authoritiesForScopes(permissions: Set<Permission>): Set<String> {
        val effective = if (permissions.contains(Permission.ADMIN)) ADMIN_PERMISSIONS else permissions
        return setOf("ROLE_${MemberRole.SERVICE_ACCOUNT.name}") + effective.map { it.authority }
    }

    /**
     * The role a first-time federated sign-in with no membership receives (#156 FR-5.3).
     * The safe option, and the documented default.
     */
    val DEFAULT_ROLE_FOR_NEW_MEMBER: MemberRole = MemberRole.VIEWER
}
