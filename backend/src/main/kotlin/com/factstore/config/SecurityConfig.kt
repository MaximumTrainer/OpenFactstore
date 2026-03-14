package com.factstore.config

import com.factstore.adapter.inbound.web.ApiKeyAuthFilter
import com.factstore.adapter.inbound.web.SsoJwtAuthFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Central security configuration for OpenFactstore.
 *
 * Access model:
 *  - REST API callers authenticate via API keys (header `X-API-Key` or `Authorization: ApiKey <key>`).
 *  - SSO users authenticate via the JWT returned by the `/sso/callback` endpoint
 *    (`Authorization: Bearer <jwt>`).
 *  - Web UI users authenticate via GitHub OAuth 2.0 / SSO (enabled when
 *    `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET` environment variables are set).
 *  - Public paths (Swagger UI, H2 console, OpenAPI docs) are permitted without authentication.
 *
 * HTTPS enforcement: configure `server.ssl.*` properties and set
 * `server.ssl.enabled=true` in your production `application.yml` (or via environment
 * variables). All traffic must be over TLS in production deployments.
 *
 * The [BCryptPasswordEncoder] bean lives in [PasswordEncoderConfig] to avoid a circular
 * dependency with [ApiKeyAuthFilter].
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val apiKeyAuthFilter: ApiKeyAuthFilter,
    private val ssoJwtAuthFilter: SsoJwtAuthFilter,
    @Value("\${spring.security.oauth2.client.registration.github.client-id:}")
    private val githubClientId: String
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { csrf -> csrf.ignoringRequestMatchers("/api/v1/**") }
            // Allow H2 console frames (dev only)
            .headers { headers -> headers.frameOptions { it.sameOrigin() } }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
            .authorizeHttpRequests { auth ->
                auth
                    // H2 console (dev)
                    .requestMatchers("/h2-console/**").permitAll()
                    // OpenAPI / Swagger
                    .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    // OAuth2 login endpoints
                    .requestMatchers("/login/**", "/oauth2/**").permitAll()
                    // SSO login/callback do not require prior authentication.
                    .requestMatchers("/api/v1/organisations/*/sso/login", "/api/v1/organisations/*/sso/callback").permitAll()
                    // All other endpoints: permitted (API key auth is additive / optional).
                    // Tighten to .authenticated() in production to enforce authentication.
                    .anyRequest().permitAll()
            }
            // SSO JWT filter runs first so that JWT-authenticated users are recognised
            // before the API key filter tries to treat the Bearer token as an API key.
            .addFilterBefore(ssoJwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(apiKeyAuthFilter, SsoJwtAuthFilter::class.java)

        // Enable GitHub OAuth2 login only when credentials are configured
        if (githubClientId.isNotBlank()) {
            http.oauth2Login { }
        }

        return http.build()
    }
}
