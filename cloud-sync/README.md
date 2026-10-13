# AyamKas Pro — Remote Control & Google Sheets Sync

This branch adds an Android sync panel and a spreadsheet-bound Google Apps Script bridge. It preserves the Room database as the local ledger so normal transaction entry continues offline. No third-party Add-on is required.

## What this change does

- Android **Cadangan** screen has URL/key settings, manual sync, optional auto-sync while the app is open, last-sync status, and a cloud spreadsheet backup button.
- Sync sends transactions and deletion markers with stable IDs. Google Sheets merges records by ID and \`updatedAt\`; newer timestamps take precedence. Deleted records are soft-deleted in Sheets rather than permanently erased.
- Spreadsheet menu **AyamKas Pro** creates/refreshes the ledger sheets, creates a sync key, recalculates reports, and creates a Drive copy.
- \`Ringkasan\`, \`KartuDO\`, and \`Validasi\` use live sheet formulas and highlight incomplete or inconsistent data.

## Setup: Google Sheets / Apps Script

Use a **new blank test spreadsheet** first. Do not paste this script into a live accounting sheet with a different column structure without making a backup and testing a copy.

1. Open the test spreadsheet.
2. Select **Ekstensi → Apps Script**.
3. Replace the editor content with \`cloud-sync/Code.gs\` from this repository and save.
4. Return to the spreadsheet and reload it. A custom menu **AyamKas Pro** should appear. Select **Siapkan / Periksa Lembar** and accept the requested Google permissions.
5. Select **AyamKas Pro → Buat / Ganti Kunci Sinkronisasi**. Copy the generated key; creating a new key invalidates the previous key.
6. Select **Deploy → New deployment → Web app**. Deploy as the spreadsheet owner. The Android HTTP client needs a deployment setting that permits its POST request without an interactive Google sign-in. Where Google offers anonymous access, use it only for this owner-only setup and keep both the URL and generated key private. Some Workspace administrators disable anonymous access; in that case this lightweight client needs a proper Google OAuth flow instead and will not work by simply pasting the URL.
7. Copy the deployed Web App URL that ends in \`/exec\`.
8. In AyamKas Pro Android, open **Cadangan**, paste the \`/exec\` URL and key, choose whether to enable auto-sync, then press **Sinkronkan Sekarang**. Test add/edit/verify/delete on the test spreadsheet before connecting an operational dataset.

The endpoint's \`doGet\` response is a JSON health check. \`doPost\` accepts \`ping\`, \`sync\`, and \`backup\` actions. This is an Apps Script deployment, not a Google Sheets Add-on: no Add-on marketplace installation is required.

## Calculation flow and meaning

1. **DO purchase:** one purchase record needs a stable \`ref\` (DO number), purchase amount, paid amount, and kg.
2. **Sales/omzet:** record sale amount, collected amount (\`paidAmount\`), quantity sold (\`qtyKg\`), and link the sale to its source DO through \`batchRef\`.
3. **Cash movement:** calculated from confirmed transactions and money actually received/paid. Empty \`paidAmount\` is treated as Rp0 by both Android and Sheets; the \`Validasi\` sheet warns when the field is empty. Enter the actual payment amount, use \`0\` for fully-credit transactions, and enter the full amount only when fully paid/collected. Do not leave payment blank to mean fully paid.
4. **Stock and HPP per DO:** \`KartuDO\` groups purchase rows that use the same DO reference and calculates total purchased kg/cost, weighted HPP per purchased kg, linked sold kg, remaining kg, and remaining stock value. Negative stock, missing kg, zero/invalid cost, and unlinked sales are flagged. A DO reference must identify the same physical batch; do not reuse it for a new delivery.
5. **Profit:** omzet − estimated HPP of linked sales − operating expenses. Ringkasan labels the result **temporary** while HPP validation warnings exist.
6. **Backup:** Drive backup copies the spreadsheet only. The app's **Ekspor JSON** remains the local phone backup.

## Known accounting limits — do not skip these

- The current HPP formula assumes purchased kg and sold kg use the **same weight basis**. If the DO weight is live weight and sales are dressed/carcass weight, the business must record output/saleable kg per DO (and processing cost, weight loss, and by-products) before profit can be called final. No standard yield percentage is filled automatically because it must come from measured business data.
- Receivables and supplier payables currently show aggregate estimates. To reconcile each customer/supplier invoice exactly, collections and payments need a required invoice/source reference. Do not treat aggregate estimates as an invoice aging report.
- Android sync includes cash accounts, inter-account transfers, loan records, employees, attendance and payroll in their own spreadsheet tabs. Test every table on a blank spreadsheet before use; payroll confirmation posts its approved total as a salary expense, with the paid amount controlling the cash movement.
- Auto-sync runs after local edits and when the app opens **while the app is active and online**. It is not a promise of 24-hour background sync while Android force-stops the app.
- The key is stored in app preferences so it does not need to be hard-coded in the APK. Anyone who obtains both the URL and key may be able to write to this spreadsheet. Do not share the key; use proper Google OAuth for multi-user/public deployments.
- Timestamp-based conflict resolution is intentionally simple (newer \`updatedAt\` wins); it depends on correctly set device clocks. Review the last-sync status and spreadsheet audit log after use.

## Research references

- Apps Script Web Apps: https://developers.google.com/apps-script/guides/web
- Web App access and execution identity: https://developers.google.com/apps-script/manifest/web-app-api-executable
- Google Sheets API quotas/limits: https://developers.google.com/workspace/sheets/api/limits
- AppSheet offline behavior (alternative with different licensing/configuration): https://support.google.com/appsheet/answer/10107724



## Operational control sequence (recommended)

1. **Opening control:** enter opening cash and bank balances as starting balances; keep owner capital and operational income as distinct transaction types. Reconcile the opening amount against actual cash/bank statements.
2. **Receive each DO:** record one stable DO reference, date, supplier, invoice/DO amount, amount paid, unpaid balance, and purchased weight. Use one reference for all rows that belong to the same delivery only.
3. **Record cutting/production yield before final HPP:** record live/input kg, saleable carcass/output kg, weight loss, by-products, direct cutting labor, packaging, and other conversion cost per batch. If the source data does not record the output yield, the system must mark HPP/profit as incomplete rather than invent a yield percent.
4. **Record sales by batch:** enter invoice number, customer, sale date, saleable kg, gross sale value, amount collected, and source DO/batch. Partial customer receipts must be linked to that invoice to support accurate aging.
5. **Record payment and collection events:** for every amount paid/received after the original invoice, record a separate payment transaction linked to the supplier bill or customer invoice. Until those links are required and validated, payable/receivable numbers remain aggregate estimates, not invoice-by-invoice aging.
6. **Record operating costs once:** enter cutting labor, packaging, transport, utilities, admin, other operating costs, and wages; classify costs consistently so direct conversion costs are not counted both inside HPP and again as operating expense.
7. **Daily close:** compare app cash balance with actual cash and bank balances; compare physical stock with each DO card; check negative stock, missing payments, missing batch references, unverified records, and unsold kilograms. Resolve validation warnings before accepting profit as final.
8. **Back up both sides:** backup the spreadsheet to Drive and separately export local Android JSON. Test restoring each backup before deleting or resetting data.

## Editing the ledger directly in Google Sheets

Editing cells in the managed \`Transaksi\` sheet is also supported. The bound Apps Script stamps the row with a new \`updatedAt\` and source \`sheets\`; changing business data marks the row as \`draft\` and refreshes formulas/validation. To add a row directly in Sheets, fill the date, transaction type, and positive amount; a stable ID is generated once those required fields exist. The row starts as a draft. Review it and explicitly set its status back to \`confirmed\` after checking it. The next Android sync pulls the newer version. Do not edit generated report formulas by hand; use the \`AyamKas Pro → Hitung Ulang Ringkasan & Validasi\` menu if needed.

## Important scope boundary

This deployment synchronizes **transaction and auxiliary ledger data** between Android and Sheets. Invoice-level receivable/payable aging still requires each collection/payment to carry a required source invoice or DO reference. The current batch model treats mortality as a percentage of input weight and must use the same weight basis for purchases and sales; if purchases are live-weight and sales are carcass-weight, measured saleable carcass output and co-product/by-product allocations must be added before HPP is considered final. For this reason the current system must label profit as preliminary whenever cost/yield/payment validation remains unresolved.

## Validation checklist before real use

- [ ] Setup on a separate blank spreadsheet.
- [ ] Key generated and kept private.
- [ ] URL ends in \`/exec\` and sync status reports success.
- [ ] Add one draft transaction; confirm that draft does not affect summaries.
- [ ] Verify a DO and link a sale by \`batchRef\`; check stock/HPP formulas.
- [ ] Test partial payment, fully unpaid transaction (\`paidAmount=0\`), and received/bank payments.
- [ ] Delete a record on Android and verify the matching spreadsheet row is marked deleted and excluded from reports.
- [ ] Make a newer cloud edit and verify the next sync does not overwrite it with an older local copy.
- [ ] Open a Drive backup and separately export local JSON.
- [ ] Resolve every \`Validasi\` warning and reconcile cash/stock physically before treating the profit figure as final.
