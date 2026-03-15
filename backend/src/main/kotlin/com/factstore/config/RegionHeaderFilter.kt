package com.factstore.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class RegionHeaderFilter(
    @Value("\${factstore.region:us-east-1}") private val localRegion: String
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val requestedRegion = request.getHeader("X-Factstore-Region")
        RegionContextHolder.set(requestedRegion ?: localRegion)
        response.setHeader("X-Factstore-Region", localRegion)
        try {
            filterChain.doFilter(request, response)
        } finally {
            RegionContextHolder.clear()
        }
    }
}
