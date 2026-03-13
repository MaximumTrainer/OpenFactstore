package com.factstore.application

import com.factstore.core.domain.OrganisationMembership
import com.factstore.core.port.inbound.IOrganisationMemberService
import com.factstore.core.port.outbound.IOrganisationMembershipRepository
import com.factstore.core.port.outbound.IUserRepository
import com.factstore.dto.InviteMemberRequest
import com.factstore.dto.MemberResponse
import com.factstore.dto.UpdateMemberRoleRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class OrganisationMemberService(
    private val membershipRepository: IOrganisationMembershipRepository,
    private val userRepository: IUserRepository
) : IOrganisationMemberService {

    private val log = LoggerFactory.getLogger(OrganisationMemberService::class.java)

    @Transactional(readOnly = true)
    override fun listMembers(orgSlug: String): List<MemberResponse> {
        val memberships = membershipRepository.findByOrgSlug(orgSlug)
        val userIds = memberships.map { it.userId }.toSet()
        val usersById = userRepository.findAllById(userIds).associateBy { it.id }
        return memberships.map { membership ->
            // Users are referenced via FK; a missing entry here indicates data corruption.
            val user = usersById[membership.userId]
                ?: throw IllegalStateException("Data corruption: membership references unknown user '${membership.userId}'")
            MemberResponse(
                userId = membership.userId,
                email = user.email,
                name = user.name,
                role = membership.role,
                joinedAt = membership.joinedAt
            )
        }
    }

    override fun inviteMember(orgSlug: String, request: InviteMemberRequest): MemberResponse {
        val user = userRepository.findByEmail(request.email)
            ?: throw NotFoundException("User with email '${request.email}' not found")
        if (membershipRepository.existsByOrgSlugAndUserId(orgSlug, user.id)) {
            throw ConflictException("User '${request.email}' is already a member of organisation '$orgSlug'")
        }
        val membership = OrganisationMembership(
            orgSlug = orgSlug,
            userId = user.id,
            role = request.role
        )
        val saved = try {
            membershipRepository.save(membership)
        } catch (ex: ConflictException) {
            // The adapter translates a unique-constraint violation into ConflictException so
            // concurrent invites racing past the existsByOrgSlugAndUserId check are also caught.
            throw ConflictException("User '${request.email}' is already a member of organisation '$orgSlug'")
        }
        log.info("Invited user=${user.id} to org=$orgSlug role=${request.role}")
        return MemberResponse(
            userId = saved.userId,
            email = user.email,
            name = user.name,
            role = saved.role,
            joinedAt = saved.joinedAt
        )
    }

    @Transactional(readOnly = true)
    override fun getMember(orgSlug: String, userId: UUID): MemberResponse {
        val membership = membershipRepository.findByOrgSlugAndUserId(orgSlug, userId)
            ?: throw NotFoundException("User '$userId' is not a member of organisation '$orgSlug'")
        return membership.toResponse()
    }

    override fun updateMemberRole(orgSlug: String, userId: UUID, request: UpdateMemberRoleRequest): MemberResponse {
        val membership = membershipRepository.findByOrgSlugAndUserId(orgSlug, userId)
            ?: throw NotFoundException("User '$userId' is not a member of organisation '$orgSlug'")
        membership.role = request.role
        val saved = membershipRepository.save(membership)
        log.info("Updated role for user=$userId in org=$orgSlug to ${request.role}")
        return saved.toResponse()
    }

    override fun removeMember(orgSlug: String, userId: UUID) {
        val membership = membershipRepository.findByOrgSlugAndUserId(orgSlug, userId)
            ?: throw NotFoundException("User '$userId' is not a member of organisation '$orgSlug'")
        membershipRepository.delete(membership)
        log.info("Removed user=$userId from org=$orgSlug")
    }

    private fun OrganisationMembership.toResponse(): MemberResponse {
        val user = userRepository.findById(userId)
            ?: throw NotFoundException("User '$userId' not found")
        return MemberResponse(
            userId = userId,
            email = user.email,
            name = user.name,
            role = role,
            joinedAt = joinedAt
        )
    }
}
