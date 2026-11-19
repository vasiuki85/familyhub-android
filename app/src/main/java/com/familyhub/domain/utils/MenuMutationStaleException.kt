package com.familyhub.domain.utils

class MenuMutationStaleException(
    message: String = "Menu mutation stale snapshot"
) : IllegalStateException(message)
