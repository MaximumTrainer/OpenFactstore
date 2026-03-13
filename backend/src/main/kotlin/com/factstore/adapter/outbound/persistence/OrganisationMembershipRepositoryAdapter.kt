package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.OrganisationMembership
import com.factstore.core.port.outbound.IOrganisationMembershipRepository
import com.factstore.exception.ConflictException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface OrganisationMembershipRepositoryJpa : JpaRepository<OrganisationMembership, UUID> {
    fun findByOrgSlug(orgSlug: String): List<OrganisationMembership>
    fun findByOrgSlugAndUserId(orgSlug: String, userId: UUID): OrganisationMembership?
    fun existsByOrgSlugAndUserId(orgSlug: String, userId: UUID): Boolean
}

@Component
class OrganisationMembershipRepositoryAdapter(
    private val jpa: OrganisationMembershipRepositoryJpa
) : IOrganisationMembershipRepository {
    override fun findByOrgSlug(orgSlug: String): List<OrganisationMembership> = jpa.findByOrgSlug(orgSlug)
    override fun findByOrgSlugAndUserId(orgSlug: String, userId: UUID): OrganisationMembership? =
        jpa.findByOrgSlugAndUserId(orgSlug, userId)
    override fun existsByOrgSlugAndUserId(orgSlug: String, userId: UUID): Boolean =
        jpa.existsByOrgSlugAndUserId(orgSlug, userId)
    override fun save(membership: OrganisationMembership): OrganisationMembership = try {
        jpa.saveAndFlush(membership)
    } catch (ex: DataIntegrityViolationException) {
        throw ConflictException("User '${membership.userId}' is already a member of organisation '${membership.orgSlug}'")
    }
    override fun delete(membership: OrganisationMembership) = jpa.delete(membership)
}
