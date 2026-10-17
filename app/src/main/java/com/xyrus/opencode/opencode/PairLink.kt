package com.xyrus.opencode.opencode

import org.json.JSONObject

/**
 * Working out how to reach a server from what the user typed or scanned.
 *
 * `opencode pair` prints a QR whose payload is JSON carrying one pairing code
 * and every address the machine answers on, already ranked:
 *
 *   {"code":"GjH-wVfX2KIOe3FzKi2Z4g",
 *    "urls":["http://100.96.0.2:45278", ...]}
 *
 * Tailscale comes first, then LAN, then link-local, then the remote tunnel.
 * Loopback is left out because it is never reachable from a phone. So one scan
 * supplies the code *and* every candidate, and the app only has to find which
 * one answers from where the phone happens to be.
 */
object PairLink {

    /** A pairing session: one code, and the addresses to try for it, best first. */
    data class Parts(val code: String, val bases: List<String>) {
        val primary: String get() = bases.first()
    }

    private val PRIVATE_10 = Regex("^10\\.")
    private val PRIVATE_172 = Regex("^172\\.(1[6-9]|2\\d|3[01])\\.")
    private val PRIVATE_192 = Regex("^192\\.168\\.")
    /** 100.64.0.0/10 is the CGNAT range Tailscale hands out. */
    private val TAILSCALE = Regex("^100\\.(6[4-9]|[7-9]\\d|1[01]\\d|12[0-7])\\.")
    private val LINK_LOCAL = Regex("^169\\.254\\.")

    /**
     * Loopback and private LAN addresses speak plain HTTP; everything else,
     * including the remote tunnel, needs HTTPS.
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

    /** True for the addresses worth trying first: Tailscale, then the LAN. */
    private fun rank(base: String): Int {
        val hostname = base.substringAfter("://", base)
            .substringBeforeLast(':').removeSurrounding("[", "]")
        return when {
            TAILSCALE.containsMatchIn(hostname) -> 0
            PRIVATE_10.containsMatchIn(hostname) ||
                PRIVATE_192.containsMatchIn(hostname) -> 1
            PRIVATE_172.containsMatchIn(hostname) -> 2
            LINK_LOCAL.containsMatchIn(hostname) -> 3
            base.startsWith("https://") -> 4
            else -> 5
        }
    }

    private fun order(bases: List<String>): List<String> =
        bases.distinct().sortedBy { rank(it) }

    /**
     * Read a scanned QR payload.
     *
     * opencode encodes JSON. Anything else is tolerated so a plain URL, a
     * terminal one-liner, or a bare code pasted by hand still works.
     */
    fun fromScan(raw: String): Parts? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        if (text.startsWith("{")) {
            runCatching {
                val json = JSONObject(text)
                val code = json.optString("code")
                val arr = json.optJSONArray("urls")
                val urls = buildList {
                    for (i in 0 until (arr?.length() ?: 0)) {
                        arr!!.optString(i).takeIf { it.isNotBlank() }?.let { add(normalizeBase(it)) }
                    }
                }
                if (code.isNotBlank()) return Parts(code, order(urls.ifEmpty { listOf("") }))
            }
            return null
        }

        val code = extractCode(text) ?: return null
        val base = if (text.contains("/auth/connect/")) {
            normalizeBase(text.substringBefore("/auth/connect/"))
        } else {
            null
        }
        return Parts(code, order(listOfNotNull(base)))
    }

    /** The one-time code from a full pair link, or null if it is not one. */
    fun extractCode(raw: String): String? =
        raw.trim()
            .substringAfterLast("/auth/connect/", "")
            .trim()
            .takeIf { it.isNotEmpty() && it != raw.trim() }

    /**
     * Resolve the two manual fields into a code and the addresses to try.
     *
     * Handles a full link pasted into either field. Returns null when no usable
     * code was supplied.
     */
    fun resolve(addressInput: String, codeInput: String): Parts? {
        val address = addressInput.trim()
        val code = codeInput.trim()

        val fromAddress = extractCode(address)
        if (fromAddress != null) {
            return Parts(
                fromAddress,
                order(listOf(normalizeBase(address.substringBefore("/auth/connect/")))),
            )
        }

        val fromCode = extractCode(code)
        if (fromCode != null) {
            return Parts(fromCode, order(listOf(normalizeBase(address))))
        }

        if (code.isEmpty() || address.isEmpty()) return null
        return Parts(code, order(listOf(normalizeBase(address))))
    }
}