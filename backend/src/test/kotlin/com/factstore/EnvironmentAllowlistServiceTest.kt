package com.factstore

import com.factstore.application.EnvironmentAllowlistService
import com.factstore.core.domain.AllowlistEntryStatus
import com.factstore.core.domain.EnvironmentType
import com.factstore.dto.CreateAllowlistEntryRequest
import com.factstore.dto.CreateEnvironmentRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
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
class EnvironmentAllowlistServiceTest {

    @Autowired
    lateinit var allowlistService: EnvironmentAllowlistService

    @Autowired
    lateinit var environmentService: com.factstore.application.EnvironmentService

    private fun createEnv(name: String = "test-env-${UUID.randomUUID()}") =
        environmentService.createEnvironment(CreateEnvironmentRequest(name, EnvironmentType.K8S))

    @Test
    fun `addEntry succeeds with sha256`() {
        val env = createEnv()
        val resp = allowlistService.addEntry(
            env.id,
            CreateAllowlistEntryRequest(sha256 = "abc123", reason = "approved vendor", approvedBy = "security-team")
        )
        assertEquals(env.id, resp.environmentId)
        assertEquals("abc123", resp.sha256)
        assertNull(resp.namePattern)
        assertEquals(AllowlistEntryStatus.ACTIVE, resp.status)
        assertTrue(resp.isEffective)
    }

    @Test
    fun `addEntry succeeds with namePattern`() {
        val env = createEnv()
        val resp = allowlistService.addEntry(
            env.id,
            CreateAllowlistEntryRequest(namePattern = "^vendor/.*", reason = "vendor images", approvedBy = "ops")
        )
        assertEquals("^vendor/.*", resp.namePattern)
        assertNull(resp.sha256)
        assertTrue(resp.isEffective)
    }

    @Test
    fun `addEntry fails when neither sha256 nor namePattern provided`() {
        val env = createEnv()
        assertThrows<IllegalArgumentException> {
            allowlistService.addEntry(
                env.id,
                CreateAllowlistEntryRequest(reason = "no identifier", approvedBy = "ops")
            )
        }
    }

    @Test
    fun `addEntry fails for unknown environment`() {
        assertThrows<NotFoundException> {
            allowlistService.addEntry(
                UUID.randomUUID(),
                CreateAllowlistEntryRequest(sha256 = "abc", reason = "test", approvedBy = "ops")
            )
        }
    }

    @Test
    fun `removeEntry marks entry as REMOVED`() {
        val env = createEnv()
        val entry = allowlistService.addEntry(
            env.id,
            CreateAllowlistEntryRequest(sha256 = "removable", reason = "temp", approvedBy = "ops")
        )
        val removed = allowlistService.removeEntry(env.id, entry.id)
        assertEquals(AllowlistEntryStatus.REMOVED, removed.status)
        assertFalse(removed.isEffective)
    }

    @Test
    fun `removeEntry twice throws ConflictException`() {
        val env = createEnv()
        val entry = allowlistService.addEntry(
            env.id,
            CreateAllowlistEntryRequest(sha256 = "double-remove", reason = "temp", approvedBy = "ops")
        )
        allowlistService.removeEntry(env.id, entry.id)
        assertThrows<ConflictException> {
            allowlistService.removeEntry(env.id, entry.id)
        }
    }

    @Test
    fun `isAllowlisted returns true for exact sha256 match`() {
        val env = createEnv()
        allowlistService.addEntry(
            env.id,
            CreateAllowlistEntryRequest(sha256 = "sha256:exact", reason = "known image", approvedBy = "ops")
        )
        assertTrue(allowlistService.isAllowlisted(env.id, sha256 = "sha256:exact"))
    }

    @Test
    fun `isAllowlisted returns true for name pattern match`() {
        val env = createEnv()
        allowlistService.addEntry(
            env.id,
            CreateAllowlistEntryRequest(namePattern = "^myregistry/.*", reason = "internal registry", approvedBy = "ops")
        )
        assertTrue(allowlistService.isAllowlisted(env.id, artifactName = "myregistry/myimage:latest"))
    }

    @Test
    fun `isAllowlisted returns false for expired entry`() {
        val env = createEnv()
        allowlistService.addEntry(
            env.id,
            CreateAllowlistEntryRequest(
                sha256 = "expired-sha",
                reason = "temp approval",
                approvedBy = "ops",
                expiresAt = Instant.now().minusSeconds(3600)
            )
        )
        assertFalse(allowlistService.isAllowlisted(env.id, sha256 = "expired-sha"))
    }

    @Test
    fun `isAllowlisted returns false when no match`() {
        val env = createEnv()
        allowlistService.addEntry(
            env.id,
            CreateAllowlistEntryRequest(sha256 = "known-sha", reason = "approved", approvedBy = "ops")
        )
        assertFalse(allowlistService.isAllowlisted(env.id, sha256 = "unknown-sha"))
    }

    @Test
    fun `listEntries returns all entries including removed`() {
        val env = createEnv()
        val e1 = allowlistService.addEntry(
            env.id,
            CreateAllowlistEntryRequest(sha256 = "sha-1", reason = "r1", approvedBy = "ops")
        )
        val e2 = allowlistService.addEntry(
            env.id,
            CreateAllowlistEntryRequest(namePattern = "pattern.*", reason = "r2", approvedBy = "ops")
        )
        allowlistService.removeEntry(env.id, e1.id)
        val entries = allowlistService.listEntries(env.id)
        assertEquals(2, entries.size)
        assertTrue(entries.any { it.id == e1.id && it.status == AllowlistEntryStatus.REMOVED })
        assertTrue(entries.any { it.id == e2.id && it.status == AllowlistEntryStatus.ACTIVE })
    }
}
