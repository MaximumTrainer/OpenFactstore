package com.factstore.repository

import com.factstore.domain.Flow
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface FlowRepository : JpaRepository<Flow, UUID> {
    fun findByName(name: String): Flow?
    fun existsByName(name: String): Boolean
}
