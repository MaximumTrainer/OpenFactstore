package com.factstore.policy

import com.factstore.application.ArtifactService
import com.factstore.application.AssertService
import com.factstore.application.AttestationService
import com.factstore.application.FlowService
import com.factstore.application.HubService
import com.factstore.application.OrgTemplateService
import com.factstore.application.PolicyService
import com.factstore.application.policy.PolicyExpressionEvaluator
import com.factstore.application.template.TemplateParser
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.port.inbound.ITrailService
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.dto.AssertRequest
import com.factstore.dto.ComplianceStatus
import com.factstore.dto.CreateArtifactRequest
import com.factstore.dto.CreateAttestationRequest
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.CreateOrgTemplateRequest
import com.factstore.dto.CreatePolicyRequest
import com.factstore.dto.CreateTrailRequest
import com.factstore.dto.UpdatePolicyRequest
import com.factstore.dto.UpdateFlowRequest
import com.factstore.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * #241: an `if:` condition that cannot be evaluated must never silently drop a required
 * attestation. Invalid conditions are refused at upload, and a stored condition that still
 * fails at assert time makes its attestation required.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConditionFailClosedTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var flowRepository: IFlowRepository
    @Autowired lateinit var trailService: ITrailService
    @Autowired lateinit var attestationService: AttestationService
    @Autowired lateinit var artifactService: ArtifactService
    @Autowired lateinit var assertService: AssertService
    @Autowired lateinit var policyService: PolicyService
    @Autowired lateinit var orgTemplateService: OrgTemplateService
    @Autowired lateinit var hubService: HubService

    private val typoTemplate = """
        version: 1
        trail:
          attestations:
            - name: unit-tests
              type: junit
            - name: security-scan
              type: snyk
              if: 'flow.nmae == "payments"'
    """.trimIndent()

    private val brokenTemplate = """
        version: 1
        trail:
          attestations:
            - name: security-scan
              type: snyk
              if: 'flow.name == "payments'
    """.trimIndent()

    private fun unique(prefix: String) = "$prefix-${System.nanoTime()}"

    @Test
    fun `creating a flow with an invalid condition is refused`() {
        val e = assertThrows<BadRequestException> {
            flowService.createFlow(CreateFlowRequest(unique("f"), "d", emptyList(), templateYaml = typoTemplate))
        }
        assertTrue(e.message!!.contains("security-scan"), e.message)
        assertTrue(e.message!!.contains("flow.nmae"), e.message)
    }

    @Test
    fun `updating a flow with an invalid condition is refused`() {
        val flow = flowService.createFlow(CreateFlowRequest(unique("f"), "d", listOf("junit")))
        assertThrows<BadRequestException> {
            flowService.updateFlow(flow.id, UpdateFlowRequest(templateYaml = brokenTemplate))
        }
    }

    @Test
    fun `creating a trail with an invalid template override is refused`() {
        val flow = flowService.createFlow(CreateFlowRequest(unique("f"), "d", listOf("junit")))
        assertThrows<BadRequestException> {
            trailService.createTrail(
                CreateTrailRequest(
                    flowId = flow.id, gitCommitSha = "c", gitBranch = "main",
                    gitAuthor = "a", gitAuthorEmail = "a@example.com", templateYaml = brokenTemplate
                )
            )
        }
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(authorities = ["SCOPE_flows:write"])
    fun `POST flows with an invalid condition returns 400 naming the rule`() {
        val body = mapOf("name" to unique("f"), "description" to "d", "templateYaml" to typoTemplate)
        mockMvc.post("/api/v1/flows") {
            contentType = MediaType.APPLICATION_JSON
            content = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value(org.hamcrest.Matchers.containsString("security-scan")) }
        }
    }

    @Test
    fun `publishing an org template with an invalid condition is refused`() {
        assertThrows<BadRequestException> {
            orgTemplateService.create(
                CreateOrgTemplateRequest(templateId = unique("t"), name = "t", templateYaml = typoTemplate)
            )
        }
    }

    private fun policyYaml(condition: String) = """
        rules: []
        artifacts:
          attestations:
            - name: security-scan
              type: snyk
              if: '$condition'
    """.trimIndent()

    @Test
    fun `creating or updating a policy with an invalid condition is refused`() {
        val e = assertThrows<BadRequestException> {
            policyService.createPolicy(CreatePolicyRequest(unique("p"), policyYaml = policyYaml("flow.nmae == \"x\"")))
        }
        assertTrue(e.message!!.contains("flow.nmae"), e.message)

        val ok = policyService.createPolicy(CreatePolicyRequest(unique("p"), policyYaml = policyYaml("flow.name == \"x\"")))
        assertThrows<BadRequestException> {
            policyService.updatePolicy(ok.id, UpdatePolicyRequest(policyYaml = policyYaml("flow.name == \"x")))
        }
    }

    @Test
    fun `a stored condition that fails to evaluate makes the attestation required and is reported`() {
        // Legacy data: written before upload validation existed, so it bypasses the service.
        val flow = flowService.createFlow(CreateFlowRequest(unique("legacy"), "d", emptyList()))
        val entity = flowRepository.findById(flow.id)!!
        entity.templateYaml = typoTemplate
        flowRepository.save(entity)

        val trail = trailService.createTrail(
            CreateTrailRequest(flowId = flow.id, gitCommitSha = "c", gitBranch = "main", gitAuthor = "a", gitAuthorEmail = "a@example.com")
        )
        attestationService.recordAttestation(trail.id, CreateAttestationRequest(type = "junit", name = "unit-tests", status = AttestationStatus.PASSED))
        val digest = "sha256:" + (UUID.randomUUID().toString() + UUID.randomUUID().toString()).replace("-", "")
        artifactService.reportArtifact(trail.id, CreateArtifactRequest(imageName = "img", imageTag = "1", sha256Digest = digest, reportedBy = "ci"))

        val result = assertService.assertCompliance(AssertRequest(digest, flow.id))

        assertEquals(ComplianceStatus.NON_COMPLIANT, result.status)
        assertTrue("security-scan" in result.missingAttestationNames, result.missingAttestationNames.toString())
        assertTrue(result.details.contains("flow.nmae"), result.details)
        assertTrue(result.details.contains("security-scan"), result.details)
    }

    @Test
    fun `every built-in hub template passes condition validation`() {
        val parser = TemplateParser()
        val templates = hubService.listTemplates()
        assertTrue(templates.isNotEmpty())
        templates.forEach { parser.validate(it.yaml) }
        // And the evaluator still accepts the documented examples.
        val evaluator = PolicyExpressionEvaluator()
        listOf(
            """flow.tags["pci-scope"] == "true"""",
            """matches(artifact.name, "^backend.*")""",
            """flow.name == "payments-service"""",
            """exists(flow) and flow.name == "x"""",
            """flow.name in ["svc-a", "svc-b"]""",
            "not exists(flow)"
        ).forEach { evaluator.validate(it) }
    }
}

class TemplateComposerConditionTest {
    @Test
    fun `composing keeps an if condition containing quotes intact and valid`() {
        val parser = TemplateParser()
        val composer = com.factstore.application.TemplateComposer(parser)
        val condition = """flow.tags["pci-scope"] == "true" and flow.name != "it's""""
        val source = com.factstore.core.domain.HubTemplate(
            id = "t", name = "t", description = "", framework = "", version = "1",
            yaml = "version: 1\ntrail:\n  attestations:\n    - name: scan\n      type: snyk\n      if: '${condition.replace("'", "''")}'\n"
        )
        val composed = composer.compose(listOf(source)).templateYaml
        parser.validate(composed)
        assertEquals(condition, parser.parse(composed)!!.trailAttestations.single().ifCondition)
    }
}
