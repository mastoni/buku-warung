# PHASE 6B — BUKU WARUNG UI/UX DESIGN MASTER AUDIT

**Date**: 2026-09-12  
**Status**: 📋 **AUDIT COMPLETE — WAITING FOR REVIEW**  
**Mode**: Audit Only (Zero Source Code Changes)  
**Reference Asset**: [`docs/design-master.png`](file:///e:/Android%20Project/BukuWarungKotlin/docs/design-master.png)  

---

## 1. Executive Summary & Current UI State

This audit compares the current Compose implementation of **Buku Warung** against the authoritative visual reference ([`docs/design-master.png`](file:///e:/Android%20Project/BukuWarungKotlin/docs/design-master.png)).

The core underlying application architecture (Room Database v9, atomic accounting, immutable transactions, hardware printer integration, and DataStore preferences) is fully locked, stable, and regression-free. However, multiple UI screens—especially the **Welcome Screen** and **Home Screen**—exhibit noticeable visual and layout divergence from the clean, warm, friendly "Warung Modern" aesthetic established in the Design Master.

---

## 2. Design Master Core Principles

The Design Master establishes the following visual language:
1. **Target Persona**: Everyday Indonesian warung / toko kelontong owners (friendly, clear, effortless, non-intimidating).
2. **Color Palette**: 
   - Primary Green (`#0B9F57`), Dark Forest Green text accents (`#087A43`).
   - Clean White background (`#FFFFFF`) with soft mint-tinted surfaces (`#F7FCF9`).
   - Pastel category accents: Soft Green (`#E8F7EF`), Soft Pink/Red (`#FFE8E8`), Soft Blue (`#E8F3FF`), Soft Orange (`#FFF1DD`), Soft Purple (`#F3E8FF`), Soft Teal (`#E6FFFB`).
3. **Typography**: Clean modern sans-serif with strong title hierarchy, warm emoji accents (`👋`, `💚`), and unambiguous Indonesian labels.
4. **Cards & Corner Radii**: Highly rounded corners (16dp cards, 20dp chips, 24dp pills), subtle 1dp borders (`#E0E0E0`), and soft elevation.
5. **No Enterprise ERP Bloat**: Generous spacing, clear iconography inside colored circular/rounded containers, concise metrics, and single-purpose action flows.

---

## 3. Screen-by-Screen Detailed Comparison

### Screen 1: Splash / Welcome Screen
| Element | Design Master Reference | Current Implementation | Severity |
|---|---|---|---|
| **Top Logo & Branding** | Centered Green rounded square storefront logo + Bold "Buku Warung" + "Pembukuan Warung Kecil" subtitle at top. | Programmatic awning hero artwork is placed at top; logo is pushed to the middle. | **P1** |
| **Hero Illustration** | Hand-drawn warung storefront illustration (smiling female warung owner, wooden counter, snack display, trees). | Programmatic Canvas/Box artwork with 4 floating badges and 2 hanging boards; feels artificial and cluttered. | **P1** |
| **Slogan / Value Prop** | Stylized 3-line tagline: *"Catat Jualan / Kelola Keuangan / Usaha Makin Maju!"* | Standard text: *"Catat jualan lebih mudah. Kelola warung lebih rapi."* | **P2** |
| **Feature Carousel / Chips** | *Not present on splash* (kept purely clean and uncluttered). | 5 interactive icon columns with selection states and 3 paging dots. | **P1** |
| **Primary CTA Button** | Full-width rounded green button: `Mulai Sekarang ->`. | Green button present, but overshadowed by secondary action and feature chips. | **P2** |
| **Trust / Storage Card** | Bottom soft pill card: `[ Google Sheets Icon ] Data Anda tersimpan di Google Sheets Anda sendiri`. | Replaced with `[ Security Icon ] Sekali bayar, untuk warung Anda selamanya.` | **P1** |
| **Language Selector** | *Not in Splash* (clean top margin). | Top-right `Indonesia ▾` dropdown button occupying status bar area. | **P2** |

---

### Screen 2: Google Login Screen
| Element | Design Master Reference | Current Implementation | Severity |
|---|---|---|---|
| **Header** | Top Buku Warung logo + subtitle. | Currently absent; onboarding jumps directly from Welcome to Setup dialog without a dedicated Google sync intro step. | **P1** |
| **Central Visual** | 3D/Flat Google Sheets spreadsheet icon with floating cloud. | N/A (Screen not rendered in standalone onboarding route). | **P1** |
| **Google Sign-In CTA** | White card with Google "G" logo: `[G] Lanjut dengan Google`. | N/A (Google Drive sync currently resides exclusively in Settings). | **P2** |
| **Feature Checklist** | 4-point green checkmark card: *Aman dan gratis*, *Data milik Anda sendiri*, *Bisa diakses kapan saja*, *Tidak perlu server mahal*. | N/A. | **P2** |

---

### Screen 3: Home / Beranda
| Element | Design Master Reference | Current Implementation | Severity |
|---|---|---|---|
| **Header Bar** | Top-left Buku Warung logo + subtitle; Top-right Notification Bell + Store Photo Avatar with verified green badge. | TopAppBar with `shopName` text title and generic sync icon button. | **P1** |
| **Greeting & Date** | *"Selamat pagi,"* + **Warung Bu Siti 👋** + *"Selasa, 9 September 2025"* (Indonesian day & date). | *"Selamat datang,"* + **$shopName 👋**; missing Indonesian date. | **P2** |
| **Summary 2x2 Grid** | 4 soft pastel cards with colored icons: Penjualan (`#E8F7EF`, cart icon, amount, tx count), Pengeluaran (`#FFE8E8`, expense icon, amount, tx count), Saldo Kas (`#E8F3FF`, wallet icon, amount), Barang Hampir Habis (`#FFF1DD`, warning icon, count). | 4 cards present, but text-heavy without icons and missing transaction counts. | **P1** |
| **Main Menu Grid** | Colorful pastel container icons arranged in neat rows (4 items per row in reference). Icons sit inside colored rounded squares. | 2-column large cards with centered green monochromatic icons. | **P1** |
| **Bottom Navigation Bar** | 5 destinations: `Beranda`, `Transaksi`, `(+) Quick FAB`, `Laporan`, `Lainnya/Pengaturan`. | Bottom navigation bar is rendered externally by `MainScreen`, but missing floating center (+) action button. | **P2** |

---

### Screen 4: Jualan / Kasir (POS)
| Element | Design Master Reference | Current Implementation | Severity |
|---|---|---|---|
| **Top Bar** | Back arrow `<-`, Title "Jualan (Kasir)", Barcode scanner icon, 3-dots menu. | Top bar has title and actions; consistent. | **P2** |
| **Search & Scan** | Rounded search bar with inline barcode icon shortcut. | Search textfield with separate barcode scanner modal button. | **P2** |
| **Category Chips** | Horizontal scroll: `Semua`, `Makanan`, `Minuman`, `Sembako`. | Implemented with `LazyRow` filter chips. | **PASS** |
| **Product Items** | Image thumbnail on left, Name, Price, Stock, round green `(+)` button on right. | Implemented with thumbnail, qty increment/decrement stepper, and price. | **PASS** |
| **Bottom Cart Sheet** | Collapsible floating cart bar: `[Cart] 3 item | Rp 24.500 [^]` + Big green CTA `BAYAR SEKARANG ->`. | Implemented with bottom summary card and checkout action. | **PASS** |

---

### Screen 5: Produk & Stok
| Element | Design Master Reference | Current Implementation | Severity |
|---|---|---|---|
| **Top Bar & Search** | Title "Produk & Stok", 3-dots menu, search bar with category dropdown filter. | Implemented with search bar and category chips. | **PASS** |
| **Product List** | Product thumbnail, Name, Price, Stock, item 3-dots menu. | Implemented with thumbnail, name, price, stock, and 3-dots menu. | **PASS** |
| **Bottom Action Button** | Full-width bottom floating green button: `+ Tambah Produk`. | Implemented with `ExtendedFloatingActionButton` (`+ Tambah Produk`). | **PASS** |

---

### Screen 6: Pembelian (Belanja)
| Element | Design Master Reference | Current Implementation | Severity |
|---|---|---|---|
| **Period Selector** | `[ Calendar Icon ] September 2025 v`. | Implemented with current month banner. | **PASS** |
| **Item Row Stepper** | Image, Name, `[- 10 +]` quantity stepper, unit price, subtotal, trash icon. | Implemented with quantity stepper and item subtotal. | **PASS** |
| **Tabs & History** | Seamless toggle between New Purchase and Purchase History. | Implemented in Phase 6A.2 with `Belanja Baru` vs `Riwayat Belanja`. | **PASS** |
| **Bottom Bar** | Total Belanja `Rp 310.000` + `Simpan Belanja` green button. | Implemented with payment toggle (Tunai / Hutang) + Primary CTA. | **PASS** |

---

### Screen 7: Uang Kas
| Element | Design Master Reference | Current Implementation | Severity |
|---|---|---|---|
| **Filter Chips** | `Semua`, `Pemasukan`, `Pengeluaran`. | Implemented with horizontal filter chips. | **PASS** |
| **Saldo Card** | Green wallet icon + "Saldo Kas" + large bold rupiah amount. | Implemented with `Saldo Kas` card. | **PASS** |
| **Dual Quick Actions** | Side-by-side or stacked buttons: `+ Tambah Pemasukan` (Green), `+ Tambah Pengeluaran` (Red/Pink). | Implemented with dual action buttons. | **PASS** |
| **Recent Transactions** | "Transaksi Terbaru" section with "Lihat Semua", transaction rows with colored delta amounts (`+ Rp 385.000` green, `- Rp 125.000` red). | Implemented with transaction history list. | **PASS** |

---

### Screen 8: Laporan (Reports)
| Element | Design Master Reference | Current Implementation | Severity |
|---|---|---|---|
| **Metric Grid (2x2)** | 4 distinct cards: Total Penjualan (Green), Total Transaksi (Blue), Total Pengeluaran (Orange), Perkiraan Laba (Purple). | Implemented with financial summary metrics. | **PASS** |
| **Sales Chart** | Clean daily bar chart ("Penjualan Harian") with date labels and peak tooltip. | Metric breakdowns present; chart visualization can be refined. | **P2** |
| **Top Selling List** | "Produk Terlaris" numbered badges (1, 2, 3), thumbnails, units sold, total revenue. | Implemented with top product rankings. | **PASS** |

---

### Screen 9 & 10: Pelanggan & Piutang / Supplier & Hutang
| Element | Design Master Reference | Current Implementation | Severity |
|---|---|---|---|
| **Search & Add** | Search input + Outlined pill button `+ Tambah Pelanggan` / `+ Tambah Supplier`. | Implemented with search and add dialogs. | **PASS** |
| **Contact Row Avatars** | Circular avatar with customer/supplier initial letter in pastel colors (B, A, S, R, T). | Implemented with contact initial avatars and debt breakdown. | **PASS** |
| **Debt Status** | Outstanding balance in bold, chevron `>` leading to transaction detail/payment. | Implemented with atomic repayment modals. | **PASS** |

---

### Screen 11: Pengaturan (Settings)
| Element | Design Master Reference | Current Implementation | Severity |
|---|---|---|---|
| **Profile Card** | Shop photo/avatar, Shop Name, Email, chevron `>`. | Implemented with shop name header and security PIN status. | **P2** |
| **Menu Groups** | Grouped icon items: `Spreadsheet Saya`, `Cadangkan Data`, `Sinkronisasi`, `Tentang Aplikasi`, `Bantuan & FAQ`, `Kebijakan Privasi`, `Keluar`. | Implemented with structured settings list (Printer, Backup, PIN, License). | **PASS** |

---

## 4. Priority Classification of UI/UX Gaps

### P0 (Blocks Usability):
- *None*. All business workflows (POS, Product CRUD, Purchase, Receivables, Payables, Cash Flow, Printer, Backup Engine) are 100% functional.

### P1 (Major Visual / UX Divergence from Design Master):
1. **Welcome Screen Structure**:
   - Programmatic Canvas awning and floating badges diverge from the Design Master's clean illustration and top-aligned logo hierarchy.
   - Missing the bottom Google Sheets trust card.
   - Unnecessary language selector and interactive 5-column feature picker create visual clutter.
2. **Home Screen Visual Styling**:
   - Summary cards lack icons and transaction count badges.
   - Menu grid uses large generic 2-column cards with monochromatic green icons rather than friendly, colorful pastel-backed icon tiles.
   - TopAppBar lacks the warm shop branding, avatar, and formatted Indonesian day/date.

### P2 (Polish / Minor Alignment):
1. **Bottom Navigation**: Add center quick-action FAB if full bottom navigation parity is targeted.
2. **Laporan Bar Chart**: Stylize daily sales bar heights with soft rounded bars.
3. **Typography & Font Weighting**: Fine-tune font weights and spacing across secondary metadata rows.

---

## 5. Detailed Welcome Screen Gap Analysis

```
DESIGN MASTER SPLASH HIERARCHY:
┌──────────────────────────────────────────────┐
│  [ Storefront Icon (Green) ]                 │
│  Buku Warung                                 │
│  Pembukuan Warung Kecil                      │
│                                              │
│  "Catat Jualan, Kelola Keuangan,             │
│   Usaha Makin Maju!"                         │
│                                              │
│  [ Clean Warung Storefront Illustration ]    │
│                                              │
│  [========= Mulai Sekarang -> ==============]│
│                                              │
│  [ 📊 Data Anda tersimpan di Google Sheets ] │
└──────────────────────────────────────────────┘
```

**Specific Welcome Screen Flaws in Current Code**:
1. **Inverted Hierarchy**: The hero art is placed above the brand name and logo, pushing the app identity to the center.
2. **Artificial Artwork**: Programmatic awning boxes and text boards look like generic geometric shapes rather than a warm warung illustration.
3. **Redundant Feature Carousel**: Five feature selector icons with indicator dots duplicate what the user will see immediately on the Home screen.
4. **Secondary CTA Overload**: Secondary "Lihat Cara Kerjanya" and "Sekali bayar..." clutter the onboarding viewport on standard phone heights (requires scrolling).

---

## 6. Detailed Home Screen Gap Analysis

```
DESIGN MASTER HOME HIERARCHY:
┌──────────────────────────────────────────────┐
│ [Logo] Buku Warung                 [Bell][Img]
│                                              │
│ Selamat pagi,                                │
│ Warung Bu Siti 👋                             │
│ Selasa, 9 September 2025                     │
│                                              │
│ ┌──────────────────────┐┌──────────────────┐ │
│ │ 🛒 Penjualan Hari Ini ││ 💸 Pengeluaran   │ │
│ │ Rp 385.000           ││ Rp 125.000       │ │
│ │ 12 transaksi         ││ 3 transaksi      │ │
│ └──────────────────────┘└──────────────────┘ │
│ ┌──────────────────────┐┌──────────────────┐ │
│ │ 💳 Saldo Kas         ││ ⚠️ Stok Menipis   │ │
│ │ Rp 2.350.000         ││ 3 barang         │ │
│ └──────────────────────┘└──────────────────┘ │
│                                              │
│ Menu Utama                                   │
│  [🛒 Jualan]  [📦 Produk]  [🏪 Belanja]  [👥 Pelanggan]
│  [🚚 Supplier][💵 Kas]     [📊 Laporan]  [⚙️ Pengaturan]
└──────────────────────────────────────────────┘
```

**Specific Home Screen Flaws in Current Code**:
1. **Header Bar**: Displays `shopName` as standard raw text instead of the branded logo header.
2. **Date Header**: Missing Indonesian formatted day and date string (`EEEE, d MMMM yyyy`).
3. **Summary Cards**: Rendered with plain text numbers without descriptive icons inside colored container cards.
4. **Menu Grid**: Generic 2-column cards take up excessive vertical space; should use friendly, compact rounded tiles with colorful icon backgrounds.

---

## 7. Menu Structure Recommendation

### Finding on Menu Count:
- The Design Master illustration groups menus into 7 tiles (with Supplier embedded).
- In Phase 6A.1, Supplier & Hutang was cleanly separated into an independent business screen, resulting in **8 core menus**:
  1. **Jualan** (`POS`)
  2. **Produk & Stok** (`PRODUCTS`)
  3. **Pembelian** (`PURCHASE`)
  4. **Pelanggan & Piutang** (`CUSTOMERS`)
  5. **Supplier & Hutang** (`SUPPLIERS`)
  6. **Uang Kas** (`CASH`)
  7. **Laporan** (`REPORTS`)
  8. **Pengaturan** (`SETTINGS`)

### Recommendation:
- **Preserve all 8 menus**. The 8-menu structure represents true business domain boundaries.
- Arrange the 8 menus in a **4x2 or 2-column compact grid** using colorful pastel icon containers matching the Design Master styling:
  - Row 1: `Jualan` (Green), `Produk & Stok` (Blue), `Pembelian` (Orange), `Pelanggan & Piutang` (Purple)
  - Row 2: `Supplier & Hutang` (Teal), `Uang Kas` (Pink/Red), `Laporan` (Cyan), `Pengaturan` (Slate Gray)
- This preserves 100% of the Phase 6A business features while aligning visual aesthetics with the Design Master.

---

## 8. Responsive & Layout Considerations

1. **Screen Height Adaptability**:
   - On compact screens (e.g. 320x640 / 360x640), Welcome Screen must fit without mandatory vertical scrolling.
   - Home Screen menu tiles must scale cleanly in 4-column or 2-column layouts using adaptive grid weights.
2. **Safe Insets**:
   - Proper `statusBarsPadding()` and `navigationBarsPadding()` ensure no clipping on gesture navigation bars and camera punch-holes.

---

## 9. Recommended Minimal UI Changes (For Subsequent Implementation Phase)

1. **WelcomeScreen.kt**:
   - Align layout to Design Master: Top Logo + Slogan + Warm Illustration + Green CTA + Google Sheets Trust Pill.
   - Remove redundant language picker and 5-feature carousel.
2. **HomeScreen.kt**:
   - Add Indonesian day/date subtitle (`EEEE, d MMMM yyyy`).
   - Add colored icons and transaction counts to the 4 summary cards.
   - Update `MenuGrid` to use pastel rounded icon backgrounds for all 8 menus.
3. **Components & Theme**:
   - Add pastel background color tokens to `AppColors` (`IconBgGreen`, `IconBgBlue`, `IconBgOrange`, `IconBgPurple`, `IconBgTeal`, `IconBgRed`, `IconBgGray`).

---

## 10. Exact Implementation Order (For Future Phases)

1. **Step 1 — Design Tokens**: Add pastel icon color tokens in `AppDesignSystem.kt`.
2. **Step 2 — Welcome Screen Realignment**: Update `WelcomeScreen.kt` to match Screen 1 layout hierarchy.
3. **Step 3 — Home Screen Realignment**: Update `HomeScreen.kt` header, summary cards, and 8-menu pastel icon tiles.
4. **Step 4 — Automated & Manual AVD Verification**: Run all unit tests, instrumented tests (144 tests), and visual smoke tests on AVD `Pixel_6_API_36`.

---

## 11. Safety & Non-Regressed Protected Domains

- ❌ Room schema version: **Locked at 9** (zero migrations).
- ❌ POS / Accounting transactions: **Untouched**.
- ❌ Backup / Google Sheets engine: **Untouched**.
- ❌ Printer hardware services: **Untouched**.
- ❌ License / Security PIN: **Untouched**.

---

## 12. Final Verdict

# **AUDIT COMPLETE — WAITING FOR REVIEW**

**Changes Made in this Phase**: **NONE** (Audit Only).
