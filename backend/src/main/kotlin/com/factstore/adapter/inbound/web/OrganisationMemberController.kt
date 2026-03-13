package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.IOrganisationMemberService
import com.factstore.dto.InviteMemberRequest
import com.factstore.dto.MemberResponse
import com.factstore.dto.UpdateMemberRoleRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organisations/{slug}/members")
@Tag(name = "Organisation Members", description = "Organisation membership and role management")
class OrganisationMemberController(private val memberService: IOrganisationMemberService) {

    @GetMapping
    @Operation(summary = "List all members of an organisation")
    fun listMembers(@PathVariable slug: String): ResponseEntity<List<MemberResponse>> =
        ResponseEntity.ok(memberService.listMembers(slug))

    @PostMapping
    @Operation(summary = "Invite a user to an organisation by email and assign a role")
    fun inviteMember(
        @PathVariable slug: String,
        @RequestBody request: InviteMemberRequest
    ): ResponseEntity<MemberResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(memberService.inviteMember(slug, request))

    @GetMapping("/{userId}")
    @Operation(summary = "Get a specific member of an organisation")
    fun getMember(
        @PathVariable slug: String,
        @PathVariable userId: UUID
    ): ResponseEntity<MemberResponse> =
        ResponseEntity.ok(memberService.getMember(slug, userId))

    @PutMapping("/{userId}")
    @Operation(summary = "Update the role of a member in an organisation")
    fun updateMemberRole(
        @PathVariable slug: String,
        @PathVariable userId: UUID,
        @RequestBody request: UpdateMemberRoleRequest
    ): ResponseEntity<MemberResponse> =
        ResponseEntity.ok(memberService.updateMemberRole(slug, userId, request))

    @DeleteMapping("/{userId}")
    @Operation(summary = "Remove a member from an organisation")
    fun removeMember(
        @PathVariable slug: String,
        @PathVariable userId: UUID
    ): ResponseEntity<Void> {
        memberService.removeMember(slug, userId)
        return ResponseEntity.noContent().build()
    }
}
