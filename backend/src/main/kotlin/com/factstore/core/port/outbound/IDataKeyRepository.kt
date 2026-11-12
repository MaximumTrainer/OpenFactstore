package com.factstore.core.port.outbound

import com.factstore.core.domain.DataKey
import com.factstore.core.domain.ShredRequest
import java.util.UUID

interface IDataKeyRepository {
    fun save(key: DataKey): DataKey
    fun find(tenant: String, period: String): DataKey?
    fun findAll(): List<DataKey>
}

interface IShredRequestRepository {
    fun save(request: ShredRequest): ShredRequest
    fun findById(id: UUID): ShredRequest?
}

/**
 * The key-encryption key that wraps data keys (#200). It lives in a key service (Vault Transit,
 * a cloud KMS); Factstore only asks it to wrap and unwrap.
 */
interface IKeyEncryptionKey {
    val keyId: String
    fun wrap(dataKey: ByteArray): String
    fun unwrap(wrapped: String): ByteArray
}
