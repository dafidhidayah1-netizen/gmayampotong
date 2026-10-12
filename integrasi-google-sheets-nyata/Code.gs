/**
 * AyamKas Pro — backend Google Sheets + Apps Script Web App.
 * No third-party API key is required. Access to write/read is protected by a script property token.
 * Create a token with configureApiToken() from the Apps Script editor before deploying.
 */
const APP_NAME = 'AyamKas Pro';
const API_TOKEN_PROPERTY = 'AYAMKAS_API_TOKEN';
const SHEETS = {
  AKP_Transaksi: [
    'id','createdAt','tanggal','jenis','kategori','keterangan','nominal','dibayar',
    'qtyKg','kgSiapJual','batchRef','catatan','status','statusPembayaran','metode',
    'pihak','referensi','sisa','updatedAt','deleted','source',
    'hppPerKg','hppJual','labaKotor','kgSisa','statusHpp'
  ],
  AKP_Karyawan: ['id','nama','jabatan','tarifHarian','tarifLemburPerJam','aktif','updatedAt','deleted'],
  AKP_Absensi: ['id','tanggal','karyawanId','namaKaryawan','status','jamMasuk','jamPulang','lemburJam','catatan','updatedAt','deleted'],
  AKP_Penggajian: ['id','periode','karyawanId','namaKaryawan','hariHadir','lemburJam','tarifHarian','tarifLemburPerJam','gajiPokok','uangLembur','bonus','potongan','total','statusPersetujuan','statusBayar','ledgerTransactionId','updatedAt','deleted'],
  AKP_AuditLog: ['waktu','user','aksi','sheet','recordId','detail'],
  AKP_Ringkasan: ['indikator','nilai','rumus_dan_penjelasan','diperbarui']
};

const TRANSACTION_TYPES = [
  'capital_in','purchase','sale','expense','capital_out','supplier_payment','receivable_payment'
];

function doGet(e) {
  if (e && e.parameter && e.parameter.health === '1') {
    return jsonOutput_({ok:true,app:APP_NAME,version:2,mode:'health'});
  }
  return HtmlService.createTemplateFromFile('Index').evaluate()
    .setTitle(APP_NAME + ' · Integrasi Cloud')
    .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL);
}

/** One-time setup from Apps Script editor. Run as spreadsheet owner and copy the returned token. */
function configureApiToken() {
  const props = PropertiesService.getScriptProperties();
  let token = props.getProperty(API_TOKEN_PROPERTY);
  if (!token) {
    token = Utilities.getUuid().replace(/-/g,'') + Utilities.getUuid().replace(/-/g,'');
    props.setProperty(API_TOKEN_PROPERTY, token);
  }
  Logger.log('AYAMKAS_API_TOKEN=' + token);
  return 'Token siap. Salin nilai AYAMKAS_API_TOKEN dari Execution log, lalu simpan pada perangkat yang diizinkan.';
}

function doPost(e) {
  try {
    const request = JSON.parse((e && e.postData && e.postData.contents) || '{}');
    assertApiToken_(request.token || request.apiKey);
    const payload = request.payload || request.data || request;
    const result = dispatch_(String(request.action || ''), payload);
    return jsonOutput_(Object.assign({ok:true}, result || {}));
  } catch (err) {
    return jsonOutput_({ok:false,error:String(err && err.message ? err.message : err)});
  }
}

/** For the HTML client. The token is intentionally never embedded in the published page. */
function api(action, payload, token) {
  assertApiToken_(token);
  return dispatch_(String(action || ''), payload || {});
}

function dispatch_(action, payload) {
  if (action === 'ping') {
    const ss = SpreadsheetApp.getActiveSpreadsheet();
    return {app:APP_NAME,version:2,spreadsheetId:ss.getId(),serverTime:new Date().toISOString()};
  }
  setup_();
  switch (action) {
    case 'init':
      recalculate_();
      return {
        app:APP_NAME,
        spreadsheetId:SpreadsheetApp.getActiveSpreadsheet().getId(),
        sheets:Object.keys(SHEETS),
        transactions:list_('AKP_Transaksi'),
        summary:list_('AKP_Ringkasan'),
        employees:list_('AKP_Karyawan'),
        attendance:list_('AKP_Absensi'),
        payroll:list_('AKP_Penggajian')
      };
    case 'saveTransaction':
      saveTransaction_(payload);
      recalculate_();
      return {ok:true,transactions:list_('AKP_Transaksi')};
    case 'deleteTransaction':
      softDelete_('AKP_Transaksi', payload.id);
      recalculate_();
      return {ok:true};
    case 'saveEmployee':
      saveRecord_('AKP_Karyawan', payload, 'id');
      return {ok:true};
    case 'saveAttendance':
      saveRecord_('AKP_Absensi', payload, 'id');
      recalculatePayrollForAttendance_(String(payload.karyawanId || ''), String(payload.tanggal || '').slice(0,7));
      return {ok:true};
    case 'savePayroll':
      saveRecord_('AKP_Penggajian', payload, 'id');
      recalculate_();
      return {ok:true};
    case 'backup':
      return backup_();
    case 'sync':
      return syncTransactions_(payload);
    default:
      throw new Error('Aksi tidak dikenal: ' + action);
  }
}

function assertApiToken_(provided) {
  const expected = PropertiesService.getScriptProperties().getProperty(API_TOKEN_PROPERTY);
  const actual = String(provided || '');
  if (!expected) throw new Error('Token belum dibuat. Jalankan configureApiToken() di editor Apps Script.');
  if (!actual || actual.length !== expected.length) throw new Error('Token tidak valid atau belum diisi.');
  let diff = 0;
  for (let i = 0; i < expected.length; i++) diff |= expected.charCodeAt(i) ^ actual.charCodeAt(i);
  if (diff !== 0) throw new Error('Token tidak valid atau belum diisi.');
}

function jsonOutput_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

/**
 * Creates missing tabs/headers only. Existing columns and values are preserved.
 * Do not replace this with a destructive reset.
 */
function setup_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  Object.keys(SHEETS).forEach(name => {
    let sh = ss.getSheetByName(name);
    if (!sh) sh = ss.insertSheet(name);
    if (sh.getLastRow() === 0 || sh.getLastColumn() === 0) {
      sh.getRange(1,1,1,SHEETS[name].length).setValues([SHEETS[name]]);
      sh.setFrozenRows(1);
    } else {
      const current = sh.getRange(1,1,1,sh.getLastColumn()).getDisplayValues()[0].map(String);
      const missing = SHEETS[name].filter(h => !current.includes(h));
      if (missing.length) sh.getRange(1,sh.getLastColumn()+1,1,missing.length).setValues([missing]);
    }
    const lastCol = sh.getLastColumn();
    sh.getRange(1,1,1,lastCol).setFontWeight('bold').setBackground('#123c2c').setFontColor('#ffffff');
    sh.setFrozenRows(1);
  });
  return {ok:true,spreadsheetId:ss.getId(),sheets:Object.keys(SHEETS)};
}

function headers_(sh) {
  if (sh.getLastColumn() < 1) throw new Error('Header sheet kosong: ' + sh.getName());
  return sh.getRange(1,1,1,sh.getLastColumn()).getDisplayValues()[0].map(String);
}

function list_(sheetName, includeDeleted) {
  const sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(sheetName);
  if (!sh || sh.getLastRow() < 2) return [];
  const headers = headers_(sh);
  const rows = sh.getRange(2,1,sh.getLastRow()-1,headers.length).getValues();
  return rows.map(row => {
    const obj = {};
    headers.forEach((h,i) => { obj[h] = normalizeCell_(row[i]); });
    return obj;
  }).filter(obj => includeDeleted || String(obj.deleted) !== 'true');
}

function normalizeCell_(value) {
  if (value instanceof Date) return value.toISOString();
  return value;
}

function normalizeTransaction_(input) {
  const o = input || {};
  const type = String(o.type !== undefined ? o.type : o.jenis || '').trim().toLowerCase();
  const amountValue = o.amount !== undefined ? o.amount : o.nominal;
  const paidRaw = o.paidAmount !== undefined ? o.paidAmount : o.dibayar;
  const qtyRaw = o.qtyKg !== undefined ? o.qtyKg : o.quantityKg;
  const saleableRaw = o.saleableKg !== undefined ? o.saleableKg : o.kgSiapJual;
  const amount = Number(amountValue);
  const paidAmount = paidRaw === '' || paidRaw === null || paidRaw === undefined ? null : Number(paidRaw);
  const qtyKg = qtyRaw === '' || qtyRaw === null || qtyRaw === undefined ? null : Number(qtyRaw);
  const saleableKg = saleableRaw === '' || saleableRaw === null || saleableRaw === undefined ? null : Number(saleableRaw);
  const statusRaw = String(o.status || 'draft').toLowerCase();
  const status = statusRaw === 'confirmed' ? 'confirmed' : 'draft';
  if (!o.date && !o.tanggal) throw new Error('Tanggal transaksi wajib diisi.');
  if (!TRANSACTION_TYPES.includes(type)) throw new Error('Jenis transaksi tidak dikenal: ' + type);
  if (!Number.isFinite(amount) || amount <= 0) throw new Error('Nominal harus lebih besar dari nol.');
  if (paidAmount !== null && (!Number.isFinite(paidAmount) || paidAmount < 0)) throw new Error('Pembayaran tidak boleh negatif.');
  if (paidAmount !== null && ['sale','purchase','expense'].includes(type) && paidAmount > amount) {
    throw new Error('Pembayaran sekarang tidak boleh melebihi nominal transaksi.');
  }
  if (qtyKg !== null && (!Number.isFinite(qtyKg) || qtyKg < 0)) throw new Error('Berat kg tidak valid.');
  if (saleableKg !== null && (!Number.isFinite(saleableKg) || saleableKg < 0)) throw new Error('Kg siap jual tidak valid.');
  if (type === 'purchase' && saleableKg !== null && qtyKg !== null && saleableKg > qtyKg + 0.0001) {
    throw new Error('Kg siap jual melebihi kg diterima. Periksa berat masuk dan hasil proses.');
  }
  return {
    id:String(o.id || Utilities.getUuid()),
    createdAt:String(o.createdAt || new Date().toISOString()),
    date:String(o.date || o.tanggal),
    type:type,
    category:String(o.category !== undefined ? o.category : o.kategori || 'Umum'),
    description:String(o.description !== undefined ? o.description : o.keterangan || ''),
    amount:amount,
    paidAmount:paidAmount,
    qtyKg:qtyKg,
    saleableKg:saleableKg,
    batchRef:String(o.batchRef || ''),
    notes:String(o.notes !== undefined ? o.notes : o.catatan || ''),
    status:status,
    paymentMethod:String(o.paymentMethod !== undefined ? o.paymentMethod : o.metode || ''),
    party:String(o.party !== undefined ? o.party : o.pihak || ''),
    ref:String(o.ref !== undefined ? o.ref : o.referensi || ''),
    updatedAt:String(o.updatedAt || new Date().toISOString()),
    deleted:false,
    source:String(o.source || 'cloud'),
    cashBalance:0,
    paymentStatus:''
  };
}

function toSheetRow_(model, headers) {
  const data = {
    id:model.id, createdAt:model.createdAt, tanggal:model.date, jenis:model.type,
    kategori:model.category, keterangan:model.description, nominal:model.amount,
    dibayar:model.paidAmount === null ? '' : model.paidAmount,
    qtyKg:model.qtyKg === null ? '' : model.qtyKg,
    kgSiapJual:model.saleableKg === null ? '' : model.saleableKg,
    batchRef:model.batchRef, catatan:model.notes, status:model.status,
    statusPembayaran:model.paymentStatus || '', metode:model.paymentMethod,
    pihak:model.party, referensi:model.ref, sisa:model.cashBalance,
    updatedAt:model.updatedAt, deleted:!!model.deleted, source:model.source
  };
  return headers.map(h => data[h] === undefined ? '' : data[h]);
}

function sheetToModel_(rowObj) {
  const paid = rowObj.dibayar === '' || rowObj.dibayar === null || rowObj.dibayar === undefined ? null : Number(rowObj.dibayar);
  const qty = rowObj.qtyKg === '' || rowObj.qtyKg === null || rowObj.qtyKg === undefined ? null : Number(rowObj.qtyKg);
  const saleable = rowObj.kgSiapJual === '' || rowObj.kgSiapJual === null || rowObj.kgSiapJual === undefined ? null : Number(rowObj.kgSiapJual);
  return {
    id:String(rowObj.id || ''),
    createdAt:String(rowObj.createdAt || ''),
    date:String(rowObj.tanggal || ''),
    type:String(rowObj.jenis || ''),
    category:String(rowObj.kategori || 'Umum'),
    description:String(rowObj.keterangan || ''),
    amount:Number(rowObj.nominal || 0),
    paidAmount:paid,
    qtyKg:qty,
    saleableKg:saleable,
    batchRef:String(rowObj.batchRef || ''),
    notes:String(rowObj.catatan || ''),
    status:String(rowObj.status || 'draft'),
    paymentMethod:String(rowObj.metode || ''),
    party:String(rowObj.pihak || ''),
    ref:String(rowObj.referensi || ''),
    updatedAt:String(rowObj.updatedAt || ''),
    deleted:String(rowObj.deleted) === 'true' || rowObj.deleted === true,
    source:String(rowObj.source || 'cloud')
  };
}

function upsertModel_(model) {
  const lock = LockService.getScriptLock();
  lock.waitLock(15000);
  try {
    const sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName('AKP_Transaksi');
    const headers = headers_(sh);
    const all = list_('AKP_Transaksi', true);
    const index = all.findIndex(row => String(row.id) === model.id);
    const targetRow = index >= 0 ? index + 2 : 0;
    const existing = index >= 0 ? sheetToModel_(all[index]) : null;
    const nowMs = Date.parse(model.updatedAt || '') || 0;
    const existingMs = existing ? (Date.parse(existing.updatedAt || '') || 0) : 0;
    if (existing && existingMs > nowMs) return {written:false,conflict:true};
    const statusPayment = ['sale','purchase','expense'].includes(model.type)
      ? ((model.paidAmount || 0) >= model.amount ? 'LUNAS' : ((model.paidAmount || 0) > 0 ? 'SEBAGIAN' : 'BELUM BAYAR'))
      : '—';
    const withCalculatedPayment = Object.assign({},model,{
      paymentStatus:statusPayment,
      cashBalance:['sale','purchase'].includes(model.type) ? Math.max(0,model.amount-(model.paidAmount || 0)) : 0,
      updatedAt:model.updatedAt || new Date().toISOString()
    });
    const row = toSheetRow_(withCalculatedPayment,headers);
    if (targetRow) sh.getRange(targetRow,1,1,headers.length).setValues([row]);
    else sh.appendRow(row);
    return {written:true,conflict:false};
  } finally {
    lock.releaseLock();
  }
}
function saveTransaction_(payload) {
  const model = normalizeTransaction_(payload);
  const result = upsertModel_(model);
  if (result.conflict) throw new Error('Data yang lebih baru sudah ada di Google Sheets. Muat ulang sebelum mengedit.');
  audit_('SAVE','AKP_Transaksi',model.id,JSON.stringify({type:model.type,amount:model.amount,status:model.status}));
  return {id:model.id};
}

function saveRecord_(sheetName, input, idField) {
  const obj = Object.assign({},input || {});
  const lock = LockService.getScriptLock();
  let saved;
  lock.waitLock(15000);
  try {
    const sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(sheetName);
    const headers = headers_(sh);
    const data = list_(sheetName,true);
    let id = String(obj[idField] || '');
    // One attendance row per employee/day; one payroll row per employee/month.
    if (!id && sheetName === 'AKP_Absensi') {
      const match = data.find(row => String(row.karyawanId) === String(obj.karyawanId || '') &&
        String(row.tanggal) === String(obj.tanggal || '') && String(row.deleted) !== 'true');
      if (match) id = String(match.id);
    }
    if (!id && sheetName === 'AKP_Penggajian') {
      const match = data.find(row => String(row.karyawanId) === String(obj.karyawanId || '') &&
        String(row.periode) === String(obj.periode || '') && String(row.deleted) !== 'true');
      if (match) id = String(match.id);
    }
    id = id || Utilities.getUuid();
    obj[idField] = id;
    obj.id = id;
    const previousRecord = data.find(row => String(row.id) === id) || null;
    obj.updatedAt = new Date().toISOString();
    obj.deleted = false;

    if (sheetName === 'AKP_Karyawan') {
      if (!String(obj.nama || '').trim()) throw new Error('Nama karyawan wajib diisi.');
      if (Number(obj.tarifHarian || 0) < 0 || Number(obj.tarifLemburPerJam || 0) < 0) {
        throw new Error('Tarif tidak boleh negatif.');
      }
      obj.tarifHarian = Number(obj.tarifHarian || 0);
      obj.tarifLemburPerJam = Number(obj.tarifLemburPerJam || 0);
      obj.aktif = obj.aktif !== false && String(obj.aktif).toLowerCase() !== 'false';
    }

    if (sheetName === 'AKP_Absensi') {
      if (!obj.tanggal || !obj.karyawanId) throw new Error('Tanggal dan karyawan wajib dipilih.');
      const employee = list_('AKP_Karyawan').find(row => String(row.id) === String(obj.karyawanId));
      if (!employee) throw new Error('Karyawan tidak ditemukan atau sudah diarsipkan.');
      const statuses = ['HADIR','IZIN','SAKIT','ALPHA','LIBUR'];
      obj.status = String(obj.status || 'HADIR').toUpperCase();
      if (!statuses.includes(obj.status)) throw new Error('Status absensi tidak valid.');
      obj.namaKaryawan = employee.nama;
      obj.lemburJam = Math.max(0,Number(obj.lemburJam || 0));
      if (obj.lemburJam > 24) throw new Error('Jam lembur tidak boleh lebih dari 24 jam sehari.');
      if (obj.status !== 'HADIR') obj.lemburJam = 0;
    }

    if (sheetName === 'AKP_Penggajian') {
      obj.periode = String(obj.periode || '');
      if (!/^\d{4}-\d{2}$/.test(obj.periode)) throw new Error('Periode penggajian harus YYYY-MM.');
      const employee = list_('AKP_Karyawan').find(row => String(row.id) === String(obj.karyawanId));
      if (!employee) throw new Error('Pilih karyawan yang terdaftar.');
      obj.namaKaryawan = employee.nama;
      obj.tarifHarian = previousRecord && previousRecord.tarifHarian !== '' && previousRecord.tarifHarian !== null
        ? Math.max(0,Number(previousRecord.tarifHarian))
        : Math.max(0,Number(employee.tarifHarian || 0));
      obj.tarifLemburPerJam = previousRecord && previousRecord.tarifLemburPerJam !== '' && previousRecord.tarifLemburPerJam !== null
        ? Math.max(0,Number(previousRecord.tarifLemburPerJam))
        : Math.max(0,Number(employee.tarifLemburPerJam || 0));

      const attendance = list_('AKP_Absensi').filter(row =>
        String(row.karyawanId) === String(obj.karyawanId) &&
        String(row.tanggal || '').slice(0,7) === obj.periode
      );
      // Deduplicate by date in case the old ledger already contains duplicate attendance rows.
      const byDate = {};
      attendance.forEach(row => {
        const date = String(row.tanggal || '');
        if (!date) return;
        if (!byDate[date] || String(row.updatedAt || '') > String(byDate[date].updatedAt || '')) byDate[date] = row;
      });
      const approvedDays = Object.values(byDate).filter(row => String(row.status).toUpperCase() === 'HADIR');
      obj.hariHadir = approvedDays.length;
      obj.lemburJam = approvedDays.reduce((sum,row)=>sum+Math.max(0,Number(row.lemburJam || 0)),0);
      obj.gajiPokok = obj.hariHadir * obj.tarifHarian;
      obj.uangLembur = obj.lemburJam * obj.tarifLemburPerJam;
      obj.bonus = Number(obj.bonus || 0);
      obj.potongan = Number(obj.potongan || 0);
      if (!Number.isFinite(obj.bonus) || !Number.isFinite(obj.potongan) || obj.bonus < 0 || obj.potongan < 0) {
        throw new Error('Bonus dan potongan harus berupa angka tidak negatif.');
      }
      obj.total = obj.gajiPokok + obj.uangLembur + obj.bonus - obj.potongan;
      if (obj.total < 0) throw new Error('Total gaji tidak boleh negatif.');
      obj.statusPersetujuan = String(obj.statusPersetujuan || 'DRAF').toUpperCase() === 'DISETUJUI' ? 'DISETUJUI' : 'DRAF';
      obj.statusBayar = String(obj.statusBayar || 'BELUM DIBAYAR').toUpperCase() === 'DIBAYAR' ? 'DIBAYAR' : 'BELUM DIBAYAR';
      if (obj.statusBayar === 'DIBAYAR' && obj.statusPersetujuan !== 'DISETUJUI') {
        throw new Error('Gaji harus disetujui sebelum ditandai sudah dibayar.');
      }
      obj.ledgerTransactionId = 'PAYROLL-' + id;
    }

    const index = data.findIndex(row => String(row.id) === id);
    const targetRow = index >= 0 ? index + 2 : 0;
    const row = headers.map(header => obj[header] === undefined ? '' : obj[header]);
    if (targetRow) sh.getRange(targetRow,1,1,headers.length).setValues([row]);
    else sh.appendRow(row);
    audit_('SAVE',sheetName,id,JSON.stringify({record:id}));
    saved = {ok:true,id:id,record:obj};
  } finally {
    lock.releaseLock();
  }

  // The generated expense is upserted only after the main payroll lock is released.
  if (sheetName === 'AKP_Penggajian') syncPayrollExpense_(saved.record);
  return {ok:true,id:saved.id};
}

function syncPayrollExpense_(payroll) {
  const transactionId = String(payroll.ledgerTransactionId || ('PAYROLL-' + payroll.id));
  const amount = Number(payroll.total || 0);
  const existing = list_('AKP_Transaksi',true).find(row => String(row.id) === transactionId);
  if (amount <= 0) {
    if (existing && String(existing.deleted) !== 'true') softDelete_('AKP_Transaksi',transactionId);
    return;
  }
  const approved = String(payroll.statusPersetujuan || '').toUpperCase() === 'DISETUJUI';
  const paid = String(payroll.statusBayar || '').toUpperCase() === 'DIBAYAR';
  const model = normalizeTransaction_({
    id:transactionId,
    date:String(payroll.periode) + '-01',
    type:'expense',
    category:'Gaji',
    ref:'PAY-' + String(payroll.id),
    description:'Gaji ' + String(payroll.namaKaryawan || 'Karyawan') + ' periode ' + String(payroll.periode),
    amount:amount,
    paidAmount:paid ? amount : 0,
    party:String(payroll.namaKaryawan || ''),
    notes:'Dihasilkan dari Penggajian ID ' + String(payroll.id) + '; persetujuan=' + (approved ? 'DISETUJUI' : 'DRAF'),
    status:approved ? 'confirmed' : 'draft',
    source:'payroll',
    updatedAt:String(payroll.updatedAt || new Date().toISOString())
  });
  const result = upsertModel_(model);
  if (result.conflict) throw new Error('Transaksi gaji pada ledger lebih baru. Muat ulang data payroll sebelum mengubah.');
  audit_('SYNC_PAYROLL','AKP_Transaksi',transactionId,'Pengeluaran gaji tersinkron dari penggajian '+payroll.id);
}
function recalculatePayrollForAttendance_(employeeId, period) {
  if (!employeeId || !/^\d{4}-\d{2}$/.test(period)) return;
  const payrolls = list_('AKP_Penggajian').filter(row =>
    String(row.karyawanId) === employeeId && String(row.periode) === period
  );
  let changed = false;
  payrolls.forEach(row => {
    if (String(row.statusBayar || '').toUpperCase() === 'DIBAYAR') {
      audit_('ATTENDANCE_CHANGE_AFTER_PAY','AKP_Penggajian',String(row.id),
        'Absensi berubah setelah payroll dibayar; rekap dan kas tidak diubah otomatis. Buat penyesuaian terpisah jika perlu.');
      return;
    }
    saveRecord_('AKP_Penggajian',row,'id');
    changed = true;
  });
  if (changed) recalculate_();
}

function syncTransactions_(payload) {
  setup_();
  const incoming = Array.isArray(payload.transactions) ? payload.transactions : [];
  const incomingDeleted = Array.isArray(payload.deletedTransactions) ? payload.deletedTransactions : [];
  const deletedIds = Array.from(new Set(
    (Array.isArray(payload.deletedIds) ? payload.deletedIds.map(String) : [])
      .concat(incomingDeleted.map(x => String((x && (x.originalId || x.id)) || '')).filter(Boolean))
  ));
  const conflicts = [];
  const errors = [];
  let accepted = 0;
  incoming.forEach(raw => {
    try {
      const model = normalizeTransaction_(raw);
      const result = upsertModel_(model);
      if (result.conflict) conflicts.push(model.id);
      else if (result.written) accepted++;
    } catch (err) {
      errors.push({id:String(raw && raw.id || ''),error:String(err.message || err)});
    }
  });
  if (deletedIds.length) {
    const lock = LockService.getScriptLock();
    lock.waitLock(15000);
    try {
      const sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName('AKP_Transaksi');
      const headers = headers_(sh);
      const rows = list_('AKP_Transaksi',true);
      const deletedCol = headers.indexOf('deleted')+1;
      const updatedCol = headers.indexOf('updatedAt')+1;
      deletedIds.forEach(id => {
        const index = rows.findIndex(row => String(row.id) === id);
        if (index >= 0 && String(rows[index].deleted) !== 'true') {
          const row = index+2;
          sh.getRange(row,deletedCol).setValue(true);
          sh.getRange(row,updatedCol).setValue(new Date().toISOString());
          audit_('SOFT_DELETE','AKP_Transaksi',id,'Dihapus dari perangkat dan diarsipkan di cloud');
        }
      });
    } finally {
      lock.releaseLock();
    }
  }
  recalculate_();
  const all = readTransactionModels_(true);
  const active = all.filter(row => row.deleted !== true);
  const deleted = all.filter(row => row.deleted === true).map(row => Object.assign({},row,{
    originalId:row.id,
    deletedAt:row.updatedAt
  }));
  return {
    app:APP_NAME,
    spreadsheetId:SpreadsheetApp.getActiveSpreadsheet().getId(),
    serverTime:new Date().toISOString(),
    accepted:accepted,
    transactions:active,
    deletedTransactions:deleted,
    conflicts:conflicts,
    rejected:errors
  };
}

function readTransactionModels_(includeDeleted) {
  return list_('AKP_Transaksi', !!includeDeleted).map(row => {
    const model = sheetToModel_(row);
    model.hppPerKg = Number(row.hppPerKg || 0);
    model.hppJual = Number(row.hppJual || 0);
    model.labaKotor = Number(row.labaKotor || 0);
    model.kgSisa = Number(row.kgSisa || 0);
    model.statusHpp = String(row.statusHpp || '');
    return model;
  });
}

function recalculate_() {
  const sh = SpreadsheetApp.getActiveSpreadsheet().getSheetByName('AKP_Transaksi');
  if (!sh || sh.getLastRow() < 2) { updateSummary_(); return; }
  const h = headers_(sh);
  const idx = {};
  h.forEach((name,i)=>idx[name]=i+1);
  const L = n => colLetter_(idx[n]);
  const lastRow = sh.getLastRow();
  const type = L('jenis'), amount = L('nominal'), qty = L('qtyKg'), output = L('kgSiapJual');
  const batch = L('batchRef'), ref = L('referensi'), status = L('status'), deleted = L('deleted');
  const hppUnit = L('hppPerKg'), hppSale = L('hppJual'), gross = L('labaKotor'), stock = L('kgSisa'), hppStatus = L('statusHpp');
  for (let row=2; row<=lastRow; row++) {
    const amountCell = amount+row, typeCell = type+row, qtyCell=qty+row, outputCell=output+row;
    const batchCell=batch+row, refCell=ref+row, statusCell=status+row, deletedCell=deleted+row;
    const unitFormula = '=IF(AND('+typeCell+'="purchase",'+statusCell+'="confirmed",'+deletedCell+'<>TRUE),IFERROR(('+amountCell+'+SUMIFS($'+amount+'$2:$'+amount+',$'+type+'$2:$'+type+',"expense",$'+batch+'$2:$'+batch+','+refCell+',$'+status+'$2:$'+status+',"confirmed",$'+deleted+'$2:$'+deleted+',"<>TRUE"))/IF('+outputCell+'>0,'+outputCell+','+qtyCell+'),0),"")';
    const saleFormula = '=IF(AND('+typeCell+'="sale",'+statusCell+'="confirmed",'+deletedCell+'<>TRUE),IF(OR('+batchCell+'="",COUNTIFS($'+type+'$2:$'+type+',"purchase",$'+ref+'$2:$'+ref+','+batchCell+',$'+status+'$2:$'+status+',"confirmed",$'+deleted+'$2:$'+deleted+',"<>TRUE")<>1),0,IFERROR('+qtyCell+'*INDEX($'+hppUnit+'$2:$'+hppUnit+',MATCH('+batchCell+',$'+ref+'$2:$'+ref+',0)),0)),"")';
    const grossFormula = '=IF(AND('+typeCell+'="sale",'+statusCell+'="confirmed",'+deletedCell+'<>TRUE),IF('+hppSale+row+'>0,'+amountCell+'-'+hppSale+row+',""),"")';
    const stockFormula = '=IF(AND('+typeCell+'="purchase",'+statusCell+'="confirmed",'+deletedCell+'<>TRUE),IF('+outputCell+'>0,'+outputCell+','+qtyCell+')-SUMIFS($'+qty+'$2:$'+qty+',$'+type+'$2:$'+type+',"sale",$'+batch+'$2:$'+batch+','+refCell+',$'+status+'$2:$'+status+',"confirmed",$'+deleted+'$2:$'+deleted+',"<>TRUE"),"")';
    const purchaseRefCount = 'COUNTIFS($'+type+'$2:$'+type+',"purchase",$'+ref+'$2:$'+ref+','+refCell+',$'+status+'$2:$'+status+',"confirmed",$'+deleted+'$2:$'+deleted+',"<>TRUE")';
    const statusFormula = '=IF('+deletedCell+'=TRUE,"DIARSIPKAN",IF('+statusCell+'<>"confirmed","DRAF",IF('+typeCell+'="purchase",IF('+refCell+'="","REF DO KOSONG",IF('+purchaseRefCount+'>1,"REF DO GANDA",IF('+hppUnit+row+'<=0,"HPP BELUM LENGKAP",IF('+outputCell+'="","ASUMSI KG MASUK",IF('+stock+row+'<0,"STOK MINUS","OK"))))),IF('+typeCell+'="sale",IF(OR('+qtyCell+'<=0,'+batchCell+'="",'+hppSale+row+'<=0),"HPP BELUM LENGKAP","OK"),IF(AND('+typeCell+'="expense",'+batchCell+'<>"",COUNTIFS($'+type+'$2:$'+type+',"purchase",$'+ref+'$2:$'+ref+','+batchCell+',$'+status+'$2:$'+status+',"confirmed",$'+deleted+'$2:$'+deleted+',"<>TRUE")<>1),"BIAYA BATCH TANPA DO","OK")))))';
    sh.getRange(row,idx.hppPerKg).setFormula(unitFormula);
    sh.getRange(row,idx.hppJual).setFormula(saleFormula);
    sh.getRange(row,idx.labaKotor).setFormula(grossFormula);
    sh.getRange(row,idx.kgSisa).setFormula(stockFormula);
    sh.getRange(row,idx.statusHpp).setFormula(statusFormula);
  }
  updateSummary_();
}

function updateSummary_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const sh = ss.getSheetByName('AKP_Ringkasan');
  const tx = ss.getSheetByName('AKP_Transaksi');
  if (!sh || !tx) return;
  const h = headers_(tx), ix={};
  h.forEach((name,i)=>ix[name]=i+1);
  const L = n => colLetter_(ix[n]);
  const t = 'AKP_Transaksi!';
  const r = n => t+'$'+L(n)+'$2:$'+L(n);
  const typ=r('jenis'), amount=r('nominal'), paid=r('dibayar'), kg=r('qtyKg'), batch=r('batchRef'),
        ver=r('status'), del=r('deleted'), hpp=r('hppJual'), kgRemain=r('kgSisa');
  const confirmed = ver+', "confirmed", '+del+', "<>TRUE"';
  const sum = (field, type) => '=SUMIFS('+r(field)+','+typ+',"'+type+'",'+confirmed+')';
  const countHppMissing = '=COUNTIFS('+typ+',"sale",'+ver+',"confirmed",'+del+',"<>TRUE",'+r('statusHpp')+',"HPP BELUM LENGKAP")';
  const countDrafts = '=COUNTIF('+ver+',"draft")';
  const countAssumedYield = '=COUNTIFS('+typ+',"purchase",'+ver+',"confirmed",'+del+',"<>TRUE",'+r('kgSiapJual')+',"" )';
  const countNegativeStock = '=COUNTIFS('+r('statusHpp')+',"STOK MINUS",'+ver+',"confirmed",'+del+',"<>TRUE")';
  const countDuplicateDo = '=COUNTIFS('+r('statusHpp')+',"REF DO GANDA",'+ver+',"confirmed",'+del+',"<>TRUE")';
  const countUnlinkedBatchCost = '=COUNTIFS('+r('statusHpp')+',"BIAYA BATCH TANPA DO",'+ver+',"confirmed",'+del+',"<>TRUE")';
  const formulas = [
    ['Omzet penjualan',sum('nominal','sale'),'Jumlah nominal transaksi penjualan terverifikasi; bukan penerimaan kas.'],
    ['HPP ayam terjual','=SUMIFS('+hpp+','+typ+',"sale",'+confirmed+')','Kg terjual × HPP/kg dari DO yang ditautkan. DO yang tidak unik atau belum lengkap tidak boleh dianggap memiliki HPP valid.'],
    ['Laba kotor','=B2-B3','Omzet penjualan − HPP barang yang benar-benar terjual.'],
    ['Biaya operasional','=SUMIFS('+r('nominal')+','+typ+',"expense",'+batch+',"",'+confirmed+')','Biaya tanpa referensi batch DO; biaya batch yang tertaut dimasukkan ke HPP.'],
    ['Laba operasional sementara','=B4-B5','Laba kotor − biaya operasional yang tidak dialokasikan ke batch. Belum otomatis memuat pajak/biaya lain yang belum dicatat.'],
    ['Pembelian DO',sum('nominal','purchase'),'Pembelian menambah persediaan dan utang/kas, tidak otomatis menjadi seluruh beban laba pada hari pembelian.'],
    ['Stok bersih tercatat (kg)','=SUMIFS('+kgRemain+','+typ+',"purchase",'+confirmed+')','Kg siap jual per DO − kg penjualan tertaut. Selisih susut harus dimasukkan pada kg siap jual.'],
    ['Penerimaan tunai dari penjualan','=SUMIFS('+paid+','+typ+',"sale",'+confirmed+')','Menggunakan kolom dibayar, bukan omzet. Pembayaran yang belum diketahui tidak diasumsikan diterima.'],
    ['Kas keluar pembelian DO','=SUMIFS('+paid+','+typ+',"purchase",'+confirmed+')','Pembayaran saat pembelian saja; pembayaran utang berikutnya dihitung di baris terpisah.'],
    ['Terima piutang',sum('nominal','receivable_payment'),'Arus kas masuk untuk pembayaran piutang terdahulu. Tautkan referensi invoice pada transaksi.'],
    ['Bayar utang supplier',sum('nominal','supplier_payment'),'Arus kas keluar untuk membayar utang DO terdahulu.'],
    ['Kas bersih tercatat','=SUMIFS('+r('nominal')+','+typ+',"capital_in",'+confirmed+')+SUMIFS('+paid+','+typ+',"sale",'+confirmed+')+SUMIFS('+r('nominal')+','+typ+',"receivable_payment",'+confirmed+')-SUMIFS('+paid+','+typ+',"purchase",'+confirmed+')-SUMIFS('+paid+','+typ+',"expense",'+confirmed+')-SUMIFS('+r('nominal')+','+typ+',"supplier_payment",'+confirmed+')-SUMIFS('+r('nominal')+','+typ+',"capital_out",'+confirmed+')','Kas awal + kas masuk aktual − kas keluar aktual. Masukkan saldo/modal awal sebagai transaksi modal masuk; mutasi transfer antar-akun belum dihitung otomatis.'],
    ['Piutang agregat (estimasi)','=MAX(0,SUMIFS('+r('nominal')+','+typ+',"sale",'+confirmed+')-SUMIFS('+paid+','+typ+',"sale",'+confirmed+')-SUMIFS('+r('nominal')+','+typ+',"receivable_payment",'+confirmed+'))','Estimasi total semua invoice. Untuk umur piutang dan saldo per pelanggan, setiap penerimaan wajib ditautkan ke invoice terkait.'],
    ['Utang DO agregat (estimasi)','=MAX(0,SUMIFS('+r('nominal')+','+typ+',"purchase",'+confirmed+')-SUMIFS('+paid+','+typ+',"purchase",'+confirmed+')-SUMIFS('+r('nominal')+','+typ+',"supplier_payment",'+confirmed+'))','Estimasi total utang. Untuk saldo per supplier/DO, pembayaran perlu ditautkan ke invoice/DO terkait.'],
    ['Penjualan belum lengkap HPP',countHppMissing,'Jumlah penjualan terverifikasi yang kg atau tautan DO-nya belum membuat HPP dapat dihitung.'],
    ['Transaksi draf',countDrafts,'Belum masuk ringkasan resmi sampai diverifikasi.'],
    ['DO tanpa kg siap jual',countAssumedYield,'HPP masih memakai kg masuk sebagai asumsi. Isi berat bersih setelah potong/susut.'],
    ['Stok negatif',countNegativeStock,'Jumlah batch dengan penjualan melebihi kg siap jual. Periksa timbang, batch dan retur.'],
    ['Referensi DO ganda',countDuplicateDo,'Nomor DO yang tidak unik membuat alokasi HPP tidak aman.'],
    ['Biaya batch tanpa DO valid',countUnlinkedBatchCost,'Biaya tertaut ke batch yang tidak ditemukan atau memiliki referensi ganda.']
  ];
  const rows = formulas.map(x=>[x[0],'',x[2],new Date()]);
  if (sh.getLastRow() > 1) sh.getRange(2,1,sh.getLastRow()-1,4).clearContent();
  sh.getRange(2,1,rows.length,4).setValues(rows);
  formulas.forEach((x,i)=>sh.getRange(i+2,2).setFormula(x[1]));
  sh.getRange(2,2,rows.length,1).setNumberFormat('#,##0.00;[Red]-#,##0.00');
  sh.autoResizeColumns(1,4);
}

function colLetter_(column) {
  let n=Number(column), s='';
  while (n>0) { const r=(n-1)%26; s=String.fromCharCode(65+r)+s; n=Math.floor((n-1)/26); }
  return s;
}

function softDelete_(sheetName,id) {
  if (!id) throw new Error('ID wajib diisi.');
  const sh=SpreadsheetApp.getActiveSpreadsheet().getSheetByName(sheetName);
  const headers=headers_(sh);
  const rows=list_(sheetName,true);
  const i=rows.findIndex(r=>String(r.id)===String(id));
  if (i<0) throw new Error('Data tidak ditemukan.');
  sh.getRange(i+2,headers.indexOf('deleted')+1).setValue(true);
  sh.getRange(i+2,headers.indexOf('updatedAt')+1).setValue(new Date().toISOString());
  audit_('SOFT_DELETE',sheetName,id,'Data diarsipkan, bukan dihapus permanen.');
  return {ok:true};
}

function audit_(action,sheet,id,detail) {
  const sh=SpreadsheetApp.getActiveSpreadsheet().getSheetByName('AKP_AuditLog');
  if (!sh) return;
  let user='pengguna-terotorisasi';
  try { user=Session.getActiveUser().getEmail() || user; } catch (e) {}
  sh.appendRow([new Date(),user,action,sheet,id,detail]);
}

function backup_() {
  const ss=SpreadsheetApp.getActiveSpreadsheet();
  const copy=DriveApp.getFileById(ss.getId()).makeCopy(
    'Backup AyamKas Pro '+Utilities.formatDate(new Date(),Session.getScriptTimeZone(),'yyyy-MM-dd HHmmss')
  );
  audit_('BACKUP','Drive',copy.getId(),copy.getName());
  return {ok:true,fileId:copy.getId(),name:copy.getName(),url:copy.getUrl()};
}
