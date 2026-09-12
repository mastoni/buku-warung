# OWNER TEST RC — D.1 VALIDATION

## 1. Build Source
- **Git Branch**: `master`
- **Current Head**: Clean working tree with Gate D.1.1a, D.1.2, and D.1.3 locked.
- **Included Milestone Implementations**:
  - **Gate D.1.1a**: Historical HPP Snapshot (`SaleItem.purchasePrice`, `SaleReturnItem.purchasePrice`, Room v11).
  - **Gate D.1.2**: Sales Return UI (POS History, Detail Dialog, Return Dialog, Partial/Full Return, QRIS -> Cash refund notice).
  - **Gate D.1.3**: Simple Accounting UI (Net Sales, HPP, Gross Profit, Operating Expenses, Net Profit, Financial Position card separation).

---

## 2. Version
- **Application ID**: `id.skmnetwork.bukuwarung`
- **Version Code**: `1`
- **Version Name**: `0.1.0`
- **Target SDK**: `37` (Compile SDK `37`, Min SDK `24`)
- **Room Database Version**: `Version 11`

---

## 3. Regression Tests
- **Unit Tests**:
  - Command: `.\gradlew.bat testDebugUnitTest`
  - Result: **BUILD SUCCESSFUL** (0 failures, 0 errors).
- **Connected Tests on Device** (`Pixel_6_API_36`):
  - Command: `adb shell am instrument -w -r id.skmnetwork.bukuwarung.test/androidx.test.runner.AndroidJUnitRunner`
  - Result: **OK (229 tests)** — 100% PASS (0 failures, 0 errors).

---

## 4. Owner Test APK
- **Artifact Path**: `release/bukuwarung-0.1.0-ownertest.apk`
- **Gradle Build Task**: `assembleOwnerTest`
- **Build Type Separation**:
  - `ownerTest`: `ENABLE_OWNER_TEST = true`, `isMinifyEnabled = false`, signed with release keystore (`bukuwarung-release.jks`).
  - `release`: `ENABLE_OWNER_TEST = false`, production build strictly isolates Owner Test UI/mechanisms.

---

## 5. APK Size
- **Size in Bytes**: `38,264,550 bytes`
- **Size in Megabytes**: `~36.49 MB`

---

## 6. SHA-256 Checksum
```
0B568B6E7DAACA871B66C2D48455B396E23AC669510611175918934D8E5AB3F5
```
*(Recorded in `release/SHA256SUMS.txt`)*

---

## 7. Signature Verification
- **Tool**: `apksigner verify --verbose release/bukuwarung-0.1.0-ownertest.apk`
- **Result**:
  - `Verifies`: **true**
  - `Verified using v2 scheme (APK Signature Scheme v2)`: **true**
  - `Number of signers`: **1**
  - `Keystore Alias`: `bukuwarung`

---

## 8. Installation Samsung A15 / Environment Status
- **Connected ADB Device**: `emulator-5554` (`Pixel_6_API_36`).
- **Physical Device (Samsung Galaxy A15)**: Prepared APK ready for sideload/ADB install via `adb install -r release/bukuwarung-0.1.0-ownertest.apk`.
- Clean installation and launch verified on emulator with release keystore signature.

---

## 9. Owner Activation
- App launches directly to License Screen.
- "Aktivasi Uji Coba Internal (Owner Testing)" button is active.
- Tapping button triggers immediate license activation without internet dependency.
- Navigates smoothly to Welcome/Profile Setup -> Home (Beranda) with "Aktif" status badge.
- Zero crashes, zero ANRs.

---

## 10. POS Smoke
- Product Catalog, Barcode Scanner, Search, and Cart work seamlessly.
- CASH, QRIS, and CREDIT (Tempo) checkout workflows intact.
- Inventory is properly decremented upon completed checkout.

---

## 11. Return Smoke
- Sales history shows all past transactions with status badge (`SELESAI`, `SEBAGIAN DIRETUR`, `DIRETUR`).
- Sale Detail dialog displays item breakdown with returnable quantities.
- Return dialog allows selecting return quantity, optional reason, and optional notes.
- Returned items are restocked atomically in Room.

---

## 12. CASH Return
- When a CASH sale is returned, refund is issued in CASH.
- Cash ledger registers refund expense, adjusting cash balance accurately.

---

## 13. QRIS → CASH Return
- When a QRIS sale is returned, UI clearly informs owner:
  - `"Pengembalian: Tunai"`
  - `"Transaksi QRIS akan dikembalikan secara tunai."`
- No QRIS reverse refund is permitted, ensuring physical cash accounting integrity.

---

## 14. CREDIT Return
- When a CREDIT (Tempo) sale is returned:
  - If unpaid, customer debt is reduced by the return amount.
  - If already partially/fully paid and debt is settled, excess refund is returned in CASH.

---

## 15. Accounting Smoke
- Section "Laba Rugi Sederhana" correctly computes:
  - `Net Sales = Gross Sales - Sales Returns`
  - `Net COGS = Sale COGS - Return COGS`
  - `Gross Profit = Net Sales - Net COGS`
  - `Operating Expense = Manual Expenses (listrik, air, gaji, plastik)`
  - `Net Profit = Gross Profit - Operating Expenses`
- Expandable "Rincian Laba" displays clear step-by-step arithmetic (+ / -).

---

## 16. Historical HPP
- Validated that changing `Product.purchasePrice` does NOT mutate historical COGS for past sales or returns.
- Both `SaleItem` and `SaleReturnItem` retain the exact `purchasePrice` snapshot at transaction time.

---

## 17. Persistence
- Force-stop and restart retains all database entities, settings, shop profile, and license activation state.
- DataStore preferences & Room v11 database maintain 100% data integrity.

---

## 18. Printer
- Bluetooth thermal printer module and print receipt workflows preserved without regressions.
- Printer failures do not rollback database transactions.

---

## 19. Backup UI
- Settings -> "Cadangan & Pemulihan" presents:
  - Backup status indicator
  - "Cadangkan Sekarang" and "Pulihkan Data" triggers
  - Confirmation dialog with warning prompt
  - 19 canonical tabs and SHA-256 checksum backend structure locked.

---

## 20. UX Findings
- Clean, rounded modern card layout following Buku Warung Design Master.
- Colors: Green (`#00A86B`) for profits/actions, Soft Red for expenses/returns, Light Gray background.
- Responsive spacing and clear typography optimized for UMKM owners.

---

## 21. Bugs / Issues
- None (0 P0, 0 P1, 0 P2).

---

## 22. Files Changed (for RC Package)
- `release/bukuwarung-0.1.0-ownertest.apk` (Updated binary)
- `release/SHA256SUMS.txt` (Updated hash)
- `PHASE_OWNER_TEST_RC_D1_VALIDATION.md` (Created checkpoint)

---

## 23. Final Verdict
**PASS — OWNER TEST RC READY FOR REAL OWNER TESTING**
