# Agen getgua — aplikasi agen Android (fase 1)

Aplikasi Android native (Kotlin, tanpa dependensi eksternal) yang menjadi
agen pribadi di HP: mengendalikan HP (ketuk, ketik, geser, screenshot),
diajak chat di dalam aplikasi, dan otaknya memanggil endpoint
OpenAI-compatible lewat Custom API Key (9Router atau langsung ke provider).

**Bukan pengganti getgua client + Hermes** — versi lain yang berdampingan.
**Bukan seperti getgua client**: aplikasi ini jalan sendiri di HP dan
diperintah lewat chat melalui otak (API key) — TANPA server HTTP
kendali jarak jauh dan TANPA token HTTP. Paket `com.getgua.agent`.

Rencana lengkap: `~/workspace/hermes-termux/RENCANA-APLIKASI-AGEN.md`.
Catatan build ini: `CATATAN-BUILD.md`.

## Yang sudah jadi (v1.0.1, fase 0 + fase 1)

- **Kendali HP** — reuse modul getgua client v1.0.1 (baca sebagai
  referensi): Accessibility ACTION_CLICK + dispatchGesture utama,
  fallback root; screenshot JPEG; UI dump (accessibility /
  uiautomator); tap/tap_text/tap_resource/swipe/scroll/long_press/
  double_tap/type/key/launch/stop_app/wait.
- **Foreground service + autostart** — service hidup terus (tanpa idle
  timeout), nyala lagi sesudah reboot, wake lock, notifikasi persisten.
  Service ini rumah bagi loop agen + penjadwalan — BUKAN server HTTP.
- **Tanpa data bawaan (v1.0.1)** — install pertama = kosong total:
  tanpa jadwal, skill, pelajaran, memori, atau riwayat contoh; tanpa
  model default dan tanpa alamat API default. Semua isi datang dari Pak.
- **Pilih + kunci model (v1.0.1)** — sesudah Tes Otak berhasil, daftar
  model dari endpoint tampil dan bisa diketuk untuk DIKUNCI; otak hanya
  memakai model yang terkunci (tanpa model cadangan). Alternatif: ketik
  ID model bebas di Pengaturan lalu "Kunci model". Tanpa model terkunci,
  otak menolak dengan pesan yang jelas.
- **Jadwal klaim bebas (v1.0.1)** — tanpa pilihan interval bawaan; Pak
  menulis sendiri jadwalnya (mis. "45 menit", "3 jam", "2 hari", "07:05").
- **Chat UI** — 4 tab (Chat, Sub obrolan, Status, Pengaturan) sesuai
  mockup v11: sub obrolan terpisah dengan pin (maks 3), Custom API Key
  (alamat endpoint + key tersamar ••••+4 terakhir, preset 9Router),
  Tombol HENTIKAN AGEN, Jadwal Klaim, Riwayat Aksi, Laporan Harian,
  Skill & Memori (skill + Pelajaran + memori, tersimpan lokal).
- **Loop otak** — `POST /v1/chat/completions` ke endpoint yang diatur,
  dua mode: function calling dulu, otomatis jatuh ke mode blok JSON
  bila model/server menolak tools. Verifikasi tiap langkah + koreksi
  diri (maks 3 percobaan per langkah, lalu berhenti dan lapor).
  Batas 25 langkah per tugas. Satu tugas dalam satu waktu.
- **Agen bisa menulis/membuat (v1.0.1)** — selain ketuk/ketik di layar,
  agen punya tool langsung ke penyimpanan: `create_schedule` (buat
  jadwal — masuk daftar yang sama dengan yang dibuat manual),
  `list_schedules` (lihat jadwal yang ada), `add_memory` (catat
  memori). Pak bisa memerintah lewat chat: "buatkan jadwal klaim X
  tiap 45 menit" — agen yang menuliskan jadwalnya.
- **Batas otonomi** — aksi berisiko (tap tombol beli/bayar/sign/
  connect wallet) ditolak dan loop JEDA menunggu konfirmasi Pak di
  chat ("ya" = lanjutkan satu aksi itu). Tanpa kartu konfirmasi
  (ditolak Pak).
- **Tes Otak** — tombol di Status: `GET /v1/models` ke endpoint yang
  diatur, melaporkan model yang tersedia (spike fase 0, dijalankan
  di HP sesudah Pak memasukkan key-nya sendiri). Ketuk salah satu
  model dari daftar untuk menguncinya.
- **Riwayat Aksi** — semua aksi tercatat lokal (Berhasil / Gagal /
  Dikoreksi sendiri); pemakaian otak dicatat (jumlah panggilan +
  token).
- **Allowlist Telegram** — daftar chat_id yang diizinkan (tambah/
  hapus) di Pengaturan, tersimpan di SharedPreferences. Botnya baru
  fase 2, tapi konfigurasinya sudah ada: hanya chat_id terdaftar
  yang dilayani, pengirim lain diabaikan diam-diam.

## Yang belum (fase 2–5, struktur sudah disiapkan)

- Telegram (fase 2): token bot + allowlist chat_id baru disimpan,
  belum ada polling.
- WhatsApp (fase 3): belum ada — hanya catatan di Pengaturan.
- Skill & Memori (fase 4): daftar + tambah/hapus jalan; pelajaran
  yang relevan sudah dimuat ke konteks loop, tapi skill belum
  dieksekusi sebagai prosedur dan belum ada perekaman cara Pak.
- Jadwal + proaktif (fase 5): jadwal tersimpan + bisa diaktifkan/
  dimatikan, tapi belum dijalankan otomatis; laporan harian baru
  statistik dari Riwayat Aksi; evaluasi diri belum ada.

## Build (tanpa Gradle)

Gradle tidak bisa jalan di sandbox ini (JVM tidak bisa akses jaringan).
Build manual — pola yang sama dipakai getgua client v1.0.0/v1.0.1:

```bash
bash build.sh
# Output: getgua-agent-1.0.1-debug.apk (debug-signed, terverifikasi apksigner)
```

Kebutuhan: Android SDK 34 + build-tools 34.0.0 (`~/Android/sdk`),
JDK 17 (`~/jdk/jdk17`), kotlinc (`~/workspace/tools/kotlinc`).

## Cara Pak menguji

1. Install APK (Chrome → Download → ketuk APK → Install).
2. Buka aplikasi "Agen getgua" → izinkan root saat prompt muncul →
   aktifkan aksesibilitas (Status → Aksesibilitas → nyalakan
   "Agen getgua") → bebaskan battery optimization (Status → Baterai).
3. Status → Custom API Key: isi alamat endpoint + kunci API sendiri
   (preset 9Router = `http://localhost:20128/v1`; atau alamat
   provider OpenAI-compatible lain). Kunci TIDAK PERNAH ditulis di
   mana pun selain aplikasi ini.
4. Status → Tes Otak: pastikan endpoint merespons, lalu KETUK salah
   satu model dari daftar untuk menguncinya (atau ketik ID model di
   Pengaturan → Kunci model). Tanpa model terkunci, otak tidak jalan.
5. Chat → kirim: `buka Chrome, cari muse ai`. Agen bekerja sendiri;
   pantau di chat + Riwayat Aksi. HENTIKAN AGEN bila perlu.
