package com.xyrus.opencode.opencode

/**
 * Working out how to reach a server from what the user typed.
 *
 * `opencode pair` prints a link like
 * `https://abc.opentunnel.xyz/auth/connect/CODE`, and people paste that whole
 * link into either the address field or the code field. Both have to work,
 * because both are what a person naturally does.
 */
object PairLink {

    data class Parts(val base: String, val code: String)

    private val PRIVATE_10 = Regex("^10\\.")
    private val PRIVATE_172 = Regex("^172\\.(1[6-9]|2\\d|3[01])\\.")
    private val PRIVATE_192 = Regex("^192\\.168\\.")

    /**
     * Loopback and private LAN addresses speak plain HTTP; everything else,
     * including the OpenTunnel address, needs HTTPS.
     *
     * Defaulting the other way round was a real bug: a bare hostname got
     * `http://` prepended and the request to an HTTPS-only tunnel failed.
     */
    fun normalizeBase(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        val authority = trimmed.substringAfter("://", trimmed)
        // Hostname without the port, so "macbook.local:4096" still reads as .local.
        val hostname = authority.substringBeforeLast(':').removeSurrounding("[", "]")
        val scheme = when {
            trimmed.startsWith("https://", ignoreCase = true) -> "https://"
            trimmed.startsWith("http://", ignoreCase = true) -> "http://"
            hostname == "localhost" || hostname == "::1" || hostname.startsWith("127.") -> "http://"
            PRIVATE_10.containsMatchIn(hostname) ||
                PRIVATE_172.containsMatchIn(hostname) ||
                PRIVATE_192.containsMatchIn(hostname) -> "http://"
            // mDNS/Bonjour names such as macbook.local are on the LAN too, and
            // a dot in the name does not make them remote.
            hostname.endsWith(".local", ignoreCase = true) -> "http://"
            // A bare hostname (devbox, my-machine) is not TLS either.
            !hostname.contains('.') -> "http://"
            else -> "https://"
        }
        return scheme + authority
    }

    /** The one-time code from a full pair link, or null if it is not one. */
    fun extractCode(raw: String): String? =
        raw.trim()
            .substringAfterLast("/auth/connect/", "")
            .trim()
            .takeIf { it.isNotEmpty() && it != raw.trim() }

    /**
     * Resolve the two fields into an address and a code.
     *
     * Returns null when no usable code was supplied. Handles a full link pasted
     * into either field.
     */
    fun resolve(addressInput: String, codeInput: String): Parts? {
        val address = addressInput.trim()
        val code = codeInput.trim()

        val fromAddress = extractCode(address)
        if (fromAddress != null) {
            return Parts(normalizeBase(address.substringBefore("/auth/connect/")), fromAddress)
        }

        val fromCode = extractCode(code)
        if (fromCode != null) {
            return Parts(normalizeBase(address), fromCode)
        }

        if (code.isEmpty() || address.isEmpty()) return null
        return Parts(normalizeBase(address), code)
    }
}