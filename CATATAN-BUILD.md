# Catatan Build — Agen getgua v1.0.1

Dibangun 15 Nov 2026 atas perintah Pak di chat utama (14:53–14:58 WIB).
Toolchain manual: aapt2 + kotlinc + d8 + apksigner (tanpa Gradle —
JVM tidak bisa akses jaringan di sandbox).

## Hasil build

- APK: `getgua-agent-1.0.1-debug.apk` — debug-signed (keystore yang
  SAMA dengan v1.0.0, jadi bisa install menimpa), `apksigner verify`
  LULUS, paket `com.getgua.agent`, versionCode 2, minSdk 30 /
  targetSdk 34, tanpa dependensi eksternal.
- Kode: 21 berkas Kotlin (3 dihapus, 1 baru: ScheduleStore).

## Yang berubah dari v1.0.0 (semua atas perintah Pak)

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

## Verifikasi grep (v1.0.1, di seluruh folder proyek)

- 0 sebutan provider yang dihapus atas perintah Pak (14:34 WIB).
- 0 model default.
- 0 port HTTP.
- 0 baca file config Termux.
- Tidak ada kode server HTTP / token yang tertinggal.

## Yang BELUM teruji (butuh HP + Pak)

- Install menimpa v1.0.0 + izin root + aksesibilitas + battery
  exemption (langkah yang sama seperti getgua client).
- Tes Otak + kunci model + perintah pertama end-to-end:
  "buka Chrome, cari muse ai" (function calling vs mode JSON —
  tergantung model yang dipakai).
- Perintah "buatkan jadwal ..." lewat chat (tool create_schedule).
- Koreksi diri (3x gagal → berhenti + lapor) dan HENTIKAN AGEN.
- Konfirmasi aksi berisiko lewat chat.
- Ketahanan service 24 jam di XOS + autostart sesudah reboot.
- Koeksistensi dengan getgua client 8766 (keduanya jalan bersamaan).

## Yang butuh Pak

1. Install APK v1.0.1 (menimpa v1.0.0 — tanda tangan sama).
2. Status → Tes Otak → ketuk salah satu model untuk menguncinya.
3. Tes perintah pertama + perintah "buatkan jadwal ..." + laporkan
   hasilnya untuk iterasi berikutnya.

## Riwayat

- v1.0.0 (15 Nov 14:39 WIB): fase 0 + fase 1, 778 KB, commit
  40ab88ff. Termasuk server HTTP + token (dihapus di
  v1.0.1) dan data contoh dari mockup (dihapus di v1.0.1).
- v1.0.1 (15 Nov ~15:00 WIB): perbaikan di atas.

## Bukan bagian build ini

- Fase 2 (Telegram), 3 (WhatsApp), 4 (skill/memori penuh), 5
  (jadwal/proaktif: eksekusi otomatis jadwal) — struktur disiapkan,
  eksekusi menyusul.
- Repo getgua client tidak disentuh (hanya dibaca sebagai referensi).
