# Alur perhitungan AyamKas Pro — aturan kontrol

## 1. ID dan status
Setiap transaksi mendapat ID unik. Nomor DO/invoice berbeda dari ID teknis, dan nomor DO untuk penjualan harus menunjuk pada tepat satu pembelian terverifikasi. Transaksi berstatus draf tidak ikut ringkasan resmi.

## 2. Pembelian DO dan hasil siap jual
Catat tanggal, supplier, nomor DO unik, nilai pembelian, berat masuk (kg), pembayaran yang benar-benar terjadi, dan—setelah pemotongan/susut—berat bersih siap jual. Jika kg siap jual kosong, sistem sementara memakai berat masuk; tandai ini sebagai asumsi, bukan berat produksi yang telah diverifikasi.

## 3. Biaya langsung per batch
Biaya yang bisa ditelusuri ke batch (mis. ongkos potong atau angkut khusus) harus ditautkan ke nomor DO yang sama melalui `batchRef`. Biaya tersebut masuk biaya batch/HPP dan tidak boleh dihitung lagi sebagai biaya operasional umum.

## 4. HPP penjualan
```
Biaya batch = nilai pembelian DO + biaya langsung yang ditautkan
HPP/kg = biaya batch / kg siap jual
HPP penjualan = kg terjual × HPP/kg
Laba kotor = omzet − HPP penjualan
```
HPP hanya dianggap valid jika DO unik, kg siap jual positif, serta penjualan punya kg dan batchRef yang valid. Penjualan yang tidak tertaut dan DO duplikat diberi status HPP belum lengkap.

## 5. Stok per DO
```
Sisa kg = kg siap jual − total kg terjual yang ditautkan
Nilai stok perkiraan = sisa kg positif × HPP/kg
```
Stok minus perlu diperiksa: kg salah, penjualan melebihi stok, DO tertukar, retur atau hasil timbang belum dicatat. Gunakan transaksi koreksi dengan jejak audit daripada menghapus sejarah.

## 6. Bedakan laba, kas, utang, dan piutang
- Omzet bukan uang diterima.
- Pembelian DO membentuk persediaan; bukan otomatis beban penuh pada hari pembelian.
- Kas dihitung dari pembayaran aktual, penerimaan/pembayaran piutang/utang, modal dan prive yang dicatat.
- Modal owner bukan omzet. Prive bukan beban operasional.
- Piutang agregat = penjualan − penerimaan saat penjualan − pembayaran piutang yang dicatat. Utang agregat = pembelian − pembayaran saat pembelian − pembayaran utang tercatat. Angka agregat dapat tidak akurat jika saldo awal, nota kredit, retur, atau pembayaran yang tidak tertaut belum direkonsiliasi.
- Laba operasional masih sementara jika HPP belum lengkap, susut/retur/gaji/biaya belum dicatat, atau ada transaksi draf.

## 7. Absensi dan penggajian
```
Gaji pokok = hari hadir disetujui × tarif harian
Lembur = jam lembur disetujui × tarif lembur per jam
Gaji hitung = gaji pokok + lembur + bonus − potongan yang disetujui
```
Ini kalkulasi operasional dasar, bukan mesin kepatuhan gaji. Verifikasi persetujuan lembur, potongan sah, BPJS, PPh 21, dan ketentuan ketenagakerjaan yang berlaku sebelum membayar.

## 8. Tutup hari
1. Cocokkan transaksi dengan nota, DO, timbangan, dan bukti bank.
2. Cocokkan kas fisik dan mutasi bank.
3. Periksa draf, DO ganda, penjualan tanpa HPP, biaya batch tanpa DO, stok minus, pembayaran sebagian, utang/piutang.
4. Simpan backup dan uji dengan membuka berkas hasil salinan.
5. Setelah periode direkonsiliasi, lakukan perubahan menggunakan transaksi koreksi dan jejak audit.

## Sumber prinsip persediaan
IAI menerbitkan SAK EMKM yang membahas biaya perolehan persediaan dan pengakuan beban ketika barang dijual: https://web.iaiglobal.or.id/SAK-EMKM. Rumus dalam aplikasi ini ditujukan untuk kontrol harian dan tetap perlu ditinjau sesuai pencatatan akuntansi usaha.
