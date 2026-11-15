package com.getgua.agent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Penyimpanan chat lokal (filesDir/chats.json).
 *
 * Satu thread "main" (Chat Utama) + sub obrolan buatan Pak.
 * Maksimal 3 thread di-pin (aturan UI mockup v11).
 * Riwayat pesan per thread dipotong ke 200 pesan terbaru.
 */
object ChatStore {

    const val MAIN_ID = "main"
    const val MAX_PINNED = 3
    private const val MAX_MESSAGES = 200

    data class Message(val from: String, val text: String, val time: String)
    data class Thread(val id: String, val name: String, val pinned: Boolean, val messages: List<Message>)

    @Volatile private var storeFile: File? = null

    fun init(context: Context) {
        try {
            storeFile = File(context.filesDir, "chats.json")
            ensureMain()
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun threads(): List<Thread> {
        val f = storeFile ?: return emptyList()
        return try {
            if (!f.exists()) return listOf(emptyMain())
            val root = JSONObject(f.readText())
            val arr = root.optJSONArray("threads") ?: JSONArray()
            val out = ArrayList<Thread>()
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                val msgs = ArrayList<Message>()
                val mArr = t.optJSONArray("messages") ?: JSONArray()
                for (j in 0 until mArr.length()) {
                    val m = mArr.getJSONObject(j)
                    msgs.add(Message(m.optString("from"), m.optString("text"), m.optString("time")))
                }
                out.add(Thread(t.optString("id"), t.optString("name"), t.optBoolean("pinned"), msgs))
            }
            if (out.none { it.id == MAIN_ID }) out.add(0, emptyMain())
            out
        } catch (_: Exception) {
            listOf(emptyMain())
        }
    }

    fun getThread(id: String): Thread? = threads().find { it.id == id }

    @Synchronized
    fun addMessage(threadId: String, from: String, text: String) {
        val all = threads().toMutableList()
        val idx = all.indexOfFirst { it.id == threadId }
        if (idx < 0) return
        val t = all[idx]
        val now = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        val msgs = (t.messages + Message(from, text, now)).takeLast(MAX_MESSAGES)
        all[idx] = t.copy(messages = msgs)
        save(all)
    }

    @Synchronized
    fun createThread(name: String): Thread {
        val all = threads().toMutableList()
        val id = "t" + System.currentTimeMillis()
        val t = Thread(id, name, false, emptyList())
        all.add(t)
        save(all)
        return t
    }

    /**
     * Pin/unpin thread. Mengembalikan false bila pin ditolak
     * (sudah 3 thread di-pin).
     */
    @Synchronized
    fun setPinned(id: String, pinned: Boolean): Boolean {
        val all = threads().toMutableList()
        if (pinned && all.count { it.pinned } >= MAX_PINNED && all.find { it.id == id }?.pinned != true) {
            return false
        }
        val idx = all.indexOfFirst { it.id == id }
        if (idx < 0) return false
        all[idx] = all[idx].copy(pinned = pinned)
        save(all)
        return true
    }

    private fun emptyMain(): Thread = Thread(
        MAIN_ID,
        "Chat Utama",
        false,
        listOf(
            Message(
                "agent",
                "Halo Pak! Saya agen getgua. Perintah apa yang bisa saya kerjakan? Contoh: \"buka Chrome, cari muse ai\".",
                SimpleDateFormat("HH:mm", Locale.US).format(Date()),
            ),
        ),
    )

    private fun ensureMain() {
        val all = threads()
        if (all.none { it.id == MAIN_ID }) save(all + emptyMain())
    }

    private fun save(all: List<Thread>) {
        val f = storeFile ?: return
        try {
            val arr = JSONArray()
            for (t in all) {
                val mArr = JSONArray()
                for (m in t.messages) {
                    mArr.put(JSONObject().put("from", m.from).put("text", m.text).put("time", m.time))
                }
                arr.put(
                    JSONObject()
                        .put("id", t.id)
                        .put("name", t.name)
                        .put("pinned", t.pinned)
                        .put("messages", mArr),
                )
            }
            f.writeText(JSONObject().put("threads", arr).toString())
        } catch (_: Exception) {
        }
    }
}
