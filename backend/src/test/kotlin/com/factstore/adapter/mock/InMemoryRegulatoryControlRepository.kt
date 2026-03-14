package com.factstore.adapter.mock

import com.factstore.core.domain.RegulatoryControl
import com.factstore.core.port.outbound.IRegulatoryControlRepository
import java.util.UUID

class InMemoryRegulatoryControlRepository : IRegulatoryControlRepository {
    private val store = mutableMapOf<UUID, RegulatoryControl>()
    override fun save(control: RegulatoryControl): RegulatoryControl { store[control.id] = control; return control }
    override fun findById(id: UUID): RegulatoryControl? = store[id]
    override fun findByFrameworkId(frameworkId: UUID): List<RegulatoryControl> = store.values.filter { it.frameworkId == frameworkId }
}
