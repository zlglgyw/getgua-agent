package com.getgua.agent

import android.content.Context
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * Loop otak agen (fase 1).
 *
 * Perintah → panggil model (function calling dulu, jatuh ke mode
 * blok JSON bila model tak mendukung) → eksekusi tool → observasi
 * baru → ulang sampai selesai / batas langkah / dihentikan Pak.
 *
 * Koreksi diri: tiap aksi diverifikasi (hasil + perubahan UI).
 * Aksi gagal → model diminta coba CARA LAIN; sesudah 3 kegagalan
 * beruntun untuk langkah yang sama, tugas dihentikan dan dilaporkan
 * ke Pak — tidak nekat mengulang tanpa henti.
 *
 * Batas otonomi: aksi berisiko (tap tombol beli/bayar/sign/connect
 * wallet) DITOLAK Automation dan loop JEDA menunggu konfirmasi Pak
 * di chat ("ya" = lanjutkan satu aksi itu, selain itu = batal).
 * Satu tugas dalam satu waktu.
 */
object AgentLoop {

    const val MAX_STEPS = 25
    const val MAX_RETRIES_PER_STEP = 3

    interface Listener {
        fun onStatus(text: String)
        fun onMessage(threadId: String, from: String, text: String)
        fun onFinished(threadId: String)
    }

    data class PendingConfirmation(
        val toolName: String,
        val argsJson: String,
        val reason: String,
    )

    @Volatile var running = false
        private set
    @Volatile var statusText: String = "Siap"
        private set
    @Volatile var currentTask: String? = null
        private set
    @Volatile var pendingConfirmation: PendingConfirmation? = null
        private set

    @Volatile var listener: Listener? = null

    @Volatile private var stopRequested = false
    @Volatile private var confirmDecision: Boolean? = null
    private var screenW = 720
    private var screenH = 1640

    fun init(context: Context) {
        try {
            val (w, h) = DeviceInfo.screenSize(context.applicationContext)
            screenW = w
            screenH = h
        } catch (_: Exception) {
        }
    }

    /** Mulai tugas baru. False bila agen sedang sibuk. */
    @Synchronized
    fun start(threadId: String, command: String): Boolean {
        if (running) return false
        if (!Prefs.hasBrainConfig()) {
            postToChat(threadId, "agent", "Otak belum diatur, Pak. Isi dulu Custom API Key di menu Status (alamat + kunci API), lalu coba lagi.")
            return false
        }
        running = true
        stopRequested = false
        confirmDecision = null
        pendingConfirmation = null
        currentTask = command
        setStatus("Mengerjakan: $command")
        thread(name = "agent-loop", isDaemon = true) {
            try {
                runLoop(threadId, command)
            } catch (e: Exception) {
                postToChat(threadId, "agent", "Terjadi kesalahan di loop agen: ${(e.message ?: "").take(150)}")
                ActionLog.log(command.take(60), ActionLog.FAIL, "loop exception")
            } finally {
                running = false
                currentTask = null
                pendingConfirmation = null
                setStatus("Siap")
                try {
                    listener?.onFinished(threadId)
                } catch (_: Exception) {
                }
            }
        }
        return true
    }

    /** Hentikan tugas yang sedang berjalan (tombol HENTIKAN AGEN). */
    fun stop() {
        stopRequested = true
        confirmDecision = false
    }

    /**
     * Jawab konfirmasi yang tertunda. Dipanggil dari chat saat Pak
     * membalas "ya"/"tidak" (atau perintah baru = batal + perintah baru).
     */
    fun confirm(yes: Boolean) {
        confirmDecision = yes
    }

    private fun setStatus(text: String) {
        statusText = text
        try {
            listener?.onStatus(text)
        } catch (_: Exception) {
        }
    }

    private fun postToChat(threadId: String, from: String, text: String) {
        try {
            ChatStore.addMessage(threadId, from, text)
        } catch (_: Exception) {
        }
        try {
            listener?.onMessage(threadId, from, text)
        } catch (_: Exception) {
        }
    }

    private fun runLoop(threadId: String, command: String) {
        val lessons = LearningStore.relevantLessons(command)
        var jsonMode = false
        var emptyTurns = 0
        var consecutiveFailures = 0
        var steps = 0

        val systemPrompt = buildSystemPrompt(lessons, jsonMode = false)
        val messages = ArrayList<JSONObject>()
        messages.add(JSONObject().put("role", "system").put("content", systemPrompt))
        messages.add(
            JSONObject().put("role", "user").put(
                "content",
                "Perintah: $command\n\nLayar saat ini:\n" + AgentTools.uiDumpText(),
            ),
        )

        while (steps < MAX_STEPS) {
            if (stopRequested) {
                postToChat(threadId, "agent", "Tugas dihentikan, Pak.")
                ActionLog.log(command.take(60), ActionLog.FAIL, "dihentikan Pak")
                return
            }
            steps++

            val tools = if (jsonMode) null else AgentTools.definitionsJson()
            val result = BrainClient.chat(messages, tools)

            if (result.error != null) {
                if (result.toolsRejected && !jsonMode) {
                    // Model tak dukung function calling — pindah ke mode JSON.
                    jsonMode = true
                    messages[0] = JSONObject().put("role", "system").put("content", buildSystemPrompt(lessons, jsonMode = true))
                    messages.add(JSONObject().put("role", "user").put("content", "Mode function calling tidak didukung. Lanjutkan dengan mode JSON."))
                    AppLog.log("agent", "function calling ditolak server — pindah ke mode JSON")
                    continue
                }
                postToChat(threadId, "agent", "Otak tidak bisa dihubungi: ${result.error}")
                ActionLog.log(command.take(60), ActionLog.FAIL, result.error.take(120))
                return
            }

            // --- Parse aksi dari respons. ---
            val action = parseAction(result)
            if (action == null) {
                val text = result.content?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    // Model menjawab teks tanpa aksi — anggap jawaban akhir.
                    postToChat(threadId, "agent", text)
                    ActionLog.log(command.take(60), ActionLog.OK, "dijawab tanpa aksi")
                    return
                }
                emptyTurns++
                if (emptyTurns >= 2) {
                    postToChat(threadId, "agent", "Model tidak memberi aksi, Pak. Tugas dihentikan.")
                    ActionLog.log(command.take(60), ActionLog.FAIL, "model tidak memberi aksi")
                    return
                }
                messages.add(JSONObject().put("role", "user").put("content", "Kamu belum memberi aksi. Berikan satu aksi berikutnya."))
                continue
            }
            emptyTurns = 0

            val (toolName, args) = action
            if (toolName == "done") {
                val msg = args.optString("message", "Selesai, Pak.").ifEmpty { "Selesai, Pak." }
                postToChat(threadId, "agent", msg)
                ActionLog.log(command.take(60), ActionLog.OK, "selesai dalam $steps langkah")
                return
            }

            setStatus("Langkah $steps: $toolName")

            // --- Eksekusi + verifikasi. ---
            var outcome = AgentTools.execute(toolName, args)

            if (outcome.info == "blocked_risky") {
                // Jeda: minta konfirmasi Pak di chat.
                pendingConfirmation = PendingConfirmation(
                    toolName,
                    args.toString(),
                    "Aksi \"$toolName\" menyentuh tombol berisiko (${args.optString("text", "")}).",
                )
                postToChat(
                    threadId,
                    "agent",
                    "Aksi ini butuh konfirmasi Pak: $toolName ${args.optString("text", "")} (tombol berisiko: beli/bayar/sign/connect wallet). Balas \"ya\" untuk melanjutkan satu aksi ini, atau kirim perintah lain untuk membatalkan.",
                )
                val decision = waitForConfirmation()
                pendingConfirmation = null
                if (decision != true || stopRequested) {
                    postToChat(threadId, "agent", "Aksi berisiko dibatalkan. Tugas dihentikan.")
                    ActionLog.log(command.take(60), ActionLog.FAIL, "aksi berisiko dibatalkan Pak")
                    return
                }
                // Pak mengizinkan SATU aksi ini saja.
                outcome = AgentTools.execute(toolName, args, allowRisky = true)
            }

            val expectedChange = toolName in setOf("tap", "tap_text", "tap_resource", "swipe", "scroll", "type", "key", "launch")
            val failed = !outcome.ok || (expectedChange && outcome.uiChanged == false)

            if (failed) {
                consecutiveFailures++
                val tried = "Percobaan ke-$consecutiveFailures gagal: ${outcome.detail} (${outcome.info})."
                ActionLog.log("$toolName (${command.take(30)})", ActionLog.FAIL, tried.take(150))
                if (consecutiveFailures >= MAX_RETRIES_PER_STEP) {
                    postToChat(
                        threadId,
                        "agent",
                        "Saya sudah mencoba $MAX_RETRIES_PER_STEP kali tapi langkah ini tetap gagal ($toolName — ${outcome.info}). Tugas dihentikan, Pak. Coba periksa layarnya, lalu perintahkan lagi.",
                    )
                    ActionLog.log(command.take(60), ActionLog.FAIL, "3x gagal di $toolName")
                    return
                }
                messages.add(
                    JSONObject().put("role", "user").put(
                        "content",
                        "Aksi $toolName GAGAL: ${outcome.detail} (${outcome.info}). Layar tidak berubah seperti diharapkan. Coba CARA LAIN (geser layar, cari teks lain, tunggu, atau kembali). Ini percobaan ke-$consecutiveFailures dari $MAX_RETRIES_PER_STEP.\n\nLayar sekarang:\n" + AgentTools.uiDumpText(),
                    ),
                )
                continue
            }

            // Berhasil — bila sebelumnya sempat gagal, ini koreksi diri.
            val wasCorrected = consecutiveFailures > 0
            consecutiveFailures = 0
            ActionLog.log(
                "$toolName (${command.take(30)})",
                if (wasCorrected) ActionLog.FIXED else ActionLog.OK,
                outcome.detail.take(150),
            )
            messages.add(
                JSONObject().put("role", "user").put(
                    "content",
                    "Hasil aksi $toolName: BERHASIL — ${outcome.detail}.\n\nLayar sekarang:\n" + AgentTools.uiDumpText(),
                ),
            )
        }

        postToChat(threadId, "agent", "Batas $MAX_STEPS langkah tercapai, Pak. Tugas dihentikan — coba perintahkan lagi dengan langkah yang lebih kecil.")
        ActionLog.log(command.take(60), ActionLog.FAIL, "batas $MAX_STEPS langkah")
    }

    /** Tunggu keputusan konfirmasi Pak (maks 10 menit). */
    private fun waitForConfirmation(): Boolean? {
        confirmDecision = null
        val deadline = System.currentTimeMillis() + 10 * 60 * 1000L
        while (System.currentTimeMillis() < deadline) {
            if (stopRequested) return false
            val d = confirmDecision
            if (d != null) return d
            try {
                Thread.sleep(500)
            } catch (_: InterruptedException) {
                return false
            }
        }
        return false
    }

    /** Parse aksi: tool_calls dulu (mode function calling), lalu objek JSON dari content (mode JSON). */
    private fun parseAction(result: BrainClient.ChatResult): Pair<String, JSONObject>? {
        // Mode function calling: pakai tool_calls.
        val call = result.toolCalls.firstOrNull()
        if (call != null && call.name.isNotEmpty()) {
            return try {
                call.name to JSONObject(call.argsJson.ifEmpty { "{}" })
            } catch (_: Exception) {
                call.name to JSONObject()
            }
        }
        // Mode JSON / fallback: ekstrak objek JSON pertama dari content.
        val content = result.content ?: return null
        val jsonText = extractJsonObject(content) ?: return null
        return try {
            val obj = JSONObject(jsonText)
            val tool = obj.optString("tool", "")
            if (tool.isEmpty()) return null
            tool to (obj.optJSONObject("args") ?: JSONObject())
        } catch (_: Exception) {
            null
        }
    }

    /** Ambil objek JSON pertama dari teks (menangani ```json fence). */
    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (escaped) {
                escaped = false
                continue
            }
            when (c) {
                '\\' -> if (inString) escaped = true
                '"' -> inString = !inString
                '{' -> if (!inString) depth++
                '}' -> if (!inString) {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun buildSystemPrompt(lessons: List<LearningStore.Lesson>, jsonMode: Boolean): String {
        val sb = StringBuilder()
        sb.append("Kamu adalah agen pengendali HP Android milik Pak. Layar berukuran ")
            .append(screenW).append("x").append(screenH)
            .append(". Jawab dan laporkan dalam Bahasa Indonesia, singkat.\n\n")
        sb.append("Aturan:\n")
        sb.append("1. Satu aksi per giliran. Setelah tiap aksi kamu menerima hasil + isi layar terbaru.\n")
        sb.append("2. Periksa hasil tiap aksi: kalau aksi gagal atau layar tidak berubah padahal seharusnya berubah, coba CARA LAIN (geser layar, cari teks lain, tunggu, tombol Back) — maksimal 3 percobaan untuk satu langkah. Kalau tetap gagal, hentikan dan laporkan.\n")
        sb.append("3. Jangan pernah mengetuk tombol beli/bayar/transfer/connect wallet/sign/approve — hentikan tugas dan laporkan ke Pak bila tugas membutuhkan itu.\n")
        sb.append("4. Jangan pernah mengetik seed phrase, kata sandi, atau kode sandi.\n")
        sb.append("5. Kalau tugas selesai, panggil tool done dengan pesan hasil akhirnya.\n")
        sb.append("6. Kalau perintah tidak jelas, selesaikan dengan done berisi pertanyaanmu — jangan menebak aksi berisiko.\n")
        if (lessons.isNotEmpty()) {
            sb.append("\nPelajaran dari koreksi Pak (wajib dipakai):\n")
            for (l in lessons) sb.append("- ").append(l.text).append("\n")
        }
        if (jsonMode) {
            sb.append("\nKeluarkan HANYA satu objek JSON per giliran, tanpa teks lain: {\"tool\": \"nama_tool\", \"args\": {...}}. Contoh: {\"tool\": \"tap_text\", \"args\": {\"text\": \"Chrome\"}}. Untuk selesai: {\"tool\": \"done\", \"args\": {\"message\": \"...\"}}.\n\nDaftar tools:\n")
            sb.append(AgentTools.definitionsText())
        }
        return sb.toString()
    }
}
