package com.factstore.adapter.inbound.web.query

import com.factstore.exception.BadRequestException
import org.springframework.http.ResponseEntity

/**
 * #250 — optional paging for the v2 list endpoints. Without `page` and `size` the whole list is
 * returned, as before. With them, one page is returned and `X-Total-Count` gives the full size,
 * so the response stays a plain JSON array and existing v2 clients are unaffected.
 */
internal object Paging {
    const val TOTAL_COUNT = "X-Total-Count"
    const val MAX_SIZE = 500

    fun <T> page(items: List<T>, page: Int?, size: Int?): ResponseEntity<List<T>> {
        if (page == null && size == null) return ResponseEntity.ok(items)
        val p = page ?: 0
        val s = size ?: 20
        if (p < 0) throw BadRequestException("page must be 0 or more")
        if (s !in 1..MAX_SIZE) throw BadRequestException("size must be between 1 and $MAX_SIZE")
        val from = (p.toLong() * s).coerceAtMost(items.size.toLong()).toInt()
        return ResponseEntity.ok()
            .header(TOTAL_COUNT, items.size.toString())
            .body(items.subList(from, (from + s).coerceAtMost(items.size)))
    }
}
