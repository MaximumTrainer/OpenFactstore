package com.factstore.config

import com.factstore.adapter.inbound.web.ApiKeyAuthFilter
import com.factstore.adapter.inbound.web.GitHubSignInSuccessHandler
import com.factstore.adapter.inbound.web.BearerAuthenticationEntryPoint
import com.factstore.adapter.inbound.web.SessionAuthFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Central security configuration for OpenFactstore.
 *
 * Access model:
 *  - REST API callers authenticate via API keys (header `X-API-Key` or `Authorization: ApiKey <key>`).
 *  - People authenticate with a session issued by `/sso/callback`, presented as the
 *    `fs_session` HttpOnly cookie (web UI) or `Authorization: Bearer <token>` (API clients).
 *    See [SessionAuthFilter] and `docs/authentication.md`.
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
    private val sessionAuthFilter: SessionAuthFilter,
    private val gitHubSignInSuccessHandler: GitHubSignInSuccessHandler,
    private val bearerAuthenticationEntryPoint: BearerAuthenticationEntryPoint,
    @Value("\${spring.security.oauth2.client.registration.github.client-id:}")
    private val githubClientId: String,
    @Value("\${security.enforce-auth:false}")
    private val enforceAuth: Boolean,
    /**
     * Comma-separated origins permitted to call the API. Empty means none, which is the
     * correct default for an API the browser reaches through the same origin.
     */
    @Value("\${security.cors.allowed-origins:}")
    private val allowedOrigins: String
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { csrf -> csrf.ignoringRequestMatchers("/api/v1/**", "/api/v2/**") }
            .cors { cors -> cors.configurationSource(corsConfigurationSource()) }
            .headers { headers ->
                // The H2 console needs same-origin frames; nothing else does.
                headers.frameOptions { it.sameOrigin() }
                headers.contentTypeOptions { }
                headers.referrerPolicy { it.policy(ReferrerPolicy.SAME_ORIGIN) }
                if (enforceAuth) {
                    // Only meaningful once traffic is expected to be HTTPS.
                    headers.httpStrictTransportSecurity { hsts ->
                        hsts.includeSubDomains(true).maxAgeInSeconds(HSTS_MAX_AGE_SECONDS)
                    }
                }
            }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
            // An unauthenticated request is a 401 with a Bearer challenge, not Spring's default
            // bare 403 (#155 FR-2.4). An authenticated caller lacking a scope still gets 403.
            .exceptionHandling { it.authenticationEntryPoint(bearerAuthenticationEntryPoint) }
            .authorizeHttpRequests { auth ->
                auth
                if (!enforceAuth) {
                    // Development conveniences. With enforcement on they require a credential
                    // like anything else, rather than being a permanent open door (#155 FR-1.3).
                    auth.requestMatchers("/h2-console/**").permitAll()
                    auth.requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                }
                auth
                    // OAuth2 login endpoints
                    .requestMatchers("/login/**", "/oauth2/**").permitAll()
                    // Only liveness and readiness are public. /actuator/env, /metrics and
                    // /loggers expose configuration, traffic shape and log control, so they
                    // are not something to hand out unauthenticated (#155 FR-1.4).
                    .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/health")
                    .permitAll()
                    // Sign-in itself cannot require being signed in.
                    .requestMatchers("/api/v1/organisations/*/sso/login", "/api/v1/organisations/*/sso/callback").permitAll()
                    // Logout must work with an expired or already-revoked session, so the
                    // cookie can always be cleared.
                    .requestMatchers("/api/v1/auth/logout").permitAll()
                    // A client has to be able to ask whether this instance enforces
                    // authentication *before* it has a credential, or the UI cannot know
                    // whether to gate navigation. It discloses only that one fact.
                    .requestMatchers("/api/v1/auth/config").permitAll()
                    // Dev sign-in answers 404 unless switched on, refuses non-loopback callers,
                    // and cannot be switched on under the prod profile (#156 FR-8.4).
                    .requestMatchers("/api/v1/auth/dev-login").permitAll()
                    // Inbound hooks from GitHub/GitLab and Slack cannot present a Factstore
                    // credential. They are authenticated by the signature over the payload,
                    // which their handlers verify (#155 FR-4.1: marked @PublicEndpoint).
                    .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/*").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/organisations/*/slack/commands",
                        "/api/v1/organisations/*/slack/actions").permitAll()
                    // Remaining endpoints: enforce authentication when the flag is enabled.
                    // Set SECURITY_ENFORCE_AUTH=true in production to require authentication.
                if (enforceAuth) {
                    auth.anyRequest().authenticated()
                } else {
                    auth.anyRequest().permitAll()
                }
            }
            // The session filter runs first so a signed-in person is recognised before the
            // API key filter tries to read their Bearer token as an API key.
            .addFilterBefore(sessionAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(apiKeyAuthFilter, SessionAuthFilter::class.java)

        // Enable GitHub OAuth2 login only when credentials are configured
        if (githubClientId.isNotBlank()) {
            // A successful GitHub sign-in becomes a Factstore session, exactly like the OIDC
            // path (#156 FR-1.2). A failed or cancelled one returns to /login with a reason,
            // never with GitHub's error detail in the address bar (FR-1.4).
            http.oauth2Login { login ->
                login.successHandler(gitHubSignInSuccessHandler)
                login.failureHandler { _, response, _ -> response.sendRedirect(GitHubSignInSuccessHandler.FAILED) }
            }
        }

        return http.build()
    }

    /**
     * Deny by default (#155 FR-10.3). No allowlist configured means no cross-origin request
     * is permitted; a wildcard origin is refused outright when enforcement is on, because
     * `*` with credentials is exactly the combination that makes an ambient session cookie
     * readable by any site.
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        val origins = allowedOrigins.split(",").map { it.trim() }.filter { it.isNotBlank() }

        if (origins.contains("*")) {
            require(!enforceAuth) {
                "security.cors.allowed-origins must not be '*' when authentication is enforced; " +
                    "list the origins that may call this API."
            }
            configuration.addAllowedOriginPattern("*")
        } else {
            configuration.allowedOrigins = origins
        }

        configuration.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf(
            "Authorization", "Content-Type", "X-API-Key", "X-Factstore-Client",
            "X-Factstore-CI-Context", "X-Factstore-Dry-Run"
        )
        configuration.exposedHeaders = listOf("X-Factstore-Credential-Warning", "Retry-After")
        configuration.allowCredentials = origins.isNotEmpty() && !origins.contains("*")

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/api/**", configuration)
        return source
    }

    private companion object {
        const val HSTS_MAX_AGE_SECONDS = 31_536_000L
    }
}
