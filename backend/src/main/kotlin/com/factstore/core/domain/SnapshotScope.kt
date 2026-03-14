package com.factstore.core.domain

data class SnapshotScope(
    val includeNames: List<String> = emptyList(),
    val includePatterns: List<String> = emptyList(),
    val excludeNames: List<String> = emptyList(),
    val excludePatterns: List<String> = emptyList()
)
