package com.factstore.adapter.mock

import com.factstore.core.port.outbound.IRetentionArchive

/** Keeps archived records in memory so a test can read back what retention archived (#251). */
class InMemoryRetentionArchive : IRetentionArchive {
    val entries = linkedMapOf<String, String>()

    override fun store(key: String, content: String): String {
        entries[key] = content
        return "memory:$key"
    }
}
