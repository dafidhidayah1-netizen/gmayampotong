/**
 * AyamKas Pro — Google Sheets bridge (offline-first Android sync)
 * Bound to one Google Spreadsheet. Deploy as a Web App to enable Android sync.
 *
 * Security: create an API key from the spreadsheet menu, store it only on the
 * owner's device, and restrict who can access the spreadsheet/Drive backup.
 */
const APP_NAME = 'AyamKas Pro';
const API_KEY_PROPERTY = 'AYAMKAS_ANDROID_API_KEY';
const TABLES = {
  Transaksi: ['id','updatedAt','date','ref','type','category','description','amount','paidAmount','qtyKg','batchRef','notes','status','source','deleted','deletedAt','cashEffect','effectivePaid'],
  Ringkasan: ['Indikator','Nilai','Keterangan'],
  KartuDO: ['Ref DO','Tanggal DO','Kg Pembelian','Nilai DO (Rp)','HPP per Kg (Rp)','Kg Terjual','Sisa Kg','Nilai Stok (Rp)','Status Stok/HPP'],
  Validasi: ['Level','Kategori','ID Transaksi','Tanggal','Referensi','Peringatan'],
  LogSinkronisasi: ['Waktu','Aksi','ID Transaksi','Perangkat/Pengguna','Keterangan']
};

function onOpen() {
  SpreadsheetApp.getUi().createMenu('AyamKas Pro')
    .addItem('Siapkan / Periksa Lembar', 'setupAyamKasPro')
    .addItem('Buat / Ganti Kunci Sinkronisasi', 'createAyamKasApiKey')
    .addItem('Hitung Ulang Ringkasan & Validasi', 'refreshAyamKasReports')
    .addItem('Cadangkan Spreadsheet ke Drive', 'backupAyamKasSpreadsheet')
    .addItem('Petunjuk Sambungkan Android', 'showAyamKasGuide')
    .addToUi();
}

function doGet() {
  return json_({
    ok: true,
    app: APP_NAME,
    endpoint: 'AyamKas Pro sync API aktif',
    actions: ['ping', 'sync', 'backup'],
    note: 'Gunakan metode POST dari aplikasi Android; jangan membagikan URL dan kunci API.'
  });
}

function doPost(e) {
  try {
    const payload = JSON.parse((e && e.postData && e.postData.contents) || '{}');
    const savedKey = PropertiesService.getScriptProperties().getProperty(API_KEY_PROPERTY);
    if (!savedKey || !payload.apiKey || !constantTimeEquals_(String(payload.apiKey), savedKey)) {
      throw new Error('Akses ditolak. Buat kunci melalui menu AyamKas Pro di spreadsheet.');
    }
    const action = String(payload.action || 'ping');
    if (action === 'ping') {
      return json_({ok:true, app:APP_NAME, spreadsheetId:SpreadsheetApp.getActiveSpreadsheet().getId(), serverTime:new Date().toISOString()});
    }
    if (action === 'sync') return json_(sync_(payload));
    if (action === 'backup') return json_(backup_());
    throw new Error('Aksi tidak dikenal: ' + action);
  } catch (err) {
    return json_({ok:false, error:String(err && err.message ? err.message : err), serverTime:new Date().toISOString()});
  }
}

function setupAyamKasPro() {
  ensureTables_();
  refreshAyamKasReports();
  SpreadsheetApp.getUi().alert(
    'AyamKas Pro siap',
    'Lembar Transaksi, Ringkasan, KartuDO, Validasi, dan LogSinkronisasi telah diperiksa. Data yang sudah ada tidak dihapus.',
    SpreadsheetApp.getUi().ButtonSet.OK
  );
}

function ensureTables_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  Object.keys(TABLES).forEach(function(name) {
    let sh = ss.getSheetByName(name);
    if (!sh) sh = ss.insertSheet(name);
    const expected = TABLES[name];
    if (sh.getLastRow() === 0) {
      sh.getRange(1,1,1,expected.length).setValues([expected]);
    } else {
      const lastCol = Math.max(sh.getLastColumn(), 1);
      const existing = sh.getRange(1,1,1,lastCol).getDisplayValues()[0].map(String);
      const missing = expected.filter(function(h) { return existing.indexOf(h) < 0; });
      if (missing.length) sh.getRange(1,lastCol+1,1,missing.length).setValues([missing]);
    }
    sh.setFrozenRows(1);
    sh.getRange(1,1,1,sh.getLastColumn()).setFontWeight('bold').setBackground('#dff1e8');
    sh.autoResizeColumns(1, Math.min(sh.getLastColumn(), 12));
  });
  const tx = ss.getSheetByName('Transaksi');
  tx.getRange('H:H').setNumberFormat('"Rp" #,##0');
  tx.getRange('I:I').setNumberFormat('"Rp" #,##0');
  tx.getRange('J:J').setNumberFormat('#,##0.###');
  tx.getRange('Q:R').setNumberFormat('"Rp" #,##0;[Red]-"Rp" #,##0');
}

function createAyamKasApiKey() {
  ensureTables_();
  const key = Utilities.getUuid().replace(/-/g,'') + Utilities.getUuid().replace(/-/g,'');
  PropertiesService.getScriptProperties().setProperty(API_KEY_PROPERTY, key);
  SpreadsheetApp.getUi().alert(
    'Kunci sinkronisasi baru dibuat',
    'Salin kunci berikut ke panel Integrasi Cloud di AyamKas Pro. Kunci lama tidak berlaku lagi.\n\n' + key +
    '\n\nJangan membagikan kunci ini. Jika hilang, buat kunci baru dan perbarui pengaturan aplikasi.',
    SpreadsheetApp.getUi().ButtonSet.OK
  );
}

function showAyamKasGuide() {
  SpreadsheetApp.getUi().alert(
    'Sambungkan AyamKas Pro Android',
    '1. Siapkan/periksa lembar.\n2. Buat kunci sinkronisasi dan salin.\n3. Deploy > New deployment > Web app.\n4. Jalankan sebagai akun Anda; berikan akses hanya sesuai kebutuhan akun Google Anda. Akses anonim, jika tersedia/dipakai untuk Android, berarti URL + kunci harus dijaga ketat.\n5. Salin URL berakhiran /exec dan kunci ke panel Integrasi Cloud pada Android.\n6. Uji pada spreadsheet salinan terlebih dahulu, lalu tekan Sinkronkan Sekarang.',
    SpreadsheetApp.getUi().ButtonSet.OK
  );
}

function sync_(payload) {
  ensureTables_();
  const lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    const incoming = Array.isArray(payload.transactions) ? payload.transactions : [];
    const deletions = Array.isArray(payload.deletedTransactions) ? payload.deletedTransactions : [];
    const sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName('Transaksi');
    const headers = sh.getRange(1,1,1,sh.getLastColumn()).getDisplayValues()[0].map(String);
    const rows = sh.getLastRow() > 1 ? sh.getRange(2,1,sh.getLastRow()-1,headers.length).getValues() : [];
    const byId = new Map();
    rows.forEach(function(row, i) {
      const obj = rowToObject_(headers, row);
      if (obj.id) byId.set(String(obj.id), {rowNumber:i+2, obj:obj});
    });

    let accepted = 0, keptServerVersion = 0, appliedDeletes = 0;
    incoming.forEach(function(tx) {
      if (!tx || !tx.id || !tx.date || !tx.type) return;
      const id = String(tx.id);
      const now = String(tx.updatedAt || new Date().toISOString());
      const existing = byId.get(id);
      const incomingTime = timeValue_(now);
      const existingTime = existing ? timeValue_(existing.obj.updatedAt) : -1;
      const deletedTime = existing ? timeValue_(existing.obj.deletedAt) : -1;
      if (existing && (existingTime > incomingTime || (existing.obj.deleted === true && deletedTime >= incomingTime))) {
        keptServerVersion++;
        return;
      }
      const normalized = normalizeTx_(tx);
      normalized.deleted = false;
      normalized.deletedAt = '';
      normalized.cashEffect = '';
      writeRecord_(sh, headers, normalized, existing ? existing.rowNumber : 0);
      accepted++;
    });

    deletions.forEach(function(del) {
      if (!del || !del.originalId) return;
      const id = String(del.originalId);
      const deletedAt = String(del.deletedAt || del.updatedAt || new Date().toISOString());
      const existing = byId.get(id);
      const existingTime = existing ? timeValue_(existing.obj.updatedAt) : -1;
      if (existing && existingTime > timeValue_(deletedAt)) {
        keptServerVersion++;
        return;
      }
      const tombstone = normalizeTx_(Object.assign({}, del, {
        id:id,
        updatedAt:deletedAt,
        deleted:true,
        deletedAt:deletedAt
      }));
      tombstone.deleted = true;
      tombstone.deletedAt = deletedAt;
      tombstone.cashEffect = '';
      writeRecord_(sh, headers, tombstone, existing ? existing.rowNumber : 0);
      appliedDeletes++;
    });

    applyCashFormulas_();
    refreshAyamKasReports();
    appendLog_('SYNC','',String(payload.deviceName || 'Android'),JSON.stringify({
      diterima:accepted, versiServerDipertahankan:keptServerVersion, penghapusan:appliedDeletes
    }));
    const all = readTransactions_();
    return {
      ok:true,
      spreadsheetId:SpreadsheetApp.getActiveSpreadsheet().getId(),
      serverTime:new Date().toISOString(),
      accepted:accepted,
      keptServerVersion:keptServerVersion,
      appliedDeletes:appliedDeletes,
      transactions:all.filter(function(tx){return !tx.deleted;}),
      deletedTransactions:all.filter(function(tx){return tx.deleted;}).map(function(tx) {
        return Object.assign({}, tx, {originalId:tx.id, id:'CLOUD-'+tx.id});
      })
    };
  } finally {
    lock.releaseLock();
  }
}

function normalizeTx_(tx) {
  const amount = number_(tx.amount);
  let paid = tx.paidAmount;
  if (paid === null || paid === undefined || paid === '') paid = '';
  else paid = number_(paid);
  const qty = tx.qtyKg;
  return {
    id:String(tx.id || ''),
    updatedAt:String(tx.updatedAt || new Date().toISOString()),
    date:String(tx.date || ''),
    ref:String(tx.ref || ''),
    type:String(tx.type || ''),
    category:String(tx.category || 'Umum'),
    description:String(tx.description || ''),
    amount:amount,
    paidAmount:paid,
    qtyKg:qty === null || qty === undefined || qty === '' ? '' : number_(qty),
    batchRef:String(tx.batchRef || ''),
    notes:String(tx.notes || ''),
    status:String(tx.status || 'draft'),
    source:String(tx.source || 'android'),
    deleted:tx.deleted === true || String(tx.deleted) === 'true',
    deletedAt:String(tx.deletedAt || ''),
    cashEffect:''
  };
}

function writeRecord_(sh, headers, obj, rowNumber) {
  const row = headers.map(function(h) { return obj[h] === undefined ? '' : obj[h]; });
  const target = rowNumber || Math.max(sh.getLastRow()+1, 2);
  sh.getRange(target,1,1,headers.length).setValues([row]);
}

function readTransactions_() {
  const sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName('Transaksi');
  if (sh.getLastRow() < 2) return [];
  const headers = sh.getRange(1,1,1,sh.getLastColumn()).getDisplayValues()[0].map(String);
  return sh.getRange(2,1,sh.getLastRow()-1,headers.length).getValues().map(function(row) {
    const o = rowToObject_(headers,row);
    o.deleted = o.deleted === true || String(o.deleted) === 'true';
    o.amount = number_(o.amount);
    o.paidAmount = o.paidAmount === '' || o.paidAmount === null ? null : number_(o.paidAmount);
    o.qtyKg = o.qtyKg === '' || o.qtyKg === null ? null : number_(o.qtyKg);
    ['id','updatedAt','date','ref','type','category','description','batchRef','notes','status','source','deletedAt'].forEach(function(k) {
      o[k] = o[k] === null || o[k] === undefined ? '' : String(o[k]);
    });
    return o;
  }).filter(function(o){return !!o.id;});
}

function applyCashFormulas_() {
  const sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName('Transaksi');
  const last = sh.getLastRow();
  if (last < 2) return;
  const cashFormulas = [];
  const paidFormulas = [];
  for (let r=2; r<=last; r++) {
    cashFormulas.push(['=IF(OR($M'+r+'<>"confirmed",$O'+r+'=TRUE),0,SWITCH($E'+r+',"capital_in",$H'+r+',"sale",IF($I'+r+'="", $H'+r+',$I'+r+'),"receivable_payment",$H'+r+',"purchase",-IF($I'+r+'="", $H'+r+',$I'+r+'),"expense",-IF($I'+r+'="", $H'+r+',$I'+r+'),"capital_out",-$H'+r+',"supplier_payment",-$H'+r+',0))']);
    paidFormulas.push(['=IF(OR($M'+r+'<>"confirmed",$O'+r+'=TRUE),0,IF(OR($E'+r+'="sale",$E'+r+'="purchase",$E'+r+'="expense"),IF($I'+r+'="", $H'+r+',$I'+r+'),0))']);
  }
  sh.getRange(2,17,cashFormulas.length,1).setFormulas(cashFormulas);
  sh.getRange(2,18,paidFormulas.length,1).setFormulas(paidFormulas);
}

function refreshAyamKasReports() {
  ensureTables_();
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const txs = readTransactions_().filter(function(tx){return !tx.deleted;});
  const refs = Array.from(new Set(txs.filter(function(tx){return tx.type==='purchase' && tx.ref && tx.status==='confirmed';}).map(function(tx){return tx.ref;})));
  const doSheet = ss.getSheetByName('KartuDO');
  if (doSheet.getMaxRows() > 1) doSheet.getRange(2,1,doSheet.getMaxRows()-1,9).clearContent();
  if (refs.length) {
    const rows = refs.map(function(ref){return [ref,'','','','','','','',''];});
    doSheet.getRange(2,1,rows.length,9).setValues(rows);
    const formulas = refs.map(function(_,i) {
      const r=i+2;
      return [
        '=IFERROR(INDEX(FILTER(Transaksi!$C$2:$C,Transaksi!$D$2:$D=$A'+r+',Transaksi!$E$2:$E="purchase",Transaksi!$M$2:$M="confirmed",Transaksi!$O$2:$O<>TRUE),1),"")',
        '=SUMIFS(Transaksi!$J:$J,Transaksi!$D:$D,$A'+r+',Transaksi!$E:$E,"purchase",Transaksi!$M:$M,"confirmed",Transaksi!$O:$O,FALSE)',
        '=SUMIFS(Transaksi!$H:$H,Transaksi!$D:$D,$A'+r+',Transaksi!$E:$E,"purchase",Transaksi!$M:$M,"confirmed",Transaksi!$O:$O,FALSE)',
        '=IFERROR(D'+r+'/C'+r+',0)',
        '=SUMIFS(Transaksi!$J:$J,Transaksi!$K:$K,$A'+r+',Transaksi!$E:$E,"sale",Transaksi!$M:$M,"confirmed",Transaksi!$O:$O,FALSE)',
        '=C'+r+'-F'+r,
        '=G'+r+'*E'+r,
        '=IF(OR(C'+r+'<=0,D'+r+'<=0),"HPP BELUM LENGKAP",IF(G'+r+'<0,"STOK NEGATIF","TERHITUNG"))'
      ];
    });
    doSheet.getRange(2,2,formulas.length,8).setFormulas(formulas);
    doSheet.getRange(2,4,refs.length,2).setNumberFormat('"Rp" #,##0');
    doSheet.getRange(2,8,refs.length,1).setNumberFormat('"Rp" #,##0');
  }

  const val = ss.getSheetByName('Validasi');
  if (val.getMaxRows() > 1) val.getRange(2,1,val.getMaxRows()-1,6).clearContent();
  const warnings = [];
  const purchases = new Map();
  txs.filter(function(tx){return tx.type==='purchase' && tx.status==='confirmed';}).forEach(function(p) {
    if (p.ref) purchases.set(p.ref.trim().toLowerCase(), p);
  });
  txs.forEach(function(tx) {
    if (tx.status !== 'confirmed') warnings.push(['PERIKSA','DRAF',tx.id,tx.date,tx.ref,'Transaksi masih draf; belum boleh masuk laporan final.']);
    if (tx.type==='purchase' && (!(number_(tx.qtyKg)>0) || !tx.ref)) warnings.push(['TINGGI','HPP',tx.id,tx.date,tx.ref,'Pembelian DO belum memiliki referensi DO atau berat kg yang valid.']);
    if (tx.type==='sale' && tx.status==='confirmed') {
      const p = purchases.get(String(tx.batchRef||'').trim().toLowerCase());
      if (!(number_(tx.qtyKg)>0)) warnings.push(['TINGGI','HPP',tx.id,tx.date,tx.ref,'Penjualan tidak memiliki berat kg valid.']);
      if (!tx.batchRef || !p) warnings.push(['TINGGI','HPP',tx.id,tx.date,tx.batchRef,'Penjualan belum tertaut ke referensi DO pembelian yang terverifikasi.']);
    }
    if (['sale','purchase','expense'].indexOf(tx.type)>=0 && (tx.paidAmount===null || tx.paidAmount===undefined || tx.paidAmount==='')) {
      warnings.push(['PERIKSA','PEMBAYARAN',tx.id,tx.date,tx.ref,'Pembayaran tidak diisi; periksa apakah transaksi tunai atau kredit.']);
    }
  });
  const soldByRef = {};
  txs.filter(function(tx){return tx.type==='sale' && tx.status==='confirmed';}).forEach(function(s) {
    const key = String(s.batchRef||'').trim().toLowerCase();
    soldByRef[key] = (soldByRef[key]||0) + Math.max(number_(s.qtyKg),0);
  });
  purchases.forEach(function(p,key) {
    const remaining = number_(p.qtyKg) - (soldByRef[key]||0);
    if (remaining < -0.0001) warnings.push(['TINGGI','STOK',p.id,p.date,p.ref,'Kg penjualan melebihi kg pembelian DO. Periksa tautan DO, susut, dan berat.']);
  });
  if (warnings.length) val.getRange(2,1,warnings.length,6).setValues(warnings);

  const sum = ss.getSheetByName('Ringkasan');
  if (sum.getMaxRows() > 1) sum.getRange(2,1,sum.getMaxRows()-1,3).clearContent();
  const summaryRows = [
    ['Saldo kas tercatat','=SUM(Transaksi!Q2:Q)','Berdasarkan transaksi terverifikasi dan pembayaran yang tercatat; cocokkan dengan kas/bank nyata.'],
    ['Omzet penjualan','=SUMIFS(Transaksi!H:H,Transaksi!E:E,"sale",Transaksi!M:M,"confirmed",Transaksi!O:O,FALSE)','Penjualan bruto terverifikasi, bukan selalu uang yang sudah diterima.'],
    ['HPP penjualan (estimasi basis kg beli)','=SUMPRODUCT(KartuDO!E2:E1000,KartuDO!F2:F1000)','Asumsi kg beli dan kg terjual berada pada basis yang sama. Belum memasukkan rendemen karkas jika berat berubah setelah pemotongan.'],
    ['Biaya operasional','=SUMIFS(Transaksi!H:H,Transaksi!E:E,"expense",Transaksi!M:M,"confirmed",Transaksi!O:O,FALSE)','Biaya yang diinput sebagai expense; cek agar gaji/biaya potong tidak dicatat ganda.'],
    ['Laba/rugi sementara','=B3-B4-B5','Omzet - HPP - biaya operasional. Gunakan sebagai final hanya jika semua peringatan HPP telah diselesaikan.'],
    ['Status laba','=IF(COUNTIF(Validasi!B2:B,"HPP")>0,"SEMENTARA — PERIKSA HPP","PERIKSA TUTUP BUKU")','Status otomatis untuk mencegah laba dianggap final saat HPP belum lengkap.'],
    ['Stok tercatat (kg)','=SUM(KartuDO!G2:G1000)','Kg pembelian terverifikasi dikurangi kg penjualan tertaut. Belum menghitung susut jika tidak dicatat terpisah.'],
    ['Nilai persediaan tersisa','=SUM(KartuDO!H2:H1000)','Stok sisa × HPP/kg per DO, pada basis berat yang sama.'],
    ['Piutang agregat','=MAX(0,SUMIFS(Transaksi!H:H,Transaksi!E:E,"sale",Transaksi!M:M,"confirmed",Transaksi!O:O,FALSE)-SUMIFS(Transaksi!R:R,Transaksi!E:E,"sale",Transaksi!M:M,"confirmed",Transaksi!O:O,FALSE)-SUMIFS(Transaksi!H:H,Transaksi!E:E,"receivable_payment",Transaksi!M:M,"confirmed",Transaksi!O:O,FALSE))','Estimasi agregat. Belum rekonsiliasi per faktur karena pembayaran piutang belum wajib tertaut ke invoice.'],
    ['Utang DO agregat','=MAX(0,SUMIFS(Transaksi!H:H,Transaksi!E:E,"purchase",Transaksi!M:M,"confirmed",Transaksi!O:O,FALSE)-SUMIFS(Transaksi!R:R,Transaksi!E:E,"purchase",Transaksi!M:M,"confirmed",Transaksi!O:O,FALSE)-SUMIFS(Transaksi!H:H,Transaksi!E:E,"supplier_payment",Transaksi!M:M,"confirmed",Transaksi!O:O,FALSE))','Estimasi agregat. Belum rekonsiliasi per faktur supplier/DO.'],
    ['Jumlah transaksi draf','=COUNTIFS(Transaksi!M:M,"draft",Transaksi!O:O,FALSE)','Selesaikan verifikasi sebelum tutup periode.'],
    ['Jumlah peringatan','=COUNTA(Validasi!A2:A)','Buka lembar Validasi untuk melihat hal yang perlu dibenahi.']
  ];
  sum.getRange(2,1,summaryRows.length,3).setValues(summaryRows.map(function(r){return [r[0],r[1],r[2]];}));
  summaryRows.forEach(function(r,i){if(String(r[1]).charAt(0)==='=') sum.getRange(i+2,2).setFormula(r[1]);});
  sum.getRange(2,2,summaryRows.length,1).setNumberFormat('"Rp" #,##0;[Red]-"Rp" #,##0');
  sum.getRange(8,2,1,1).setNumberFormat('#,##0.###');
  sum.getRange(2,1,summaryRows.length,3).setWrap(true);
  sum.setColumnWidth(1,240); sum.setColumnWidth(2,190); sum.setColumnWidth(3,460);
  val.setColumnWidth(1,95); val.setColumnWidth(2,110); val.setColumnWidth(6,520);
  doSheet.setColumnWidth(1,140); doSheet.setColumnWidth(9,170);
}

function backupAyamKasSpreadsheet() {
  const result = backup_();
  SpreadsheetApp.getUi().alert('Cadangan selesai', result.name + '\n' + result.url, SpreadsheetApp.getUi().ButtonSet.OK);
}

function backup_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const copy = DriveApp.getFileById(ss.getId()).makeCopy(
    'Backup AyamKas Pro ' + Utilities.formatDate(new Date(), Session.getScriptTimeZone(), 'yyyy-MM-dd HHmmss')
  );
  appendLog_('BACKUP',copy.getId(),'Google Sheets','Salinan penuh spreadsheet dibuat di Drive');
  return {ok:true, name:copy.getName(), url:copy.getUrl(), fileId:copy.getId()};
}

function appendLog_(action,id,device,details) {
  const sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName('LogSinkronisasi');
  if (sh) sh.appendRow([new Date(),action,id,device,details]);
}
function rowToObject_(headers,row) {
  const o = {};
  headers.forEach(function(h,i){o[h]=row[i] instanceof Date ? row[i].toISOString() : row[i];});
  return o;
}
function number_(v) {
  if (v === '' || v === null || v === undefined) return 0;
  const n = Number(v);
  return Number.isFinite(n) ? n : 0;
}
function timeValue_(v) {
  if (!v) return -1;
  const t = Date.parse(String(v));
  return Number.isFinite(t) ? t : -1;
}
function constantTimeEquals_(a,b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i=0; i<a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}
function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}