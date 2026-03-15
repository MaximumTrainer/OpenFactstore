package com.factstore.core.port.outbound

interface ISecretStore {
    fun get(path: String): String?
    fun put(path: String, value: String)
    fun delete(path: String)
}
