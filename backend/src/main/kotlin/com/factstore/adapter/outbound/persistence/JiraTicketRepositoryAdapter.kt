package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.JiraTicket
import com.factstore.core.port.outbound.IJiraTicketRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface JiraTicketRepositoryJpa : JpaRepository<JiraTicket, UUID> {
    fun findByTrailId(trailId: UUID): List<JiraTicket>
}

@Component
class JiraTicketRepositoryAdapter(private val jpa: JiraTicketRepositoryJpa) : IJiraTicketRepository {
    override fun save(ticket: JiraTicket): JiraTicket = jpa.save(ticket)
    override fun findAll(): List<JiraTicket> = jpa.findAll()
    override fun findByTrailId(trailId: UUID): List<JiraTicket> = jpa.findByTrailId(trailId)
    override fun findById(id: UUID): JiraTicket? = jpa.findById(id).orElse(null)
}
