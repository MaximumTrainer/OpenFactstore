package com.factstore.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

/**
 * One published version of a fact schema (#195). Immutable once published: a change is a new
 * version, and a new version must stay backwards compatible with the one before it.
 */
@Entity
@Table(name = "fact_schemas", uniqueConstraints = [UniqueConstraint(columnNames = ["name", "version"])])
class FactSchema(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "name", nullable = false, length = 128)
    val name: String,

    @Column(name = "version", nullable = false)
    val version: Int,

    /** The JSON Schema (draft 2020-12) document, as published. */
    @Column(name = "schema_json", nullable = false, columnDefinition = "TEXT")
    val schemaJson: String,

    @Column(name = "created_by", nullable = false)
    val createdBy: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
) {
    val ref: String get() = SchemaUrn.of(name, version).value
}

/**
 * A schema reference, `urn:schema:<name>:v<version>` — what a fact carries in
 * `governance.schema_ref` (#195).
 */
@JvmInline
value class SchemaUrn(val value: String) {
    init {
        require(PATTERN.matches(value)) { "schema_ref must look like urn:schema:<name>:v<version>, got '$value'" }
    }

    val name: String get() = PATTERN.find(value)!!.groupValues[1]
    val version: Int get() = PATTERN.find(value)!!.groupValues[2].toInt()

    companion object {
        private val PATTERN = Regex("^urn:schema:([a-z0-9][a-z0-9._-]{0,127}):v([1-9][0-9]{0,5})$")
        val NAME = Regex("^[a-z0-9][a-z0-9._-]{0,127}$")
        fun of(name: String, version: Int) = SchemaUrn("urn:schema:$name:v$version")
    }
}
