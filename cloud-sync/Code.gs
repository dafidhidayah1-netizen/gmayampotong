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
  Transaksi: ['id','updatedAt','date','ref','type','category','description','amount','paidAmount','qtyKg','batchRef','notes','status','source','deleted','deletedAt','cashEffect','effectivePaid','paymentMethod','cashAccount','supplier','weightLossKg','mortalityRatePct','fcr','weightGainKg','feedPricePerKg','processingCost'],
  Ringkasan: ['Indikator','Nilai','Keterangan'],
  KartuDO: ['Ref DO','Tanggal DO','Kg Pembelian','Nilai DO (Rp)','HPP per Kg (Rp)','Kg Terjual','Sisa Kg','Nilai Stok (Rp)','Status Stok/HPP'],
  Validasi: ['Level','Kategori','ID Transaksi','Tanggal','Referensi','Peringatan'],
  LogSinkronisasi: ['Waktu','Aksi','ID Transaksi','Perangkat/Pengguna','Keterangan'],
  KasTransaksi: ['id','sourceTransactionId','date','accountName','direction','amount','paymentMethod','category','description','notes','status','updatedAt','deleted'],
  KasTransfer: ['id','date','fromAccount','toAccount','amount','reference','notes','status','updatedAt'],
  PinjamanLuar: ['id','date','party','loanType','direction','amount','paidAmount','dueDate','cashAccount','notes','status','updatedAt'],
  Karyawan: ['id','name','position','dailyRate','standardHoursPerShift','overtimeRatePerHour','active','updatedAt'],
  Absensi: ['id','employeeId','employeeName','workDate','shiftName','clockIn','clockOut','breakMinutes','overtimeHours','status','notes','updatedAt'],
  Payroll: ['id','employeeId','employeeName','period','daysPresent','dailyRate','basePay','overtimeHours','overtimePay','allowance','deductions','totalPay','paidAmount','paymentMethod','cashAccount','status','notes','updatedAt']
};

function onOpen() {
  SpreadsheetApp.getUi().createMenu('AyamKas Pro')
    .addItem('Siapkan / Periksa Lembar', 'setupAyamKasPro')
    .addItem('Buat / Ganti Kunci Sinkronisasi', 'createAyamKasApiKey')
    .addItem('Hitung Ulang Ringkasan & Validasi', 'refreshAyamKasReports')
    .addItem('Cadangkan Spreadsheet ke Drive', 'backupAyamKasSpreadsheet')
    .addItem('Aktifkan Backup Otomatis Harian', 'enableDailyAyamKasBackup')
    .addItem('Nonaktifkan Backup Otomatis Harian', 'disableDailyAyamKasBackup')
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

/**
 * Two-way sync safety for edits made directly in the spreadsheet UI.
 * Business-field edits become drafts and receive a fresh timestamp so Android
 * can pull the edit. The user must explicitly set status=confirmed after review.
 * Edits performed by the Apps Script sync API do not fire this simple trigger.
 */
function onEdit(e) {
  if (!e || !e.range) return;
  const sh = e.range.getSheet();
  if (sh.getName() !== 'Transaksi' || e.range.getRow() < 2) return;

  const cols = columnMap_(sh);
  const firstCol = e.range.getColumn();
  const lastCol = firstCol + e.range.getNumColumns() - 1;
  const touches = function(name) {
    return cols[name] >= firstCol && cols[name] <= lastCol;
  };
  const businessFields = ['date','ref','type','category','description','amount','paidAmount','qtyKg','batchRef','notes'];
  const businessEdit = businessFields.some(touches);
  const statusEdit = touches('status');
  const deletedEdit = touches('deleted');
  if (!businessEdit && !statusEdit && !deletedEdit) return;

  const lock = LockService.getScriptLock();
  lock.waitLock(15000);
  try {
    const firstRow = e.range.getRow();
    const lastRow = firstRow + e.range.getNumRows() - 1;
    const now = new Date().toISOString();
    for (let row = firstRow; row <= lastRow; row++) {
      const idCell = sh.getRange(row, cols.id);
      const dateValue = sh.getRange(row, cols.date).getValue();
      const typeValue = String(sh.getRange(row, cols.type).getValue() || '').trim();
      const amountValue = Number(sh.getRange(row, cols.amount).getValue());
      const hasRequiredBase = dateValue !== '' && dateValue !== null &&
        typeValue !== '' && Number.isFinite(amountValue) && amountValue > 0;
      if (!String(idCell.getDisplayValue() || '').trim() && hasRequiredBase) {
        // New rows entered in Sheets get a stable ID only after their required
        // date/type/positive amount fields are present; they start as drafts.
        idCell.setValue(Utilities.getUuid());
        if (!String(sh.getRange(row, cols.status).getValue() || '').trim()) {
          sh.getRange(row, cols.status).setValue('draft');
        }
      }
      if (!String(idCell.getDisplayValue() || '').trim()) continue;

      const deletedCell = sh.getRange(row, cols.deleted);
      const isDeleted = deletedCell.getValue() === true ||
        String(deletedCell.getValue()).toLowerCase() === 'true';
      if (deletedEdit) {
        if (isDeleted) {
          sh.getRange(row, cols.deletedAt).setValue(now);
        } else {
          sh.getRange(row, cols.deletedAt).clearContent();
          sh.getRange(row, cols.status).setValue('draft');
        }
      } else if (isDeleted) {
        // Do not accidentally resurrect or timestamp-edit a deleted tombstone.
        continue;
      }
      sh.getRange(row, cols.updatedAt).setValue(now);
      sh.getRange(row, cols.source).setValue('sheets');
      if (businessEdit && !deletedEdit) sh.getRange(row, cols.status).setValue('draft');
    }
    applyCashFormulas_();
    refreshAyamKasReports();
  } finally {
    lock.releaseLock();
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
  // Apply number formats by header name, not fixed letters. This protects an
  // existing sheet if extra columns were inserted or its columns were rearranged.
  const tx = ss.getSheetByName('Transaksi');
  const txCols = columnMap_(tx);
  ['amount','paidAmount','cashEffect','effectivePaid'].forEach(function(header) {
    tx.getRange(1,txCols[header],tx.getMaxRows(),1).setNumberFormat('"Rp" #,##0;[Red]-"Rp" #,##0');
  });
  tx.getRange(1,txCols.qtyKg,tx.getMaxRows(),1).setNumberFormat('#,##0.###');
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
    const ledger = {};
    const auxSpecs = [
      ['cashTransactions','KasTransaksi'], ['cashFlows','KasTransfer'],
      ['externalLoans','PinjamanLuar'], ['employees','Karyawan'],
      ['attendance','Absensi'], ['payroll','Payroll']
    ];
    auxSpecs.forEach(function(spec) {
      const key = spec[0], sheetName = spec[1];
      const incomingRecords = Array.isArray(payload[key]) ? payload[key] : [];
      ledger[key] = mergeAuxRecords_(sheetName, incomingRecords);
    });
    reconcileAuxCashRows_();
    ledger.cashTransactions.records = readAuxRecords_('KasTransaksi');
    refreshAyamKasReports();
    appendLog_('SYNC','',String(payload.deviceName || 'Android'),JSON.stringify({
      diterima:accepted, versiServerDipertahankan:keptServerVersion, penghapusan:appliedDeletes,
      tabelTambahan:auxSpecs.reduce(function(acc,spec){acc[spec[0]]=ledger[spec[0]].accepted;return acc;},{})
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
      }),
      cashTransactions:ledger.cashTransactions.records,
      cashFlows:ledger.cashFlows.records,
      externalLoans:ledger.externalLoans.records,
      employees:ledger.employees.records,
      attendance:ledger.attendance.records,
      payroll:ledger.payroll.records
    };
  } finally {
    lock.releaseLock();
  }
}

function reconcileAuxCashRows_() {
  const ss=SpreadsheetApp.getActiveSpreadsheet();
  const txSheet=ss.getSheetByName('Transaksi');
  const txHeaders=txSheet.getRange(1,1,1,txSheet.getLastColumn()).getDisplayValues()[0].map(String);
  const txRows=txSheet.getLastRow()>1 ? txSheet.getRange(2,1,txSheet.getLastRow()-1,txHeaders.length).getValues() : [];
  const txById=new Map();
  txRows.forEach(function(row) {
    const tx=rowToObject_(txHeaders,row);
    if(tx.id) txById.set(String(tx.id),tx);
  });
  const cashSheet=ss.getSheetByName('KasTransaksi');
  if(!cashSheet || cashSheet.getLastRow()<2) return;
  const headers=cashSheet.getRange(1,1,1,cashSheet.getLastColumn()).getDisplayValues()[0].map(String);
  const cols=columnMap_(cashSheet);
  const rows=cashSheet.getRange(2,1,cashSheet.getLastRow()-1,headers.length).getValues();
  rows.forEach(function(row,index) {
    const record=rowToObject_(headers,row);
    const source=String(record.sourceTransactionId||'');
    if(!source || source.indexOf('LOAN-')===0) return;
    const linked=txById.get(source);
    if(!linked) return;
    const shouldDelete=String(linked.deleted).toLowerCase()==='true' || String(linked.status).toLowerCase()!=='confirmed';
    const currentlyDeleted=String(record.deleted).toLowerCase()==='true';
    if(shouldDelete!==currentlyDeleted) {
      cashSheet.getRange(index+2,cols.deleted).setValue(shouldDelete);
      cashSheet.getRange(index+2,cols.updatedAt).setValue(String(linked.updatedAt||new Date().toISOString()));
    }
  });
}

function mergeAuxRecords_(sheetName, incoming) {
  const sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(sheetName);
  const headers = sh.getRange(1,1,1,sh.getLastColumn()).getDisplayValues()[0].map(String);
  const rows = sh.getLastRow() > 1 ? sh.getRange(2,1,sh.getLastRow()-1,headers.length).getValues() : [];
  const byId = new Map();
  rows.forEach(function(row,index) {
    const obj = rowToObject_(headers,row);
    if (obj.id) byId.set(String(obj.id),{rowNumber:index+2,obj:obj});
  });
  let accepted = 0, keptServerVersion = 0;
  incoming.forEach(function(record) {
    if (!record || !record.id) return;
    const id=String(record.id);
    const existing=byId.get(id);
    const incomingTime=timeValue_(record.updatedAt);
    const serverTime=existing ? timeValue_(existing.obj.updatedAt) : -1;
    if (existing && serverTime > incomingTime) { keptServerVersion++; return; }
    const normalized={};
    headers.forEach(function(header) {
      const value=record[header];
      normalized[header]=value===undefined || value===null ? '' : value;
    });
    if (headers.indexOf('updatedAt')>=0 && !normalized.updatedAt) normalized.updatedAt=new Date().toISOString();
    writeRecord_(sh,headers,normalized,existing ? existing.rowNumber : 0);
    accepted++;
  });
  const records = readAuxRecords_(sheetName);
  return {accepted:accepted,keptServerVersion:keptServerVersion,records:records};
}

function readAuxRecords_(sheetName) {
  const sh=SpreadsheetApp.getActiveSpreadsheet().getSheetByName(sheetName);
  if (!sh || sh.getLastRow()<2) return [];
  const headers=sh.getRange(1,1,1,sh.getLastColumn()).getDisplayValues()[0].map(String);
  return sh.getRange(2,1,sh.getLastRow()-1,headers.length).getValues().map(function(row) {
    const obj=rowToObject_(headers,row);
    headers.forEach(function(header) {
      if (obj[header]===null || obj[header]===undefined) obj[header]='';
      if (['dailyRate','standardHoursPerShift','overtimeRatePerHour','amount','paidAmount','breakMinutes',
           'overtimeHours','daysPresent','basePay','overtimePay','allowance','deductions','totalPay'].indexOf(header)>=0) {
        obj[header]=obj[header]==='' ? 0 : number_(obj[header]);
      }
      if (header==='active' || header==='deleted') obj[header]=obj[header]===true || String(obj[header]).toLowerCase()==='true';
      if (header==='date' || header==='workDate' || header==='dueDate') {
        if (obj[header] instanceof Date) obj[header]=Utilities.formatDate(obj[header],Session.getScriptTimeZone(),'yyyy-MM-dd');
      }
      if (typeof obj[header]==='object' && obj[header] instanceof Date) obj[header]=obj[header].toISOString();
    });
    return obj;
  }).filter(function(obj){return !!obj.id;});
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
    paymentMethod:String(tx.paymentMethod || 'CASH'),
    cashAccount:String(tx.cashAccount || 'DOMPET'),
    supplier:String(tx.supplier || ''),
    weightLossKg:tx.weightLossKg === null || tx.weightLossKg === undefined || tx.weightLossKg === '' ? '' : number_(tx.weightLossKg),
    mortalityRatePct:tx.mortalityRatePct === null || tx.mortalityRatePct === undefined || tx.mortalityRatePct === '' ? '' : number_(tx.mortalityRatePct),
    fcr:tx.fcr === null || tx.fcr === undefined || tx.fcr === '' ? '' : number_(tx.fcr),
    weightGainKg:tx.weightGainKg === null || tx.weightGainKg === undefined || tx.weightGainKg === '' ? '' : number_(tx.weightGainKg),
    feedPricePerKg:tx.feedPricePerKg === null || tx.feedPricePerKg === undefined || tx.feedPricePerKg === '' ? '' : number_(tx.feedPricePerKg),
    processingCost:number_(tx.processingCost),
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
  const cols = columnMap_(sh);
  const ref = function(name,row) { return '$' + columnLetter_(cols[name]) + row; };
  const cashFormulas = [];
  const paidFormulas = [];
  for (let r=2; r<=last; r++) {
    const inactive = 'OR(' + ref('status',r) + '<>"confirmed",' + ref('deleted',r) + '=TRUE)';
    cashFormulas.push(['=IF(' + inactive + ',0,SWITCH(' + ref('type',r) +
      ',"capital_in",' + ref('amount',r) +
      ',"sale",N(' + ref('paidAmount',r) + ')' +
      ',"receivable_payment",' + ref('amount',r) +
      ',"purchase",-N(' + ref('paidAmount',r) + ')' +
      ',"expense",-N(' + ref('paidAmount',r) + ')' +
      ',"capital_out",-'+ref('amount',r) +
      ',"supplier_payment",-'+ref('amount',r) + ',0))']);
    paidFormulas.push(['=IF(' + inactive + ',0,IF(OR(' + ref('type',r) +
      '="sale",' + ref('type',r) + '="purchase",' + ref('type',r) +
      '="expense"),N(' + ref('paidAmount',r) + '),0))']);
  }
  sh.getRange(2,cols.cashEffect,cashFormulas.length,1).setFormulas(cashFormulas);
  sh.getRange(2,cols.effectivePaid,paidFormulas.length,1).setFormulas(paidFormulas);
}
function refreshAyamKasReports() {
  ensureTables_();
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const txSheet = ss.getSheetByName('Transaksi');
  const txCols = columnMap_(txSheet);
  const txRange = function(name) {
    const letter = columnLetter_(txCols[name]);
    return "'Transaksi'!$" + letter + "$2:$" + letter;
  };
  const txs = readTransactions_().filter(function(tx){return !tx.deleted;});

  // One row per normalized DO reference. Costs and weights are aggregated so
  // reusing a reference cannot duplicate kg sold or silently select one cost row.
  const refMap = new Map();
  txs.filter(function(tx){return tx.type==='purchase' && tx.status==='confirmed' && String(tx.ref||'').trim();})
    .forEach(function(tx) {
      const display = String(tx.ref).trim();
      const key = display.toLowerCase();
      if (!refMap.has(key)) refMap.set(key, display);
    });
  const refs = Array.from(refMap.values());
  const doSheet = ss.getSheetByName('KartuDO');
  const doCols = columnMap_(doSheet);
  const oldDoRows = Math.max(doSheet.getLastRow()-1,0);
  if (oldDoRows > 0) doSheet.getRange(2,1,oldDoRows,doSheet.getLastColumn()).clearContent();

  if (refs.length) {
    doSheet.getRange(2,doCols['Ref DO'],refs.length,1).setValues(refs.map(function(ref){return [ref];}));
    const letter = function(name) { return columnLetter_(doCols[name]); };
    const formulasByHeader = {};
    const headers = ['Tanggal DO','Kg Pembelian','Nilai DO (Rp)','HPP per Kg (Rp)','Kg Terjual','Sisa Kg','Nilai Stok (Rp)','Status Stok/HPP'];
    headers.forEach(function(header){formulasByHeader[header]=[];});
    refs.forEach(function(_,i) {
      const r=i+2;
      const doRef = '$' + letter('Ref DO') + r;
      const dateFormula = '=IFERROR(INDEX(FILTER(' + txRange('date') + ',' +
        txRange('ref') + '=' + doRef + ',' + txRange('type') + '="purchase",' +
        txRange('status') + '="confirmed",' + txRange('deleted') + '<>TRUE),1),"")';
      const kgFormula = '=SUMIFS(' + txRange('qtyKg') + ',' + txRange('ref') + ',' + doRef +
        ',' + txRange('type') + ',"purchase",' + txRange('status') + ',"confirmed",' +
        txRange('deleted') + ',"<>TRUE")';
      const costFormula = '=SUMIFS(' + txRange('amount') + ',' + txRange('ref') + ',' + doRef +
        ',' + txRange('type') + ',"purchase",' + txRange('status') + ',"confirmed",' +
        txRange('deleted') + ',"<>TRUE")';
      const soldFormula = '=SUMIFS(' + txRange('qtyKg') + ',' + txRange('batchRef') + ',' + doRef +
        ',' + txRange('type') + ',"sale",' + txRange('status') + ',"confirmed",' +
        txRange('deleted') + ',"<>TRUE")';
      formulasByHeader['Tanggal DO'].push([dateFormula]);
      formulasByHeader['Kg Pembelian'].push([kgFormula]);
      formulasByHeader['Nilai DO (Rp)'].push([costFormula]);
      formulasByHeader['HPP per Kg (Rp)'].push(['=IFERROR(' + letter('Nilai DO (Rp)') + r + '/' + letter('Kg Pembelian') + r + ',0)']);
      formulasByHeader['Kg Terjual'].push([soldFormula]);
      formulasByHeader['Sisa Kg'].push(['=' + letter('Kg Pembelian') + r + '-' + letter('Kg Terjual') + r]);
      formulasByHeader['Nilai Stok (Rp)'].push(['=MAX(0,' + letter('Sisa Kg') + r + ')*' + letter('HPP per Kg (Rp)') + r]);
      formulasByHeader['Status Stok/HPP'].push(['=IF(OR(' + letter('Kg Pembelian') + r + '<=0,' +
        letter('Nilai DO (Rp)') + r + '<=0),"HPP BELUM LENGKAP",IF(' +
        letter('Sisa Kg') + r + '<0,"STOK NEGATIF","TERHITUNG"))']);
    });
    Object.keys(formulasByHeader).forEach(function(header) {
      doSheet.getRange(2,doCols[header],refs.length,1).setFormulas(formulasByHeader[header]);
    });
    ['Nilai DO (Rp)','HPP per Kg (Rp)','Nilai Stok (Rp)'].forEach(function(header) {
      doSheet.getRange(2,doCols[header],refs.length,1).setNumberFormat('"Rp" #,##0;[Red]-"Rp" #,##0');
    });
    ['Kg Pembelian','Kg Terjual','Sisa Kg'].forEach(function(header) {
      doSheet.getRange(2,doCols[header],refs.length,1).setNumberFormat('#,##0.###');
    });
  }

  const val = ss.getSheetByName('Validasi');
  const valCols = columnMap_(val);
  const oldValRows = Math.max(val.getLastRow()-1,0);
  if (oldValRows > 0) val.getRange(2,1,oldValRows,val.getLastColumn()).clearContent();
  const warnings = [];
  const purchases = new Map();
  txs.filter(function(tx){return tx.type==='purchase' && tx.status==='confirmed';}).forEach(function(p) {
    const displayRef = String(p.ref||'').trim();
    if (!displayRef) return;
    const key = displayRef.toLowerCase();
    if (!purchases.has(key)) {
      purchases.set(key,{id:p.id,date:p.date,ref:displayRef,qtyKg:0,amount:0,rows:0});
    }
    const batch = purchases.get(key);
    batch.qtyKg += Math.max(number_(p.qtyKg),0);
    batch.amount += Math.max(number_(p.amount),0);
    batch.rows += 1;
    if (String(p.date) < String(batch.date)) batch.date = p.date;
  });

  txs.forEach(function(tx) {
    if (tx.status !== 'confirmed') {
      warnings.push(['PERIKSA','DRAF',tx.id,tx.date,tx.ref,'Transaksi masih draf; belum boleh masuk laporan final.']);
    }
    if (tx.status === 'confirmed' && number_(tx.amount) <= 0) {
      warnings.push(['TINGGI','NOMINAL',tx.id,tx.date,tx.ref,'Nominal transaksi terverifikasi harus lebih besar dari nol.']);
    }
    if (tx.type==='purchase' && (!(number_(tx.qtyKg)>0) || !String(tx.ref||'').trim())) {
      warnings.push(['TINGGI','HPP',tx.id,tx.date,tx.ref,'Pembelian DO belum memiliki referensi DO unik atau berat kg yang valid.']);
    }
    if (tx.type==='sale' && tx.status==='confirmed') {
      const batchRef = String(tx.batchRef||'').trim().toLowerCase();
      const batch = purchases.get(batchRef);
      if (!(number_(tx.qtyKg)>0)) {
        warnings.push(['TINGGI','HPP',tx.id,tx.date,tx.ref,'Penjualan tidak memiliki berat kg valid.']);
      }
      if (!batchRef || !batch) {
        warnings.push(['TINGGI','HPP',tx.id,tx.date,tx.batchRef,'Penjualan belum tertaut ke referensi DO pembelian terverifikasi.']);
      } else if (String(tx.date) < String(batch.date)) {
        warnings.push(['TINGGI','HPP',tx.id,tx.date,tx.batchRef,'Tanggal penjualan mendahului tanggal pembelian DO; periksa tanggal dan tautannya.']);
      }
    }
    if (['sale','purchase','expense'].indexOf(tx.type)>=0 &&
        (tx.paidAmount===null || tx.paidAmount===undefined || tx.paidAmount==='')) {
      warnings.push(['PERIKSA','PEMBAYARAN',tx.id,tx.date,tx.ref,'Nilai dibayar/diterima kosong. Sistem membaca kosong sebagai Rp0; isi jumlah aktual agar kas dan piutang/utang benar.']);
    } else if (['sale','purchase','expense'].indexOf(tx.type)>=0 &&
        (number_(tx.paidAmount)<0 || number_(tx.paidAmount)>number_(tx.amount))) {
      warnings.push(['TINGGI','PEMBAYARAN',tx.id,tx.date,tx.ref,'Pembayaran harus berada antara Rp0 dan nominal transaksi.']);
    }
  });

  const soldByRef = {};
  txs.filter(function(tx){return tx.type==='sale' && tx.status==='confirmed';}).forEach(function(s) {
    const key = String(s.batchRef||'').trim().toLowerCase();
    soldByRef[key] = (soldByRef[key]||0) + Math.max(number_(s.qtyKg),0);
  });
  purchases.forEach(function(batch,key) {
    const remaining = number_(batch.qtyKg) - (soldByRef[key]||0);
    if (remaining < -0.0001) {
      warnings.push(['TINGGI','STOK',batch.id,batch.date,batch.ref,'Kg penjualan melebihi total kg pembelian DO. Periksa tautan DO, susut, dan basis berat.']);
    }
  });
  if (warnings.length) {
    ['Level','Kategori','ID Transaksi','Tanggal','Referensi','Peringatan'].forEach(function(header,i) {
      val.getRange(2,valCols[header],warnings.length,1).setValues(warnings.map(function(row){return [row[i]];}));
    });
  }

  const sum = ss.getSheetByName('Ringkasan');
  const sumCols = columnMap_(sum);
  const oldSumRows = Math.max(sum.getLastRow()-1,0);
  if (oldSumRows > 0) sum.getRange(2,1,oldSumRows,sum.getLastColumn()).clearContent();
  const doRange = function(name) {
    const letter = columnLetter_(doCols[name]);
    return "'KartuDO'!$" + letter + "$2:$" + letter;
  };
  const valRange = function(name) {
    const letter = columnLetter_(valCols[name]);
    return "'Validasi'!$" + letter + "$2:$" + letter;
  };
  const summaryRows = [
    ['Saldo kas tercatat','=SUM(' + txRange('cashEffect') + ')','Berdasarkan pembayaran aktual yang tercatat pada transaksi terverifikasi; cocokkan dengan kas dan rekening bank.'],
    ['Omzet penjualan','=SUMIFS(' + txRange('amount') + ',' + txRange('type') + ',"sale",' + txRange('status') + ',"confirmed",' + txRange('deleted') + ',"<>TRUE")','Omzet penjualan terverifikasi; tidak sama dengan kas yang sudah diterima.'],
    ['HPP penjualan (basis kg sama)','=SUMPRODUCT(' + doRange('HPP per Kg (Rp)') + ',' + doRange('Kg Terjual') + ')','Valid hanya jika kg pembelian dan kg penjualan memakai basis yang sama. Jika beli ayam hidup dan jual karkas, catat rendemen/hasil karkas sebelum memakai angka ini sebagai HPP final.'],
    ['Biaya operasional','=SUMIFS(' + txRange('amount') + ',' + txRange('type') + ',"expense",' + txRange('status') + ',"confirmed",' + txRange('deleted') + ',"<>TRUE")','Biaya operasional yang dicatat; pastikan gaji/biaya potong tidak dihitung dua kali.'],
    ['Laba/rugi sementara','=B3-B4-B5','Omzet - HPP - biaya operasional. Angka tetap sementara bila masih ada peringatan HPP, basis berat, stok, atau tutup buku.'],
    ['Status laba','=IF(COUNTIF(' + valRange('Kategori') + ',"HPP")>0,"SEMENTARA — PERIKSA HPP",IF(COUNTIF(' + valRange('Kategori') + ',"STOK")>0,"SEMENTARA — PERIKSA STOK","PERIKSA TUTUP BUKU"))','Status otomatis untuk mencegah laba dianggap final saat HPP/stok belum lengkap.'],
    ['Stok tercatat (kg)','=SUM(' + doRange('Sisa Kg') + ')','Kg pembelian dikurangi kg penjualan tertaut, dengan asumsi basis berat sama dan susut dicatat.'],
    ['Nilai persediaan tersisa','=SUM(' + doRange('Nilai Stok (Rp)') + ')','Stok sisa × HPP per kg pada basis berat yang sama.'],
    ['Piutang agregat','=MAX(0,SUMIFS(' + txRange('amount') + ',' + txRange('type') + ',"sale",' + txRange('status') + ',"confirmed",' + txRange('deleted') + ',"<>TRUE")-SUMIFS(' + txRange('effectivePaid') + ',' + txRange('type') + ',"sale",' + txRange('status') + ',"confirmed",' + txRange('deleted') + ',"<>TRUE")-SUMIFS(' + txRange('amount') + ',' + txRange('type') + ',"receivable_payment",' + txRange('status') + ',"confirmed",' + txRange('deleted') + ',"<>TRUE"))','Estimasi agregat, belum rincian faktur pelanggan; tautkan pembayaran ke invoice untuk aging yang akurat.'],
    ['Utang DO agregat','=MAX(0,SUMIFS(' + txRange('amount') + ',' + txRange('type') + ',"purchase",' + txRange('status') + ',"confirmed",' + txRange('deleted') + ',"<>TRUE")-SUMIFS(' + txRange('effectivePaid') + ',' + txRange('type') + ',"purchase",' + txRange('status') + ',"confirmed",' + txRange('deleted') + ',"<>TRUE")-SUMIFS(' + txRange('amount') + ',' + txRange('type') + ',"supplier_payment",' + txRange('status') + ',"confirmed",' + txRange('deleted') + ',"<>TRUE"))','Estimasi agregat, belum rincian faktur supplier/DO; tautkan pembayaran ke tagihan yang benar.'],
    ['Jumlah transaksi draf','=COUNTIFS(' + txRange('status') + ',"draft",' + txRange('deleted') + ',"<>TRUE")','Selesaikan pemeriksaan dan konfirmasi sebelum tutup periode.'],
    ['Jumlah peringatan','=COUNTA(' + valRange('Level') + ')','Buka lembar Validasi untuk meninjau dan menyelesaikan temuan.']
  ];
  ['Indikator','Nilai','Keterangan'].forEach(function(header,i) {
    sum.getRange(2,sumCols[header],summaryRows.length,1).setValues(summaryRows.map(function(row){return [row[i]];}));
  });
  const valueLetter = columnLetter_(sumCols['Nilai']);
  summaryRows.forEach(function(r,i) {
    if (String(r[1]).charAt(0)==='=') {
      const formula = i===4
        ? '=' + valueLetter + '3-' + valueLetter + '4-' + valueLetter + '5'
        : r[1];
      sum.getRange(i+2,sumCols['Nilai']).setFormula(formula);
    }
  });
  sum.getRange(2,sumCols['Nilai'],5,1).setNumberFormat('"Rp" #,##0;[Red]-"Rp" #,##0');
  sum.getRange(8,sumCols['Nilai'],1,1).setNumberFormat('#,##0.###');
  sum.getRange(9,sumCols['Nilai'],3,1).setNumberFormat('"Rp" #,##0;[Red]-"Rp" #,##0');
  sum.getRange(2,1,summaryRows.length,3).setWrap(true);
  sum.setColumnWidth(sumCols['Indikator'],240);
  sum.setColumnWidth(sumCols['Nilai'],190);
  sum.setColumnWidth(sumCols['Keterangan'],520);
  val.setColumnWidth(valCols['Level'],95);
  val.setColumnWidth(valCols['Kategori'],115);
  val.setColumnWidth(valCols['Peringatan'],560);
  doSheet.setColumnWidth(doCols['Ref DO'],140);
  doSheet.setColumnWidth(doCols['Status Stok/HPP'],180);
}
function enableDailyAyamKasBackup() {
  ensureTables_();
  const exists = ScriptApp.getProjectTriggers().some(function(trigger) {
    return trigger.getHandlerFunction() === 'dailyAyamKasBackup';
  });
  if (!exists) {
    ScriptApp.newTrigger('dailyAyamKasBackup').timeBased().everyDays(1).atHour(2).create();
  }
  SpreadsheetApp.getUi().alert(
    'Backup otomatis harian aktif',
    'Apps Script akan membuat satu salinan spreadsheet per hari sekitar pukul 02.00 menurut zona waktu proyek. Cadangan JSON lokal Android tetap harus dibuat terpisah.',
    SpreadsheetApp.getUi().ButtonSet.OK
  );
}

function disableDailyAyamKasBackup() {
  ScriptApp.getProjectTriggers().forEach(function(trigger) {
    if (trigger.getHandlerFunction() === 'dailyAyamKasBackup') ScriptApp.deleteTrigger(trigger);
  });
  SpreadsheetApp.getUi().alert('Backup otomatis harian dinonaktifkan');
}

function dailyAyamKasBackup() {
  ensureTables_();
  const result = backup_();
  appendLog_('AUTO_BACKUP',result.fileId,'Apps Script','Backup harian otomatis: '+result.name);
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

function columnMap_(sheet) {
  const headers = sheet.getRange(1,1,1,sheet.getLastColumn()).getDisplayValues()[0].map(String);
  const result = {};
  headers.forEach(function(header,index){ result[header] = index + 1; });
  return result;
}
function columnLetter_(column) {
  let result = '';
  let n = Number(column);
  while (n > 0) {
    const remainder = (n - 1) % 26;
    result = String.fromCharCode(65 + remainder) + result;
    n = Math.floor((n - 1) / 26);
  }
  return result;
}
function rowToObject_(headers,row) {
  const o = {};
  headers.forEach(function(h,i) {
    if (row[i] instanceof Date && ['date','workDate','dueDate'].indexOf(h)>=0) {
      o[h] = Utilities.formatDate(row[i], Session.getScriptTimeZone(), 'yyyy-MM-dd');
    } else if (row[i] instanceof Date && h === 'period') {
      o[h] = Utilities.formatDate(row[i], Session.getScriptTimeZone(), 'yyyy-MM');
    } else {
      o[h] = row[i] instanceof Date ? row[i].toISOString() : row[i];
    }
  });
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