package com.factstore

import com.factstore.application.OrganisationMemberService
import com.factstore.application.UserService
import com.factstore.core.domain.MemberRole
import com.factstore.dto.CreateUserRequest
import com.factstore.dto.InviteMemberRequest
import com.factstore.dto.UpdateMemberRoleRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class OrganisationMemberServiceTest {

    @Autowired
    lateinit var memberService: OrganisationMemberService

    @Autowired
    lateinit var userService: UserService

    private val orgSlug = "test-org"

    @Test
    fun `invite member succeeds and is listed`() {
        val user = userService.createUser(CreateUserRequest("invite@example.com", "Invite User"))
        val member = memberService.inviteMember(orgSlug, InviteMemberRequest(user.email, MemberRole.MEMBER))
        assertEquals(user.id, member.userId)
        assertEquals("invite@example.com", member.email)
        assertEquals("Invite User", member.name)
        assertEquals(MemberRole.MEMBER, member.role)

        val members = memberService.listMembers(orgSlug)
        assertTrue(members.any { it.userId == user.id })
    }

    @Test
    fun `invite member with unknown email throws NotFoundException`() {
        assertThrows<NotFoundException> {
            memberService.inviteMember(orgSlug, InviteMemberRequest("unknown@example.com", MemberRole.VIEWER))
        }
    }

    @Test
    fun `invite duplicate member throws ConflictException`() {
        val user = userService.createUser(CreateUserRequest("dup-member@example.com", "Dup"))
        memberService.inviteMember(orgSlug, InviteMemberRequest(user.email, MemberRole.VIEWER))
        assertThrows<ConflictException> {
            memberService.inviteMember(orgSlug, InviteMemberRequest(user.email, MemberRole.MEMBER))
        }
    }

    @Test
    fun `get member returns correct member`() {
        val user = userService.createUser(CreateUserRequest("get-member@example.com", "Get Member"))
        memberService.inviteMember(orgSlug, InviteMemberRequest(user.email, MemberRole.ADMIN))

        val member = memberService.getMember(orgSlug, user.id)
        assertEquals(user.id, member.userId)
        assertEquals(MemberRole.ADMIN, member.role)
    }

    @Test
    fun `get non-existent member throws NotFoundException`() {
        assertThrows<NotFoundException> {
            memberService.getMember(orgSlug, UUID.randomUUID())
        }
    }

    @Test
    fun `update member role succeeds`() {
        val user = userService.createUser(CreateUserRequest("role-upd@example.com", "Role Update"))
        memberService.inviteMember(orgSlug, InviteMemberRequest(user.email, MemberRole.VIEWER))

        val updated = memberService.updateMemberRole(orgSlug, user.id, UpdateMemberRoleRequest(MemberRole.ADMIN))
        assertEquals(MemberRole.ADMIN, updated.role)
    }

    @Test
    fun `update role of non-member throws NotFoundException`() {
        assertThrows<NotFoundException> {
            memberService.updateMemberRole(orgSlug, UUID.randomUUID(), UpdateMemberRoleRequest(MemberRole.MEMBER))
        }
    }

    @Test
    fun `remove member succeeds`() {
        val user = userService.createUser(CreateUserRequest("remove@example.com", "Remove Me"))
        memberService.inviteMember(orgSlug, InviteMemberRequest(user.email, MemberRole.MEMBER))

        memberService.removeMember(orgSlug, user.id)
        assertThrows<NotFoundException> { memberService.getMember(orgSlug, user.id) }
    }

    @Test
    fun `remove non-existent member throws NotFoundException`() {
        assertThrows<NotFoundException> {
            memberService.removeMember(orgSlug, UUID.randomUUID())
        }
    }

    @Test
    fun `list members returns only members of the given org`() {
        val user1 = userService.createUser(CreateUserRequest("org1-user@example.com", "Org1 User"))
        val user2 = userService.createUser(CreateUserRequest("org2-user@example.com", "Org2 User"))
        memberService.inviteMember("org-a", InviteMemberRequest(user1.email, MemberRole.MEMBER))
        memberService.inviteMember("org-b", InviteMemberRequest(user2.email, MemberRole.VIEWER))

        val orgAMembers = memberService.listMembers("org-a")
        assertTrue(orgAMembers.any { it.userId == user1.id })
        assertTrue(orgAMembers.none { it.userId == user2.id })
    }

    @Test
    fun `all roles can be assigned`() {
        MemberRole.entries.forEachIndexed { idx, role ->
            val user = userService.createUser(CreateUserRequest("role-$idx@example.com", "Role $idx"))
            val member = memberService.inviteMember("roles-org", InviteMemberRequest(user.email, role))
            assertEquals(role, member.role)
        }
    }
}
