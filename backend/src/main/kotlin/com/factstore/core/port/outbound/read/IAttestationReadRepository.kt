package com.factstore.core.port.outbound.read

import com.factstore.dto.query.AttestationView
import java.util.UUID

interface IAttestationReadRepository {
    fun findByTrailId(trailId: UUID): List<AttestationView>
}
