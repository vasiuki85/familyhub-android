package com.familyhub.domain.utils

class MenuMutationForbiddenException(
    message: String = "Menu mutation forbidden"
) : IllegalStateException(message)
