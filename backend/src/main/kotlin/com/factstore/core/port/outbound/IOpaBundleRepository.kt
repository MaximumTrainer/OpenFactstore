package com.factstore.core.port.outbound

import com.factstore.core.domain.OpaBundle
import java.util.UUID

interface IOpaBundleRepository {
    fun save(bundle: OpaBundle): OpaBundle
    fun findById(id: UUID): OpaBundle?
    fun findAll(): List<OpaBundle>
    fun findActive(): OpaBundle?
}
