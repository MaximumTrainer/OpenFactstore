package com.factstore.core.domain

data class HubTemplate(
    val id: String,
    val name: String,
    val description: String,
    val framework: String,
    val version: String,
    val yaml: String
)
