package com.factstore.core.port.outbound

import com.factstore.core.domain.SsoConfig

interface ISsoConfigRepository {
    fun save(ssoConfig: SsoConfig): SsoConfig
    fun findByOrgSlug(orgSlug: String): SsoConfig?
    fun existsByOrgSlug(orgSlug: String): Boolean
    fun delete(ssoConfig: SsoConfig)
}
