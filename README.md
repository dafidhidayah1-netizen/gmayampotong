# AyamKas Pro — Pembukuan Usaha Ayam Potong

Android native (Kotlin + Jetpack Compose) dengan Room sebagai penyimpanan lokal. Catatan yang disimpan pengguna tetap tersedia offline. Google Sheets/Drive merupakan sinkronisasi opsional yang perlu dipasang sendiri ke spreadsheet milik pemilik usaha.

## Prinsip data nyata

- Instalasi baru **benar-benar kosong**. Tidak ada transaksi usaha yang dibundel, di-seed, atau dimuat ulang oleh tombol default. Data hanya masuk melalui input pengguna, impor/restore JSON, atau sinkronisasi spreadsheet yang dipilih pemilik.
- Form input, validasi, kalkulasi, arsip, dan ekspor/pulihkan menggunakan database Room. Data yang belum lengkap tidak boleh dianggap laba final.
- Saldo kas, omzet, HPP, stok, piutang, utang, transfer, dan pinjaman dicatat terpisah. Nominal faktur bukan otomatis jumlah kas yang diterima/dibayar.
- Logo/nama perusahaan dan mode tema System/Light/Dark disimpan di pengaturan perangkat.

## Modul Android

- **Dashboard:** saldo buku, omzet, laba kotor dan margin, laba sementara, sisa stok layak jual, nilai persediaan, dan rincian penerimaan Tunai/Transfer/QRIS serta metode historis yang belum dipetakan.
- **Transaksi:** modal, pembelian DO, penjualan, biaya, bayar utang, terima piutang, dan prive/modal keluar; pencarian, filter, draf, konfirmasi, arsip, dan pemulihan.
- **DO & Stok:** satu referensi unik per pengiriman, kuantitas, supplier, nilai DO, pembayaran, penyusutan, kematian, pertambahan berat/FCR opsional, biaya pakan/pemotongan, kg layak jual, HPP/kg, penjualan tertaut, dan sisa stok.
- **Kas & Pinjaman:** saldo terpisah DOMPET, ATM/BANK, QRIS dan akun belum dipetakan; transfer antar-akun; pinjaman/pinjaman owner, pinjaman usaha dan pemberian pinjaman ke pihak lain dengan referensi tetap untuk menghitung saldo terutang.
- **Karyawan & Payroll:** tarif harian, jam standar shift, tarif lembur, absensi, jam masuk/keluar, istirahat, lembur otomatis, tunjangan, potongan, pembayaran, dan posting biaya gaji yang disetujui.
- **Laporan:** ringkasan kas dan laba-rugi, HPP yang ditautkan ke DO, validasi data, ekspor CSV.
- **Kalkulator HPP karkas:** hasil baru muncul setelah pengguna memasukkan bobot/harga/rendemen/biaya/target sendiri; aplikasi tidak mengisi harga atau rendemen contoh sebagai hasil bisnis.
- **Cadangan:** ekspor JSON lengkap v2 untuk transaksi aktif, arsip terhapus, kas, transfer, pinjaman, karyawan, absensi, dan payroll; pemulihan v2 mengganti tabel yang tercakup. JSON v1 lama berisi transaksi saja dan mempertahankan tabel tambahan.

## Rumus operasional

**Berat layak jual (kg)** = kg pembelian + pertambahan berat terukur − penyusutan terukur − kematian ekuivalen berat.  
**Biaya batch** = nilai DO + biaya pakan (FCR × pertambahan berat × harga pakan/kg, bila ketiganya diisi lengkap) + biaya proses/pemotongan.  
**HPP per kg layak jual** = biaya batch ÷ kg layak jual.  
**HPP terjual** = jumlah kg penjualan tertaut ke DO × HPP/kg DO sumber.  
**Sisa stok DO (kg)** = kg layak jual − kg penjualan tertaut.  
**Laba kotor** = omzet − HPP barang terjual. **Laba sementara** = laba kotor − biaya operasional terpisah.

Penyusutan, kematian, pertambahan berat dan penjualan harus menggunakan satu basis berat yang konsisten. Jika berat pembelian merupakan ayam hidup sedangkan penjualan adalah karkas, ukur hasil karkas/susut aktual dan jangan memakai laba sebagai final sebelum data itu lengkap. Biaya pakan/pemotongan jangan dihitung dua kali sebagai biaya batch dan biaya operasional.

## Integrasi Google Sheets/Drive

Sumber backend: [cloud-sync/Code.gs](cloud-sync/Code.gs)  
Panduan deployment: [cloud-sync/README.md](cloud-sync/README.md)

1. Buat **salinan spreadsheet uji**.
2. Buka **Ekstensi → Apps Script**, tempel `cloud-sync/Code.gs`, simpan, dan muat ulang spreadsheet.
3. Gunakan menu **AyamKas Pro → Siapkan / Periksa Lembar**, lalu **Buat / Ganti Kunci Sinkronisasi**.
4. Deploy sebagai **Web app** yang menerima POST. Konektor sederhana ini memakai URL `/exec` + kunci API; bila akses tanpa login interaktif tidak diizinkan oleh administrator, OAuth Google perlu ditambahkan.
5. Masukkan URL dan kunci pada panel Cloud Android, lalu uji sinkronisasi dari kedua arah pada spreadsheet uji.
6. Opsional: aktifkan backup harian melalui menu **AyamKas Pro**. Backup Drive hanya menyalin spreadsheet; ekspor JSON lokal Android tetap perlu disimpan terpisah.

Tabel sinkronisasi mencakup Transaksi, KasTransaksi, KasTransfer, PinjamanLuar, Karyawan, Absensi, Payroll, Ringkasan, KartuDO, Validasi, dan LogSinkronisasi. Data dipadukan berdasarkan ID stabil dan waktu pembaruan; perubahan manual di Sheets yang menyangkut data bisnis dikembalikan menjadi draf untuk ditinjau.

## Batas yang harus diketahui

- **Koneksi belum otomatis aktif hanya karena kode ada di GitHub.** Backend harus disalin/deploy ke spreadsheet pemilik dan diuji add/edit/konfirmasi/hapus/backup dua arah. Jangan gunakan spreadsheet operasional sebelum tes itu lulus.
- Piutang dan utang agregat belum sama dengan aging per invoice; pembayaran harus memakai referensi faktur/DO untuk laporan per tagihan.
- Perhitungan payroll memakai tarif shift dan lembur yang diatur pemilik; periksa peraturan kerja/pajak yang berlaku sebelum pembayaran.
- Tidak ada sinkronisasi background 24 jam ketika aplikasi dihentikan paksa. Auto-sync berjalan saat aplikasi digunakan/online dan setelah perubahan yang memicunya.
- Versi APK dari CI bertanda tangan **debug sementara**; bukan rilisan produksi. APK ini mungkin tidak dapat dipasang sebagai update di atas instalasi bertanda tangan kunci berbeda. Sebelum migrasi/penggantian APK, ekspor JSON lengkap.

## Referensi implementasi

- [Apps Script Web Apps](https://developers.google.com/apps-script/guides/web)
- [Apps Script LockService](https://developers.google.com/apps-script/reference/lock/lock-service)
- [IFRS IAS 2 — Inventories](https://www.ifrs.org/issued-standards/list-of-standards/ias-2-inventories/)
