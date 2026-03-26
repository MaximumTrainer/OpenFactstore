package com.factstore.core.port.inbound.query

import com.factstore.dto.query.AttestationView
import java.util.UUID

interface IAttestationQueryHandler {
    fun listAttestations(trailId: UUID): List<AttestationView>
}
