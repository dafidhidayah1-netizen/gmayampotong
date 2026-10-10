const APP_NAME = 'AyamKas Pro';
const SHEETS = {
  Transaksi: ['id','createdAt','tanggal','jenis','kategori','keterangan','nominal','status','metode','pihak','referensi','dibayar','sisa','updatedAt','deleted'],
  Karyawan: ['id','nama','jabatan','tarifHarian','aktif','updatedAt','deleted'],
  Absensi: ['id','tanggal','karyawanId','namaKaryawan','status','jamMasuk','jamPulang','lemburJam','catatan','updatedAt','deleted'],
  Penggajian: ['id','periode','karyawanId','namaKaryawan','hariHadir','lemburJam','gajiPokok','uangLembur','potongan','total','statusBayar','updatedAt','deleted'],
  AuditLog: ['waktu','user','aksi','sheet','recordId','detail']
};
function doGet() {
  setup_();
  return HtmlService.createTemplateFromFile('Index').evaluate()
    .setTitle(APP_NAME).setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL);
}
function include_(name) { return HtmlService.createHtmlOutputFromFile(name).getContent(); }
function setup_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  Object.keys(SHEETS).forEach(name => {
    let sh = ss.getSheetByName(name);
    if (!sh) sh = ss.insertSheet(name);
    if (sh.getLastRow() === 0) {
      sh.getRange(1,1,1,SHEETS[name].length).setValues([SHEETS[name]]);
      sh.setFrozenRows(1);
      sh.getRange(1,1,1,SHEETS[name].length).setFontWeight('bold');
    } else {
      const current = sh.getRange(1,1,1,Math.max(sh.getLastColumn(),1)).getDisplayValues()[0];
      const missing = SHEETS[name].filter(h => !current.includes(h));
      if (missing.length) sh.getRange(1,current.length+1,1,missing.length).setValues([missing]);
    }
  });
  return {ok:true,spreadsheetId:ss.getId(),sheets:Object.keys(SHEETS)};
}
function api(action, payload) {
  setup_();
  payload = payload || {};
  switch (action) {
    case 'init': return {ok:true,app:APP_NAME,spreadsheetId:SpreadsheetApp.getActiveSpreadsheet().getId(),sheets:Object.keys(SHEETS),transactions:list_('Transaksi'),employees:list_('Karyawan'),attendance:list_('Absensi'),payroll:list_('Penggajian')};
    case 'saveTransaction': return save_('Transaksi', payload, 'id');
    case 'deleteTransaction': return softDelete_('Transaksi', payload.id);
    case 'saveEmployee': return save_('Karyawan', payload, 'id');
    case 'saveAttendance': return save_('Absensi', payload, 'id');
    case 'savePayroll': return save_('Penggajian', payload, 'id');
    case 'backup': return backup_();
    default: throw new Error('Aksi tidak dikenal: ' + action);
  }
}
function list_(sheetName) {
  const sh=SpreadsheetApp.getActiveSpreadsheet().getSheetByName(sheetName), values=sh.getDataRange().getValues();
  if(values.length<2) return [];
  const headers=values[0].map(String);
  return values.slice(1).map(row => Object.fromEntries(headers.map((h,i)=>[h, row[i] instanceof Date ? row[i].toISOString() : row[i]])))
    .filter(o=>String(o.deleted)!=='true');
}
function save_(sheetName, obj, idField) {
  if (!obj || typeof obj !== 'object') throw new Error('Data tidak valid');
  const lock=LockService.getScriptLock(); lock.waitLock(15000);
  try {
    const sh=SpreadsheetApp.getActiveSpreadsheet().getSheetByName(sheetName);
    const headers=sh.getRange(1,1,1,sh.getLastColumn()).getDisplayValues()[0];
    const id=String(obj[idField] || Utilities.getUuid());
    const now=new Date().toISOString();
    const data=Object.assign({},obj,{id:id,updatedAt:now,deleted:false});
    if(sheetName==='Transaksi') {
      if(!data.tanggal || !data.jenis || !Number.isFinite(Number(data.nominal)) || Number(data.nominal)<=0) throw new Error('Tanggal, jenis, dan nominal positif wajib diisi');
      data.nominal=Number(data.nominal);
      data.dibayar=Number(data.dibayar||0);
      if(data.dibayar<0 || data.dibayar>data.nominal) throw new Error('Pembayaran harus antara 0 dan nominal');
      data.sisa=data.nominal-data.dibayar;
      data.status=data.sisa===0?'LUNAS':(data.dibayar>0?'SEBAGIAN':'BELUM BAYAR');
    }
    const last=sh.getLastRow(); let target=0;
    if(last>1) {
      const idCol=headers.indexOf('id')+1;
      const ids=sh.getRange(2,idCol,last-1,1).getDisplayValues().flat();
      const index=ids.indexOf(id); if(index>=0) target=index+2;
    }
    const row=headers.map(h => data[h]===undefined ? '' : data[h]);
    if(target) sh.getRange(target,1,1,headers.length).setValues([row]);
    else sh.appendRow(row);
    audit_('SAVE',sheetName,id,JSON.stringify({jenis:data.jenis||'',nominal:data.nominal||'',status:data.status||''}));
    return {ok:true,id:id,record:data};
  } finally { lock.releaseLock(); }
}
function softDelete_(sheetName,id) {
  if(!id) throw new Error('ID wajib diisi');
  const sh=SpreadsheetApp.getActiveSpreadsheet().getSheetByName(sheetName), headers=sh.getRange(1,1,1,sh.getLastColumn()).getDisplayValues()[0];
  const ids=sh.getRange(2,headers.indexOf('id')+1,Math.max(sh.getLastRow()-1,1),1).getDisplayValues().flat();
  const i=ids.indexOf(String(id)); if(i<0) throw new Error('Data tidak ditemukan');
  const row=i+2; sh.getRange(row,headers.indexOf('deleted')+1).setValue(true); sh.getRange(row,headers.indexOf('updatedAt')+1).setValue(new Date().toISOString());
  audit_('SOFT_DELETE',sheetName,id,'Data dinonaktifkan, tidak dihapus permanen');
  return {ok:true};
}
function audit_(action,sheet,id,detail) {
  SpreadsheetApp.getActiveSpreadsheet().getSheetByName('AuditLog').appendRow([new Date(),Session.getActiveUser().getEmail()||'pengguna-terotorisasi',action,sheet,id,detail]);
}
function backup_() {
  const ss=SpreadsheetApp.getActiveSpreadsheet();
  const file=DriveApp.getFileById(ss.getId());
  const copy=file.makeCopy('Backup AyamKas Pro '+Utilities.formatDate(new Date(),Session.getScriptTimeZone(),'yyyy-MM-dd HHmmss'));
  audit_('BACKUP','Drive',copy.getId(),copy.getName());
  return {ok:true,fileId:copy.getId(),name:copy.getName(),url:copy.getUrl()};
}
