package com.xyrus.opencode.opencode

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** How the app was given access to a server. */
enum class AuthKind { Pairing, Password }

/**
 * One opencode server the phone can talk to.
 *
 * [secret] is either a pairing token or a server password; both are sent as the
 * password half of Basic auth. [baseUrl] is scheme + host + port, e.g.
 * "http://192.168.1.5:4096" for the LAN service or the OpenTunnel address after
 * `opencode pair --remote`.
 */
data class ServerConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val secret: String,
    val kind: AuthKind = AuthKind.Pairing,
    val username: String = PairCredential.USERNAME,
) {
    val isExpired: Boolean get() = PairCredential.isExpired(secret)
    val daysRemaining: Long? get() = PairCredential.daysRemaining(secret)

    fun toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("baseUrl", baseUrl)
        .put("secret", secret)
        .put("kind", kind.name)
        .put("username", username)

    companion object {
        fun fromJson(o: JSONObject) = ServerConfig(
            id = o.optString("id"),
            name = o.optString("name"),
            baseUrl = o.optString("baseUrl"),
            secret = o.optString("secret"),
            kind = runCatching { AuthKind.valueOf(o.optString("kind")) }.getOrDefault(AuthKind.Pairing),
            username = o.optString("username").ifEmpty { PairCredential.USERNAME },
        )
    }
}

/**
 * Saved servers plus which one is selected.
 *
 * Secrets live here, so this is deliberately not world-readable and is kept
 * apart from cosmetic preferences. Note this is plain prefs, not an encrypted
 * store: adequate for a LAN token, and a follow-up should move it behind the
 * Android Keystore if servers ever gain remote exposure.
 */
class ServerStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("opencode.servers", Context.MODE_PRIVATE)

    fun servers(): List<ServerConfig> {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { ServerConfig.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun save(config: ServerConfig) {
        val all = servers().filterNot { it.id == config.id } + config
        write(all)
    }

    fun delete(id: String) {
        write(servers().filterNot { it.id == id })
        if (selectedId == id) prefs.edit().remove(KEY_SELECTED).apply()
    }

    var selectedId: String?
        get() = prefs.getString(KEY_SELECTED, null)
        set(value) = prefs.edit().putString(KEY_SELECTED, value).apply()

    fun selected(): ServerConfig? = servers().firstOrNull { it.id == selectedId }

    /** Which transport to use; the bridge stays available as a fallback. */
    var useBridge: Boolean
        get() = prefs.getBoolean(KEY_USE_BRIDGE, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_BRIDGE, value).apply()

    /** Legacy bridge coordinates, kept so the fallback still works. */
    var bridgeHost: String
        get() = prefs.getString(KEY_BRIDGE_HOST, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BRIDGE_HOST, value).apply()

    var bridgePort: Int
        get() = prefs.getInt(KEY_BRIDGE_PORT, 9876)
        set(value) = prefs.edit().putInt(KEY_BRIDGE_PORT, value).apply()

    /** Forget pairing credentials and saved addresses, keeping preferences. */
    fun clearCache() {
        prefs.edit().remove(KEY_LIST).remove(KEY_SELECTED).apply()
    }

    private fun write(list: List<ServerConfig>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    private companion object {
        const val KEY_LIST = "servers"
        const val KEY_SELECTED = "selected"
        const val KEY_USE_BRIDGE = "use_bridge"
        const val KEY_BRIDGE_HOST = "bridge_host"
        const val KEY_BRIDGE_PORT = "bridge_port"
    }
}