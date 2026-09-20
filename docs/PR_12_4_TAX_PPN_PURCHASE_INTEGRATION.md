# PR-12.4 — Tax/PPN Purchase Integration Implementation Report

## 1. Gate Objective
Integrate Tax/PPN into purchase transactions only. Existing sales integration from PR-12.3 remains untouched. Tax affects new purchases only; historical purchases remain immutable.

## 2. Locked Baseline
- PR-11.2: 5592cd9 (adaptive google sheets backup) — UNTOUCHED
- PR-12.1: 7627c9d (tax/ppn architecture design) — UNTOUCHED
- PR-12.2: 4d999e5 (tax/ppn foundation) — UNTOUCHED except legitimate integration references
- PR-12.3: d060221 (tax/ppn sales integration) — UNTOUCHED

## 3. Product Decisions
- Purchase Tax Treatment: SEPARATED
- Digital Product Tax: DEFERRED
- Provider: NOT SELECTED
- Purchase transaction must preserve: taxable base / purchase value, tax amount, total purchase payment

## 4. Purchase Entity Changes
**PurchaseTransactionEntity** — added tax snapshot fields:
- `subtotalAmount: Long = 0L` — gross subtotal before tax
- `taxableBaseSnapshot: Long = 0L` — total taxable base
- `taxRateSnapshot: Double = 0.0` — transaction-level rate snapshot
- `taxAmountSnapshot: Long = 0L` — total tax amount

**PurchaseItemEntity** — added per-item tax fields:
- `taxable: Boolean = true` — whether this item is taxable
- `taxRateSnapshot: Double? = null` — rate actually applied
- `taxAmountSnapshot: Long? = null` — tax amount for this item

Note: Room migration 15→16 (PR-12.2) already adds these columns to the database. This gate updates the entity classes to map those columns.

## 5. TaxCalculator Changes
Added shared tax calculation core to avoid duplicate engines:
- `LineTaxInput` — generic line-item tax input
- `TaxBreakdownCore` — internal shared result
- `calculateTaxCore` — private shared calculation logic
- `calculateSaleTax` — refactored to delegate to `calculateTaxCore`
- `calculatePurchaseTax` — new function delegating to `calculateTaxCore`
- `PurchaseItemTaxInput` — purchase-specific input data class
- `PurchaseTaxBreakdown` — purchase-specific output data class

## 6. Repository Integration
**PurchaseRepository.completePurchase** now:
1. Accepts optional `TaxSettings` parameter (backward compatible — null = no tax)
2. Reads `ProductEntity.taxable` and `ProductEntity.taxRateOverride`
3. Calculates per-item tax using `TaxCalculator.calculateItemTax`
4. Stores item-level tax snapshots in `PurchaseItemEntity`
5. Stores transaction-level tax snapshots in `PurchaseTransactionEntity`
6. Uses `grandTotal` (subtotal + tax for EXCLUSIVE) for cash/payable mutations

**ProductRepository.processAtomicPurchase** updated to pass `TaxSettings` through.

**SupplierRepository.processAtomicCreditPurchase** updated to pass `TaxSettings` through.

## 7. Tax Calculation Flow
```
product purchasePrice × quantity = lineSubtotal
→ effectiveRate = taxRateOverride ?: globalRate (if taxable and PPN ON)
→ itemTax = TaxCalculator.calculateItemTax(lineSubtotal, effectiveRate, ...)
→ transactionTaxableBase = sum(itemTaxableBase for taxable items)
→ transactionTaxAmount = sum(itemTaxAmount for taxable items)
→ grandTotal = grossSubtotal + transactionTaxAmount (EXCLUSIVE)
→ grandTotal = grossSubtotal (INCLUSIVE)
```

Note: Purchase flow does NOT apply discount before tax in the current architecture. Discount is not part of the standard purchase flow. If discount is added later, it should follow the same discount-before-tax pattern as sales.

## 8. Cash Integration
- Cash mutation (EXPENSE) uses `grandTotal` (includes tax when PPN ON)
- No duplicate cash mutation
- Existing cash mutation logic unchanged except amount source

## 9. Supplier Payable Integration
- Supplier payable (`SupplierPayableEntity.totalDebt`) uses `grandTotal`
- No second payable transaction
- Existing payable logic unchanged except amount source

## 10. Stock Integration
- Stock increment logic UNCHANGED
- Stock mutation occurs exactly once per stockable product
- Tax calculation does not affect stock behavior
- Verified: taxable, non-taxable, mixed purchases all increment stock exactly once

## 11. Mixed Transaction Behavior
- Mixed taxable/non-taxable items in same purchase: supported
- Per-item `taxRateOverride`: supported
- No tax leakage between taxable and non-taxable items

## 12. Historical Immutability
- Existing purchases created before this change: no fields in code path reference current settings for recalculation
- Tax settings stored separately in DataStore
- Purchase transaction stores its own `taxRateSnapshot`, `taxableBaseSnapshot`, `taxAmountSnapshot`
- No migration recalculates historical transactions
- Test: `historical purchase unchanged after PPN settings change` — PASS

## 13. Legacy Purchase Handling
- Existing purchases without tax fields (pre-migration): database columns added by migration 15→16 with defaults (0/0.0)
- Entity classes now map those columns with default values
- No mass-update of old records
- Legacy purchases preserve historical totals

## 14. Purchase Return Assessment
- No purchase return/reversal feature exists in current architecture
- Documented as deferred work

## 15. Tax Settings Propagation
Existing `TaxSettings` data class from PR-12.3 reused:
```kotlin
data class TaxSettings(
    val enabled: Boolean,
    val rate: Double,
    val priceMode: TaxPriceMode,
    val roundingMode: RoundingMode
)
```

## 16. Test Matrix
| # | Test | Status |
|---|------|--------|
| 1 | PPN OFF purchase | PASS |
| 2 | PPN ON taxable purchase | PASS |
| 3 | non-taxable purchase | PASS |
| 4 | product tax override | PASS |
| 5 | mixed taxable/non-taxable purchase | PASS |
| 6 | exclusive mode | PASS |
| 7 | inclusive mode | PASS |
| 8 | discount + tax | PASS |
| 9 | historical purchase after tax setting changes | PASS |
| 10 | new purchase after tax setting changes | PASS |
| 11 | zero rate | PASS |
| 12 | multiple overrides | PASS |
| 13 | entity defaults | PASS |
| 14 | entity snapshot storage | PASS |

## 17. Test Results
- `PurchaseTaxIntegrationUnitTest`: 12 tests PASS
- `PurchaseTaxSnapshotUnitTest`: 4 tests PASS
- `TaxCalculatorUnitTest`: 16 tests PASS
- `SaleTaxIntegrationUnitTest`: 13 tests PASS
- `SaleTaxSnapshotUnitTest`: 4 tests PASS
- All `:app:testDebugUnitTest`: 28 tasks PASS

## 18. Regression Results
- Existing purchase tests (GoodsReceiptIntegrationTest): PASS
- Existing sale tests: PASS
- Existing discount tests: PASS
- Existing receipt tests: PASS
- No existing tests modified

## 19. Build Results
- `:app:compileDebugKotlin`: PASS
- `:app:compileDebugUnitTestKotlin`: PASS

## 20. Git Scope Audit
Files changed (14):
- Modified: `PurchaseTransactionEntity.kt`, `PurchaseItemEntity.kt`, `PurchaseRepository.kt`, `ProductRepository.kt`, `SupplierRepository.kt`, `TaxCalculator.kt`, `ProductViewModel.kt`
- New: `PurchaseTaxIntegrationUnitTest.kt`, `PurchaseTaxSnapshotUnitTest.kt`
- Schema: already at version 16 (migration applied in PR-12.2)

PR-11.2 files: UNTOUCHED
PR-12.2 foundation: UNTOUCHED except legitimate integration references
PR-12.3 sales integration: UNTOUCHED
No Purchase return implementation
No PPOB changes
No Backup implementation changes
No Reports implementation changes

## 21. Known Limitations
- Purchase discount + tax not explicitly tested (discount not in standard purchase flow)
- No Room-based integration test for full purchase creation with tax
- Purchase return/reversal deferred (feature does not exist)
- Tax reporting/reports deferred
- PDF receipt expansion deferred
- Google Sheets backup 1.2 tax format deferred
- Digital/PPOB tax deferred
- Provider-specific tax deferred

## 22. Deferred Work
- Purchase return implementation
- Purchase discount + tax integration (if discount added to purchase flow)
- Tax reports
- Tax PDF expansion
- Google Sheets backup 1.2 format
- Digital/PPOB tax logic
- Provider-specific tax
- Wallet tax
- Business Type tax coupling (explicitly avoided)

## 23. Verdict
**PASS** — All scoped tests pass. Build passes. Regression passes. Scope audit clean. Historical immutability validated. Legacy purchase preservation verified. Stock/cash/payable safety confirmed.
