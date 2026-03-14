package com.factstore

import com.factstore.application.SsoConfigService
import com.factstore.core.domain.MemberRole
import com.factstore.core.domain.SsoProvider
import com.factstore.dto.CreateSsoConfigRequest
import com.factstore.dto.UpdateSsoConfigRequest
import com.factstore.exception.BadRequestException
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Transactional
class SsoConfigServiceTest {

    @Autowired
    lateinit var ssoService: SsoConfigService

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private val orgSlug = "sso-test-org"

    private fun defaultRequest(provider: SsoProvider = SsoProvider.OKTA) = CreateSsoConfigRequest(
        provider = provider,
        issuerUrl = "https://dev-123.okta.com/oauth2/default",
        clientId = "client-abc",
        clientSecret = "secret-xyz",
        isMandatory = false
    )

    // ---------------------------------------------------------------
    // CRUD
    // ---------------------------------------------------------------

    @Test
    fun `create SSO config succeeds`() {
        val response = ssoService.createSsoConfig(orgSlug, defaultRequest())
        assertNotNull(response.id)
        assertEquals(orgSlug, response.orgSlug)
        assertEquals(SsoProvider.OKTA, response.provider)
        assertEquals("https://dev-123.okta.com/oauth2/default", response.issuerUrl)
        assertEquals("client-abc", response.clientId)
        assertFalse(response.isMandatory)
    }

    @Test
    fun `create duplicate SSO config throws ConflictException`() {
        ssoService.createSsoConfig(orgSlug, defaultRequest())
        assertThrows<ConflictException> {
            ssoService.createSsoConfig(orgSlug, defaultRequest())
        }
    }

    @Test
    fun `create SSO config with HTTP issuer URL throws BadRequestException`() {
        assertThrows<BadRequestException> {
            ssoService.createSsoConfig(
                orgSlug,
                defaultRequest().copy(issuerUrl = "http://dev-123.okta.com/oauth2/default")
            )
        }
    }

    @Test
    fun `update SSO config with HTTP issuer URL throws BadRequestException`() {
        ssoService.createSsoConfig(orgSlug, defaultRequest())
        assertThrows<BadRequestException> {
            ssoService.updateSsoConfig(
                orgSlug,
                UpdateSsoConfigRequest(issuerUrl = "http://evil.example.com/oauth2/v1")
            )
        }
    }

    @Test
    fun `get SSO config returns stored config`() {
        ssoService.createSsoConfig(orgSlug, defaultRequest())
        val fetched = ssoService.getSsoConfig(orgSlug)
        assertEquals(orgSlug, fetched.orgSlug)
        assertEquals(SsoProvider.OKTA, fetched.provider)
    }

    @Test
    fun `get non-existent SSO config throws NotFoundException`() {
        assertThrows<NotFoundException> {
            ssoService.getSsoConfig("no-such-org-${UUID.randomUUID()}")
        }
    }

    @Test
    fun `update SSO config fields`() {
        ssoService.createSsoConfig(orgSlug, defaultRequest())
        val updated = ssoService.updateSsoConfig(
            orgSlug,
            UpdateSsoConfigRequest(
                provider = SsoProvider.ENTRA_ID,
                issuerUrl = "https://login.microsoftonline.com/tenant-id/v2.0",
                isMandatory = true
            )
        )
        assertEquals(SsoProvider.ENTRA_ID, updated.provider)
        assertEquals("https://login.microsoftonline.com/tenant-id/v2.0", updated.issuerUrl)
        assertTrue(updated.isMandatory)
    }

    @Test
    fun `update non-existent SSO config throws NotFoundException`() {
        assertThrows<NotFoundException> {
            ssoService.updateSsoConfig("ghost-org", UpdateSsoConfigRequest(isMandatory = true))
        }
    }

    @Test
    fun `delete SSO config removes it`() {
        ssoService.createSsoConfig(orgSlug, defaultRequest())
        ssoService.deleteSsoConfig(orgSlug)
        assertThrows<NotFoundException> {
            ssoService.getSsoConfig(orgSlug)
        }
    }

    @Test
    fun `delete non-existent SSO config throws NotFoundException`() {
        assertThrows<NotFoundException> {
            ssoService.deleteSsoConfig("non-existent-org")
        }
    }

    @Test
    fun `issuer URL trailing slash is trimmed on create`() {
        val response = ssoService.createSsoConfig(
            orgSlug,
            defaultRequest().copy(issuerUrl = "https://dev-123.okta.com/oauth2/default/")
        )
        assertEquals("https://dev-123.okta.com/oauth2/default", response.issuerUrl)
    }

    @Test
    fun `issuer URL trailing slash is trimmed on update`() {
        ssoService.createSsoConfig(orgSlug, defaultRequest())
        val updated = ssoService.updateSsoConfig(
            orgSlug,
            UpdateSsoConfigRequest(issuerUrl = "https://login.microsoftonline.com/tenant/v2.0/")
        )
        assertEquals("https://login.microsoftonline.com/tenant/v2.0", updated.issuerUrl)
    }

    @Test
    fun `ENTRA_ID provider can be configured`() {
        val response = ssoService.createSsoConfig(
            orgSlug,
            CreateSsoConfigRequest(
                provider = SsoProvider.ENTRA_ID,
                issuerUrl = "https://login.microsoftonline.com/my-tenant/v2.0",
                clientId = "entra-client-id",
                isMandatory = true
            )
        )
        assertEquals(SsoProvider.ENTRA_ID, response.provider)
        assertTrue(response.isMandatory)
    }

    // ---------------------------------------------------------------
    // isSsoMandatory
    // ---------------------------------------------------------------

    @Test
    fun `isSsoMandatory returns false when no config exists`() {
        assertFalse(ssoService.isSsoMandatory("no-config-org-${UUID.randomUUID()}"))
    }

    @Test
    fun `isSsoMandatory returns false when config has isMandatory false`() {
        ssoService.createSsoConfig(orgSlug, defaultRequest().copy(isMandatory = false))
        assertFalse(ssoService.isSsoMandatory(orgSlug))
    }

    @Test
    fun `isSsoMandatory returns true when config has isMandatory true`() {
        ssoService.createSsoConfig(orgSlug, defaultRequest().copy(isMandatory = true))
        assertTrue(ssoService.isSsoMandatory(orgSlug))
    }

    // ---------------------------------------------------------------
    // resolveRole (internal method — tested directly)
    // ---------------------------------------------------------------

    @Test
    fun `resolveRole returns MEMBER when groups list is empty`() {
        val role = ssoService.resolveRole(emptyList(), """{"admins":"ADMIN"}""")
        assertEquals(MemberRole.MEMBER, role)
    }

    @Test
    fun `resolveRole maps matching group to correct role`() {
        val role = ssoService.resolveRole(listOf("admins"), """{"admins":"ADMIN"}""")
        assertEquals(MemberRole.ADMIN, role)
    }

    @Test
    fun `resolveRole returns MEMBER when no group matches`() {
        val role = ssoService.resolveRole(listOf("unknown-group"), """{"admins":"ADMIN"}""")
        assertEquals(MemberRole.MEMBER, role)
    }

    @Test
    fun `resolveRole picks highest privilege when user belongs to multiple groups`() {
        // User is in both 'viewers' (VIEWER) and 'admins' (ADMIN) — should get ADMIN.
        val role = ssoService.resolveRole(
            listOf("viewers", "admins"),
            """{"admins":"ADMIN","viewers":"VIEWER"}"""
        )
        assertEquals(MemberRole.ADMIN, role)
    }

    @Test
    fun `resolveRole picks highest privilege regardless of group claim order`() {
        // Same groups in reverse order; result must be deterministic.
        val roleA = ssoService.resolveRole(
            listOf("admins", "viewers"),
            """{"admins":"ADMIN","viewers":"VIEWER"}"""
        )
        val roleB = ssoService.resolveRole(
            listOf("viewers", "admins"),
            """{"admins":"ADMIN","viewers":"VIEWER"}"""
        )
        assertEquals(MemberRole.ADMIN, roleA)
        assertEquals(MemberRole.ADMIN, roleB)
    }

    @Test
    fun `resolveRole falls back to MEMBER on invalid JSON mappings`() {
        val role = ssoService.resolveRole(listOf("admins"), "not-valid-json")
        assertEquals(MemberRole.MEMBER, role)
    }

    @Test
    fun `resolveRole falls back to MEMBER when role name is unknown`() {
        val role = ssoService.resolveRole(listOf("admins"), """{"admins":"SUPER_ADMIN"}""")
        assertEquals(MemberRole.MEMBER, role)
    }

    @Test
    fun `resolveRole skips groups with unknown roles and uses next matching group`() {
        val role = ssoService.resolveRole(
            listOf("super-admins", "developers"),
            """{"super-admins":"NOT_A_ROLE","developers":"MEMBER"}"""
        )
        assertEquals(MemberRole.MEMBER, role)
    }

    // ---------------------------------------------------------------
    // State validation (callback without HTTP)
    // ---------------------------------------------------------------

    @Test
    fun `handleSsoCallback throws BadRequestException for unknown state`() {
        assertThrows<BadRequestException> {
            ssoService.handleSsoCallback(orgSlug, "some-code", "nonexistent-state")
        }
    }

    @Test
    fun `handleSsoCallback throws BadRequestException for expired state`() {
        // Inject an already-expired state directly into the internal map.
        ssoService.pendingStates["expired-state"] = SsoConfigService.PendingOidcState(
            orgSlug = orgSlug,
            redirectUri = "https://app.example.com/callback",
            expiresAt = Instant.parse("2000-01-01T00:00:00Z")
        )
        assertThrows<BadRequestException> {
            ssoService.handleSsoCallback(orgSlug, "code", "expired-state")
        }
    }

    @Test
    fun `handleSsoCallback throws BadRequestException when state belongs to different org`() {
        ssoService.pendingStates["cross-org-state"] = SsoConfigService.PendingOidcState(
            orgSlug = "other-org",
            redirectUri = "https://app.example.com/callback",
            expiresAt = Instant.parse("2099-12-31T23:59:59Z")
        )
        assertThrows<BadRequestException> {
            ssoService.handleSsoCallback(orgSlug, "code", "cross-org-state")
        }
    }

    // ---------------------------------------------------------------
    // generateJwt
    // ---------------------------------------------------------------

    @Test
    fun `generate JWT contains expected claims`() {
        val userId = UUID.randomUUID()
        val jwt = ssoService.generateJwt(userId, "test@example.com", "my-org")
        val parts = jwt.split(".")
        assertEquals(3, parts.size, "JWT should have 3 parts: header.payload.signature")

        val payloadJson = String(
            java.util.Base64.getUrlDecoder().decode(
                parts[1].let { s -> s + "=".repeat((4 - s.length % 4) % 4) }
            )
        )
        assertTrue(payloadJson.contains("\"sub\":\"$userId\""))
        assertTrue(payloadJson.contains("\"email\":\"test@example.com\""))
        assertTrue(payloadJson.contains("\"org\":\"my-org\""))
        assertTrue(payloadJson.contains("\"iat\""))
        assertTrue(payloadJson.contains("\"exp\""))
    }

    @Test
    fun `generate JWT with special characters in email produces valid JSON`() {
        val jwt = ssoService.generateJwt(UUID.randomUUID(), "user+tag@example.com", "org/slug")
        val parts = jwt.split(".")
        assertEquals(3, parts.size)
        // Payload must be parseable JSON — ObjectMapper serialization ensures this.
        val payloadJson = String(
            java.util.Base64.getUrlDecoder().decode(
                parts[1].let { s -> s + "=".repeat((4 - s.length % 4) % 4) }
            )
        )
        val map = objectMapper.readValue(payloadJson, Map::class.java)
        assertEquals("user+tag@example.com", map["email"])
        assertEquals("org/slug", map["org"])
    }

    @Test
    fun `client secret is not exposed in SSO config response`() {
        ssoService.createSsoConfig(orgSlug, defaultRequest(SsoProvider.ENTRA_ID))
        val response = ssoService.getSsoConfig(orgSlug)
        // Verify the secret does not appear in the serialized API response JSON
        val json = objectMapper.writeValueAsString(response)
        assertFalse(json.contains("secret-xyz"), "clientSecret must not appear in the API response JSON")
    }
}
