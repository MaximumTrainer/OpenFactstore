package com.factstore

import com.factstore.application.AuditPackageService
import com.factstore.application.AttestationService
import com.factstore.application.ArtifactService
import com.factstore.application.EvidenceVaultService
import com.factstore.application.FlowService
import com.factstore.application.TrailService
import com.factstore.core.domain.AttestationStatus
import com.factstore.dto.CreateArtifactRequest
import com.factstore.dto.CreateAttestationRequest
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.CreateTrailRequest
import com.factstore.dto.TrailResponse
import com.factstore.exception.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.GZIPInputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@SpringBootTest
@Transactional
class AuditPackageServiceTest {

    @Autowired lateinit var auditPackageService: AuditPackageService
    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var trailService: TrailService
    @Autowired lateinit var attestationService: AttestationService
    @Autowired lateinit var artifactService: ArtifactService
    @Autowired lateinit var evidenceVaultService: EvidenceVaultService
    @Autowired lateinit var objectMapper: ObjectMapper

    @Value("\${audit.hmac-secret}")
    lateinit var hmacSecret: String

    private fun createTrail(): TrailResponse {
        val flow = flowService.createFlow(CreateFlowRequest("audit-flow-${System.nanoTime()}", "desc", listOf("junit")))
        return trailService.createTrail(
            CreateTrailRequest(
                flowId = flow.id,
                gitCommitSha = "abc${System.nanoTime()}",
                gitBranch = "main",
                gitAuthor = "tester",
                gitAuthorEmail = "tester@example.com"
            )
        )
    }

    /** Decompresses and reads all tar entries from a tar.gz byte array. */
    private fun readTarEntries(bytes: ByteArray): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        GZIPInputStream(ByteArrayInputStream(bytes)).use { gzip ->
            TarArchiveInputStream(gzip).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val baos = ByteArrayOutputStream()
                        tar.copyTo(baos)
                        result[entry.name] = baos.toByteArray()
                    }
                    entry = tar.nextEntry
                }
            }
        }
        return result
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun computeHmac(data: ByteArray, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data).joinToString("") { "%02x".format(it) }
    }

    // ---- buildForTrail tests ----

    @Test
    fun `buildForTrail returns valid tar-gz archive`() {
        val trail = createTrail()
        val bytes = auditPackageService.buildForTrail(trail.id)
        assertTrue(bytes.isNotEmpty(), "Archive must not be empty")

        val entries = readTarEntries(bytes)
        assertTrue(entries.containsKey("manifest.json"), "Must contain manifest.json")
        assertTrue(entries.containsKey("trail.json"), "Must contain trail.json")
        assertTrue(entries.containsKey("flow.json"), "Must contain flow.json")
    }

    @Test
    fun `buildForTrail manifest lists all content files with correct sha256`() {
        val trail = createTrail()
        val att = attestationService.recordAttestation(trail.id, CreateAttestationRequest("junit", AttestationStatus.PASSED))
        evidenceVaultService.store(att.id, "report.txt", "text/plain", "evidence content".toByteArray())

        val bytes = auditPackageService.buildForTrail(trail.id)
        val entries = readTarEntries(bytes)

        val manifest = objectMapper.readTree(entries["manifest.json"])
        val files = manifest.get("files")
        assertNotNull(files, "Manifest must have 'files' array")

        // Verify each file in the manifest has the correct SHA-256
        files.forEach { fileNode ->
            val path = fileNode.get("path").asText()
            val expectedSha = fileNode.get("sha256").asText()
            val actualBytes = entries[path]
            assertNotNull(actualBytes, "Archive must contain file: $path")
            assertEquals(expectedSha, sha256Hex(actualBytes!!), "SHA-256 mismatch for $path")
        }
    }

    @Test
    fun `buildForTrail manifest HMAC is verifiable`() {
        val trail = createTrail()
        val bytes = auditPackageService.buildForTrail(trail.id)
        val entries = readTarEntries(bytes)

        val manifest = objectMapper.readTree(entries["manifest.json"])
        val storedHmac = manifest.get("hmacSha256").asText()
        val filesNode = manifest.get("files")

        // Re-compute HMAC over the JSON-serialised files array
        val filesJson = objectMapper.writeValueAsBytes(
            objectMapper.treeToValue(filesNode, Array<Any>::class.java)
        )
        val expectedHmac = computeHmac(filesJson, hmacSecret)
        assertEquals(expectedHmac, storedHmac, "Manifest HMAC must be verifiable with the server secret")
    }

    @Test
    fun `buildForTrail includes attestation and evidence files`() {
        val trail = createTrail()
        val att = attestationService.recordAttestation(trail.id, CreateAttestationRequest("junit", AttestationStatus.PASSED))
        val content = "evidence bytes".toByteArray()
        evidenceVaultService.store(att.id, "result.bin", "application/octet-stream", content)

        val bytes = auditPackageService.buildForTrail(trail.id)
        val entries = readTarEntries(bytes)

        // attestation JSON file
        assertTrue(entries.keys.any { it.startsWith("attestations/") }, "Must contain attestation files")
        // evidence binary (named by sha256)
        val expectedHash = sha256Hex(content)
        assertTrue(
            entries.keys.any { it.startsWith("evidence/") && it.contains(expectedHash) },
            "Must contain evidence file named by sha256"
        )
    }

    @Test
    fun `buildForTrail includes artifact file`() {
        val trail = createTrail()
        artifactService.reportArtifact(trail.id, CreateArtifactRequest("myimage", "v1.0", "sha256:abc123", reportedBy = "ci"))

        val bytes = auditPackageService.buildForTrail(trail.id)
        val entries = readTarEntries(bytes)

        assertTrue(entries.keys.any { it.startsWith("artifacts/") }, "Must contain artifact files")
    }

    @Test
    fun `buildForTrail throws NotFoundException for unknown trail`() {
        assertThrows<NotFoundException> {
            auditPackageService.buildForTrail(UUID.randomUUID())
        }
    }

    // ---- buildForArtifact tests ----

    @Test
    fun `buildForArtifact returns valid archive`() {
        val trail = createTrail()
        val artifact = artifactService.reportArtifact(
            trail.id, CreateArtifactRequest("img", "latest", "sha256:xyz", reportedBy = "ci")
        )

        val bytes = auditPackageService.buildForArtifact(artifact.id)
        val entries = readTarEntries(bytes)

        assertTrue(entries.containsKey("manifest.json"))
        assertTrue(entries.containsKey("trail.json"))
        assertTrue(entries.keys.any { it.startsWith("artifacts/") })
    }

    @Test
    fun `buildForArtifact throws NotFoundException for unknown artifact`() {
        assertThrows<NotFoundException> {
            auditPackageService.buildForArtifact(UUID.randomUUID())
        }
    }

    // ---- buildForAttestation tests ----

    @Test
    fun `buildForAttestation returns valid archive`() {
        val trail = createTrail()
        val att = attestationService.recordAttestation(trail.id, CreateAttestationRequest("junit", AttestationStatus.PASSED))

        val bytes = auditPackageService.buildForAttestation(att.id)
        val entries = readTarEntries(bytes)

        assertTrue(entries.containsKey("manifest.json"))
        assertTrue(entries.containsKey("trail.json"))
        assertTrue(entries.keys.any { it.startsWith("attestations/") })
    }

    @Test
    fun `buildForAttestation throws NotFoundException for unknown attestation`() {
        assertThrows<NotFoundException> {
            auditPackageService.buildForAttestation(UUID.randomUUID())
        }
    }

    // ---- external evidence reference test ----

    @Test
    fun `buildForTrail includes ref json for external evidence with externalUrl field`() {
        val trail = createTrail()
        val att = attestationService.recordAttestation(trail.id, CreateAttestationRequest("snyk", AttestationStatus.PASSED))
        val externalUrl = "https://ci.example.com/scan-results.html"
        evidenceVaultService.storeExternal(
            attestationId = att.id,
            fileName = "scan-results.html",
            contentType = "text/html",
            externalUrl = externalUrl,
            sha256Hash = "e".repeat(64),
            fileSizeBytes = 4096L
        )

        val bytes = auditPackageService.buildForTrail(trail.id)
        val entries = readTarEntries(bytes)

        val refEntry = entries.entries.firstOrNull { it.key.startsWith("evidence/") && it.key.endsWith(".ref.json") }
        assertNotNull(refEntry, "External evidence must produce a .ref.json descriptor in the archive")
        val refJson = objectMapper.readTree(refEntry!!.value)
        assertEquals("external-reference", refJson.get("type").asText())
        assertEquals(externalUrl, refJson.get("externalUrl").asText(), ".ref.json must include the externalUrl")
        assertEquals("scan-results.html", refJson.get("fileName").asText())
        assertEquals("e".repeat(64), refJson.get("sha256Hash").asText())
    }

    @Test
    fun `buildForTrail deduplicates evidence files with the same sha256`() {
        val trail = createTrail()
        val att1 = attestationService.recordAttestation(trail.id, CreateAttestationRequest("junit", AttestationStatus.PASSED))
        val att2 = attestationService.recordAttestation(trail.id, CreateAttestationRequest("snyk", AttestationStatus.PASSED))
        // Same content → same sha256 → should produce only one entry in the archive
        val content = "shared evidence content".toByteArray()
        evidenceVaultService.store(att1.id, "report.txt", "text/plain", content)
        evidenceVaultService.store(att2.id, "report.txt", "text/plain", content)

        val bytes = auditPackageService.buildForTrail(trail.id)
        val entries = readTarEntries(bytes)

        val evidencePaths = entries.keys.filter { it.startsWith("evidence/") }
        assertEquals(1, evidencePaths.size, "Duplicate sha256 evidence files must be deduplicated to a single archive entry")

        // Manifest must not list the same path twice
        val manifest = objectMapper.readTree(entries["manifest.json"])
        val manifestPaths = manifest.get("files").map { it.get("path").asText() }
        assertEquals(manifestPaths.size, manifestPaths.toSet().size, "Manifest must have no duplicate paths")
    }
}
