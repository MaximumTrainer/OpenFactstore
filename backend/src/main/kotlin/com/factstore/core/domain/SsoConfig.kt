package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class SsoProvider { ENTRA_ID, OKTA }

/**
 * Per-organisation SSO configuration for OIDC-based single sign-on.
 *
 * The [clientSecret] is stored in plain text here for simplicity.
 * In production deployments it should be encrypted at rest (e.g., via a secrets manager
 * or column-level encryption) to protect the credential.
 */
@Entity
@Table(
    name = "sso_configs",
    uniqueConstraints = [UniqueConstraint(name = "uq_sso_configs_org_slug", columnNames = ["org_slug"])]
)
class SsoConfig(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "org_slug", nullable = false, length = 255)
    val orgSlug: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var provider: SsoProvider,

    /** OIDC Issuer URL, e.g. https://login.microsoftonline.com/{tenantId}/v2.0 */
    @Column(name = "issuer_url", nullable = false)
    var issuerUrl: String,

    @Column(name = "client_id", nullable = false)
    var clientId: String,

    /** OIDC client secret — keep this confidential. */
    @Column(name = "client_secret")
    var clientSecret: String? = null,

    /**
     * JSON object mapping Factstore field names to their corresponding IdP claim names.
     * Keys are Factstore fields: "email", "name", "role".
     * Values are the claim names in the IdP's ID token.  Example:
     * ```json
     * {"email":"email","name":"name","role":"groups"}
     * ```
     * With this mapping, the "email" claim is read from the IdP token field "email",
     * the user's name from "name", and group membership from "groups".
     */
    @Column(name = "attribute_mappings", columnDefinition = "TEXT")
    var attributeMappings: String = """{"email":"email","name":"name"}""",

    /**
     * JSON object mapping IdP group names to [MemberRole] values.
     * Example: `{"admins":"ADMIN","developers":"MEMBER"}`.
     */
    @Column(name = "group_role_mappings", columnDefinition = "TEXT")
    var groupRoleMappings: String = "{}",

    /** When true, users of this organisation cannot log in via password – only SSO. */
    @Column(name = "is_mandatory")
    var isMandatory: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
