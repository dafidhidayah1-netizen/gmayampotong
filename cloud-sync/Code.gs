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
  KartuDO: ['Ref DO','Tanggal DO','Kg Pembelian','Penyusutan Kg','Kematian Kg','Pertambahan Berat Kg','Kg Layak Jual','Nilai DO (Rp)','Biaya Pakan (Rp)','Biaya Proses (Rp)','Total Biaya Batch (Rp)','HPP per Kg (Rp)','Kg Terjual','Sisa Kg','Nilai Stok (Rp)','Status Stok/HPP'],
  Validasi: ['Level','Kategori','ID Transaksi','Tanggal','Referensi','Peringatan'],
  LogSinkronisasi: ['Waktu','Aksi','ID Transaksi','Perangkat/Pengguna','Keterangan'],
  KasTransaksi: ['id','sourceTransactionId','date','accountName','direction','amount','paymentMethod','category','description','notes','status','updatedAt','deleted'],
  KasTransfer: ['id','date','fromAccount','toAccount','amount','reference','notes','status','updatedAt'],
  PinjamanLuar: ['id','date','party','loanReference','loanType','direction','amount','paidAmount','dueDate','cashAccount','notes','status','updatedAt'],
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
  if (e.range.getRow() < 2) return;
  ensureTables_();
  if (sh.getName() !== 'Transaksi') {
    handleAuxSheetEdit_(sh,e);
    return;
  }

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
      upsertCashMirrorFromTransaction_(sh,row,now);
    }
    applyCashFormulas_();
    refreshAyamKasReports();
  } finally {
    lock.releaseLock();
  }
}

function upsertCashMirrorFromTransaction_(txSheet,rowNumber,stamp) {
  const txCols=columnMap_(txSheet);
  const get=function(header){return txSheet.getRange(rowNumber,txCols[header]).getValue();};
  const id=String(get('id')||'').trim();
  if(!id) return;
  const type=String(get('type')||'');
  const status=String(get('status')||'').toLowerCase();
  const isDeleted=get('deleted')===true || String(get('deleted')).toLowerCase()==='true';
  const amount=Math.max(0,number_(get('amount')));
  const paidValue=get('paidAmount');
  const paid=paidValue===''||paidValue===null||paidValue===undefined?0:Math.max(0,number_(paidValue));
  let direction='', movement=0;
  switch(type) {
    case 'capital_in': direction='IN'; movement=amount; break;
    case 'sale': direction='IN'; movement=paid; break;
    case 'receivable_payment': direction='IN'; movement=amount; break;
    case 'purchase': case 'expense': direction='OUT'; movement=paid; break;
    case 'capital_out': case 'supplier_payment': direction='OUT'; movement=amount; break;
    default: direction=''; movement=0;
  }
  const cashSheet=SpreadsheetApp.getActiveSpreadsheet().getSheetByName('KasTransaksi');
  const cashCols=columnMap_(cashSheet);
  const cashId='CASH-'+id;
  const ids=cashSheet.getLastRow()>1?cashSheet.getRange(2,cashCols.id,cashSheet.getLastRow()-1,1).getDisplayValues().flat():[];
  const found=ids.indexOf(cashId);
  const exists=found>=0;
  const cashRecord={
    id:cashId,sourceTransactionId:id,date:String(get('date')||''),
    accountName:String(get('cashAccount')||'BELUM_DIPETAKAN'),
    direction:direction||'OUT',amount:movement,
    paymentMethod:String(get('paymentMethod')||'UNVERIFIED'),
    category:type,description:String(get('description')||''),notes:String(get('notes')||''),
    status:status==='confirmed'&&!isDeleted&&movement>0?'confirmed':'draft',
    updatedAt:stamp||String(get('updatedAt')||new Date().toISOString()),
    deleted:isDeleted||status!=='confirmed'||movement<=0||!direction
  };
  writeRecord_(cashSheet,Object.keys(cashCols).sort(function(a,b){return cashCols[a]-cashCols[b];}),cashRecord,exists?found+2:0);
}

function validateLoanRecordFromSheet_(sh,rowNumber) {
  const cols=columnMap_(sh);
  const get=function(header){return sh.getRange(rowNumber,cols[header]).getValue();};
  const loan={
    id:String(get('id')||''),date:String(get('date')||''),party:String(get('party')||''),
    loanReference:String(get('loanReference')||'').trim(),loanType:String(get('loanType')||''),
    direction:String(get('direction')||''),amount:number_(get('amount')),
    status:String(get('status')||'').toLowerCase()
  };
  if(loan.amount<=0) return 'Nominal harus lebih dari nol.';
  const debtTypes=['OWNER_LOAN','BUSINESS_BORROWING'];
  const allowed={
    OWNER_LOAN:['RECEIVED','REPAYMENT'],
    BUSINESS_BORROWING:['RECEIVED','REPAYMENT'],
    BUSINESS_LENDING:['GIVEN','COLLECTION'],
    OWNER_INJECTION:['RECEIVED'],
    OWNER_PERSONAL:['GIVEN'],
    UNEXPECTED_COST:['GIVEN']
  };
  if(!allowed[loan.loanType] || allowed[loan.loanType].indexOf(loan.direction)<0) return 'Kategori dan arah kas tidak sesuai.';
  if(debtTypes.concat(['BUSINESS_LENDING']).indexOf(loan.loanType)>=0 && !loan.loanReference) return 'Referensi pinjaman wajib diisi.';
  if(loan.direction==='REPAYMENT'||loan.direction==='COLLECTION') {
    if(!loan.loanReference) return 'Pelunasan/penagihan wajib memakai referensi pinjaman asal.';
    const loanSheet=SpreadsheetApp.getActiveSpreadsheet().getSheetByName('PinjamanLuar');
    const loanCols=columnMap_(loanSheet);
    const records=loanSheet.getLastRow()>1?loanSheet.getRange(2,1,loanSheet.getLastRow()-1,loanSheet.getLastColumn()).getValues().map(function(row){return rowToObject_(Object.keys(loanCols).sort(function(a,b){return loanCols[a]-loanCols[b];}),row);}):[];
    const sourceDirection=loan.direction==='REPAYMENT'?'RECEIVED':'GIVEN';
    const settleDirection=loan.direction;
    const linked=records.filter(function(record){
      return String(record.id)!==loan.id&&String(record.status).toLowerCase()==='confirmed'&&
        String(record.loanReference||'').trim().toLowerCase()===loan.loanReference.toLowerCase()&&
        String(record.loanType)===loan.loanType;
    });
    const principal=linked.filter(function(record){return String(record.direction)===sourceDirection;}).reduce(function(sum,record){return sum+number_(record.amount);},0);
    const settled=linked.filter(function(record){return String(record.direction)===settleDirection;}).reduce(function(sum,record){return sum+number_(record.amount);},0);
    const outstanding=Math.max(0,principal-settled);
    if(principal<=0) return 'Referensi pinjaman asal tidak ditemukan atau belum terverifikasi.';
    if(loan.amount>outstanding+0.0001) return 'Nominal melebihi saldo pinjaman tersisa Rp'+Math.round(outstanding).toLocaleString('id-ID')+'.';
  }
  return '';
}

function validateTransferRecordFromSheet_(sh,rowNumber) {
  const cols=columnMap_(sh);
  const from=String(sh.getRange(rowNumber,cols.fromAccount).getValue()||'').trim();
  const to=String(sh.getRange(rowNumber,cols.toAccount).getValue()||'').trim();
  const amount=number_(sh.getRange(rowNumber,cols.amount).getValue());
  if(!from||!to) return 'Akun asal dan tujuan wajib diisi.';
  if(from.toLowerCase()===to.toLowerCase()) return 'Akun asal dan tujuan harus berbeda.';
  if(amount<=0) return 'Nominal transfer harus lebih besar dari nol.';
  return '';
}

function validatePayrollRecordFromSheet_(sh,rowNumber) {
  const cols=columnMap_(sh);
  const total=number_(sh.getRange(rowNumber,cols.totalPay).getValue());
  const paid=number_(sh.getRange(rowNumber,cols.paidAmount).getValue());
  const present=number_(sh.getRange(rowNumber,cols.daysPresent).getValue());
  if(total<=0) return 'Total payroll harus lebih besar dari nol.';
  if(present<0) return 'Hari hadir tidak boleh negatif.';
  if(paid<0||paid>total) return 'Pembayaran payroll harus berada antara Rp0 dan total gaji.';
  return '';
}

function invalidatePayrollForEmployee_(employeeId,stamp) {
  if(!employeeId) return;
  const sh=SpreadsheetApp.getActiveSpreadsheet().getSheetByName('Payroll');
  const cols=columnMap_(sh);
  if(sh.getLastRow()<2) return;
  const rows=sh.getRange(2,1,sh.getLastRow()-1,sh.getLastColumn()).getValues();
  rows.forEach(function(row,index) {
    const record={}; Object.keys(cols).forEach(function(header){record[header]=row[cols[header]-1];});
    if(String(record.employeeId)===employeeId&&String(record.status).toUpperCase()!=='DRAFT') {
      sh.getRange(index+2,cols.status).setValue('DRAFT');
      sh.getRange(index+2,cols.updatedAt).setValue(stamp);
      syncPayrollExpenseFromSheet_(sh,index+2,stamp);
    }
  });
}

function handleAuxSheetEdit_(sh,e) {
  const profiles = {
    KasTransfer: {required:['date','fromAccount','toAccount','amount'],draftStatus:true},
    PinjamanLuar: {required:['date','party','loanType','direction','amount','cashAccount'],draftStatus:true},
    Karyawan: {required:['name','position','dailyRate'],draftStatus:false},
    Absensi: {required:['employeeId','employeeName','workDate','status'],draftStatus:false},
    Payroll: {required:['employeeId','employeeName','period','totalPay'],draftStatus:true}
  };
  const profile=profiles[sh.getName()];
  // KasTransaksi is a generated mirror; edit its source in Transaksi or PinjamanLuar instead.
  if (!profile) return;
  const cols=columnMap_(sh);
  const firstCol=e.range.getColumn(), lastCol=firstCol+e.range.getNumColumns()-1;
  const editedHeaders=Object.keys(cols).filter(function(header){
    return cols[header]>=firstCol && cols[header]<=lastCol;
  });
  if (!editedHeaders.some(function(header){return header!=='id' && header!=='updatedAt';})) return;
  const lock=LockService.getScriptLock();
  lock.waitLock(15000);
  try {
    const firstRow=e.range.getRow(), lastRow=firstRow+e.range.getNumRows()-1;
    const now=new Date().toISOString();
    let latestEditedRow=0;
    for(let row=firstRow;row<=lastRow;row++) {
      latestEditedRow=row;
      const values={};
      profile.required.forEach(function(header){values[header]=sh.getRange(row,cols[header]).getValue();});
      const ready=profile.required.every(function(header){
        const value=values[header];
        return value!=='' && value!==null && value!==undefined &&
          (header!=='amount' && header!=='dailyRate' && header!=='totalPay' || Number.isFinite(Number(value)) && Number(value)>0);
      });
      const idCell=sh.getRange(row,cols.id);
      if(!String(idCell.getDisplayValue()||'').trim()) {
        if(!ready) continue;
        idCell.setValue(Utilities.getUuid());
        if(profile.draftStatus && cols.status && !String(sh.getRange(row,cols.status).getValue()||'').trim()) {
          sh.getRange(row,cols.status).setValue(sh.getName()==='Payroll'?'DRAFT':'draft');
        }
        if(sh.getName()==='Karyawan') {
          const activeCell=sh.getRange(row,cols.active);
          const activeValue=activeCell.getValue();
          if(activeValue==='' || activeValue===null) activeCell.setValue(true);
        }
      }
      if(profile.draftStatus && cols.status && editedHeaders.some(function(header){return header!=='status';})) {
        sh.getRange(row,cols.status).setValue(sh.getName()==='Payroll'?'DRAFT':'draft');
      }
      sh.getRange(row,cols.updatedAt).setValue(now);
      if(!ready) continue;
      if(sh.getName()==='KasTransfer' && String(sh.getRange(row,cols.status).getValue()).toLowerCase()==='confirmed') {
        const error=validateTransferRecordFromSheet_(sh,row);
        if(error) {
          sh.getRange(row,cols.status).setValue('draft');
          appendLog_('VALIDATION_BLOCKED',String(sh.getRange(row,cols.id).getValue()),'Sheets',error);
        }
      }
      if(sh.getName()==='PinjamanLuar' && String(sh.getRange(row,cols.status).getValue()).toLowerCase()==='confirmed') {
        const error=validateLoanRecordFromSheet_(sh,row);
        if(error) {
          sh.getRange(row,cols.status).setValue('draft');
          appendLog_('VALIDATION_BLOCKED',String(sh.getRange(row,cols.id).getValue()),'Sheets',error);
        }
      }
      if(sh.getName()==='Payroll' && String(sh.getRange(row,cols.status).getValue()).toUpperCase()!=='DRAFT') {
        const error=validatePayrollRecordFromSheet_(sh,row);
        if(error) {
          sh.getRange(row,cols.status).setValue('DRAFT');
          appendLog_('VALIDATION_BLOCKED',String(sh.getRange(row,cols.id).getValue()),'Sheets',error);
        }
      }
      if(sh.getName()==='Karyawan') invalidatePayrollForEmployee_(String(sh.getRange(row,cols.id).getValue()||''),now);
      if(sh.getName()==='Payroll') syncPayrollExpenseFromSheet_(sh,row,now);
      if(sh.getName()==='Absensi') {
        invalidatePayrollForAttendance_(
          String(sh.getRange(row,cols.employeeId).getDisplayValue()||''),
          String(sh.getRange(row,cols.workDate).getDisplayValue()||''),
          now
        );
      }
    }
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
    if(!source) return;
    let linked=null, shouldDelete=false;
    if(source.indexOf('LOAN-')===0) {
      const loanSheet=ss.getSheetByName('PinjamanLuar');
      const loanHeaders=loanSheet.getRange(1,1,1,loanSheet.getLastColumn()).getDisplayValues()[0].map(String);
      const loanRows=loanSheet.getLastRow()>1 ? loanSheet.getRange(2,1,loanSheet.getLastRow()-1,loanHeaders.length).getValues() : [];
      const loanId=source.substring('LOAN-'.length);
      const loanRow=loanRows.map(function(row){return rowToObject_(loanHeaders,row);})
        .find(function(item){return String(item.id||'')===loanId;});
      if(!loanRow) return;
      linked=loanRow;
      shouldDelete=String(loanRow.status).toLowerCase()!=='confirmed';
    } else {
      linked=txById.get(source);
      if(!linked) return;
      shouldDelete=String(linked.deleted).toLowerCase()==='true' || String(linked.status).toLowerCase()!=='confirmed';
    }
    const currentlyDeleted=String(record.deleted).toLowerCase()==='true';
    if(shouldDelete!==currentlyDeleted) {
      cashSheet.getRange(index+2,cols.deleted).setValue(shouldDelete);
      cashSheet.getRange(index+2,cols.updatedAt).setValue(String(linked.updatedAt||new Date().toISOString()));
    }
  });
}

function syncPayrollExpenseFromSheet_(payrollSheet,rowNumber,stamp) {
  const pCols=columnMap_(payrollSheet);
  const payroll={
    id:String(payrollSheet.getRange(rowNumber,pCols.id).getDisplayValue()||''),
    employeeId:String(payrollSheet.getRange(rowNumber,pCols.employeeId).getDisplayValue()||''),
    employeeName:String(payrollSheet.getRange(rowNumber,pCols.employeeName).getDisplayValue()||''),
    period:String(payrollSheet.getRange(rowNumber,pCols.period).getDisplayValue()||''),
    totalPay:number_(payrollSheet.getRange(rowNumber,pCols.totalPay).getValue()),
    paidAmount:number_(payrollSheet.getRange(rowNumber,pCols.paidAmount).getValue()),
    paymentMethod:String(payrollSheet.getRange(rowNumber,pCols.paymentMethod).getDisplayValue()||'TRANSFER'),
    cashAccount:String(payrollSheet.getRange(rowNumber,pCols.cashAccount).getDisplayValue()||'ATM/BANK'),
    status:String(payrollSheet.getRange(rowNumber,pCols.status).getDisplayValue()||'DRAFT')
  };
  if(!payroll.id || !payroll.employeeId || !/^\d{4}-\d{2}$/.test(payroll.period) || payroll.totalPay<=0) return;
  const txSheet=SpreadsheetApp.getActiveSpreadsheet().getSheetByName('Transaksi');
  const txHeaders=txSheet.getRange(1,1,1,txSheet.getLastColumn()).getDisplayValues()[0].map(String);
  const txCols=columnMap_(txSheet);
  const id='PAYROLL-'+payroll.id;
  const ids=txSheet.getLastRow()>1 ? txSheet.getRange(2,txCols.id,txSheet.getLastRow()-1,1).getDisplayValues().flat() : [];
  const found=ids.indexOf(id);
  const row={
    id:id,updatedAt:stamp||new Date().toISOString(),date:payroll.period+'-01',
    ref:'PAY-'+payroll.period+'-'+payroll.employeeId,type:'expense',category:'Gaji',
    description:'Upah '+payroll.employeeName+' periode '+payroll.period,
    amount:payroll.totalPay,paidAmount:payroll.paidAmount,qtyKg:'',batchRef:'',
    notes:'Diposting dari lembar Payroll '+payroll.id,
    status:payroll.status==='DRAFT'?'draft':'confirmed',source:'sheets',
    deleted:false,deletedAt:'',paymentMethod:payroll.paymentMethod,cashAccount:payroll.cashAccount,
    supplier:'',weightLossKg:'',mortalityRatePct:'',fcr:'',weightGainKg:'',feedPricePerKg:'',processingCost:0,
    cashEffect:'',effectivePaid:''
  };
  writeRecord_(txSheet,txHeaders,row,found>=0?found+2:0);
  applyCashFormulas_();
  refreshAyamKasReports();
}

function invalidatePayrollForAttendance_(employeeId,workDate,stamp) {
  if(!employeeId || !/^\d{4}-\d{2}-\d{2}$/.test(workDate)) return;
  const period=workDate.substring(0,7);
  const payrollSheet=SpreadsheetApp.getActiveSpreadsheet().getSheetByName('Payroll');
  const headers=payrollSheet.getRange(1,1,1,payrollSheet.getLastColumn()).getDisplayValues()[0].map(String);
  const cols=columnMap_(payrollSheet);
  const rows=payrollSheet.getLastRow()>1?payrollSheet.getRange(2,1,payrollSheet.getLastRow()-1,headers.length).getValues():[];
  rows.forEach(function(row,index) {
    const record=rowToObject_(headers,row);
    if(String(record.employeeId)===employeeId && String(record.period)===period) {
      payrollSheet.getRange(index+2,cols.status).setValue('DRAFT');
      payrollSheet.getRange(index+2,cols.updatedAt).setValue(stamp);
      syncPayrollExpenseFromSheet_(payrollSheet,index+2,stamp);
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
  const refMap = new Map();
  txs.filter(function(tx){return tx.type==='purchase' && tx.status==='confirmed' && String(tx.ref||'').trim();})
    .forEach(function(tx) {
      const display = String(tx.ref).trim();
      if (!refMap.has(display.toLowerCase())) refMap.set(display.toLowerCase(),display);
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
    const formulaHeaders = [
      'Tanggal DO','Kg Pembelian','Penyusutan Kg','Kematian Kg','Pertambahan Berat Kg',
      'Kg Layak Jual','Nilai DO (Rp)','Biaya Pakan (Rp)','Biaya Proses (Rp)',
      'Total Biaya Batch (Rp)','HPP per Kg (Rp)','Kg Terjual','Sisa Kg','Nilai Stok (Rp)','Status Stok/HPP'
    ];
    formulaHeaders.forEach(function(header){formulasByHeader[header]=[];});
    refs.forEach(function(_,i) {
      const r=i+2;
      const doRef = '$' + letter('Ref DO') + r;
      const purchaseCriteria = txRange('ref') + ',' + doRef + ',' + txRange('type') + ',"purchase",' +
        txRange('status') + ',"confirmed",' + txRange('deleted') + ',"<>TRUE"';
      const saleCriteria = txRange('batchRef') + ',' + doRef + ',' + txRange('type') + ',"sale",' +
        txRange('status') + ',"confirmed",' + txRange('deleted') + ',"<>TRUE"';
      const purchaseWeight = txRange('qtyKg');
      const purchaseLoss = txRange('weightLossKg');
      const mortality = txRange('mortalityRatePct');
      const gain = txRange('weightGainKg');
      const fcr = txRange('fcr');
      const feedPrice = txRange('feedPricePerKg');
      const txType = txRange('type');
      const txStatus = txRange('status');
      const txDeleted = txRange('deleted');
      const txRef = txRange('ref');
      const multipurchase = '(' + txRef + '=' + doRef + ')*(' + txType + '="purchase")*(' +
        txStatus + '="confirmed")*(' + txDeleted + '<>TRUE)';
      const deathFormula = '=SUMPRODUCT(' + multipurchase + '*IFERROR(1*' + purchaseWeight + ',0)*IFERROR(1*' + mortality + ',0)/100)';
      const feedFormula = '=SUMPRODUCT(' + multipurchase + '*IFERROR(1*' + fcr + ',0)*IFERROR(1*' + gain + ',0)*IFERROR(1*' + feedPrice + ',0))';
      const dateFormula = '=IFERROR(INDEX(FILTER(' + txRange('date') + ',' +
        txRef + '=' + doRef + ',' + txType + '="purchase",' +
        txStatus + '="confirmed",' + txDeleted + '<>TRUE),1),"")';
      const kgFormula = '=SUMIFS(' + purchaseWeight + ',' + purchaseCriteria + ')';
      const lossFormula = '=SUMIFS(' + purchaseLoss + ',' + purchaseCriteria + ')';
      const gainFormula = '=SUMIFS(' + gain + ',' + purchaseCriteria + ')';
      const costFormula = '=SUMIFS(' + txRange('amount') + ',' + purchaseCriteria + ')';
      const processFormula = '=SUMIFS(' + txRange('processingCost') + ',' + purchaseCriteria + ')';
      const soldFormula = '=SUMIFS(' + txRange('qtyKg') + ',' + saleCriteria + ')';
      formulasByHeader['Tanggal DO'].push([dateFormula]);
      formulasByHeader['Kg Pembelian'].push([kgFormula]);
      formulasByHeader['Penyusutan Kg'].push([lossFormula]);
      formulasByHeader['Kematian Kg'].push([deathFormula]);
      formulasByHeader['Pertambahan Berat Kg'].push([gainFormula]);
      formulasByHeader['Kg Layak Jual'].push(['=' + letter('Kg Pembelian') + r + '+' + letter('Pertambahan Berat Kg') + r + '-' +
        letter('Penyusutan Kg') + r + '-' + letter('Kematian Kg') + r]);
      formulasByHeader['Nilai DO (Rp)'].push([costFormula]);
      formulasByHeader['Biaya Pakan (Rp)'].push([feedFormula]);
      formulasByHeader['Biaya Proses (Rp)'].push([processFormula]);
      formulasByHeader['Total Biaya Batch (Rp)'].push(['=' + letter('Nilai DO (Rp)') + r + '+' +
        letter('Biaya Pakan (Rp)') + r + '+' + letter('Biaya Proses (Rp)') + r]);
      formulasByHeader['HPP per Kg (Rp)'].push(['=IFERROR(' + letter('Total Biaya Batch (Rp)') + r + '/' + letter('Kg Layak Jual') + r + ',0)']);
      formulasByHeader['Kg Terjual'].push([soldFormula]);
      formulasByHeader['Sisa Kg'].push(['=' + letter('Kg Layak Jual') + r + '-' + letter('Kg Terjual') + r]);
      formulasByHeader['Nilai Stok (Rp)'].push(['=MAX(0,' + letter('Sisa Kg') + r + ')*' + letter('HPP per Kg (Rp)') + r]);
      formulasByHeader['Status Stok/HPP'].push(['=IF(OR(' + letter('Kg Layak Jual') + r + '<=0,' +
        letter('Total Biaya Batch (Rp)') + r + '<=0),"HPP BELUM LENGKAP",IF(' +
        letter('Sisa Kg') + r + '<0,"STOK NEGATIF",IF(COUNTIFS(' + txRef + ',' + doRef + ',' +
        txType + ',"purchase",' + txStatus + ',"confirmed",' + txDeleted + ',"<>TRUE",' +
        purchaseLoss + ',"")>0,"INPUT SUSUT BELUM LENGKAP",IF(COUNTIFS(' + txRef + ',' + doRef + ',' +
        txType + ',"purchase",' + txStatus + ',"confirmed",' + txDeleted + ',"<>TRUE",' +
        mortality + ',"")>0,"INPUT KEMATIAN BELUM LENGKAP","TERHITUNG"))))']);
    });
    Object.keys(formulasByHeader).forEach(function(header) {
      doSheet.getRange(2,doCols[header],refs.length,1).setFormulas(formulasByHeader[header]);
    });
    ['Nilai DO (Rp)','Biaya Pakan (Rp)','Biaya Proses (Rp)','Total Biaya Batch (Rp)','HPP per Kg (Rp)','Nilai Stok (Rp)'].forEach(function(header) {
      doSheet.getRange(2,doCols[header],refs.length,1).setNumberFormat('"Rp" #,##0;[Red]-"Rp" #,##0');
    });
    ['Kg Pembelian','Penyusutan Kg','Kematian Kg','Pertambahan Berat Kg','Kg Layak Jual','Kg Terjual','Sisa Kg'].forEach(function(header) {
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
    if (!purchases.has(key)) purchases.set(key,{id:p.id,date:p.date,ref:displayRef,qtyKg:0,lossKg:0,mortalityKg:0,gainKg:0,amount:0,feedCost:0,processCost:0,missingProduction:false});
    const batch = purchases.get(key);
    const rawKg = Math.max(number_(p.qtyKg),0);
    batch.qtyKg += rawKg;
    batch.lossKg += Math.max(number_(p.weightLossKg),0);
    batch.mortalityKg += rawKg * Math.min(100,Math.max(0,number_(p.mortalityRatePct))) / 100;
    batch.gainKg += Math.max(number_(p.weightGainKg),0);
    batch.amount += Math.max(number_(p.amount),0);
    batch.processCost += Math.max(number_(p.processingCost),0);
    if (p.fcr !== '' && p.fcr !== null && p.fcr !== undefined &&
        p.weightGainKg !== '' && p.weightGainKg !== null && p.weightGainKg !== undefined &&
        p.feedPricePerKg !== '' && p.feedPricePerKg !== null && p.feedPricePerKg !== undefined) {
      batch.feedCost += Math.max(number_(p.fcr),0)*Math.max(number_(p.weightGainKg),0)*Math.max(number_(p.feedPricePerKg),0);
    }
    if (p.weightLossKg === '' || p.weightLossKg === null || p.weightLossKg === undefined ||
        p.mortalityRatePct === '' || p.mortalityRatePct === null || p.mortalityRatePct === undefined) batch.missingProduction = true;
    const fcrState = [p.fcr,p.weightGainKg,p.feedPricePerKg].filter(function(v){return v!==''&&v!==null&&v!==undefined;}).length;
    if (fcrState!==0 && fcrState!==3) batch.missingProduction = true;
    if (!batch.date || String(p.date)<String(batch.date)) batch.date=p.date;
    if (number_(p.qtyKg)<=0 || number_(p.amount)<=0 || number_(p.weightLossKg)<0 ||
        number_(p.weightLossKg)>number_(p.qtyKg) || number_(p.mortalityRatePct)<0 ||
        number_(p.mortalityRatePct)>100 || !String(p.supplier||'').trim()) batch.missingProduction=true;
  });

  txs.forEach(function(tx) {
    if (tx.status !== 'confirmed') warnings.push(['PERIKSA','DRAF',tx.id,tx.date,tx.ref,'Transaksi masih draf; belum boleh masuk laporan final.']);
    if (tx.status === 'confirmed' && number_(tx.amount) <= 0) warnings.push(['TINGGI','NOMINAL',tx.id,tx.date,tx.ref,'Nominal transaksi terverifikasi harus lebih besar dari nol.']);
    if (tx.type==='purchase') {
      if (!(number_(tx.qtyKg)>0) || !String(tx.ref||'').trim()) {
        warnings.push(['TINGGI','HPP',tx.id,tx.date,tx.ref,'Pembelian DO belum memiliki referensi DO atau berat kg valid.']);
      }
      if (tx.status==='confirmed' && (tx.weightLossKg==='' || tx.weightLossKg===null || tx.weightLossKg===undefined)) {
        warnings.push(['TINGGI','HPP',tx.id,tx.date,tx.ref,'Input penyusutan belum dicatat. Catat kg nyata atau isi 0 jika tidak ada.']);
      }
      if (tx.status==='confirmed' && (tx.mortalityRatePct==='' || tx.mortalityRatePct===null || tx.mortalityRatePct===undefined)) {
        warnings.push(['TINGGI','HPP',tx.id,tx.date,tx.ref,'Input kematian belum dicatat. Catat persentase terukur atau isi 0 jika tidak ada.']);
      }
      const fcrCount=[tx.fcr,tx.weightGainKg,tx.feedPricePerKg].filter(function(v){return v!==''&&v!==null&&v!==undefined;}).length;
      if (tx.status==='confirmed' && fcrCount!==0 && fcrCount!==3) {
        warnings.push(['TINGGI','HPP',tx.id,tx.date,tx.ref,'FCR, pertambahan berat, dan harga pakan harus diisi lengkap atau dikosongkan seluruhnya.']);
      }
    }
    if (tx.type==='sale' && tx.status==='confirmed') {
      const batchRef=String(tx.batchRef||'').trim().toLowerCase();
      const batch=purchases.get(batchRef);
      if (!(number_(tx.qtyKg)>0)) warnings.push(['TINGGI','HPP',tx.id,tx.date,tx.ref,'Penjualan tidak memiliki berat kg valid.']);
      if (!batchRef || !batch) {
        warnings.push(['TINGGI','HPP',tx.id,tx.date,tx.batchRef,'Penjualan belum tertaut ke referensi DO pembelian terverifikasi.']);
      } else if (String(tx.date)<String(batch.date)) {
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
    if (tx.status==='confirmed' && tx.paymentMethod==='UNVERIFIED') {
      warnings.push(['PERIKSA','PEMBAYARAN',tx.id,tx.date,tx.ref,'Metode pembayaran historis belum dipetakan. Jangan menganggapnya sebagai tunai/transfer/QRIS.']);
    }
  });

  const soldByRef = {};
  txs.filter(function(tx){return tx.type==='sale' && tx.status==='confirmed';}).forEach(function(s) {
    const key=String(s.batchRef||'').trim().toLowerCase();
    soldByRef[key]=(soldByRef[key]||0)+Math.max(number_(s.qtyKg),0);
  });
  purchases.forEach(function(batch,key) {
    const saleable=batch.qtyKg+batch.gainKg-batch.lossKg-batch.mortalityKg;
    const remaining=saleable-(soldByRef[key]||0);
    if (remaining < -0.0001) warnings.push(['TINGGI','STOK',batch.id,batch.date,batch.ref,'Kg penjualan melebihi kg layak jual. Periksa tautan DO, susut, kematian, dan pertambahan berat.']);
    if (batch.missingProduction) warnings.push(['TINGGI','HPP',batch.id,batch.date,batch.ref,'HPP batch belum lengkap; lengkapi supplier, penyusutan, kematian, dan semua input FCR/pakan yang digunakan.']);
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
    const letter=columnLetter_(doCols[name]);
    return "'KartuDO'!$"+letter+"$2:$"+letter;
  };
  const valRange = function(name) {
    const letter=columnLetter_(valCols[name]);
    return "'Validasi'!$"+letter+"$2:$"+letter;
  };
  const cashSheet=ss.getSheetByName('KasTransaksi');
  const cashCols=columnMap_(cashSheet);
  const cashRange=function(name){const letter=columnLetter_(cashCols[name]);return "'KasTransaksi'!$"+letter+"$2:$"+letter;};
  const transferSheet=ss.getSheetByName('KasTransfer');
  const transferCols=columnMap_(transferSheet);
  const transferRange=function(name){const letter=columnLetter_(transferCols[name]);return "'KasTransfer'!$"+letter+"$2:$"+letter;};
  const summaryRows = [
    ['Saldo kas tercatat','=SUMIFS('+cashRange('amount')+','+cashRange('status')+',"confirmed",'+cashRange('deleted')+',FALSE,'+cashRange('direction')+',"IN")-SUMIFS('+cashRange('amount')+','+cashRange('status')+',"confirmed",'+cashRange('deleted')+',FALSE,'+cashRange('direction')+',"OUT")','Kas masuk/keluar dari buku kas terverifikasi termasuk pencatatan pinjaman; transfer antar akun tidak mengubah total kas. Rekonsiliasi ke kas dan rekening nyata tetap wajib.'],
    ['Omzet penjualan','=SUMIFS('+txRange('amount')+','+txRange('type')+',"sale",'+txRange('status')+',"confirmed",'+txRange('deleted')+',"<>TRUE")','Omzet penjualan terverifikasi; tidak sama dengan kas yang telah diterima.'],
    ['HPP penjualan (DO + susut + kematian + pakan + proses)','=SUMPRODUCT('+doRange('HPP per Kg (Rp)')+','+doRange('Kg Terjual')+')','HPP dihitung dari total biaya batch dibagi kg layak jual. Valid jika kg pembelian, kehilangan berat, kematian, pertambahan berat, dan kg terjual menggunakan basis yang konsisten.'],
    ['Biaya operasional','=SUMIFS('+txRange('amount')+','+txRange('type')+',"expense",'+txRange('status')+',"confirmed",'+txRange('deleted')+',"<>TRUE")','Biaya operasional yang dicatat. Jangan catat ulang biaya proses/pakan di sini bila sudah masuk dalam biaya HPP batch.'],
    ['Laba/rugi sementara','=B3-B4-B5','Omzet - HPP - biaya operasional. Angka tetap sementara bila masih ada peringatan HPP, basis berat, pembayaran, atau stok.'],
    ['Status laba','=IF(COUNTIF('+valRange('Kategori')+',"HPP")>0,"SEMENTARA — PERIKSA HPP",IF(COUNTIF('+valRange('Kategori')+',"STOK")>0,"SEMENTARA — PERIKSA STOK","PERIKSA TUTUP BUKU"))','Status otomatis. Selesaikan semua temuan sebelum laporan dianggap final.'],
    ['Sisa stok layak jual (kg)','=SUM('+doRange('Sisa Kg')+')','Kg pembelian + pertambahan berat − penyusutan − kematian − kg terjual tertaut.'],
    ['Nilai persediaan tersisa','=SUM('+doRange('Nilai Stok (Rp)')+')','Sisa kg × HPP per kg batch pada basis berat yang sama.'],
    ['Piutang agregat','=MAX(0,SUMIFS('+txRange('amount')+','+txRange('type')+',"sale",'+txRange('status')+',"confirmed",'+txRange('deleted')+',"<>TRUE")-SUMIFS('+txRange('effectivePaid')+','+txRange('type')+',"sale",'+txRange('status')+',"confirmed",'+txRange('deleted')+',"<>TRUE")-SUMIFS('+txRange('amount')+','+txRange('type')+',"receivable_payment",'+txRange('status')+',"confirmed",'+txRange('deleted')+',"<>TRUE"))','Estimasi agregat. Aging akurat per faktur memerlukan referensi invoice pada penerimaan piutang.'],
    ['Utang DO agregat','=MAX(0,SUMIFS('+txRange('amount')+','+txRange('type')+',"purchase",'+txRange('status')+',"confirmed",'+txRange('deleted')+',"<>TRUE")-SUMIFS('+txRange('effectivePaid')+','+txRange('type')+',"purchase",'+txRange('status')+',"confirmed",'+txRange('deleted')+',"<>TRUE")-SUMIFS('+txRange('amount')+','+txRange('type')+',"supplier_payment",'+txRange('status')+',"confirmed",'+txRange('deleted')+',"<>TRUE"))','Estimasi agregat. Aging akurat per faktur memerlukan referensi DO/tagihan pada pembayaran utang.'],
    ['Jumlah transaksi draf','=COUNTIFS('+txRange('status')+',"draft",'+txRange('deleted')+',"<>TRUE")','Selesaikan pemeriksaan dan konfirmasi sebelum tutup periode.'],
    ['Jumlah peringatan','=COUNTA('+valRange('Level')+')','Buka lembar Validasi untuk meninjau dan menyelesaikan temuan.'],
    ['Saldo Dompet','=SUMIFS('+cashRange('amount')+','+cashRange('accountName')+',"DOMPET",'+cashRange('status')+',"confirmed",'+cashRange('deleted')+',FALSE,'+cashRange('direction')+',"IN")-SUMIFS('+cashRange('amount')+','+cashRange('accountName')+',"DOMPET",'+cashRange('status')+',"confirmed",'+cashRange('deleted')+',FALSE,'+cashRange('direction')+',"OUT")+SUMIFS('+transferRange('amount')+','+transferRange('toAccount')+',"DOMPET",'+transferRange('status')+',"confirmed")-SUMIFS('+transferRange('amount')+','+transferRange('fromAccount')+',"DOMPET",'+transferRange('status')+',"confirmed")','Saldo kas tunai dompet setelah transfer antar akun tercatat. Cocokkan dengan uang fisik.'],
    ['Saldo ATM/Bank','=SUMIFS('+cashRange('amount')+','+cashRange('accountName')+',"ATM/BANK",'+cashRange('status')+',"confirmed",'+cashRange('deleted')+',FALSE,'+cashRange('direction')+',"IN")-SUMIFS('+cashRange('amount')+','+cashRange('accountName')+',"ATM/BANK",'+cashRange('status')+',"confirmed",'+cashRange('deleted')+',FALSE,'+cashRange('direction')+',"OUT")+SUMIFS('+transferRange('amount')+','+transferRange('toAccount')+',"ATM/BANK",'+transferRange('status')+',"confirmed")-SUMIFS('+transferRange('amount')+','+transferRange('fromAccount')+',"ATM/BANK",'+transferRange('status')+',"confirmed")','Saldo rekening setelah transfer tercatat. Cocokkan dengan rekening koran bank/ATM.'],
    ['Saldo QRIS','=SUMIFS('+cashRange('amount')+','+cashRange('accountName')+',"QRIS",'+cashRange('status')+',"confirmed",'+cashRange('deleted')+',FALSE,'+cashRange('direction')+',"IN")-SUMIFS('+cashRange('amount')+','+cashRange('accountName')+',"QRIS",'+cashRange('status')+',"confirmed",'+cashRange('deleted')+',FALSE,'+cashRange('direction')+',"OUT")+SUMIFS('+transferRange('amount')+','+transferRange('toAccount')+',"QRIS",'+transferRange('status')+',"confirmed")-SUMIFS('+transferRange('amount')+','+transferRange('fromAccount')+',"QRIS",'+transferRange('status')+',"confirmed")','Saldo akun/settlement QRIS yang dimasukkan. Cocokkan ke settlement aktual penyedia QRIS.'],
    ['Saldo akun belum dipetakan','=SUMIFS('+cashRange('amount')+','+cashRange('accountName')+',"BELUM_DIPETAKAN",'+cashRange('status')+',"confirmed",'+cashRange('deleted')+',FALSE,'+cashRange('direction')+',"IN")-SUMIFS('+cashRange('amount')+','+cashRange('accountName')+',"BELUM_DIPETAKAN",'+cashRange('status')+',"confirmed",'+cashRange('deleted')+',FALSE,'+cashRange('direction')+',"OUT")','Nilai historis yang belum dipetakan ke Dompet, ATM/Bank, atau QRIS; jangan dianggap cocok sebelum direkonsiliasi.'],
    ['Penerimaan penjualan — Tunai','=SUMIFS('+cashRange('amount')+','+cashRange('category')+',"sale",'+cashRange('paymentMethod')+',"CASH",'+cashRange('direction')+',"IN",'+cashRange('status')+',"confirmed",'+cashRange('deleted')+',FALSE)','Kas masuk dari penjualan yang memakai metode Tunai.'],
    ['Penerimaan penjualan — Transfer','=SUMIFS('+cashRange('amount')+','+cashRange('category')+',"sale",'+cashRange('paymentMethod')+',"TRANSFER",'+cashRange('direction')+',"IN",'+cashRange('status')+',"confirmed",'+cashRange('deleted')+',FALSE)','Kas masuk penjualan melalui transfer.'],
    ['Penerimaan penjualan — QRIS','=SUMIFS('+cashRange('amount')+','+cashRange('category')+',"sale",'+cashRange('paymentMethod')+',"QRIS",'+cashRange('direction')+',"IN",'+cashRange('status')+',"confirmed",'+cashRange('deleted')+',FALSE)','Kas masuk penjualan melalui QRIS.'],
    ['Penerimaan penjualan — Belum dipetakan','=SUMIFS('+cashRange('amount')+','+cashRange('category')+',"sale",'+cashRange('paymentMethod')+',"UNVERIFIED",'+cashRange('direction')+',"IN",'+cashRange('status')+',"confirmed",'+cashRange('deleted')+',FALSE)','Penerimaan penjualan yang metode aktualnya belum diverifikasi.'],
    ['Laba kotor','=B3-B4','Omzet dikurangi HPP batch yang ditautkan.'],
    ['Margin kotor (%)','=IFERROR((B3-B4)/B3*100,0)','Persentase margin kotor dari omzet; tampil sebagai persen.']
  ];
  ['Indikator','Nilai','Keterangan'].forEach(function(header,i) {
    sum.getRange(2,sumCols[header],summaryRows.length,1).setValues(summaryRows.map(function(row){return [row[i]];}));
  });
  const valueLetter = columnLetter_(sumCols['Nilai']);
  summaryRows.forEach(function(r,i) {
    if (String(r[1]).charAt(0)==='=') {
      let formula = r[1];
      if (i===4) formula = '=' + valueLetter + '3-' + valueLetter + '4-' + valueLetter + '5';
      if (i===summaryRows.length-1) formula = '=IFERROR((' + valueLetter + '3-' + valueLetter + '4)/' + valueLetter + '3*100,0)';
      sum.getRange(i+2,sumCols['Nilai']).setFormula(formula);
    }
  });
  summaryRows.forEach(function(r,i) {
    const label=String(r[0]);
    const row=i+2;
    if (/(kg)/i.test(label)) sum.getRange(row,sumCols['Nilai']).setNumberFormat('#,##0.###');
    else if (/Jumlah transaksi|Jumlah peringatan/.test(label)) sum.getRange(row,sumCols['Nilai']).setNumberFormat('#,##0');
    else if (/Margin kotor/.test(label)) sum.getRange(row,sumCols['Nilai']).setNumberFormat('0.00"%"');
    else if (!/Status laba/.test(label)) sum.getRange(row,sumCols['Nilai']).setNumberFormat('"Rp" #,##0;[Red]-"Rp" #,##0');
  });
  sum.getRange(2,1,summaryRows.length,3).setWrap(true);
  sum.setColumnWidth(sumCols['Indikator'],260);
  sum.setColumnWidth(sumCols['Nilai'],200);
  sum.setColumnWidth(sumCols['Keterangan'],560);
  val.setColumnWidth(valCols['Level'],95);
  val.setColumnWidth(valCols['Kategori'],115);
  val.setColumnWidth(valCols['Peringatan'],560);
  doSheet.setColumnWidth(doCols['Ref DO'],140);
  doSheet.setColumnWidth(doCols['Status Stok/HPP'],210);
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