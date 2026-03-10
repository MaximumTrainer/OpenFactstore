package com.factstore.controller

import com.factstore.dto.AssertRequest
import com.factstore.dto.AssertResponse
import com.factstore.service.AssertService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/assert")
@Tag(name = "Assert", description = "Compliance assertion")
class AssertController(private val assertService: AssertService) {

    @PostMapping
    @Operation(summary = "Assert compliance for an artifact against a flow")
    fun assertCompliance(@RequestBody request: AssertRequest): ResponseEntity<AssertResponse> =
        ResponseEntity.ok(assertService.assertCompliance(request))
}
