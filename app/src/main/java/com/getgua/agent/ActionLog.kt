package com.getgua.agent

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Riwayat Aksi — jejak audit lokal setiap aksi agen.
 *
 * Satu baris JSON per aksi di filesDir/action_log.jsonl.
 * result: "ok" (berhasil), "fail" (gagal), "fixed" (berhasil
 * setelah dikoreksi sendiri). Tidak berisi kredensial.
 */
object ActionLog {

    const val OK = "ok"
    const val FAIL = "fail"
    const val FIXED = "fixed"

    private const val MAX_ENTRIES = 500

    data class Entry(
        val time: String,
        val date: String,
        val text: String,
        val result: String,
        val detail: String,
    )

    @Volatile private var logFile: File? = null

    fun init(context: Context) {
        try {
            logFile = File(context.filesDir, "action_log.jsonl")
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun log(text: String, result: String, detail: String = "") {
        val f = logFile ?: return
        try {
            if (f.exists() && f.length() > 300_000L) {
                val lines = f.readLines()
                val kept = lines.takeLast(MAX_ENTRIES)
                f.writeText(kept.joinToString("\n") + if (kept.isNotEmpty()) "\n" else "")
            }
            val now = Date()
            val time = SimpleDateFormat("HH:mm", Locale.US).format(now)
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
            val obj = JSONObject()
                .put("time", time)
                .put("date", date)
                .put("text", text)
                .put("result", result)
                .put("detail", detail)
            f.appendText(obj.toString() + "\n")
        } catch (_: Exception) {
        }
    }

    /** Entri terbaru dulu, maksimal [limit]. */
    fun list(limit: Int = 100): List<Entry> {
        val f = logFile ?: return emptyList()
        return try {
            if (!f.exists()) return emptyList()
            f.readLines().takeLast(limit).reversed().mapNotNull { line ->
                try {
                    val o = JSONObject(line)
                    Entry(
                        time = o.optString("time", ""),
                        date = o.optString("date", ""),
                        text = o.optString("text", ""),
                        result = o.optString("result", OK),
                        detail = o.optString("detail", ""),
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun clear() {
        try {
            logFile?.writeText("")
        } catch (_: Exception) {
        }
    }
}
