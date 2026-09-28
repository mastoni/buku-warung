package id.skmnetwork.bukuwarung.ui.customer

import id.skmnetwork.bukuwarung.data.local.entity.DebtEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

/**
 * Step 8A - the payment target shown in the pay-debt dialog.
 *
 * The dialog used to settle `openDebts.first()` directly. A customer can hold several open debts
 * (one row per credit sale), so that silently settled whichever row sorted first. The dialog now
 * resolves its target through [resolveDebtPaymentTarget] and renders that debt's reference, date
 * and amounts before the merchant confirms, so the debt being paid is always identifiable.
 *
 * The important property is the absence of a fallback: with several open debts and no choice, there
 * is no target and the confirm button stays disabled. No FIFO/LIFO/oldest-first rule is invented -
 * the merchant is asked instead - and a customer with exactly one open debt keeps the flow it had.
 */
class ResolveDebtPaymentTargetTest {

    private fun debt(id: Long, createdAt: Long) = DebtEntity(
        id = id,
        uuid = UUID.randomUUID().toString(),
        businessId = "TEST-BUSINESS-001",
        customerId = 1L,
        totalDebt = 100_000L * id,
        paidAmount = 0L,
        status = "OPEN",
        createdAt = createdAt
    )

    // Debt #1 (older) and Debt #2 (newer) for one customer.
    private val debt1 = debt(id = 1L, createdAt = 1_000L)
    private val debt2 = debt(id = 2L, createdAt = 2_000L)

    @Test
    fun `no open debt means no payment target`() {
        assertNull(resolveDebtPaymentTarget(emptyList(), selectedDebtId = null))
        assertNull(resolveDebtPaymentTarget(emptyList(), selectedDebtId = 1L))
    }

    @Test
    fun `a single open debt is the target without any choice`() {
        // One open debt is not a choice, so the existing single-debt flow is unchanged.
        assertEquals(debt1, resolveDebtPaymentTarget(listOf(debt1), selectedDebtId = null))
        assertEquals(debt1, resolveDebtPaymentTarget(listOf(debt1), selectedDebtId = 2L))
    }

    @Test
    fun `several open debts and no choice yield no target`() {
        // This is what removed the implicit `openDebts.first()`: the payment cannot be aimed at a
        // debt the merchant never identified.
        assertNull(resolveDebtPaymentTarget(listOf(debt1, debt2), selectedDebtId = null))
    }

    @Test
    fun `the chosen debt is the target when several debts are open`() {
        val open = listOf(debt1, debt2)
        assertEquals("Debt #1 must be the target when it was chosen", debt1, resolveDebtPaymentTarget(open, 1L))
        assertEquals("Debt #2 must be the target when it was chosen", debt2, resolveDebtPaymentTarget(open, 2L))
    }

    @Test
    fun `the target id is never redirected to another open debt`() {
        // The id the dialog hands to payDebt must be exactly the selected one, for every debt the
        // merchant can pick, in both list orders.
        val orders = listOf(listOf(debt1, debt2), listOf(debt2, debt1))
        for (open in orders) {
            for (candidate in open) {
                val resolved = resolveDebtPaymentTarget(open, candidate.id)
                assertEquals(
                    "A selection must resolve to the very debt that was selected",
                    candidate.id,
                    resolved?.id
                )
            }
        }
    }

    @Test
    fun `a selection that is no longer open is not silently replaced`() {
        // Debt #1 was paid away while Debt #2 is still open. With only one open debt left there is
        // no ambiguity, so the remaining debt is the target.
        val open = listOf(debt2)
        assertEquals(debt2, resolveDebtPaymentTarget(open, selectedDebtId = 1L))

        // But with several open debts a stale selection leaves the payment untargeted rather than
        // aiming it at a sibling the merchant never chose.
        assertNull(resolveDebtPaymentTarget(listOf(debt1, debt2), selectedDebtId = 99L))
    }

    @Test
    fun `a target is resolved for every selection once a debt is chosen`() {
        val many = (1L..5L).map { debt(it, createdAt = it * 1000L) }
        for (candidate in many) {
            val resolved = resolveDebtPaymentTarget(many, candidate.id)
            assertEquals(candidate.id, resolved?.id)
        }
    }
}
