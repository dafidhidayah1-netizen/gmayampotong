# AyamKas Pro — Pembukuan Usaha Ayam Potong

Aplikasi Android native menggunakan Kotlin dan Jetpack Compose untuk pembukuan operasional usaha ayam potong (GM Ayam Potong). Data utama tersimpan lokal di Room Database sehingga pencatatan tetap dapat dilakukan offline. Sinkronisasi Google Sheets/Drive bersifat opsional dan baru aktif setelah pemilik mengatur Apps Script Web App. Tidak diperlukan add-on marketplace atau API pihak ketiga.

## Fitur Utama

- **Dashboard Operasional & Ringkasan Keuangan:**
  - Saldo kas tercatat (dari transaksi terverifikasi)
  - Omzet penjualan ayam
  - Biaya operasional harian & pakan
  - Laba/rugi sementara yang hanya layak difinalkan setelah HPP, basis berat, pembayaran, dan stok direkonsiliasi
  - Total stok ayam tercatat (kg) & estimasi nilai sisa persediaan
  - Rekap Utang DO & Piutang Penjualan
  - Indikator Kualitas Pencatatan (% terverifikasi, penjualan tanpa HPP, status draf)
  - Mutasi kas terbaru (+ / −)

- **Buku Transaksi:**
  - 7 Jenis transaksi spesifik: Modal Masuk Owner, Pembelian DO Ayam, Penjualan / Omzet, Biaya Operasional, Prive / Modal Keluar, Bayar Utang DO, dan Terima Piutang Pelanggan.
  - Alur verifikasi: 33 catatan historis awal dimuat sebagai draf agar tidak masuk saldo/laporan resmi sebelum dokumen, tanggal, pembayaran, berat, dan tautan DO ditinjau.
  - Filter pencarian, filter jenis transaksi, filter status (Semua / Draf / Terverifikasi), dan filter bulan.
  - Aksi Tinjau/Edit, Konfirmasi/Batalkan Verifikasi, dan Hapus.

- **DO & Stok Ayam Potong:**
  - Kontrol berat pembelian (kg), kg terjual tertaut, dan sisa stok ayam per nomor DO.
  - Perhitungan HPP per kg per DO; beberapa baris pembelian dengan referensi DO yang sama digabung menggunakan biaya/kg tertimbang.
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


## Integrasi Google Sheets & Drive

Konektor yang direkomendasikan untuk APK ini ada di \`cloud-sync/Code.gs\`. Gunakan **Ekstensi → Apps Script** dari spreadsheet yang akan menjadi buku utama—tidak perlu memilih Add-on/API Connector dari menu yang terlihat pada screenshot.

1. Buat **salinan spreadsheet uji baru**. Jangan jalankan uji pertama di buku kas aktif.
2. Buka **Ekstensi → Apps Script**, ganti isi editor dengan file [\`cloud-sync/Code.gs\`](cloud-sync/Code.gs), lalu simpan.
3. Kembali ke spreadsheet dan muat ulang. Buka menu **AyamKas Pro → Siapkan / Periksa Lembar**.
4. Pilih **AyamKas Pro → Buat / Ganti Kunci Sinkronisasi**. Kunci ini diperlukan selain URL; simpan rahasia dan jangan membagikannya.
5. Pilih **Deploy → New deployment → Web app**, jalankan sebagai akun pemilik spreadsheet, lalu setel akses yang sesuai. Konektor Android sederhana membutuhkan endpoint yang dapat menerima POST tanpa layar login interaktif; jika akses anonim tidak diizinkan oleh akun/organisasi, autentikasi Google/OAuth tambahan perlu dibuat.
6. Salin URL deployment yang berakhiran \`/exec\` dan kunci ke panel Cloud di APK. Tekan **Sinkronkan Sekarang**.
7. Uji dari dua arah: buat draf di HP dan pastikan masuk ke Sheets; edit transaksi di lembar \`Transaksi\` dan pastikan muncul di HP setelah sinkronisasi. Edit bidang transaksi di Sheets akan membuat statusnya kembali menjadi draf sampai ditinjau dan dikonfirmasi.
8. Uji backup spreadsheet ke Drive dan ekspor JSON lokal secara terpisah.

Petunjuk lengkap: [\`cloud-sync/README.md\`](cloud-sync/README.md).

## Alur hitung dan batasan akuntansi

- **Kas** dihitung dari uang yang benar-benar diterima/dibayar, bukan hanya nominal invoice. Isi pembayaran aktual; gunakan Rp0 untuk transaksi kredit yang belum dibayar/diterima. Kolom pembayaran kosong diperlakukan sebagai Rp0 dan diberi peringatan.
- **Omzet** adalah nilai penjualan yang terverifikasi, bukan otomatis saldo kas.
- **HPP** saat ini memakai biaya DO dibagi kg pembelian dikalikan kg terjual. Rumus ini benar hanya bila kg pembelian dan kg yang dijual menggunakan basis berat yang sama. Jika membeli ayam hidup tetapi menjual karkas, wajib catat kg hasil karkas, susut/hasil samping, dan biaya pemotongan sebelum laba dinyatakan final.
- **Laba sementara** = omzet − HPP terjual − biaya operasional yang dicatat. Biaya tenaga kerja langsung/pemotongan jangan dimasukkan dua kali: putuskan apakah dicatat dalam biaya konversi HPP atau sebagai biaya periode sesuai rancangan pembukuan.
- **Piutang dan utang yang ada masih agregat**, belum laporan umur invoice per pelanggan/supplier. Pembayaran perlu tertaut ke invoice/DO agar dapat dihitung akurat per tagihan.
- **Absensi/gaji belum terhubung** dalam sinkronisasi Android–Sheets ini; catat gaji aktual sebagai biaya sampai modul payroll dibangun dan tersambung ke buku yang sama.
- **Data historis pada instalasi baru berstatus draf.** Perubahan ini tidak otomatis mengubah status data yang sudah tersimpan di HP. Cadangkan JSON sebelum mengubah data lama atau menggunakan fitur reset/pulihkan.

## Sumber standar dan implementasi

- IAS 2 menjelaskan biaya persediaan mencakup biaya pembelian, biaya konversi (termasuk tenaga kerja langsung dan overhead produksi), serta biaya lain agar barang siap pada kondisi/lokasi sekarang. Rumus biaya persediaan untuk barang yang saling dapat dipertukarkan lazim menggunakan FIFO atau rata-rata tertimbang. [IFRS — IAS 2 Inventories](https://www.ifrs.org/issued-standards/list-of-standards/ias-2-inventories/)
- Google Apps Script Web App menggunakan handler \`doGet(e)\` atau \`doPost(e)\` dan deployment memiliki pengaturan akses serta identitas eksekusi. [Dokumentasi resmi Apps Script Web Apps](https://developers.google.com/apps-script/guides/web)
- Script memakai \`LockService\` untuk menghindari tabrakan ketika beberapa proses memperbarui spreadsheet bersamaan. [Dokumentasi resmi LockService](https://developers.google.com/apps-script/reference/lock/lock-service)

## Teknologi & Arsitektur

- **Bahasa:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3)
- **Arsitektur:** MVVM (Model-View-ViewModel) + Clean Architecture
- **Penyimpanan Data:** Room Database (SQLite lokal terenkapsulasi)
- **Tema:** Palet Hijau Hutan & Emerald khas AyamKas Pro
