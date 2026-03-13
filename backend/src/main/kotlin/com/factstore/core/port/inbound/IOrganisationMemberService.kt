package com.factstore.core.port.inbound

import com.factstore.dto.InviteMemberRequest
import com.factstore.dto.MemberResponse
import com.factstore.dto.UpdateMemberRoleRequest
import java.util.UUID

interface IOrganisationMemberService {
    fun listMembers(orgSlug: String): List<MemberResponse>
    fun inviteMember(orgSlug: String, request: InviteMemberRequest): MemberResponse
    fun getMember(orgSlug: String, userId: UUID): MemberResponse
    fun updateMemberRole(orgSlug: String, userId: UUID, request: UpdateMemberRoleRequest): MemberResponse
    fun removeMember(orgSlug: String, userId: UUID)
}
