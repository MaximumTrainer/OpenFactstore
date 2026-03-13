package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.ILedgerService
import com.factstore.dto.ChainVerificationResponse
import com.factstore.dto.LedgerEntryResponse
import com.factstore.dto.LedgerStatusResponse
import com.factstore.dto.PagedLedgerEntriesResponse
import com.factstore.dto.VerificationResponse
import com.factstore.dto.VerifyChainRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/ledger")
@Tag(name = "Ledger", description = "Immutable ledger — hash-chain verification and audit trail")
@ConditionalOnProperty(name = ["ledger.enabled"], havingValue = "true")
class LedgerController(
    private val ledgerService: ILedgerService
) {

    @GetMapping("/entries")
    @Operation(summary = "List ledger entries (paginated)")
    fun listEntries(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PagedLedgerEntriesResponse> =
        ResponseEntity.ok(ledgerService.listEntries(page, size))

    @GetMapping("/entries/{factId}")
    @Operation(summary = "Get ledger entry for a specific fact")
    fun getEntry(@PathVariable factId: UUID): ResponseEntity<LedgerEntryResponse> =
        ResponseEntity.ok(ledgerService.getEntry(factId))

    @PostMapping("/verify/{factId}")
    @Operation(summary = "Verify the integrity of a specific fact")
    fun verifyFact(@PathVariable factId: UUID): ResponseEntity<VerificationResponse> =
        ResponseEntity.ok(ledgerService.verifyFact(factId))

    @PostMapping("/verify-chain")
    @Operation(summary = "Verify chain integrity for a date range")
    fun verifyChain(@RequestBody request: VerifyChainRequest): ResponseEntity<ChainVerificationResponse> =
        ResponseEntity.ok(ledgerService.verifyChain(request))

    @GetMapping("/status")
    @Operation(summary = "Ledger health and sync status")
    fun getStatus(): ResponseEntity<LedgerStatusResponse> =
        ResponseEntity.ok(ledgerService.getStatus())
}
