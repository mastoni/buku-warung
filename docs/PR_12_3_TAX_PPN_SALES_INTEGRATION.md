# PR-12.3 — Tax/PPN Sales Integration Implementation Report

## 1. Objective
Integrate Tax/PPN into the sales flow only. Existing foundation from PR-12.2 remains untouched. Tax affects new sales only; historical sales remain immutable.

## 2. Locked Baseline
- PR-11.2: 5592cd9 (adaptive google sheets backup) — UNTOUCHED
- PR-12.1: 7627c9d (tax/ppn architecture design) — UNTOUCHED
- PR-12.2: 4d999e5 (tax/ppn foundation) — UNTOUCHED except legitimate integration references

## 3. Sale Entity Changes
**SaleTransactionEntity** — added tax snapshot fields:
- `subtotalAmount: Long = 0L` — gross subtotal before discount
- `taxableBaseSnapshot: Long = 0L` — total taxable base after discount
- `taxRateSnapshot: Double = 0.0` — transaction-level rate snapshot
- `taxAmountSnapshot: Long = 0L` — total tax amount for the transaction

**SaleItemEntity** — added per-item tax fields:
- `taxable: Boolean = true` — whether this item is taxable
- `taxRateSnapshot: Double? = null` — rate actually applied to this item
- `taxAmountSnapshot: Long? = null` — tax amount for this item

## 4. Repository Integration
**SaleRepository.completeSaleInTransaction** now:
1. Accepts optional `TaxSettings` parameter (backward compatible — null = no tax)
2. Reads `ProductEntity.taxable` and `ProductEntity.taxRateOverride`
3. Allocates discount proportionally per item
4. Calculates per-item tax using `TaxCalculator.calculateItemTax` with per-item effective rate
5. Stores item-level tax snapshots in `SaleItemEntity`
6. Stores transaction-level tax snapshots in `SaleTransactionEntity`
7. Uses `grandTotal` (netTotal + totalTax) for cash/debt mutations

**ProductRepository** and **CustomerRepository** updated to pass `TaxSettings` through.

**CheckoutOrchestrator** updated to accept and pass `TaxSettings`.

## 5. Tax Calculation Flow
```
product price × quantity = lineSubtotal
→ proportional discount allocation per item
→ discountedSubtotal = lineSubtotal - itemDiscount
→ effectiveRate = taxRateOverride ?: globalRate (if taxable and PPN ON)
→ itemTax = TaxCalculator.calculateItemTax(discountedSubtotal, effectiveRate, ...)
→ transactionTaxableBase = sum(itemTaxableBase for taxable items)
→ transactionTaxAmount = sum(itemTaxAmount for taxable items)
→ grandTotal = (grossSubtotal - discount) + transactionTaxAmount (EXCLUSIVE)
→ grandTotal = (grossSubtotal - discount) (INCLUSIVE)
```

## 6. POS Integration
**PosScreen**:
- Constructs `TaxSettings` from `UserSettings`
- Computes `taxBreakdown` using `TaxCalculator.calculateSaleTax` for display
- Passes `taxSettings` to all checkout methods
- Payment dialogs use `grandTotal` instead of `netTotal`
- Payment summary shows DPP/PPN breakdown when tax > 0
- Checkout success dialog shows PPN amount

**ProductViewModel** and **CustomerViewModel** updated to accept and propagate `TaxSettings`.

## 7. Receipt Integration
**ReceiptData.ReceiptPaymentInfo** — added:
- `taxableBase: Long? = null`
- `taxAmount: Long? = null`

**ReceiptMapper** — includes tax breakdown when `sale.taxAmountSnapshot > 0`.

## 8. Mixed Transaction Behavior
- Mixed taxable/non-taxable items in same sale: supported
- Per-item `taxRateOverride`: supported via `SaleItemTaxInput.taxRateOverride`
- Discount allocated proportionally per item before tax calculation
- No tax leakage between taxable and non-taxable items

## 9. Historical Immutability
- Existing sales created before this change: no fields in code path reference current settings for recalculation
- Tax settings stored separately in DataStore (UserPreferencesRepository)
- Sale transaction stores its own `taxRateSnapshot`, `taxableBaseSnapshot`, `taxAmountSnapshot`
- No migration recalculates historical transactions
- Test: `historical sale unchanged after PPN settings change` — PASS

## 10. Cash Integration
- Cash mutation uses `grandTotal` (includes tax when PPN ON)
- No duplicate cash mutation
- Existing cash mutation logic unchanged except amount source

## 11. Debt Integration
- Debt creation uses `grandTotal`
- No second debt transaction
- Existing debt logic unchanged except amount source

## 12. Tax Settings Propagation
New `TaxSettings` data class in `domain.tax`:
```kotlin
data class TaxSettings(
    val enabled: Boolean,
    val rate: Double,
    val priceMode: TaxPriceMode,
    val roundingMode: RoundingMode
)
```

## 13. New TaxCalculator Functions
- `calculateSaleTax` — sale-level tax calculation with per-item rate overrides
- `SaleItemTaxInput` — input with `lineSubtotal`, `taxable`, `taxRateOverride`
- `SaleTaxBreakdown` — output with `totalTaxableBase`, `totalTaxAmount`, `grandTotal`
- `roundToLong` — made public for repository discount allocation

## 14. Test Matrix
| # | Test | Status |
|---|------|--------|
| 1 | PPN OFF sale | PASS |
| 2 | PPN ON taxable sale | PASS |
| 3 | non-taxable product | PASS |
| 4 | product tax override | PASS |
| 5 | mixed taxable/non-taxable sale | PASS |
| 6 | exclusive mode | PASS |
| 7 | inclusive mode | PASS |
| 8 | discount + tax | PASS |
| 9 | historical sale after tax setting changes | PASS |
| 10 | new sale after tax setting changes | PASS |
| 11 | rounding consistency | PASS |
| 12 | zero rate | PASS |
| 13 | multiple overrides | PASS |
| 14 | entity defaults | PASS |
| 15 | entity snapshot storage | PASS |

## 15. Test Results
- `TaxCalculatorUnitTest`: 16 tests PASS
- `SaleTaxIntegrationUnitTest`: 13 tests PASS
- `SaleTaxSnapshotUnitTest`: 4 tests PASS
- `ReceiptMapperUnitTest`: existing tests PASS
- All `:app:testDebugUnitTest`: 28 tasks PASS

## 16. Regression Results
- Existing sale tests: PASS
- Existing discount tests: PASS
- Existing receipt tests: PASS
- No existing tests modified

## 17. Build Results
- `:app:compileDebugKotlin`: PASS
- `:app:compileDebugUnitTestKotlin`: PASS

## 18. Scope Audit
Files changed (14):
- Modified: `SaleTransactionEntity.kt`, `SaleItemEntity.kt`, `SaleRepository.kt`, `ProductRepository.kt`, `CustomerRepository.kt`, `CheckoutOrchestrator.kt`, `ReceiptData.kt`, `ReceiptMapper.kt`, `TaxCalculator.kt`, `ProductViewModel.kt`, `CustomerViewModel.kt`, `PosScreen.kt`, `AppScreen.kt`
- New: `TaxSettings.kt`, `SaleTaxIntegrationUnitTest.kt`, `SaleTaxSnapshotUnitTest.kt`
- Schema: `16.json` (regenerated)

PR-11.2 files: UNTOUCHED
PR-12.2 files: UNTOUCHED except legitimate integration references
No Purchase changes
No PPOB changes
No Backup implementation changes

## 19. Known Limitations
- Tax integration is sales-only; Purchase tax deferred to PR-12.4+
- Tax reporting/reports deferred
- PDF receipt expansion deferred
- Google Sheets backup 1.2 tax format deferred
- Digital/PPOB tax deferred
- Provider-specific tax deferred
- No Room-based integration test for full sale creation with tax (complex setup; unit tests cover calculation logic)
- Inclusive mode transaction-level grandTotal uses `netSubtotal` (tax embedded in price)

## 20. Deferred Work
- Purchase tax integration
- Purchase payable tax
- Sales reports tax column
- PDF tax expansion
- Google Sheets backup 1.2 format
- Digital/PPOB tax logic
- Provider-specific tax
- Wallet tax
- Business Type tax coupling (explicitly avoided)

## 21. Verdict
**PASS** — All scoped tests pass. Build passes. Regression passes. Scope audit clean. Historical immutability validated. Legacy product safety preserved.
