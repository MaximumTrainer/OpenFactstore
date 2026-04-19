package com.factstore.application

import com.factstore.core.domain.ScmIntegration
import com.factstore.core.domain.ScmProvider
import com.factstore.core.port.outbound.IScmIntegrationRepository
import com.factstore.dto.CreateScmIntegrationRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ScmIntegrationServiceUnitTest {

    @Mock
    private lateinit var scmIntegrationRepository: IScmIntegrationRepository

    private val encryptionService = ScmTokenEncryptionService("test-key-32-chars-for-unit-tests")

    private val service: ScmIntegrationService by lazy {
        ScmIntegrationService(scmIntegrationRepository, encryptionService)
    }

    @Test
    fun `create integration saves and returns response`() {
        val orgSlug = "my-org"
        val request = CreateScmIntegrationRequest(provider = ScmProvider.GITHUB, token = "ghp_token123")
        whenever(scmIntegrationRepository.existsByOrgSlugAndProvider(orgSlug, ScmProvider.GITHUB)).thenReturn(false)
        whenever(scmIntegrationRepository.save(any())).thenAnswer { it.arguments[0] as ScmIntegration }

        val response = service.createIntegration(orgSlug, request)

        assertEquals(orgSlug, response.orgSlug)
        assertEquals(ScmProvider.GITHUB, response.provider)
        assertTrue(response.isTokenEncrypted)
        verify(scmIntegrationRepository).save(any())
    }

    @Test
    fun `create integration throws ConflictException when already exists`() {
        val orgSlug = "my-org"
        val request = CreateScmIntegrationRequest(provider = ScmProvider.GITLAB, token = "glpat_token")
        whenever(scmIntegrationRepository.existsByOrgSlugAndProvider(orgSlug, ScmProvider.GITLAB)).thenReturn(true)

        assertThrows(ConflictException::class.java) {
            service.createIntegration(orgSlug, request)
        }
    }

    @Test
    fun `list integrations returns mapped responses`() {
        val orgSlug = "my-org"
        val integrations = listOf(
            ScmIntegration(orgSlug = orgSlug, provider = ScmProvider.GITHUB, encryptedToken = "enc1", isTokenEncrypted = true),
            ScmIntegration(orgSlug = orgSlug, provider = ScmProvider.GITLAB, encryptedToken = "enc2", isTokenEncrypted = true)
        )
        whenever(scmIntegrationRepository.findByOrgSlug(orgSlug)).thenReturn(integrations)

        val responses = service.listIntegrations(orgSlug)

        assertEquals(2, responses.size)
        assertEquals(ScmProvider.GITHUB, responses[0].provider)
        assertEquals(ScmProvider.GITLAB, responses[1].provider)
    }

    @Test
    fun `delete integration succeeds when exists`() {
        val orgSlug = "my-org"
        whenever(scmIntegrationRepository.existsByOrgSlugAndProvider(orgSlug, ScmProvider.BITBUCKET)).thenReturn(true)

        assertDoesNotThrow { service.deleteIntegration(orgSlug, ScmProvider.BITBUCKET) }

        verify(scmIntegrationRepository).deleteByOrgSlugAndProvider(orgSlug, ScmProvider.BITBUCKET)
    }

    @Test
    fun `delete integration throws NotFoundException when not found`() {
        val orgSlug = "my-org"
        whenever(scmIntegrationRepository.existsByOrgSlugAndProvider(orgSlug, ScmProvider.AZURE_DEVOPS)).thenReturn(false)

        assertThrows(NotFoundException::class.java) {
            service.deleteIntegration(orgSlug, ScmProvider.AZURE_DEVOPS)
        }
    }
}
