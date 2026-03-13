package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.ISearchService
import com.factstore.dto.SearchResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Search", description = "Cross-entity full-text search")
class SearchController(private val searchService: ISearchService) {

    @GetMapping
    @Operation(summary = "Search across trails and artifacts", description = "Full-text search by query string. Use type=trail|artifact to limit results. An invalid type value returns HTTP 400.")
    fun search(
        @RequestParam q: String,
        @RequestParam(required = false) type: String?
    ): ResponseEntity<SearchResponse> =
        ResponseEntity.ok(searchService.search(q, type))
}
