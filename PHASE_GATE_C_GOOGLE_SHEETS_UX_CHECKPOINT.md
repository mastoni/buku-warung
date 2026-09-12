# PHASE GATE C — GOOGLE SHEETS BACKUP & RESTORE UX CHECKPOINT

**Date:** 2026-09-12  
**Target Platform:** Pixel_6_API_36 (AVD) / Samsung Galaxy A15  
**Final Verdict:** **PASS WITH LIMITATION** (UX & SPI Architecture Fully Verified; Real Google Cloud OAuth credentials pending external cloud project setup)

---

## 1. Scope & Governance Compliance

- **Objective:** Implement user-facing Google Sheets Backup & Restore interface and ViewModel in `SettingsScreen` without modifying Room schema, canonical 17-tab export format, checksum calculation, or SyncQueue architecture.
- **Constraints Maintained:**
  - ✅ Zero Room schema changes / Zero Room migrations added.
  - ✅ Preserved existing 17-tab canonical snapshot architecture (`BackupRestoreManager`, `CanonicalSerializer`, `BackupValidator`).
  - ✅ Preserved SHA-256 deterministic checksum and atomic Room restore transaction.
  - ✅ No new external dependencies added.
  - ✅ User-facing Indonesian language without technical jargon (no `checksum`, `hash`, `API`, `token`, `payload`, `Room`, `UUID`).
  - ✅ Destructive restore protected with explicit user confirmation dialog (`Batal` / `Pulihkan`).
  - ✅ Offline-first architecture preserved 100% — Zero network calls during screen initialization.

---

## 2. Files Changed & Added

1. **`app/src/main/java/id/skmnetwork/bukuwarung/data/preferences/UserPreferencesRepository.kt`**:
   - Added `lastBackupTimestamp: Long = 0L`, `backupSpreadsheetId: String = ""`, and `googleAccountEmail: String = ""` to `UserSettings`.
   - Added DataStore keys: `LAST_BACKUP_TIMESTAMP`, `BACKUP_SPREADSHEET_ID`, `GOOGLE_ACCOUNT_EMAIL`.
   - Implemented `updateBackupInfo(lastBackupTimestamp, backupSpreadsheetId, googleAccountEmail)` helper.

2. **`app/src/main/java/id/skmnetwork/bukuwarung/ui/settings/BackupViewModel.kt`** *(NEW)*:
   - Implemented `BackupViewModel` & `BackupViewModelFactory`.
   - Managed state machines `BackupOpState` (`Idle`, `Loading`, `Success`, `Error`) and `RestoreOpState` (`Idle`, `Loading`, `Success`, `Error`).
   - Mapped technical exceptions (`ChecksumMismatchException`, `CorruptedBackupException`, `IOException`, `SecurityException`) to friendly user messages.

3. **`app/src/main/java/id/skmnetwork/bukuwarung/ui/settings/SettingsScreen.kt`**:
   - Added Section 10: "10. Cadangan & Pemulihan" with friendly subtitle: *"Amankan data warung Anda di Google Sheets."*
   - Status card displaying formatted last backup date (`SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))`) or `"Belum pernah dicadangkan"`.
   - Integrated "Cadangkan Sekarang" (PrimaryButton) and "Pulihkan Data" (OutlinedButton).
   - Added `AlertDialog` confirmation dialog before executing restore.
   - Renumbered "Tentang Aplikasi" to Section 11.

4. **`app/src/main/java/id/skmnetwork/bukuwarung/ui/navigation/AppNavigation.kt`**:
   - Instantiated `BackupRestoreManager` and `BackupViewModel` via factory and wired into `SettingsScreen`.

5. **`app/src/androidTest/java/id/skmnetwork/bukuwarung/SettingsBackupRestoreTest.kt`** *(NEW)*:
   - Automated instrumented test suite covering all 9 gate requirements (A through I).

---

## 3. Backup UX Flow

- User navigates to **Pengaturan** → Section 10 **"10. Cadangan & Pemulihan"**.
- User views last backup status: `"Belum pernah dicadangkan"` when timestamp is `0L`.
- User taps **"Cadangkan Sekarang"**:
  - UI transitions to loading state (`"Sedang mencadangkan data..."` with progress indicator).
  - Background coroutine exports Room entities to 17-tab canonical snapshot and calls `BackupRestoreManager.performBackup()`.
  - On success: DataStore updates `lastBackupTimestamp`, UI displays green feedback banner and formatted timestamp.
  - On failure: UI displays red user-friendly error message, leaving local Room database 100% intact.

---

## 4. Restore UX Flow & Destructive Safety

- User taps **"Pulihkan Data"**.
- System intercepts action and presents an explicit confirmation dialog:
  - **Judul:** *"Pulihkan Data Cadangan?"*
  - **Pesan:** *"Data di perangkat ini akan diganti dengan data dari cadangan."*
  - **Pilihan:** `"Batal"` and `"Pulihkan"`.
- If user taps `"Batal"`: Dialog dismisses immediately with zero mutation to local database.
- If user taps `"Pulihkan"`:
  - UI enters loading state (`"Sedang memulihkan data..."`).
  - `BackupRestoreManager.performRestore()` executes Pre-Validation (`BackupValidator.validate()`).
  - If validation fails (e.g. Tampered data or Checksum mismatch): Exception is caught, local Room database is **NOT mutated**, and UI renders friendly error: *"Data cadangan di Google Sheets telah diubah atau rusak. Pemulihan dibatalkan demi keamanan data."*
  - If validation succeeds: Atomic Room transaction restores tables in topological order.

---

## 5. Security & Persistence Verification

- **DataStore Storage:** Only stores `lastBackupTimestamp: Long`, `backupSpreadsheetId: String`, and `googleAccountEmail: String`.
- **Zero Secrets in Preferences:** No access tokens, refresh tokens, client secrets, or user passwords stored in DataStore.
- **PIN & Security:** Owner PIN hashing (Salted SHA-256) remains decoupled and safe.
- **Authoritative Boundary:** Room database remains the authoritative source of truth.

---

## 6. Automated Test Suite Results

### A. Unit Tests (`./gradlew.bat testDebugUnitTest`)
- **Status:** `BUILD SUCCESSFUL`

### B. Connected Android Tests (`./gradlew.bat connectedDebugAndroidTest`)
- **Device:** `Pixel_6_API_36(AVD) - 16`
- **Total Tests Executed:** All suites passed (including `SettingsBackupRestoreTest` with 9 requirement tests).
- **Status:** `BUILD SUCCESSFUL`

| Test Name | Requirement Covered | Result |
|---|---|---|
| `testRequirementA_SettingsInitialStateAndPreferencesDefaults` | Requirement A (Settings section & defaults) | **PASS** |
| `testRequirementB_StatusNeverBackedUpWhenTimestampZero` | Requirement B ("Belum pernah dicadangkan") | **PASS** |
| `testRequirementC_BackupLoadingAndSuccessState` | Requirement C (Backup loading & success) | **PASS** |
| `testRequirementD_BackupSuccessUpdatesTimestampAndPreferences` | Requirement D (Timestamp persistence) | **PASS** |
| `testRequirementE_BackupFailureDisplaysFriendlyErrorWithoutMutatingLocalData` | Requirement E (Friendly error, zero mutation) | **PASS** |
| `testRequirementF_G_CancelRestoreDoesNotModifyData` | Requirement F & G (Confirmation dialog & Cancel) | **PASS** |
| `testRequirementH_RestoreFailureKeepsLocalDatabaseIntact` | Requirement H (Atomic pre-validation & zero rollback) | **PASS** |
| `testRequirementI_OfflineSettingsNoAutomaticNetworkCall` | Requirement I (Zero automatic network calls) | **PASS** |

---

## 7. Manual AVD Smoke Test Validation (Pixel_6_API_36)

| Step | Action | Expected Behavior | Actual Behavior | Result |
|---|---|---|---|---|
| 1 | Open Pengaturan | Section 10 "Cadangan & Pemulihan" visible | Section 10 visible with icon & subtitle | **PASS** |
| 2 | Check Initial Status | "Belum pernah dicadangkan" shown | "Cadangan Terakhir: Belum pernah dicadangkan" | **PASS** |
| 3 | Tap "Pulihkan Data" | Confirmation dialog shown | Dialog "Pulihkan Data Cadangan?" appears | **PASS** |
| 4 | Tap "Batal" | Dialog dismissed, no changes | Dialog closed, state remained clean | **PASS** |
| 5 | Tap "Pulihkan" (in dialog) | Loading state & safe error handling | Loading shown; user-friendly error rendered, local DB intact | **PASS** |
| 6 | Tap "Cadangkan Sekarang" | Triggers backup flow | Backup triggered, progress feedback rendered | **PASS** |
| 7 | Verify Offline Behavior | App opens and functions offline | Zero network requirement on launch or viewing settings | **PASS** |

---

## 8. Real Google API Limitation & Known Limitations

- **Real Google OAuth Limitation:**
  - Real Google Cloud Console project OAuth Client ID and SHA-1 signing certificate fingerprint must be configured by the owner in Google Cloud Console before live production spreadsheet synchronization can take place.
  - The UI and transport SPI architecture (`SheetsBackupTransport`, `MockSheetsTransport`, `GoogleSheetsApiTransport`) are fully wired and verified.
- **Verdict Rationale:**
  - In accordance with governance instructions, because live Google Cloud OAuth credentials have not been configured for live end-to-end cloud sync, the verdict is accurately declared as **PASS WITH LIMITATION** rather than a fake unqualified pass.

---

## 9. Final Verdict

**FINAL VERDICT: PASS WITH LIMITATION**  
Gate C Google Sheets Backup & Restore UX is implemented, thoroughly tested, and verified.
Stopping immediately as instructed. Do not proceed to Gate D.
