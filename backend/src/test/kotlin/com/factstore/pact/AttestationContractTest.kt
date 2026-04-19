package com.factstore.pact

import au.com.dius.pact.provider.junit5.HttpTestTarget
import au.com.dius.pact.provider.junit5.PactVerificationContext
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider
import au.com.dius.pact.provider.junitsupport.Provider
import au.com.dius.pact.provider.junitsupport.State
import au.com.dius.pact.provider.junitsupport.loader.PactFolder
import com.factstore.adapter.outbound.persistence.AttestationRepositoryJpa
import com.factstore.adapter.outbound.persistence.FlowRepositoryJpa
import com.factstore.adapter.outbound.persistence.OrganisationRepositoryJpa
import com.factstore.adapter.outbound.persistence.TrailRepositoryJpa
import com.factstore.core.domain.Flow
import com.factstore.core.domain.Organisation
import com.factstore.core.domain.Trail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.UUID

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Provider("factstore")
@PactFolder("../pacts")
class AttestationContractTest {

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var attestationRepository: AttestationRepositoryJpa

    @Autowired
    lateinit var flowRepository: FlowRepositoryJpa

    @Autowired
    lateinit var trailRepository: TrailRepositoryJpa

    @Autowired
    lateinit var organisationRepository: OrganisationRepositoryJpa

    @BeforeEach
    fun setup(context: PactVerificationContext) {
        context.target = HttpTestTarget("localhost", port)
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider::class)
    fun pactVerificationTestTemplate(context: PactVerificationContext) {
        context.verifyInteraction()
    }

    @State("a trail with id aabbccdd-0000-0000-0000-000000000001 exists")
    fun setupTrailWithKnownId() {
        attestationRepository.deleteAll()
        trailRepository.deleteAll()
        flowRepository.deleteAll()
        organisationRepository.deleteAll()
        val org = organisationRepository.save(Organisation(slug = "my-org", name = "My Org"))
        val flow = Flow(name = "my-flow", description = "contract test flow", orgSlug = org.slug)
        val savedFlow = flowRepository.save(flow)
        val trail = Trail(
            id = UUID.fromString("aabbccdd-0000-0000-0000-000000000001"),
            flowId = savedFlow.id,
            gitCommitSha = "abc123",
            gitBranch = "main",
            gitAuthor = "test",
            gitAuthorEmail = "test@example.com"
        )
        trailRepository.save(trail)
    }
}
