package com.factstore.core.port.outbound

import com.factstore.core.domain.Fact
import java.util.UUID

interface IFactRepository {
    fun save(fact: Fact): Fact
    fun findById(factId: UUID): Fact?
    fun existsById(factId: UUID): Boolean
}
