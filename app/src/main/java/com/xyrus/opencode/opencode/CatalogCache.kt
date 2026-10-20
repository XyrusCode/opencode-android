package com.xyrus.opencode.opencode

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Disk cache for the model and agent catalogs.
 *
 * `/api/model` answers with 892 KB and 1107 models, identically on every connect.
 * Over a remote tunnel that is seconds of connect time spent on a list the person
 * did not ask for. Keeping the last good copy means the pickers are usable on the
 * first frame and correct by the time anyone opens them.
 *
 * Kept apart from [V2Client] and free of Android types so the awkward cases (a
 * truncated file, a missing directory) can be tested directly.
 */
object CatalogCache {

    /** What we cache: the two arrays the picker frames are built from. */
    data class Catalogs(val providers: JSONArray, val agents: JSONArray)

    /** Reads the cache, or null if it is missing, empty, or unreadable. */
    fun read(file: File?): Catalogs? {
        if (file == null || !file.isFile || file.length() == 0L) return null
        return runCatching {
            val json = JSONObject(file.readText())
            val providers = json.optJSONArray("providers") ?: return null
            Catalogs(providers, json.optJSONArray("agents") ?: JSONArray())
        }.getOrNull()
    }

    /**
     * Writes through a sibling temp file and renames.
     *
     * A process kill partway through a direct write leaves a half-written file
     * that fails to parse on every launch after, and the user just gets an empty
     * model picker with no clue why. Rename is atomic within a directory.
     */
    fun write(file: File?, catalogs: Catalogs) {
        if (file == null) return
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(
                JSONObject()
                    .put("providers", catalogs.providers)
                    .put("agents", catalogs.agents)
                    .toString()
            )
            // rename overwrites on Android, but not everywhere the JVM runs, and
            // a silently-skipped rename means the cache never refreshes after the
            // first write. Removing first keeps the refresh working everywhere.
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }
}