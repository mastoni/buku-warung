package id.skmnetwork.bukuwarung.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Step 2 (H) - a "has the first real value arrived" flag.
 *
 * Several screens used a `stateIn(..., initialValue = emptyList())` / `initialValue = 0L` and then
 * rendered that initial value straight away, so a merchant briefly saw "Rp 0" and "Belum ada produk"
 * as if they were real answers while the database was still being read. That is worse than an
 * explicit wait: a shop owner cannot tell a genuinely empty till from a not-yet-loaded one.
 *
 * Attach this to the RAW source flow, before `stateIn`, because a `StateFlow` that already has an
 * initial value would report "loaded" immediately.
 *
 * An empty result is a real result, so the flag goes false on the first emission even when that
 * emission is an empty list - the point is only to cover the gap before the first emission.
 */
fun <T> Flow<T>.loadingFlag(scope: CoroutineScope): StateFlow<Boolean> {
    val flag = MutableStateFlow(true)
    scope.launch {
        try {
            first()
            flag.value = false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A failed read is still a finished attempt: show the empty state rather than a spinner
            // that never stops.
            flag.value = false
        }
    }
    return flag.asStateFlow()
}
