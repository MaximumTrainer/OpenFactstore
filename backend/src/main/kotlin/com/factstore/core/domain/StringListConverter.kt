package com.factstore.core.domain

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/** A list of strings stored as a JSON array in one TEXT column. */
@Converter
class StringListConverter : AttributeConverter<List<String>, String> {
    private val mapper = jacksonObjectMapper()
    override fun convertToDatabaseColumn(attribute: List<String>?): String = mapper.writeValueAsString(attribute ?: emptyList<String>())
    override fun convertToEntityAttribute(dbData: String?): List<String> =
        if (dbData.isNullOrBlank()) emptyList() else mapper.readValue(dbData)
}
