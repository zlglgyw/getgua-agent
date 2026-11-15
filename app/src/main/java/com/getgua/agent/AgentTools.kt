package com.getgua.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * Tools yang bisa dipanggil model — pembungkus Automation +
 * Screenshot + UiNodes menjadi daftar berdeskripsi.
 *
 * Dua bentuk definisi: format function calling OpenAI (mode utama)
 * dan teks ringkas (mode fallback blok JSON).
 */
object AgentTools {

    data class ToolOutcome(
        val ok: Boolean,
        val info: String,
        val detail: String,
        val uiChanged: Boolean?,
    )

    data class Def(
        val name: String,
        val description: String,
        /** param -> deskripsi (semua string/number/boolean sederhana). */
        val params: List<Triple<String, String, Boolean>>,
    )

    private val DEFS = listOf(
        Def("tap", "Ketuk koordinat layar (x,y).", listOf(Triple("x", "integer", true), Triple("y", "integer", true))),
        Def("tap_text", "Ketuk elemen yang teksnya mengandung teks yang diberikan.", listOf(Triple("text", "string", true))),
        Def("tap_resource", "Ketuk elemen berdasarkan resource-id (substring).", listOf(Triple("resource", "string", true))),
        Def("swipe", "Geser dari (x1,y1) ke (x2,y2).", listOf(Triple("x1", "integer", true), Triple("y1", "integer", true), Triple("x2", "integer", true), Triple("y2", "integer", true), Triple("duration", "integer", false))),
        Def("scroll", "Gulir layar: up/down/left/right.", listOf(Triple("direction", "string", true))),
        Def("long_press", "Tekan lama di koordinat (x,y), durasi ms (default 1000).", listOf(Triple("x", "integer", true), Triple("y", "integer", true), Triple("duration", "integer", false))),
        Def("double_tap", "Ketuk dua kali di koordinat (x,y).", listOf(Triple("x", "integer", true), Triple("y", "integer", true))),
        Def("type", "Ketik teks ke field yang sedang fokus. clear=true menghapus dulu, enter=true menekan Enter sesudahnya.", listOf(Triple("text", "string", true), Triple("clear", "boolean", false), Triple("enter", "boolean", false))),
        Def("key", "Tekan tombol hardware: 3 Home, 4 Back, 26 Power, 66 Enter, 67 Hapus, 187 Recents.", listOf(Triple("key", "integer", true))),
        Def("launch", "Buka aplikasi berdasarkan nama paket (mis. com.android.chrome).", listOf(Triple("package", "string", true))),
        Def("wait", "Tunggu sekian detik.", listOf(Triple("seconds", "number", true))),
        Def("ui", "Baca ulang isi layar saat ini (daftar elemen).", emptyList()),
        Def("screenshot", "Ambil screenshot layar saat ini (dipakai untuk verifikasi).", emptyList()),
        Def("done", "Tugas selesai — sampaikan hasil akhirnya ke Pak.", listOf(Triple("message", "string", true))),
    )

    /** Definisi format function calling OpenAI. */
    fun definitionsJson(): JSONArray {
        val arr = JSONArray()
        for (d in DEFS) {
            val props = JSONObject()
            val required = JSONArray()
            for ((name, type, req) in d.params) {
                props.put(name, JSONObject().put("type", type))
                if (req) required.put(name)
            }
            arr.put(
                JSONObject()
                    .put("type", "function")
                    .put(
                        "function",
                        JSONObject()
                            .put("name", d.name)
                            .put("description", d.description)
                            .put(
                                "parameters",
                                JSONObject()
                                    .put("type", "object")
                                    .put("properties", props)
                                    .put("required", required),
                            ),
                    ),
            )
        }
        return arr
    }

    /** Definisi teks ringkas untuk mode fallback blok JSON. */
    fun definitionsText(): String {
        val sb = StringBuilder()
        for (d in DEFS) {
            sb.append("- ").append(d.name).append(": ").append(d.description)
            if (d.params.isNotEmpty()) {
                sb.append(" Param: ")
                sb.append(d.params.joinToString(", ") { (n, t, r) -> "$n ($t${if (r) ", wajib" else ""})" })
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    /**
     * Jalankan satu tool. "done" tidak dieksekusi di sini (ditangani loop).
     * [allowRisky] hanya true sesudah Pak mengonfirmasi satu aksi berisiko.
     */
    fun execute(name: String, args: JSONObject, allowRisky: Boolean = false): ToolOutcome {
        return try {
            when (name) {
                "tap" -> {
                    val r = Automation.tap(args.optInt("x"), args.optInt("y"))
                    ToolOutcome(r.ok, r.info, "tap (${args.optInt("x")},${args.optInt("y")}) via ${r.method}", r.uiChanged)
                }
                "tap_text" -> {
                    val r = Automation.tapText(args.optString("text", ""), allowRisky = allowRisky)
                    ToolOutcome(r.ok, r.info, "tap_text \"${args.optString("text", "")}\" via ${r.method}", r.uiChanged)
                }
                "tap_resource" -> {
                    val r = Automation.tapResource(args.optString("resource", ""))
                    ToolOutcome(r.ok, r.info, "tap_resource \"${args.optString("resource", "")}\" via ${r.method}", r.uiChanged)
                }
                "swipe" -> {
                    val r = Automation.swipe(args.optInt("x1"), args.optInt("y1"), args.optInt("x2"), args.optInt("y2"), args.optInt("duration", 300))
                    ToolOutcome(r.ok, r.info, "swipe via ${r.method}", r.uiChanged)
                }
                "scroll" -> {
                    val r = Automation.scroll(args.optString("direction", ""))
                    ToolOutcome(r.ok, r.info, "scroll ${args.optString("direction", "")}", r.uiChanged)
                }
                "long_press" -> {
                    val r = Automation.longPress(args.optInt("x"), args.optInt("y"), args.optInt("duration", 1000))
                    ToolOutcome(r.ok, r.info, "long_press (${args.optInt("x")},${args.optInt("y")}) ${args.optInt("duration", 1000)}ms", r.uiChanged)
                }
                "double_tap" -> {
                    val r = Automation.doubleTap(args.optInt("x"), args.optInt("y"))
                    ToolOutcome(r.ok, r.info, "double_tap (${args.optInt("x")},${args.optInt("y")})", r.uiChanged)
                }
                "type" -> {
                    val r = Automation.type(args.optString("text", ""), args.optBoolean("clear", false), args.optBoolean("enter", false))
                    ToolOutcome(r.ok, r.info, "type \"${args.optString("text", "").take(40)}\"", r.uiChanged)
                }
                "key" -> {
                    val r = Automation.key(args.optInt("key"))
                    ToolOutcome(r.ok, r.info, "key ${args.optInt("key")}", r.uiChanged)
                }
                "launch" -> {
                    val r = Automation.launch(args.optString("package", ""))
                    ToolOutcome(r.ok, r.info, "launch ${args.optString("package", "")}", r.uiChanged)
                }
                "wait" -> {
                    val r = Automation.wait(args.optDouble("seconds", 1.0))
                    ToolOutcome(r.ok, r.info, "wait ${args.optDouble("seconds", 1.0)} dtk", r.uiChanged)
                }
                "ui" -> {
                    ToolOutcome(true, "ui", "Layar saat ini:\n" + uiDumpText(), null)
                }
                "screenshot" -> {
                    val png = Screenshot.capturePng()
                    if (png == null) {
                        ToolOutcome(false, "screenshot_failed", "Screenshot gagal diambil", null)
                    } else {
                        ToolOutcome(true, "screenshot", "Screenshot diambil (${png.size} byte).", null)
                    }
                }
                else -> ToolOutcome(false, "unknown_tool", "Tool tidak dikenal: $name", null)
            }
        } catch (e: Exception) {
            ToolOutcome(false, "exception", "Gagal menjalankan $name: ${(e.message ?: "").take(120)}", null)
        }
    }

    /**
     * Dump UI sebagai teks ringkas untuk observasi model.
     * Hanya node yang punya teks/deskripsi atau bisa diklik.
     */
    fun uiDumpText(maxNodes: Int = 150, maxChars: Int = 6000): String {
        val (nodes, source) = try {
            Automation.nodes()
        } catch (_: Exception) {
            return "(tidak bisa membaca layar)"
        }
        if (nodes.isEmpty()) return "(layar kosong / tidak bisa dibaca — sumber: $source)"
        val sb = StringBuilder()
        sb.append("(sumber: ").append(source).append(", ").append(nodes.size).append(" elemen)\n")
        var shown = 0
        for (n in nodes) {
            if (shown >= maxNodes || sb.length > maxChars) break
            val label = when {
                n.text.isNotBlank() -> n.text.trim()
                n.desc.isNotBlank() -> "[" + n.desc.trim() + "]"
                else -> ""
            }
            if (label.isEmpty() && !n.clickable) continue
            sb.append("- ")
            if (label.isNotEmpty()) sb.append('"').append(label.take(60)).append('"')
            if (n.clickable) sb.append(" [ketuk]")
            if (n.x != null && n.y != null) sb.append(" @").append(n.x).append(",").append(n.y)
            if (n.resource.isNotEmpty()) sb.append(" id=").append(n.resource.substringAfterLast('/').take(40))
            sb.append("\n")
            shown++
        }
        if (shown == 0) sb.append("(tidak ada elemen berteks di layar)\n")
        return sb.toString()
    }
}
