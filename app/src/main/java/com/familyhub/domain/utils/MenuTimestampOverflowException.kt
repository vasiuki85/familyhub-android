package com.familyhub.domain.utils

class MenuTimestampOverflowException(
    message: String = "Menu mutation timestamp overflow"
) : IllegalStateException(message)
