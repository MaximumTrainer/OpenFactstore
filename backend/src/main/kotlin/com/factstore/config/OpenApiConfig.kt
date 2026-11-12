package com.factstore.config

import com.factstore.core.domain.AuditEventType
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Schema
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Factstore API")
                    .version("v1")
                    .description("Factstore - Supply Chain Compliance Fact Store API")
            )
    }

    /**
     * Value sets that grow with the product are published as `x-extensible-enum`, not `enum` (#197).
     * Each new capability adds an audit event type, and a closed enum would make every addition a
     * breaking change to the contract, which `oasdiff breaking` refuses. Clients must tolerate
     * values they do not know.
     */
    @Bean
    fun extensibleEnums(): OpenApiCustomizer = OpenApiCustomizer { api ->
        val open = EXTENSIBLE.map { it.toSet() }
        val seen = java.util.IdentityHashMap<Any, Boolean>()
        fun visit(schema: Schema<*>?) {
            if (schema == null || seen.put(schema, true) != null) return
            val values = schema.enum?.map { it.toString() }
            if (values != null && open.any { it == values.toSet() }) {
                @Suppress("UNCHECKED_CAST")
                (schema as Schema<Any>).enum = null
                schema.addExtension("x-extensible-enum", values)
            }
            schema.properties?.values?.forEach { visit(it) }
            visit(schema.items)
            (schema.additionalProperties as? Schema<*>)?.let { visit(it) }
            schema.allOf?.forEach { visit(it) }
            schema.anyOf?.forEach { visit(it) }
            schema.oneOf?.forEach { visit(it) }
        }
        api.components?.schemas?.values?.forEach { visit(it) }
        api.paths?.values?.forEach { path ->
            path.readOperations().forEach { operation ->
                operation.parameters?.forEach { visit(it.schema) }
                operation.requestBody?.content?.values?.forEach { visit(it.schema) }
                operation.responses?.values?.forEach { response -> response.content?.values?.forEach { visit(it.schema) } }
            }
        }
    }

    companion object {
        /** Enums published as open sets. */
        val EXTENSIBLE: List<List<String>> = listOf(AuditEventType.entries.map { it.name })
    }
}
