package com.factstore.exception

import com.factstore.dto.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ResponseEntity<ErrorResponse> {
        log.warn("Not found: ${ex.message}")
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(404, "Not Found", ex.message ?: "Resource not found"))
    }

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException): ResponseEntity<ErrorResponse> {
        log.warn("Conflict: ${ex.message}")
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse(409, "Conflict", ex.message ?: "Conflict"))
    }

    @ExceptionHandler(BadRequestException::class)
    fun handleBadRequest(ex: BadRequestException): ResponseEntity<ErrorResponse> {
        log.warn("Bad request: ${ex.message}")
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(400, "Bad Request", ex.message ?: "Bad request"))
    }

    @ExceptionHandler(IntegrityException::class)
    fun handleIntegrity(ex: IntegrityException): ResponseEntity<ErrorResponse> {
        log.error("Integrity error: ${ex.message}")
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(500, "Integrity Error", ex.message ?: "Data integrity error"))
    }

    @ExceptionHandler(PullRequestNotFoundException::class)
    fun handlePrNotFound(ex: PullRequestNotFoundException): ResponseEntity<ErrorResponse> {
        log.warn("Pull request not found: ${ex.message}")
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse(422, "Unprocessable Entity", ex.message ?: "No pull request found for commit"))
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unexpected error", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(500, "Internal Server Error", ex.message ?: "An unexpected error occurred"))
    }
}
