package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "security_thresholds")
class SecurityThreshold(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "flow_id", nullable = false) val flowId: UUID,
    @Column(name = "max_critical") var maxCritical: Int = 0,
    @Column(name = "max_high") var maxHigh: Int = 0,
    @Column(name = "max_medium") var maxMedium: Int = 10,
    @Column(name = "max_low") var maxLow: Int = Int.MAX_VALUE,
    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now()
)
