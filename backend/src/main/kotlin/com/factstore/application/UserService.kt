package com.factstore.application

import com.factstore.core.domain.User
import com.factstore.core.port.inbound.IUserService
import com.factstore.core.port.outbound.IUserRepository
import com.factstore.dto.CreateUserRequest
import com.factstore.dto.UpdateUserRequest
import com.factstore.dto.UserResponse
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class UserService(private val userRepository: IUserRepository) : IUserService {

    private val log = LoggerFactory.getLogger(UserService::class.java)

    override fun createUser(request: CreateUserRequest): UserResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw ConflictException("User with email '${request.email}' already exists")
        }
        val user = User(
            email = request.email,
            name = request.name,
            githubId = request.githubId
        )
        val saved = userRepository.save(user)
        log.info("Created user: ${saved.id} email=${saved.email}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun listUsers(): List<UserResponse> = userRepository.findAll().map { it.toResponse() }

    @Transactional(readOnly = true)
    override fun getUser(id: UUID): UserResponse =
        (userRepository.findById(id) ?: throw NotFoundException("User not found: $id")).toResponse()

    override fun updateUser(id: UUID, request: UpdateUserRequest): UserResponse {
        val user = userRepository.findById(id) ?: throw NotFoundException("User not found: $id")
        request.name?.let { user.name = it }
        request.githubId?.let { user.githubId = it }
        user.updatedAt = Instant.now()
        return userRepository.save(user).toResponse()
    }

    override fun deleteUser(id: UUID) {
        if (!userRepository.existsById(id)) throw NotFoundException("User not found: $id")
        userRepository.deleteById(id)
        log.info("Deleted user: $id")
    }

    override fun findOrCreateByGithub(githubId: String, email: String, name: String): UserResponse {
        val existing = userRepository.findByGithubId(githubId)
            ?: userRepository.findByEmail(email)
        if (existing != null) {
            if (existing.githubId == null) {
                existing.githubId = githubId
                existing.updatedAt = Instant.now()
                userRepository.save(existing)
            }
            return existing.toResponse()
        }
        val user = User(email = email, name = name, githubId = githubId)
        val saved = userRepository.save(user)
        log.info("Created user via GitHub OAuth: ${saved.id} githubId=$githubId")
        return saved.toResponse()
    }

    override fun getUserEntity(id: UUID): User =
        userRepository.findById(id) ?: throw NotFoundException("User not found: $id")
}

fun User.toResponse() = UserResponse(
    id = id,
    email = email,
    name = name,
    githubId = githubId,
    createdAt = createdAt,
    updatedAt = updatedAt
)
