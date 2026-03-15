package com.factstore.core.port.outbound

import com.factstore.core.domain.OidcJtiLog

interface IOidcJtiLogRepository {
    fun existsByJti(jti: String): Boolean
    fun save(log: OidcJtiLog): OidcJtiLog
}
