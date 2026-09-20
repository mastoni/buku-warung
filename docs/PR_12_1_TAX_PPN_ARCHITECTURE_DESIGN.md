# PR-12.1 — TAX / PPN ARCHITECTURE DESIGN

**Project:** Buku Warung Android  
**Repository:** mastoni/buku-warung  
**Design Date:** 2026-09-20  
**Designer:** Kilo  
**Baseline:** PR-12 Audit (`docs/PR_12_TAX_PPN_CONFIGURATION_ARCHITECTURE_AUDIT.md`), PR-11.2 locked at `5592cd9`  

---

## 1. EXECUTIVE SUMMARY

Design ini mengdefinisikan arsitektur Tax/PPN Configuration untuk Buku Warung sebagai domain yang sepenuhnya independen dari Business Type, Capability, dan License.

Prinsip utama:
- Tax Configuration adalah domain konfigurasi tersendiri.
- Current Tax Configuration **tidak pernah** mengubah Historical Transactions.
- Setiap transaksi menyimpan tax snapshot yang immutable.
- Product dapat memiliki tax policy override per-item.
- Semua monetary values tetap menggunakan `Long` (integer Rupiah).
- Backup/restore tetap kompatibel dengan legacy transactions tanpa tax data.

---

## 2. DESIGN GOALS

1. **Historical Immutability**: Perubahan setting PPN tidak pernah mengubah transaksi lama.
2. **Independent Domain**: Tax Configuration terpisah dari Business Type dan Capability.
3. **Deterministic Calculation**: Semua perhitungan pajak reproducible dan auditable.
4. **Backward Compatibility**: Legacy transactions (tanpa tax) tetap valid.
5. **Mixed Transaction Support**: Transaksi dapat berisi produk taxable dan non-taxable.
6. **Integer Arithmetic**: Semua monetary values menggunakan `Long` untuk menghindari floating point error.
7. **Backup Compatibility**: PR-11.2 backup dapat di-extend tanpa memutuskan compatibility.

---

## 3. NON-GOALS

1. **Tax/PPN implementation** — design only.
2. **Room schema migration** — design only.
3. **DataStore migration** — design only.
4. **UI implementation** — design only.
5. **Transaction engine changes** — design only.
6. **Report/PDF/Receipt implementation** — design only.
7. **Backup implementation changes** — design only.
8. **Digital/PPOB provider-specific tax** — provider belum dipilih.
9. **Purchase Tax (PPN Masukan) accounting** — dibiarkan sebagai product decision.
10. **Tax filing/reporting to government** — out of scope.

---

## 4. EXISTING ARCHITECTURE BASELINE

### 4.1 Current State (from PR-12 Audit)
- Room schema version: **15**
- No tax fields in any entity
- No tax keys in DataStore/UserSettings
- No tax UI
- No tax calculation engine
- No tax display in receipt, PDF, or reports
- Monetary values: **Long** (integer Rupiah)
- Transaction total flow: `grossSubtotal → discount → netTotal → totalAmount`
- No tax snapshot in historical transactions

### 4.2 Existing Architecture Layers
```
LICENSE
  ↓
ENTITLEMENTS
  ↓
BUSINESS PROFILE
  ↓
BUSINESS TYPE (identity/orientation)
  ↓
CAPABILITIES (active features)
  ↓
PRODUCT / POS / UI
```

Tax Configuration akan ditambahkan sebagai layer baru:
```
BUSINESS PROFILE
  ↓
TAX CONFIGURATION (new, independent domain)
  ↓
TAX CALCULATION
  ↓
TRANSACTION TAX SNAPSHOT
  ↓
REPORT / DOCUMENT / BACKUP
```

### 4.3 Key Insight
Business Type dan Capability sudah terpisah dari tax concerns. Tax Configuration harus ditambahkan sebagai domain baru yang juga independen.

---

## 5. TAX DOMAIN MODEL

### 5.1 Ownership
Tax Configuration dimiliki oleh **Business Profile**, bukan oleh Business Type atau Capability.

### 5.2 Scope
Tax Configuration mengatur:
- Apakah pajak aktif untuk usaha ini
- Rate pajak yang berlaku
- Mode harga (inclusive/exclusive)
- Applicability policy (global/per-product/per-transaction)
- Rounding behavior

### 5.3 Independence
Perubahan Tax Configuration:
- **Tidak** mengubah Business Type
- **Tidak** mengubah Capability set
- **Tidak** mengubah Product catalog
- **Hanya** mempengaruhi transaksi baru yang dibuat setelah perubahan

---

## 6. CONFIGURATION MODEL

### 6.1 TaxSettings (DataStore)

```kotlin
data class TaxSettings(
    val enabled: Boolean = false,
    val rate: Double = 0.0,
    val priceMode: TaxPriceMode = TaxPriceMode.EXCLUSIVE,
    val applicability: TaxApplicability = TaxApplicability.GLOBAL,
    val roundingMode: RoundingMode = RoundingMode.HALF_UP,
    val effectiveDate: Long = 0L
)

enum class TaxPriceMode {
    EXCLUSIVE,  // PPN ditambahkan ke harga
    INCLUSIVE   // PPN sudah termasuk di harga
}

enum class TaxApplicability {
    NONE,       // Tidak ada pajak
    GLOBAL,     // Semua produk kena pajak
    PRODUCT     // Per-product override
}
```

### 6.2 DataStore Keys

```kotlin
object Keys {
    // Existing keys...
    
    // Tax Configuration
    val TAX_ENABLED = booleanPreferencesKey("tax_enabled")
    val TAX_RATE = doublePreferencesKey("tax_rate")
    val TAX_PRICE_MODE = stringPreferencesKey("tax_price_mode")
    val TAX_APPLICABILITY = stringPreferencesKey("tax_applicability")
    val TAX_ROUNDING_MODE = stringPreferencesKey("tax_rounding_mode")
    val TAX_EFFECTIVE_DATE = longPreferencesKey("tax_effective_date")
}
```

### 6.3 Defaults
- `enabled`: `false` (PPN OFF by default)
- `rate`: `0.0` (0% when disabled)
- `priceMode`: `EXCLUSIVE` (standar Indonesia)
- `applicability`: `GLOBAL` (semua produk kena pajak kecuali di-override)
- `roundingMode`: `HALF_UP` (pembulatan standar)
- `effectiveDate`: `0L` (belum pernah diubah)

### 6.4 No Room Entity Required
Tax Configuration **hanya** disimpan di DataStore. Tidak perlu Room entity karena:
- Ini adalah setting aplikasi, bukan data transaksi
- Tidak perlu query complextax history configuration
- Backup akan menyimpan tax settings di metadata

### 6.5 Configuration History
**DECISION:** Configuration history **TIDAK DIPERLUKAN** untuk versi pertama.
- Transaction snapshot sudah menyimpan `taxRateSnapshot`
- Jika perlu audit perubahan setting, bisa ditambahkan kemudian

---

## 7. PRODUCT TAX POLICY

### 7.1 ProductEntity Extension

```kotlin
@Entity(tableName = "products")
data class ProductEntity(
    // existing fields...
    val taxable: Boolean = true,        // NEW: default taxable
    val taxRateOverride: Double? = null // NEW: null = use global rate
)
```

### 7.2 Tax Policy Values

| Policy | taxable | taxRateOverride | Behavior |
|--------|---------|-----------------|----------|
| DEFAULT_TAXABLE | `true` | `null` | Use global rate |
| TAXABLE_CUSTOM_RATE | `true` | `11.0` | Use override rate |
| NON_TAXABLE | `false` | `null` | Tax = 0 |

### 7.3 Business Type Independence
- Semua Business Type dapat memiliki produk taxable dan non-taxable
- Tidak ada Business Type yang secara otomatis mengatur tax policy
- User mengatur tax policy per-product melalui Product UI

### 7.4 Capability Independence
- Tax policy tidak bergantung pada Capability
- Tax calculation hanya dijalankan jika `TaxSettings.enabled == true`
- Capability tetap mengontrol fitur lain (inventory, barcode, dll.)

---

## 8. TRANSACTION SNAPSHOT MODEL

### 8.1 Immutable Tax Snapshot Principle

**MANDATORY:** Setiap transaksi harus menyimpan semua nilai pajak yang relevan pada saat transaksi dibuat. Perubahan Tax Configuration setelah transaksi dibuat **tidak boleh** mempengaruhi transaksi tersebut.

### 8.2 SaleTransactionEntity Extension

```kotlin
@Entity(tableName = "sales_transactions")
data class SaleTransactionEntity(
    // existing fields...
    val subtotalAmount: Long = 0L,           // NEW: gross sebelum discount
    val taxableBaseSnapshot: Long = 0L,      // NEW: amount yang kena pajak
    val taxRateSnapshot: Double = 0.0,       // NEW: rate pada saat transaksi
    val taxAmountSnapshot: Long = 0L,        // NEW: jumlah pajak
    val totalAmount: Long                    // EXISTING: subtotal - discount + tax
)
```

### 8.3 SaleItemEntity Extension

```kotlin
@Entity(tableName = "sale_items")
data class SaleItemEntity(
    // existing fields...
    val taxable: Boolean = true,             // NEW: product tax policy saat transaksi
    val taxRateSnapshot: Double? = null,     // NEW: rate yang berlaku untuk item ini
    val taxAmountSnapshot: Long? = null      // NEW: tax amount untuk item ini
)
```

### 8.4 PurchaseTransactionEntity Extension

```kotlin
@Entity(tableName = "purchase_transactions")
data class PurchaseTransactionEntity(
    // existing fields...
    val subtotalAmount: Long = 0L,           // NEW
    val taxableBaseSnapshot: Long = 0L,      // NEW
    val taxRateSnapshot: Double = 0.0,       // NEW
    val taxAmountSnapshot: Long = 0L,        // NEW
    val totalAmount: Long                    // EXISTING
)
```

### 8.5 PurchaseItemEntity Extension

```kotlin
@Entity(tableName = "purchase_items")
data class PurchaseItemEntity(
    // existing fields...
    val taxable: Boolean = true,             // NEW
    val taxRateSnapshot: Double? = null,     // NEW
    val taxAmountSnapshot: Long? = null      // NEW
)
```

### 8.6 SaleReturnTransactionEntity Extension

```kotlin
@Entity(tableName = "sale_return_transactions")
data class SaleReturnTransactionEntity(
    // existing fields...
    val taxableBaseSnapshot: Long = 0L,      // NEW
    val taxRateSnapshot: Double = 0.0,       // NEW
    val taxAmountSnapshot: Long = 0L         // NEW
)
```

### 8.7 SaleReturnItemEntity Extension

```kotlin
@Entity(tableName = "sale_return_items")
data class SaleReturnItemEntity(
    // existing fields...
    val taxable: Boolean = true,             // NEW
    val taxRateSnapshot: Double? = null,     // NEW
    val taxAmountSnapshot: Long? = null      // NEW
)
```

### 8.8 Mixed Taxable/Non-Taxable Transaction Support

**DECISION:** Transaksi dapat berisi produk taxable dan non-taxable secara campuran.

Alasan:
- UMKM menjual berbagai jenis produk
- Beberapa produk mungkin tidak kena pajak (misal: jasa tertentu, produk eksen)
- User harus bisa mengatur per-product tax policy

Implementasi:
- Setiap `SaleItem` menyimpan `taxable` dan `taxRateSnapshot` sendiri
- `SaleTransaction.taxableBaseSnapshot` = sum dari taxable items only
- `SaleTransaction.taxAmountSnapshot` = sum dari tax per item
- `SaleTransaction.totalAmount` = sum dari semua items (taxable + non-taxable)

---

## 9. CALCULATION MODEL

### 9.1 Deterministic Calculation Order

```
price
→ quantity
→ lineSubtotal = price * quantity
→ transactionGrossSubtotal = sum(lineSubtotal)
→ discountAmount
→ taxableBase = sum(lineSubtotal for taxable items)
→ taxAmount = calculateTax(taxableBase, taxRate, roundingMode)
→ grandTotal = (transactionGrossSubtotal - discountAmount) + taxAmount
```

### 9.2 Tax Exclusive (Default)

```kotlin
// Concept only, not implementation
fun calculateTaxExclusive(taxableBase: Long, rate: Double, roundingMode: RoundingMode): Long {
    // taxAmount = round(taxableBase * rate / 100, roundingMode)
    // grandTotal = taxableBase + taxAmount
}
```

### 9.3 Tax Inclusive

```kotlin
// Concept only, not implementation
fun calculateTaxInclusive(grossPrice: Long, rate: Double, roundingMode: RoundingMode): TaxResult {
    // taxableBase = grossPrice * 100 / (100 + rate)
    // taxAmount = grossPrice - taxableBase
    // grandTotal = grossPrice (already includes tax)
}
```

### 9.4 Key Invariants
- `totalAmount` selalu mewakili jumlah yang dibayar customer
- `taxAmountSnapshot` selalu disimpan sebagai integer Long
- `taxableBaseSnapshot` selalu mewakili jumlah yang terkena pajak
- `subtotalAmount` = gross sebelum discount (baru ditambahkan)

---

## 10. DISCOUNT ORDER

### 10.1 Defined Order

```
lineSubtotal = price × quantity
transactionGrossSubtotal = Σ(lineSubtotal)
discountAmount = calculateDiscount(transactionGrossSubtotal, discountInput)
taxableBase = Σ(lineSubtotal for taxable items) - discountAllocation
taxAmount = calculateTax(taxableBase, taxRate, roundingMode)
grandTotal = (transactionGrossSubtotal - discountAmount) + taxAmount
```

### 10.2 Discount Before Tax

**DECISION:** Discount selalu dihitung **sebelum** tax.

Alasan:
- Standar akuntansi Indonesia: discount mengurangi nilai transaksi sebelum pajak dihitung
- Lebih sederhana dan predictable
- Konsisten dengan current behavior (saat ini discount dari grossSubtotal)

### 10.3 Discount Allocation untuk Mixed Transaction

Jika transaksi memiliki produk taxable dan non-taxable:

```
Option A: Discount proporsional
- taxableDiscount = discountAmount × (taxableBase / grossSubtotal)
- nonTaxableDiscount = discountAmount × (nonTaxableBase / grossSubtotal)

Option B: Discount dari taxable base terlebih dahulu
- taxableDiscount = min(discountAmount, taxableBase)
- sisa discount ke non-taxable

DECISION: Option A (proporsional)
```

### 10.4 Item-Level Discount
Saat ini tidak ada item-level discount. Jika ditambahkan kemudian:
- Item discount mengurangi line subtotal
- Transaction discount mengurangi remaining subtotal
- Keduanya dihitung sebelum tax

---

## 11. ROUNDING MODEL

### 11.1 Current State
- Semua monetary values menggunakan `Long` (integer Rupiah)
- Discount percentage: `floor((grossSubtotal * percent) / 100.0).toLong()`
- Potensi floating point error pada discount percentage

### 11.2 Tax Rounding Strategy

**DECISION:** Gunakan integer arithmetic untuk tax calculation.

```kotlin
// Concept only
fun calculateTaxAmount(taxableBase: Long, rate: Double, roundingMode: RoundingMode): Long {
    // Convert to smallest unit (already in Rupiah, no decimals)
    // Use BigDecimal for intermediate calculation
    val taxExact = BigDecimal(taxableBase) * BigDecimal(rate) / BigDecimal(100)
    return taxExact.setScale(0, roundingMode).toLong()
}
```

### 11.3 Rounding Mode
- Default: `HALF_UP` (standar akuntansi)
- Konfigurasi: user bisa pilih `HALF_UP`, `HALF_DOWN`, `CEILING`, `FLOOR`
- Disimpan di `TaxSettings.roundingMode`

### 11.4 Per-Item vs Transaction-Level Rounding

**DECISION:** Rounding dilakukan per-item, lalu di-sum.

Alasan:
- Lebih akurat untuk mixed transaction
- Setiap item menampilkan tax amount yang benar di receipt
- Total tax = sum(tax per item)

### 11.5 Precision
- Semua values dalam Rupiah (integer)
- Tidak ada decimal places
- Rate disimpan sebagai `Double` (e.g., `11.0` untuk 11%)

---

## 12. SALES DESIGN

### 12.1 Sale Transaction Flow

```
1. User adds items to cart
2. System calculates:
   - lineSubtotal per item
   - transactionGrossSubtotal
   - discountAmount (if any)
   - taxableBase = sum(taxable items)
   - taxAmount = calculateTax(taxableBase, rate, mode)
   - grandTotal = grossSubtotal - discount + tax
3. User selects payment method (CASH/QRIS/CREDIT)
4. System creates:
   - SaleTransactionEntity with tax snapshot
   - SaleItemEntity with per-item tax snapshot
   - DebtEntity (if CREDIT) with tax included in totalDebt
5. Receipt displays:
   - Subtotal
   - Discount
   - Taxable Base
   - PPN
   - Total
```

### 12.2 Payment Method Impact
- **CASH/QRIS**: `totalAmount` dibayar upfront, termasuk tax
- **CREDIT**: `totalAmount` menjadi `totalDebt`, termasuk tax
- Tax tidak mempengaruhi payment method selection

### 12.3 Sale Return Flow
```
1. User selects original sale to return
2. System reads original tax snapshot:
   - taxRateSnapshot
   - taxAmountSnapshot per item
3. User specifies return quantity
4. System calculates:
   - refundAmount = sum(returned items' subtotal)
   - refundTax = proportional tax refund
   - totalRefund = refundAmount + refundTax
5. Creates:
   - SaleReturnTransactionEntity with tax snapshot
   - SaleReturnItemEntity with per-item tax snapshot
6. Adjusts:
   - Debt (if original was CREDIT)
   - Cash (if refund method is CASH)
```

### 12.4 Cancelled Transaction
- Cancel sebelum payment: tidak ada tax impact
- Cancel setelah payment: treated as return with full tax refund

---

## 13. PURCHASE DESIGN

### 13.1 Purchase Transaction Flow

```
1. User adds items to purchase
2. System calculates:
   - lineSubtotal per item
   - transactionGrossSubtotal
   - taxableBase
   - taxAmount (if applicable)
   - grandTotal
3. User selects payment method
4. System creates:
   - PurchaseTransactionEntity with tax snapshot
   - PurchaseItemEntity with per-item tax snapshot
   - SupplierPayableEntity (if CREDIT) including tax
```

### 13.2 Purchase Tax Treatment

**PRODUCT DECISION:** PPN Masukan (input tax) treatment.

Options:
A. **Included in Cost**: Tax included in `totalAmount`, no separate tracking
B. **Separated**: Tax tracked separately for tax reporting
C. **Informational Only**: Tax calculated but not separated in accounting

**DECISION:** Option A for v1 (included in cost).
- Simpler for UMKM users
- No separate tax accounting required
- Can be extended later if needed

### 13.3 Purchase Return
- Same as sale return: uses original tax snapshot
- Refund amount includes original tax

---

## 14. DEBT / CASH DESIGN

### 14.1 Customer Debt (DebtEntity)

```kotlin
data class DebtEntity(
    val totalDebt: Long,        // Include tax
    val paidAmount: Long,       // Include tax
    // No separate tax fields needed
)
```

**Rationale:** Debt is a financial obligation. Tax is already included in transaction total. No need to separate.

### 14.2 Supplier Payable (SupplierPayableEntity)

```kotlin
data class SupplierPayableEntity(
    val totalDebt: Long,        // Include tax
    val paidAmount: Long,       // Include tax
    // No separate tax fields needed
)
```

Same rationale as customer debt.

### 14.3 Cash Transaction (CashTransactionEntity)

```kotlin
data class CashTransactionEntity(
    val amount: Long,           // Include tax
    // No separate tax fields needed
)
```

Cash mutation reflects actual money movement, which includes tax.

### 14.4 Key Boundary
Tax calculation **tidak membuat** transaksi keuangan terpisah. Tax adalah komponen dari total transaksi yang sudah ada.

---

## 15. RETURN DESIGN

### 15.1 Full Return
- Menggunakan tax snapshot dari original transaction
- `totalRefundAmount` = original `totalAmount` untuk items yang diretur
- Tax refund = original `taxAmountSnapshot` untuk items yang diretur

### 15.2 Partial Return
- Tax refund dihitung proporsional
- Berdasarkan `taxAmountSnapshot` per item
- Tidak menggunakan current tax settings

### 15.3 Return Tax Display
Receipt/PDF untuk return menampilkan:
```
Pengembalian: Rp 50.000
Termasuk PPN (11%): Rp 5.000
Total Dikembalikan: Rp 55.000
```

---

## 16. REPORT DESIGN

### 16.1 Sales Report

```kotlin
data class SalesReportData(
    val grossSales: Long,           // Sum of totalAmount
    val totalDiscount: Long,        // Sum of discountAmount
    val totalTax: Long,             // NEW: Sum of taxAmountSnapshot
    val taxableSales: Long,         // NEW: Sum of taxableBaseSnapshot
    val totalRefund: Long,          // Include tax refund
    val netSales: Long,             // grossSales - totalRefund
    val totalTransactions: Int
)
```

### 16.2 Purchase Report

```kotlin
data class PurchaseReportData(
    val totalPurchases: Long,       // Include tax
    val totalTax: Long,             // NEW
    val cashPurchasesTotal: Long,
    val creditPurchasesTotal: Long,
    val totalTransactions: Int
)
```

### 16.3 Tax Summary Report (New)

```kotlin
data class TaxSummaryReportData(
    val periodStart: Long,
    val periodEnd: Long,
    val totalTaxableSales: Long,
    val totalTaxCollected: Long,
    val totalTaxRefunded: Long,
    val netTaxPayable: Long,
    val transactions: List<TaxReportTransaction>
)
```

### 16.4 Daily/Monthly Report
- Menampilkan tax totals terpisah dari gross sales
- Historical transactions menggunakan snapshot mereka sendiri

---

## 17. RECEIPT / PDF DESIGN

### 17.1 Receipt Layout (PPN ON)

```
================================
WARUNG SAYA
Alamat: Jl. Test No. 123
Telp: 081234567890
================================
No. Trx: TRX-20260120-001
Tanggal: 20/01/2026 14:30
Kasir: HP Utama
================================
Produk A      2 x Rp 10.000   20.000
Produk B      1 x Rp 15.000   15.000
================================
Subtotal:                    35.000
Diskon:                      -2.000
DPP:                         30.000
PPN 11%:                      3.300
================================
TOTAL:                       38.300
Bayar Tunai:                 40.000
Kembali:                      1.700
================================
TERIMA KASIH
```

### 17.2 Receipt Layout (PPN OFF)

```
================================
WARUNG SAYA
================================
No. Trx: TRX-20260120-002
Tanggal: 20/01/2026 14:35
================================
Produk A      2 x Rp 10.000   20.000
Produk B      1 x Rp 15.000   15.000
================================
Subtotal:                    35.000
Diskon:                      -2.000
TOTAL:                       33.000
Bayar Tunai:                 40.000
Kembali:                      7.000
================================
```

### 17.3 Historical Receipt Behavior
- Transaksi lama (sebelum PPN diaktifkan) menampilkan receipt tanpa tax
- Transaksi baru (setelah PPN diaktifkan) menampilkan receipt dengan tax
- Receipt menggunakan tax snapshot dari transaksi tersebut, bukan current settings

### 17.4 ReceiptData Extension

```kotlin
data class ReceiptPaymentInfo(
    val method: String,
    val totalAmount: Long,
    val subtotalAmount: Long? = null,
    val discountAmount: Long? = null,
    val taxableBase: Long? = null,      // NEW
    val taxAmount: Long? = null,        // NEW
    val taxRate: Double? = null,        // NEW
    val payAmount: Long? = null,
    val changeAmount: Long? = null,
    val customerName: String? = null,
    val remainingDebt: Long? = null
)
```

---

## 18. BACKUP / RESTORE DESIGN

### 18.1 PR-11.2 Extension

PR-11.2 (commit `5592cd9`) **TIDAK DIUBAH**.

Tax fields ditambahkan sebagai extension:

### 18.2 Backup Format Version
- Current: `1.1`
- With Tax: `1.2` (increment minor version)

### 18.3 New Backup Columns

**Sales Transactions (`06_Sales`):**
```
subtotal_amount, taxable_base_snapshot, tax_rate_snapshot, tax_amount_snapshot
```

**Sale Items (`07_SaleItems`):**
```
taxable, tax_rate_snapshot, tax_amount_snapshot
```

**Purchase Transactions (`08_Purchases`):**
```
subtotal_amount, taxable_base_snapshot, tax_rate_snapshot, tax_amount_snapshot
```

**Purchase Items (`09_PurchaseItems`):**
```
taxable, tax_rate_snapshot, tax_amount_snapshot
```

**Sale Returns (`17_SaleReturns`):**
```
taxable_base_snapshot, tax_rate_snapshot, tax_amount_snapshot
```

**Sale Return Items (`18_SaleReturnItems`):**
```
taxable, tax_rate_snapshot, tax_amount_snapshot
```

**Products (`04_Products`):**
```
taxable, tax_rate_override
```

**Metadata (`00_Metadata`):**
```
tax_enabled, tax_rate, tax_price_mode, tax_applicability, tax_rounding_mode
```

### 18.4 Restore Compatibility

| Backup Version | Restore Behavior |
|----------------|------------------|
| `1.0` (PR-11.1) | Accepted, tax fields = null/0 |
| `1.1` (PR-11.2) | Accepted, tax fields = null/0 |
| `1.2` (with tax) | Accepted, tax fields populated |

### 18.5 Checksum Impact
- Tax fields included in canonical payload
- Checksum changes when tax fields change
- Legacy backups without tax fields have different checksum (expected)

### 18.6 Migration Strategy
- Restore dari backup lama: `taxableBaseSnapshot = 0`, `taxAmountSnapshot = 0`, `taxRateSnapshot = 0.0`
- Products: `taxable = true`, `taxRateOverride = null`
- Transactions: treated as `taxExempt` implicitly by zero values

---

## 19. ROOM MIGRATION DESIGN

### 19.1 Migration Path
```
Version 15 (current) → Version 16 (tax-capable)
```

### 19.2 Migration Strategy

**DECISION:** Add nullable tax columns with defaults.

```kotlin
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // SaleTransactionEntity
        database.execSQL("ALTER TABLE sales_transactions ADD COLUMN subtotal_amount INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE sales_transactions ADD COLUMN taxable_base_snapshot INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE sales_transactions ADD COLUMN tax_rate_snapshot REAL NOT NULL DEFAULT 0.0")
        database.execSQL("ALTER TABLE sales_transactions ADD COLUMN tax_amount_snapshot INTEGER NOT NULL DEFAULT 0")
        
        // SaleItemEntity
        database.execSQL("ALTER TABLE sale_items ADD COLUMN taxable INTEGER NOT NULL DEFAULT 1")
        database.execSQL("ALTER TABLE sale_items ADD COLUMN tax_rate_snapshot REAL")
        database.execSQL("ALTER TABLE sale_items ADD COLUMN tax_amount_snapshot INTEGER")
        
        // PurchaseTransactionEntity
        database.execSQL("ALTER TABLE purchase_transactions ADD COLUMN subtotal_amount INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE purchase_transactions ADD COLUMN taxable_base_snapshot INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE purchase_transactions ADD COLUMN tax_rate_snapshot REAL NOT NULL DEFAULT 0.0")
        database.execSQL("ALTER TABLE purchase_transactions ADD COLUMN tax_amount_snapshot INTEGER NOT NULL DEFAULT 0")
        
        // PurchaseItemEntity
        database.execSQL("ALTER TABLE purchase_items ADD COLUMN taxable INTEGER NOT NULL DEFAULT 1")
        database.execSQL("ALTER TABLE purchase_items ADD COLUMN tax_rate_snapshot REAL")
        database.execSQL("ALTER TABLE purchase_items ADD COLUMN tax_amount_snapshot INTEGER")
        
        // SaleReturnTransactionEntity
        database.execSQL("ALTER TABLE sale_return_transactions ADD COLUMN taxable_base_snapshot INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE sale_return_transactions ADD COLUMN tax_rate_snapshot REAL NOT NULL DEFAULT 0.0")
        database.execSQL("ALTER TABLE sale_return_transactions ADD COLUMN tax_amount_snapshot INTEGER NOT NULL DEFAULT 0")
        
        // SaleReturnItemEntity
        database.execSQL("ALTER TABLE sale_return_items ADD COLUMN taxable INTEGER NOT NULL DEFAULT 1")
        database.execSQL("ALTER TABLE sale_return_items ADD COLUMN tax_rate_snapshot REAL")
        database.execSQL("ALTER TABLE sale_return_items ADD COLUMN tax_amount_snapshot INTEGER")
        
        // ProductEntity
        database.execSQL("ALTER TABLE products ADD COLUMN taxable INTEGER NOT NULL DEFAULT 1")
        database.execSQL("ALTER TABLE products ADD COLUMN tax_rate_override REAL")
    }
}
```

### 19.3 Legacy Transaction Semantics
- Semua transaksi existing: `taxableBaseSnapshot = 0`, `taxAmountSnapshot = 0`, `taxRateSnapshot = 0.0`
- Semua products: `taxable = 1` (true)
- **Tidak ada** silent recalculation
- Legacy transactions tetap menampilkan `totalAmount` asli

### 19.4 Index Strategy
- Tax columns tidak perlu index khusus (bukan frequently queried)
- Query existing tetap menggunakan `business_id`, `transaction_date`, dll.

---

## 20. DATASTORE MIGRATION DESIGN

### 20.1 First Upgrade
- User membuka aplikasi pertama kali setelah update
- `TaxSettings` di-load dengan defaults: `enabled = false`, `rate = 0.0`
- Tidak ada prompt awal (PPN OFF by default)

### 20.2 Missing Keys
- Semua keys menggunakan default values
- `tax_enabled` → `false`
- `tax_rate` → `0.0`
- `tax_price_mode` → `EXCLUSIVE`
- `tax_applicability` → `GLOBAL`
- `tax_rounding_mode` → `HALF_UP`
- `tax_effective_date` → `0L`

### 20.3 Corrupted Values
- `tax_rate` < 0 atau > 100 → reset ke `0.0`
- `tax_price_mode` invalid → reset ke `EXCLUSIVE`
- `tax_applicability` invalid → reset ke `GLOBAL`
- `tax_rounding_mode` invalid → reset ke `HALF_UP`

### 20.4 Backwards Compatibility
- UserSettings tetap kompatible
- Tax settings opsional
- Aplikasi lama tanpa tax settings tetap berfungsi

---

## 21. LEGACY TRANSACTION STRATEGY

### 21.1 Semantic Distinction

```
LEGACY TRANSACTION (pre-tax)
  ├── created before Tax Configuration existed
  ├── taxRateSnapshot = 0.0
  ├── taxableBaseSnapshot = 0
  ├── taxAmountSnapshot = 0
  └── Display: tanpa tax breakdown

TAX-AWARE TRANSACTION (post-tax)
  ├── created after Tax Configuration
  ├── taxRateSnapshot = actual rate
  ├── taxableBaseSnapshot = actual base
  ├── taxAmountSnapshot = actual amount
  └── Display: dengan tax breakdown
```

### 21.2 Display Rules
- Receipt/PDF legacy transaction: **tidak menampilkan** tax breakdown
- Receipt/PDF tax-aware transaction: menampilkan tax breakdown
- Tidak ada "estimated tax" untuk legacy transactions
- Reports: legacy transactions contribute `0` to tax totals

### 21.3 Migration Rule
**TIDAK ADA** migration/recalculation untuk legacy transactions.
- Legacy transactions tetap apa adanya
- Jika user ingin menandai某些 transaksi sebagai taxable, harus dilakukan manual (out of scope)

---

## 22. UI/UX FLOW

### 22.1 Settings

```
Settings
  ↓
Tax / PPN
  ├── ON / OFF toggle
  ├── Rate input (default 11.0)
  ├── Price Mode: EXCLUSIVE / INCLUSIVE
  ├── Applicability: GLOBAL / PRODUCT
  ├── Rounding: HALF_UP / HALF_DOWN / CEILING / FLOOR
  └── Effective Date (auto-set on change)
```

### 22.2 Product UI

```
Product Edit
  ↓
Tax Policy
  ├── Default (follow global settings)
  ├── Taxable (use global or override rate)
  └── Non-Taxable (exempt)
```

### 22.3 POS

```
Checkout
  ↓
Display:
  - Subtotal
  - Discount
  - Taxable Amount (if PPN ON)
  - PPN (if PPN ON)
  - Total
```

### 22.4 Purchase

```
Purchase Entry
  ↓
Display tax if applicable:
  - Subtotal
  - PPN
  - Total
```

### 22.5 Reports

```
Sales Report
  ↓
Columns:
  - No
  - Date/Trx No
  - Customer
  - Subtotal
  - Discount
  - Taxable Base
  - PPN
  - Total
  - Status
```

### 22.6 Receipt

Same as Section 17.

### 22.7 Business Type Separation
- Tax settings **tidak** ada di Business Type selector
- Tax settings **tidak** otomatis berubah saat Business Type berubah
- Tax settings **tidak** ditampilkan di onboarding Business Type

---

## 23. BUSINESS TYPE INTERACTION

### 23.1 Explicit Separation

| Domain | Purpose | Example |
|--------|---------|---------|
| Business Type | Identity/orientation usaha | WARUNG_SEMBAKO, BENGKEL, SALON |
| Capability | Fitur yang aktif | CAP_INVENTORY, CAP_BARCODE |
| Tax Configuration | Fiscal/pricing | PPN ON/OFF, rate, mode |
| Product | Seller catalog | Nama, harga, stok |
| License | Entitlement | Commercial license activation |

### 23.2 Valid Configurations

```
WARUNG_SEMBAKO + PPN OFF
WARUNG_SEMBAKO + PPN ON 11%
WARUNG_SEMBAKO + PPN ON custom rate

BENGKEL + PPN OFF
BENGKEL + PPN ON 11%

SALON + PPN OFF
SALON + PPN ON 11%
```

Semua kombinasi valid. Tidak ada Business Type yang memaksa tax status tertentu.

### 23.3 Capability Independence
- Tax calculation dijalankan jika `TaxSettings.enabled == true`
- Tidak bergantung pada Capability
- Capability tetap mengontrol fitur lain

---

## 24. DIGITAL / PPOB BOUNDARY

### 24.1 Current State
- DigitalTransactionEntity sudah ada (untuk provider digital products)
- Provider belum dipilih
- Tax treatment untuk digital products belum ditentukan

### 24.2 Extension Boundary

```kotlin
@Entity(tableName = "digital_transactions")
data class DigitalTransactionEntity(
    // existing fields...
    val taxable: Boolean? = null,           // NEW: null = follow global
    val taxRateSnapshot: Double? = null,    // NEW
    val taxAmountSnapshot: Long? = null     // NEW
)
```

### 24.3 Future Decision
- Tax treatment untuk digital products (pulsa, paket data) tergantung pada:
  - Provider agreement
  - Indonesian tax regulation untuk digital services
  - Product decision
- **Ditandai sebagai FUTURE DECISION**

---

## 25. LICENSE / ENTITLEMENT BOUNDARY

### 25.1 Existing License Architecture
- `commercial_license_activated_at` di DataStore
- License activation check di `LicenseGateScreen`
- Tax **tidak** menjadi second license system

### 25.2 Tax as Feature
**DECISION:** Tax adalah fitur yang universally available untuk semua license level.
- Tidak ada entitlement kontrol untuk tax
- Semua user bisa mengaktifkan/menonaktifkan PPN
- Jika nanti ada commercial requirement, ditambahkan kemudian

---

## 26. SECURITY / INTEGRITY

### 26.1 Historical Immutability
- Tax snapshot disimpan di database, bukan dihitung ulang dari settings
- Tidak ada API untuk mutate historical tax snapshot
- Settings perubahan hanya mempengaruhi transaksi baru

### 26.2 Database Consistency
- Semua tax fields `NOT NULL` dengan default `0` / `false`
- Migration menambahkan default values
- Tidak ada nullable tax fields yang bisa corrupt

### 26.3 Transaction Atomicity
- Tax calculation terjadi dalam transaksi yang sama dengan transaction creation
- Jika gagal, seluruh transaksi di-rollback

### 26.4 Backup/Restore Consistency
- Tax settings included in backup metadata
- Tax snapshots included in transaction backup
- Restore from old backup: tax fields = default values

### 26.5 Invalid Tax Rates
- Validation: `rate >= 0 && rate <= 100`
- Invalid rate → reset ke default (`0.0`)
- UI: input validation saat user mengubah rate

### 26.6 Negative/Overflow Scenarios
- Semua values dalam `Long` (maks ~9.2 quintillion Rupiah)
- `taxableBase * rate / 100` menggunakan `BigDecimal` untuk avoid overflow
- Coercion: `taxAmount.coerceAtLeast(0L)`

### 26.7 Concurrent Configuration Changes
- Tax settings di-read pada saat transaksi dibuat
- Tidak ada optimistic locking untuk settings (acceptable untuk config)
- Jika settings changed during transaction, transaction menggunakan snapshot saat itu

---

## 27. TEST STRATEGY

### 27.1 Test Matrix

| # | Scenario | Expected |
|---|----------|----------|
| 1 | PPN OFF → create transaction | tax = 0, tax fields = 0 |
| 2 | PPN ON 11% → create transaction | tax = 11% of taxable base |
| 3 | 0% rate | tax = 0 |
| 4 | Normal rate (11%) | tax = 11% |
| 5 | Tax Exclusive | tax ditambahkan ke total |
| 6 | Tax Inclusive | tax diekstrak dari harga |
| 7 | Taxable product | tax calculated |
| 8 | Non-taxable product | tax = 0 |
| 9 | Mixed transaction | correct per-item tax |
| 10 | Discount + tax | discount before tax |
| 11 | Cash sale + tax | total includes tax |
| 12 | Debt sale + tax | debt includes tax |
| 13 | Purchase + tax | total includes tax |
| 14 | Return + tax | refund includes tax |
| 15 | Legacy transaction | no tax, no tax display |
| 16 | Config change → old transaction | old transaction unchanged |
| 17 | Backup with tax | tax fields included |
| 18 | Restore without tax | tax fields = default |
| 19 | Migration v15→v16 | legacy = taxExempt |
| 20 | Rounding | deterministic, no floating error |

### 27.2 Invariants
- `totalAmount` selalu equals `(grossSubtotal - discount) + taxAmount`
- `taxAmountSnapshot` selalu equals sum of `taxAmountSnapshot` per item
- `taxableBaseSnapshot` selalu equals sum of `lineSubtotal` for taxable items
- Legacy transaction: `taxAmountSnapshot == 0`, `taxRateSnapshot == 0.0`

---

## 28. ADRs

### ADR-01: Tax Configuration Ownership

**Decision:** Tax Configuration dimiliki oleh Business Profile, bukan Business Type atau Capability.

**Reason:** Tax adalah fiscal/pricing concern, bukan business identity atau feature flag.

**Alternatives Considered:**
- Tax sebagai Capability → ditolak karena Capability adalah feature flag, bukan configuration
- Tax sebagai Business Type modifier → ditolak karena melanggar prinsip independence

**Consequence:** Tax settings terpisah dari Business Type changes.

---

### ADR-02: Transaction Tax Snapshot

**Decision:** Setiap transaksi menyimpan `taxRateSnapshot`, `taxAmountSnapshot`, `taxableBaseSnapshot` secara immutable.

**Reason:** Historical transactions tidak boleh berubah saat tax configuration berubah.

**Alternatives Considered:**
- Calculate tax on-the-fly saat display → ditolak karena membutuhkan historical settings
- Store only global rate changes → ditolak karena tidak cukup granular

**Consequence:** Database schema bertambah, tapi historical integrity terjaga.

---

### ADR-03: Tax Calculation Boundary

**Decision:** Tax calculation terjadi di Repository layer, sebelum entity creation.

**Reason:** Repository memiliki akses ke TaxSettings dan Product catalog.

**Alternatives Considered:**
- Tax calculation di Entity → ditolak karena Entity tidak tahu TaxSettings
- Tax calculation di UI → ditolak karena UI layer tidak boleh handle business logic

**Consequence:** Repository menjadi single source of truth untuk tax calculation.

---

### ADR-04: Inclusive/Exclusive Pricing

**Decision:** Default `EXCLUSIVE`, support both modes via `TaxPriceMode` enum.

**Reason:** Standar Indonesia adalah exclusive, tapi beberapa bisnis mungkin perlu inclusive.

**Alternatives Considered:**
- Hanya support exclusive → ditolak karena kurang fleksibel
- Hanya support inclusive → ditolak karena tidak standar

**Consequence:** UI harus menampilkan pilihan mode, calculation engine harus handle both.

---

### ADR-05: Discount Ordering

**Decision:** Discount dihitung sebelum tax.

**Reason:** Standar akuntansi Indonesia. Discount mengurangi nilai transaksi sebelum pajak dihitung.

**Alternatives Considered:**
- Discount after tax → ditolak karena non-standard
- Mixed (configurable) → ditolak karena kompleksitas tidak sebanding manfaat

**Consequence:** `taxableBase = grossSubtotal - discountAllocation`.

---

### ADR-06: Rounding

**Decision:** Per-item rounding, menggunakan `RoundingMode.HALF_UP` by default.

**Reason:** Lebih akurat untuk mixed transaction, konsisten dengan akuntansi.

**Alternatives Considered:**
- Transaction-level rounding → ditolak karena kurang akurat untuk mixed items
- Floor only → ditolak karena tidak ada konfigurasi

**Consequence:** `taxAmount` per item di-round, lalu di-sum untuk total.

---

### ADR-07: Product Tax Policy

**Decision:** Per-product override via `taxable` (Boolean) dan `taxRateOverride` (Double?).

**Reason:** Fleksibel untuk UMKM yang menjual produk taxable dan non-taxable.

**Alternatives Considered:**
- Global only → ditolak karena kurang fleksibel
- Category-level → ditolak karena kompleksitas
- Per-transaction override → ditolak karena menyimpang dari standar

**Consequence:** Product UI perlu menampilkan tax policy selector.

---

### ADR-08: Legacy Transaction Handling

**Decision:** Legacy transactions tetap sebagai `taxExempt` (taxRateSnapshot = 0.0, taxAmountSnapshot = 0).

**Reason:** Tidak ada data historical untuk recalculation. Melanggar historical immutability principle.

**Alternatives Considered:**
- Backfill dengan estimated tax → ditolak karena tidak ada data
- Prompt user untuk input → ditolak karena UX burden

**Consequence:** Legacy transactions menampilkan receipt tanpa tax breakdown.

---

### ADR-09: Room Migration

**Decision:** Add nullable columns with defaults, no data migration.

**Reason:** Legacy transactions tetap valid dengan default values. Tidak ada silent recalculation.

**Alternatives Considered:**
- Drop and recreate → ditolak karena data loss
- Complex backfill → ditolak karena tidak ada historical tax data

**Consequence:** Migration 15→16 sederhana, cepat, safe.

---

### ADR-10: Backup Compatibility

**Decision:** Increment backup format version to `1.2` for tax support. Old backups accepted with tax fields = default.

**Reason:** Backward compatibility tanpa breaking changes.

**Alternatives Considered:**
- Increment to `2.0` → ditolak karena terlalu aggressive
- Same format `1.1` → ditolak karena schema changed

**Consequence:** Backup system handles version detection, applies defaults for missing tax fields.

---

### ADR-11: Digital Transaction Boundary

**Decision:** DigitalTransactionEntity dapat memiliki tax fields, tapi tax treatment tergantung provider.

**Reason:** Provider belum dipilih, tax regulation untuk digital services belum jelas.

**Alternatives Considered:**
- No tax for digital → ditolak karena possible future requirement
- Fixed tax for digital → ditolak karena premature decision

**Consequence:** Tax fields ada di entity, tapi logic bisa di-implement later.

---

## 29. IMPLEMENTATION BOUNDARY

### 29.1 Design Now
- [x] Tax Configuration domain model
- [x] Product tax policy
- [x] Transaction snapshot schema
- [x] Calculation model
- [x] Discount ordering
- [x] Rounding strategy
- [x] ADRs

### 29.2 Implementation Later
- [ ] TaxSettings DataStore migration
- [ ] Room entities migration (15→16)
- [ ] TaxCalculator engine
- [ ] SaleRepository tax integration
- [ ] PurchaseRepository tax integration
- [ ] Return tax logic
- [ ] Settings UI for tax
- [ ] Product UI for tax policy
- [ ] POS display tax
- [ ] Receipt/PDF tax display
- [ ] Report tax totals
- [ ] Backup format 1.2
- [ ] Tests

### 29.3 Out of Scope (This Gate)
- Tax/PPN implementation
- Room migration code
- DataStore migration code
- UI changes
- Report/PDF changes
- Backup changes
- Digital transaction tax logic
- Purchase tax accounting decision

---

## 30. OPEN PRODUCT / ACCOUNTING DECISIONS

| # | Decision | Options | Impact |
|---|----------|---------|--------|
| 1 | Purchase Tax Treatment | A) Included in cost, B) Separated, C) Informational only | Accounting reporting |
| 2 | Digital Product Tax | Follow provider rules or standard PPN | Digital transaction domain |
| 3 | Tax Reporting | Internal summary only or government filing ready | Report design |
| 4 | Multi-Tax | Support multiple tax rates (e.g., PPN 11% + PPnBM) | Calculation engine |
| 5 | Tax Effective Date | Per-transaction or global config change | Migration strategy |
| 6 | Rounding Difference | Absorb or track separately | Accounting treatment |

---

## 31. RISKS

### 31.1 Historical Data Gap
**Risk:** Legacy transactions tidak memiliki tax snapshot.

**Mitigation:** Design explicit legacy transaction semantics. No silent recalculation.

### 31.2 Rounding Errors
**Risk:** Floating point calculation untuk tax.

**Mitigation:** Gunakan `BigDecimal` dengan `RoundingMode` yang jelas. Integer arithmetic di layer terakhir.

### 31.3 Migration Complexity
**Risk:** Menambahkan 7+ kolom ke entities yang sudah ada.

**Mitigation:** Migration sederhana dengan default values. No data backfill required.

### 31.4 Backup Compatibility
**Risk:** Backup lama tidak compatible dengan schema baru.

**Mitigation:** Backup format versioning. Restore applies defaults for missing columns.

### 31.5 User Confusion
**Risk:** User mengira Business Type = Tax Status.

**Mitigation:** UI separation jelas. Documentation. Settings tidak mencampur kedua domain.

### 31.6 Mixed Transaction Complexity
**Risk:** Perhitungan tax untuk mixed taxable/non-taxable items kompleks.

**Mitigation:** Per-item tax calculation dengan snapshot. Proporsional discount allocation.

---

## 32. RECOMMENDED IMPLEMENTATION SEQUENCE

### Phase 1: Foundation (Sprint 1-2)
1. TaxSettings DataStore + keys
2. TaxCalculator engine
3. ProductEntity tax fields + migration
4. Settings UI for tax configuration
5. Unit tests for TaxCalculator

### Phase 2: Sales Integration (Sprint 3-4)
1. SaleTransactionEntity + SaleItemEntity tax fields + migration
2. SaleRepository tax calculation
3. POS display tax
4. Receipt tax display
5. Integration tests

### Phase 3: Purchase Integration (Sprint 5)
1. PurchaseTransactionEntity + PurchaseItemEntity tax fields + migration
2. PurchaseRepository tax calculation
3. Purchase display tax

### Phase 4: Return Integration (Sprint 6)
1. SaleReturnTransactionEntity + SaleReturnItemEntity tax fields + migration
2. Return tax logic using original snapshot
3. Return receipt tax display

### Phase 5: Reports & Backup (Sprint 7-8)
1. Report tax totals
2. PDF tax display
3. Backup format 1.2
4. Restore backward compatibility

### Phase 6: Testing & Polish (Sprint 9)
1. Full integration tests
2. Migration tests
3. Edge case tests
4. Documentation

---

## 33. FINAL VERDICT

**PASS**

### Rationale

Design ini addresses semua PR-12 P0 findings:

| PR-12 Finding | Design Address |
|---------------|----------------|
| No tax configuration | Section 6: TaxSettings DataStore model |
| No tax fields in entities | Section 8: Transaction snapshot schema |
| No historical snapshot | Section 8.2-8.7: Immutable tax snapshot per entity |
| No tax calculation engine | Section 9: Calculation model |
| Business Type confusion | Section 23: Explicit separation |
| Backup compatibility | Section 18: Version 1.2 with backward compatibility |
| Legacy transaction safety | Section 21: Explicit legacy semantics |

### Key Architectural Decisions Locked

1. Tax Configuration adalah domain independen
2. Current settings tidak pernah mutate historical transactions
3. Semua monetary values menggunakan `Long`
4. Per-item tax calculation dengan rounding
5. Discount sebelum tax
6. Legacy transactions tetap tax-exempt
7. Backup format 1.2, backward compatible

### Next Gate

**PR-12.2: TAX/PPN IMPLEMENTATION**  
Tujuan: Implementasi sesuai design ini, diawali oleh Phase 1 (Foundation).

Prerequisites:
- Approval design ini
- Product decision untuk purchase tax treatment
- Product decision untuk digital product tax

---

## APPENDIX A: ENTITY TAX FIELD SUMMARY

| Entity | New Fields | Default | Snapshot |
|--------|-----------|---------|----------|
| SaleTransactionEntity | subtotalAmount, taxableBaseSnapshot, taxRateSnapshot, taxAmountSnapshot | 0/0.0 | Yes |
| SaleItemEntity | taxable, taxRateSnapshot, taxAmountSnapshot | true/null/null | Yes |
| PurchaseTransactionEntity | subtotalAmount, taxableBaseSnapshot, taxRateSnapshot, taxAmountSnapshot | 0/0.0 | Yes |
| PurchaseItemEntity | taxable, taxRateSnapshot, taxAmountSnapshot | true/null/null | Yes |
| SaleReturnTransactionEntity | taxableBaseSnapshot, taxRateSnapshot, taxAmountSnapshot | 0/0.0 | Yes |
| SaleReturnItemEntity | taxable, taxRateSnapshot, taxAmountSnapshot | true/null/null | Yes |
| ProductEntity | taxable, taxRateOverride | true/null | No |
| DigitalTransactionEntity | taxable, taxRateSnapshot, taxAmountSnapshot | null/null/null | Yes (future) |

## APPENDIX B: DATASTORE KEYS SUMMARY

| Key | Type | Default | Purpose |
|-----|------|---------|---------|
| `tax_enabled` | Boolean | `false` | Enable/disable tax |
| `tax_rate` | Double | `0.0` | Tax rate percentage |
| `tax_price_mode` | String | `EXCLUSIVE` | Price mode |
| `tax_applicability` | String | `GLOBAL` | Applicability scope |
| `tax_rounding_mode` | String | `HALF_UP` | Rounding mode |
| `tax_effective_date` | Long | `0L` | Last config change |

## APPENDIX C: BACKUP FORMAT EXTENSION

| Sheet | New Columns | Version |
|-------|-------------|---------|
| `06_Sales` | subtotal_amount, taxable_base_snapshot, tax_rate_snapshot, tax_amount_snapshot | 1.2 |
| `07_SaleItems` | taxable, tax_rate_snapshot, tax_amount_snapshot | 1.2 |
| `08_Purchases` | subtotal_amount, taxable_base_snapshot, tax_rate_snapshot, tax_amount_snapshot | 1.2 |
| `09_PurchaseItems` | taxable, tax_rate_snapshot, tax_amount_snapshot | 1.2 |
| `17_SaleReturns` | taxable_base_snapshot, tax_rate_snapshot, tax_amount_snapshot | 1.2 |
| `18_SaleReturnItems` | taxable, tax_rate_snapshot, tax_amount_snapshot | 1.2 |
| `04_Products` | taxable, tax_rate_override | 1.2 |
| `00_Metadata` | tax_enabled, tax_rate, tax_price_mode, tax_applicability, tax_rounding_mode | 1.2 |
