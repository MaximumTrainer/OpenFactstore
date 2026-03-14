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

    /**
     * Handles the OIDC authorization-code callback from the identity provider.
     * The redirect URI used in the token exchange is the one that was stored in the
     * pending state when [initiateSsoLogin] was called, ensuring an exact match.
     */
    fun handleSsoCallback(orgSlug: String, code: String, state: String): SsoCallbackResponse

    /**
     * Returns true when SSO is configured for [orgSlug] **and** is marked as mandatory.
     *
     * Callers (e.g., a future password-login endpoint) should check this flag and deny
     * non-SSO authentication attempts when it is `true`.
     */
    fun isSsoMandatory(orgSlug: String): Boolean
}
