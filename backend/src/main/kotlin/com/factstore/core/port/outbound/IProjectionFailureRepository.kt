package com.factstore.core.port.outbound

import com.factstore.core.domain.ProjectionFailure

interface IProjectionFailureRepository {
    fun save(failure: ProjectionFailure): ProjectionFailure
    fun findBySequenceNumber(sequenceNumber: Long): ProjectionFailure?
}
