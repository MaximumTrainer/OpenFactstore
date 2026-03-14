package com.factstore.core.port.inbound

import com.factstore.dto.AllowlistEntryResponse
import com.factstore.dto.CreateAllowlistEntryRequest
import java.util.UUID

interface IEnvironmentAllowlistService {
    fun addEntry(environmentId: UUID, request: CreateAllowlistEntryRequest): AllowlistEntryResponse
    fun listEntries(environmentId: UUID): List<AllowlistEntryResponse>
    fun removeEntry(environmentId: UUID, entryId: UUID): AllowlistEntryResponse
    fun isAllowlisted(environmentId: UUID, sha256: String? = null, artifactName: String? = null): Boolean
}
