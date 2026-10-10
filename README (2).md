# AyamKas Pro — Pembukuan Ayam Potong

Aplikasi pembukuan offline yang dibungkus dengan Android WebView. Aplikasi tidak membutuhkan API key, database cloud, atau server berbayar. Transaksi disimpan di penyimpanan lokal pada perangkat.

## Isi paket

- `AyamKasPro.html` — sumber utama aplikasi yang bisa dibuka langsung di browser.
- `app/src/main/assets/index.html` — salinan HTML untuk APK. Gradle otomatis menyalin `AyamKasPro.html` ke aset Android setiap build agar kode HTML APK dan versi mandiri tetap sinkron.
- `app/` — proyek wrapper Android.
- `.github/workflows/build-apk.yml` — workflow untuk membangun APK otomatis di GitHub Actions.

## Fitur aplikasi

- Ringkasan kas, omzet, biaya, laba sementara/final, stok kg, perkiraan nilai stok, utang DO, dan piutang.
- Transaksi modal owner, pembelian DO, penjualan, biaya operasional, prive/modal keluar, pembayaran utang DO, dan penerimaan piutang.
- Catatan kg per DO, tautan penjualan ke DO, perhitungan HPP/kg, stok tersisa, dan penandaan laporan laba yang belum lengkap.
- Status draf/perlu verifikasi versus terverifikasi. Draf tidak masuk ke laporan resmi.
- Edit, hapus, konfirmasi, pencarian, filter tanggal/bulan/jenis, dan ekspor CSV/JSON.
- Pulihkan dari cadangan JSON.
- Tampilan responsif untuk ponsel dan desktop.

## Data transaksi awal

Riwayat transaksi yang diberikan pengguna disertakan sebagai draf untuk diverifikasi. Draf tidak memengaruhi laporan final sampai diverifikasi. Format tanggal campuran, jumlah pembayaran yang belum jelas, kg terjual yang tidak selalu ada, dan biaya dalam catatan harus diperiksa sebelum laporan dijadikan laporan resmi. “Untung ayam” pada catatan sumber diperlakukan sebagai omzet sementara, bukan otomatis laba bersih.

## Cara mendapatkan APK otomatis (tanpa memasang Android Studio)

Metode ini memakai GitHub Actions untuk melakukan build pada server GitHub. Tidak membutuhkan API key. Agar jalur gratisnya sederhana, buat repositori **public**; batas penggunaan paket akun GitHub dapat berbeda untuk repositori private.

1. Unduh dan ekstrak `AyamKasPro-Android-Source.zip`.
2. Buka <https://github.com/new> dan buat repositori baru bernama `AyamKasPro`. Pilih **Public**. Jangan menambahkan README/license/gitignore otomatis karena paket ini sudah memiliki file proyek sendiri.
3. Unggah **isi folder `AyamKasPro` yang sudah diekstrak** ke akar repositori, termasuk folder tersembunyi `.github/workflows`. Struktur di akar repositori harus terlihat seperti `settings.gradle`, `build.gradle`, `AyamKasPro.html`, `app/`, dan `.github/`.
4. Setelah file masuk ke branch `main`, buka tab **Actions** pada repositori dan pilih workflow **Build AyamKas Pro APK**. Push ke `main` juga akan memicu workflow secara otomatis.
5. Buka run terbaru yang sudah bertanda hijau **Success**, gulir ke bagian **Artifacts**, lalu unduh `AyamKasPro-APK`.
6. Ekstrak ZIP artefak yang diunduh. Di dalamnya ada `AyamKasPro-debug.apk`.
7. Kirim APK ke ponsel Android, buka file tersebut, izinkan instalasi dari sumber itu bila diminta, lalu instal.

Build pertama mengunduh Android SDK dan komponen build pada server GitHub; tidak menggunakan server/hosting milik aplikasi. APK yang dihasilkan adalah **debug APK** untuk instalasi langsung dan pengujian, bukan paket Play Store yang ditandatangani untuk rilis.

## Alternatif: build di komputer sendiri

1. Instal Android Studio dari <https://developer.android.com/studio>.
2. Ekstrak ZIP, lalu di Android Studio pilih **Open** dan buka folder `AyamKasPro` yang berisi `settings.gradle`.
3. Izinkan Android Studio mengunduh Android SDK Platform 35 dan komponen yang diminta. Versi Gradle yang sesuai adalah 8.7 dan JDK 17.
4. Pilih **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. APK umumnya berada di `app/build/outputs/apk/debug/app-debug.apk`.

## Sinkronisasi kode dan data

- **Kode aplikasi:** sumber `AyamKasPro.html` otomatis disalin ke aset Android ketika build berjalan. Jadi APK terbaru menggunakan kode HTML yang sama dengan versi mandiri pada saat build.
- **Data transaksi antarperangkat:** tidak otomatis tersinkron online karena aplikasi offline dan tiap perangkat punya penyimpanan sendiri. Untuk memindahkan data dari versi browser ke APK, tekan **Unduh cadangan JSON** pada aplikasi sumber, lalu di aplikasi tujuan buka **Cadangkan & Pulihkan → Pilih file JSON** dan pilih file tadi. Pemulihan mengganti data lokal yang sedang ada—ekspor cadangan terlebih dahulu.
- Untuk sinkronisasi real-time antarperangkat, dibutuhkan server/database bersama atau layanan cloud; fitur itu memang tidak termasuk paket offline ini.

## Rumus utama

- Kas = modal masuk + kas penjualan diterima + penerimaan piutang − pembayaran pembelian DO − biaya dibayar − prive − pembayaran utang DO.
- Utang DO = pembelian DO − pembayaran pembelian − pembayaran utang DO.
- Piutang = omzet penjualan − penerimaan penjualan − penerimaan piutang.
- HPP penjualan = kg terjual × (harga pembelian DO / kg pembelian DO) untuk penjualan yang ditautkan ke DO.
- Laba/rugi = omzet − HPP penjualan − biaya operasional.
- Stok DO = kg pembelian − kg penjualan terverifikasi yang ditautkan ke DO.

Aplikasi ini adalah sistem pembukuan operasional lokal, bukan pengganti pemeriksaan akuntan atau penasihat pajak. Keakuratan laporan bergantung pada catatan pembayaran, stok, dan bukti transaksi yang lengkap.
