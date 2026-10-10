/**
 * AyamKas Pro — backend Google Apps Script yang benar-benar membaca/menulis Google Sheets.
 * Pakai sebagai script TERIKAT (bound) pada spreadsheet yang menjadi sumber data utama.
 *
 * Instalasi: salin file ini sebagai Code.gs, salin AyamKasPro.html sebagai file HTML "AyamKasPro",
 * jalankan setupAyamKasPro sekali, kemudian deploy sebagai Web app.
 */
const AK = {
  property: 'AYAMKAS_SPREADSHEET_ID',
  txSheet: 'AK_Transaksi',
  employeesSheet: 'AK_Karyawan',
  attendanceSheet: 'AK_Absensi',
  payrollSheet: 'AK_Gaji',
  auditSheet: 'AK_Audit',
  txHeaders: ['ID','Tanggal','Referensi','Jenis','Kategori','Keterangan','Nominal','Diterima_Dibayar','Qty_Kg','Ref_DO','Status','Catatan','UpdatedAt','UpdatedBy'],
  employeeHeaders: ['ID','Nama','Telepon','JenisGaji','GajiHarian','GajiBulanan','Status','DibuatAt','UpdatedAt'],
  attendanceHeaders: ['ID','Tanggal','KaryawanID','NamaKaryawan','Status','JamMasuk','JamPulang','JamLembur','Catatan','UpdatedAt','UpdatedBy'],
  payrollHeaders: ['ID','Periode','KaryawanID','NamaKaryawan','JenisGaji','GajiPokok','Lembur','Tunjangan','Potongan','TotalBersih','SudahDibayar','TanggalBayar','Status','Catatan','TransaksiID','UpdatedAt'],
  auditHeaders: ['Timestamp','Pengguna','Aksi','Modul','IDRecord','Detail'],
  transactionTypes: ['capital_in','purchase','sale','expense','capital_out','supplier_payment','receivable_payment'],
  attendanceStatuses: ['Hadir','Setengah Hari','Izin','Sakit','Cuti','Alpa','Libur']
};

function doGet() {
  return HtmlService.createHtmlOutputFromFile('AyamKasPro')
    .setTitle('AyamKas Pro — Pembukuan Ayam Potong')
    .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL);
}

function setupAyamKasPro() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  if (!ss) throw new Error('Buka Apps Script dari spreadsheet Google Sheets yang akan menjadi database utama.');
  ss.setSpreadsheetTimeZone('Asia/Jakarta');
  PropertiesService.getScriptProperties().setProperty(AK.property, ss.getId());
  ensureSheet_(ss, AK.txSheet, AK.txHeaders);
  ensureSheet_(ss, AK.employeesSheet, AK.employeeHeaders);
  ensureSheet_(ss, AK.attendanceSheet, AK.attendanceHeaders);
  ensureSheet_(ss, AK.payrollSheet, AK.payrollHeaders);
  ensureSheet_(ss, AK.auditSheet, AK.auditHeaders);
  styleSheet_(ss.getSheetByName(AK.txSheet), AK.txHeaders, 'TRANSAKSI UTAMA — AyamKas Pro');
  styleSheet_(ss.getSheetByName(AK.employeesSheet), AK.employeeHeaders, 'MASTER KARYAWAN');
  styleSheet_(ss.getSheetByName(AK.attendanceSheet), AK.attendanceHeaders, 'ABSENSI HARIAN');
  styleSheet_(ss.getSheetByName(AK.payrollSheet), AK.payrollHeaders, 'REKAP GAJI');
  styleSheet_(ss.getSheetByName(AK.auditSheet), AK.auditHeaders, 'JEJAK AUDIT');
  SpreadsheetApp.flush();
  return {ok:true, spreadsheetId:ss.getId(), spreadsheetName:ss.getName(), sheets:[AK.txSheet,AK.employeesSheet,AK.attendanceSheet,AK.payrollSheet,AK.auditSheet]};
}

function apiBootstrap() {
  var ss = getDb_();
  return {
    ok:true,
    spreadsheetName:ss.getName(),
    serverTime:new Date().toISOString(),
    transactions:readTransactions_(),
    operations:readOperations_()
  };
}

function apiSyncTransactions(localTransactions) {
  if (!Array.isArray(localTransactions)) throw new Error('Data transaksi harus berbentuk daftar.');
  if (localTransactions.length > 2000) throw new Error('Maksimal 2.000 transaksi per sync. Pecah cadangan/data awal jika lebih besar.');
  var lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    var ss = getDb_();
    var sh = ss.getSheetByName(AK.txSheet);
    var current = readTransactionsWithRows_(sh);
    var byId = {};
    current.forEach(function(x){ byId[x.record.id] = x; });
    var inserted=0, updated=0, skipped=0;
    var conflicts=[];
    localTransactions.forEach(function(raw){
      var incoming = cleanTransaction_(raw);
      if (!incoming.id) throw new Error('Ada transaksi tanpa ID. Ekspor cadangan sebelum melanjutkan.');
      var old = byId[incoming.id];
      if (!old) {
        if (!incoming.updatedAt) incoming.updatedAt = new Date().toISOString();
        var nextRow = Math.max(sh.getLastRow()+1,2);
        sh.getRange(nextRow,1,1,AK.txHeaders.length).setValues([transactionToRow_(incoming)]);
        byId[incoming.id] = {record:incoming,row:nextRow};
        inserted++;
        audit_(ss,'INSERT','Transaksi',incoming.id,incoming.type+' '+incoming.ref+' '+incoming.amount);
        return;
      }
      // Record lama tanpa updatedAt tidak boleh mengalahkan versi server.
      if (!raw.updatedAt) { skipped++; return; }
      var incomingTime = Date.parse(incoming.updatedAt) || 0;
      var serverTime = Date.parse(old.record.updatedAt) || 0;
      if (incomingTime > serverTime) {
        incoming.updatedAt = new Date().toISOString();
        sh.getRange(old.row,1,1,AK.txHeaders.length).setValues([transactionToRow_(incoming)]);
        byId[incoming.id] = {record:incoming,row:old.row};
        updated++;
        audit_(ss,'UPDATE','Transaksi',incoming.id,incoming.type+' '+incoming.ref+' '+incoming.amount);
      } else if (incomingTime < serverTime && !sameTransaction_(incoming,old.record)) {
        conflicts.push({id:incoming.id,ref:incoming.ref,serverUpdatedAt:old.record.updatedAt});
      } else {
        skipped++;
      }
    });
    // Server mencegah transaksi terverifikasi yang pasti menghasilkan HPP/stok palsu.
    var fresh = readTransactionsWithRows_(sh);
    var purchases = {};
    fresh.forEach(function(x){
      var t=x.record;
      if (t.type==='purchase' && t.ref) purchases[String(t.ref).trim().toLowerCase()] = t;
    });
    fresh.forEach(function(x){
      var t=x.record, reason='';
      if (t.status==='confirmed' && t.type==='sale') {
        if (!(Number(t.qtyKg)>0)) reason='Penjualan belum memiliki kg terjual.';
        else if (!t.batchRef || !purchases[String(t.batchRef).trim().toLowerCase()]) reason='Penjualan belum terhubung ke DO pembelian yang valid.';
      }
      if (t.status==='confirmed' && t.type==='purchase' && (!(Number(t.qtyKg)>0) || !t.ref)) reason='DO terverifikasi wajib memiliki referensi unik dan berat kg.';
      if (reason) {
        t.status='draft';
        t.updatedAt=new Date().toISOString();
        t.notes=[t.notes, 'Otomatis ditahan server: '+reason].filter(Boolean).join(' | ');
        sh.getRange(x.row,1,1,AK.txHeaders.length).setValues([transactionToRow_(t)]);
        audit_(ss,'DOWNGRADE','Transaksi',t.id,reason);
      }
    });
    SpreadsheetApp.flush();
    return {
      ok:true, inserted:inserted, updated:updated, skipped:skipped, conflicts:conflicts,
      serverTime:new Date().toISOString(),
      transactions:readTransactions_(),
      operations:readOperations_()
    };
  } finally {
    lock.releaseLock();
  }
}

function apiGetOperations() {
  return {ok:true, operations:readOperations_(), serverTime:new Date().toISOString()};
}

function apiSaveEmployee(input) {
  if (!input || !String(input.name||'').trim()) throw new Error('Nama karyawan wajib diisi.');
  var type = input.payType === 'Bulanan' ? 'Bulanan' : 'Harian';
  var daily = money_(input.dailyWage), monthly = money_(input.monthlyWage);
  if (type==='Harian' && daily<=0) throw new Error('Gaji harian harus lebih dari 0.');
  if (type==='Bulanan' && monthly<=0) throw new Error('Gaji bulanan harus lebih dari 0.');
  var lock=LockService.getScriptLock(); lock.waitLock(20000);
  try {
    var ss=getDb_(), sh=ss.getSheetByName(AK.employeesSheet), rows=readObjectRows_(sh,AK.employeeHeaders);
    var id=String(input.id||'').trim() || ('EMP-'+Utilities.getUuid().slice(0,8).toUpperCase());
    var found=rows.filter(function(r){return r.ID===id;})[0];
    var now=new Date().toISOString();
    var record={ID:id,Nama:String(input.name).trim(),Telepon:String(input.phone||'').trim(),JenisGaji:type,
      GajiHarian:daily,GajiBulanan:monthly,Status:input.active===false?'Nonaktif':'Aktif',
      DibuatAt:found?found.DibuatAt:now,UpdatedAt:now};
    upsertObjectRow_(sh,AK.employeeHeaders,record,'ID');
    audit_(ss,found?'UPDATE':'INSERT','Karyawan',id,record.Nama);
    SpreadsheetApp.flush();
    return {ok:true, operations:readOperations_(), employee:objectToEmployee_(record)};
  } finally { lock.releaseLock(); }
}

function apiSaveAttendance(input) {
  if (!input || !input.date || !input.employeeId) throw new Error('Tanggal dan karyawan wajib dipilih.');
  var status=String(input.status||'');
  if (AK.attendanceStatuses.indexOf(status)<0) throw new Error('Status absensi tidak dikenal.');
  var overtime=Number(input.overtimeHours||0);
  if (!isFinite(overtime) || overtime<0 || overtime>24) throw new Error('Jam lembur harus antara 0 dan 24.');
  var lock=LockService.getScriptLock(); lock.waitLock(20000);
  try {
    var ss=getDb_(), employees=readEmployees_(), emp=employees.filter(function(e){return e.id===String(input.employeeId);})[0];
    if (!emp) throw new Error('Karyawan tidak ditemukan. Simpan master karyawan terlebih dahulu.');
    var sh=ss.getSheetByName(AK.attendanceSheet), records=readAttendance_();
    var old=records.filter(function(r){return r.employeeId===emp.id && r.date===String(input.date);})[0];
    var id=old?old.id:(String(input.id||'').trim()||('ABS-'+Utilities.getUuid().slice(0,8).toUpperCase()));
    var obj={ID:id,Tanggal:String(input.date),KaryawanID:emp.id,NamaKaryawan:emp.name,Status:status,
      JamMasuk:String(input.timeIn||''),JamPulang:String(input.timeOut||''),JamLembur:overtime,
      Catatan:String(input.notes||'').trim(),UpdatedAt:new Date().toISOString(),UpdatedBy:actor_()};
    upsertObjectRow_(sh,AK.attendanceHeaders,obj,'ID');
    audit_(ss,old?'UPDATE':'INSERT','Absensi',id,emp.name+' '+input.date+' '+status);
    SpreadsheetApp.flush();
    return {ok:true, operations:readOperations_()};
  } finally { lock.releaseLock(); }
}

function apiSavePayroll(input) {
  if (!input || !input.employeeId || !/^\d{4}-\d{2}$/.test(String(input.period||''))) throw new Error('Karyawan dan periode YYYY-MM wajib diisi.');
  var overtime=money_(input.overtime), allowances=money_(input.allowances), deductions=money_(input.deductions), paid=money_(input.paidAmount);
  if ([overtime,allowances,deductions,paid].some(function(n){return n<0;})) throw new Error('Nominal gaji tidak boleh negatif.');
  var lock=LockService.getScriptLock(); lock.waitLock(20000);
  try {
    var ss=getDb_(), employees=readEmployees_(), emp=employees.filter(function(e){return e.id===String(input.employeeId);})[0];
    if (!emp) throw new Error('Karyawan tidak ditemukan.');
    var ops=readOperations_(), att=ops.attendance.filter(function(a){return a.employeeId===emp.id && a.date.slice(0,7)===String(input.period);});
    var base=0;
    if (emp.payType==='Bulanan') {
      base=emp.monthlyWage;
    } else {
      att.forEach(function(a){if(a.status==='Hadir')base+=emp.dailyWage;else if(a.status==='Setengah Hari')base+=emp.dailyWage/2;});
    }
    var net=Math.round(base+overtime+allowances-deductions);
    if (net<0) throw new Error('Total gaji bersih tidak boleh negatif.');
    if (paid>net) throw new Error('Jumlah dibayar tidak boleh melebihi total gaji bersih.');
    var payrollSh=ss.getSheetByName(AK.payrollSheet), existing=readObjectRows_(payrollSh,AK.payrollHeaders);
    var old=existing.filter(function(r){return r.KaryawanID===emp.id && r.Periode===String(input.period);})[0];
    var id=old?old.ID:('PAY-'+String(input.period).replace('-','')+'-'+emp.id);
    var status=String(input.status||'Draft')==='Terverifikasi'?'Terverifikasi':'Draft';
    var txId='GAJI-'+String(input.period).replace('-','')+'-'+emp.id;
    var now=new Date().toISOString();
    var pr={ID:id,Periode:String(input.period),KaryawanID:emp.id,NamaKaryawan:emp.name,JenisGaji:emp.payType,
      GajiPokok:base,Lembur:overtime,Tunjangan:allowances,Potongan:deductions,TotalBersih:net,
      SudahDibayar:paid,TanggalBayar:String(input.paymentDate||''),Status:status,
      Catatan:String(input.notes||'').trim(),TransaksiID:txId,UpdatedAt:now};
    upsertObjectRow_(payrollSh,AK.payrollHeaders,pr,'ID');
    var txRecord=null;
    if (status==='Terverifikasi') {
      txRecord={id:txId,date:String(input.paymentDate||String(input.period)+'-01'),ref:id,type:'expense',category:'Gaji',
        description:'Gaji '+String(input.period)+' - '+emp.name,amount:net,paidAmount:paid,qtyKg:null,batchRef:'',
        status:'confirmed',notes:'Dibuat dari modul penggajian. '+String(input.notes||'').trim(),updatedAt:now,updatedBy:actor_()};
      upsertTxInternal_(ss,txRecord);
    } else {
      var oldTx=readTransactionsWithRows_(ss.getSheetByName(AK.txSheet)).filter(function(x){return x.record.id===txId;})[0];
      if(oldTx){oldTx.record.status='draft';oldTx.record.updatedAt=now;ss.getSheetByName(AK.txSheet).getRange(oldTx.row,1,1,AK.txHeaders.length).setValues([transactionToRow_(oldTx.record)]);}
    }
    audit_(ss,old?'UPDATE':'INSERT','Penggajian',id,emp.name+' '+input.period+' total='+net+' paid='+paid);
    SpreadsheetApp.flush();
    return {ok:true, operations:readOperations_(), transaction:txRecord, serverTime:now};
  } finally { lock.releaseLock(); }
}

function apiBackupToDrive() {
  var ss=getDb(), file=DriveApp.getFileById(ss.getId());
  var folderIterator=file.getParents();
  var folder=folderIterator.hasNext()?folderIterator.next():DriveApp.getRootFolder();
  var stamp=Utilities.formatDate(new Date(),'Asia/Jakarta','yyyyMMdd-HHmmss');
  var copy=file.makeCopy('AyamKasPro_Backup_'+stamp,folder);
  audit_(ss,'BACKUP','Drive',copy.getId(),copy.getName());
  return {ok:true,name:copy.getName(),id:copy.getId(),url:copy.getUrl(),createdAt:new Date().toISOString()};
}

function apiHealthCheck() {
  var ss=getDb();
  return {ok:true,spreadsheetName:ss.getName(),spreadsheetId:ss.getId(),sheets:[AK.txSheet,AK.employeesSheet,AK.attendanceSheet,AK.payrollSheet,AK.auditSheet].map(function(n){return {name:n,rows:ss.getSheetByName(n).getLastRow()};}),time:new Date().toISOString()};
}

// ---------- Helpers ----------
function getDb_() {
  var id=PropertiesService.getScriptProperties().getProperty(AK.property);
  if (!id) throw new Error('Database belum diinisialisasi. Jalankan setupAyamKasPro() dari Apps Script yang terikat pada spreadsheet.');
  return SpreadsheetApp.openById(id);
}
function ensureSheet_(ss,name,headers) {
  var sh=ss.getSheetByName(name); if(!sh) sh=ss.insertSheet(name);
  var last=sh.getLastRow();
  if(last===0) sh.getRange(1,1,1,headers.length).setValues([headers]);
  else {
    var old=sh.getRange(1,1,1,headers.length).getDisplayValues()[0];
    var empty=old.every(function(v){return !String(v||'').trim();});
    if(empty) sh.getRange(1,1,1,headers.length).setValues([headers]);
    else if(headers.some(function(h,i){return String(old[i]||'')!==h;})) throw new Error('Header sheet '+name+' tidak cocok. Data tidak dihapus; periksa sheet sebelum setup ulang.');
  }
  sh.setFrozenRows(1);
  return sh;
}
function styleSheet_(sh,headers,title) {
  sh.getRange(1,1,1,headers.length).setFontWeight('bold').setBackground('#d9ead3').setWrap(true);
  sh.autoResizeColumns(1,headers.length);
}
function readTransactions_() { return readTransactionsWithRows_(getDb_().getSheetByName(AK.txSheet)).map(function(x){return x.record;}); }
function readTransactionsWithRows_(sh) {
  if(sh.getLastRow()<2)return [];
  return sh.getRange(2,1,sh.getLastRow()-1,AK.txHeaders.length).getValues().map(function(r,i){
    if(!r[0])return null;
    return {row:i+2,record:{id:String(r[0]),date:dateString_(r[1]),ref:String(r[2]||''),type:String(r[3]||'expense'),
      category:String(r[4]||'Umum'),description:String(r[5]||''),amount:Number(r[6])||0,
      paidAmount:r[7]===''||r[7]===null?null:Number(r[7]),qtyKg:r[8]===''||r[8]===null?null:Number(r[8]),
      batchRef:String(r[9]||''),status:String(r[10]||'draft'),notes:String(r[11]||''),
      updatedAt:String(r[12]||''),updatedBy:String(r[13]||'')}};
  }).filter(Boolean);
}
function cleanTransaction_(t) {
  if(!t || typeof t!=='object')throw new Error('Format transaksi tidak valid.');
  var amount=Number(t.amount);
  if(!isFinite(amount)||amount<=0)throw new Error('Nominal transaksi harus lebih dari nol (ID '+String(t.id||'?')+').');
  var paid=t.paidAmount===null||t.paidAmount===''||typeof t.paidAmount==='undefined'?null:Number(t.paidAmount);
  if(paid!==null&&(!isFinite(paid)||paid<0))throw new Error('Nominal dibayar tidak valid (ID '+String(t.id||'?')+').');
  if(paid!==null&&paid>amount&&['sale','purchase','expense'].indexOf(String(t.type))>=0)throw new Error('Pembayaran melebihi nilai transaksi (ID '+String(t.id||'?')+').');
  var qty=t.qtyKg===null||t.qtyKg===''||typeof t.qtyKg==='undefined'?null:Number(t.qtyKg);
  if(qty!==null&&(!isFinite(qty)||qty<0))throw new Error('Kg tidak valid (ID '+String(t.id||'?')+').');
  var status=['confirmed','draft','void'].indexOf(String(t.status))>=0?String(t.status):'draft';
  if(AK.transactionTypes.indexOf(String(t.type))<0)throw new Error('Jenis transaksi tidak dikenali: '+String(t.type));
  if(!/^\d{4}-\d{2}-\d{2}$/.test(String(t.date||'')))throw new Error('Tanggal harus YYYY-MM-DD (ID '+String(t.id||'?')+').');
  return {id:String(t.id||'').trim(),date:String(t.date),ref:String(t.ref||'').trim(),type:String(t.type),
    category:String(t.category||'Umum').trim(),description:String(t.description||'').trim(),amount:amount,
    paidAmount:paid,qtyKg:qty,batchRef:String(t.batchRef||'').trim(),status:status,
    notes:String(t.notes||''),updatedAt:String(t.updatedAt||''),updatedBy:actor_()};
}
function transactionToRow_(t) {
  return [t.id,t.date,t.ref,t.type,t.category,t.description,t.amount,t.paidAmount===null?'':t.paidAmount,
    t.qtyKg===null?'':t.qtyKg,t.batchRef,t.status,t.notes,t.updatedAt||new Date().toISOString(),t.updatedBy||actor_()];
}
function sameTransaction_(a,b) {
  return ['date','ref','type','category','description','amount','paidAmount','qtyKg','batchRef','status','notes'].every(function(k){return String(a[k]===null?'':a[k])===String(b[k]===null?'':b[k]);});
}
function upsertTxInternal_(ss,tx) {
  var sh=ss.getSheetByName(AK.txSheet), rows=readTransactionsWithRows_(sh), old=rows.filter(function(x){return x.record.id===tx.id;})[0];
  if(old)sh.getRange(old.row,1,1,AK.txHeaders.length).setValues([transactionToRow_(tx)]);
  else sh.getRange(Math.max(sh.getLastRow()+1,2),1,1,AK.txHeaders.length).setValues([transactionToRow_(tx)]);
}
function readObjectRows_(sh,headers) {
  if(sh.getLastRow()<2)return [];
  return sh.getRange(2,1,sh.getLastRow()-1,headers.length).getValues().filter(function(row){return row[0]!==''&&row[0]!==null;}).map(function(row){
    var o={}; headers.forEach(function(h,i){o[h]=row[i] instanceof Date?dateString_(row[i]):row[i];}); return o;
  });
}
function upsertObjectRow_(sh,headers,obj,key) {
  var rows=readObjectRows_(sh,headers), target=0;
  for(var i=0;i<rows.length;i++){if(String(rows[i][key])===String(obj[key])){target=i+2;break;}}
  var arr=headers.map(function(h){return obj[h]===undefined||obj[h]===null?'':obj[h];});
  if(target)sh.getRange(target,1,1,headers.length).setValues([arr]);
  else sh.getRange(Math.max(sh.getLastRow()+1,2),1,1,headers.length).setValues([arr]);
}
function readEmployees_() {
  return readObjectRows_(getDb_().getSheetByName(AK.employeesSheet),AK.employeeHeaders).map(objectToEmployee_);
}
function objectToEmployee_(r) {
  return {id:String(r.ID||''),name:String(r.Nama||''),phone:String(r.Telepon||''),payType:String(r.JenisGaji||'Harian'),
    dailyWage:Number(r.GajiHarian)||0,monthlyWage:Number(r.GajiBulanan)||0,active:String(r.Status||'Aktif')==='Aktif',
    createdAt:String(r.DibuatAt||''),updatedAt:String(r.UpdatedAt||'')};
}
function readAttendance_() {
  return readObjectRows_(getDb_().getSheetByName(AK.attendanceSheet),AK.attendanceHeaders).map(function(r){
    return {id:String(r.ID||''),date:dateString_(r.Tanggal),employeeId:String(r.KaryawanID||''),employeeName:String(r.NamaKaryawan||''),
      status:String(r.Status||''),timeIn:String(r.JamMasuk||''),timeOut:String(r.JamPulang||''),overtimeHours:Number(r.JamLembur)||0,
      notes:String(r.Catatan||''),updatedAt:String(r.UpdatedAt||''),updatedBy:String(r.UpdatedBy||'')};
  });
}
function readPayroll_() {
  return readObjectRows_(getDb_().getSheetByName(AK.payrollSheet),AK.payrollHeaders).map(function(r){
    return {id:String(r.ID||''),period:String(r.Periode||''),employeeId:String(r.KaryawanID||''),employeeName:String(r.NamaKaryawan||''),
      payType:String(r.JenisGaji||''),baseSalary:Number(r.GajiPokok)||0,overtime:Number(r.Lembur)||0,allowances:Number(r.Tunjangan)||0,
      deductions:Number(r.Potongan)||0,total:Number(r.TotalBersih)||0,paidAmount:Number(r.SudahDibayar)||0,paymentDate:dateString_(r.TanggalBayar),
      status:String(r.Status||''),notes:String(r.Catatan||''),transactionId:String(r.TransaksiID||''),updatedAt:String(r.UpdatedAt||'')};
  });
}
function readOperations_() { return {employees:readEmployees_(),attendance:readAttendance_(),payroll:readPayroll_()}; }
function audit_(ss,action,module,id,detail) {
  var sh=ss.getSheetByName(AK.auditSheet);
  if(sh)sh.appendRow([new Date().toISOString(),actor_(),action,module,id,String(detail||'').slice(0,400)]);
}
function actor_() {
  try { return Session.getActiveUser().getEmail()||'Pengguna web app'; } catch(e) { return 'Pengguna web app'; }
}
function money_(v) { var n=Number(v||0); if(!isFinite(n))throw new Error('Nominal tidak valid.'); return Math.round(n); }
function dateString_(v) {
  if(!v)return '';
  if(v instanceof Date)return Utilities.formatDate(v,'Asia/Jakarta','yyyy-MM-dd');
  var s=String(v); return /^\d{4}-\d{2}-\d{2}/.test(s)?s.slice(0,10):s;
}