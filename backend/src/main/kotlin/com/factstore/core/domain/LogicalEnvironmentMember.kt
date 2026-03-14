package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "logical_environment_members",
    uniqueConstraints = [UniqueConstraint(columnNames = ["logical_env_id", "physical_env_id"])]
)
class LogicalEnvironmentMember(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "logical_env_id", nullable = false)
    val logicalEnvId: UUID,

    @Column(name = "physical_env_id", nullable = false)
    val physicalEnvId: UUID,

    @Column(name = "added_at", nullable = false)
    val addedAt: Instant = Instant.now()
)
