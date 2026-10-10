/**
 * SISTEM KAS TERINTEGRASI - GOOGLE SHEETS
 * Versi: 1.0
 * Engine: Google Apps Script (JavaScript)
 *
 * SETUP:
 * 1) Buat Google Sheet kosong.
 * 2) Extensions > Apps Script.
 * 3) Ganti isi Code.gs dengan file ini.
 * 4) Save, lalu jalankan setupSistemKas() sekali dan berikan izin.
 * 5) Refresh Google Sheet. Menu "KAS" akan muncul.
 *
 * Fitur nyata:
 * - Kas Masuk / Kas Keluar / Transfer dengan form dan append row.
 * - ID, tanggal, jam dan waktu input permanen oleh script (bukan NOW/TODAY untuk timestamp transaksi).
 * - Dropdown Akun, Kategori, Status, PIC, Alokasi.
 * - Dashboard / laporan otomatis dengan SUMIFS/FILTER.
 * - Arsip transaksi terhapus.
 * - Backup ke Google Drive.
 * - Restore data/formula dari file backup.
 * - Sinkronisasi paksa dengan SpreadsheetApp.flush().
 * - Auto-backup harian melalui time-driven trigger.
 *
 * Catatan:
 * Google Sheets sendiri melakukan auto-save ke cloud. Script ini menambahkan
 * fungsi "Simpan & Sync" dan backup terjadwal; script tidak meniru/fake tombol.
 */

const CFG = {
  maxRows: 2000,
  ledgerStartRow: 15,
  sheets: {
    dashboard: 'Dashboard',
    masuk: 'Kas_Masuk',
    keluar: 'Kas_Keluar',
    all: 'Transaksi_All',
    akun: 'Akun',
    transfer: 'Transfer',
    laporan: 'Laporan',
    anggaran: 'Anggaran',
    alokasi: 'Alokasi',
    piutang: 'Piutang',
    kalender: 'Kalender',
    kalkulator: 'Kalkulator',
    catatan: 'Buku_Catatan',
    pengaturan: 'Pengaturan',
    kategori: 'Kategori',
    backup: 'Cadangan_Pulihkan',
    deleted: 'Transaksi_Dihapus',
    bantuan: 'Bantuan'
  },
  moneyFormat: 'Rp #,##0;[Red](Rp #,##0);-'
};

const HEADERS = [
  'ID','Tanggal','Jam','Akun','Nama Transaksi','Kategori','Keterangan','Nominal',
  'Alokasi Anggaran','PIC','Bukti Foto/URL','No. Bukti','Proyek/Unit','Catatan','Status','Waktu Input','Input Oleh'
];

function onOpen() {
  SpreadsheetApp.getUi()
    .createMenu('KAS')
    .addItem('Simpan Kas Masuk','simpanKasMasuk')
    .addItem('Simpan Kas Keluar','simpanKasKeluar')
    .addItem('Simpan Transfer','simpanTransfer')
    .addItem('Simpan & Sync Sekarang','simpanDanSyncAktif')
    .addItem('Upload Bukti ke Google Drive','bukaUploadBukti')
    .addSeparator()
    .addItem('Sinkronkan / Refresh','sinkronkanSistem')
    .addItem('Arsipkan Transaksi Terpilih','arsipkanTransaksiTerpilih')
    .addSeparator()
    .addItem('Backup ke Google Drive','backupKeDrive')
    .addItem('Pulihkan Data dari Backup','pulihkanDariBackup')
    .addItem('Aktifkan Auto Backup Harian','aktifkanAutoBackupHarian')
    .addSeparator()
    .addItem('Buat Ulang Struktur / Setup','setupSistemKas')
    .addToUi();
}

function setupSistemKas() {
  const ss = SpreadsheetApp.getActive();
  const ui = SpreadsheetApp.getUi();
  ss.setSpreadsheetTimeZone('Asia/Jakarta');
  const onlyDefaultSheet = ss.getSheets().length === 1 && /^Sheet\d*$/.test(ss.getSheets()[0].getName()) && ss.getSheets()[0].getLastRow() <= 1;
  const answer = ui.alert('Setup Sistem Kas', 'Setup awal akan membuat ulang 18 sheet sistem. Jalankan hanya pada file kosong atau file yang sudah di-backup. Lanjut?', ui.ButtonSet.YES_NO);
  if (answer !== ui.Button.YES) return;

  Object.values(CFG.sheets).forEach(name => ensureSheet_(ss, name));

  setupPengaturan_(ss);
  setupKategori_(ss);
  setupAkun_(ss);
  setupKasForm_(ss, CFG.sheets.masuk, 'KAS MASUK', '#188038');
  setupKasForm_(ss, CFG.sheets.keluar, 'KAS KELUAR', '#d93025');
  setupTransfer_(ss);
  setupTransaksiAll_(ss);
  setupAnggaran_(ss);
  setupAlokasi_(ss);
  setupPiutang_(ss);
  setupDashboard_(ss);
  setupLaporan_(ss);
  setupKalender_(ss);
  setupKalkulator_(ss);
  setupCatatan_(ss);
  setupBackup_(ss);
  setupDeleted_(ss);
  setupBantuan_(ss);
  applyValidations_(ss);
  applyFormatting_(ss);
  if (onlyDefaultSheet) {
    const defaultName = ss.getSheets().find(s => /^Sheet\d*$/.test(s.getName()) && !Object.values(CFG.sheets).includes(s.getName()));
    if (defaultName && defaultName.getLastRow() <= 1 && ss.getSheets().length > Object.values(CFG.sheets).length) ss.deleteSheet(defaultName);
  }
  SpreadsheetApp.flush();
  ss.toast('Sistem Kas berhasil dibuat / diperbarui.', 'KAS', 5);
}

function ensureSheet_(ss, name) {
  let sh = ss.getSheetByName(name);
  if (!sh) sh = ss.insertSheet(name);
  return sh;
}

function reset_(sh) {
  sh.clear({contentsOnly:false});
  sh.setHiddenGridlines(true);
}

function styleTitle_(range, title, color) {
  range.merge();
  range.setValue(title);
  range.setFontWeight('bold').setFontSize(16).setFontColor('#ffffff')
    .setBackground(color).setHorizontalAlignment('center').setVerticalAlignment('middle');
  range.setRowHeight(30);
}

function styleHeader_(range) {
  range.setFontWeight('bold').setBackground('#e8f0fe').setFontColor('#17365d')
    .setHorizontalAlignment('center').setVerticalAlignment('middle').setWrap(true);
  range.setBorder(true,true,true,true,true,true);
}

function setupPengaturan_(ss) {
  const sh = ss.getSheetByName(CFG.sheets.pengaturan); reset_(sh);
  styleTitle_(sh.getRange('A1:F1'),'PENGATURAN','#17365d');
  sh.getRange('A3:B12').setValues([
    ['Nama Sistem','Sistem Kas Terintegrasi'],
    ['Nama Perusahaan',''],
    ['Mata Uang','IDR / Rupiah'],
    ['Zona Waktu','Asia/Jakarta'],
    ['Baris Maksimum Ledger',CFG.maxRows],
    ['Baris Mulai Ledger',CFG.ledgerStartRow],
    ['Auto Backup','Nonaktif sebelum diaktifkan dari menu KAS'],
    ['Status Script','Aktif setelah setupSistemKas dijalankan'],
    ['Catatan','Google Sheets auto-save ke cloud secara native.'],
    ['Versi','1.0']
  ]);
  sh.getRange('A3:A12').setFontWeight('bold').setBackground('#f3f6fa');
  sh.autoResizeColumns(1,2);
}

function setupKategori_(ss) {
  const sh = ss.getSheetByName(CFG.sheets.kategori); reset_(sh);
  styleTitle_(sh.getRange('A1:G1'),'MASTER KATEGORI & DROPDOWN','#17365d');
  sh.getRange('A3:G3').setValues([['Kategori Masuk','Kategori Keluar','Status','PIC','Nama Transaksi','Akun','Alokasi Anggaran']]);
  styleHeader_(sh.getRange('A3:G3'));
  const rows = [
    ['Penjualan','Belanja Barang','Draft','Admin','Penjualan','Kas Tunai','Operasional'],
    ['Piutang Masuk','Gaji','Selesai','Bendahara','Pelunasan Piutang','Bank BCA','Gaji'],
    ['Modal','Transportasi','Batal','Keuangan','Modal','Bank BRI','Marketing'],
    ['Pendapatan Lain','Listrik/Internet','Dihapus','Manager','Pendapatan Lain','Kas Besar','Proyek'],
    ['Transfer Masuk','Sewa','Pending','PIC Operasional','Lainnya','Bank Mandiri','Lainnya']
  ];
  sh.getRange(4,1,rows.length,7).setValues(rows);
  sh.getRange('I3:J3').setValues([['Petunjuk','Nilai master dapat ditambah di bawah. Dropdown mengambil kolom ini.']]);
  styleHeader_(sh.getRange('I3:J3'));
  sh.getRange('A:Q').setWrap(true);
  sh.autoResizeColumns(1,10);
}

function setupAkun_(ss) {
  const sh = ss.getSheetByName(CFG.sheets.akun); reset_(sh);
  styleTitle_(sh.getRange('A1:F1'),'DAFTAR AKUN & SALDO','#17365d');
  sh.getRange('A3:F3').setValues([['Akun','Jenis','Saldo Awal','Total Masuk','Total Keluar','Saldo Saat Ini']]);
  styleHeader_(sh.getRange('A3:F3'));
  sh.getRange('A4:C8').setValues([
    ['Kas Tunai','Kas',0],['Bank BCA','Bank',0],['Bank BRI','Bank',0],['Bank Mandiri','Bank',0],['Kas Besar','Kas',0]
  ]);
  for (let r=4;r<=50;r++) {
    sh.getRange(r,4).setFormula(`=IF(A${r}="","",SUMIFS('${CFG.sheets.masuk}'!$H:$H,'${CFG.sheets.masuk}'!$D:$D,A${r},'${CFG.sheets.masuk}'!$O:$O,"Selesai")+SUMIFS('${CFG.sheets.transfer}'!$H:$H,'${CFG.sheets.transfer}'!$F:$F,A${r},'${CFG.sheets.transfer}'!$N:$N,"Selesai"))`);
    sh.getRange(r,5).setFormula(`=IF(A${r}="","",SUMIFS('${CFG.sheets.keluar}'!$H:$H,'${CFG.sheets.keluar}'!$D:$D,A${r},'${CFG.sheets.keluar}'!$O:$O,"Selesai")+SUMIFS('${CFG.sheets.transfer}'!$G:$G,'${CFG.sheets.transfer}'!$E:$E,A${r},'${CFG.sheets.transfer}'!$N:$N,"Selesai"))`);
    sh.getRange(r,6).setFormula(`=IF(A${r}="","",C${r}+D${r}-E${r})`);
  }
  sh.getRange('C:F').setNumberFormat(CFG.moneyFormat);
  sh.setFrozenRows(3); sh.autoResizeColumns(1,6);
}

function setupKasForm_(ss, name, title, color) {
  const sh = ss.getSheetByName(name); reset_(sh);
  styleTitle_(sh.getRange('A1:Q1'),title,color);
  sh.getRange('A3:B12').setValues([
    ['Tanggal',new Date()],['Akun',''],['Nama Transaksi',''],['Kategori',''],['Keterangan',''],['Nominal',''],['Alokasi Anggaran',''],['PIC',''],['Bukti Foto/URL',''],['No. Bukti','']
  ]);
  sh.getRange('D3:E7').setValues([
    ['Proyek/Unit',''],['Catatan',''],['Status','Selesai'],['Simpan','Gunakan menu KAS > Simpan '+title.replace('KAS ','Kas ')],['Keterangan','Data form di atas akan di-append ke tabel ledger di bawah.']
  ]);
  sh.getRange('A3:A12').setFontWeight('bold').setBackground('#f3f6fa');
  sh.getRange('D3:D7').setFontWeight('bold').setBackground('#f3f6fa');
  sh.getRange('B3:B12').setBackground('#fffdf0');
  sh.getRange('E3:E7').setBackground('#fffdf0');
  sh.getRange('A14:Q14').setValues([HEADERS]); styleHeader_(sh.getRange('A14:Q14'));
  sh.getRange(CFG.ledgerStartRow,1,CFG.maxRows-CFG.ledgerStartRow+1,HEADERS.length).setBorder(true,true,true,true,false,false);
  sh.setFrozenRows(14);
  sh.getRange('A3:A3').setNumberFormat('dd/mm/yyyy');
  sh.getRange('B8:B8').setNumberFormat(CFG.moneyFormat);
  sh.getRange('B4:B12').setWrap(true);
  sh.setColumnWidths(1,17,110);
  sh.setColumnWidth(1,100); sh.setColumnWidth(5,150); sh.setColumnWidth(7,220); sh.setColumnWidth(11,220); sh.setColumnWidth(14,220);
}

function setupTransfer_(ss) {
  const sh = ss.getSheetByName(CFG.sheets.transfer); reset_(sh);
  styleTitle_(sh.getRange('A1:O1'),'TRANSFER ANTAR AKUN','#7f6000');
  sh.getRange('A3:B10').setValues([
    ['Tanggal',new Date()],['Dari Akun',''],['Ke Akun',''],['Nominal',''],['Keterangan',''],['Bukti Foto/URL',''],['No. Bukti',''],['Status','Selesai']
  ]);
  sh.getRange('A3:A10').setFontWeight('bold').setBackground('#f3f6fa');
  sh.getRange('B3:B10').setBackground('#fffdf0');
  sh.getRange('A14:O14').setValues([['ID','Tanggal','Jam','Keterangan','Dari Akun','Ke Akun','Nominal','Nominal Transfer','PIC','Bukti Foto/URL','No. Bukti','Proyek/Unit','Catatan','Status','Waktu Input']]);
  styleHeader_(sh.getRange('A14:O14')); sh.setFrozenRows(14);
  sh.getRange('B3').setNumberFormat('dd/mm/yyyy'); sh.getRange('B6').setNumberFormat(CFG.moneyFormat); sh.getRange('G: H').setNumberFormat(CFG.moneyFormat);
  sh.setColumnWidths(1,15,115); sh.setColumnWidth(4,220); sh.setColumnWidth(10,220); sh.setColumnWidth(13,220);
}

function setupTransaksiAll_(ss) {
  const sh = ss.getSheetByName(CFG.sheets.all); reset_(sh);
  styleTitle_(sh.getRange('A1:R1'),'TRANSAKSI SEMUA AKUN','#17365d');
  sh.getRange('A3:Q3').setValues([HEADERS]); styleHeader_(sh.getRange('A3:Q3'));
  sh.getRange('A4').setFormula(`=QUERY({${CFG.sheets.masuk}!A15:Q;${CFG.sheets.keluar}!A15:Q},"select * where Col1 is not null",0)`);
  sh.setFrozenRows(3); sh.setColumnWidths(1,17,110); sh.setColumnWidth(7,220); sh.setColumnWidth(11,220); sh.setColumnWidth(14,220);
  sh.getRange('H:H').setNumberFormat(CFG.moneyFormat); sh.getRange('B:B').setNumberFormat('dd/mm/yyyy');
}

function setupAnggaran_(ss) {
  const sh = ss.getSheetByName(CFG.sheets.anggaran); reset_(sh);
  styleTitle_(sh.getRange('A1:H1'),'ANGGARAN & REALISASI','#38761d');
  sh.getRange('A3:H3').setValues([['Periode','Alokasi Anggaran','Budget','Realisasi','Sisa','% Terpakai','Status','Catatan']]); styleHeader_(sh.getRange('A3:H3'));
  sh.getRange('A4:C8').setValues([
    ['2026-10','Operasional',0],['2026-10','Gaji',0],['2026-10','Marketing',0],['2026-10','Proyek',0],['2026-10','Lainnya',0]
  ]);
  for(let r=4;r<=50;r++){
    sh.getRange(r,4).setFormula(`=IF(OR(A${r}="",B${r}=""),"",SUMIFS('${CFG.sheets.keluar}'!$H:$H,'${CFG.sheets.keluar}'!$I:$I,B${r},'${CFG.sheets.keluar}'!$O:$O,"Selesai",'${CFG.sheets.keluar}'!$B:$B,">="&DATEVALUE(A${r}&"-01"),'${CFG.sheets.keluar}'!$B:$B,"<"&EDATE(DATEVALUE(A${r}&"-01"),1)))`);
    sh.getRange(r,5).setFormula(`=IF(C${r}="","",C${r}-D${r})`);
    sh.getRange(r,6).setFormula(`=IF(C${r}=0,"",D${r}/C${r})`);
    sh.getRange(r,7).setFormula(`=IF(C${r}="","",IF(D${r}>C${r},"OVER","OK"))`);
  }
  sh.getRange('C:E').setNumberFormat(CFG.moneyFormat); sh.getRange('F:F').setNumberFormat('0.0%'); sh.setFrozenRows(3); sh.autoResizeColumns(1,8);
}

function setupAlokasi_(ss) {
  const sh = ss.getSheetByName(CFG.sheets.alokasi); reset_(sh);
  styleTitle_(sh.getRange('A1:F1'),'ALOKASI ANGGARAN / PROYEK','#38761d');
  sh.getRange('A3:F3').setValues([['Alokasi','Anggaran','Terpakai','Sisa','% Terpakai','Catatan']]); styleHeader_(sh.getRange('A3:F3'));
  const rows=[['Operasional',0],['Gaji',0],['Marketing',0],['Proyek',0],['Lainnya',0]];
  sh.getRange('A4:B8').setValues(rows);
  for(let r=4;r<=50;r++){
    sh.getRange(r,3).setFormula(`=IF(A${r}="","",SUMIFS('${CFG.sheets.keluar}'!$H:$H,'${CFG.sheets.keluar}'!$I:$I,A${r},'${CFG.sheets.keluar}'!$O:$O,"Selesai"))`);
    sh.getRange(r,4).setFormula(`=IF(B${r}="","",B${r}-C${r})`);
    sh.getRange(r,5).setFormula(`=IF(B${r}=0,"",C${r}/B${r})`);
  }
  sh.getRange('B:D').setNumberFormat(CFG.moneyFormat); sh.getRange('E:E').setNumberFormat('0.0%'); sh.autoResizeColumns(1,6);
}

function setupPiutang_(ss) {
  const sh = ss.getSheetByName(CFG.sheets.piutang); reset_(sh);
  styleTitle_(sh.getRange('A1:K1'),'KELOLA PIUTANG','#674ea7');
  sh.getRange('A3:K3').setValues([['ID Piutang','Tanggal','Pelanggan/Pihak','Keterangan','Nominal','Jatuh Tempo','Sudah Dibayar','Sisa','Status','Akun Masuk','Catatan']]); styleHeader_(sh.getRange('A3:K3'));
  for(let r=4;r<=200;r++){
    sh.getRange(r,8).setFormula(`=IF(E${r}="","",MAX(0,E${r}-G${r}))`);
    sh.getRange(r,9).setFormula(`=IF(H${r}="","",IF(H${r}=0,"Lunas",IF(F${r}<TODAY(),"Jatuh Tempo","Belum Jatuh Tempo")))`);
  }
  sh.getRange('E:H').setNumberFormat(CFG.moneyFormat); sh.getRange('B:B').setNumberFormat('dd/mm/yyyy'); sh.getRange('F:F').setNumberFormat('dd/mm/yyyy'); sh.setFrozenRows(3); sh.autoResizeColumns(1,11);
}

function setupDashboard_(ss) {
  const sh = ss.getSheetByName(CFG.sheets.dashboard); reset_(sh);
  styleTitle_(sh.getRange('A1:H1'),'DASHBOARD SISTEM KAS','#17365d');
  sh.getRange('A3:B9').setValues([['Saldo Awal',0],['Kas Masuk Hari Ini',0],['Kas Keluar Hari Ini',0],['Net Hari Ini',0],['Kas Masuk Bulan Ini',0],['Kas Keluar Bulan Ini',0],['Saldo Saat Ini',0]]);
  sh.getRange('A3:A9').setFontWeight('bold').setBackground('#f3f6fa');
  sh.getRange('B3').setFormula(`=SUM('${CFG.sheets.akun}'!C4:C50)`);
  sh.getRange('B4').setFormula(`=SUMIFS('${CFG.sheets.masuk}'!$H:$H,'${CFG.sheets.masuk}'!$B:$B,TODAY(),'${CFG.sheets.masuk}'!$O:$O,"Selesai")`);
  sh.getRange('B5').setFormula(`=SUMIFS('${CFG.sheets.keluar}'!$H:$H,'${CFG.sheets.keluar}'!$B:$B,TODAY(),'${CFG.sheets.keluar}'!$O:$O,"Selesai")`);
  sh.getRange('B6').setFormula('=B4-B5');
  sh.getRange('B7').setFormula(`=SUMIFS('${CFG.sheets.masuk}'!$H:$H,'${CFG.sheets.masuk}'!$B:$B,">="&EOMONTH(TODAY(),-1)+1,'${CFG.sheets.masuk}'!$B:$B,"<="&EOMONTH(TODAY(),0),'${CFG.sheets.masuk}'!$O:$O,"Selesai")`);
  sh.getRange('B8').setFormula(`=SUMIFS('${CFG.sheets.keluar}'!$H:$H,'${CFG.sheets.keluar}'!$B:$B,">="&EOMONTH(TODAY(),-1)+1,'${CFG.sheets.keluar}'!$B:$B,"<="&EOMONTH(TODAY(),0),'${CFG.sheets.keluar}'!$O:$O,"Selesai")`);
  sh.getRange('B9').setFormula(`=SUM('${CFG.sheets.akun}'!F4:F50)`);
  sh.getRange('B3:B9').setNumberFormat(CFG.moneyFormat).setFontWeight('bold').setFontSize(12);
  sh.getRange('D3:H9').setValues([
    ['KONTROL SISTEM','','','',''],['Menu KAS','Gunakan menu atas untuk Simpan/Sync/Backup','','',''],['Warna input','Kuning = kolom yang diisi pengguna','','',''],['Status','Selesai = masuk laporan','Draft/Pending = belum dihitung','Batal/Dihapus = dikeluarkan',''],['Backup','Buat backup sebelum operasi besar','','',''],['Auto Save','Google Sheets menyimpan perubahan ke cloud secara native','','',''],['Versi','1.0','','','']
  ]);
  sh.getRange('D3:H3').merge(); sh.getRange('D3').setFontWeight('bold').setBackground('#d9ead3');
  sh.autoResizeColumns(1,8);
}

function setupLaporan_(ss) {
  const sh = ss.getSheetByName(CFG.sheets.laporan); reset_(sh);
  styleTitle_(sh.getRange('A1:H1'),'LAPORAN SEMUA AKUN','#17365d');
  sh.getRange('A3:B7').setValues([['Tanggal Mulai',new Date(new Date().getFullYear(),new Date().getMonth(),1)],['Tanggal Akhir',new Date()],['Akun','Semua'],['Kategori','Semua'],['Status','Selesai']]);
  sh.getRange('A3:A7').setFontWeight('bold').setBackground('#f3f6fa');
  sh.getRange('D3:E8').setValues([['KPI','Nilai'],['Total Masuk',''],['Total Keluar',''],['Net',''],['Jumlah Transaksi',''],['Saldo Saat Ini','']]); styleHeader_(sh.getRange('D3:E3'));
  sh.getRange('E4').setFormula(`=IF(B6="Semua",IF(B5="Semua",SUMIFS('${CFG.sheets.masuk}'!$H:$H,'${CFG.sheets.masuk}'!$B:$B,">="&B3,'${CFG.sheets.masuk}'!$B:$B,"<="&B4,'${CFG.sheets.masuk}'!$O:$O,B7),SUMIFS('${CFG.sheets.masuk}'!$H:$H,'${CFG.sheets.masuk}'!$D:$D,B5,'${CFG.sheets.masuk}'!$B:$B,">="&B3,'${CFG.sheets.masuk}'!$B:$B,"<="&B4,'${CFG.sheets.masuk}'!$O:$O,B7)),IF(B5="Semua",SUMIFS('${CFG.sheets.masuk}'!$H:$H,'${CFG.sheets.masuk}'!$F:$F,B6,'${CFG.sheets.masuk}'!$B:$B,">="&B3,'${CFG.sheets.masuk}'!$B:$B,"<="&B4,'${CFG.sheets.masuk}'!$O:$O,B7),SUMIFS('${CFG.sheets.masuk}'!$H:$H,'${CFG.sheets.masuk}'!$D:$D,B5,'${CFG.sheets.masuk}'!$F:$F,B6,'${CFG.sheets.masuk}'!$B:$B,">="&B3,'${CFG.sheets.masuk}'!$B:$B,"<="&B4,'${CFG.sheets.masuk}'!$O:$O,B7)))`);
  sh.getRange('E5').setFormula(`=IF(B6="Semua",IF(B5="Semua",SUMIFS('${CFG.sheets.keluar}'!$H:$H,'${CFG.sheets.keluar}'!$B:$B,">="&B3,'${CFG.sheets.keluar}'!$B:$B,"<="&B4,'${CFG.sheets.keluar}'!$O:$O,B7),SUMIFS('${CFG.sheets.keluar}'!$H:$H,'${CFG.sheets.keluar}'!$D:$D,B5,'${CFG.sheets.keluar}'!$B:$B,">="&B3,'${CFG.sheets.keluar}'!$B:$B,"<="&B4,'${CFG.sheets.keluar}'!$O:$O,B7)),IF(B5="Semua",SUMIFS('${CFG.sheets.keluar}'!$H:$H,'${CFG.sheets.keluar}'!$F:$F,B6,'${CFG.sheets.keluar}'!$B:$B,">="&B3,'${CFG.sheets.keluar}'!$B:$B,"<="&B4,'${CFG.sheets.keluar}'!$O:$O,B7),SUMIFS('${CFG.sheets.keluar}'!$H:$H,'${CFG.sheets.keluar}'!$D:$D,B5,'${CFG.sheets.keluar}'!$F:$F,B6,'${CFG.sheets.keluar}'!$B:$B,">="&B3,'${CFG.sheets.keluar}'!$B:$B,"<="&B4,'${CFG.sheets.keluar}'!$O:$O,B7)))`);
  sh.getRange('E6').setFormula('=E4-E5');
  sh.getRange('E7').setFormula(`=COUNTIFS('${CFG.sheets.masuk}'!$B:$B,">="&B3,'${CFG.sheets.masuk}'!$B:$B,"<="&B4,'${CFG.sheets.masuk}'!$O:$O,B7)+COUNTIFS('${CFG.sheets.keluar}'!$B:$B,">="&B3,'${CFG.sheets.keluar}'!$B:$B,"<="&B4,'${CFG.sheets.keluar}'!$O:$O,B7)`);
  sh.getRange('E8').setFormula(`=SUM('${CFG.sheets.akun}'!F4:F50)`);
  sh.getRange('E4:E6').setNumberFormat(CFG.moneyFormat); sh.getRange('E8').setNumberFormat(CFG.moneyFormat); sh.getRange('B3:B4').setNumberFormat('dd/mm/yyyy');
  sh.getRange('A10:Q10').setValues([HEADERS]); styleHeader_(sh.getRange('A10:Q10'));
  sh.getRange('A11').setFormula(`=IFERROR(FILTER('${CFG.sheets.all}'!A4:Q,'${CFG.sheets.all}'!B4:B>=B3,'${CFG.sheets.all}'!B4:B<=B4,IF(B5="Semua",TRUE,'${CFG.sheets.all}'!D4:D=B5),IF(B6="Semua",TRUE,'${CFG.sheets.all}'!F4:F=B6),IF(B7="Semua",TRUE,'${CFG.sheets.all}'!O4:O=B7)),"Tidak ada transaksi sesuai filter")`);
  sh.setFrozenRows(10); sh.setColumnWidths(1,17,110); sh.setColumnWidth(7,220); sh.setColumnWidth(11,220); sh.setColumnWidth(14,220);
}

function setupKalender_(ss) {
  const sh = ss.getSheetByName(CFG.sheets.kalender); reset_(sh);
  styleTitle_(sh.getRange('A1:H1'),'KALENDER KAS','#0b5394');
  sh.getRange('A3:H3').setValues([['Tanggal','Masuk','Keluar','Net','Jumlah Masuk','Jumlah Keluar','Status Kas','Catatan']]); styleHeader_(sh.getRange('A3:H3'));
  const d0 = new Date(new Date().getFullYear(),new Date().getMonth(),1);
  const dates=[]; for(let i=0;i<62;i++){const d=new Date(d0);d.setDate(d.getDate()+i);dates.push([d]);}
  sh.getRange(4,1,dates.length,1).setValues(dates);
  for(let r=4;r<4+dates.length;r++){
    sh.getRange(r,2).setFormula(`=SUMIFS('${CFG.sheets.masuk}'!$H:$H,'${CFG.sheets.masuk}'!$B:$B,A${r},'${CFG.sheets.masuk}'!$O:$O,"Selesai")`);
    sh.getRange(r,3).setFormula(`=SUMIFS('${CFG.sheets.keluar}'!$H:$H,'${CFG.sheets.keluar}'!$B:$B,A${r},'${CFG.sheets.keluar}'!$O:$O,"Selesai")`);
    sh.getRange(r,4).setFormula(`=B${r}-C${r}`);
    sh.getRange(r,5).setFormula(`=COUNTIFS('${CFG.sheets.masuk}'!$B:$B,A${r},'${CFG.sheets.masuk}'!$O:$O,"Selesai")`);
    sh.getRange(r,6).setFormula(`=COUNTIFS('${CFG.sheets.keluar}'!$B:$B,A${r},'${CFG.sheets.keluar}'!$O:$O,"Selesai")`);
    sh.getRange(r,7).setFormula(`=IF(D${r}<0,"Minus",IF(D${r}=0,"Nol","Positif"))`);
  }
  sh.getRange('A:A').setNumberFormat('dd/mm/yyyy'); sh.getRange('B:D').setNumberFormat(CFG.moneyFormat); sh.autoResizeColumns(1,8);
}

function setupKalkulator_(ss) {
  const sh = ss.getSheetByName(CFG.sheets.kalkulator); reset_(sh);
  styleTitle_(sh.getRange('A1:D1'),'KALKULATOR KEUANGAN','#134f5c');
  sh.getRange('A3:B8').setValues([['Nilai Awal',0],['Pemasukan',0],['Pengeluaran',0],['Margin/Net',''],['% Pengeluaran',''],['Saldo Akhir','']]);
  sh.getRange('A3:A8').setFontWeight('bold').setBackground('#f3f6fa');
  sh.getRange('B6').setFormula('=B4-B5'); sh.getRange('B7').setFormula('=IF(B4=0,"",B5/B4)'); sh.getRange('B8').setFormula('=B3+B6');
  sh.getRange('B3:B6').setNumberFormat(CFG.moneyFormat); sh.getRange('B8').setNumberFormat(CFG.moneyFormat); sh.getRange('B7').setNumberFormat('0.0%'); sh.autoResizeColumns(1,4);
}

function setupCatatan_(ss) {
  const sh = ss.getSheetByName(CFG.sheets.catatan); reset_(sh);
  styleTitle_(sh.getRange('A1:F1'),'BUKU CATATAN','#595959');
  sh.getRange('A3:F3').setValues([['Tanggal','Judul','Catatan','PIC','Prioritas','Status']]); styleHeader_(sh.getRange('A3:F3'));
  sh.getRange('E4:E20').setDataValidation(SpreadsheetApp.newDataValidation().requireValueInList(['Rendah','Sedang','Tinggi'],true).build());
  sh.getRange('F4:F20').setDataValidation(SpreadsheetApp.newDataValidation().requireValueInList(['Open','Done','Follow Up'],true).build());
  sh.getRange('A:A').setNumberFormat('dd/mm/yyyy'); sh.autoResizeColumns(1,6); sh.setColumnWidth(3,350);
}

function setupBackup_(ss) {
  const sh = ss.getSheetByName(CFG.sheets.backup); reset_(sh);
  styleTitle_(sh.getRange('A1:E1'),'CADANGAN & PULIHKAN','#7f6000');
  sh.getRange('A3:E3').setValues([['Waktu','File Backup','File ID','Dibuat Oleh','Keterangan']]); styleHeader_(sh.getRange('A3:E3'));
  sh.getRange('A5:E8').setValues([
    ['Auto Backup','Gunakan menu KAS > Aktifkan Auto Backup Harian','','',''],
    ['Backup Manual','Gunakan menu KAS > Backup ke Google Drive','','',''],
    ['Restore','Gunakan menu KAS > Pulihkan Data dari Backup lalu masukkan File ID','','',''],
    ['Auto Save','Google Sheets menyimpan perubahan ke cloud secara native.','','','']
  ]);
  sh.autoResizeColumns(1,5); sh.setColumnWidth(5,350);
}

function setupDeleted_(ss) {
  const sh = ss.getSheetByName(CFG.sheets.deleted); reset_(sh);
  styleTitle_(sh.getRange('A1:T1'),'TRANSAKSI DIHAPUS / ARSIP','#990000');
  sh.getRange('A3:T3').setValues([['Deleted At','Deleted By','Sumber'].concat(HEADERS)]); styleHeader_(sh.getRange('A3:T3'));
  sh.setColumnWidths(1,20,110);
  sh.setFrozenRows(3); sh.setColumnWidth(10,220); sh.setColumnWidth(14,220); sh.setColumnWidth(17,220);
}

function setupBantuan_(ss) {
  const sh = ss.getSheetByName(CFG.sheets.bantuan); reset_(sh);
  styleTitle_(sh.getRange('A1:F1'),'BANTUAN / CARA PAKAI','#17365d');
  sh.getRange('A3:B14').setValues([
    ['1','Isi form Kas_Masuk atau Kas_Keluar.'],
    ['2','Gunakan menu KAS > Simpan Kas Masuk / Kas Keluar.'],
    ['3','Tanggal/jam/waktu input transaksi dibuat permanen oleh script.'],
    ['4','Laporan hanya menghitung transaksi berstatus Selesai.'],
    ['5','Transfer antar akun tidak dihitung sebagai pendapatan/biaya; hanya memindahkan saldo.'],
    ['6','Bukti Foto/URL dapat diisi link Google Drive atau URL file.'],
    ['7','Arsipkan transaksi terpilih memindahkannya ke Transaksi_Dihapus sebelum menghapus dari ledger.'],
    ['8','Backup ke Google Drive membuat salinan spreadsheet yang sebenarnya.'],
    ['9','Pulihkan membutuhkan File ID backup. Restore menyalin data/formula kembali ke sheet yang sama.'],
    ['10','Aktifkan Auto Backup Harian untuk membuat backup otomatis setiap hari.'],
    ['11','Dropdown master dikelola di sheet Kategori.'],
    ['12','Google Sheets auto-save ke cloud secara native; menu Sync memaksa flush/recalculate.']
  ]);
  sh.getRange('A3:A14').setFontWeight('bold'); sh.getRange('B:B').setWrap(true); sh.setColumnWidth(1,50); sh.setColumnWidth(2,620);
}

function applyValidations_(ss) {
  const shK = ss.getSheetByName(CFG.sheets.kategori);
  const lists = {
    masukKategori: shK.getRange('A4:A100'),
    keluarKategori: shK.getRange('B4:B100'),
    status: shK.getRange('C4:C100'),
    pic: shK.getRange('D4:D100'),
    nama: shK.getRange('E4:E100'),
    akun: shK.getRange('F4:F100'),
    alokasi: shK.getRange('G4:G100')
  };
  const dm = SpreadsheetApp.newDataValidation;
  const m = ss.getSheetByName(CFG.sheets.masuk);
  const k = ss.getSheetByName(CFG.sheets.keluar);
  const t = ss.getSheetByName(CFG.sheets.transfer);
  m.getRange('B4').setDataValidation(dm().requireValueInRange(lists.akun,true).build());
  m.getRange('B5').setDataValidation(dm().requireValueInRange(lists.nama,true).build());
  m.getRange('B6').setDataValidation(dm().requireValueInRange(lists.masukKategori,true).build());
  m.getRange('B9').setDataValidation(dm().requireValueInRange(lists.alokasi,true).build());
  m.getRange('B10').setDataValidation(dm().requireValueInRange(lists.pic,true).build());
  m.getRange('E5').setDataValidation(dm().requireValueInRange(lists.status,true).build());
  k.getRange('B4').setDataValidation(dm().requireValueInRange(lists.akun,true).build());
  k.getRange('B5').setDataValidation(dm().requireValueInRange(lists.nama,true).build());
  k.getRange('B6').setDataValidation(dm().requireValueInRange(lists.keluarKategori,true).build());
  k.getRange('B9').setDataValidation(dm().requireValueInRange(lists.alokasi,true).build());
  k.getRange('B10').setDataValidation(dm().requireValueInRange(lists.pic,true).build());
  k.getRange('E5').setDataValidation(dm().requireValueInRange(lists.status,true).build());
  t.getRange('B4:B5').setDataValidation(dm().requireValueInRange(lists.akun,true).build());
  t.getRange('B10').setDataValidation(dm().requireValueInRange(lists.status,true).build());
  ss.getSheetByName(CFG.sheets.laporan).getRange('B5').setDataValidation(dm().requireValueInRange(lists.akun,true).build());
  ss.getSheetByName(CFG.sheets.laporan).getRange('B6').setDataValidation(dm().requireValueInRange(lists.keluarKategori,true).build());
  ss.getSheetByName(CFG.sheets.laporan).getRange('B7').setDataValidation(dm().requireValueInRange(lists.status,true).build());
}

function applyFormatting_(ss) {
  [CFG.sheets.masuk,CFG.sheets.keluar].forEach(name=>{
    const sh=ss.getSheetByName(name);
    sh.getRange('B3').setNumberFormat('dd/mm/yyyy');
    sh.getRange('B8').setNumberFormat(CFG.moneyFormat);
    sh.getRange(CFG.ledgerStartRow,2,CFG.maxRows-CFG.ledgerStartRow+1,1).setNumberFormat('dd/mm/yyyy');
    sh.getRange(CFG.ledgerStartRow,3,CFG.maxRows-CFG.ledgerStartRow+1,1).setNumberFormat('hh:mm:ss');
    sh.getRange(CFG.ledgerStartRow,8,CFG.maxRows-CFG.ledgerStartRow+1,1).setNumberFormat(CFG.moneyFormat);
    sh.getRange(CFG.ledgerStartRow,16,CFG.maxRows-CFG.ledgerStartRow+1,1).setNumberFormat('dd/mm/yyyy hh:mm:ss');
  });
  const t=ss.getSheetByName(CFG.sheets.transfer);
  t.getRange('B3').setNumberFormat('dd/mm/yyyy'); t.getRange('B6').setNumberFormat(CFG.moneyFormat);
  t.getRange('B15:B2000').setNumberFormat('dd/mm/yyyy'); t.getRange('C15:C2000').setNumberFormat('hh:mm:ss'); t.getRange('G15:H2000').setNumberFormat(CFG.moneyFormat); t.getRange('O15:O2000').setNumberFormat('dd/mm/yyyy hh:mm:ss');
  ss.getSheetByName(CFG.sheets.all).getRange('B4:B2000').setNumberFormat('dd/mm/yyyy');
  ss.getSheetByName(CFG.sheets.all).getRange('C4:C2000').setNumberFormat('hh:mm:ss');
  ss.getSheetByName(CFG.sheets.all).getRange('H4:H2000').setNumberFormat(CFG.moneyFormat);
  ss.getSheetByName(CFG.sheets.all).getRange('P4:P2000').setNumberFormat('dd/mm/yyyy hh:mm:ss');
  ss.getSheetByName(CFG.sheets.all).setFrozenRows(3);
}

function getUser_() {
  try { return Session.getActiveUser().getEmail() || 'User'; } catch(e) { return 'User'; }
}

function newId_(prefix) {
  const d = new Date();
  return prefix+'-'+Utilities.formatDate(d,Session.getScriptTimeZone() || 'Asia/Jakarta','yyyyMMdd-HHmmss')+'-'+Utilities.getUuid().slice(0,6).toUpperCase();
}

function validateForm_(data) {
  if (!data.date) throw new Error('Tanggal wajib diisi.');
  if (!data.account) throw new Error('Akun wajib dipilih.');
  if (!data.name) throw new Error('Nama transaksi wajib dipilih/diisi.');
  if (!data.category) throw new Error('Kategori wajib dipilih.');
  if (!data.amount || Number(data.amount)<=0) throw new Error('Nominal harus lebih besar dari 0.');
  if (!data.status) data.status='Selesai';
}

function readKasForm_(sh) {
  return {
    date: sh.getRange('B3').getValue(),
    account: sh.getRange('B4').getValue(),
    name: sh.getRange('B5').getValue(),
    category: sh.getRange('B6').getValue(),
    description: sh.getRange('B7').getValue(),
    amount: sh.getRange('B8').getValue(),
    allocation: sh.getRange('B9').getValue(),
    pic: sh.getRange('B10').getValue(),
    proof: sh.getRange('B11').getValue(),
    receipt: sh.getRange('B12').getValue(),
    project: sh.getRange('E3').getValue(),
    note: sh.getRange('E4').getValue(),
    status: sh.getRange('E5').getValue()
  };
}

function appendLedger_(sheetName, prefix) {
  const ss=SpreadsheetApp.getActive(); const sh=ss.getSheetByName(sheetName); const data=readKasForm_(sh); validateForm_(data);
  const now=new Date();
  const row=[newId_(prefix),data.date,now, data.account,data.name,data.category,data.description,Number(data.amount),data.allocation,data.pic,data.proof,data.receipt,data.project,data.note,data.status,now,getUser_()];
  const target=Math.max(sh.getLastRow()+1,CFG.ledgerStartRow);
  sh.getRange(target,1,1,row.length).setValues([row]);
  sh.getRange(target,2).setNumberFormat('dd/mm/yyyy'); sh.getRange(target,3).setNumberFormat('hh:mm:ss'); sh.getRange(target,8).setNumberFormat(CFG.moneyFormat); sh.getRange(target,16).setNumberFormat('dd/mm/yyyy hh:mm:ss');
  clearForm_(sh);
  SpreadsheetApp.flush();
  return row[0];
}

function clearForm_(sh) {
  sh.getRange('B4:B12').clearContent();
  sh.getRange('E3:E4').clearContent();
  sh.getRange('B3').setValue(new Date());
  sh.getRange('E5').setValue('Selesai');
}

function simpanKasMasuk(){
  try { const id=appendLedger_(CFG.sheets.masuk,'KM'); SpreadsheetApp.getActive().toast('Kas Masuk tersimpan: '+id,'KAS',4); }
  catch(e){SpreadsheetApp.getUi().alert('Gagal Simpan Kas Masuk',e.message,SpreadsheetApp.getUi().ButtonSet.OK);}
}
function simpanKasKeluar(){
  try { const id=appendLedger_(CFG.sheets.keluar,'KK'); SpreadsheetApp.getActive().toast('Kas Keluar tersimpan: '+id,'KAS',4); }
  catch(e){SpreadsheetApp.getUi().alert('Gagal Simpan Kas Keluar',e.message,SpreadsheetApp.getUi().ButtonSet.OK);}
}

function simpanTransfer(){
  const ss=SpreadsheetApp.getActive(), sh=ss.getSheetByName(CFG.sheets.transfer);
  try{
    const date=sh.getRange('B3').getValue(), from=sh.getRange('B4').getValue(), to=sh.getRange('B5').getValue(), amount=Number(sh.getRange('B6').getValue()), note=sh.getRange('B7').getValue(), proof=sh.getRange('B8').getValue(), receipt=sh.getRange('B9').getValue(), status=sh.getRange('B10').getValue()||'Selesai';
    if(!date||!from||!to||from===to||!amount||amount<=0) throw new Error('Tanggal, Dari Akun, Ke Akun berbeda, dan nominal wajib valid.');
    const now=new Date(); const id=newId_('TR');
    const row=[id,date,now,note,from,to,amount,amount,'',proof,receipt,'','',status,now];
    const target=Math.max(sh.getLastRow()+1,CFG.ledgerStartRow);
    sh.getRange(target,1,1,row.length).setValues([row]);
    sh.getRange(target,2).setNumberFormat('dd/mm/yyyy'); sh.getRange(target,3).setNumberFormat('hh:mm:ss'); sh.getRange(target,7,1,2).setNumberFormat(CFG.moneyFormat); sh.getRange(target,15).setNumberFormat('dd/mm/yyyy hh:mm:ss');
    sh.getRange('B4:B9').clearContent(); sh.getRange('B3').setValue(new Date()); sh.getRange('B10').setValue('Selesai');
    SpreadsheetApp.flush(); ss.toast('Transfer tersimpan: '+id,'KAS',4);
  }catch(e){ss.getUi().alert('Gagal Simpan Transfer',e.message,ss.getUi().ButtonSet.OK);}
}


function simpanDanSyncAktif(){
  const name=SpreadsheetApp.getActive().getActiveSheet().getName();
  if(name===CFG.sheets.masuk){simpanKasMasuk(); return;}
  if(name===CFG.sheets.keluar){simpanKasKeluar(); return;}
  if(name===CFG.sheets.transfer){simpanTransfer(); return;}
  sinkronkanSistem();
}

function bukaUploadBukti(){
  const ss=SpreadsheetApp.getActive();
  const range=ss.getActiveRange();
  if(!range){ss.getUi().alert('Pilih dulu sel tujuan, misalnya kolom Bukti Foto/URL pada transaksi.'); return;}
  PropertiesService.getDocumentProperties().setProperty('UPLOAD_TARGET', range.getSheet().getName()+'!'+range.getA1Notation());
  const html=HtmlService.createHtmlOutput(`
    <div style="font-family:Arial;padding:16px">
      <h3>Upload Bukti Kas</h3>
      <input id="file" type="file" accept="image/*,.pdf,.jpg,.jpeg,.png" />
      <br><br><button onclick="upload()">Upload</button>
      <span id="msg" style="margin-left:8px"></span>
      <script>
        function upload(){
          const f=document.getElementById('file').files[0];
          if(!f){document.getElementById('msg').textContent='Pilih file dulu.';return;}
          document.getElementById('msg').textContent='Mengunggah...';
          const reader=new FileReader();
          reader.onload=function(e){google.script.run.withSuccessHandler(function(url){document.getElementById('msg').textContent='Selesai';google.script.host.close();}).withFailureHandler(function(err){document.getElementById('msg').textContent=err.message;}).uploadFileToDrive(e.target.result,f.name,f.type);};
          reader.readAsDataURL(f);
        }
      </script>
    </div>`).setWidth(420).setHeight(220);
  SpreadsheetApp.getUi().showModalDialog(html,'Upload Bukti');
}

function uploadFileToDrive(dataUrl,fileName,mimeType){
  const base64=dataUrl.split(',')[1];
  const blob=Utilities.newBlob(Utilities.base64Decode(base64),mimeType,fileName);
  const ss=SpreadsheetApp.getActive();
  const src=DriveApp.getFileById(ss.getId());
  const parents=src.getParents();
  const folder=parents.hasNext()?getOrCreateFolder_(parents.next(),'Bukti_Kas'):DriveApp.getRootFolder();
  const file=folder.createFile(blob);
  const target=PropertiesService.getDocumentProperties().getProperty('UPLOAD_TARGET');
  if(target){const [sheetName,a1]=target.split('!'); const sh=ss.getSheetByName(sheetName); if(sh) sh.getRange(a1).setValue(file.getUrl());}
  SpreadsheetApp.flush();
  return file.getUrl();
}

function getOrCreateFolder_(parent,name){
  const it=parent.getFoldersByName(name); return it.hasNext()?it.next():parent.createFolder(name);
}

function sinkronkanSistem(){
  SpreadsheetApp.flush();
  SpreadsheetApp.getActive().toast('Sinkronisasi selesai. Rumus dan dashboard sudah di-flush/recalculate.','KAS',3);
}

function arsipkanTransaksiTerpilih(){
  const ss=SpreadsheetApp.getActive(), sh=ss.getActiveSheet();
  const allowed=[CFG.sheets.masuk,CFG.sheets.keluar,CFG.sheets.transfer];
  if(!allowed.includes(sh.getName())){ss.getUi().alert('Buka Kas_Masuk, Kas_Keluar, atau Transfer lalu pilih satu baris transaksi.');return;}
  const row=sh.getActiveRange().getRow();
  if(row<CFG.ledgerStartRow){ss.getUi().alert('Pilih baris transaksi di bawah header ledger.');return;}
  const values=sh.getRange(row,1,1,sh.getLastColumn()).getValues()[0];
  const archive=ss.getSheetByName(CFG.sheets.deleted);
  let common;
  if(name===CFG.sheets.transfer){
    common=[values[0],values[1],values[2],'TRANSFER: '+values[4]+' → '+values[5],'Transfer','Transfer',values[3],values[6],'',values[8],values[9],values[10],'',values[12],values[13],values[14],''];
  } else {
    common=values;
  }
  archive.appendRow([new Date(),getUser_(),name].concat(common));
  sh.deleteRow(row);
  SpreadsheetApp.flush();
  ss.toast('Transaksi dipindahkan ke Transaksi_Dihapus.','KAS',4);
}

function backupKeDrive(){
  const ss=SpreadsheetApp.getActive();
  const file=DriveApp.getFileById(ss.getId());
  const stamp=Utilities.formatDate(new Date(),Session.getScriptTimeZone()||'Asia/Jakarta','yyyyMMdd-HHmmss');
  const name='Backup_Sistem_Kas_'+stamp;
  const parents=file.getParents();
  const copy=parents.hasNext() ? file.makeCopy(name, parents.next()) : file.makeCopy(name);
  const log=ss.getSheetByName(CFG.sheets.backup);
  log.getRange(Math.max(log.getLastRow()+1,3),1,1,5).setValues([[new Date(),copy.getName(),copy.getId(),getUser_(),'Backup nyata ke Google Drive']]);
  SpreadsheetApp.flush();
  ss.toast('Backup berhasil: '+copy.getName(),'KAS',5);
}

function aktifkanAutoBackupHarian(){
  const ss=SpreadsheetApp.getActive();
  ScriptApp.getProjectTriggers().filter(t=>t.getHandlerFunction()==='backupKeDrive').forEach(t=>ScriptApp.deleteTrigger(t));
  ScriptApp.newTrigger('backupKeDrive').timeBased().everyDays(1).atHour(23).create();
  ss.getSheetByName(CFG.sheets.pengaturan).getRange('B9').setValue('Aktif - backup harian sekitar pukul 23:00');
  ss.toast('Auto Backup Harian aktif.','KAS',5);
}

function pulihkanDariBackup(){
  const ui=SpreadsheetApp.getUi();
  const result=ui.prompt('Pulihkan Data dari Backup','Masukkan File ID backup Google Sheets:',ui.ButtonSet.OK_CANCEL);
  if(result.getSelectedButton()!==ui.Button.OK)return;
  const backupId=result.getResponseText().trim();
  if(!backupId){ui.alert('File ID kosong.');return;}
  try{
    const src=SpreadsheetApp.openById(backupId), dst=SpreadsheetApp.getActive();
    Object.values(CFG.sheets).forEach(name=>{
      const s=src.getSheetByName(name); if(!s)return;
      const d=dst.getSheetByName(name)||dst.insertSheet(name);
      const srcRange=s.getDataRange(); const vals=srcRange.getValues(); const forms=srcRange.getFormulas();
      d.clearContents();
      const mixed=vals.map((row,r)=>row.map((v,c)=>forms[r][c] ? forms[r][c] : v));
      if(mixed.length && mixed[0].length){d.getRange(1,1,mixed.length,mixed[0].length).setValues(mixed);}
    });
    SpreadsheetApp.flush(); dst.toast('Data/formula berhasil dipulihkan dari backup.','KAS',5);
  }catch(e){ui.alert('Restore gagal',e.message,ui.ButtonSet.OK);}
}

function onEdit(e){
  try{
    const sh=e.range.getSheet(), name=sh.getName(), row=e.range.getRow(), col=e.range.getColumn();
    if(![CFG.sheets.masuk,CFG.sheets.keluar,CFG.sheets.transfer].includes(name) || row<CFG.ledgerStartRow)return;
    const idCell=sh.getRange(row,1), timeCell=(name===CFG.sheets.transfer?sh.getRange(row,15):sh.getRange(row,16));
    const now=new Date();
    if(!idCell.getValue())idCell.setValue(newId_(name===CFG.sheets.masuk?'KM':name===CFG.sheets.keluar?'KK':'TR'));
    if(!timeCell.getValue())timeCell.setValue(now);
    if(name!==CFG.sheets.transfer && !sh.getRange(row,2).getValue())sh.getRange(row,2).setValue(now);
    if(name!==CFG.sheets.transfer && !sh.getRange(row,3).getValue())sh.getRange(row,3).setValue(now);
    SpreadsheetApp.flush();
  }catch(err){/* intentionally silent for simple onEdit */}
}
