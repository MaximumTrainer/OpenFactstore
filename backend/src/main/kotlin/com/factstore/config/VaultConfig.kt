package com.factstore.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.vault.authentication.ClientAuthentication
import org.springframework.vault.authentication.AppRoleAuthentication
import org.springframework.vault.authentication.AppRoleAuthenticationOptions
import org.springframework.vault.authentication.TokenAuthentication
import org.springframework.vault.client.VaultEndpoint
import org.springframework.vault.client.VaultClients
import org.springframework.vault.core.VaultTemplate
import java.net.URI

/**
 * Spring configuration for HashiCorp Vault integration.
 *
 * Creates a [VaultTemplate] bean only when `vault.enabled=true`. The template
 * is configured according to the `vault.*` properties:
 * - **TOKEN** auth: uses a static root/service token (suitable for dev/local)
 * - **APPROLE** auth: uses role-id + secret-id (recommended for production)
 * - **KUBERNETES**: not yet supported — startup will fail with a clear error
 */
@Configuration
@ConditionalOnProperty(name = ["vault.enabled"], havingValue = "true")
class VaultConfig(private val vaultProperties: VaultProperties) {

    @Bean
    fun vaultEndpoint(): VaultEndpoint {
        return VaultEndpoint.from(URI.create(vaultProperties.uri))
    }

    @Bean
    fun clientAuthentication(vaultEndpoint: VaultEndpoint): ClientAuthentication {
        return when (vaultProperties.authentication) {
            VaultProperties.AuthMethod.APPROLE -> {
                val requestFactory = VaultClients.createRestTemplate().requestFactory!!
                val options = AppRoleAuthenticationOptions.builder()
                    .roleId(AppRoleAuthenticationOptions.RoleId.provided(vaultProperties.appRole.roleId))
                    .secretId(AppRoleAuthenticationOptions.SecretId.provided(vaultProperties.appRole.secretId))
                    .build()
                AppRoleAuthentication(options, VaultClients.createRestTemplate(vaultEndpoint, requestFactory))
            }
            VaultProperties.AuthMethod.TOKEN -> TokenAuthentication(vaultProperties.token)
            VaultProperties.AuthMethod.KUBERNETES ->
                throw IllegalStateException(
                    "Vault authentication method KUBERNETES is not yet supported. " +
                    "Use TOKEN or APPROLE (set vault.authentication accordingly)."
                )
        }
    }

    @Bean
    fun vaultTemplate(
        vaultEndpoint: VaultEndpoint,
        clientAuthentication: ClientAuthentication
    ): VaultTemplate {
        return VaultTemplate(vaultEndpoint, clientAuthentication)
    }
}

