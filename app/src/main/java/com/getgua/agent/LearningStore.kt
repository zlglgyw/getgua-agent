package com.getgua.agent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Skill + Pelajaran + Memori (fase 4 — struktur disiapkan di fase 1).
 *
 * Disimpan lokal di filesDir/learning.json. Pelajaran adalah hal
 * yang agen pelajari dari koreksi Pak; sebelum menjalankan tugas,
 * loop agen memuat pelajaran yang relevan ke konteks supaya tidak
 * mengulang kesalahan yang sama.
 */
object LearningStore {

    data class Skill(val id: String, val name: String, val desc: String, val active: Boolean)
    data class Lesson(val id: String, val text: String, val meta: String)
    data class Memory(val id: String, val text: String, val meta: String)

    data class All(
        val skills: List<Skill>,
        val lessons: List<Lesson>,
        val memories: List<Memory>,
    )

    @Volatile private var storeFile: File? = null

    fun init(context: Context) {
        try {
            storeFile = File(context.filesDir, "learning.json")
            if (storeFile?.exists() != true) save(seed())
        } catch (_: Exception) {
        }
    }

    private fun seed(): All = All(
        skills = listOf(
            Skill("s1", "Airdrop", "Klaim harian, cek mint, tugas airdrop di HP", true),
            Skill("s2", "Pengendali HP", "Ketuk, geser, ketik, screenshot, buka aplikasi", true),
        ),
        lessons = listOf(
            Lesson(
                "p1",
                "Tombol Kirim Telegraph: ketuk (667,1036), bukan koordinat yang dilaporkan UI dump",
                "Dari koreksi Pak · 13 Nov 2026",
            ),
            Lesson(
                "p2",
                "Kalau Bitget minta kode sandi, baca dulu dari Keep sebelum ketuk Konfirmasi",
                "Dari koreksi Pak · 15 Nov 2026",
            ),
        ),
        memories = listOf(
            Memory("m1", "Pak: klaim harian dulu sebelum tugas lain", "Dari chat · 15 Nov 2026"),
            Memory("m2", "Dompet utama: Bitget Saya64", "Dari chat · 11 Nov 2026"),
        ),
    )

    @Synchronized
    fun load(): All {
        val f = storeFile ?: return seed()
        return try {
            if (!f.exists()) return seed()
            val root = JSONObject(f.readText())
            val skills = ArrayList<Skill>()
            val sArr = root.optJSONArray("skills") ?: JSONArray()
            for (i in 0 until sArr.length()) {
                val o = sArr.getJSONObject(i)
                skills.add(Skill(o.optString("id"), o.optString("name"), o.optString("desc"), o.optBoolean("active", true)))
            }
            val lessons = ArrayList<Lesson>()
            val lArr = root.optJSONArray("lessons") ?: JSONArray()
            for (i in 0 until lArr.length()) {
                val o = lArr.getJSONObject(i)
                lessons.add(Lesson(o.optString("id"), o.optString("text"), o.optString("meta")))
            }
            val memories = ArrayList<Memory>()
            val mArr = root.optJSONArray("memories") ?: JSONArray()
            for (i in 0 until mArr.length()) {
                val o = mArr.getJSONObject(i)
                memories.add(Memory(o.optString("id"), o.optString("text"), o.optString("meta")))
            }
            All(skills, lessons, memories)
        } catch (_: Exception) {
            seed()
        }
    }

    @Synchronized
    fun addSkill(name: String) {
        val all = load()
        val list = all.skills + Skill("s" + System.currentTimeMillis(), name, "Skill baru — deskripsi belum diatur", true)
        save(all.copy(skills = list))
    }

    @Synchronized
    fun toggleSkill(id: String) {
        val all = load()
        val list = all.skills.map { if (it.id == id) it.copy(active = !it.active) else it }
        save(all.copy(skills = list))
    }

    @Synchronized
    fun addLesson(text: String) {
        val all = load()
        val meta = "Ditambah Pak · " + SimpleDateFormat("d MMM yyyy", Locale("id", "ID")).format(Date())
        val list = all.lessons + Lesson("p" + System.currentTimeMillis(), text, meta)
        save(all.copy(lessons = list))
    }

    @Synchronized
    fun delLesson(id: String) {
        val all = load()
        save(all.copy(lessons = all.lessons.filter { it.id != id }))
    }

    @Synchronized
    fun addMemory(text: String) {
        val all = load()
        val meta = "Ditambah Pak · " + SimpleDateFormat("d MMM yyyy", Locale("id", "ID")).format(Date())
        val list = all.memories + Memory("m" + System.currentTimeMillis(), text, meta)
        save(all.copy(memories = list))
    }

    @Synchronized
    fun delMemory(id: String) {
        val all = load()
        save(all.copy(memories = all.memories.filter { it.id != id }))
    }

    /**
     * Pelajaran yang relevan dengan perintah — cocokkan kata kunci
     * sederhana (kata perintah yang muncul di teks pelajaran).
     */
    fun relevantLessons(command: String): List<Lesson> {
        val words = command.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 4 }
            .toSet()
        if (words.isEmpty()) return emptyList()
        return load().lessons.filter { lesson ->
            val t = lesson.text.lowercase()
            words.any { it in t }
        }
    }

    private fun save(all: All) {
        val f = storeFile ?: return
        try {
            val sArr = JSONArray()
            for (s in all.skills) {
                sArr.put(JSONObject().put("id", s.id).put("name", s.name).put("desc", s.desc).put("active", s.active))
            }
            val lArr = JSONArray()
            for (l in all.lessons) {
                lArr.put(JSONObject().put("id", l.id).put("text", l.text).put("meta", l.meta))
            }
            val mArr = JSONArray()
            for (m in all.memories) {
                mArr.put(JSONObject().put("id", m.id).put("text", m.text).put("meta", m.meta))
            }
            f.writeText(
                JSONObject().put("skills", sArr).put("lessons", lArr).put("memories", mArr).toString(),
            )
        } catch (_: Exception) {
        }
    }
}
