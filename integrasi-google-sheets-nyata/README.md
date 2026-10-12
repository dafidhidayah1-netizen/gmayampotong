# AyamKas Pro — Integrasi cloud dan alur pembukuan

## Apa yang ada di cabang ini
- `Code.gs`: backend Google Apps Script untuk transaksi, ringkasan formula, karyawan, absensi, penggajian, audit log, arsip soft-delete, sinkronisasi berdasarkan ID + timestamp, dan backup spreadsheet.
- `Index.html`: panel admin web yang membaca dan menulis langsung ke spreadsheet aktif.
- Aplikasi Android native tetap memakai Room sebagai penyimpanan lokal; integrasi Android ke backend harus dikonfigurasi setelah backend dideploy.

## Instalasi yang aman
1. **Jangan pasang di satu-satunya spreadsheet operasional.** Buat salinan terlebih dahulu dan verifikasi transaksi lama, validasi, format, dan formula yang sudah ada.
2. Dari spreadsheet salinan pilih **Ekstensi → Apps Script**. Project Apps Script harus terkait dengan spreadsheet itu.
3. Buka file `Code.gs` dan ganti isi dengan file dari folder ini. Tambahkan file bertipe **HTML** bernama tepat `Index` (editor menampilkan `Index.html`), lalu tempel isi `Index.html`.
4. Simpan semua file. Pilih fungsi `configureApiToken` dan jalankan satu kali. Berikan izin yang diminta. Buka **Execution log** milik pemilik script, lalu salin nilai `AYAMKAS_API_TOKEN`. Jangan kirim token melalui chat atau menyimpannya di spreadsheet.
5. Pilih fungsi `setup_` dan jalankan satu kali. Fungsi ini hanya membuat tab yang belum ada dan menambahkan header yang kurang; ia tidak mereset transaksi.
6. Deploy → New deployment → **Web app**. Pilih *Execute as: Me*. Atur akses mengikuti kebutuhan dan kebijakan akun Google. Akses untuk pemanggilan langsung Android dapat memerlukan deployment yang dapat diakses publik; token membatasi operasi API tetapi tidak sama dengan autentikasi multi-pengguna.
7. Buka URL deployment berakhiran `/exec`, masukkan token pada panel, lalu tekan **Uji koneksi & muat data**. Uji satu transaksi baru, cocokkan tab `Transaksi` dan formula `Ringkasan`, lalu gunakan backup dan buka file hasil salinan untuk memeriksa bahwa datanya benar-benar ada.
8. Jika kode diubah sesudah deploy, perbarui deployment ke versi baru. Menyimpan source saja tidak otomatis memperbarui deployment aktif.
9. Untuk Google Sites: Insert → Embed → URL → masukkan URL `/exec`. Uji dari akun/browser lain dan jangan menaruh penggajian di halaman publik.

## Token dan perlindungan data
- Token dibuat dan disimpan pada Script Properties server; tidak ada token baku tertanam di HTML.
- Siapa pun yang memperoleh token dan dapat mengakses deployment berpotensi mengirim perubahan. Batasi penyebaran URL/token, dan rotasi token jika bocor.
- Deployment publik dengan token sederhana sesuai untuk uji terbatas pribadi; bukan pengganti OAuth, peran pengguna, persetujuan berjenjang atau keamanan perusahaan.
- Jangan menampilkan NIK, rekening, detail gaji, atau dokumen identitas melalui deployment publik.

## Batasan yang harus diketahui
- GitHub tidak dapat otomatis menghubungkan atau men-deploy ke akun Google Anda. Pemasangan dan izin di akun pemilik tetap diperlukan.
- HPP/stok dihitung dari formula batch. Validasi pada data uji yang diketahui benar sebelum dipakai secara operasional.
- Piutang dan utang yang ditampilkan masih berupa estimasi agregat. Umur saldo, jatuh tempo, dan saldo per pelanggan/supplier memerlukan modul invoice serta pembayaran yang tertaut ke invoice/DO.
- Payroll adalah kalkulasi dasar; tidak otomatis menetapkan kewajiban lembur, BPJS, PPh 21 atau semua ketentuan ketenagakerjaan.
- Backup menyalin spreadsheet, bukan data lokal Android.
- Jangan menganggap status koneksi saja sebagai bukti berhasil. Verifikasi respons API, ID transaksi, waktu sync, nilai sheet dan rekonsiliasi kas fisik.

## Referensi
- Google Apps Script Web Apps: https://developers.google.com/apps-script/guides/web
- Apps Script Content Service: https://developers.google.com/apps-script/guides/content
- IAI — SAK EMKM: https://web.iaiglobal.or.id/SAK-EMKM
