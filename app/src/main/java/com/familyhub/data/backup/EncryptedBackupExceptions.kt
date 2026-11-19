package com.familyhub.data.backup

/**
 * Wrong password, truncated/tampered ciphertext, or GCM tag failure.
 * Message must never include password or key material.
 */
class EncryptedBackupAuthException :
    Exception("Encrypted backup authentication failed")

/** Magic/version/header fields that identify an unsupported or future format. */
class EncryptedBackupUnsupportedFormatException(message: String) :
    Exception(message)

/** Structural/limits violations discovered before or instead of crypto auth. */
class EncryptedBackupFormatException(message: String) :
    Exception(message)

/**
 * Password fails contract checks (length, UTF-16 well-formedness, UTF-8 size).
 * Message must never include the password itself.
 */
class EncryptedBackupPasswordException(message: String) :
    Exception(message)
