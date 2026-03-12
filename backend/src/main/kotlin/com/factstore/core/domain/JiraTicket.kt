package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "jira_tickets")
class JiraTicket(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "ticket_key", nullable = false)
    var ticketKey: String,

    @Column(name = "summary", nullable = false, length = 512)
    var summary: String,

    @Column(name = "status", nullable = false)
    var status: String,

    @Column(name = "issue_type", nullable = false)
    var issueType: String,

    @Column(name = "trail_id", nullable = true)
    var trailId: UUID? = null,

    @Column(name = "project_key", nullable = false)
    var projectKey: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
