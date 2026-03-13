package com.factstore.core.port.inbound

import com.factstore.dto.CreateSsoConfigRequest
import com.factstore.dto.SsoCallbackResponse
import com.factstore.dto.SsoConfigResponse
import com.factstore.dto.SsoLoginUrlResponse
import com.factstore.dto.SsoTestConnectionResponse
import com.factstore.dto.UpdateSsoConfigRequest

interface ISsoConfigService {
    fun createSsoConfig(orgSlug: String, request: CreateSsoConfigRequest): SsoConfigResponse
    fun getSsoConfig(orgSlug: String): SsoConfigResponse
    fun updateSsoConfig(orgSlug: String, request: UpdateSsoConfigRequest): SsoConfigResponse
    fun deleteSsoConfig(orgSlug: String)
    fun testSsoConnection(orgSlug: String): SsoTestConnectionResponse
    fun initiateSsoLogin(orgSlug: String, redirectUri: String): SsoLoginUrlResponse
    fun handleSsoCallback(orgSlug: String, code: String, state: String, redirectUri: String): SsoCallbackResponse
}
