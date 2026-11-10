package com.factstore.core.port.outbound

import com.factstore.core.domain.SigningKey

interface ISigningKeyRepository {
    fun save(key: SigningKey): SigningKey
    fun findByKeyId(keyId: String): SigningKey?
    fun findAll(): List<SigningKey>
}
