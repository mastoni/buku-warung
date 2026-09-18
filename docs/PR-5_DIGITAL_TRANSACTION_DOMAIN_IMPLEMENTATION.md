# PR-5 Digital Transaction Domain Implementation

## 1. Scope
Implemented local data persistence and state machine for digital fulfillment as designed in PR-4 and PR-4.1. This gate deals exclusively with the local domain models, Room schema evolution (v13 to v14), and state validation rules. No external provider integrations are made.

## 2. Baseline
*   **Repository:** `E:\Android Project\BukuWarungKotlin`
*   **Release Version:** v0.2.0
*   **Database Schema:** Room v14 (Migrated from v13)
*   **Git HEAD:** `a0e0f97aa02956503c3fe43bdbb855ca1834f9de`

## 3. Source Audit
Prior to implementation, an audit of the `ProductEntity`, `SaleItemEntity`, and `SaleTransactionEntity` revealed:
- `SaleItemEntity` relies on `transaction_id` and utilizes an `onDelete = ForeignKey.CASCADE` policy for deletion. This exact cascading policy is appropriately applied to `DigitalTransactionEntity` -> `SaleItemEntity`.
- UUIDs are universally generated via `java.util.UUID.randomUUID().toString()`.
- Accounting records (Cash Expense/Refund) and external integrations were explicitly ignored per the PR-4.1 bounds.

## 4. Implemented Components
- `FulfillmentMode` (MANUAL, PROVIDER)
- `DigitalTransactionStatus` (DRAFT, PENDING, SUCCESS, FAILED, UNKNOWN)
- Modifications to `ProductEntity` (added fulfillmentMode, digitalProviderId, digitalProductCode)
- `DigitalTransactionEntity`
- `DigitalTransactionDao`
- `DigitalTransactionRepository`
- `AppDatabase` (MIGRATION_13_14)
- `DigitalTransactionDomainTest`

## 5. FulfillmentMode
An explicit enumeration `FulfillmentMode` was added. `ProductEntity` utilizes `FulfillmentMode.MANUAL.name` as its default, ensuring backwards compatibility for all existing digital products that require manual fulfillment. `FulfillmentMode.PROVIDER.name` is the explicit opt-in for automated PPOB flows.

## 6. DigitalTransactionEntity
Implemented with exactly the fields mandated in PR-4.1: `uuid`, `saleItemId`, `providerId`, `providerProductCode`, `destinationNumber`, `sellingPrice`, `actualPurchasePrice`, `status`, `providerReferenceId`, `snToken`, `failureReason`, `createdAt`, `updatedAt`.

## 7. DAO
`DigitalTransactionDao` handles essential CRUD, along with specific getters by `saleItemId` and `status` to enable polling and queue processing in the upcoming backend integration.

## 8. Repository
`DigitalTransactionRepository` implements strict domain policies, validating every state transition and throwing an `IllegalStateException` on invalid moves (e.g., SUCCESS -> FAILED is rejected).

## 9. State Machine
Valid transitions strictly conform to:
* DRAFT -> PENDING
* PENDING -> SUCCESS, FAILED, UNKNOWN
* UNKNOWN -> SUCCESS, FAILED, PENDING

## 10. Room Migration
`MIGRATION_13_14` safely adds `fulfillment_mode`, `digital_provider_id`, and `digital_product_code` to `products`, and creates the `digital_transactions` table. All existing v13 data across categories, products, cash, debt, and sales is preserved.

## 11. Snapshot Behavior
`actualPurchasePrice` is saved to `DigitalTransactionEntity` at the moment of DRAFT creation. Future catalog updates to `ProductEntity`'s purchase price will safely ignore historical transactions.

## 12. Idempotency Identity
`DigitalTransactionEntity.uuid` serves as the strict local transaction identifier. Retrievals for retry/status checks rely on this UUID rather than creating duplicate instances.

## 13. Offline Boundary
`DigitalTransactionStatus.DRAFT` is successfully implemented to safely queue an offline checkout. The network submission logic is deferred to the provider adapter gate.

## 14. Accounting Boundary
The existing `SaleReturnTransactionEntity` refund behavior was explicitly ignored in this gate as no failed-state refunds were processed. 

## 15. Tests
`DigitalTransactionDomainTest.kt` passes perfectly, validating Entity persistence, cascade deletions from SaleItem, strict state transitions (both positive and negative), and isolated snapshot behaviors. 

## 16. Regression
Existing operations surrounding Physical, Fuel, Service, and manual Digital sales remain fully functional through backward compatible models.

## 17. Provider Integration Boundary
Implementation is strictly bound to local scope; no API endpoints, Retrofit objects, or credentials have been injected.

## 18. Known Limitations
- The system still awaits a Backend Proxy structure to facilitate `createTransaction` requests.
- No UI currently captures the `destinationNumber` from the user.

## 19. Verdict
**PASS**
PR-5 successfully implements the underlying persistence layer for digital fulfillment transactions. The path is clear for the UI and Provider integration implementations.
