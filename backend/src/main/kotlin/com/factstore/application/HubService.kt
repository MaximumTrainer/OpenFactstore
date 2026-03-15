package com.factstore.application

import com.factstore.core.domain.HubTemplate
import com.factstore.exception.NotFoundException
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Service
import org.yaml.snakeyaml.Yaml

@Service
class HubService {
    private val templates: Map<String, HubTemplate> by lazy { loadTemplates() }

    fun listTemplates(): List<HubTemplate> = templates.values.toList()

    fun getTemplate(id: String): HubTemplate =
        templates[id] ?: throw NotFoundException("Hub template not found: $id")

    private fun loadTemplates(): Map<String, HubTemplate> {
        val resolver = PathMatchingResourcePatternResolver()
        val resources = resolver.getResources("classpath:hub-templates/*.yml")
        val yaml = Yaml()
        return resources.mapNotNull { resource ->
            try {
                val content = resource.inputStream.bufferedReader().readText()
                @Suppress("UNCHECKED_CAST")
                val map = yaml.load<Map<String, Any>>(content)
                HubTemplate(
                    id = map["id"] as String,
                    name = map["name"] as String,
                    description = map["description"] as String,
                    framework = map["framework"] as String,
                    version = map["version"] as String,
                    yaml = content
                )
            } catch (e: Exception) {
                null
            }
        }.associateBy { it.id }
    }
}
