package id.skmnetwork.bukuwarung.ui.cash

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Step 7 - the cash ledger contract and the save acknowledgement.
 *
 * Two things are pinned here, both of them properties the ledger depends on for audit integrity:
 *
 *  - the ledger is append-only, and must stay that way;
 *  - a successful save is acknowledged, while a failed one is never acknowledged.
 *
 * The write path itself (tenant scoping, atomicity, sync-queue pairing, derived balance) is
 * asserted from the source, because that behaviour lives in Room and a JVM unit test cannot
 * exercise it. What is *not* asserted here is that a delete or edit was added: if one ever is, this
 * suite fails, which is the point.
 */
class CashTransactionContractTest {

    private val cashScreen = File("src/main/java/id/skmnetwork/bukuwarung/ui/cash/CashScreen.kt")
    private val cashDao = File("src/main/java/id/skmnetwork/bukuwarung/data/local/dao/CashDao.kt")
    private val cashRepository = File("src/main/java/id/skmnetwork/bukuwarung/data/repository/CashRepository.kt")
    private val cashEntity = File("src/main/java/id/skmnetwork/bukuwarung/data/local/entity/CashTransactionEntity.kt")
    private val productViewModel = File("src/main/java/id/skmnetwork/bukuwarung/ui/product/ProductViewModel.kt")

    private fun source(file: File): String = file.readText().replace("\r\n", "\n")

    private fun body(text: String, from: String, to: String): String =
        text.substringAfter(from).substringBefore(to)

    // ---- the ledger is append-only ---------------------------------------------

    @Test
    fun theCashDaoCanOnlyInsert() {
        val dao = source(cashDao)

        assertTrue("The ledger must keep its insert", dao.contains("insertCashTransaction"))
        assertFalse(
            "An UPDATE on a cash ledger row is a correction feature, and this gate must not add one",
            Regex("""@Query\("UPDATE cash_transactions""").containsMatchIn(dao)
        )
        assertFalse(
            "A DELETE on a cash ledger row is a correction feature, and this gate must not add one",
            Regex("""@Query\("DELETE FROM cash_transactions""").containsMatchIn(dao)
        )
        assertFalse("The DAO must not grow an update method", dao.contains("fun updateCashTransaction"))
        assertFalse("The DAO must not grow a delete method", dao.contains("fun deleteCashTransaction"))
    }

    @Test
    fun theRepositoryExposesNoCorrectionEntryPoint() {
        val repo = source(cashRepository)

        assertFalse(
            "The repository must not offer a delete",
            Regex("""fun (delete|remove|void)\w*Cash""").containsMatchIn(repo)
        )
        assertFalse(
            "The repository must not offer an update",
            Regex("""fun update\w*Cash""").containsMatchIn(repo)
        )
    }

    @Test
    fun theLedgerRowCarriesItsAuditIdentity() {
        val entity = source(cashEntity)

        assertTrue("A ledger row is identified by a uuid", entity.contains("val uuid: String"))
        assertTrue("A ledger row is stamped with the tenant that owns it", entity.contains("val businessId: String"))
        assertTrue("A ledger row records which device wrote it", entity.contains("val deviceId: String"))
        assertTrue("A ledger row records when it was written", entity.contains("val createdAt: Long"))
        assertTrue("A ledger row records the amount as an exact integer", entity.contains("val amount: Long"))
        assertTrue(
            "A ledger row links back to whatever produced it",
            entity.contains("val refId: Long?") && entity.contains("val refUuid: String?")
        )
        assertFalse(
            "Unlike a product, a ledger row must not be soft-deletable",
            entity.contains("isDeleted") || entity.contains("is_deleted")
        )
    }

    // ---- write integrity -------------------------------------------------------

    @Test
    fun aTransactionAndItsSyncQueueRowAreWrittenInOneTransaction() {
        val repo = source(cashRepository)

        // Both manual writers must pair the ledger row and the sync row inside one transaction,
        // or a crash between them would leave an unsynced entry with no trace of it.
        listOf("recordManualIncome", "recordManualExpense").forEach { writer ->
            val block = body(repo, "suspend fun $writer(", "    suspend fun")
            assertTrue(
                "$writer must write inside a transaction",
                block.contains("appDatabase.withTransaction {")
            )
            assertTrue(
                "$writer must enqueue its sync row in the same transaction",
                body(block, "appDatabase.withTransaction {", "}\n        }").contains("syncQueueDao.insert(")
            )
        }
    }

    @Test
    fun aTransactionIsStampedWithTheRepositorysTenant() {
        val repo = source(cashRepository)

        assertTrue(
            "The repository takes the tenant, so a row cannot be written for the wrong one",
            repo.contains("private val businessId: String")
        )
        listOf("recordManualIncome", "recordManualExpense").forEach { writer ->
            val block = body(repo, "suspend fun $writer(", "    suspend fun")
            assertTrue(
                "$writer must take its tenant from the repository, never from a parameter",
                block.contains("businessId = businessId")
            )
            assertFalse(
                "$writer must not accept a businessId parameter",
                Regex("""fun $writer\([^)]*businessId""").containsMatchIn(repo)
            )
        }
    }

    @Test
    fun theBalanceIsDerivedFromTheLedgerAndNeverStored() {
        val dao = source(cashDao)

        assertTrue(
            "The balance must be a SUM over the ledger, not a counter that can drift",
            dao.contains("SUM(CASE WHEN type = 'INCOME' THEN amount ELSE -amount END)")
        )
        assertTrue("The balance must be scoped to one tenant", dao.contains("WHERE business_id = :businessId"))
        // Amounts are exact integers end to end; no REAL money anywhere.
        assertFalse("No floating point money", dao.contains("SUM(amount) * 1.0"))
    }

    @Test
    fun anInvalidAmountOrDescriptionIsRejectedBeforeAnythingIsWritten() {
        val repo = source(cashRepository)

        listOf("recordManualIncome", "recordManualExpense").forEach { writer ->
            val block = body(repo, "suspend fun $writer(", "    suspend fun")
            assertTrue("$writer must refuse a non-positive amount", block.contains("if (amount <= 0)"))
            assertTrue("$writer must require a description", block.contains("isEmpty()"))
            assertTrue(
                "$writer must return a failure rather than throwing",
                block.contains("Result.failure(")
            )
        }
    }

    @Test
    fun aFailedWriteIsReportedAsAFailureAndNotAsASuccess() {
        val vm = source(productViewModel)
        val block = body(vm, "fun addManualCash(", "\n}")

        assertTrue("The view model must surface the repository's Result", block.contains("result.fold("))
        assertTrue("A failure must reach the error path", block.contains("onFailure ="))
        assertTrue(
            "The repository returns a failure when the write throws, so nothing partial is committed",
            source(cashRepository).contains("runCatching {")
        )
    }

    // ---- the save flow ---------------------------------------------------------

    @Test
    fun aDuplicateSubmitIsBlockedWhileASaveIsInFlight() {
        val screen = source(cashScreen)
        val submit = body(screen, "if (isSaving) return@Button", "enabled = !isSaving")

        assertTrue("The save must set the in-flight flag", submit.contains("isSaving = true"))
        assertTrue(
            "A second tap while saving must be ignored",
            screen.contains("if (isSaving) return@Button")
        )
        assertTrue("The button must be disabled while saving", screen.contains("enabled = !isSaving"))
        assertTrue("The dialog must not be dismissable while saving", screen.contains("onDismissRequest = { if (!isSaving) showCashDialog = false }"))
        assertTrue("A visible saving state must exist", screen.contains("if (isSaving) \"Menyimpan...\""))
    }

    @Test
    fun aSuccessfulSaveIsAcknowledged() {
        val screen = source(cashScreen)
        val onSuccess = body(screen, "onSuccess = {", "onError = { error ->")

        assertTrue(
            "A recorded transaction must be confirmed to the merchant",
            onSuccess.contains("Toast.makeText(")
        )
        assertTrue(
            "The confirmation must use the app's existing transient pattern, not a new one",
            screen.contains("Toast.LENGTH_SHORT")
        )
        assertTrue(
            "The confirmation must name the recorded side",
            onSuccess.contains("Pemasukan") && onSuccess.contains("Pengeluaran")
        )
        assertTrue(
            "The confirmation must use the existing money formatter, never its own",
            onSuccess.contains("formatRupiah(recordedAmount)")
        )
        assertTrue("The dialog must still close after a success", onSuccess.contains("showCashDialog = false"))
        assertTrue("The form must be cleared after a success", onSuccess.contains("manualAmount = \"\""))
    }

    @Test
    fun aFailedSaveShowsTheErrorAndDoesNotAcknowledge() {
        val screen = source(cashScreen)
        val onError = body(screen, "onError = { error ->", "dismissButton = {")

        assertTrue("A failure must be shown in the dialog", onError.contains("dialogError = error"))
        assertFalse(
            "A failed write must never be acknowledged as saved",
            onError.contains("Toast")
        )
        assertFalse(
            "A failed write must not close the dialog, or the merchant loses their input",
            onError.contains("showCashDialog = false")
        )
    }

    @Test
    fun theListAndBalanceAreDrivenByTheLedgerAndSoRefreshThemselves() {
        val screen = source(cashScreen)

        assertTrue(
            "The list must come from the repository flow, so an insert appears without a manual reload",
            screen.contains("viewModel.cashTransactions.collectAsStateWithLifecycle()")
        )
        assertTrue(
            "The balance must come from the repository flow, so it cannot go stale",
            screen.contains("viewModel.cashBalance.collectAsStateWithLifecycle()")
        )
    }
}
