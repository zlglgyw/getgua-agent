package com.getgua.agent

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Klien HTTP ke endpoint OpenAI-compatible (mis. 9Router atau
 * provider lain) — tanpa dependensi, HttpURLConnection + org.json.
 *
 * TIDAK PERNAH mencatat kunci API ke log.
 */
object BrainClient {

    data class ToolCall(val name: String, val argsJson: String)

    data class ChatResult(
        val content: String?,
        val toolCalls: List<ToolCall>,
        val usageTokens: Int,
        val error: String?,
        /** true bila server menolak parameter tools (model tak dukung function calling). */
        val toolsRejected: Boolean = false,
    )

    data class ModelsResult(
        val ok: Boolean,
        val models: List<String>,
        val error: String?,
    )

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val MODELS_TIMEOUT_MS = 30_000
    private const val CHAT_TIMEOUT_MS = 120_000

    /**
     * Tes Otak (spike fase 0): GET /v1/models ke endpoint yang
     * diatur. Dipakai Pak sesudah memasukkan kunci API sendiri.
     */
    fun testModels(): ModelsResult {
        val base = Prefs.apiUrl
        val key = Prefs.apiKey
        if (base.isEmpty() || key.isEmpty()) {
            return ModelsResult(false, emptyList(), "Alamat API / kunci belum diatur di Status")
        }
        return try {
            val conn = openConnection("$base/models", key, MODELS_TIMEOUT_MS)
            val (code, body) = readResponse(conn)
            if (code != 200) {
                return ModelsResult(false, emptyList(), "HTTP $code: ${body.take(200)}")
            }
            val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
            val models = ArrayList<String>()
            for (i in 0 until data.length()) {
                val id = data.optJSONObject(i)?.optString("id", "") ?: ""
                if (id.isNotEmpty()) models.add(id)
            }
            ModelsResult(true, models, null)
        } catch (e: Exception) {
            ModelsResult(false, emptyList(), describeError(e))
        }
    }

    /**
     * POST /v1/chat/completions.
     *
     * @param messages daftar pesan {"role":..,"content":..} (JSONObject).
     * @param tools definisi function calling (JSONArray) atau null.
     */
    fun chat(messages: List<JSONObject>, tools: JSONArray?): ChatResult {
        val base = Prefs.apiUrl
        val key = Prefs.apiKey
        if (base.isEmpty() || key.isEmpty()) {
            return ChatResult(null, emptyList(), 0, "Alamat API / kunci belum diatur di Status")
        }
        // v1.0.1: otak hanya memakai model yang DIKUNCI Pak — tanpa
        // model terkunci, tolak dengan jelas (tidak ada model cadangan).
        val model = Prefs.model
        if (model.isEmpty()) {
            return ChatResult(
                null, emptyList(), 0,
                "Model belum dipilih — buka Status, ketuk Tes Otak, lalu ketuk salah satu model untuk menguncinya",
            )
        }
        return try {
            val body = JSONObject()
                .put("model", Prefs.model)
                .put("messages", JSONArray(messages))
                .put("temperature", 0.2)
                .put("max_tokens", 1024)
            if (tools != null) {
                body.put("tools", tools)
                body.put("tool_choice", "auto")
            }
            val conn = openConnection("$base/chat/completions", key, CHAT_TIMEOUT_MS)
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val (code, respBody) = readResponse(conn)
            if (code != 200) {
                val rejected = code == 400 &&
                    (respBody.contains("tool", ignoreCase = true) ||
                        respBody.contains("function", ignoreCase = true))
                return ChatResult(null, emptyList(), 0, "HTTP $code: ${respBody.take(300)}", rejected)
            }
            val root = JSONObject(respBody)
            val usageTokens = root.optJSONObject("usage")?.optInt("total_tokens", 0) ?: 0
            if (usageTokens > 0) Prefs.addUsage(usageTokens) else Prefs.addUsage(0)
            val choices = root.optJSONArray("choices") ?: JSONArray()
            if (choices.length() == 0) {
                return ChatResult(null, emptyList(), usageTokens, "Respons kosong dari server")
            }
            val message = choices.getJSONObject(0).optJSONObject("message") ?: JSONObject()
            val content = message.optString("content", "").ifEmpty { null }
            val calls = ArrayList<ToolCall>()
            val tcArr = message.optJSONArray("tool_calls") ?: JSONArray()
            for (i in 0 until tcArr.length()) {
                val fn = tcArr.getJSONObject(i).optJSONObject("function") ?: continue
                calls.add(ToolCall(fn.optString("name", ""), fn.optString("arguments", "{}")))
            }
            ChatResult(content, calls, usageTokens, null)
        } catch (e: Exception) {
            ChatResult(null, emptyList(), 0, describeError(e))
        }
    }

    private fun openConnection(url: String, key: String, readTimeoutMs: Int): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = readTimeoutMs
        conn.setRequestProperty("Authorization", "Bearer $key")
        conn.setRequestProperty("Accept", "application/json")
        return conn
    }

    private fun readResponse(conn: HttpURLConnection): Pair<Int, String> {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = try {
            stream?.readBytes()?.toString(Charsets.UTF_8) ?: ""
        } catch (_: Exception) {
            ""
        }
        return code to body
    }

    private fun describeError(e: Exception): String {
        val msg = e.message ?: e.javaClass.simpleName
        return when {
            msg.contains("Unable to resolve host", ignoreCase = true) ->
                "Tidak bisa menghubungi server — periksa alamat API / koneksi"
            msg.contains("ConnectException", ignoreCase = true) ||
                msg.contains("Failed to connect", ignoreCase = true) ->
                "Koneksi ditolak — pastikan 9Router jalan (bila memakai 9Router) atau alamat benar"
            msg.contains("SocketTimeout", ignoreCase = true) ||
                msg.contains("timed out", ignoreCase = true) ->
                "Server tidak merespons (timeout)"
            msg.contains("Cleartext", ignoreCase = true) ->
                "HTTP biasa diblokir — pakai https atau localhost"
            else -> msg.take(200)
        }
    }
}
