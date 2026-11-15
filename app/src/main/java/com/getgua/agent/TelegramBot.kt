package com.getgua.agent

import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Bot Telegram (fase 2) — Bot API resmi via HTTPS, long polling
 * getUpdates di background service. Tanpa webhook (tidak butuh
 * URL publik, cocok untuk HP).
 *
 * Keamanan — ALLOWLIST WAJIB (Pak, 15 Nov 14:37 WIB): hanya
 * chat_id yang ada di allowlist Telegram (Prefs.tgAllowlist) yang
 * dilayani; pesan dari pengirim lain diabaikan diam-diam, tanpa
 * balasan. Bila allowlist kosong, bot tidak melayani siapa pun.
 *
 * Polling hanya jalan bila token bot sudah diisi (Prefs.tgToken).
 * Bila token belum diisi, bot diam (tidak error berisik).
 *
 * Pesan dari chat_id allowlist dijalankan sebagai perintah lewat
 * AgentLoop yang sama seperti chat dalam aplikasi (satu tugas dalam
 * satu waktu — Telegram + chat dalam aplikasi berbagi antrean).
 * Jawaban loop diteruskan ke pengirim lewat sendMessage.
 *
 * Perintah khusus: /batal, /status, /layar, /start.
 *
 * Token bot TIDAK PERNAH ditulis ke log.
 */
object TelegramBot {

    private const val API_BASE = "https://api.telegram.org/bot"
    private const val POLL_TIMEOUT_SEC = 30
    private const val NO_TOKEN_SLEEP_MS = 10_000L
    private const val ERROR_SLEEP_MS = 5_000L
    private const val AUTH_ERROR_SLEEP_MS = 60_000L
    private const val MAX_MESSAGE_CHARS = 4000

    /** True saat polling aktif dengan token yang terisi. */
    @Volatile var polling = false
        private set

    @Volatile private var stopRequested = false
    private var pollThread: Thread? = null
    @Volatile private var offset: Long = 0
    private var primedToken: String? = null

    /** Mulai loop polling (dipanggil AgentService). Aman dipanggil berulang. */
    @Synchronized
    fun start() {
        if (pollThread?.isAlive == true) return
        stopRequested = false
        pollThread = thread(name = "telegram-poll", isDaemon = true) {
            pollLoop()
        }
    }

    /** Hentikan loop polling (dipanggil AgentService.onDestroy). */
    @Synchronized
    fun stop() {
        stopRequested = true
        pollThread?.interrupt()
        pollThread = null
        polling = false
    }

    /**
     * Bangunkan loop polling supaya langsung memeriksa ulang
     * (dipanggil sesudah token bot disimpan di Pengaturan).
     */
    fun kick() {
        pollThread?.interrupt()
    }

    /** Status Telegram + panduan singkat — jawaban perintah "hubungkan ke telegram". */
    fun statusText(): String {
        val sb = StringBuilder()
        sb.append("Status Telegram:\n")
        sb.append("• Token bot: ").append(if (Prefs.hasTgToken()) "sudah diisi" else "BELUM DIISI").append("\n")
        val n = Prefs.tgAllowlist().size
        sb.append("• Allowlist: ")
            .append(if (n == 0) "kosong — bot tidak melayani siapa pun" else "$n chat_id diizinkan")
            .append("\n")
        sb.append("• Polling: ").append(if (polling) "aktif" else "tidak aktif").append("\n")
        sb.append("\nCara menghubungkan:\n")
        sb.append("1. Buat bot di @BotFather (Telegram), salin tokennya.\n")
        sb.append("2. Di aplikasi ini: Pengaturan → tempel token bot → Simpan.\n")
        sb.append("3. Pengaturan → Allowlist Telegram: tambah chat_id Telegram Pak (cek via @userinfobot).\n")
        sb.append("4. Buka chat dengan bot di Telegram, kirim /start.")
        return sb.toString()
    }

    /** Status singkat agen untuk perintah /status. */
    private fun agentStatusText(): String {
        val sb = StringBuilder()
        sb.append("Status agen:\n")
        sb.append("• Service: ").append(if (AgentService.running) "jalan" else "berhenti").append("\n")
        sb.append("• Otak: ")
        if (!Prefs.hasBrainConfig()) {
            sb.append("BELUM DIATUR (isi Custom API Key di aplikasi)")
        } else if (!Prefs.hasModel()) {
            sb.append("kunci API terisi — MODEL BELUM DIPILIH")
        } else {
            sb.append("model ").append(Prefs.model).append(" (terkunci)")
        }
        sb.append("\n")
        sb.append("• Tugas: ")
        if (AgentLoop.running) {
            sb.append("mengerjakan \"").append((AgentLoop.currentTask ?: "").take(60)).append("\"")
        } else {
            sb.append("siap")
        }
        sb.append("\n")
        sb.append("• Telegram: polling ").append(if (polling) "aktif" else "tidak aktif")
            .append(", ").append(Prefs.tgAllowlist().size).append(" chat_id di allowlist")
        return sb.toString()
    }

    private fun pollLoop() {
        while (!stopRequested) {
            try {
                val token = Prefs.tgToken
                if (token.isEmpty()) {
                    polling = false
                    sleepInterruptible(NO_TOKEN_SLEEP_MS)
                    continue
                }
                // Token baru (atau pertama kali) — buang antrean update
                // lama supaya perintah usang tidak dijalankan lagi.
                if (primedToken != token) {
                    primeOffset(token)
                    primedToken = token
                    if (stopRequested) break
                }
                polling = true
                val updates = getUpdates(token)
                for (i in 0 until updates.length()) {
                    if (stopRequested) break
                    val u = updates.optJSONObject(i) ?: continue
                    try {
                        handleUpdate(token, u)
                    } catch (e: Exception) {
                        AppLog.log("telegram", "handle update gagal: ${(e.message ?: "").take(100)}")
                    }
                }
            } catch (e: InterruptedException) {
                // kick() atau stop() — lanjut loop kecuali diminta berhenti.
                if (stopRequested) break
            } catch (e: Exception) {
                polling = false
                AppLog.log("telegram", "poll error: ${(e.message ?: "").take(100)}")
                try {
                    sleepInterruptible(ERROR_SLEEP_MS)
                } catch (_: InterruptedException) {
                    if (stopRequested) break
                }
            }
        }
        polling = false
    }

    /** Buang update yang menumpuk (maks 5 batch) tanpa memprosesnya. */
    private fun primeOffset(token: String) {
        repeat(5) {
            val updates = try {
                getUpdates(token, timeoutSec = 0)
            } catch (_: Exception) {
                return
            }
            if (updates.length() == 0) return
            var maxId = offset
            for (i in 0 until updates.length()) {
                val id = updates.optJSONObject(i)?.optLong("update_id", 0) ?: 0
                if (id >= maxId) maxId = id
            }
            offset = maxId + 1
            if (updates.length() < 100) return
        }
    }

    /** Long polling getUpdates. Mengembalikan array result (bisa kosong). */
    private fun getUpdates(token: String, timeoutSec: Int = POLL_TIMEOUT_SEC): JSONArray {
        val url = "$API_BASE$token/getUpdates?timeout=$timeoutSec&offset=$offset"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = (timeoutSec + 15) * 1000
        conn.setRequestProperty("Accept", "application/json")
        val (code, body) = readResponse(conn)
        if (code == 401 || code == 403) {
            // Token salah / bot dihapus — jangan spam server.
            AppLog.log("telegram", "getUpdates HTTP $code — token bot mungkin salah")
            try {
                sleepInterruptible(AUTH_ERROR_SLEEP_MS)
            } catch (_: InterruptedException) {
            }
            return JSONArray()
        }
        if (code != 200) {
            AppLog.log("telegram", "getUpdates HTTP $code")
            return JSONArray()
        }
        val root = try {
            JSONObject(body)
        } catch (_: Exception) {
            return JSONArray()
        }
        if (!root.optBoolean("ok", false)) return JSONArray()
        return root.optJSONArray("result") ?: JSONArray()
    }

    private fun handleUpdate(token: String, update: JSONObject) {
        val updateId = update.optLong("update_id", 0)
        if (updateId >= offset) offset = updateId + 1
        val message = update.optJSONObject("message") ?: return
        val chat = message.optJSONObject("chat") ?: return
        val chatId = chat.optLong("id", 0)
        if (chatId == 0L) return
        val text = message.optString("text", "").trim()
        if (text.isEmpty()) return
        // Allowlist: diam-diam abaikan pengirim yang tidak terdaftar.
        if (!Prefs.isTgAllowed(chatId.toString())) return
        handleCommand(token, chatId, text)
    }

    private fun handleCommand(token: String, chatId: Long, text: String) {
        // Perintah bot: /nama (boleh pakai suffix @NamaBot).
        if (text.startsWith("/")) {
            val cmd = text.substringBefore(" ").substringBefore("@").lowercase()
            when (cmd) {
                "/start", "/help" -> {
                    sendMessage(
                        token, chatId,
                        "Halo Pak! Saya agen getgua. Kirim perintah seperti di chat aplikasi — mis. \"buka Chrome, cari muse ai\".\n" +
                            "Perintah khusus: /status (cek agen), /layar (kirim screenshot), /batal (hentikan tugas).",
                    )
                }
                "/batal" -> {
                    if (AgentLoop.running) {
                        AgentLoop.stop()
                        sendMessage(token, chatId, "Tugas dihentikan, Pak.")
                    } else {
                        sendMessage(token, chatId, "Tidak ada tugas yang berjalan.")
                    }
                }
                "/status" -> sendMessage(token, chatId, agentStatusText())
                "/layar" -> sendScreenshot(token, chatId)
                else -> sendMessage(token, chatId, "Perintah tidak dikenal. Yang tersedia: /status, /layar, /batal.")
            }
            return
        }

        // Jawaban konfirmasi "ya/tidak" untuk aksi berisiko yang tertunda.
        if (AgentLoop.pendingConfirmation != null) {
            val lower = text.lowercase()
            when (lower) {
                "ya", "iya", "y", "ok", "oke", "lanjut", "lanjutkan", "setuju", "boleh" -> {
                    AgentLoop.confirm(true)
                    sendMessage(token, chatId, "Baik, Pak — melanjutkan satu aksi itu.")
                    return
                }
                "tidak", "nggak", "gak", "ga", "t", "batal", "jangan" -> {
                    AgentLoop.confirm(false)
                    return
                }
                else -> {
                    // Perintah baru = batalkan konfirmasi, jalankan perintah baru.
                    AgentLoop.confirm(false)
                    waitForIdle(3000)
                }
            }
        }

        if (AgentLoop.running) {
            sendMessage(token, chatId, "Agen sedang mengerjakan tugas lain, Pak. Tunggu selesai, atau kirim /batal dulu.")
            return
        }

        // Jalankan sebagai tugas lewat AgentLoop yang sama — jawaban
        // loop diteruskan ke chat ini lewat reply sink.
        val threadId = "tg:$chatId"
        ChatStore.ensureThread(threadId, "Telegram $chatId")
        val sink: (String) -> Unit = { reply -> sendMessage(token, chatId, reply) }
        AgentLoop.start(threadId, text, sink)
    }

    /** Tunggu loop agen berhenti (maks [timeoutMs]) — dipakai sesudah batal konfirmasi. */
    private fun waitForIdle(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!AgentLoop.running) return
            try {
                Thread.sleep(200)
            } catch (_: InterruptedException) {
                return
            }
        }
    }

    /** Kirim screenshot layar sebagai foto. */
    private fun sendScreenshot(token: String, chatId: Long) {
        val png = try {
            Screenshot.capturePng()
        } catch (_: Exception) {
            null
        }
        if (png == null) {
            val problem = try {
                Automation.permissionProblem()
            } catch (_: Exception) {
                null
            }
            val reason = if (problem != null) {
                "Screenshot gagal diambil: $problem."
            } else {
                "Screenshot gagal diambil (mungkin layar aplikasi ini dilindungi — FLAG_SECURE)."
            }
            sendMessage(token, chatId, reason)
            return
        }
        val jpeg = try {
            Screenshot.toJpeg(png, 70, 720)
        } catch (_: Exception) {
            null
        }
        val ok = if (jpeg != null) {
            sendPhoto(token, chatId, jpeg, "layar.jpg", "image/jpeg")
        } else {
            sendPhoto(token, chatId, png, "layar.png", "image/png")
        }
        if (!ok) sendMessage(token, chatId, "Screenshot gagal dikirim ke Telegram.")
    }

    /** Kirim pesan teks. Potong ke batas Telegram (4096 karakter). */
    fun sendMessage(token: String, chatId: Long, text: String): Boolean {
        val trimmed = text.take(MAX_MESSAGE_CHARS)
        return try {
            val body = JSONObject()
                .put("chat_id", chatId)
                .put("text", trimmed)
            val conn = URL("$API_BASE$token/sendMessage").openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val (code, respBody) = readResponse(conn)
            if (code != 200) {
                AppLog.log("telegram", "sendMessage HTTP $code")
                return false
            }
            JSONObject(respBody).optBoolean("ok", false)
        } catch (e: Exception) {
            AppLog.log("telegram", "sendMessage gagal: ${(e.message ?: "").take(100)}")
            false
        }
    }

    /** Kirim foto (multipart/form-data). */
    private fun sendPhoto(token: String, chatId: Long, bytes: ByteArray, fileName: String, mime: String): Boolean {
        val boundary = "----AgenGetgua" + System.currentTimeMillis()
        return try {
            val conn = URL("$API_BASE$token/sendPhoto").openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setRequestProperty("Accept", "application/json")
            val out = DataOutputStream(conn.outputStream)
            val crlf = "\r\n"
            out.writeBytes("--$boundary$crlf")
            out.writeBytes("Content-Disposition: form-data; name=\"chat_id\"$crlf$crlf")
            out.writeBytes(chatId.toString() + crlf)
            out.writeBytes("--$boundary$crlf")
            out.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"$fileName\"$crlf")
            out.writeBytes("Content-Type: $mime$crlf$crlf")
            out.write(bytes)
            out.writeBytes(crlf)
            out.writeBytes("--$boundary--$crlf")
            out.flush()
            out.close()
            val (code, respBody) = readResponse(conn)
            if (code != 200) {
                AppLog.log("telegram", "sendPhoto HTTP $code")
                return false
            }
            JSONObject(respBody).optBoolean("ok", false)
        } catch (e: Exception) {
            AppLog.log("telegram", "sendPhoto gagal: ${(e.message ?: "").take(100)}")
            false
        }
    }

    private fun readResponse(conn: HttpURLConnection): Pair<Int, String> {
        val code = try {
            conn.responseCode
        } catch (e: Exception) {
            return -1 to (e.message ?: "")
        }
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = try {
            stream?.readBytes()?.toString(Charsets.UTF_8) ?: ""
        } catch (_: Exception) {
            ""
        } finally {
            try {
                conn.disconnect()
            } catch (_: Exception) {
            }
        }
        return code to body
    }

    /** Tidur yang bisa diinterupsi kick()/stop() tanpa menelan flag berhenti. */
    @Throws(InterruptedException::class)
    private fun sleepInterruptible(ms: Long) {
        Thread.sleep(ms)
    }
}
