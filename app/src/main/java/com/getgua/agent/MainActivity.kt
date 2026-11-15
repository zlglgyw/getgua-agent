package com.getgua.agent

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * UI utama aplikasi Agen getgua — sesuai mockup v11.
 *
 * 4 tab: Chat | Sub obrolan | Status | Pengaturan, plus layar
 * dalam: Thread, Jadwal Klaim, Riwayat Aksi, Laporan Harian,
 * Skill & Memori. Chat NYATA: perintah dikirim ke AgentLoop.
 *
 * v1.0.1: tanpa server HTTP/token (bukan getgua client), tanpa data
 * bawaan, model dipilih Pak lewat Tes Otak lalu DIKUNCI.
 */
class MainActivity : Activity(), AgentLoop.Listener {

    private var currentScreen = SCREEN_CHAT
    private var activeThreadId: String = ChatStore.MAIN_ID
    private var editingKey = false

    private lateinit var screenChat: View
    private lateinit var screenSubList: View
    private lateinit var screenThread: View
    private lateinit var screenStatus: View
    private lateinit var screenJadwal: View
    private lateinit var screenRiwayat: View
    private lateinit var screenLaporan: View
    private lateinit var screenSettings: View
    private lateinit var screenSkills: View
    private lateinit var tabChat: Button
    private lateinit var tabSubList: Button
    private lateinit var tabStatus: Button
    private lateinit var tabSettings: Button

    private lateinit var tvAgentStatus: TextView
    private lateinit var tvThreadStatus: TextView
    private lateinit var scrollMessages: ScrollView
    private lateinit var messagesContainer: LinearLayout
    private lateinit var etChatInput: EditText
    private lateinit var scrollThreadMessages: ScrollView
    private lateinit var threadMessagesContainer: LinearLayout
    private lateinit var etThreadInput: EditText
    private lateinit var tvThreadTitle: TextView

    private lateinit var subListContainer: LinearLayout
    private lateinit var etNewThread: EditText
    private lateinit var tvPinWarn: TextView

    private lateinit var tvStoppedBanner: TextView
    private lateinit var apiKeyEditGroup: View
    private lateinit var apiKeySavedGroup: View
    private lateinit var etApiUrl: EditText
    private lateinit var etApiKey: EditText
    private lateinit var btnCancelEditKey: Button
    private lateinit var tvApiUrlSaved: TextView
    private lateinit var tvApiKeyMasked: TextView
    private lateinit var tvTestBrainResult: TextView
    private lateinit var btnTestBrain: Button
    private lateinit var modelListContainer: LinearLayout
    private lateinit var tvStService: TextView
    private lateinit var tvStRoot: TextView
    private lateinit var tvStA11y: TextView
    private lateinit var tvStBattery: TextView
    private lateinit var tvStBrain: TextView
    private lateinit var tvStUsage: TextView

    private lateinit var jadwalContainer: LinearLayout
    private lateinit var etNewSched: EditText
    private lateinit var etSchedInterval: EditText
    private lateinit var riwayatContainer: LinearLayout
    private lateinit var tvLaporanStats: TextView
    private lateinit var tvLaporanAttention: TextView
    private lateinit var spReportTime: Spinner

    private lateinit var tvLockedModel: TextView
    private lateinit var etCustomModel: EditText
    private lateinit var etAllowlist: EditText
    private lateinit var etTgToken: EditText
    private lateinit var tvTgTokenStatus: TextView
    private lateinit var tgAllowlistContainer: LinearLayout
    private lateinit var etNewTgAllowlist: EditText

    private lateinit var skillsContainer: LinearLayout
    private lateinit var lessonsContainer: LinearLayout
    private lateinit var memoriesContainer: LinearLayout
    private lateinit var etNewSkill: EditText
    private lateinit var etNewLesson: EditText
    private lateinit var etNewMemory: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        AppLog.init(this)
        Automation.init(this)
        Prefs.init(this)
        ActionLog.init(this)
        ChatStore.init(this)
        LearningStore.init(this)
        AgentLoop.init(this)
        AgentLoop.listener = this

        bindViews()
        wireNav()
        wireChat()
        wireSubList()
        wireStatus()
        wireJadwalRiwayatLaporan()
        wireSettings()
        wireSkills()

        // Service adalah inti aplikasi — nyalakan otomatis saat dibuka.
        if (!AgentService.running) {
            try {
                startForegroundService(Intent(this, AgentService::class.java))
            } catch (_: Exception) {
            }
        }

        showScreen(SCREEN_CHAT)
        renderChat()
        refreshApiKeyCard()
        refreshSecretStatus()
        renderTgAllowlist()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        when (currentScreen) {
            SCREEN_CHAT -> renderChat()
            SCREEN_THREAD -> renderThread()
            SCREEN_SUBLIST -> renderSubList()
            SCREEN_RIWAYAT -> renderRiwayat()
            SCREEN_JADWAL -> renderJadwal()
            SCREEN_LAPORAN -> renderLaporan()
            SCREEN_SKILLS -> renderSkills()
            SCREEN_SETTINGS -> renderTgAllowlist()
        }
    }

    override fun onDestroy() {
        if (AgentLoop.listener == this) AgentLoop.listener = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // AgentLoop.Listener (dipanggil dari thread worker — pindah ke UI)
    // ------------------------------------------------------------------

    override fun onStatus(text: String) {
        runOnUiThread {
            tvAgentStatus.text = "Status agen: $text"
            tvThreadStatus.text = "Status agen: $text"
        }
    }

    override fun onMessage(threadId: String, from: String, text: String) {
        runOnUiThread {
            if (threadId == ChatStore.MAIN_ID && currentScreen == SCREEN_CHAT) {
                renderChat()
            } else if (threadId == activeThreadId && currentScreen == SCREEN_THREAD) {
                renderThread()
            }
            if (currentScreen == SCREEN_RIWAYAT) renderRiwayat()
        }
    }

    override fun onFinished(threadId: String) {
        runOnUiThread {
            tvAgentStatus.text = "Status agen: Siap"
            tvThreadStatus.text = "Status agen: Siap"
            refreshStatus()
            if (currentScreen == SCREEN_RIWAYAT) renderRiwayat()
        }
    }

    // ------------------------------------------------------------------
    // Navigasi
    // ------------------------------------------------------------------

    private fun showScreen(screen: Int) {
        currentScreen = screen
        screenChat.visibility = if (screen == SCREEN_CHAT) View.VISIBLE else View.GONE
        screenSubList.visibility = if (screen == SCREEN_SUBLIST) View.VISIBLE else View.GONE
        screenThread.visibility = if (screen == SCREEN_THREAD) View.VISIBLE else View.GONE
        screenStatus.visibility = if (screen == SCREEN_STATUS) View.VISIBLE else View.GONE
        screenJadwal.visibility = if (screen == SCREEN_JADWAL) View.VISIBLE else View.GONE
        screenRiwayat.visibility = if (screen == SCREEN_RIWAYAT) View.VISIBLE else View.GONE
        screenLaporan.visibility = if (screen == SCREEN_LAPORAN) View.VISIBLE else View.GONE
        screenSettings.visibility = if (screen == SCREEN_SETTINGS) View.VISIBLE else View.GONE
        screenSkills.visibility = if (screen == SCREEN_SKILLS) View.VISIBLE else View.GONE
        tabChat.typeface = if (screen == SCREEN_CHAT) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        tabSubList.typeface =
            if (screen == SCREEN_SUBLIST || screen == SCREEN_THREAD) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        tabStatus.typeface =
            if (screen == SCREEN_STATUS || screen == SCREEN_JADWAL || screen == SCREEN_RIWAYAT || screen == SCREEN_LAPORAN) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        tabSettings.typeface =
            if (screen == SCREEN_SETTINGS || screen == SCREEN_SKILLS) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun wireNav() {
        tabChat.setOnClickListener { showScreen(SCREEN_CHAT); renderChat() }
        tabSubList.setOnClickListener { showScreen(SCREEN_SUBLIST); renderSubList() }
        tabStatus.setOnClickListener { showScreen(SCREEN_STATUS); refreshStatus(); refreshApiKeyCard() }
        tabSettings.setOnClickListener { showScreen(SCREEN_SETTINGS); renderTgAllowlist() }
    }

    // ------------------------------------------------------------------
    // Chat
    // ------------------------------------------------------------------

    private fun wireChat() {
        findViewById<Button>(R.id.btnSend).setOnClickListener { sendChat(ChatStore.MAIN_ID, etChatInput) }
        etChatInput.setOnEditorActionListener { _, _, _ ->
            sendChat(ChatStore.MAIN_ID, etChatInput)
            true
        }
        findViewById<Button>(R.id.btnThreadSend).setOnClickListener { sendChat(activeThreadId, etThreadInput) }
        etThreadInput.setOnEditorActionListener { _, _, _ ->
            sendChat(activeThreadId, etThreadInput)
            true
        }
        findViewById<Button>(R.id.btnThreadBack).setOnClickListener {
            showScreen(SCREEN_SUBLIST)
            renderSubList()
        }
    }

    private fun sendChat(threadId: String, input: EditText) {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.setText("")
        ChatStore.addMessage(threadId, "user", text)
        if (threadId == ChatStore.MAIN_ID) renderChat() else renderThread()

        // Konfirmasi aksi berisiko sedang tertunda — jawaban Pak di sini.
        if (AgentLoop.pendingConfirmation != null) {
            val lower = text.lowercase(Locale.US)
            when {
                lower in setOf("ya", "iya", "y", "ok", "oke", "lanjut", "lanjutkan", "setuju", "boleh") -> {
                    AgentLoop.confirm(true)
                    ChatStore.addMessage(threadId, "agent", "Baik, Pak — melanjutkan satu aksi itu.")
                }
                lower in setOf("tidak", "nggak", "gak", "ga", "t", "batal", "jangan") -> {
                    AgentLoop.confirm(false)
                }
                else -> {
                    // Perintah baru = batalkan konfirmasi, jalankan perintah baru.
                    AgentLoop.confirm(false)
                    thread(isDaemon = true) {
                        Thread.sleep(800)
                        runOnUiThread { startTask(threadId, text) }
                    }
                    return
                }
            }
            if (threadId == ChatStore.MAIN_ID) renderChat() else renderThread()
            return
        }
        startTask(threadId, text)
    }

    private fun startTask(threadId: String, text: String) {
        if (AgentLoop.running) {
            ChatStore.addMessage(threadId, "agent", "Agen sedang mengerjakan tugas lain, Pak. Tunggu selesai, atau tekan HENTIKAN AGEN di menu Status dulu.")
            if (threadId == ChatStore.MAIN_ID) renderChat() else renderThread()
            return
        }
        tvStoppedBanner.visibility = View.GONE
        if (!AgentLoop.start(threadId, text)) {
            if (threadId == ChatStore.MAIN_ID) renderChat() else renderThread()
        }
    }

    private fun renderChat() {
        renderMessages(ChatStore.getThread(ChatStore.MAIN_ID), messagesContainer, scrollMessages)
    }

    private fun renderThread() {
        val t = ChatStore.getThread(activeThreadId) ?: return
        tvThreadTitle.text = t.name
        renderMessages(t, threadMessagesContainer, scrollThreadMessages)
    }

    private fun renderMessages(t: ChatStore.Thread?, container: LinearLayout, scroll: ScrollView) {
        container.removeAllViews()
        if (t == null) return
        for (m in t.messages) {
            container.addView(messageBubble(m.text, m.from == "user", m.time))
        }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun messageBubble(text: String, isUser: Boolean, time: String): TextView {
        val bubble = TextView(this).apply {
            this.text = if (time.isNotEmpty()) "$text\n$time" else text
            textSize = 15f
            setBackgroundResource(if (isUser) R.drawable.bubble_user else R.drawable.bubble_agent)
            val pad = dp(10)
            setPadding(pad, dp(8), pad, dp(8))
            maxWidth = (resources.displayMetrics.widthPixels * 0.85).toInt()
        }
        bubble.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = if (isUser) Gravity.END else Gravity.START
            topMargin = dp(6)
        }
        return bubble
    }

    // ------------------------------------------------------------------
    // Sub obrolan
    // ------------------------------------------------------------------

    private fun wireSubList() {
        findViewById<Button>(R.id.btnNewThread).setOnClickListener {
            val name = etNewThread.text.toString().trim()
            val t = ChatStore.createThread(if (name.isEmpty()) "Sub obrolan" else name)
            etNewThread.setText("")
            openThread(t.id)
        }
    }

    private fun openThread(id: String) {
        activeThreadId = id
        showScreen(SCREEN_THREAD)
        renderThread()
    }

    private fun renderSubList() {
        subListContainer.removeAllViews()
        val threads = ChatStore.threads().filter { it.id != ChatStore.MAIN_ID }
        val pinned = threads.filter { it.pinned }
        val rest = threads.filter { !it.pinned }
        if (pinned.isNotEmpty()) {
            subListContainer.addView(sectionLabel("DI-PIN"))
            for (t in pinned) subListContainer.addView(threadRow(t))
        }
        subListContainer.addView(sectionLabel("SEMUA CHAT"))
        if (rest.isEmpty() && pinned.isEmpty()) {
            subListContainer.addView(noteText("Belum ada sub obrolan. Buat satu di atas."))
        }
        for (t in rest) subListContainer.addView(threadRow(t))
    }

    private fun threadRow(t: ChatStore.Thread): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }
        val last = t.messages.lastOrNull()
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        main.addView(TextView(this).apply { text = t.name; textSize = 16f; setTypeface(null, Typeface.BOLD) })
        main.addView(TextView(this).apply { text = last?.text ?: ""; textSize = 13f; maxLines = 1 })
        val pinBtn = Button(this).apply {
            text = if (t.pinned) "★" else "☆"
            setOnClickListener {
                if (!ChatStore.setPinned(t.id, !t.pinned)) {
                    tvPinWarn.text = "Maksimal 3 chat di-pin — lepas satu dulu."
                    tvPinWarn.visibility = View.VISIBLE
                } else {
                    tvPinWarn.visibility = View.GONE
                }
                renderSubList()
            }
        }
        row.addView(main)
        row.addView(pinBtn)
        row.setOnClickListener { openThread(t.id) }
        return row
    }

    // ------------------------------------------------------------------
    // Status
    // ------------------------------------------------------------------

    private fun wireStatus() {
        findViewById<Button>(R.id.btnStopAgent).setOnClickListener {
            if (AgentLoop.running) {
                AgentLoop.stop()
                tvStoppedBanner.text = "AGEN DIHENTIKAN — tugas yang berjalan dibatalkan."
                tvStoppedBanner.visibility = View.VISIBLE
                Toast.makeText(this, "Agen dihentikan", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Tidak ada tugas yang berjalan", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.btnPreset9Router).setOnClickListener { etApiUrl.setText(Prefs.URL_9ROUTER) }
        findViewById<Button>(R.id.btnSaveApiKey).setOnClickListener {
            val url = etApiUrl.text.toString().trim()
            val key = etApiKey.text.toString().trim()
            if (url.isEmpty() || key.isEmpty()) {
                Toast.makeText(this, "Alamat API dan kunci harus diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Prefs.apiUrl = url
            Prefs.apiKey = key
            etApiKey.setText("")
            editingKey = false
            refreshApiKeyCard()
            refreshStatus()
            Toast.makeText(this, "Custom API Key tersimpan", Toast.LENGTH_SHORT).show()
        }
        btnCancelEditKey.setOnClickListener {
            editingKey = false
            refreshApiKeyCard()
        }
        findViewById<Button>(R.id.btnEditApiKey).setOnClickListener {
            editingKey = true
            etApiUrl.setText(Prefs.apiUrl)
            etApiKey.setText(Prefs.apiKey)
            refreshApiKeyCard()
        }
        findViewById<Button>(R.id.btnDeleteApiKey).setOnClickListener {
            Prefs.clearApiKey()
            editingKey = false
            refreshApiKeyCard()
            refreshStatus()
            Toast.makeText(this, "Custom API Key dihapus", Toast.LENGTH_SHORT).show()
        }
        btnTestBrain.setOnClickListener {
            btnTestBrain.isEnabled = false
            tvTestBrainResult.text = "Menghubungi endpoint…"
            modelListContainer.removeAllViews()
            thread(isDaemon = true) {
                val r = BrainClient.testModels()
                runOnUiThread {
                    btnTestBrain.isEnabled = true
                    if (r.ok) {
                        if (r.models.isEmpty()) {
                            tvTestBrainResult.text = "Terhubung — server tidak mengembalikan daftar model. Ketik ID model di Pengaturan."
                        } else {
                            tvTestBrainResult.text = "Terhubung — ${r.models.size} model. Ketuk salah satu untuk menguncinya:"
                            renderModelList(r.models)
                        }
                    } else {
                        tvTestBrainResult.text = "Gagal: ${r.error}"
                    }
                }
            }
        }
        findViewById<Button>(R.id.btnOpenJadwal).setOnClickListener { showScreen(SCREEN_JADWAL); renderJadwal() }
        findViewById<Button>(R.id.btnOpenRiwayat).setOnClickListener { showScreen(SCREEN_RIWAYAT); renderRiwayat() }
        findViewById<Button>(R.id.btnOpenLaporan).setOnClickListener { showScreen(SCREEN_LAPORAN); renderLaporan() }
        findViewById<Button>(R.id.btnA11y).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    },
                )
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        findViewById<Button>(R.id.btnRefreshStatus).setOnClickListener { refreshStatus() }
    }

    // ------------------------------------------------------------------
    // Pilih + kunci model (v1.0.1)
    // ------------------------------------------------------------------

    /** Daftar model dari Tes Otak — ketuk salah satu untuk menguncinya. */
    private fun renderModelList(models: List<String>) {
        modelListContainer.removeAllViews()
        for (id in models) {
            val locked = id == Prefs.model
            val btn = Button(this).apply {
                text = if (locked) "● $id (terkunci)" else id
                setOnClickListener { lockModel(id) }
            }
            modelListContainer.addView(btn)
        }
    }

    /** Kunci model: dipakai semua panggilan otak sampai Pak mengubahnya. */
    private fun lockModel(id: String) {
        val clean = id.trim()
        if (clean.isEmpty()) return
        Prefs.model = clean
        refreshLockedModel()
        refreshStatus()
        // Tandai ulang tombol yang terkunci bila daftar sedang tampil.
        for (i in 0 until modelListContainer.childCount) {
            val v = modelListContainer.getChildAt(i)
            if (v is Button) {
                val mid = v.text.toString().removePrefix("● ").removeSuffix(" (terkunci)")
                v.text = if (mid == clean) "● $mid (terkunci)" else mid
            }
        }
        Toast.makeText(this, "Model dikunci: $clean", Toast.LENGTH_SHORT).show()
    }

    private fun refreshLockedModel() {
        tvLockedModel.text = if (Prefs.hasModel()) {
            "Model terkunci: ${Prefs.model}"
        } else {
            "Model terkunci: BELUM DIPILIH — ketuk Tes Otak di Status, lalu ketuk salah satu model."
        }
    }

    private fun refreshApiKeyCard() {
        val has = Prefs.hasApiKey()
        if (!has || editingKey) {
            apiKeyEditGroup.visibility = View.VISIBLE
            apiKeySavedGroup.visibility = View.GONE
            btnCancelEditKey.visibility = if (has && editingKey) View.VISIBLE else View.GONE
            if (!editingKey) etApiUrl.setText(Prefs.apiUrl)
        } else {
            apiKeyEditGroup.visibility = View.GONE
            apiKeySavedGroup.visibility = View.VISIBLE
            tvApiUrlSaved.text = Prefs.apiUrl.ifEmpty { "Belum diatur" }
            tvApiKeyMasked.text = Prefs.maskedApiKey()
        }
    }

    private fun refreshStatus() {
        tvStService.text = "Service: " + if (AgentService.running) "JALAN" else "berhenti"
        tvStA11y.text = "Aksesibilitas: " +
            if (AgentAccessibilityService.isActive()) "AKTIF" else "tidak aktif — aktifkan di Pengaturan > Aksesibilitas"
        tvStBattery.text = "Baterai: " + (DeviceInfo.batteryLevel(this)?.let { "$it%" } ?: "…")
        tvStBrain.text = "Otak: " + if (Prefs.hasBrainConfig()) {
            if (Prefs.hasModel()) {
                "teratur — ${Prefs.model} @ ${Prefs.apiUrl}"
            } else {
                "kunci API terisi — MODEL BELUM DIPILIH (ketuk Tes Otak lalu pilih model)"
            }
        } else {
            "BELUM DIATUR — isi Custom API Key di atas"
        }
        tvStUsage.text = "Pemakaian: ${Prefs.usageCalls} panggilan, ${Prefs.usageTokens} token"
        tvStRoot.text = "Root: memeriksa…"
        thread(isDaemon = true) {
            val rootOk = RootShell.isRootAvailable()
            runOnUiThread {
                tvStRoot.text = "Root: " + if (rootOk) "tersedia (uid=0)" else "TIDAK TERSEDIA — izinkan root saat prompt muncul"
            }
        }
    }

    // ------------------------------------------------------------------
    // Jadwal / Riwayat / Laporan
    // ------------------------------------------------------------------

    private fun wireJadwalRiwayatLaporan() {
        findViewById<Button>(R.id.btnJadwalBack).setOnClickListener { showScreen(SCREEN_STATUS) }
        findViewById<Button>(R.id.btnRiwayatBack).setOnClickListener { showScreen(SCREEN_STATUS) }
        findViewById<Button>(R.id.btnLaporanBack).setOnClickListener { showScreen(SCREEN_STATUS) }
        findViewById<Button>(R.id.btnAddSched).setOnClickListener {
            val name = etNewSched.text.toString().trim()
            if (name.isEmpty()) return@setOnClickListener
            // v1.0.1: jadwal BEBAS — Pak yang menulis sendiri (mis. "45 menit",
            // "3 jam", "2 hari", "07:05"). Tanpa pilihan interval bawaan.
            ScheduleStore.add(name, etSchedInterval.text.toString())
            etNewSched.setText("")
            etSchedInterval.setText("")
            renderJadwal()
        }
        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            ActionLog.clear()
            renderRiwayat()
            Toast.makeText(this, "Riwayat dibersihkan", Toast.LENGTH_SHORT).show()
        }
        spReportTime.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf("06:00", "07:00", "08:00"),
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val rtIdx = listOf("06:00", "07:00", "08:00").indexOf(Prefs.reportTime).coerceAtLeast(0)
        spReportTime.setSelection(rtIdx)
        spReportTime.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                Prefs.reportTime = listOf("06:00", "07:00", "08:00")[pos]
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun renderJadwal() {
        jadwalContainer.removeAllViews()
        val list = ScheduleStore.load()
        for (s in list) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(8), dp(10), dp(8), dp(10))
            }
            val main = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            main.addView(TextView(this).apply { text = s.name; textSize = 16f; setTypeface(null, Typeface.BOLD) })
            main.addView(TextView(this).apply { text = "${s.interval} · berikutnya ${s.next}"; textSize = 13f })
            val tog = Button(this).apply {
                text = if (s.active) "Aktif" else "Mati"
                setOnClickListener {
                    ScheduleStore.setActive(s.id, !s.active)
                    renderJadwal()
                }
            }
            row.addView(main)
            row.addView(tog)
            jadwalContainer.addView(row)
        }
        if (list.isEmpty()) jadwalContainer.addView(noteText("Belum ada jadwal."))
    }

    private fun renderRiwayat() {
        riwayatContainer.removeAllViews()
        val entries = ActionLog.list(100)
        if (entries.isEmpty()) {
            riwayatContainer.addView(noteText("Belum ada aksi tercatat."))
            return
        }
        for (e in entries) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            val time = TextView(this).apply { text = e.time; textSize = 12f; setPadding(0, 0, dp(8), 0) }
            val main = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            main.addView(TextView(this).apply { text = e.text; textSize = 15f })
            if (e.detail.isNotEmpty()) {
                main.addView(TextView(this).apply { text = e.detail; textSize = 12f })
            }
            val badge = TextView(this).apply {
                text = when (e.result) {
                    ActionLog.OK -> "Berhasil"
                    ActionLog.FIXED -> "Dikoreksi sendiri"
                    else -> "Gagal"
                }
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(
                    when (e.result) {
                        ActionLog.OK -> 0xFF166534.toInt()
                        ActionLog.FIXED -> 0xFF92400E.toInt()
                        else -> 0xFF991B1B.toInt()
                    },
                )
            }
            row.addView(time)
            row.addView(main)
            row.addView(badge)
            riwayatContainer.addView(row)
        }
    }

    private fun renderLaporan() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val entries = ActionLog.list(500).filter { it.date == today }
        val ok = entries.count { it.result == ActionLog.OK }
        val fixed = entries.count { it.result == ActionLog.FIXED }
        val fail = entries.count { it.result == ActionLog.FAIL }
        tvLaporanStats.text = "Hari ini (${SimpleDateFormat("d MMM yyyy", Locale("id", "ID")).format(Date())}):\n" +
            "${entries.size} aksi tercatat · $ok berhasil · $fixed dikoreksi sendiri · $fail gagal"
        val fails = entries.filter { it.result == ActionLog.FAIL }.take(3)
        tvLaporanAttention.text = if (fails.isEmpty()) {
            "Perlu perhatian: tidak ada kegagalan hari ini."
        } else {
            "Perlu perhatian:\n" + fails.joinToString("\n") { "• ${it.time} ${it.text} — ${it.detail}" }
        }
    }

    // ------------------------------------------------------------------
    // Pengaturan
    // ------------------------------------------------------------------

    private fun wireSettings() {
        refreshLockedModel()
        findViewById<Button>(R.id.btnSaveModel).setOnClickListener {
            val v = etCustomModel.text.toString().trim()
            if (v.isEmpty()) return@setOnClickListener
            lockModel(v)
            etCustomModel.setText("")
        }
        findViewById<Button>(R.id.btnApiKeyRow).setOnClickListener {
            showScreen(SCREEN_STATUS)
            refreshApiKeyCard()
        }
        findViewById<Button>(R.id.btnSkillsRow).setOnClickListener { showScreen(SCREEN_SKILLS); renderSkills() }
        etAllowlist.setText(Prefs.allowlist)
        findViewById<Button>(R.id.btnSaveAllowlist).setOnClickListener {
            Prefs.allowlist = etAllowlist.text.toString().trim()
            Toast.makeText(this, "Allowlist tersimpan", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnSaveTgToken).setOnClickListener {
            val v = etTgToken.text.toString().trim()
            if (v.isEmpty()) {
                Toast.makeText(this, "Token Telegram kosong", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Prefs.tgToken = v
            etTgToken.setText("")
            refreshSecretStatus()
            Toast.makeText(this, "Token Telegram tersimpan (dipakai fase 2)", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnAddTgAllowlist).setOnClickListener {
            val v = etNewTgAllowlist.text.toString().trim()
            if (v.isEmpty()) return@setOnClickListener
            if (Prefs.addTgAllowlist(v)) {
                etNewTgAllowlist.setText("")
                renderTgAllowlist()
                Toast.makeText(this, "chat_id $v diizinkan", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "chat_id sudah ada di daftar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshSecretStatus() {
        tvTgTokenStatus.text = if (Prefs.hasTgToken()) "Tersimpan" else "Belum diisi"
    }

    private fun renderTgAllowlist() {
        tgAllowlistContainer.removeAllViews()
        val list = Prefs.tgAllowlist()
        if (list.isEmpty()) {
            tgAllowlistContainer.addView(noteText("Belum ada chat_id yang diizinkan."))
            return
        }
        for (id in list) {
            tgAllowlistContainer.addView(deletableRow(id, "chat_id Telegram") { Prefs.removeTgAllowlist(id); renderTgAllowlist() })
        }
    }

    // ------------------------------------------------------------------
    // Skill & Memori
    // ------------------------------------------------------------------

    private fun wireSkills() {
        findViewById<Button>(R.id.btnSkillsBack).setOnClickListener { showScreen(SCREEN_SETTINGS) }
        findViewById<Button>(R.id.btnAddSkill).setOnClickListener {
            val v = etNewSkill.text.toString().trim()
            if (v.isEmpty()) return@setOnClickListener
            LearningStore.addSkill(v)
            etNewSkill.setText("")
            renderSkills()
        }
        findViewById<Button>(R.id.btnAddLesson).setOnClickListener {
            val v = etNewLesson.text.toString().trim()
            if (v.isEmpty()) return@setOnClickListener
            LearningStore.addLesson(v)
            etNewLesson.setText("")
            renderSkills()
        }
        findViewById<Button>(R.id.btnAddMemory).setOnClickListener {
            val v = etNewMemory.text.toString().trim()
            if (v.isEmpty()) return@setOnClickListener
            LearningStore.addMemory(v)
            etNewMemory.setText("")
            renderSkills()
        }
    }

    private fun renderSkills() {
        val all = LearningStore.load()
        skillsContainer.removeAllViews()
        for (s in all.skills) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            val main = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            main.addView(TextView(this).apply { text = s.name; textSize = 15f; setTypeface(null, Typeface.BOLD) })
            main.addView(TextView(this).apply { text = s.desc; textSize = 12f })
            val tog = Button(this).apply {
                text = if (s.active) "Aktif" else "Nonaktif"
                setOnClickListener { LearningStore.toggleSkill(s.id); renderSkills() }
            }
            row.addView(main)
            row.addView(tog)
            skillsContainer.addView(row)
        }
        lessonsContainer.removeAllViews()
        for (l in all.lessons) {
            lessonsContainer.addView(deletableRow(l.text, l.meta) { LearningStore.delLesson(l.id); renderSkills() })
        }
        if (all.lessons.isEmpty()) lessonsContainer.addView(noteText("Belum ada pelajaran tersimpan."))
        memoriesContainer.removeAllViews()
        for (m in all.memories) {
            memoriesContainer.addView(deletableRow(m.text, m.meta) { LearningStore.delMemory(m.id); renderSkills() })
        }
        if (all.memories.isEmpty()) memoriesContainer.addView(noteText("Belum ada memori tersimpan."))
    }

    private fun deletableRow(text: String, meta: String, onDelete: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        main.addView(TextView(this).apply { this.text = text; textSize = 14f })
        main.addView(TextView(this).apply { this.text = meta; textSize = 11f })
        val del = Button(this).apply {
            this.text = "×"
            setOnClickListener { onDelete() }
        }
        row.addView(main)
        row.addView(del)
        return row
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun sectionLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(8), dp(12), dp(8), dp(4))
        }
    }

    private fun noteText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(12))
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun bindViews() {
        screenChat = findViewById(R.id.screenChat)
        screenSubList = findViewById(R.id.screenSubList)
        screenThread = findViewById(R.id.screenThread)
        screenStatus = findViewById(R.id.screenStatus)
        screenJadwal = findViewById(R.id.screenJadwal)
        screenRiwayat = findViewById(R.id.screenRiwayat)
        screenLaporan = findViewById(R.id.screenLaporan)
        screenSettings = findViewById(R.id.screenSettings)
        screenSkills = findViewById(R.id.screenSkills)
        tabChat = findViewById(R.id.tabChat)
        tabSubList = findViewById(R.id.tabSubList)
        tabStatus = findViewById(R.id.tabStatus)
        tabSettings = findViewById(R.id.tabSettings)

        tvAgentStatus = findViewById(R.id.tvAgentStatus)
        tvThreadStatus = findViewById(R.id.tvThreadStatus)
        scrollMessages = findViewById(R.id.scrollMessages)
        messagesContainer = findViewById(R.id.messagesContainer)
        etChatInput = findViewById(R.id.etChatInput)
        scrollThreadMessages = findViewById(R.id.scrollThreadMessages)
        threadMessagesContainer = findViewById(R.id.threadMessagesContainer)
        etThreadInput = findViewById(R.id.etThreadInput)
        tvThreadTitle = findViewById(R.id.tvThreadTitle)

        subListContainer = findViewById(R.id.subListContainer)
        etNewThread = findViewById(R.id.etNewThread)
        tvPinWarn = findViewById(R.id.tvPinWarn)

        tvStoppedBanner = findViewById(R.id.tvStoppedBanner)
        apiKeyEditGroup = findViewById(R.id.apiKeyEditGroup)
        apiKeySavedGroup = findViewById(R.id.apiKeySavedGroup)
        etApiUrl = findViewById(R.id.etApiUrl)
        etApiKey = findViewById(R.id.etApiKey)
        btnCancelEditKey = findViewById(R.id.btnCancelEditKey)
        tvApiUrlSaved = findViewById(R.id.tvApiUrlSaved)
        tvApiKeyMasked = findViewById(R.id.tvApiKeyMasked)
        tvTestBrainResult = findViewById(R.id.tvTestBrainResult)
        btnTestBrain = findViewById(R.id.btnTestBrain)
        modelListContainer = findViewById(R.id.modelListContainer)
        tvStService = findViewById(R.id.tvStService)
        tvStRoot = findViewById(R.id.tvStRoot)
        tvStA11y = findViewById(R.id.tvStA11y)
        tvStBattery = findViewById(R.id.tvStBattery)
        tvStBrain = findViewById(R.id.tvStBrain)
        tvStUsage = findViewById(R.id.tvStUsage)

        jadwalContainer = findViewById(R.id.jadwalContainer)
        etNewSched = findViewById(R.id.etNewSched)
        etSchedInterval = findViewById(R.id.etSchedInterval)
        riwayatContainer = findViewById(R.id.riwayatContainer)
        tvLaporanStats = findViewById(R.id.tvLaporanStats)
        tvLaporanAttention = findViewById(R.id.tvLaporanAttention)
        spReportTime = findViewById(R.id.spReportTime)

        tvLockedModel = findViewById(R.id.tvLockedModel)
        etCustomModel = findViewById(R.id.etCustomModel)
        etAllowlist = findViewById(R.id.etAllowlist)
        etTgToken = findViewById(R.id.etTgToken)
        tvTgTokenStatus = findViewById(R.id.tvTgTokenStatus)
        tgAllowlistContainer = findViewById(R.id.tgAllowlistContainer)
        etNewTgAllowlist = findViewById(R.id.etNewTgAllowlist)

        skillsContainer = findViewById(R.id.skillsContainer)
        lessonsContainer = findViewById(R.id.lessonsContainer)
        memoriesContainer = findViewById(R.id.memoriesContainer)
        etNewSkill = findViewById(R.id.etNewSkill)
        etNewLesson = findViewById(R.id.etNewLesson)
        etNewMemory = findViewById(R.id.etNewMemory)
    }

    companion object {
        private const val SCREEN_CHAT = 0
        private const val SCREEN_SUBLIST = 1
        private const val SCREEN_THREAD = 2
        private const val SCREEN_STATUS = 3
        private const val SCREEN_JADWAL = 4
        private const val SCREEN_RIWAYAT = 5
        private const val SCREEN_LAPORAN = 6
        private const val SCREEN_SETTINGS = 7
        private const val SCREEN_SKILLS = 8
    }
}
