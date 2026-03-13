package com.factstore

import com.factstore.application.SsoConfigService
import com.factstore.application.UserService
import com.factstore.core.domain.SsoProvider
import com.factstore.dto.CreateSsoConfigRequest
import com.factstore.dto.CreateUserRequest
import com.factstore.dto.UpdateSsoConfigRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class SsoConfigServiceTest {

    @Autowired
    lateinit var ssoService: SsoConfigService

    @Autowired
    lateinit var userService: UserService

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
    fun `generate JWT contains expected claims`() {
        val userId = UUID.randomUUID()
        val jwt = ssoService.generateJwt(userId, "test@example.com", "my-org")
        val parts = jwt.split(".")
        assertEquals(3, parts.size, "JWT should have 3 parts: header.payload.signature")

        val payloadJson = String(java.util.Base64.getUrlDecoder().decode(
            parts[1].let { s -> s + "=".repeat((4 - s.length % 4) % 4) }
        ))
        assertTrue(payloadJson.contains("\"sub\":\"$userId\""))
        assertTrue(payloadJson.contains("\"email\":\"test@example.com\""))
        assertTrue(payloadJson.contains("\"org\":\"my-org\""))
        assertTrue(payloadJson.contains("\"iat\""))
        assertTrue(payloadJson.contains("\"exp\""))
    }

    @Test
    fun `client secret is not exposed in SSO config response`() {
        ssoService.createSsoConfig(orgSlug, defaultRequest(SsoProvider.ENTRA_ID))
        val response = ssoService.getSsoConfig(orgSlug)
        // Verify the secret does not appear in the serialized API response JSON
        val json = objectMapper.writeValueAsString(response)
        assertFalse(json.contains("secret-xyz"), "clientSecret must not appear in the API response JSON")
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
}
