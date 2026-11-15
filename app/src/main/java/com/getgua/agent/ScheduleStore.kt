package com.getgua.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * Jadwal klaim (fase 5 — disimpan, eksekusi otomatis menyusul).
 *
 * SATU daftar yang sama untuk jadwal yang Pak buat manual di form
 * dan yang agen buat lewat tool create_schedule.
 *
 * Jadwal BEBAS: interval adalah teks yang ditulis Pak/model apa
 * adanya (mis. "45 menit", "3 jam", "2 hari", "07:05") — tanpa
 * pilihan interval bawaan (Pak, 15 Nov 14:57 WIB).
 */
object ScheduleStore {

    data class Schedule(
        val id: String,
        val name: String,
        val interval: String,
        val next: String,
        val active: Boolean,
    )

    /** Daftar jadwal. Kosong saat pertama install — tanpa jadwal bawaan. */
    @Synchronized
    fun load(): List<Schedule> {
        val raw = Prefs.schedulesJson
        if (raw.isEmpty()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<Schedule>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Schedule(
                        o.optString("id"),
                        o.optString("name"),
                        o.optString("interval"),
                        o.optString("next"),
                        o.optBoolean("active", true),
                    ),
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun save(list: List<Schedule>) {
        val arr = JSONArray()
        for (s in list) {
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("interval", s.interval)
                    .put("next", s.next)
                    .put("active", s.active),
            )
        }
        Prefs.schedulesJson = arr.toString()
    }

    /** Tambah jadwal baru (aktif). Dipakai form manual + tool agen. */
    @Synchronized
    fun add(name: String, interval: String): Schedule {
        val s = Schedule(
            "j" + System.currentTimeMillis(),
            name.trim(),
            interval.trim().ifEmpty { "Jadwal bebas" },
            "Akan dijadwalkan",
            true,
        )
        save(load() + s)
        return s
    }

    @Synchronized
    fun setActive(id: String, active: Boolean) {
        save(load().map { if (it.id == id) it.copy(active = active) else it })
    }
}
