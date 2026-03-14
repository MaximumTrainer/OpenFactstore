package com.factstore.core.port.outbound

import com.factstore.core.domain.RegulatoryControl
import java.util.UUID

interface IRegulatoryControlRepository {
    fun save(control: RegulatoryControl): RegulatoryControl
    fun findById(id: UUID): RegulatoryControl?
    fun findByFrameworkId(frameworkId: UUID): List<RegulatoryControl>
}
