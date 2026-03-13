package com.factstore.core.port.outbound

import com.factstore.core.domain.OrganisationMembership
import java.util.UUID

interface IOrganisationMembershipRepository {
    fun findByOrgSlug(orgSlug: String): List<OrganisationMembership>
    fun findByOrgSlugAndUserId(orgSlug: String, userId: UUID): OrganisationMembership?
    fun existsByOrgSlugAndUserId(orgSlug: String, userId: UUID): Boolean
    fun save(membership: OrganisationMembership): OrganisationMembership
    fun delete(membership: OrganisationMembership)
}
