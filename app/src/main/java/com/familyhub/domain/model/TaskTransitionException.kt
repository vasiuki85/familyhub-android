package com.familyhub.domain.model

class TaskTransitionForbiddenException(message: String) : IllegalStateException(message)

class TaskTransitionInvalidException(message: String) : IllegalStateException(message)
