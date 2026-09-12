# GATE D.1.3 — SIMPLE ACCOUNTING UI CHECKPOINT

## 1. Existing Report Audit
- Evaluated `ReportRepository`, `ReportViewModel`, and `ReportsScreen` against the locked Gate D.1 return transactions and Gate D.1.1a historical HPP snapshot.
- Found that:
  - `SaleDao.getSalesTotal` computes gross sales across the period.
  - `SaleReturnDao.getSalesReturnTotal` computes return amounts across the period.
  - `SaleDao.getSaleCogsTotal` and `SaleReturnDao.getReturnCogsTotal` compute COGS directly using historical `purchase_price` snapshots saved in `sale_items` and `sale_return_items`.
  - Manual operating expenses are tracked in `cash_transactions` where `type = 'EXPENSE' AND ref_id IS NULL AND ref_uuid IS NULL`.
  - Inventory valuation is provided by `ProductDao.getTotalStockValue()`.
  - Customer debt is tracked via `DebtDao` and Supplier payables via `SupplierPayableDao`.
- No duplicate queries were created and Room schema remained strictly at **Version 11** with zero migrations.

---

## 2. Data Sources
- **Gross Sales**: `SaleDao.getSalesTotal(startDate, endDate)`
- **Sales Return**: `SaleReturnDao.getSalesReturnTotal(startDate, endDate)`
- **Net Sales**: Computed reactive flow `(Gross Sales - Sales Return)`
- **Sale COGS**: `SaleDao.getSaleCogsTotal(startDate, endDate)` (historical snapshot `quantity * purchase_price`)
- **Return COGS**: `SaleReturnDao.getReturnCogsTotal(startDate, endDate)` (historical snapshot `quantity * purchase_price`)
- **Net COGS**: Computed reactive flow `(Sale COGS - Return COGS)`
- **Gross Profit**: Computed reactive flow `(Net Sales - Net COGS)`
- **Operating Expense**: `CashDao.getOperatingExpenseTotal(startDate, endDate)` (`type = 'EXPENSE' AND ref_id IS NULL AND ref_uuid IS NULL`)
- **Net Profit**: Computed reactive flow `(Gross Profit - Operating Expense)`
- **Financial Position**:
  - Saldo Kas: `CashDao.getTotalCashBalance()`
  - Nilai Stok (Modal): `ProductDao.getTotalStockValue()`
  - Piutang Pelanggan: `DebtDao.getTotalOutstandingDebt()`
  - Hutang Supplier: `SupplierPayableDao.getTotalOutstandingPayable()`

---

## 3. Period Filter
- Maintained intuitive period filters:
  - **Hari Ini** (`ReportPeriod.TODAY`)
  - **7 Hari** (`ReportPeriod.LAST_7_DAYS`)
  - **Bulan Ini** (`ReportPeriod.THIS_MONTH`)
  - **Semua** (`ReportPeriod.ALL_TIME`)
- All income statement calculations (Penjualan Bruto, Retur, Penjualan Bersih, HPP, Laba Kotor, Operasional, Laba Bersih) reactively recalculate based on the selected period.
- Financial position metrics (Kas, Stok, Piutang, Hutang) reflect the authoritative current state.

---

## 4. Penjualan Bruto
- Displays the total revenue of all sales registered before deducting returns.
- Clearly formatted as currency (`Rp xxx`) in the expanded "Rincian Laba" card.

---

## 5. Retur
- Displays total refunded return value with explicit negative formatting (`-Rp xxx`).
- Shows return frequency (`x kali retur`) when returns exist.
- Does NOT treat returns as operational expenses.

---

## 6. Penjualan Bersih
- Formula: `Penjualan Bersih = Penjualan Bruto - Retur Penjualan`.
- Highlighted prominently as the top row of the Laba Rugi Sederhana card.

---

## 7. HPP (Modal Barang Terjual)
- Label: `"HPP / Modal Barang Terjual"` (with subtitle `"HPP Penjualan - HPP Retur"` in detailed breakdown).
- Uses locked historical snapshot from `sale_items` and `sale_return_items`.
- Guaranteed immunity against future changes to `Product.purchasePrice`.

---

## 8. Laba Kotor
- Formula: `Laba Kotor = Penjualan Bersih - HPP / Modal Terjual`.
- Displays in green (`AppColors.GreenPrimary`) when positive, and in red (`AppColors.RedExpense`) when negative (e.g. selling below cost).
- Not clamped to 0.

---

## 9. Pengeluaran Operasional
- Filters strictly manual operational expenses (listrik, air, gaji, plastik, sewa warung).
- Excludes:
  - Stock purchases (Cash / Tempo)
  - Supplier debt repayments
  - Sale return refunds
- Displays with negative indicator (`-Rp xxx`).

---

## 10. Laba Bersih
- Formula: `Laba Bersih = Laba Kotor - Pengeluaran Operasional`.
- Highlighted in a distinct card container with large typography.
- Displays negative values in red without artificial zero clamping.

---

## 11. Cash vs Profit Separation
- Dedicated **Posisi Keuangan** section clearly separates:
  - Saldo Kas $\ne$ Laba Bersih
  - Piutang Pelanggan $\ne$ Penjualan baru
  - Hutang Supplier $\ne$ Pengeluaran operasional
  - Nilai Stok $\ne$ Laba
- Includes explanatory note for warung owners: *"Catatan: Saldo kas dan nilai stok terpisah dari perhitungan laba usaha."*

---

## 12. No Double Counting Validation
- Debt repayment increases Cash and reduces Receivable, but does NOT inflate sales revenue.
- Supplier debt payment decreases Cash and reduces Payable, but does NOT enter operational expenses.
- Stock purchases adjust Inventory and Cash/Payable, but do NOT enter operational expenses.
- Return refunds decrease Gross Sales and restore Inventory, but do NOT double-count as operating expenses.
- QRIS sales do NOT falsely inflate physical cash drawer balance.

---

## 13. Tests
- Created `SimpleAccountingReportTest.kt` covering:
  1. `testAccountingExampleScenario`: Verified standard scenario (Initial Cash 500k, Sale 50k, Return 20k, Net Sales 30k, Net COGS 18k, Gross Profit 12k, Op Expense 5k, Net Profit 7k, Cash Balance 525k).
  2. `testHistoricalHppStabilityAfterPriceChange`: Verified COGS stability when `Product.purchasePrice` changes after transaction.
  3. `testNoDoubleCountingIntegrity`: Verified debt payments, supplier payments, stock purchases, and QRIS sales do not double-count.
  4. `testNegativeProfitCalculation`: Verified negative gross & net profit display without zero-clamping.
  5. `testFinancialPositionSeparation`: Verified distinction between cash balance and profit.
  6. `testReportsScreenUiElements`: Verified Compose UI nodes and "Rincian Laba" expansion.

---

## 14. AVD Manual Smoke
- Target Device: `Pixel_6_API_36` (`emulator-5554`).
- Verified screens & interactions:
  - `gate_d13_smoke_1.png`: Welcome / License screen.
  - `gate_d13_smoke_2.png`: Beranda with daily summary cards.
  - `gate_d13_smoke_3.png`: Reports screen with period filter chips and empty state notice.
  - `gate_d13_smoke_4.png`: Expanding "Rincian Laba" card.
  - `gate_d13_smoke_5.png`: Posisi Keuangan (Saldo Kas, Nilai Stok Modal, Piutang, Hutang).

---

## 15. Build
- Command: `./gradlew.bat assembleDebug assembleDebugAndroidTest`
- Result: **BUILD SUCCESSFUL**.

---

## 16. Regression
- Command: `./gradlew.bat testDebugUnitTest` -> **BUILD SUCCESSFUL**.
- Command: `connectedDebugAndroidTest` -> **229/229 PASS** (0 failures, 0 errors).
  - Previous: 223/223 PASS.
  - New test count: 229/229 PASS (+6 new accounting & UI tests).

---

## 17. Files Changed
### FILES CREATED
- [SimpleAccountingReportTest.kt](file:///e:/Android%20Project/BukuWarungKotlin/app/src/androidTest/java/id/skmnetwork/bukuwarung/SimpleAccountingReportTest.kt)
- [PHASE_GATE_D1_3_SIMPLE_ACCOUNTING_UI_CHECKPOINT.md](file:///e:/Android%20Project/BukuWarungKotlin/PHASE_GATE_D1_3_SIMPLE_ACCOUNTING_UI_CHECKPOINT.md)

### FILES MODIFIED
- [CashDao.kt](file:///e:/Android%20Project/BukuWarungKotlin/app/src/main/java/id/skmnetwork/bukuwarung/data/local/dao/CashDao.kt)
- [ProductDao.kt](file:///e:/Android%20Project/BukuWarungKotlin/app/src/main/java/id/skmnetwork/bukuwarung/data/local/dao/ProductDao.kt)
- [ReportRepository.kt](file:///e:/Android%20Project/BukuWarungKotlin/app/src/main/java/id/skmnetwork/bukuwarung/data/repository/ReportRepository.kt)
- [ReportViewModel.kt](file:///e:/Android%20Project/BukuWarungKotlin/app/src/main/java/id/skmnetwork/bukuwarung/ui/report/ReportViewModel.kt)
- [ReportsScreen.kt](file:///e:/Android%20Project/BukuWarungKotlin/app/src/main/java/id/skmnetwork/bukuwarung/ui/report/ReportsScreen.kt)

### FILES UNCHANGED
- Room schema entities & migrations (Database strictly Version 11 locked).
- `SaleRepository.kt` transaction & return backend logic.
- `GoogleSheetsApiTransport.kt` / BackupRestoreManager (19 canonical tabs locked).
- Bluetooth printer manager & POS cart engine.

---

## 18. Remaining Limitations
- Historical HPP for legacy sales recorded prior to Room v11 uses migration fallback (`0` or initial `Product.purchasePrice` at migration time) as documented in Gate D.1.1a.

---

## FINAL VERDICT
**PASS — READY FOR LOCK**
