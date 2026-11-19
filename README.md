# EduQR Scanner APK — Aplikasi Android Scanner Khusus EduQR PC (v1.2)

Aplikasi native Android (**Kotlin + CameraX + Google ML Kit**) untuk memindai kartu QR siswa secara cepat (100% *on-device* tanpa internet) dan mengirimkannya langsung ke **Server PC EduQR v9** melalui Wi-Fi / Hotspot lokal.

## Fitur Lengkap (v1.2)
- **Ganti Kamera Depan & Belakang**: Tombol cepat **`Kamera: Blk` / `Kamera: Dpn`** untuk beralih antara kamera belakang (scan genggam) dan kamera depan (mode meja / kios absensi mandiri siswa). Pilihan kamera tersimpan otomatis.
- **Ikon Aplikasi Khusus (`EduQR Scanner`)**: Ikon peluncur (*launcher icon*) resolusi tinggi lengkap dengan dukungan *Adaptive Icon* Android 8+ untuk semua ukuran layar HP.
- **Pairing 1-Detik via QR Code**: Arahkan kamera HP ke QR Pairing (`EDUQRPAIR|...`) di layar PC EduQR, maka alamat IP server, PIN 4-digit, daftar siswa, dan mata pelajaran langsung tersinkron otomatis.
- **Viewfinder Jernih dengan Laser Animasi**: Kotak bidik transparan berbingkai sudut hijau emerald, dukungan *Tap-to-Focus*, *Zoom* (`1.0x` / `1.5x` / `2.0x`), dan *Flash*.
- **Mode Absensi Umum & Mata Pelajaran + Antrean Offline**: Mendukung pemindaian absensi harian maupun per mata pelajaran, serta menyimpan antrean otomatis saat sinyal Wi-Fi terputus.

## Cara Build Menjadi File `.apk`

### Opsi 1: Otomatis via GitHub Actions (Tanpa Android Studio)
1. Buat repository baru di GitHub, lalu unggah seluruh isi folder **`EduQR-Scanner-APK/`** ke repository tersebut.
2. Buka tab **Actions** $\rightarrow$ pilih **Build EduQR Scanner APK** $\rightarrow$ klik **Run workflow**.
3. Setelah selesai (~2 menit), unduh artefak **`EduQR-Scanner-APK`** (`app-debug.apk`) dan instal ke HP Android Anda.

### Opsi 2: Lokal via Android Studio
1. Buka **Android Studio** $\rightarrow$ **Open** $\rightarrow$ pilih folder **`EduQR-Scanner-APK`**.
2. Tunggu Gradle Sync selesai, lalu klik **Build $\rightarrow$ Build Bundle(s) / APK(s) $\rightarrow$ Build APK(s)**.
3. Ambil file `app/build/outputs/apk/debug/app-debug.apk` dan instal ke HP Android.
