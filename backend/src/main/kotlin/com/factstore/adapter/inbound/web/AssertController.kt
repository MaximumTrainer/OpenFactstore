package com.factstore.adapter.inbound.web

import com.factstore.application.DryRunContext
import com.factstore.core.port.inbound.IAssertService
import com.factstore.dto.AssertRequest
import com.factstore.dto.AssertResponse
import com.factstore.dto.DryRunResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/assert")
@Tag(name = "Assert", description = "Compliance assertion")
class AssertController(private val assertService: IAssertService) {

    @PostMapping
    @Operation(summary = "Assert compliance for an artifact against a flow")
    fun assertCompliance(
        @RequestBody request: AssertRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<*> {
        val response = assertService.assertCompliance(request)
        if (DryRunContext.isDryRun(httpRequest)) {
            return ResponseEntity.ok(DryRunResponse(wouldCreate = response))
        }
        return ResponseEntity.ok(response)
    }
}
