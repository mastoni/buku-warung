package id.skmnetwork.bukuwarung.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.data.tenant.TenantResolver
import id.skmnetwork.bukuwarung.ui.theme.AppColors

/** Test tag on the tenant-not-ready state, so a composition test can assert on the safe state. */
const val TENANT_NOT_READY_TEST_TAG: String = "tenant-not-ready"

/**
 * P0 - the single place a runtime repository tenant may be decided.
 *
 * `content` runs only once a real, non-blank business id is known, and it receives that id as the
 * tenant. Until then the gate renders [TenantNotReadyState]: no repository is created, no tenant is
 * queried, and in particular `LEGACY_BUSINESS` is never used as a runtime fallback.
 *
 * The previous behaviour in `BukuWarungApp` was `userSettings.businessId.ifBlank { "LEGACY_BUSINESS" }`
 * captured by unkeyed `remember { }` blocks. Because the settings flow was collected with
 * `initialValue = UserSettings()`, the first frame always had a blank business id, so every
 * repository was permanently bound to `LEGACY_BUSINESS` while the database held the merchant's data
 * under the real id. Passing a null [userSettings] while the flow has not emitted keeps "not loaded
 * yet" distinguishable from "loaded, but the identity has not been created yet".
 */
@Composable
fun TenantGate(
    userSettings: UserSettings?,
    modifier: Modifier = Modifier,
    content: @Composable (userSettings: UserSettings, resolvedBusinessId: String) -> Unit
) {
    if (userSettings == null) {
        TenantNotReadyState(modifier)
        return
    }

    val resolvedBusinessId = TenantResolver.resolve(userSettings.businessId)
    if (resolvedBusinessId == null || !TenantResolver.isBindable(resolvedBusinessId)) {
        TenantNotReadyState(modifier)
        return
    }

    content(userSettings, resolvedBusinessId)
}

/**
 * The safe state shown while the active business identity is unknown: a spinner, and no
 * tenant-scoped work of any kind.
 */
@Composable
fun TenantNotReadyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(TENANT_NOT_READY_TEST_TAG),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = AppColors.GreenPrimary)
    }
}
