package com.xyrus.opencode.opencode

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
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

    fun pair(code: String, bases: List<String>, timeoutSeconds: Long = 4): Outcome {
        if (bases.isEmpty()) return Outcome.Unreachable(emptyList())

        val client = OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()

        // Probe every candidate at once. Trying them in order cost a full
        // timeout per unreachable address, so a phone that could only reach the
        // tunnel sat through four dead LAN attempts before connecting. In
        // parallel the whole set costs one timeout.
        val pool = Executors.newFixedThreadPool(minOf(bases.size, 8))
        val results = ConcurrentHashMap<Int, Exchange>()
        val latch = CountDownLatch(bases.size)
        try {
            bases.forEachIndexed { index, base ->
                pool.execute {
                    results[index] = exchange(client, base, code)
                    latch.countDown()
                }
            }
            // Give the slowest candidate one window, then decide on what we have.
            latch.await(timeoutSeconds, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
            client.dispatcher.executorService.shutdown()
        }

        // bases is already in preference order, so the earliest success wins:
        // Tailscale if it answered, otherwise the LAN, otherwise the tunnel.
        var refused: String? = null
        for (index in bases.indices) {
            when (val outcome = results[index]) {
                is Exchange.Token -> return Outcome.Paired(bases[index], outcome.token)
                // Remembered rather than returned: a stale server on one address
                // can refuse a code the live one accepts.
                is Exchange.Refused -> if (refused == null) refused = outcome.detail
                is Exchange.Unreachable -> Unit
                null -> Unit
            }
        }
        // Only a refusal is a real diagnosis. Anything else means we simply never
        // got a route to the machine.
        return if (refused != null) Outcome.Rejected(refused) else Outcome.Unreachable(bases)
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