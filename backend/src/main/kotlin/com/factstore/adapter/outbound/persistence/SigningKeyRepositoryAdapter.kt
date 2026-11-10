package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.SigningKey
import com.factstore.core.port.outbound.ISigningKeyRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SigningKeyJpa : JpaRepository<SigningKey, UUID> {
    fun findByKeyId(keyId: String): SigningKey?
    fun findAllByOrderByCreatedAt(): List<SigningKey>
}

@Component
class SigningKeyRepositoryAdapter(private val jpa: SigningKeyJpa) : ISigningKeyRepository {
    override fun save(key: SigningKey): SigningKey = jpa.save(key)
    override fun findByKeyId(keyId: String): SigningKey? = jpa.findByKeyId(keyId)
    override fun findAll(): List<SigningKey> = jpa.findAllByOrderByCreatedAt()
}
