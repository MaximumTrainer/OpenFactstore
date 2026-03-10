package com.factstore.controller

import com.factstore.dto.CreateTrailRequest
import com.factstore.dto.TrailResponse
import com.factstore.service.TrailService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@Tag(name = "Trails", description = "Trail management")
class TrailController(private val trailService: TrailService) {

    @PostMapping("/api/v1/trails")
    @Operation(summary = "Create/begin a trail")
    fun createTrail(@RequestBody request: CreateTrailRequest): ResponseEntity<TrailResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(trailService.createTrail(request))

    @GetMapping("/api/v1/trails")
    @Operation(summary = "List trails, optionally filter by flowId")
    fun listTrails(@RequestParam(required = false) flowId: UUID?): ResponseEntity<List<TrailResponse>> =
        ResponseEntity.ok(trailService.listTrails(flowId))

    @GetMapping("/api/v1/trails/{id}")
    @Operation(summary = "Get trail by ID")
    fun getTrail(@PathVariable id: UUID): ResponseEntity<TrailResponse> =
        ResponseEntity.ok(trailService.getTrail(id))

    @GetMapping("/api/v1/flows/{flowId}/trails")
    @Operation(summary = "List trails for a flow")
    fun listTrailsForFlow(@PathVariable flowId: UUID): ResponseEntity<List<TrailResponse>> =
        ResponseEntity.ok(trailService.listTrailsForFlow(flowId))
}
