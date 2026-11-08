package com.factstore.application.auth

import com.factstore.core.domain.AuthProvider
import com.factstore.core.domain.MemberRole
import com.factstore.core.domain.OrganisationMembership
import com.factstore.core.domain.User
import com.factstore.core.port.outbound.IOrganisationMembershipRepository
import com.factstore.core.port.outbound.IUserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Signs a developer in without an identity provider (#156 FR-8.4).
 *
 * Creates the user if needed and sets their membership to the requested role, so a developer can
 * try the UI as a VIEWER and then as an ADMIN. The session is an ordinary one, marked
 * [AuthProvider.DEV], so everything downstream of sign-in behaves exactly as in production.
 * Whether this may run at all is decided by the caller and [com.factstore.config.DevSignInGuard].
 */
@Service
class DevSignInService(
    private val users: IUserRepository,
    private val memberships: IOrganisationMembershipRepository,
    private val sessionService: SessionService
) {

    @Transactional
    fun signIn(email: String, name: String?, orgSlug: String, role: MemberRole, sourceIp: String?): IssuedSession {
        val user = users.findByEmail(email) ?: users.save(User(email = email, name = name ?: email.substringBefore('@')))
        val membership = memberships.findByOrgSlugAndUserId(orgSlug, user.id)
        if (membership == null) {
            memberships.save(OrganisationMembership(orgSlug = orgSlug, userId = user.id, role = role))
        } else if (membership.role != role) {
            membership.role = role
            memberships.save(membership)
        }
        return sessionService.issue(user.id, orgSlug, AuthProvider.DEV, sourceIp = sourceIp)
    }
}
