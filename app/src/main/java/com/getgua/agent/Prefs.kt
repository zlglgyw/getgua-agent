package com.getgua.agent

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Pengaturan aplikasi di SharedPreferences privat (MODE_PRIVATE).
 *
 * Kunci API dan token TIDAK PERNAH ditampilkan nilainya di UI —
 * hanya tersamar (•••• + 4 karakter terakhir) dan tidak pernah
 * ditulis ke log.
 */
object Prefs {

    const val URL_9ROUTER = "http://localhost:20128/v1"

    private const val FILE = "agen_prefs"
    private const val KEY_MODEL = "model"
    private const val KEY_API_URL = "api_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_TG_TOKEN = "token_telegram"
    private const val KEY_TG_ALLOWLIST = "tg_allowlist"
    private const val KEY_ALLOWLIST = "allowlist"
    private const val KEY_REPORT_TIME = "report_time"
    private const val KEY_USAGE_CALLS = "usage_calls"
    private const val KEY_USAGE_TOKENS = "usage_tokens"
    private const val KEY_SCHEDULES = "schedules_json"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
    }

    /**
     * Model otak yang DIKUNCI Pak (v1.0.1). Tidak ada model default —
     * kosong sampai Pak memilih lewat Tes Otak (Status) atau mengetik
     * ID model bebas di Pengaturan. Otak TIDAK PERNAH memakai model
     * lain selain yang terkunci di sini.
     */
    var model: String
        get() = prefs.getString(KEY_MODEL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim()).apply()

    fun hasModel(): Boolean = model.isNotEmpty()

    /** Alamat endpoint OpenAI-compatible (9Router atau provider lain). */
    var apiUrl: String
        get() = prefs.getString(KEY_API_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_URL, value.trim().trimEnd('/')).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    fun hasApiKey(): Boolean = apiKey.isNotEmpty()
    fun hasBrainConfig(): Boolean = apiKey.isNotEmpty() && apiUrl.isNotEmpty()

    /** Kunci tersamar: •••• + 4 karakter terakhir. */
    fun maskedApiKey(): String {
        val k = apiKey
        if (k.isEmpty()) return ""
        if (k.length <= 4) return "••••"
        return "••••••••" + k.takeLast(4)
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_API_KEY).remove(KEY_API_URL).apply()
    }

    /** Token bot Telegram (fase 2 — baru disimpan, belum dipakai). */
    var tgToken: String
        get() = prefs.getString(KEY_TG_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TG_TOKEN, value).apply()

    fun hasTgToken(): Boolean = tgToken.isNotEmpty()

    /**
     * Allowlist chat_id Telegram — WAJIB (Pak, 15 Nov 14:37 WIB).
     * Hanya chat_id yang terdaftar yang dilayani bot; pesan dari
     * pengirim lain diabaikan diam-diam. Botnya sendiri baru fase 2,
     * tapi daftar ini sudah dikonfigurasi sejak fase 1.
     */
    fun tgAllowlist(): List<String> {
        val raw = prefs.getString(KEY_TG_ALLOWLIST, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        } catch (_: Exception) {
            raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }
    }

    /** Tambah chat_id. False bila kosong / sudah ada. */
    fun addTgAllowlist(id: String): Boolean {
        val clean = id.trim()
        if (clean.isEmpty()) return false
        val list = tgAllowlist().toMutableList()
        if (clean in list) return false
        list.add(clean)
        saveTgAllowlist(list)
        return true
    }

    fun removeTgAllowlist(id: String) {
        saveTgAllowlist(tgAllowlist().filter { it != id })
    }

    /** Cek izin pengirim (dipakai bot Telegram fase 2). */
    fun isTgAllowed(chatId: String): Boolean = chatId.trim() in tgAllowlist()

    private fun saveTgAllowlist(list: List<String>) {
        val arr = JSONArray()
        for (id in list) arr.put(id)
        prefs.edit().putString(KEY_TG_ALLOWLIST, arr.toString()).apply()
    }

    /** Allowlist pengirim (fase 2/3 — nomor/chat_id dipisah koma). */
    var allowlist: String
        get() = prefs.getString(KEY_ALLOWLIST, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ALLOWLIST, value).apply()

    /** Jam laporan harian (fase 5). */
    var reportTime: String
        get() = prefs.getString(KEY_REPORT_TIME, "06:00") ?: "06:00"
        set(value) = prefs.edit().putString(KEY_REPORT_TIME, value).apply()

    // --- Pemakaian otak (dicatat supaya tidak ada tagihan kejutan). ---
    var usageCalls: Int
        get() = prefs.getInt(KEY_USAGE_CALLS, 0)
        private set(value) = prefs.edit().putInt(KEY_USAGE_CALLS, value).apply()

    var usageTokens: Int
        get() = prefs.getInt(KEY_USAGE_TOKENS, 0)
        private set(value) = prefs.edit().putInt(KEY_USAGE_TOKENS, value).apply()

    fun addUsage(tokens: Int) {
        usageCalls += 1
        if (tokens > 0) usageTokens += tokens
    }

    // --- Jadwal klaim (fase 5 — disimpan, belum dijalankan otomatis). ---
    var schedulesJson: String
        get() = prefs.getString(KEY_SCHEDULES, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SCHEDULES, value).apply()
}
