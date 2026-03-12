package com.factstore.core.port.outbound

import com.factstore.core.domain.JiraTicket
import java.util.UUID

interface IJiraTicketRepository {
    fun save(ticket: JiraTicket): JiraTicket
    fun findAll(): List<JiraTicket>
    fun findByTrailId(trailId: UUID): List<JiraTicket>
    fun findById(id: UUID): JiraTicket?
}
