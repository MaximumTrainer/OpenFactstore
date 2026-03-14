package com.factstore

import com.factstore.application.OpaService
import com.factstore.dto.EvaluatePolicyRequest
import com.factstore.dto.UploadBundleRequest
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class OpaPolicyServiceTest {

    @Autowired
    lateinit var opaService: OpaService

    @Test
    fun `upload bundle saves as INACTIVE`() {
        val req = UploadBundleRequest(
            name = "test-bundle",
            version = "1.0.0",
            regoContent = "package compliance\ndefault allow = false"
        )
        val resp = opaService.uploadBundle(req)
        assertEquals("test-bundle", resp.name)
        assertEquals("1.0.0", resp.version)
        assertEquals(com.factstore.core.domain.BundleStatus.INACTIVE, resp.status)
        assertNotNull(resp.id)
    }

    @Test
    fun `activate bundle changes status to ACTIVE`() {
        val uploaded = opaService.uploadBundle(
            UploadBundleRequest("activation-bundle", "1.0.0", "package compliance\ndefault allow = true")
        )
        assertEquals(com.factstore.core.domain.BundleStatus.INACTIVE, uploaded.status)

        val activated = opaService.activateBundle(uploaded.id)
        assertEquals(com.factstore.core.domain.BundleStatus.ACTIVE, activated.status)
    }

    @Test
    fun `activating a second bundle deactivates the first`() {
        val first = opaService.uploadBundle(UploadBundleRequest("bundle-a", "1.0.0", "package compliance\ndefault allow = true"))
        opaService.activateBundle(first.id)

        val second = opaService.uploadBundle(UploadBundleRequest("bundle-b", "1.0.0", "package compliance\ndefault allow = true"))
        opaService.activateBundle(second.id)

        val updatedFirst = opaService.getBundle(first.id)
        assertEquals(com.factstore.core.domain.BundleStatus.INACTIVE, updatedFirst.status)

        val updatedSecond = opaService.getBundle(second.id)
        assertEquals(com.factstore.core.domain.BundleStatus.ACTIVE, updatedSecond.status)
    }

    @Test
    fun `evaluate with active bundle persists policy decision`() {
        val bundle = opaService.uploadBundle(
            UploadBundleRequest("eval-bundle", "1.0.0", "package compliance\ndefault allow = true")
        )
        opaService.activateBundle(bundle.id)

        val result = opaService.evaluatePolicy(
            EvaluatePolicyRequest(
                artifactName = "my-service",
                artifactVersion = "1.2.3",
                environment = "production",
                approvalStatus = "APPROVED"
            )
        )

        assertTrue(result.resultAllow)
        assertEquals(bundle.id, result.bundleId)
        assertTrue(result.denyReasons.isEmpty())

        val fetched = opaService.getDecision(result.id)
        assertEquals(result.id, fetched.id)
    }

    @Test
    fun `evaluate with approval gate denies when not APPROVED`() {
        val bundle = opaService.uploadBundle(
            UploadBundleRequest(
                name = "approval-gate",
                version = "1.0.0",
                regoContent = """
                    package compliance
                    default allow = false
                    allow { input.approvalStatus == "APPROVED" }
                """.trimIndent()
            )
        )
        opaService.activateBundle(bundle.id)

        val result = opaService.evaluatePolicy(
            EvaluatePolicyRequest(
                artifactName = "my-service",
                artifactVersion = "1.0.0",
                approvalStatus = "PENDING"
            )
        )

        assertFalse(result.resultAllow)
        assertTrue(result.denyReasons.isNotEmpty())
    }

    @Test
    fun `evaluate with no active bundle throws NotFoundException`() {
        assertThrows<NotFoundException> {
            opaService.evaluatePolicy(
                EvaluatePolicyRequest(artifactName = "my-service")
            )
        }
    }

    @Test
    fun `list decisions returns all persisted decisions`() {
        val bundle = opaService.uploadBundle(
            UploadBundleRequest("list-test-bundle", "1.0.0", "package compliance\ndefault allow = true")
        )
        opaService.activateBundle(bundle.id)

        opaService.evaluatePolicy(EvaluatePolicyRequest(artifactName = "svc-a"))
        opaService.evaluatePolicy(EvaluatePolicyRequest(artifactName = "svc-b"))

        val decisions = opaService.listDecisions()
        assertTrue(decisions.size >= 2)
    }

    @Test
    fun `get decision by unknown id throws NotFoundException`() {
        assertThrows<NotFoundException> {
            opaService.getDecision(UUID.randomUUID())
        }
    }

    @Test
    fun `activate bundle by unknown id throws NotFoundException`() {
        assertThrows<NotFoundException> {
            opaService.activateBundle(UUID.randomUUID())
        }
    }
}
