# PR-10 — Digital Provider Selection Decision

## 1. Decision Scope
This document converts the PR-9 due diligence evidence into an explicit provider selection decision for Buku Warung's digital fulfillment provider. It evaluates Digiflazz, Tripay, and DANA against technical, commercial, and operational criteria.

## 2. Locked Architecture Constraints
Provider must implement `DigitalProviderAdapter` methods (catalog, destination inquiry, create transaction, status check, callback/webhook, balance) without leaking provider secrets into `PosScreen`, `SaleRepository`, `ProductEntity`, `DigitalTransactionEntity`, or accounting. Idempotency must be UUID-based, credentials server-only.

## 3. PR-9 Evidence Reconciliation
PR-9 verified public documentation for all three candidates:
- Digiflazz: Full forward-flow API documented (catalog, inquiry, create, status, webhook, balance) with structural verification.
- Tripay: Only payment-gateway API documented; no public evidence of PPOB/digital-goods fulfillment API.
- DANA: Digital Goods API documented but reverses flow (DANA initiates orders to partner), incompatible with "shop pays provider" model without architectural inversion.

## 4. Provider Decision Table
| Provider | Technical Evidence | Commercial Evidence | Decision |
|----------|-------------------|---------------------|----------|
| Digiflazz | Sufficient for further consideration | Live wholesale SKU pricing, minimum deposit, refund terms not fully verified | NOT SELECTED |
| Tripay | Payment gateway verified; official PPOB fulfillment API not verified | Not sufficiently verified | NOT SELECTED |
| DANA | Documented flow does not directly match current Buku Warung fulfillment architecture | Commission, settlement, reserve not verified | NOT SELECTED |

## 5. Provider Analysis
**Digiflazz**: Technical evidence sufficient for further consideration (API structure matches adapter contract). Commercial evidence incomplete: live wholesale SKU pricing, minimum deposit, refund/reversal terms not publicly verifiable.  
**Tripay**: Technical evidence shows payment gateway only; official PPOB/digital-goods fulfillment API not verified in public documentation. Commercial evidence not sufficiently verified.  
**DANA**: Technical evidence shows documented flow does not directly match current Buku Warung fulfillment architecture (reversed DANA-as-aggregator model). Commercial evidence incomplete: commission, settlement, reserve terms not publicly verifiable.

## 6. Commercial Evidence Status
- Digiflazz: Live wholesale pricing, minimum deposit, deposit top-up channels, refund/reversal window — **not publicly verifiable**.  
- Tripay: PPOB API existence, pricing, deposit model — **not publicly verifiable**.  
- DANA: Commission rates, settlement, float/reserve model — **not publicly verifiable**.  
All providers lack sufficient commercial evidence for final selection.

## 7. Technical Evidence Status
- Digiflazz: API structure, idempotency, webhook auth, balance inquiry, error mapping — **Technical evidence is sufficient to demonstrate compatibility with the PR-8 adapter contract, but provider implementation is not authorized until commercial provider selection is completed.**  
- Tripay: No verified PPOB API endpoints — **insufficient**.  
- DANA: API structure but flow direction mismatch — **incompatible without inversion**.

## 8. Operational/Security Evidence
- Digiflazz: IP whitelist, credential structure, HMAC webhook — **partially verified**; SLA, maintenance, reconciliation tools — **requires inquiry**.  
- Tripay: Operational/security evidence irrelevant without verified PPOB API.  
- DANA: Operational/security evidence irrelevant without flow compatibility.

## 9. Decision
**PR-10 STATUS: PASS WITH FOLLOW-UP**  
**DECISION: PROVIDER NOT SELECTED — FOLLOW-UP**  
REASON: No candidate currently has sufficient verified technical AND commercial evidence to authorize provider implementation/production fulfillment.

## 10. Exact Remaining Prerequisites
**DIGIFLAZZ:**  
1. Obtain authenticated wholesale SKU pricing.  
2. Obtain minimum deposit requirement.  
3. Obtain refund/reversal terms.  
4. Confirm commercial/reseller agreement requirements.  

**TRIPAY:**  
1. Obtain official PPOB/digital-goods API documentation if available.  
2. Confirm catalog/inquiry/create/status/callback capabilities.  
3. Confirm pricing and commercial access.  

**DANA:**  
1. Obtain commercial commission terms.  
2. Obtain settlement/reserve terms.  
3. Confirm whether the partner/aggregator flow can support Buku Warung's required fulfillment model without violating the locked architecture.  

## 11. Next Gate
PR-10.1 — Commercial Provider Access / Authenticated Evidence Acquisition  
Purpose: Obtain the missing authenticated/commercial evidence required to make a final provider selection.  
Following evidence acquisition, implement the selected provider adapter after final provider selection.  
PR-10.1 must NOT implement production transactions.  

## 12. Explicit Non-Decisions
- No provider selected.  
- No provider adapter implementation authorized.  
- No production credentials authorized.  
- No production PPOB transaction authorized.  
- No change to PR-6/PR-8 architecture authorized.