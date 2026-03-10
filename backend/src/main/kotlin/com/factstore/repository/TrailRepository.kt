package com.factstore.repository

import com.factstore.domain.Trail
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TrailRepository : JpaRepository<Trail, UUID> {
    fun findByFlowId(flowId: UUID): List<Trail>
}
