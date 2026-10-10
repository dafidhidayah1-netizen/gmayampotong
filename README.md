# AyamKas Pro — Pembukuan Usaha Ayam Potong

Aplikasi Android native menggunakan Kotlin dan Jetpack Compose untuk pembukuan operasional usaha perdagangan ayam potong (GM Ayam Potong). Bekerja 100% offline dengan penyimpanan lokal Room Database, tanpa ketergantungan server atau biaya langganan.

## Fitur Utama

- **Dashboard Operasional & Ringkasan Keuangan:**
  - Saldo kas tercatat (dari transaksi terverifikasi)
  - Omzet penjualan ayam
  - Biaya operasional harian & pakan
  - Laba/rugi bersih aktual (atau laba sementara jika data belum lengkap)
  - Total stok ayam tercatat (kg) & estimasi nilai sisa persediaan
  - Rekap Utang DO & Piutang Penjualan
  - Indikator Kualitas Pencatatan (% terverifikasi, penjualan tanpa HPP, status draf)
  - Mutasi kas terbaru (+ / −)

- **Buku Transaksi:**
  - 7 Jenis transaksi spesifik: Modal Masuk Owner, Pembelian DO Ayam, Penjualan / Omzet, Biaya Operasional, Prive / Modal Keluar, Bayar Utang DO, dan Terima Piutang Pelanggan.
  - Alur Verifikasi Draf: 22 catatan historis awal (B-027 s/d I-002) disertakan sebagai draf untuk ditinjau tanpa merusak laporan resmi sebelum divalidasi.
  - Filter pencarian, filter jenis transaksi, filter status (Semua / Draf / Terverifikasi), dan filter bulan.
  - Aksi Tinjau/Edit, Konfirmasi/Batalkan Verifikasi, dan Hapus.

- **DO & Stok Ayam Potong:**
  - Kontrol berat pembelian (kg), kg terjual tertaut, dan sisa stok ayam per nomor DO.
  - Perhitungan otomatis HPP (Harga Pokok Penjualan) per kg untuk setiap DO.
  - Penandaan status: Terhitung, HPP Belum Lengkap, atau Stok Negatif.

- **Laporan Keuangan & Laba Rugi:**
  - Filter rentang tanggal fleksibel (Dari Tanggal s/d Sampai Tanggal).
  - Ringkasan P&L: Omzet − HPP Ayam Terjual − Biaya Operasional = Laba/Rugi.
  - Rekapitulasi per jenis transaksi.
  - Ekspor laporan dan data transaksi ke file CSV (kompatibel Excel & Google Sheets).

- **Kalkulator Ayam Potong & Alat Cadangan:**
  - Kalkulator HPP & Marjin Penjualan Karkas (Bobot Hidup, Rendemen Karkas %, Biaya Potong, Target Laba per kg, BEP, dan Rekomendasi Harga Jual).
  - Ekspor Cadangan Lengkap (JSON kompatibel AyamKas Pro).
  - Pulihkan Data dari JSON.
  - Bagikan ringkasan pembukuan via WhatsApp.
  - Reset & Pulihkan Data Draf Awal.

## Teknologi & Arsitektur

- **Bahasa:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3)
- **Arsitektur:** MVVM (Model-View-ViewModel) + Clean Architecture
- **Penyimpanan Data:** Room Database (SQLite lokal terenkapsulasi)
- **Tema:** Palet Hijau Hutan & Emerald khas AyamKas Pro
