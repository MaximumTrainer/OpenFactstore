package com.factstore.core.port.inbound

import com.factstore.dto.DashboardStatsResponse

interface IDashboardService {
    fun getStats(): DashboardStatsResponse
}
