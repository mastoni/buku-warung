# Buku Warung — Kotlin Android MVP

Starter project Android native menggunakan Kotlin + Jetpack Compose.

## Target

MVP aplikasi pembukuan warung dengan target release 7 hari.

## Design Master

UI mengacu pada gambar referensi yang diberikan:
- Splash
- Login Google
- Beranda
- Jualan/Kasir
- Produk & Stok
- Pembelian
- Uang Kas
- Laporan
- Pelanggan & Hutang
- Pengaturan

## Stack

- Kotlin
- Jetpack Compose
- Material 3
- AndroidX Activity
- AndroidX Lifecycle
- Java 17
- compileSdk 37
- minSdk 24

## Kondisi Starter

Project ini sengaja dibuat sebagai FOUNDATION:
- theme hijau Buku Warung
- login placeholder
- dashboard scaffold
- bottom navigation
- product list sample
- POS scaffold
- screen scaffold untuk modul lain

Belum mengandung:
- Google OAuth production
- Room database
- transaksi persistent
- checkout production
- pembelian production
- kas production
- hutang production
- sync Google Sheets
- backup production

Fitur tersebut dikerjakan bertahap setelah foundation stabil.

## Membuka Project

1. Extract ZIP.
2. Buka folder `BukuWarung` dengan Android Studio.
3. Gunakan Android Studio Stable yang mendukung AGP 9.x.
4. Pastikan JDK 17.
5. Sync Gradle.
6. Jalankan pada emulator/device.

Jika Android Studio meminta membuat Gradle Wrapper, izinkan Android Studio membuat wrapper sesuai versi Gradle yang kompatibel dengan AGP.

## Catatan

Compose 1.12/BOM 2026.08.00 menggunakan compileSdk 37 dan membutuhkan AGP 9.1.2 atau lebih baru. Starter ini menggunakan AGP 9.4.0.
