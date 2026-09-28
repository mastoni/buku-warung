package id.skmnetwork.bukuwarung.license

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Gate H.5.1 section 3 - foreground validation.
 *
 * Attached to [androidx.lifecycle.ProcessLifecycleOwner] so that it fires on real foreground
 * transitions of the PROCESS, not on every Activity. That distinction is what makes the trigger safe
 * against Activity recreation, configuration change and multi-Activity navigation: a rotation does
 * not create a new foreground transition, so it cannot issue a duplicate validation.
 *
 * Safety properties, all enforced by [LicenseManager] rather than by this class:
 *  - At most one validation per foreground transition.
 *  - None at all while the last successful validation is still inside the TTL.
 *  - Concurrent transitions collapse onto one HTTP call (single-flight).
 *  - Cancelling [scope] (a composable leaving composition) stops the wait without fabricating a
 *    server error and without touching the entitlement.
 */
class ForegroundLicenseValidationObserver(
    private val licenseManager: LicenseManager,
    private val scope: CoroutineScope
) : LifecycleEventObserver {

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event != Lifecycle.Event.ON_START) return
        scope.launch {
            licenseManager.triggerForegroundValidation()
        }
    }
}
