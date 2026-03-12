package com.factstore.core.port.inbound

import com.factstore.dto.*

interface IConfluenceIntegrationService {
    fun saveConfig(request: ConfluenceConfigRequest): ConfluenceConfigResponse
    fun getConfig(): ConfluenceConfigResponse
    fun testConnectivity(): ConnectionTestResponse
}
