package com.factstore.core.port.inbound

import com.factstore.core.domain.User
import com.factstore.dto.CreateUserRequest
import com.factstore.dto.UpdateUserRequest
import com.factstore.dto.UserResponse
import java.util.UUID

interface IUserService {
    fun createUser(request: CreateUserRequest): UserResponse
    fun listUsers(): List<UserResponse>
    fun getUser(id: UUID): UserResponse
    fun updateUser(id: UUID, request: UpdateUserRequest): UserResponse
    fun deleteUser(id: UUID)
    /** Finds or creates a user for the given GitHub identity (used by OAuth2 login). */
    fun findOrCreateByGithub(githubId: String, email: String, name: String): UserResponse
    fun getUserEntity(id: UUID): User
}
