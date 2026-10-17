package com.xyrus.opencode.opencode

/**
 * opencode v2 hands out a credential when you pair or set a password. Both are
 * sent as the password half of HTTP Basic auth with the fixed username
 * "opencode"; a bearer token is rejected.
 *
 * Paired credentials are not permanent. They look like `<unixExpiry>.<random>`
 * where the prefix is the second they stop working, which the live server sets
 * 30 days out. Treating a saved server as valid forever would leave the app
 * silently 401ing weeks later, so callers check [isExpired] and re-pair.
 */
object PairCredential {

    const val USERNAME = "opencode"

    /** Epoch millis the credential stops working, or null if it isn't a paired token. */
    fun expiresAtMillis(token: String): Long? {
        val prefix = token.substringBefore('.', "").toLongOrNull() ?: return null
        // A unix second count for any year from 2001 on; anything else is noise.
        return if (prefix in 1_000_000_000L..4_000_000_000L) prefix * 1000L else null
    }

    /** Unknown or password-style credentials never expire. */
    fun isExpired(token: String, now: Long = System.currentTimeMillis()): Boolean {
        val expiry = expiresAtMillis(token) ?: return false
        return now >= expiry
    }

    /** Whole days left, for the "re-pair soon" warning. Null when not a paired token. */
    fun daysRemaining(token: String, now: Long = System.currentTimeMillis()): Long? {
        val expiry = expiresAtMillis(token) ?: return null
        return ((expiry - now) / 86_400_000L).coerceAtLeast(0L)
    }
}