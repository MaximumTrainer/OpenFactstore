package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.User
import com.factstore.core.port.outbound.IUserRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserRepositoryJpa : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?
    fun findByGithubId(githubId: String): User?
    fun existsByEmail(email: String): Boolean
}

@Component
class UserRepositoryAdapter(private val jpa: UserRepositoryJpa) : IUserRepository {
    override fun save(user: User): User = jpa.save(user)
    override fun findById(id: UUID): User? = jpa.findById(id).orElse(null)
    override fun findByEmail(email: String): User? = jpa.findByEmail(email)
    override fun findByGithubId(githubId: String): User? = jpa.findByGithubId(githubId)
    override fun findAll(): List<User> = jpa.findAll()
    override fun findAllById(ids: Collection<UUID>): List<User> = jpa.findAllById(ids)
    override fun existsById(id: UUID): Boolean = jpa.existsById(id)
    override fun existsByEmail(email: String): Boolean = jpa.existsByEmail(email)
    override fun deleteById(id: UUID) = jpa.deleteById(id)
}
