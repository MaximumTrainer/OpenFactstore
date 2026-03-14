package com.factstore.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "vault")
data class VaultProperties(
    val enabled: Boolean = false,
    val uri: String = "http://localhost:8200",
    val authentication: AuthMethod = AuthMethod.TOKEN,
    val token: String = "",
    val appRole: AppRoleProperties = AppRoleProperties(),
    val kv: KvProperties = KvProperties(),
    val tls: TlsProperties = TlsProperties()
) {
    enum class AuthMethod { TOKEN, APPROLE, KUBERNETES }

    data class AppRoleProperties(
        val roleId: String = "",
        val secretId: String = ""
    )

    data class KvProperties(
        val enabled: Boolean = true,
        val backend: String = "secret",
        val defaultContext: String = "factstore"
    )

    data class TlsProperties(
        val enabled: Boolean = false,
        val keyStorePath: String = "",
        val keyStorePassword: String = "",
        val trustStorePath: String = "",
        val trustStorePassword: String = ""
    )
}
