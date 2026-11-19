package com.familyhub.domain.model

class AccountingTransitionForbiddenException(message: String) : IllegalStateException(message)

class AccountingTransitionInvalidException(message: String) : IllegalStateException(message)

class AccountingIdempotencyConflictException(message: String) : IllegalStateException(message)

class AccountingOverflowException(message: String) : ArithmeticException(message)
