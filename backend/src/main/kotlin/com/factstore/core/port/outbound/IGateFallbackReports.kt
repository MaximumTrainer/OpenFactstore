package com.factstore.core.port.outbound

import java.util.UUID

/** Client gate fallback reports already recorded, by the client's report id (#193). */
interface IGateFallbackReports {
    fun find(reportId: UUID): UUID?
    fun record(reportId: UUID, auditId: UUID)
}
