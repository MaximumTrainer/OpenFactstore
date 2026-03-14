package com.factstore

import com.factstore.application.VaultEvidenceService
import com.factstore.config.VaultProperties
import com.factstore.core.port.outbound.EvidenceMetadata
import com.factstore.core.port.outbound.ISecureEvidenceStore
import com.factstore.core.port.outbound.VaultStorageReceipt
import com.factstore.dto.StoreEvidenceRequest
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class VaultEvidenceServiceTest {

    private lateinit var secureStore: ISecureEvidenceStore
    private lateinit var vaultProperties: VaultProperties
    private lateinit var service: VaultEvidenceService

    @BeforeEach
    fun setUp() {
        secureStore = mock()
        vaultProperties = VaultProperties(
            enabled = true,
            uri = "http://localhost:8200",
            authentication = VaultProperties.AuthMethod.TOKEN,
            token = "test-token"
        )
        service = VaultEvidenceService(secureStore, vaultProperties)
    }

    // ----- storeEvidence tests -----

    @Test
    fun `storeEvidence returns receipt with path and version`() {
        val receipt = VaultStorageReceipt(
            path = "secret/evidence/software_release/release-v1.0.0/security_scan",
            version = 1
        )
        whenever(
            secureStore.storeEvidence(
                entityType = "software_release",
                entityId = "release-v1.0.0",
                evidenceType = "security_scan",
                data = mapOf("result" to "Passed")
            )
        ).thenReturn(receipt)

        val request = StoreEvidenceRequest(
            evidenceType = "security_scan",
            data = mapOf("result" to "Passed")
        )
        val response = service.storeEvidence("software_release", "release-v1.0.0", request)

        assertEquals("software_release", response.entityType)
        assertEquals("release-v1.0.0", response.entityId)
        assertEquals("security_scan", response.evidenceType)
        assertEquals("secret/evidence/software_release/release-v1.0.0/security_scan", response.vaultPath)
        assertEquals(1, response.version)
        assertNotNull(response.storedAt)
    }

    @Test
    fun `storeEvidence delegates to secureStore with correct arguments`() {
        val receipt = VaultStorageReceipt(path = "secret/evidence/trail/abc/approval", version = 2)
        whenever(
            secureStore.storeEvidence(
                entityType = "trail",
                entityId = "abc",
                evidenceType = "approval",
                data = mapOf("approvedBy" to "alice")
            )
        ).thenReturn(receipt)

        val request = StoreEvidenceRequest("approval", mapOf("approvedBy" to "alice"))
        service.storeEvidence("trail", "abc", request)

        verify(secureStore).storeEvidence(
            entityType = "trail",
            entityId = "abc",
            evidenceType = "approval",
            data = mapOf("approvedBy" to "alice")
        )
    }

    // ----- retrieveEvidence tests -----

    @Test
    fun `retrieveEvidence returns response with version and storedAt from metadata`() {
        whenever(
            secureStore.getEvidenceMetadata("software_release", "release-v1.0.0", "security_scan")
        ).thenReturn(EvidenceMetadata(version = 3, createdTime = "2024-06-01T10:00:00.000000000Z"))

        val response = service.retrieveEvidence("software_release", "release-v1.0.0", "security_scan")

        assertEquals("software_release", response.entityType)
        assertEquals("release-v1.0.0", response.entityId)
        assertEquals("security_scan", response.evidenceType)
        assertEquals(3, response.version)
        assertNotNull(response.storedAt)
    }

    @Test
    fun `retrieveEvidence throws NotFoundException when evidence does not exist`() {
        whenever(
            secureStore.getEvidenceMetadata("software_release", "missing-release", "security_scan")
        ).thenReturn(null)

        assertThrows(NotFoundException::class.java) {
            service.retrieveEvidence("software_release", "missing-release", "security_scan")
        }
    }

    // ----- listEvidence tests -----

    @Test
    fun `listEvidence returns all evidence types for an entity`() {
        whenever(secureStore.listEvidence("trail", "abc-123"))
            .thenReturn(listOf("security_scan", "approval", "compliance_check"))

        val response = service.listEvidence("trail", "abc-123")

        assertEquals("trail", response.entityType)
        assertEquals("abc-123", response.entityId)
        assertEquals(listOf("security_scan", "approval", "compliance_check"), response.evidenceTypes)
    }

    @Test
    fun `listEvidence returns empty list when no evidence exists`() {
        whenever(secureStore.listEvidence(any(), any())).thenReturn(emptyList())

        val response = service.listEvidence("trail", "nonexistent")

        assertTrue(response.evidenceTypes.isEmpty())
    }

    // ----- downloadEvidence tests -----

    @Test
    fun `downloadEvidence returns raw evidence payload`() {
        val payload = mapOf("result" to "Passed", "reportUrl" to "s3://bucket/report.pdf")
        whenever(
            secureStore.retrieveEvidence("software_release", "release-v1.0.0", "security_scan")
        ).thenReturn(payload)

        val result = service.downloadEvidence("software_release", "release-v1.0.0", "security_scan")

        assertEquals(payload, result)
    }

    @Test
    fun `downloadEvidence throws NotFoundException when evidence does not exist`() {
        whenever(secureStore.retrieveEvidence(any(), any(), any())).thenReturn(null)

        assertThrows(NotFoundException::class.java) {
            service.downloadEvidence("trail", "abc", "security_scan")
        }
    }

    // ----- deleteEvidence tests -----

    @Test
    fun `deleteEvidence delegates soft-delete to secureStore`() {
        service.deleteEvidence("trail", "abc-123", "approval")

        verify(secureStore).deleteEvidence("trail", "abc-123", "approval")
    }

    // ----- getHealth tests -----

    @Test
    fun `getHealth returns healthy status when Vault is reachable`() {
        whenever(secureStore.isHealthy()).thenReturn(true)

        val health = service.getHealth()

        assertTrue(health.healthy)
        assertEquals("http://localhost:8200", health.vaultUri)
        assertEquals("TOKEN", health.authMethod)
        assertNotNull(health.message)
    }

    @Test
    fun `getHealth returns unhealthy status when Vault is unreachable`() {
        whenever(secureStore.isHealthy()).thenReturn(false)

        val health = service.getHealth()

        assertFalse(health.healthy)
    }
}

