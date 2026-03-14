package com.factstore.application

import jakarta.servlet.http.HttpServletRequest

object DryRunContext {
    fun isDryRun(request: HttpServletRequest): Boolean =
        request.getHeader("X-Factstore-Dry-Run")?.lowercase() == "true"
            || request.getParameter("dryRun")?.lowercase() == "true"
}
