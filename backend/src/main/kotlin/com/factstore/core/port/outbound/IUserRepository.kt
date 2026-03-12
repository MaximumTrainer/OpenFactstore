package com.factstore.core.port.outbound

import com.factstore.core.domain.User
import java.util.UUID

interface IUserRepository {
    fun save(user: User): User
    fun findById(id: UUID): User?
    fun findByEmail(email: String): User?
    fun findByGithubId(githubId: String): User?
    fun findAll(): List<User>
    fun existsById(id: UUID): Boolean
    fun existsByEmail(email: String): Boolean
    fun deleteById(id: UUID)
}
