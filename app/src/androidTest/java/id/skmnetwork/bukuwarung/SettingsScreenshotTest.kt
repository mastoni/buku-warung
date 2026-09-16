package id.skmnetwork.bukuwarung

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.skmnetwork.bukuwarung.backup.BackupRestoreManager
import id.skmnetwork.bukuwarung.backup.MockSheetsTransport
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.license.LicenseManager
import id.skmnetwork.bukuwarung.printer.PrinterService
import id.skmnetwork.bukuwarung.ui.settings.BackupViewModel
import id.skmnetwork.bukuwarung.ui.settings.SettingsScreen
import id.skmnetwork.bukuwarung.ui.theme.BukuWarungTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SettingsScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var licManager: LicenseManager
    private lateinit var printerService: PrinterService
    private lateinit var backupViewModel: BackupViewModel

    @Before
    fun setup() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            database = Room.inMemoryDatabaseBuilder(
                context,
                AppDatabase::class.java
            ).allowMainThreadQueries().build()

            prefsRepo = UserPreferencesRepository(context)
            prefsRepo.saveShopProfile(
                shopName = "Warung Berkah Bu Siti",
                ownerName = "Bu Siti Rohmah",
                phone = "081234567890",
                address = "Jl. Raya Pasar Minggu No. 12"
            )
            prefsRepo.updateBackupInfo(
                lastBackupTimestamp = System.currentTimeMillis() - 3600000L,
                backupSpreadsheetId = "sheet_warung_berkah_12345",
                backupSpreadsheetName = "Buku Warung - Warung Berkah Bu Siti",
                googleAccountEmail = "warungberkah.busiti@gmail.com"
            )
            prefsRepo.setOwnerPin("1234")
            prefsRepo.setPinEnabled(true)

            licManager = LicenseManager(prefsRepo)
            printerService = PrinterService()

            val mgr = BackupRestoreManager(
                database = database,
                userPreferencesRepository = prefsRepo,
                transport = MockSheetsTransport()
            )
            backupViewModel = BackupViewModel(mgr, prefsRepo)
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun saveScreenshot(filename: String) {
        try {
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val tempFile = File(context.cacheDir, filename)
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "cp ${tempFile.absolutePath} /sdcard/Download/$filename"
            ).close()
        } catch (e: Exception) {
            android.util.Log.e("SettingsScreenshotTest", "Failed to save screenshot: $filename", e)
        }
    }

    @Test
    fun test01_CaptureSettingsMain() {
        composeTestRule.setContent {
            BukuWarungTheme {
                SettingsScreen(
                    userPreferencesRepository = prefsRepo,
                    licenseManager = licManager,
                    printerService = printerService,
                    backupViewModel = backupViewModel
                )
            }
        }
        composeTestRule.waitForIdle()
        Thread.sleep(800)
        saveScreenshot("SETTINGS_MAIN_EVIDENCE.png")
    }

    @Test
    fun test02_CaptureSettingsProfile() {
        composeTestRule.setContent {
            BukuWarungTheme {
                SettingsScreen(
                    userPreferencesRepository = prefsRepo,
                    licenseManager = licManager,
                    printerService = printerService,
                    backupViewModel = backupViewModel
                )
            }
        }
        composeTestRule.onNodeWithText("Simpan Profil Warung").performScrollTo()
        composeTestRule.waitForIdle()
        Thread.sleep(800)
        saveScreenshot("SETTINGS_PROFILE_EVIDENCE.png")
    }

    @Test
    fun test03_CaptureSettingsQris() {
        composeTestRule.setContent {
            BukuWarungTheme {
                SettingsScreen(
                    userPreferencesRepository = prefsRepo,
                    licenseManager = licManager,
                    printerService = printerService,
                    backupViewModel = backupViewModel
                )
            }
        }
        composeTestRule.onNodeWithText("Pilih Galeri").performScrollTo()
        composeTestRule.waitForIdle()
        Thread.sleep(800)
        saveScreenshot("SETTINGS_QRIS_EVIDENCE.png")
    }

    @Test
    fun test04_CaptureSettingsPrinter() {
        composeTestRule.setContent {
            BukuWarungTheme {
                SettingsScreen(
                    userPreferencesRepository = prefsRepo,
                    licenseManager = licManager,
                    printerService = printerService,
                    backupViewModel = backupViewModel
                )
            }
        }
        composeTestRule.onNodeWithText("Teks Catatan Kaki Struk").performScrollTo()
        composeTestRule.waitForIdle()
        Thread.sleep(800)
        saveScreenshot("SETTINGS_PRINTER_EVIDENCE.png")
    }

    @Test
    fun test05_CaptureSettingsNotification() {
        composeTestRule.setContent {
            BukuWarungTheme {
                SettingsScreen(
                    userPreferencesRepository = prefsRepo,
                    licenseManager = licManager,
                    printerService = printerService,
                    backupViewModel = backupViewModel
                )
            }
        }
        composeTestRule.onNodeWithText("Pengingat Jatuh Tempo Hutang").performScrollTo()
        composeTestRule.waitForIdle()
        Thread.sleep(800)
        saveScreenshot("SETTINGS_NOTIFICATION_EVIDENCE.png")
    }

    @Test
    fun test06_CaptureSettingsSecurity() {
        composeTestRule.setContent {
            BukuWarungTheme {
                SettingsScreen(
                    userPreferencesRepository = prefsRepo,
                    licenseManager = licManager,
                    printerService = printerService,
                    backupViewModel = backupViewModel
                )
            }
        }
        composeTestRule.onNodeWithText("Ganti PIN").performScrollTo()
        composeTestRule.waitForIdle()
        Thread.sleep(800)
        saveScreenshot("SETTINGS_SECURITY_EVIDENCE.png")
    }

    @Test
    fun test07_CaptureSettingsBackup() {
        composeTestRule.setContent {
            BukuWarungTheme {
                SettingsScreen(
                    userPreferencesRepository = prefsRepo,
                    licenseManager = licManager,
                    printerService = printerService,
                    backupViewModel = backupViewModel
                )
            }
        }
        composeTestRule.onNodeWithText("Cadangkan Sekarang").performScrollTo()
        composeTestRule.waitForIdle()
        Thread.sleep(800)
        saveScreenshot("SETTINGS_BACKUP_EVIDENCE.png")
    }
}
