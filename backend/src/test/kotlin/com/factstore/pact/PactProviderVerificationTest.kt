package com.factstore.pact

import au.com.dius.pact.provider.junit5.HttpTestTarget
import au.com.dius.pact.provider.junit5.PactVerificationContext
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider
import au.com.dius.pact.provider.junitsupport.Provider
import au.com.dius.pact.provider.junitsupport.State
import au.com.dius.pact.provider.junitsupport.loader.PactFolder
import com.factstore.adapter.outbound.persistence.FlowRepositoryJpa
import com.factstore.adapter.outbound.persistence.TrailRepositoryJpa
import com.factstore.core.domain.Flow
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
@Provider("factstore-backend")
@PactFolder("../pacts")
class PactProviderVerificationTest {

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var flowRepository: FlowRepositoryJpa

    @Autowired
    lateinit var trailRepository: TrailRepositoryJpa

    @BeforeEach
    fun setup(context: PactVerificationContext) {
        context.target = HttpTestTarget("localhost", port)
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider::class)
    fun pactVerificationTestTemplate(context: PactVerificationContext) {
        context.verifyInteraction()
    }

    @State("flows exist")
    fun setupFlowsExist() {
        trailRepository.deleteAll()
        flowRepository.deleteAll()
        val flow = Flow(name = "test-flow", description = "A test flow")
        flow.requiredAttestationTypes = listOf("junit")
        flowRepository.save(flow)
    }

    @State("no state needed")
    fun setupNoState() {
        trailRepository.deleteAll()
        flowRepository.deleteAll()
    }

    @State("a flow with id 7f3f2b99-0000-0000-0000-000000000001 exists")
    fun setupFlowWithKnownId() {
        trailRepository.deleteAll()
        flowRepository.deleteAll()
        val flow = Flow(
            id = UUID.fromString("7f3f2b99-0000-0000-0000-000000000001"),
            name = "compliance-flow",
            description = "A flow for compliance checks"
        )
        flowRepository.save(flow)
    }
}
