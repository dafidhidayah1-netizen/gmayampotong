# AyamKas Pro — Integrasi Google Sheets nyata (uji/deploy manual)

Branch: `integrasi-google-sheets-nyata`

Folder ini menambahkan web app Google Apps Script yang menyimpan transaksi, karyawan, absensi, dan rekap penggajian langsung ke spreadsheet aktif. Ini bukan perubahan ke halaman Google Sites yang sudah dipublikasikan; pemilik akun harus memasang dan deploy versi ini.

## Isi
- `Code.gs`: backend Apps Script, pembuatan sheet, validasi dasar transaksi, ID unik, lock untuk penulisan bersamaan, soft-delete, audit log, dan backup file spreadsheet ke Drive.
- `Index.html`: UI web app yang membaca ulang data dari Sheets, menyimpan transaksi, karyawan, absensi, dan rekap gaji.
  
## Cara deploy
1. Buat salinan spreadsheet Google yang kosong atau salinan cadangan dari file operasional. Jangan uji pada file asli terlebih dahulu.
2. Buka spreadsheet salinan → Extensions / Ekstensi → Apps Script.
3. Ganti isi file `Code.gs` dengan file di folder ini. Tambah HTML file bernama tepat `Index` lalu salin isi `Index.html`.
4. Simpan. Jalankan fungsi `setup_` dari editor satu kali dan berikan izin yang diminta. Ini membuat sheet bila belum ada; jangan ubah menjadi fungsi reset.
5. Deploy → New deployment → Web app. Pilih execute as akun Anda. Atur siapa yang dapat mengakses sesuai kebijakan usaha; jangan gunakan akses publik untuk data gaji/transaksi sensitif.
6. Buka URL web app berakhiran `/exec` dan tes simpan/muat data. Untuk menanam di Google Sites: Insert → Embed → URL, lalu masukkan URL web app.
7. Aktifkan backup manual dari tombol Backup ke Drive. Uji membuka salinan backup dan pastikan data benar-benar ada.

## Batas yang harus dipahami
- Ini adalah integrasi server nyata setelah dipasang ke spreadsheet dan dideploy; repo sendiri tidak dapat memberikan akses ke Google Anda atau melakukan deployment atas nama Anda.
- Data transaksi dan data karyawan tersimpan di Google Sheets. Aplikasi membaca ulang data server ketika refresh.
- Backup membuat salinan seluruh file spreadsheet ke Drive. Backup tidak mencadangkan localStorage dari versi HTML lama.
- Rekap ringkasan kas di UI bukan laporan laba-rugi final. Jangan menganggap nominal transaksi sama dengan laba; HPP per batch, berat hasil potong/susut, retur, faktur pelanggan/supplier, dan biaya produksi masih perlu modul yang terintegrasi sebelum laporan final.
- Absensi dan penggajian di sini adalah modul dasar: input hadir, jam, lembur, tarif harian, potongan, dan total. Verifikasi manual masih wajib; belum menghitung aturan ketenagakerjaan/ pajak secara otomatis.
- Belum ada sinkronisasi offline-first/conflict resolution antar-perangkat. Saat online, data tersimpan di server; jika jaringan gagal, penyimpanan server gagal dan pesan error ditampilkan.
- Sebelum penggunaan operasional, uji seluruh alur pada salinan spreadsheet dan periksa permission akses. Jangan memasukkan data sensitif karyawan ke deployment publik.
