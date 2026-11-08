package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.ProjectionFailure
import com.factstore.core.port.outbound.IProjectionFailureRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ProjectionFailureJpa : JpaRepository<ProjectionFailure, UUID> {
    fun findFirstBySequenceNumber(sequenceNumber: Long): ProjectionFailure?
}

@Component
class ProjectionFailureRepositoryAdapter(private val jpa: ProjectionFailureJpa) : IProjectionFailureRepository {
    override fun save(failure: ProjectionFailure): ProjectionFailure = jpa.save(failure)
    override fun findBySequenceNumber(sequenceNumber: Long): ProjectionFailure? = jpa.findFirstBySequenceNumber(sequenceNumber)
}
