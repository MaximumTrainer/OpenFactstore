package com.factstore.core.port.inbound

import com.factstore.dto.SearchResponse

interface ISearchService {
    fun search(query: String, type: String?): SearchResponse
}
