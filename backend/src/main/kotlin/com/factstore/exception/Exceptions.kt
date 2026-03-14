package com.factstore.exception

class NotFoundException(message: String) : RuntimeException(message)
class ConflictException(message: String) : RuntimeException(message)
class IntegrityException(message: String) : RuntimeException(message)
class BadRequestException(message: String) : RuntimeException(message)
class PullRequestNotFoundException(message: String) : RuntimeException(message)
