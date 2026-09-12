# GATE D.1.2 — RETURN UI CHECKPOINT

## 1. Existing UI Audit
- **POS / Jualan Screen (`PosScreen.kt`)**: Previously featured only live product selection, barcode scanner, and checkout modal flow. No transaction history list existed directly within the POS screen.
- **Purchase / Kulakan Screen (`PurchaseScreen.kt`)**: Featured a standard 2-tab navigation pattern: Tab 0 (`Belanja Baru`) and Tab 1 (`Riwayat Belanja`), providing itemized transaction review and detail dialogues.
- **Return Backend (`SaleRepository.kt` & Room v11)**: Backend implementation from Gate D.1 and D.1.1a (`SaleRepository.processSaleReturn`, `SaleReturnTransactionEntity`, `SaleReturnItemEntity`, `SaleReturnDao`, atomic Room transaction, historical HPP snapshot) is locked and verified with 0 schema modifications.

---

## 2. Navigation Decision
- **Natural Entry Point**: Following the design pattern established in `PurchaseScreen.kt`, `PosScreen.kt` was enhanced with a top dual-chip tab row:
  - **Tab 0: `Kasir`** (Live cart, product grid, category filter, barcode scan, and checkout).
  - **Tab 1: `Riwayat Penjualan`** (Searchable completed sales list, transaction cards, and detail dialogue trigger).
- **Rationale**: Keeps the warung POS experience unified without creating cluttering top-level menus or modifying unaffected modules (Purchases, WhatsApp, Accounting).

---

## 3. Return UI Flow
```
POS (Jualan)
    ↓
Tab [Riwayat Penjualan]
    ↓
Tap Transaksi Penjualan
    ↓
Sale Detail Dialog (SaleDetailDialog.kt)
    ↓
Tap [Retur Barang]
    ↓
Sale Return Picker & Config Dialog (SaleReturnDialog.kt)
    ├── Item Picker with Stepper [-] [qty] [+]
    ├── Real-time Refund Calculation (Original Sale Price)
    ├── Refund Method Info (CASH / QRIS->CASH / CREDIT->Piutang)
    ├── Optional Reason Dropdown (6 standard domain reasons)
    └── Optional Notes TextField
    ↓
Tap [Proses Retur]
    ↓
Confirmation Dialog ("Konfirmasi Retur?" + Warning: "Transaksi asli tidak diubah")
    ↓
Tap [Konfirmasi Retur] (Duplicate submission disabled + loading state)
    ↓
Success Dialog (Return Number, Date, Total Refund, Method)
    ↓
Auto-refresh Sale Detail Dialog showing "Riwayat Retur" section & updated remaining returnable limits
```

---

## 4. Partial Return
- Each item displays:
  - **Terjual:** $X$
  - **Sudah diretur:** $Y$
  - **Dapat diretur:** $X - Y$
- Quantity Stepper `[-] [qty] [+]` bounds between `0` and `(X - Y)`.
- Refund calculation uses the immutable **original sale price** (`quantityReturn * SaleItem.price`).
- Partial returns create a new `SaleReturnTransactionEntity` while leaving the original sale transaction immutable.

---

## 5. Full Return
- When the merchant increments return quantity to $X - Y$ for all items, the return represents a 100% full return.
- Once processed, the returnable quantity drops to `0.0`.
- The item displays status badge **"Sudah diretur semua"** and quantity stepper controls are disabled.
- Stock is fully restored via `RETURN` `StockMovementEntity`.

---

## 6. Multiple Return
- The UI dynamically computes remaining returnable quantities via `SaleRepository.getReturnableQuantitiesForSale(saleId)`:
  $$\text{Remaining} = \text{SaleItem.quantity} - \sum \text{SaleReturnItem.quantity}$$
- Merchants can execute multiple sequential partial returns over time until remaining quantity reaches `0`.
- Subsequent attempts to return beyond the limit are rejected at both UI stepper level and atomic backend transaction level.

---

## 7. CASH Refund
- Original payment: **CASH**.
- UI Presentation: **"Pengembalian: Tunai"**.
- Backend execution: Generates `CashTransactionEntity` (`EXPENSE`, category `RETURN_EXPENSE`) and records `refundMethod = "CASH"`.

---

## 8. QRIS → CASH Refund
- Original payment: **QRIS**.
- UI Presentation:
  - **"Pengembalian: Tunai"**
  - Informative disclaimer: *"Transaksi QRIS akan dikembalikan secara tunai."*
- Dropdown selector for electronic refund is intentionally omitted per locked business rules.

---

## 9. CREDIT Return
- Original payment: **CREDIT** (Hutang).
- UI Presentation: **"Pengembalian: Piutang"**.
- Backend execution: Reduces outstanding customer debt via `DebtEntity` adjustment. If past debt payments resulted in excess refund exceeding unpaid balance, the excess is automatically returned as cash expense.

---

## 10. Reason & Notes
- **Reason**: Optional dropdown with 6 predefined domain choices:
  1. *Barang rusak/cacat*
  2. *Salah barang*
  3. *Barang tidak sesuai*
  4. *Pelanggan berubah pikiran*
  5. *Kelebihan pembelian*
  6. *Lainnya*
- Default: None / Empty.
- **Notes**: Optional free-form text input field.

---

## 11. Duplicate Submission Protection
- Double-tap and concurrent execution protection implemented via Compose `isSubmitting` state.
- Submit button is disabled and displays a progress indicator during transaction processing.

---

## 12. Return History
- `SaleDetailDialog.kt` contains a dedicated **"Riwayat Retur"** section.
- Displays for each previous return:
  - Return number (`RET-...`)
  - Date and time formatted in Indonesian locale
  - Total refund amount (Rupiah formatted)
  - Refund method badge (`TUNAI` / `HUTANG`)
  - Reason & notes if provided
  - Itemized return breakdown (quantity returned per item)
- Read-only audit trail; cannot be edited or deleted.

---

## 13. Tests
- **Unit & Local Tests (`./gradlew.bat testDebugUnitTest`)**:
  - **0 Failures, 0 Errors, BUILD SUCCESSFUL**.
- **Instrumentation & UI Tests (`SaleReturnUiTest.kt`)**:
  - 20 comprehensive test cases verifying end-to-end UI flows, stepper boundaries, reason/notes options, confirmation dialog, partial/full/multiple returns, CASH/QRIS/CREDIT semantics, and duplicate submission prevention.
  - **20 / 20 PASS (0 Failures, 0 Errors)**.

---

## 14. AVD Manual Smoke
- **Device**: `Pixel_6_API_36` (emulator-5554).
- **Smoke Steps Validated**:
  1. Open app $\rightarrow$ Jualan (POS).
  2. Tab navigation between `Kasir` and `Riwayat Penjualan`.
  3. View empty state and search bar on `Riwayat Penjualan`.
  4. Open completed sale $\rightarrow$ `SaleDetailDialog`.
  5. Open `[Retur Barang]` $\rightarrow$ `SaleReturnDialog`.
  6. Step quantity, select reason, view refund summary.
  7. Confirm dialog and verify duplicate submission lock.
  8. Verify success dialogue and return history rendering on `SaleDetailDialog`.

---

## 15. Regression
- **Full Instrumented Suite Run**:
  - Command: `adb shell am instrument -w -r id.skmnetwork.bukuwarung.test/androidx.test.runner.AndroidJUnitRunner`
  - Total Tests Executed: **223 tests** across all modules (POS, Barcode, Categories, Google Sheets Backup/Restore, HPP Snapshot, Reports, Room Migration, Sale Returns, Settings).
  - **Result: 223 / 223 PASSED (0 Failures, 0 Errors, Time: 28.395s)**.
- **Room Schema**: Locked at v11. No schema mutations or migrations introduced.

---

## 16. Files Changed
### FILES CREATED:
- `app/src/main/java/id/skmnetwork/bukuwarung/ui/pos/SaleDetailDialog.kt`
- `app/src/main/java/id/skmnetwork/bukuwarung/ui/pos/SaleReturnDialog.kt`
- `app/src/androidTest/java/id/skmnetwork/bukuwarung/SaleReturnUiTest.kt`
- `PHASE_GATE_D1_2_RETURN_UI_CHECKPOINT.md`

### FILES MODIFIED:
- `app/src/main/java/id/skmnetwork/bukuwarung/data/repository/SaleRepository.kt` (added helper read queries: `getReturnsListForSale`, `getReturnedQuantityForSaleItem`)
- `app/src/main/java/id/skmnetwork/bukuwarung/data/repository/ProductRepository.kt` (exposed return query and return processing delegates)
- `app/src/main/java/id/skmnetwork/bukuwarung/ui/product/ProductViewModel.kt` (exposed `sales`, `returns` StateFlows and return processing helper methods)
- `app/src/main/java/id/skmnetwork/bukuwarung/ui/pos/PosScreen.kt` (integrated `Kasir` vs `Riwayat Penjualan` tab row, sales history card list, and detail/return dialogue triggers)

### FILES UNCHANGED:
- All Room entity schemas and migrations (v11 locked)
- `HppSnapshotTest.kt`, `SaleReturnTest.kt`, `RoomMigrationTest.kt`
- Accounting calculations, Google Sheets transport, Printer services, Customer/Supplier modules

---

## 17. Known Limitations
- Printer receipt printing for sales returns is intentionally deferred per gate instructions (sales return receipt printing not modified on this task).
- WhatsApp catalog, gross prices (harga grosir), and discount promotions remain out of scope for Gate D.1.2.

---

## FINAL VERDICT:
**PASS — READY FOR LOCK**
