package com.factstore.core.port.inbound

import com.factstore.core.domain.ScmProvider
import com.factstore.dto.CreateScmIntegrationRequest
import com.factstore.dto.ScmIntegrationResponse

interface IScmIntegrationService {
    fun createIntegration(orgSlug: String, request: CreateScmIntegrationRequest): ScmIntegrationResponse
    fun listIntegrations(orgSlug: String): List<ScmIntegrationResponse>
    fun deleteIntegration(orgSlug: String, provider: ScmProvider)
}
