package com.familyhub.data.sync

class FamilyBindingRefusedException(
    message: String
) : IllegalStateException(message)

class LocalDataFamilyMismatchException(
    message: String
) : IllegalStateException(message)
