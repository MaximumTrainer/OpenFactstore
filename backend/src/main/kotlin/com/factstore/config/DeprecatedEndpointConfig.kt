package com.factstore.config

import com.factstore.adapter.inbound.web.DeprecatedEndpoint
import io.swagger.v3.oas.models.Operation
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * #250 — what `@DeprecatedEndpoint` does at runtime and in the OpenAPI document.
 *
 * The headers are written before the handler runs, so a deprecated call that fails (404, 409, …)
 * still tells the caller where to go.
 */
@Configuration
class DeprecatedEndpointConfig : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(DeprecationHeaders())
    }

    @Bean
    fun deprecatedOperations(): OperationCustomizer = OperationCustomizer { operation, handler ->
        handler.getMethodAnnotation(DeprecatedEndpoint::class.java)?.let { mark(operation, it) }
        operation
    }

    class DeprecationHeaders : HandlerInterceptor {
        override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
            val deprecation = (handler as? HandlerMethod)?.getMethodAnnotation(DeprecatedEndpoint::class.java) ?: return true
            @Suppress("UNCHECKED_CAST")
            val variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<String, String> ?: emptyMap()
            response.setHeader("Deprecation", "@${epochSecond(deprecation.since)}")
            response.setHeader("Sunset", httpDate(deprecation.sunset))
            val successor = UriComponentsBuilder.fromPath(deprecation.successor).buildAndExpand(variables).encode().toUriString()
            response.addHeader("Link", "<$successor>; rel=\"successor-version\"")
            return true
        }
    }

    companion object {
        private fun startOfDay(date: String) = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC)

        fun epochSecond(date: String): Long = startOfDay(date).toEpochSecond()

        fun httpDate(date: String): String = DateTimeFormatter.RFC_1123_DATE_TIME.format(startOfDay(date))

        fun mark(operation: Operation, deprecation: DeprecatedEndpoint) {
            operation.deprecated = true
            val note = "**Deprecated** since ${deprecation.since}; use `${deprecation.successor}` instead. " +
                "Removed after ${deprecation.sunset}. Responses carry `Deprecation`, `Sunset` and a `successor-version` `Link` header."
            operation.description = listOfNotNull(operation.description?.takeIf { it.isNotBlank() }, note).joinToString("\n\n")
        }
    }
}
