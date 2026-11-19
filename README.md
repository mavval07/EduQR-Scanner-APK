# EduQR Scanner APK — Aplikasi Android Scanner Khusus EduQR PC

Aplikasi native Android (**Kotlin + CameraX + Google ML Kit**) untuk memindai kartu QR siswa secara cepat (100% *on-device* tanpa internet) dan mengirimkannya langsung ke **Server PC EduQR v9** melalui Wi-Fi / Hotspot lokal.

## Fitur Utama
- **Pairing 1-Detik via QR Code**: Arahkan kamera HP ke QR Pairing (`EDUQRPAIR|...`) di layar PC EduQR, maka alamat IP server, PIN 4-digit, daftar siswa, dan mata pelajaran langsung tersinkron otomatis.
- **Mendukung HTTP (`8080`) & HTTPS Lokal (`8443`)**: Otomatis menerima sertifikat lokal PC EduQR tanpa peringatan browser.
- **Mode Absensi Umum & Mata Pelajaran**: Pilih mode scan langsung dari HP beserta pilihan mata pelajaran aktif dari server PC.
- **Antrean Offline Otomatis**: Jika sinyal Wi-Fi sempat terputus, hasil scan tersimpan aman di memori HP dan otomatis dikirim ulang ke PC saat Wi-Fi tersambung kembali.

## Cara Build Menjadi File `.apk`

### Opsi 1: Otomatis via GitHub Actions (Tanpa Android Studio)
1. Buat repository baru di GitHub, lalu unggah seluruh isi folder **`EduQR-Scanner-APK/`** ke repository tersebut.
2. Buka tab **Actions** $\rightarrow$ pilih **Build EduQR Scanner APK** $\rightarrow$ klik **Run workflow**.
3. Setelah selesai (~2 menit), unduh artefak **`EduQR-Scanner-APK`** (`app-debug.apk`) dan instal ke HP Android Anda.

### Opsi 2: Lokal via Android Studio
1. Buka **Android Studio** $\rightarrow$ **Open** $\rightarrow$ pilih folder **`EduQR-Scanner-APK`**.
2. Tunggu Gradle Sync selesai, lalu klik **Build $\rightarrow$ Build Bundle(s) / APK(s) $\rightarrow$ Build APK(s)**.
3. Ambil file `app/build/outputs/apk/debug/app-debug.apk` dan instal ke HP Android.
