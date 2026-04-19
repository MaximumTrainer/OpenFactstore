package com.factstore.application.query

import com.factstore.core.port.inbound.query.IAttestationQueryHandler
import com.factstore.core.port.outbound.read.IAttestationReadRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.query.AttestationView
import com.factstore.exception.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class AttestationQueryHandler(
    private val attestationReadRepository: IAttestationReadRepository,
    private val trailRepository: ITrailRepository
) : IAttestationQueryHandler {

    override fun listAttestations(trailId: UUID): List<AttestationView> {
        if (!trailRepository.existsById(trailId)) throw NotFoundException("Trail not found: $trailId")
        return attestationReadRepository.findByTrailId(trailId)
    }
}
