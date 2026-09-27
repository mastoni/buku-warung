# PHASE 7.5 — GOOGLE PLAY CONSOLE & INTERNAL TESTING CHECKPOINT

## 1. Executive Summary & Release Candidate Identity
- **Application ID:** `id.skmnetwork.bukuwarung`
- **Version Code:** `1`
- **Version Name:** `0.1.0`
- **Target SDK / Compile SDK:** `37` (Android 16 compliance)
- **Min SDK:** `24` (Android 7.0+)
- **Production Artifact (AAB):** `release/bukuwarung-0.1.0-release.aab`
  - Size: 25,739,603 bytes (24.55 MB)
  - SHA-256: `d2515ff5095d865ca285049ae9b616ad269e9ed3566835574d985f8d51ebbd89`
  - Signed: Production RSA 4096-bit key (`> Task :app:signReleaseBundle` verified)
- **Production Artifact (APK):** `release/bukuwarung-0.1.0-release.apk`
  - Size: 38,133,478 bytes (36.37 MB)
  - SHA-256: `7b841ab5e4f13f07b5c7aa294579e88dcd107cd68b410e5d7fcd982f03e2397e`
  - Signature Verification: `apksigner verify --verbose --print-certs` (**Verifies: true**, Scheme v2)
  - Signer SHA-256: `4f1dd37516a069735b1be1b8dd091e820cf173382e4be4b1292cc515e12b0d48`

---

## 2. Store Assets Final Audit
| Asset | File Location | Specifications | Audit Status |
| :--- | :--- | :--- | :--- |
| **App Icon (Play Store)** | `docs/store-assets/bukuwarung-512x512.png` | 512 x 512 PNG, 32-bit | **READY** |
| **Feature Graphic** | `docs/store-assets/feature-graphic-1024x500.png` | 1024 x 500 PNG, 24-bit RGB | **READY** |
| **Phone Screenshots** | `docs/store-assets/screenshots/` (11 screens) | High-res 9:16 PNG from AVD | **READY** |
| **Store Listing Metadata** | `docs/GOOGLE_PLAY_STORE_LISTING.md` | Indonesian titles, short & full text, tags | **READY** |
| **Data Safety Mapping** | `docs/GOOGLE_PLAY_DATA_SAFETY.md` | Explicit itemized form guidance | **READY** |
| **Privacy Policy Draft** | `docs/PRIVACY_POLICY_BUKU_WARUNG.md` | Comprehensive offline-first policy | **READY (DRAFT)** |

---

## 3. Privacy Policy Status
- **Draft Status:** **COMPLETE**. Accurately discloses offline-first local Room storage, optional Google Sheets cloud backup, Camera (ML Kit barcode scanning), Bluetooth/USB thermal receipt printers, and user data deletion.
- **Public URL Status:** `PENDING DEPLOYMENT`. The canonical privacy policy lives at `landing/public/privacy-policy.html` and must be published to `https://bukuwarung.skmnetwork.com/privacy-policy` before final Google Play Console store submission.

---

## 4. Google Play Console Internal Testing Guide

### A. App Creation in Play Console
1. Log in to [Google Play Console](https://play.google.com/console).
2. Click **Create app**:
   - **App name:** `Buku Warung`
   - **Default language:** `Indonesian (id-ID)`
   - **App or game:** `App`
   - **Free or paid:** `Free` (or Paid as per business model)
   - **Package Name:** `id.skmnetwork.bukuwarung`

### B. Store Presence Setup
- **Main Store Listing:** Copy Indonesian text from `docs/GOOGLE_PLAY_STORE_LISTING.md`.
- **Graphics Upload:**
  - App icon: `docs/store-assets/bukuwarung-512x512.png`
  - Feature graphic: `docs/store-assets/feature-graphic-1024x500.png`
  - Phone screenshots: Upload all 11 files from `docs/store-assets/screenshots/`

### C. Policy & App Content
- **Privacy Policy:** Enter your public URL once hosted.
- **App Access:** Select "All functionality is available without special access".
- **Ads:** Select "No, my app does not contain ads".
- **Content Rating:** Complete IARC questionnaire (Target: 3+ / Everyone).
- **Target Audience:** 18 and over.
- **Data Safety:** Fill form following `docs/GOOGLE_PLAY_DATA_SAFETY.md`.

### D. Release to Internal Testing Track
1. Navigate to **Testing** > **Internal testing**.
2. Click **Create new release**.
3. Upload `release/bukuwarung-0.1.0-release.aab`.
4. Release name: `0.1.0 (1)`.
5. Release notes (id-ID):
   ```text
   Rilis perdana Buku Warung versi 0.1.0 untuk Internal Testing.
   Fitur: Kasir POS, Manajemen Stok & Produk, Kulakan, Hutang & Piutang, Buku Kas, Laporan, dan Printer Struk Thermal.
   ```
6. Click **Save** and **Review release**.
7. In the **Testers** tab, create an email list for internal testers and distribute the join link.

---

## 5. Physical Device Validation (Samsung Galaxy A15 / Target Hardware)
Once the internal testing link is generated from Play Console:
1. Testers accept the internal testing invitation via Google Play.
2. Install **Buku Warung** directly from the Google Play Store on **Samsung Galaxy A15**.
3. Verify:
   - Play Store download & clean installation.
   - Launcher icon renders properly with adaptive styling.
   - First launch / Setup / Welcome flow.
   - Core offline transactions (POS checkout, Product management, Purchase, Debt ledger, Cash ledger, Reports).
   - ESC/POS Bluetooth / USB thermal receipt printer printing.
   - No crashes, no ANR, no memory leaks.

---

## 6. Automated Regression Testing
- **Unit Tests:** `.\gradlew.bat testDebugUnitTest --no-daemon` — **BUILD SUCCESSFUL**.
- **Connected Tests:** `.\gradlew.bat connectedDebugAndroidTest --no-daemon` — **144 / 144 PASSED (100%)** on `Pixel_6_API_36` in 16.079s.

---

## 7. Security & Git Audit
- **Git Security:** Verified zero keystores, passwords, or secrets tracked in git (`.gitignore` protects `local.properties`, `*.jks`, `*.keystore`, `*.p12`).
- **Integrity:** SHA-256 checksums documented in `release/SHA256SUMS.txt`.

---

## 8. Final Verdict
**PASS WITH LIMITATION**
*All engineering artifacts (signed AAB `bukuwarung-0.1.0-release.aab`, signed APK, 512x512 icon, 1024x500 feature graphic, 11 real screenshots, Store Listing metadata, Data Safety guide, and 144/144 regression tests) are 100% prepared and validated. The final upload to Google Play Console and public Privacy Policy URL deployment require manual account execution by the developer console owner.*
