# Alur Operasional, Master Data, Audit, dan Sinkronisasi AyamKas Pro

Dokumen ini adalah kontrak kerja sebelum fitur baru digabungkan. Jangan menambah field, tombol, atau validasi tanpa menjelaskan bagian alur yang dilayani dan menguji akibatnya pada buku kas, stok, HPP, laporan, audit, serta sinkronisasi.

## 1. Urutan pertama kali membuka aplikasi

1. **Profil usaha**: nama usaha, alamat, telepon/WhatsApp, email, NPWP opsional, logo, banner, format dokumen, awalan/nomor dokumen, footer nota.
2. **Master operasional**: akun kas, metode pembayaran, kategori pemasukan/pengeluaran, supplier, pelanggan, produk/satuan bila digunakan, jenis biaya, dan karyawan.
3. **Koneksi & perangkat**: status data lokal, ID perangkat, URL/kunci sinkronisasi jika pemilik memilih Google Sheets. Tanpa konfigurasi cloud, aplikasi tetap boleh dipakai offline.
4. Master yang wajib memiliki default awal yang masuk akal. Pengguna tidak boleh dipaksa membuat seluruh master sebelum mencatat transaksi sederhana. Nilai default dapat diedit dan transaksi menyimpan ID serta snapshot label saat dibuat agar perubahan master tidak mengubah arti transaksi lama.

## 2. Alur transaksi

**Mulai cepat → Simpan Draf → Lengkapi bila perlu → Verifikasi → Posting ke buku → Cetak/Bagikan → Sinkronkan.**

- Tombol **Simpan Draf** menyimpan input lokal secepatnya, tidak mengubah saldo kas/stok/laba dan tidak menunggu internet. Validasi hanya memeriksa data minimum supaya catatan bisa ditemukan lagi.
- Tombol **Simpan & Verifikasi** melakukan validasi sesuai jenis transaksi. Jangan memakai satu aturan untuk semua jenis transaksi.
- Jika verifikasi gagal, tampilkan bidang yang salah beserta alasannya; pertahankan form dan input pengguna. Jangan menghapus isi form, menutup halaman, atau menampilkan sukses.
- Jika penyimpanan lokal gagal, tampilkan error sebenarnya dan jangan menutup form. Database, pergerakan kas, audit, dan status sync harus konsisten.
- Sesudah berhasil, sediakan **Simpan & Baru** untuk input beruntun; pilihan master dan tanggal tetap dipakai sebagai nilai awal yang bisa diganti.
- **DO pembelian** adalah alur khusus karena menambah batch stok dan dasar HPP. Field produksi lanjutan seperti FCR/pakan dapat disembunyikan bila tidak digunakan. Penyusutan dan kematian hanya wajib saat pengguna benar-benar mengonfirmasi data produksi sesuai kebijakan usaha.
- **Penjualan** harus ditautkan pada DO yang relevan sebelum laporan laba menyatakan HPP lengkap. Input boleh disimpan sebagai draf jika informasi itu belum tersedia.

## 3. Efek posting (satu sumber kebenaran)

Satu transaksi bisnis disimpan satu kali pada ledger utama. Pergerakan kas adalah proyeksi/tautan dari ledger, bukan sumber kedua untuk menghitung laba. Transfer antar akun hanya memindahkan saldo antar akun dan tidak menjadi pendapatan atau biaya.

Sebelum mengonfirmasi:
- tanggal kalender valid;
- nominal dan arah transaksi valid;
- bidang wajib khusus jenis transaksi sudah ada;
- pembayaran aktual dapat Rp0 untuk kredit, tetapi tidak boleh kosong bila aturan posting memerlukannya;
- stok, saldo piutang/utang, atau referensi sumber tervalidasi;
- transaksi lama tidak boleh berubah karena master label diperbarui.

Posting harus atomik: jika ledger, mirror kas, atau audit gagal ditulis, seluruh operasi dibatalkan. Pesan error wajib dikembalikan ke pengguna.

## 4. Master data adalah sumber dropdown

Pilihan akun, metode pembayaran, kategori, supplier, pelanggan, dan parameter dokumen tidak boleh tersebar sebagai daftar hard-coded di banyak form. Form mengambil pilihan dari satu master service/repository. Perubahan master berlaku untuk input berikutnya; data transaksi historis tetap mempertahankan label/arti saat transaksi terjadi.

Master memiliki: ID stabil, nama tampilan, aktif/nonaktif, tanggal pembaruan, dan versi/sumber sinkronisasi. Master yang sudah dipakai transaksi tidak dihapus permanen; nonaktifkan agar riwayat tetap terbaca.

## 5. Identitas usaha dan nota/kwitansi

Nota/kwitansi dibuat dari transaksi tersimpan serta master profil usaha. Isinya minimal:
- nama dan logo usaha; banner bila dipilih;
- alamat/kontak dan informasi pajak bila diisi;
- jenis dan nomor dokumen, tanggal, uraian, total, jumlah kas benar-benar diterima/dibayar, sisa piutang/utang bila relevan, metode/akun, tautan DO, serta status draf atau terverifikasi;
- ruang tanda tangan dan footer yang bisa dikonfigurasi.

Dokumen berstatus draf harus ditandai jelas sebagai draf, bukan bukti pembayaran final. Nomor dokumen harus stabil dan tidak berubah saat dicetak ulang. Teks yang tampil di cetakan tidak boleh mengarang kas dibayar bila nilai sebenarnya belum diketahui.

Profil dan master harus masuk alur cadangan/pemulihan dan sinkronisasi sebelum dinyatakan sama di beberapa perangkat. URI gambar lokal tidak dapat diasumsikan dapat dibuka oleh perangkat lain; aset logo/banner memerlukan mekanisme penyimpanan/ID yang dapat diakses antarperangkat.

## 6. Audit trail

Audit merupakan log tambahan, bukan pengganti ledger. Untuk membuat, mengubah, mengonfirmasi, membatalkan verifikasi, menghapus/arsipkan, memulihkan, import, serta perubahan master penting, log menyimpan ID stabil, tindakan, waktu, perangkat/pengguna bila tersedia, sumber tindakan, ringkasan sebelum/sesudah, dan hasil.

Audit bersifat append-only dari UI normal. Menghapus atau mengedit transaksi tidak menghapus jejak audit. Perubahan saldo dihitung dari ledger; jangan menghitung saldo dengan menjumlahkan audit.

## 7. Sinkronisasi offline-first

- Simpan dahulu ke database lokal; sinkronisasi tidak boleh menahan tombol input atau mengharuskan internet aktif.
- Setiap perubahan yang perlu dikirim masuk antrean/outbox dengan ID operasi idempoten, entitas, waktu/perangkat, jumlah percobaan, dan status pending/syncing/synced/failed.
- Lakukan retry dengan jeda ketika jaringan/server gagal. Satu operasi gagal tidak boleh menghapus atau mengunci data lokal lain.
- Status harus membedakan **tersimpan lokal**, **menunggu sync**, **sinkron**, dan **gagal sync**. Jangan menampilkan “berhasil sync” sebelum server mengonfirmasi.
- Aturan konflik eksplisit: data dengan ID sama digabung sesuai versi/waktu pembaruan dan kebijakan konflik; penghapusan dikirim sebagai tombstone agar tidak hidup kembali.
- Pengaturan cloud harus punya tombol uji koneksi. Error URL, key, izin, format respons, limit data, dan server harus menampilkan tindakan perbaikan.

## 8. Gerbang rilis wajib

Tidak menyatakan “siap dipakai” sebelum semua ini lulus:
1. Build APK dan seluruh unit test.
2. Tes repository untuk write transaction atomik, rollback, audit, delete/restore, dan migrasi dari tiap versi database yang didukung.
3. Tes formula yang membandingkan saldo kas per akun, transfer, utang/piutang, stok DO, HPP, laba, serta payroll dengan hitungan manual.
4. Tes UI tombol per alur: simpan draf, verifikasi, edit, simpan & baru, batal, cetak, export/import, dan kegagalan penyimpanan.
5. Tes offline → input beruntun → online → sinkron, server gagal, retry, dua perangkat, konflik, dan penghapusan.
6. Tes nota/kwitansi dengan profil lengkap/kosong, logo/banner, status draf, pembayaran Rp0/sebagian/lunas.
7. CI terakhir pada commit yang sama dengan APK yang dibagikan harus lulus. Build sukses dari commit lebih lama tidak membuktikan commit terbaru benar.

## 9. Kondisi implementasi yang perlu ditutup

- Audit lokal dan aksi cetak nota/kwitansi adalah fondasi awal; tetap perlu tes ujung-ke-ujung.
- Profil nama/alamat/kontak/logo/banner belum boleh disebut master lintas perangkat sebelum ikut cadangan dan protokol sinkronisasi.
- Master kategori/akun/metode/supplier/pelanggan masih perlu dipusatkan sebelum semua form memakai konfigurasi dinamis.
- Auto-sync berbasis timer setelah perubahan bukan outbox tahan restart; perlu antrean persisten agar gagal jaringan tidak menghilangkan operasi yang belum terkirim.
- Setiap perbaikan harus menutup satu alur sampai tuntas, bukan berhenti ketika tombol atau model data sudah ditambahkan.
