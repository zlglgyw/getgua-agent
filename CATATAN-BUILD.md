# Catatan Build — Agen getgua v1.0.0

Dibangun 15 Nov 2026 (fase 0 + fase 1) atas perintah Pak (lampu hijau
14:29 WIB). Toolchain manual: aapt2 + kotlinc + d8 + apksigner
(tanpa Gradle — JVM tidak bisa akses jaringan di sandbox).

## Hasil build

- APK: `getgua-agent-1.0.0-debug.apk` — 778 KB, debug-signed,
  `apksigner verify` LULUS, paket `com.getgua.agent`, versionCode 1,
  minSdk 30 / targetSdk 34, tanpa dependensi eksternal.
- Kode: 20 berkas Kotlin, ~4.500 baris (reuse modul getgua client
  v1.0.1 sebagai referensi + ~2.000 baris baru: loop otak, chat UI,
  penyimpanan lokal).
- Peringatan compile: hanya deprecation `recycle()` (diwarisi dari
  kode getgua client, tidak berpengaruh) — tidak ada error.

## Keputusan yang dipakai saat build

- Paket `com.getgua.agent`, port HTTP **8767** (getgua client 8766).
  `/stop_app` melindungi `com.getgua.agent` DAN `com.getgua.client`
  supaya keduanya tetap hidup berdampingan.
- Token HTTP sama dengan getgua client (file phone_config.json Termux
  + input manual sebagai fallback — pelajaran dari v1.0.0 getgua
  client yang tokennya tidak terbaca dari file).
- 9Router OPSIONAL: otak memanggil endpoint OpenAI-compatible lewat
  Custom API Key; preset hanya 9Router + kolom alamat bebas.
- **Provider yang dihapus atas perintah Pak (14:34 WIB)** — preset
  endpoint dan modelnya DIHAPUS TOTAL dari aplikasi; model dipilih
  per ID lewat kolom teks bebas + daftar dari Tes Otak
  (`GET /v1/models`).
- Tanpa kartu konfirmasi Izinkan/Tolak (ditolak Pak 14:04 WIB) —
  konfirmasi aksi berisiko lewat balasan chat ("ya"/"tidak").
- Allowlist Telegram WAJIB (Pak, 14:37 WIB): daftar chat_id sudah
  bisa dikonfigurasi di Pengaturan sejak fase 1 (tambah/hapus,
  SharedPreferences); botnya sendiri baru fase 2.
- `usesCleartextTraffic=true` di manifest — diperlukan untuk
  endpoint 9Router di `http://localhost:20128`.

## Yang sudah terverifikasi (di VM, tanpa HP)

- APK ter-build + ter-sign + lolos `apksigner verify` dan
  `aapt2 dump badging` (paket, versi, SDK benar).
- Verifikasi grep: tidak ada preset endpoint / model dari provider
  yang dihapus di seluruh folder proyek.
- Tidak ada referensi kelas getgua client yang tertinggal.

## Yang BELUM teruji (butuh HP + Pak)

- Install di HP Infinix + izin root + aksesibilitas + battery
  exemption (langkah yang sama seperti getgua client).
- Service jalan di port 8767 + token terbaca (`/health`).
- Tes Otak (`GET /v1/models`) dengan key Pak sendiri.
- Perintah pertama end-to-end: "buka Chrome, cari muse ai"
  (function calling vs mode JSON — tergantung model yang dipakai).
- Koreksi diri (3x gagal → berhenti + lapor) dan HENTIKAN AGEN.
- Konfirmasi aksi berisiko lewat chat.
- Ketahanan service 24 jam di XOS + autostart sesudah reboot.
- Koeksistensi dengan getgua client 8766 (keduanya jalan bersamaan).

## Yang butuh Pak

1. Install APK + izin (root, aksesibilitas, baterai) — lihat README.
2. Masukkan Custom API Key sendiri di aplikasi (alamat + key).
3. Tes perintah pertama + laporkan hasilnya (apa yang jalan,
   apa yang gagal) untuk iterasi berikutnya.
4. Buat repo `zlglgyw/getgua-agent` di GitHub bila kode mau di-push
   (Muse tidak bisa membuat repo; push dilakukan atas perintah Pak).

## Bukan bagian build ini

- Fase 2 (Telegram), 3 (WhatsApp), 4 (skill/memoripenuh), 5
  (jadwal/proaktif) — struktur disiapkan, eksekusi menyusul.
- Tidak ada push ke repo mana pun (atas perintah tugas).
- Repo getgua client tidak disentuh (hanya dibaca sebagai referensi).
