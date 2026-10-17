package com.xyrus.opencode.opencode

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Turns a pairing code into a usable credential.
 *
 * `opencode pair` issues one code that answers on several addresses (Tailscale,
 * LAN, link-local, and the remote tunnel). The phone may only have a route to
 * one of them, so every candidate is tried in [PairLink]'s preference order and
 * the first that answers wins. A candidate that cannot be reached is skipped;
 * a candidate that answers but refuses the code is remembered, because that
 * means the code itself was the problem.
 */
object Pairer {

    sealed interface Outcome {
        /** [base] is the address that accepted the code. */
        data class Paired(val base: String, val token: String) : Outcome

        /** Every candidate answered but none accepted the code. */
        data class Rejected(val detail: String) : Outcome

        /** No candidate answered at all. */
        data class Unreachable(val tried: List<String>) : Outcome
    }

    fun pair(code: String, bases: List<String>, timeoutSeconds: Long = 10): Outcome {
        if (bases.isEmpty()) return Outcome.Unreachable(emptyList())

        val client = OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds * 2, TimeUnit.SECONDS)
            .build()

        var answered = 0
        var lastError: String? = null

        for (base in bases) {
            val attempt = exchange(client, base, code)
            when (attempt) {
                is Exchange.Token -> return Outcome.Paired(base, attempt.token)
                is Exchange.Refused -> {
                    // The server is there but said no: the code is stale, used,
                    // or belongs to a different machine. No other address will
                    // do better, so stop rather than hammering the rest.
                    return Outcome.Rejected(attempt.detail)
                }
                is Exchange.Unreachable -> lastError = attempt.detail
            }
            answered++
        }

        return Outcome.Unreachable(bases)
            .let { if (lastError != null) Outcome.Unreachable(bases) else it }
    }

    private sealed interface Exchange {
        data class Token(val token: String) : Exchange
        data class Refused(val detail: String) : Exchange
        data class Unreachable(val detail: String) : Exchange
    }

    private fun exchange(client: OkHttpClient, base: String, code: String): Exchange =
        try {
            val request = Request.Builder()
                .url("${base.trimEnd('/')}/auth/connect/$code")
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    // The server answered, so the address was right.
                    Exchange.Refused("HTTP ${resp.code}")
                } else {
                    val token = org.json.JSONObject(resp.body?.string().orEmpty())
                        .optString("token")
                    if (token.isBlank()) Exchange.Refused("no token in response") else Exchange.Token(token)
                }
            }
        } catch (e: IOException) {
            // DNS failure, refused connection, timeout: try the next address.
            Log.d("Pairer", "$base unreachable: ${e.message}")
            Exchange.Unreachable(e.message ?: "no route")
        } catch (e: Exception) {
            Log.w("Pairer", "unexpected pairing failure at $base", e)
            Exchange.Unreachable(e.message ?: "unknown")
        }
}