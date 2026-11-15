# Catatan Build — Agen getgua v1.0.2

Dibangun 15 Nov 2026 atas perintah Pak ("hubungkan ke telegram",
15:36 WIB). Toolchain manual: aapt2 + kotlinc + d8 + apksigner
(tanpa Gradle — JVM tidak bisa akses jaringan di sandbox).

## Hasil build

- APK: `getgua-agent-1.0.2-debug.apk` — debug-signed (keystore yang
  SAMA dengan v1.0.0/v1.0.1, jadi bisa install menimpa),
  `apksigner verify` LULUS, paket `com.getgua.agent`, versionCode 3,
  minSdk 30 / targetSdk 34, tanpa dependensi eksternal.
- Kode: 22 berkas Kotlin (1 baru: TelegramBot).

## Yang berubah dari v1.0.1 (fase 2 + perbaikan pesan error)

1. **Bot Telegram (FASE 2).** Bot API resmi via HTTPS, long polling
   `getUpdates` di background service (tanpa webhook). Pesan dari
   chat_id allowlist dijalankan sebagai perintah lewat AgentLoop
   yang sama seperti chat dalam aplikasi (satu tugas dalam satu
   waktu, berbagi antrean); jawaban loop dibalas via `sendMessage`
   dan percakapannya juga tercatat sebagai thread "Telegram" di Sub
   obrolan. Perintah khusus: `/status`, `/layar` (screenshot via
   `sendPhoto`), `/batal`, `/start`. Allowlist WAJIB: pengirim di
   luar allowlist diabaikan diam-diam; allowlist kosong = bot tidak
   melayani siapa pun. Polling hanya jalan bila token bot sudah
   diisi; update lama dibuang saat polling mulai. Status bot tampil
   di layar Status (baris Telegram).
2. **Perintah "hubungkan ke telegram".** Dari chat dalam aplikasi,
   agen menjawab LANGSUNG dengan status Telegram (token sudah
   diisi/belum, jumlah chat_id di allowlist, polling aktif/tidak) +
   panduan singkat — tanpa coba-coba ketuk layar (pelajaran dari
   kegagalan 15 Nov 15:37 WIB).
3. **Pesan error izin yang jelas.** Bila aksi gagal karena root
   belum diberi atau layanan Aksesibilitas belum aktif, detail
   kegagalan menyebut izinnya dengan jelas ("layanan Aksesibilitas
   'Agen getgua' belum aktif" / "izin root belum diberikan") —
   berlaku untuk tap/key/type/layar/screenshot, dan pesan akhir
   "3x gagal" kini menyertakan detailnya (bukan hanya kode
   "exception"). Aturan baru di prompt sistem: bila layar tak
   terbaca karena izin, otak langsung melaporkannya, bukan
   mengulang aksi layar. Cek root di-cache 60 detik supaya tidak
   memicu prompt Magisk berulang.
4. **Label versi dari PackageManager.** Label "v1.0.0" yang mentah
   di layout (v1.0.1 sempat tampil v1.0.0 di HP) diganti dengan
   versionName paket yang sebenarnya, dibaca saat aplikasi dibuka.
5. **Konfirmasi hapus Custom API Key.** Tombol Hapus kini membuka
   dialog konfirmasi dulu ("Hapus Custom API Key?") — kunci API
   pernah hilang karena tombol tertekan tanpa sengaja. Ini dialog
   hapus biasa, BUKAN kartu konfirmasi aksi agen.
6. **Pesan error 403 yang jelas.** Bila endpoint mengembalikan
   HTTP 403 `client_not_allowed` / `permission_error` (provider
   hanya mengizinkan klien pemrograman tertentu), error yang
   diteruskan ke otak menjelaskan blokirnya + saran ganti endpoint
   (mis. 9Router). User-Agent TIDAK diubah — tidak ada upaya
   meniru klien lain untuk mengakali blokir.

## Yang berubah dari v1.0.0 (v1.0.1, semua atas perintah Pak)

1. **Pilih + kunci model (BUG v1.0.0).** Otak v1.0.0 terkunci ke model
   default yang tidak ada di endpoint Pak — semua panggilan chat
   gagal HTTP 404 `model_not_found`. v1.0.1: TIDAK ADA model default.
   Sesudah Tes Otak berhasil, daftar model tampil dan bisa diketuk
   untuk DIKUNCI (SharedPreferences); alternatif: ketik ID model bebas
   di Pengaturan → "Kunci model". Otak hanya memakai model yang
   terkunci — tanpa model terkunci, otak menolak dengan pesan jelas.
2. **Tanpa data bawaan.** Install pertama = kosong total: jadwal
   (4 jadwal contoh dihapus), skill/pelajaran/memori contoh dihapus,
   pesan sambutan di Chat Utama dihapus, tanpa model default, tanpa
   alamat API default. Riwayat Aksi + Laporan Harian memang sudah
   kosong (hanya dari aksi nyata).
3. **Bukan getgua client.** Server HTTP kendali jarak jauh, token HTTP
   (termasuk baca file config Termux), input token manual, dan tombol
   MULAI/HENTIKAN SERVICE DIHAPUS TOTAL (ketiga berkas server/token
   dihapus). Foreground
   service tinggal untuk loop agen + penjadwalan. Aplikasi jalan
   sendiri, diperintah lewat chat melalui otak (API key).
4. **Jadwal klaim bebas.** Pilihan interval bawaan (30 menit / 90 menit
   / 4 jam / 24 jam) DIHAPUS. Pak menulis sendiri jadwalnya di kolom
   bebas (mis. "45 menit", "3 jam", "2 hari", "07:05").
5. **Agen bisa menulis/membuat.** Tool baru di loop agen:
   `create_schedule` (buat jadwal — masuk daftar yang sama dengan yang
   dibuat manual), `list_schedules`, `add_memory` (catat memori).
   Pak bisa memerintah lewat chat: "buatkan jadwal klaim X tiap ...".
   Jadwal disimpan lewat ScheduleStore (satu sumber kebenaran).

## Keputusan yang dipakai saat build

- Paket `com.getgua.agent`. `stopApp` melindungi `com.getgua.agent`
  DAN `com.getgua.client` supaya keduanya tetap hidup berdampingan.
- 9Router OPSIONAL: otak memanggil endpoint OpenAI-compatible lewat
  Custom API Key; preset hanya 9Router + kolom alamat bebas.
- Tanpa kartu konfirmasi Izinkan/Tolak (ditolak Pak 14:04 WIB) —
  konfirmasi aksi berisiko lewat balasan chat ("ya"/"tidak").
- Allowlist Telegram WAJIB (Pak, 15 Nov 14:37 WIB): daftar chat_id
  sudah bisa dikonfigurasi di Pengaturan sejak fase 1 (tambah/hapus,
  SharedPreferences); botnya sendiri baru fase 2.
- `usesCleartextTraffic=true` di manifest — diperlukan untuk
  endpoint 9Router di `http://localhost:20128`.

## Verifikasi grep (v1.0.2, di seluruh folder proyek)

- 0 sebutan provider yang dihapus atas perintah Pak (14:34 WIB).
- 0 model default.
- 0 port HTTP (8767) / 0 kode server HTTP / token.
- 0 baca file config Termux.
- 0 kunci API / token hardcoded.
- Tidak ada data seed/contoh/bawaan.

## Yang BELUM teruji (butuh HP + Pak)

- Install menimpa v1.0.1 + izin root + aksesibilitas + battery
  exemption (langkah yang sama seperti getgua client).
- Telegram end-to-end: buat bot di @BotFather → tempel token →
  isi allowlist → /start → perintah dari Telegram → jawaban +
  screenshot (/layar) → /batal saat tugas berjalan.
- Pesan dari chat_id di luar allowlist diabaikan (tanpa balasan).
- Perintah "hubungkan ke telegram" di chat aplikasi → jawaban
  status + panduan langsung.
- Pesan error izin yang jelas (matikan dulu aksesibilitas/root
  lalu perintahkan aksi layar — seharusnya lapor izin, bukan
  "exception").
- Konfirmasi aksi berisiko lewat Telegram ("ya"/"tidak").
- Ketahanan service 24 jam di XOS + autostart sesudah reboot.
- Koeksistensi dengan getgua client 8766 (keduanya jalan bersamaan).

## Yang butuh Pak

1. Install APK v1.0.2 (menimpa v1.0.1 — tanda tangan sama).
2. Telegram: buat bot, tempel token di Pengaturan, isi allowlist,
   kirim /start dari Telegram, lalu coba perintah + /layar.
3. Laporkan hasilnya untuk iterasi berikutnya.

## Riwayat

- v1.0.0 (15 Nov 14:39 WIB): fase 0 + fase 1, 778 KB, commit
  40ab88ff. Termasuk server HTTP + token (dihapus di
  v1.0.1) dan data contoh dari mockup (dihapus di v1.0.1).
- v1.0.1 (15 Nov ~15:00 WIB): perbaikan di atas.
- v1.0.2 (15 Nov): fase 2 (bot Telegram) + pesan error izin.

## Bukan bagian build ini

- Fase 3 (WhatsApp), 4 (skill/memori penuh), 5
  (jadwal/proaktif: eksekusi otomatis jadwal) — struktur disiapkan,
  eksekusi menyusul.
- Repo getgua client tidak disentuh (hanya dibaca sebagai referensi).
