export type DocCategoryKey =
  | 'instalasi'
  | 'mulai'
  | 'inventori'
  | 'pos'
  | 'pembelian'
  | 'keuangan'
  | 'hardware'
  | 'keamanan'
  | 'bantuan';

export interface DocCategoryMeta {
  key: DocCategoryKey;
  name: string;
  description: string;
  iconName: string;
}

export interface DocStep {
  title: string;
  description: string;
}

export interface DocCallout {
  type: 'info' | 'warning' | 'tip';
  title: string;
  text: string;
}

export interface DocExample {
  title: string;
  scenario: string;
  details: string[];
}

export interface DocSection {
  title: string;
  paragraphs: string[];
  listItems?: string[];
}

export interface DocScreenshot {
  src: string;
  caption: string;
}

export interface DocArticle {
  slug: string;
  title: string;
  category: DocCategoryKey;
  categoryName: string;
  order: number; // 0 for Bab 0, 1..24 for Chapters 1..24
  chapterLabel?: string;
  description: string;
  version: string;
  updatedAt: string;
  readTime: string;
  summary: string;
  targetAudience?: string;
  features?: string[];
  sections: DocSection[];
  steps?: DocStep[];
  callouts?: DocCallout[];
  example?: DocExample;
  screenshot?: DocScreenshot;
  limitations?: string[];
  relatedSlugs: string[];
  keywords: string[];
}

export const DOC_CATEGORIES: DocCategoryMeta[] = [
  {
    key: 'instalasi',
    name: 'Instalasi & Aktivasi',
    description: 'Panduan pemasangan file APK resmi Android dan aktivasi lisensi.',
    iconName: 'Download',
  },
  {
    key: 'mulai',
    name: 'Mulai Menggunakan',
    description: 'Setup awal, profil toko, dan adaptasi jenis bisnis.',
    iconName: 'Compass',
  },
  {
    key: 'inventori',
    name: 'Produk & Inventori',
    description: 'Kelola barang fisik, bahan bakar (FUEL), produk digital, dan jasa.',
    iconName: 'Package',
  },
  {
    key: 'pos',
    name: 'Kasir & Penjualan',
    description: 'Transaksi kasir POS, barcode, diskon, QRIS, dan piutang pelanggan.',
    iconName: 'ShoppingCart',
  },
  {
    key: 'pembelian',
    name: 'Pembelian & PO',
    description: 'Kulakan langsung, Purchase Order (PO), dan penerimaan barang.',
    iconName: 'Truck',
  },
  {
    key: 'keuangan',
    name: 'Buku Kas & Laporan',
    description: 'Arus kas masuk/keluar, analisis laba rugi, dan ekspor PDF resmi.',
    iconName: 'BarChart3',
  },
  {
    key: 'hardware',
    name: 'Hardware & WhatsApp',
    description: 'Koneksi printer thermal Bluetooth/USB dan katalog produk WhatsApp.',
    iconName: 'Printer',
  },
  {
    key: 'keamanan',
    name: 'Keamanan & Lisensi',
    description: 'Backup Google Sheets, proteksi PIN pemilik, dan lisensi perangkat.',
    iconName: 'ShieldCheck',
  },
  {
    key: 'bantuan',
    name: 'Bantuan & Kustomisasi',
    description: 'Panduan 19 jenis usaha, tanya jawab (FAQ), dan troubleshooting.',
    iconName: 'HelpCircle',
  },
];

export const DOC_VERSION = 'Buku Warung v0.2.0 (Build 2)';
export const DOC_LAST_UPDATED = '18 September 2026';
export const OFFICIAL_APK_SHA256 = '9f2b282f924cd2c5388759c4c0f6feef5ec52a5bd55cd434f128b28b15a05d2b';

export const DOC_ARTICLES: DocArticle[] = [
  // =========================================================================
  // BAB 0 — INSTALASI & AKTIVASI
  // =========================================================================
  {
    slug: 'instalasi',
    title: 'BAB 0 — Instalasi & Aktivasi APK Resmi',
    category: 'instalasi',
    categoryName: 'Instalasi & Aktivasi',
    order: 0,
    chapterLabel: 'BAB 0',
    description:
      'Panduan lengkap mendownload file APK resmi Buku Warung, mengizinkan instalasi di HP Android, dan aktivasi lisensi resmi.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '4 menit baca',
    summary:
      'Buku Warung v0.2.0 didistribusikan secara langsung dalam format paket APK Android resmi melalui website SKMNetwork. Ikuti panduan mudah ini untuk memasang dan mengaktifkan aplikasi di HP Anda.',
    targetAudience:
      'Semua pengguna baru Buku Warung yang baru saja membeli lisensi atau ingin menginstal aplikasi di HP Android.',
    features: [
      'Download paket APK resmi langsung dari website https://bukuwarung.skmnetwork.com',
      'Panduan perizinan instalasi untuk berbagai merek HP Android (Samsung, Xiaomi, Oppo, Vivo, Realme, Infinix)',
      'Aktivasi lisensi resmi seumur hidup (Lifetime 1 Perangkat)',
      'Verifikasi keamanan berkas APK (SHA-256 Checksum)',
    ],
    sections: [
      {
        title: '1. Alur Perjalanan Pengguna (Customer Journey)',
        paragraphs: [
          'Untuk mulai menggunakan Buku Warung, Anda melewati langkah sederhana berikut:',
        ],
        listItems: [
          'Website: Kunjungi https://bukuwarung.skmnetwork.com',
          'Beli Lisensi: Buka formulir pemesanan resmi (Public Order)',
          'Pembayaran: Bayar sekali seumur hidup (Rp 50.000 selama promo)',
          'Terima Lisensi: Dapatkan Kode Lisensi resmi (format: BW-XXXX-XXXX-XXXX) via layar dan email konfirmasi',
          'Download APK: Unduh file app-release.apk resmi dari tautan unduhan',
          'Install APK: Pasang file aplikasi di smartphone Android Anda',
          'Setup Awal: Pilih jenis usaha dan lengkapi nama warung/toko',
          'Aktivasi: Masukkan email pemilik dan kode lisensi resmi',
          'Gunakan: Aplikasi siap beroperasi secara offline dengan penyimpanan lokal!',
        ],
      },
      {
        title: '2. Persiapan Sebelum Memasang APK di Android',
        paragraphs: [
          'Karena aplikasi didistribusikan langsung dalam format APK resmi (bukan via Google Play Store), Android akan meminta izin keamanan satu kali untuk menginstal aplikasi dari browser atau pengelola berkas.',
          'Catatan: Nama menu dapat berbeda tergantung merek dan versi Android yang Anda gunakan.',
        ],
        listItems: [
          'Samsung: Pengaturan -> Keamanan dan Privasi -> Pasang Aplikasi yang Tidak Dikenal -> Pilih Browser (misal Chrome) -> Izinkan.',
          'Xiaomi / Redmi / Poco: Setelan -> Privasi & Keamanan -> Izin Khusus -> Pasang Aplikasi Tak Dikenal -> Pilih Pengelola Berkas / Chrome -> Izinkan sumber ini.',
          'Oppo / Realme: Pengaturan -> Keamanan -> Sumber Tidak Dikenal -> Aktifkan untuk Browser / File Manager.',
          'Vivo: Pengaturan -> Keamanan & Privasi -> Instal Aplikasi Tidak Dikenal -> Izinkan.',
        ],
      },
      {
        title: '3. Verifikasi Keamanan File APK (Opsional untuk Pengguna Mahir)',
        paragraphs: [
          'Untuk memastikan file APK yang Anda pasang asli dan tidak dimodifikasi oleh pihak lain, Anda dapat mencocokkan nilai SHA-256 Checksum file:',
          `SHA-256 Resmi: ${OFFICIAL_APK_SHA256}`,
          'Pengecekan ini bersifat opsional bagi pengguna tingkat lanjut dan tidak wajib bagi pengguna awam.',
        ],
      },
    ],
    steps: [
      {
        title: 'Unduh File APK Resmi',
        description:
          'Buka tautan unduhan dari website resmi https://bukuwarung.skmnetwork.com. File tersimpan di folder Download HP Anda dengan nama app-release.apk.',
      },
      {
        title: 'Buka File APK & Izinkan Instalasi',
        description:
          'Buka menu Notifikasi atau File Manager -> Download, lalu ketuk file app-release.apk. Jika muncul jendela "Izinkan instalasi dari sumber ini", tekan Izinkan / Lanjutkan.',
      },
      {
        title: 'Tekan Pasang / Install',
        description:
          'Tekan tombol Install pada layar konfirmasi Android dan tunggu beberapa detik hingga muncul keterangan "Aplikasi terpasang".',
      },
      {
        title: 'Buka Aplikasi Buku Warung',
        description:
          'Ketuk Buka. Pada layar Selamat Datang, masukkan Email Pemilik dan Kode Lisensi resmi yang Anda terima saat pembelian.',
      },
      {
        title: 'Tekan Tombol "Aktivasi Lisensi"',
        description:
          'Pastikan HP terkoneksi internet satu kali saat menekan tombol Aktivasi. Aplikasi akan memverifikasi lisensi ke server lisensi resmi SKMNetwork dan mengikat lisensi ke HP Anda.',
      },
    ],
    callouts: [
      {
        type: 'info',
        title: 'Koneksi Internet Hanya 1 Kali',
        text: 'Setelah tombol Aktivasi Lisensi berhasil, aplikasi beroperasi secara offline dan tidak membutuhkan koneksi internet untuk transaksi kasir harian.',
      },
      {
        type: 'warning',
        title: 'Apa yang Dilakukan Jika Aktivasi Gagal?',
        text: 'Periksa kembali apakah penulisan email dan kode lisensi sudah persis sama (perhatikan huruf besar/kecil dan tanda strip). Jika masih gagal, pastikan koneksi internet HP stabil atau hubungi CS resmi SKMNetwork.',
      },
    ],
    example: {
      title: 'Contoh Aktivasi di HP Android Toko Kelontong',
      scenario: 'Pak Slamet baru saja membeli lisensi Buku Warung dan mengunduh APK di HP Android miliknya.',
      details: [
        'Email Terdaftar: slamet.sembako@gmail.com',
        'Kode Lisensi: BW-ET72-CCPY-ZAYN',
        'Langkah: Pak Slamet memasang APK -> Membuka aplikasi -> Memasukkan email & kode -> Tekan Aktivasi.',
        'Hasil: Layar menampilkan status "Lisensi Aktif Seumur Hidup", dan aplikasi langsung masuk ke Beranda.',
      ],
    },
    screenshot: {
      src: '/img/screenshots/01_welcome.png',
      caption: 'Layar Selamat Datang & Pemasangan Lisensi Resmi Buku Warung v0.2.0',
    },
    limitations: [
      'Satu kode lisensi hanya dapat diaktifkan pada 1 perangkat HP Android aktif.',
      'Jika berganti HP baru, aktivasi di perangkat lama harus dilepaskan melalui bantuan CS resmi SKMNetwork.',
    ],
    relatedSlugs: ['mulai', 'lisensi', 'pin', 'faq'],
    keywords: [
      'instalasi',
      'install apk',
      'download apk',
      'aktivasi lisensi',
      'sumber tidak dikenal',
      'sumber diizinkan',
      'sha256',
      'setup awal',
    ],
  },

  // =========================================================================
  // BAB 1 — MULAI MENGGUNAKAN BUKU WARUNG
  // =========================================================================
  {
    slug: 'mulai',
    title: 'BAB 1 — Mulai Menggunakan Buku Warung',
    category: 'mulai',
    categoryName: 'Mulai Menggunakan',
    order: 1,
    chapterLabel: 'BAB 1',
    description: 'Langkah awal mengatur identitas toko, memilih jenis usaha adaptif, dan konfigurasi profil.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Panduan setup awal saat pertama kali menjalankan Buku Warung. Atur nama toko, pemilik, dan jenis usaha Anda agar aplikasi otomatis menyesuaikan istilah dan fiturnya.',
    targetAudience: 'Pemilik usaha yang baru menyelesaikan aktivasi lisensi.',
    features: [
      'Pengaturan identitas toko: Nama Toko, Nama Pemilik, Nomor WhatsApp, dan Alamat',
      'Pemilihan 1 dari 19 kategori jenis usaha adaptif UMKM',
      'Dukungan aktivitas tambahan (misal: Warung yang juga menjual Pulsa atau Servis)',
      'Otomatisasi pencetakan identitas toko pada struk printer dan laporan PDF',
    ],
    sections: [
      {
        title: '1. Filosofi Arsitektur Offline-First',
        paragraphs: [
          'Buku Warung dirancang dengan arsitektur Offline-First. Seluruh data transaksi kasir, stok barang, buku hutang piutang, dan pencatatan kas disimpan langsung di penyimpanan internal HP Anda.',
          'Anda tidak perlu khawatir jika sinyal internet mati atau kuota habis di toko. Semua fungsi utama kasir dan cetak struk tetap beroperasi tanpa hambatan.',
        ],
      },
      {
        title: '2. Pengaturan Profil Usaha yang Tepat',
        paragraphs: [
          'Data nama toko, nomor telepon, dan alamat yang Anda masukkan di layar setup awal akan otomatis dicetak pada header struk kasir thermal dan lembar laporan PDF resmi.',
          'Anda dapat mengubah data profil toko ini kapan saja melalui menu Pengaturan -> Profil Usaha.',
        ],
      },
    ],
    steps: [
      {
        title: 'Pilih Jenis Usaha Utama',
        description:
          'Pilih kategori bisnis Anda (Warung Sembako, Apotek, Bengkel, Toko Bangunan, dsb). Aplikasi akan otomatis menyesuaikan istilah produk dan satuan barang.',
      },
      {
        title: 'Pilih Aktivitas Tambahan (Opsional)',
        description:
          'Jika toko Anda memiliki layanan sampingan seperti jualan pulsa atau jasa servis, centang aktivitas tambahan yang sesuai.',
      },
      {
        title: 'Lengkapi Data Identitas Toko',
        description:
          'Ketik Nama Toko, Nama Pemilik, Nomor HP/WhatsApp untuk kontak pelanggan, dan Alamat toko Anda.',
      },
      {
        title: 'Tekan Selesai & Masuk Beranda',
        description:
          'Simpan konfigurasi. Buku Warung langsung siap digunakan untuk mencatat transaksi dan inventori.',
      },
    ],
    callouts: [
      {
        type: 'tip',
        title: 'Pilih Kategori yang Sesuai',
        text: 'Memilih kategori usaha yang tepat (misal Bengkel) akan otomatis menampilkan dukungan item jasa dan onderdil pada layar kasir.',
      },
    ],
    example: {
      title: 'Contoh Setup: Toko Sembako Berkah',
      scenario: 'Ibu Siti membuka usaha toko kelontong di Losarang yang juga menjual pulsa & token listrik.',
      details: [
        'Jenis Usaha: Warung Sembako / Kelontong',
        'Aktivitas Tambahan: Konter Pulsa & Token PLN',
        'Hasil: Toko Sembako Berkah siap mencatat sembako fisik sekaligus transaksi pulsa tanpa stok fisik.',
      ],
    },
    screenshot: {
      src: '/img/screenshots/02_first_setup.png',
      caption: 'Layar Pemilihan Jenis Usaha & Setup Profil Toko Pertama Kali',
    },
    limitations: [
      'Aplikasi beroperasi secara mandiri di 1 perangkat aktif (bukan sistem multi-kasir cloud realtime tanpa backup).',
    ],
    relatedSlugs: ['instalasi', 'beranda', 'produk', 'pin'],
    keywords: ['mulai', 'onboarding', 'profil toko', 'jenis usaha', 'setup awal', 'offline first'],
  },

  // =========================================================================
  // BAB 2 — MENGENAL BERANDA & NAVIGASI
  // =========================================================================
  {
    slug: 'beranda',
    title: 'BAB 2 — Mengenal Beranda & Navigasi',
    category: 'mulai',
    categoryName: 'Mulai Menggunakan',
    order: 2,
    chapterLabel: 'BAB 2',
    description: 'Memahami tampilan layar utama, kartu ringkasan keuangan harian, dan navigasi menu.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Layar Beranda menyajikan ringkasan omzet penjualan hari ini, saldo kas aktif, kartu peringatan stok menipis, dan akses instan ke seluruh modul operasional.',
    targetAudience: 'Pemilik toko dan kasir yang mengoperasikan aplikasi sehari-hari.',
    features: [
      'Kartu Penjualan Hari Ini: Total omzet kasir secara real-time',
      'Kartu Saldo Kas Toko: Jumlah uang tunai fisik yang ada di laci kasir',
      'Kartu Peringatan Stok Menipis: Notifikasi barang yang harus segera dikulak',
      'Kartu Piutang Pelanggan & Hutang Supplier: Saldo tagihan yang sedang berjalan',
      'Bilah navigasi 5 tab: Beranda, Kasir POS, Produk, Laporan, dan Pengaturan',
    ],
    sections: [
      {
        title: '1. Membaca Kartu Ringkasan Keuangan',
        paragraphs: [
          'Beranda Buku Warung dirancang untuk memberikan informasi seketika mengenai kondisi warung Anda tanpa perlu membuka menu laporan:',
        ],
        listItems: [
          'Penjualan Hari Ini: Menghitung seluruh transaksi kasir yang selesai pada hari ini.',
          'Saldo Kas: Menampilkan posisi uang tunai yang bertambah dari penjualan tunai dan berkurang dari pengeluaran kas atau kulakan tunai.',
          'Stok Menipis: Menampilkan jumlah produk yang stoknya sudah mencapai atau di bawah batas minimum.',
          'Piutang Pelanggan: Total uang toko yang masih belum dilunasi oleh pembeli (kasbon).',
        ],
      },
      {
        title: '2. Pintasan Menu Cepat',
        paragraphs: [
          'Dari Beranda, Anda dapat langsung mengetuk kartu pintasan untuk masuk ke menu Kasir POS, Tambah Produk Baru, Catat Pembelian, atau Buku Kas.',
        ],
      },
    ],
    callouts: [
      {
        type: 'tip',
        title: 'Ketuk Kartu Stok Menipis',
        text: 'Ketuk langsung kartu Stok Menipis di Beranda untuk melihat daftar nama barang apa saja yang hampir habis.',
      },
    ],
    screenshot: {
      src: '/img/screenshots/03_beranda.png',
      caption: 'Tampilan Beranda Utama Buku Warung dengan Kartu Ringkasan Real-Time',
    },
    relatedSlugs: ['mulai', 'pos', 'produk', 'kas', 'laporan'],
    keywords: ['beranda', 'dashboard', 'ringkasan usaha', 'omzet hari ini', 'saldo kas', 'navigasi'],
  },

  // =========================================================================
  // BAB 3 — PRODUK & MANAJEMEN STOK
  // =========================================================================
  {
    slug: 'produk',
    title: 'BAB 3 — Produk & Manajemen Stok',
    category: 'inventori',
    categoryName: 'Produk & Inventori',
    order: 3,
    chapterLabel: 'BAB 3',
    description: 'Cara menambah produk fisik, mengatur harga modal dan jual, batas minimum stok, dan barcode.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '4 menit baca',
    summary:
      'Kelola master produk barang fisik secara mudah. Stok berkurang otomatis saat terjadi penjualan di kasir dan bertambah saat barang masuk dari kulakan.',
    targetAudience: 'Penanggung jawab stok, pemilik toko, dan bagian pengadaan barang.',
    features: [
      'Master data produk: Nama barang, kategori, satuan (pcs, botol, kg, sak, strip, dll)',
      'Pengaturan Harga Beli (modal) dan Harga Jual ke pelanggan',
      'Stok Awal dan Batas Minimum Stok untuk peringatan dini',
      'Scan kode barcode kemasan menggunakan kamera HP',
      'Pencatatan mutasi riwayat stok otomatis setiap ada transaksi',
    ],
    sections: [
      {
        title: '1. Karakteristik Produk Fisik (PHYSICAL)',
        paragraphs: [
          'Produk bertipe fisik (PHYSICAL) memiliki pelacakan stok ketat. Setiap penjualan di kasir otomatis memotong stok barang di database lokal.',
          'Jika Anda menetapkan batas minimum stok (misal: 3 pcs), aplikasi akan menampilkan peringatan kuning ketika sisa stok mencapai 3 pcs.',
        ],
      },
      {
        title: '2. Pendaftaran Barcode Produk',
        paragraphs: [
          'Anda dapat memindai barcode asli pada kemasan barang (seperti sabun, makanan ringan, minuman botol) saat menambah produk. Saat melayani pembeli di kasir, Anda cukup scan barcode tersebut untuk memasukkan barang ke keranjang kasir dalam waktu kurang dari 1 detik.',
        ],
      },
    ],
    steps: [
      {
        title: 'Buka Tab Produk',
        description: 'Pilih tab Produk pada menu bawah, lalu tekan tombol bulat "+ Tambah Produk".',
      },
      {
        title: 'Isi Nama Barang & Kategori',
        description: 'Ketik nama barang (misal: Minyak Goreng Bimoli 1L) dan pilih kategorinya.',
      },
      {
        title: 'Tentukan Harga Beli & Harga Jual',
        description: 'Masukkan harga modal kulakan (Harga Beli) dan harga jual ke pembeli.',
      },
      {
        title: 'Atur Stok & Batas Minimum',
        description: 'Ketik jumlah stok yang tersedia di toko dan tentukan limit stok minimum.',
      },
      {
        title: 'Scan Barcode & Simpan',
        description: 'Arahkan kamera ke barcode kemasan barang (opsional), lalu tekan tombol Simpan.',
      },
    ],
    example: {
      title: 'Contoh Input Barang: Minyak Goreng 1 Liter',
      scenario: 'Menambah stok minyak goreng sebanyak 24 botol @ modal Rp 16.500 dan harga jual Rp 18.500.',
      details: [
        'Nama Produk: Minyak Goreng Bimoli 1L',
        'Kategori: Sembako & Kebutuhan Dapur',
        'Satuan: botol | Tipe: Fisik',
        'Harga Beli: Rp 16.500 | Harga Jual: Rp 18.500',
        'Stok Awal: 24 | Minimum Stok: 4',
      ],
    },
    screenshot: {
      src: '/img/screenshots/05_produk_stok.png',
      caption: 'Daftar Katalog Produk & Manajemen Stok Barang di Buku Warung',
    },
    relatedSlugs: ['fuel', 'digital', 'jasa', 'pos', 'pembelian'],
    keywords: ['produk', 'stok', 'stok barang', 'stok habis', 'tambah produk', 'harga jual', 'harga beli', 'barcode', 'minimum stok'],
  },

  // =========================================================================
  // BAB 4 — PRODUK KHUSUS: BAHAN BAKAR (FUEL)
  // =========================================================================
  {
    slug: 'fuel',
    title: 'BAB 4 — Produk Khusus: Bahan Bakar (FUEL)',
    category: 'inventori',
    categoryName: 'Produk & Inventori',
    order: 4,
    chapterLabel: 'BAB 4',
    description: 'Dukungan jumlah desimal (contoh: 12,5 Liter) untuk pom bensin mini, Pertamini, dan bensin eceran.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Produk tipe FUEL dirancang khusus untuk usaha bahan bakar eceran. Mendukung pengisian jumlah pecahan desimal dengan perhitungan total harga dan pemotongan stok tangki yang presisi.',
    targetAudience: 'Pemilik Pertamini, kios bensin eceran, pangkalan minyak tanah, atau penjual bahan bakar cair.',
    features: [
      'Dukungan kuantitas desimal (misal: 2,5 liter, 12,75 liter, 20,5 liter)',
      'Perhitungan harga presisi: Kuantitas Desimal × Harga per Liter',
      'Stok tangki berkurang tepat sesuai literan yang dikeluarkan',
      'Mendukung cetak struk pengisian bensin rapi ke printer thermal',
    ],
    sections: [
      {
        title: '1. Mengapa Perlu Tipe FUEL?',
        paragraphs: [
          'Usaha bahan bakar sering melayani pembeli dengan angka literan pecahan (misal: "Beli bensin 2,5 liter" atau "Isi Rp 50.000 dapat 5 liter").',
          'Tipe FUEL di Buku Warung menangani angka desimal ini secara presisi tanpa pembulatan yang merugikan pedagang maupun pembeli.',
        ],
      },
    ],
    steps: [
      {
        title: 'Tambah Produk Baru',
        description: 'Buka menu Produk -> Tambah Produk, lalu pilih Tipe Item: FUEL / Bahan Bakar.',
      },
      {
        title: 'Tentukan Satuan & Harga per Liter',
        description: 'Pilih satuan "liter", masukkan harga modal per liter dan harga jual per liter.',
      },
      {
        title: 'Input Kapasitas Tangki (Stok)',
        description: 'Ketik jumlah liter bensin yang tersedia di tangki penyimpanan Anda.',
      },
    ],
    example: {
      title: 'Contoh Penjualan Pertalite 12,5 Liter',
      scenario: 'Seorang pengendara motor mengisi bensin Pertalite sebanyak 12,5 liter @ Rp 10.000 per liter.',
      details: [
        'Produk: Pertalite (Tipe: FUEL)',
        'Jumlah: 12.5 liter',
        'Harga: Rp 10.000 / liter',
        'Total Pembayaran: Rp 125.000',
        'Pengurangan Stok: Stok Pertalite di tangki berkurang tepat 12.5 liter.',
      ],
    },
    relatedSlugs: ['produk', 'pos', 'jenis-usaha'],
    keywords: ['fuel', 'bahan bakar', 'pertalite', 'pertamini', 'bensin eceran', 'desimal', 'liter'],
  },

  // =========================================================================
  // BAB 5 — PRODUK DIGITAL (PULSA, TOKEN, PAKET DATA)
  // =========================================================================
  {
    slug: 'digital',
    title: 'BAB 5 — Produk Digital: Pulsa & Token',
    category: 'inventori',
    categoryName: 'Produk & Inventori',
    order: 5,
    chapterLabel: 'BAB 5',
    description: 'Mencatat penjualan pulsa, paket data, token listrik PLN, dan voucher tanpa stok fisik.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Produk digital ditandai dengan badge "Non-Stok". Anda dapat mencatat keuntungan penjualan pulsa dan token tanpa dibatasi kuota stok fisik.',
    targetAudience: 'Konter pulsa, warung kelontong yang melayani PPOB, agen token listrik.',
    features: [
      'Produk Non-Stok: Bebas input penjualan tanpa batasan stok gudang',
      'Perhitungan keuntungan instan dari selisih Harga Jual dengan Modal Agen',
      'Struk kasir mencantumkan nomor meteran PLN atau nomor HP pembeli',
    ],
    sections: [
      {
        title: '1. Bebas Stok Fisik',
        paragraphs: [
          'Produk bertipe DIGITAL tidak mengurangi stok gudang. Anda dapat menjual pulsa atau token kapan saja tanpa khawatir muncul peringatan stok habis.',
          'Pada laporan keuangan, omzet dan laba produk digital tetap dihitung secara akurat.',
        ],
      },
    ],
    example: {
      title: 'Contoh Penjualan Token PLN Rp 50.000',
      scenario: 'Pelanggan membeli token listrik nominal 50rb @ modal agen Rp 50.500 dan harga jual Rp 53.000.',
      details: [
        'Nama Produk: Token Listrik PLN 50rb',
        'Tipe: Digital (Non-Stok)',
        'Harga Beli (Modal): Rp 50.500',
        'Harga Jual: Rp 53.000',
        'Laba Bersih Kasir: Rp 2.500 per transaksi.',
      ],
    },
    relatedSlugs: ['produk', 'jasa', 'pos'],
    keywords: ['digital', 'pulsa', 'token pln', 'paket data', 'non stok', 'ppob', 'voucher'],
  },

  // =========================================================================
  // BAB 6 — PRODUK JASA & LAYANAN
  // =========================================================================
  {
    slug: 'jasa',
    title: 'BAB 6 — Produk Jasa & Layanan',
    category: 'inventori',
    categoryName: 'Produk & Inventori',
    order: 6,
    chapterLabel: 'BAB 6',
    description: 'Pencatatan jasa servis kendaraan, pangkas rambut, laundry kiloan, dan biaya instalasi.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Produk tipe SERVICE memungkinkan bisnis berbasis keahlian dan jasa mencatat penerimaan ongkos kerja tanpa memerlukan stok barang.',
    targetAudience: 'Bengkel motor/mobil, salon, barbershop, laundry, penjahit, teknisi elektronik.',
    features: [
      'Produk Jasa murni tanpa pengurangan stok barang fisik',
      'Dapat digabungkan dengan sparepart fisik dalam satu nota struk kasir',
      'Margin laba terhitung penuh atas ongkos tenaga kerja',
    ],
    sections: [
      {
        title: '1. Menggabungkan Jasa dan Onderdil di Bengkel',
        paragraphs: [
          'Di bengkel motor, pelanggan sering melakukan ganti oli sekaligus servis karburator. Buku Warung memungkinkan Anda memasukkan Oli Mesin (Fisik - memotong stok) dan Jasa Servis (Jasa - tanpa stok) ke dalam satu transaksi kasir.',
        ],
      },
    ],
    example: {
      title: 'Contoh Struk Bengkel Motor Jaya',
      scenario: 'Pelanggan ganti oli mesin dan melakukan servis ringan.',
      details: [
        'Item 1: Oli MPX2 0.8L (Fisik) — Rp 55.000',
        'Item 2: Jasa Servis Ringan (Jasa) — Rp 30.000',
        'Total Bayar: Rp 85.000',
        'Efek: Stok oli berkurang 1 botol, pendapatan jasa tercatat Rp 30.000.',
      ],
    },
    relatedSlugs: ['produk', 'pos', 'printer', 'jenis-usaha'],
    keywords: ['jasa', 'servis bengkel', 'laundry', 'barbershop', 'ongkos kerja', 'service'],
  },

  // =========================================================================
  // BAB 7 — TRANSAKSI KASIR (POS / JUALAN)
  // =========================================================================
  {
    slug: 'pos',
    title: 'BAB 7 — Transaksi Kasir (POS / Jualan)',
    category: 'pos',
    categoryName: 'Kasir & Penjualan',
    order: 7,
    chapterLabel: 'BAB 7',
    description: 'Panduan lengkap melayani pembeli di kasir, scan barcode cepat, dan memilih metode bayar.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '4 menit baca',
    summary:
      'Layar kasir POS dirancang super cepat dan mudah digunakan. Mendukung pencarian cepat, scan barcode kamera, diskon transaksi, pembayaran Tunai, QRIS statis, dan Kasbon tempo.',
    targetAudience: 'Kasir, penjaga toko, dan pemilik warung saat melayani antrean pembeli.',
    features: [
      'Pencarian produk instan berdasarkan nama atau kategori',
      'Scan barcode otomatis menggunakan kamera HP atau scanner Bluetooth',
      'Keranjang belanja fleksibel (tambah/kurang kuantitas, hapus item)',
      'Penerapan diskon per transaksi (nominal Rp atau persentase %)',
      '3 Metode pembayaran: Tunai (Cash), QRIS Statis, dan Kasbon (Hutang Pelanggan)',
      'Perhitungan kembalian otomatis dan pencetakan struk kasir instan',
    ],
    sections: [
      {
        title: '1. Tiga Metode Pembayaran Resmi',
        paragraphs: [
          'Buku Warung menyediakan 3 metode penyelesaian transaksi:',
        ],
        listItems: [
          'Tunai (Cash): Masukkan nominal uang pembeli (atau gunakan tombol nominal cepat). Aplikasi menghitung uang kembalian dan otomatis menambah saldo kas laci toko.',
          'QRIS: Pembeli memindai QRIS statis milik toko Anda. Penjualan tercatat resmi tanpa menambah uang fisik di laci kasir.',
          'Hutang / Kasbon: Penjualan tempo atas nama pelanggan terdaftar. Transaksi selesai tanpa uang tunai dan langsung dicatat di buku piutang pelanggan.',
        ],
      },
    ],
    steps: [
      {
        title: 'Buka Tab Jualan / Kasir',
        description: 'Pilih tab Jualan (POS) di bilah navigasi bawah.',
      },
      {
        title: 'Pilih Produk ke Keranjang',
        description: 'Ketuk produk pada daftar atau tekan ikon barcode untuk memindai kemasan barang.',
      },
      {
        title: 'Atur Kuantitas & Diskon (Jika Ada)',
        description: 'Ubah jumlah barang sesuai pesanan pembeli dan tambahkan diskon jika sedang ada potongan harga.',
      },
      {
        title: 'Tekan Tombol "Bayar"',
        description: 'Pilih metode pembayaran: Tunai, QRIS, atau Kasbon (Hutang).',
      },
      {
        title: 'Selesaikan & Cetak Struk',
        description: 'Tekan Selesai Transaksi. Struk dapat langsung dicetak ke printer Bluetooth thermal.',
      },
    ],
    callouts: [
      {
        type: 'info',
        title: 'QRIS Statis Mandiri',
        text: 'Buku Warung menggunakan sistem QRIS Statis merchant Anda sendiri. Uang pembayaran langsung masuk ke rekening bank / e-wallet Anda tanpa potongan pihak ketiga.',
      },
    ],
    example: {
      title: 'Contoh Transaksi Kasir Pembelian Sembako',
      scenario: 'Pembeli membeli 2 bungkus minyak goreng dan 1 kg gula pasir, dibayar tunai uang Rp 50.000.',
      details: [
        'Minyak Goreng: 2 × Rp 18.000 = Rp 36.000',
        'Gula Pasir: 1 × Rp 17.500 = Rp 17.500',
        'Total Belanja: Rp 53.500 | Diskon: Rp 3.500 | Total Tagihan: Rp 50.000',
        'Uang Diterima: Rp 50.000 | Kembalian: Rp 0 (Pas)',
      ],
    },
    screenshot: {
      src: '/img/screenshots/04_jualan_pos.png',
      caption: 'Layar Kasir POS Buku Warung dengan Keranjang Belanja dan Checkout',
    },
    relatedSlugs: ['diskon', 'pelanggan', 'printer', 'beranda'],
    keywords: ['pos', 'kasir', 'jualan', 'cara jual barang', 'checkout', 'tunai', 'qris', 'barcode', 'kembalian'],
  },

  // =========================================================================
  // BAB 8 — DISKON TRANSAKSI
  // =========================================================================
  {
    slug: 'diskon',
    title: 'BAB 8 — Diskon Transaksi',
    category: 'pos',
    categoryName: 'Kasir & Penjualan',
    order: 8,
    chapterLabel: 'BAB 8',
    description: 'Cara memberikan potongan harga nominal rupiah maupun persentase pada kasir belanja.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '2 menit baca',
    summary:
      'Berikan diskon promo kepada pembeli dengan perhitungan akurat. Nilai diskon dicetak jelas di struk kasir dan otomatis diperhitungkan pada laporan laba rugi.',
    targetAudience: 'Kasir dan pemilik toko saat mengadakan program potongan harga atau promo pelanggan.',
    features: [
      'Diskon Nominal (Rp): Potongan langsung dalam rupiah (misal Rp 5.000)',
      'Diskon Persen (%): Potongan dalam persentase (misal 10%)',
      'Pencatatan transparan pada struk cetak dan laporan penjualan bersih',
    ],
    sections: [
      {
        title: '1. Pengaruh Diskon pada Laporan Finansial',
        paragraphs: [
          'Diskon yang diberikan akan mengurangi nilai penjualan bruto sehingga menghasilkan angka Penjualan Bersih yang sebenarnya. HPP barang modal tetap dihitung dari harga beli asli.',
        ],
      },
    ],
    example: {
      title: 'Contoh Diskon Belanja Hari Kemerdekaan',
      scenario: 'Total belanjaan Rp 120.000 diberikan diskon potongan Rp 10.000.',
      details: [
        'Subtotal Keranjang: Rp 120.000',
        'Diskon: Rp 10.000',
        'Total Pembayaran Akhir: Rp 110.000',
      ],
    },
    relatedSlugs: ['pos', 'laporan', 'printer'],
    keywords: ['diskon', 'potongan harga', 'promo', 'persen', 'nominal diskon'],
  },

  // =========================================================================
  // BAB 9 — PELANGGAN & BUKU PIUTANG
  // =========================================================================
  {
    slug: 'pelanggan',
    title: 'BAB 9 — Pelanggan & Buku Piutang',
    category: 'pos',
    categoryName: 'Kasir & Penjualan',
    order: 9,
    chapterLabel: 'BAB 9',
    description: 'Mencatat transaksi kasbon pembeli, memantau total piutang, dan menerima cicilan pelunasan.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Buku Piutang mencatat siapa saja pelanggan yang berhutang (kasbon) secara tertib. Catat cicilan bertahap hingga lunas dengan riwayat pembayaran yang transparan.',
    targetAudience: 'Pemilik warung dan kasir yang melayani sistem belanja kasbon bagi tetangga atau langganan.',
    features: [
      'Direktori data pelanggan: Nama, Nomor Telepon, Alamat, Catatan batas hutang',
      'Penjualan kasbon langsung dari kasir POS',
      'Daftar rekap piutang aktif yang belum lunas per pelanggan',
      'Fitur bayar cicilan sebagian atau pelunasan penuh',
      'Riwayat setoran uang cicilan otomatis menambah saldo kas toko',
    ],
    sections: [
      {
        title: '1. Cara Mencatat Penjualan Bon / Kasbon',
        paragraphs: [
          'Saat pembeli ingin kasbon di kasir, pilih metode pembayaran "Hutang", lalu pilih nama pelanggan terdaftar (atau tambah pelanggan baru).',
          'Barang belanjaan tetap memotong stok toko, dan nilai belanjaan langsung dicatat sebagai piutang aktif atas nama pelanggan tersebut.',
        ],
      },
      {
        title: '2. Menerima Pembayaran Cicilan & Pelunasan',
        paragraphs: [
          'Ketika pelanggan datang mencicil hutang:',
          '1. Buka menu Pelanggan & Piutang, lalu pilih nama pelanggan.',
          '2. Tekan tombol "Bayar Hutang".',
          '3. Masukkan nominal uang yang disetorkan pembeli.',
          '4. Simpan. Sisa hutang pelanggan otomatis berkurang dan uang setoran otomatis menambah saldo kas laci toko.',
        ],
      },
    ],
    example: {
      title: 'Contoh Kasbon Pak Budi',
      scenario: 'Pak Budi kasbon sembako Rp 75.000, lalu mencicil Rp 50.000 tiga hari kemudian.',
      details: [
        'Hutang Awal: Rp 75.000 (Status: UNPAID)',
        'Cicilan Masuk: Rp 50.000 (Status berubah: PARTIAL)',
        'Sisa Piutang: Rp 25.000 | Saldo Kas Toko: Bertambah Rp 50.000.',
      ],
    },
    screenshot: {
      src: '/img/screenshots/07_pelanggan_piutang.png',
      caption: 'Buku Catatan Piutang Pelanggan & Riwayat Cicilan di Buku Warung',
    },
    relatedSlugs: ['pos', 'kas', 'laporan'],
    keywords: ['pelanggan', 'piutang', 'kasbon', 'hutang pelanggan', 'cicilan', 'buku hutang'],
  },

  // =========================================================================
  // BAB 10 — SUPPLIER & PEMASOK
  // =========================================================================
  {
    slug: 'supplier',
    title: 'BAB 10 — Supplier & Pemasok',
    category: 'pembelian',
    categoryName: 'Pembelian & PO',
    order: 10,
    chapterLabel: 'BAB 10',
    description: 'Mengelola daftar supplier kulakan, kontak sales, dan memantau saldo hutang usaha toko.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Kelola direktori pemasok (supplier) tempat Anda belanja kulakan. Pantau riwayat belanja barang masuk dan kelola jadwal pelunasan hutang supplier.',
    targetAudience: 'Pemilik toko dan bagian belanja kulakan.',
    features: [
      'Buku kontak supplier: Nama sales/distributor, Nomor WhatsApp, Alamat gudang',
      'Pelacakan total hutang usaha ke masing-masing supplier',
      'Pencatatan pembayaran pelunasan faktur supplier',
    ],
    sections: [
      {
        title: '1. Mengapa Perlu Mendata Supplier?',
        paragraphs: [
          'Dengan mendata supplier secara rapi, Anda dapat membuat Purchase Order (PO) secara instan dan memantau faktur mana saja yang sudah jatuh tempo untuk dibayar.',
        ],
      },
    ],
    screenshot: {
      src: '/img/screenshots/08_supplier_hutang.png',
      caption: 'Daftar Kontak Supplier & Buku Hutang Usaha Toko',
    },
    relatedSlugs: ['pembelian', 'purchase-order', 'kas'],
    keywords: ['supplier', 'pemasok', 'distributor', 'sales', 'hutang supplier', 'kulakan tempo'],
  },

  // =========================================================================
  // BAB 11 — PEMBELIAN LANGSUNG (KULAKAN)
  // =========================================================================
  {
    slug: 'pembelian',
    title: 'BAB 11 — Pembelian Langsung (Kulakan)',
    category: 'pembelian',
    categoryName: 'Pembelian & PO',
    order: 11,
    chapterLabel: 'BAB 11',
    description: 'Mencatat barang masuk hasil belanja kulakan tunai maupun tempo dari pasar atau distributor.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Catat setiap belanjaan barang masuk. Stok produk di toko akan langsung bertambah dan kas toko (atau hutang supplier) akan diperbarui otomatis.',
    targetAudience: 'Pemilik warung saat selesai belanja kulakan di pasar grosir atau menerima kiriman sales.',
    features: [
      'Pencatatan faktur barang masuk langsung',
      'Pilihan pembayaran: Tunai (potong kas laci) atau Hutang Supplier (tempo)',
      'Otomatisasi penambahan stok produk di master inventori',
    ],
    sections: [
      {
        title: '1. Perbedaan Pembelian Tunai vs Tempo',
        paragraphs: [
          'Pembelian Tunai: Saldo kas laci toko langsung terpotong sebesar total faktur belanja.',
          'Pembelian Tempo (Kredit): Tidak memotong kas saat ini, tetapi dicatat sebagai Hutang Supplier yang harus dilunasi kemudian.',
        ],
      },
    ],
    screenshot: {
      src: '/img/screenshots/06_pembelian.png',
      caption: 'Form Pencatatan Pembelian Barang Masuk / Kulakan',
    },
    relatedSlugs: ['supplier', 'purchase-order', 'produk', 'kas'],
    keywords: ['pembelian', 'kulakan', 'barang masuk', 'faktur beli', 'stok bertambah'],
  },

  // =========================================================================
  // BAB 12 — PURCHASE ORDER (PO / PESANAN PEMBELIAN)
  // =========================================================================
  {
    slug: 'purchase-order',
    title: 'BAB 12 — Purchase Order (PO / Pesanan Pembelian)',
    category: 'pembelian',
    categoryName: 'Pembelian & PO',
    order: 12,
    chapterLabel: 'BAB 12',
    description: 'Menyusun draf pesanan resmi sebelum barang dikirim dan memahami siklus status PO.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '4 menit baca',
    summary:
      'Purchase Order (PO) adalah dokumen pemesanan resmi dari toko Anda ke supplier. Membuat PO belum mengubah stok maupun kas hingga barang benar-benar diterima.',
    targetAudience: 'Pemilik toko kelontong, apotek, toko bangunan, dan bengkel saat memesan stok ke distributor.',
    features: [
      'Pembuatan draf pesanan pembelian ke supplier terdaftar',
      'Perhitungan estimasi nilai belanja berdasarkan harga beli terakhir',
      'Siklus 4 status resmi PO: DRAFT, ORDERED, RECEIVED, CANCELLED',
      'Kirim lembar PO via WhatsApp atau cetak ke printer thermal',
    ],
    sections: [
      {
        title: '1. Siklus 4 Status Purchase Order',
        paragraphs: [
          'Setiap PO melewati tahapan status berikut:',
        ],
        listItems: [
          'DRAFT: Pesanan sedang disusun dan masih bisa diubah (diedit) atau dihapus.',
          'ORDERED: Pesanan resmi yang sudah dikirim ke sales supplier (nomor PO terkunci).',
          'RECEIVED: Barang pesanan telah tiba di toko dan difinalisasi via menu Terima Barang (Goods Receipt).',
          'CANCELLED: Pesanan dibatalkan karena supplier kehabisan stok atau pesanan diganti.',
        ],
      },
      {
        title: '2. Aturan Penting: PO ≠ Realisasi Keuangan',
        paragraphs: [
          'Saat status PO masih DRAFT atau ORDERED, stok barang di toko belum bertambah dan saldo kas belum terpotong.',
          'Stok dan catatan keuangan baru terealisasi ketika barang tiba dan Anda menekan tombol "Terima Barang" (Goods Receipt).',
        ],
      },
    ],
    steps: [
      {
        title: 'Buka Menu Purchase Order',
        description: 'Buka menu Pembelian -> Purchase Order lalu tekan tombol "+ Buat PO".',
      },
      {
        title: 'Pilih Supplier & Tambah Barang',
        description: 'Pilih supplier tujuan, lalu tambahkan barang-barang yang ingin dipesan beserta estimasi jumlahnya.',
      },
      {
        title: 'Simpan sebagai DRAFT atau ORDERED',
        description: 'Pilih status ORDERED jika pesanan sudah final dan siap dikirim ke sales.',
      },
    ],
    callouts: [
      {
        type: 'warning',
        title: 'Batasan Fitur di Buku Warung v0.2.0',
        text: 'Pada versi v0.2.0, fitur Terima Barang mencatat penerimaan seluruh item pesanan sekaligus (full receipt). Penerimaan barang parsial/bertahap dialokasikan pada pembaruan berikutnya (G13.7).',
      },
    ],
    limitations: [
      'Penerimaan parsial (sebagian barang datang dulu) belum didukung di v0.2.0.',
      'Penyesuaian selisih harga faktur saat barang tiba dialokasikan pada pembaruan G13.7.',
      'Pembuatan draf PO otomatis saat stok menipis merupakan rencana masa depan.',
    ],
    relatedSlugs: ['kirim-po', 'goods-receipt', 'supplier', 'pembelian'],
    keywords: ['purchase order', 'po', 'pesanan pembelian', 'draft', 'ordered', 'goods receipt'],
  },

  // =========================================================================
  // BAB 13 — MENGIRIM & MENCETAK PO
  // =========================================================================
  {
    slug: 'kirim-po',
    title: 'BAB 13 — Mengirim & Mencetak Purchase Order',
    category: 'pembelian',
    categoryName: 'Pembelian & PO',
    order: 13,
    chapterLabel: 'BAB 13',
    description: 'Format pesan WhatsApp otomatis ke sales supplier dan cetak lembar PO ke printer thermal.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Teruskan pesanan PO ke supplier melalui format pesan WhatsApp rapi, dokumen PDF resmi, atau lembar cetak printer thermal.',
    targetAudience: 'Pemilik toko saat meneruskan daftar pesanan ke sales distributor.',
    features: [
      'Pesan WhatsApp otomatis lengkap dengan nomor PO, daftar barang, jumlah, dan estimasi total',
      'Cetak lembar PO berukuran 58mm atau 80mm di printer thermal',
      'Ekspor dokumen PO berformat PDF untuk arsip resmi',
    ],
    sections: [
      {
        title: '1. Format Teks WhatsApp yang Bersih & Profesional',
        paragraphs: [
          'Aplikasi menyusun pesan dengan format standar:',
          '*PURCHASE ORDER*',
          '*Toko Sembako Berkah*',
          'No. PO: PO-202609-001',
          '1. Minyak Goreng Bimoli 1L (24 botol @ Rp 16.500 = Rp 396.000)',
          'Estimasi Total: Rp 396.000',
          'Mohon konfirmasi ketersediaan dan jadwal pengiriman. Terima kasih.',
        ],
      },
    ],
    relatedSlugs: ['purchase-order', 'goods-receipt', 'printer'],
    keywords: ['kirim po', 'whatsapp po', 'cetak po thermal', 'pdf po'],
  },

  // =========================================================================
  // BAB 14 — PENERIMAAN BARANG (GOODS RECEIPT)
  // =========================================================================
  {
    slug: 'goods-receipt',
    title: 'BAB 14 — Penerimaan Barang (Goods Receipt)',
    category: 'pembelian',
    categoryName: 'Pembelian & PO',
    order: 14,
    chapterLabel: 'BAB 14',
    description: 'Memproses kedatangan barang dari PO menjadi stok nyata dan mencatat pembayaran kas/hutang.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Ketika truk pengiriman supplier tiba membawa barang pesanan, lakukan proses Terima Barang (Goods Receipt) untuk menambah stok aktif dan mencatat transaksi pembelian.',
    targetAudience: 'Petugas penerima barang dan pemilik toko.',
    features: [
      'Realisasi otomatis penambahan stok seluruh item pesanan',
      'Pilihan metode pembayaran: Tunai (potong kas) atau Tempo (Hutang Supplier)',
      'Status PO otomatis beralih menjadi RECEIVED (Selesai)',
    ],
    sections: [
      {
        title: '1. Alur Realisasi Barang Tiba',
        paragraphs: [
          'Saat kurir atau armada supplier tiba di toko membawa barang yang telah dipesan melalui Purchase Order (PO), Anda perlu memfinalisasi pesanan tersebut.',
          'Dengan menekan Terima Barang, data transaksi pembelian langsung terbentuk dan stok produk di master inventori bertambah sesuai jumlah yang dipesan.',
        ],
      },
      {
        title: '2. Pembayaran Tunai vs Tempo saat Barang Masuk',
        paragraphs: [
          'Anda dapat memilih apakah pesanan dibayar tunai saat itu juga (mengurangi saldo kas toko) atau dicatat sebagai hutang supplier jatuh tempo.',
        ],
      },
    ],
    steps: [
      {
        title: 'Buka Detail PO yang Berstatus ORDERED',
        description: 'Masuk ke menu Purchase Order dan ketuk nomor PO yang barangnya baru tiba.',
      },
      {
        title: 'Tekan Tombol "Terima Barang"',
        description: 'Periksa kembali daftar barang yang datang bersama surat jalan dari supplier.',
      },
      {
        title: 'Pilih Metode Pembayaran',
        description: 'Pilih apakah faktur dibayar Tunai (mengurangi kas) atau Tempo (menambah hutang supplier).',
      },
      {
        title: 'Konfirmasi Penerimaan',
        description: 'Tekan Simpan. Stok toko langsung bertambah dan transaksi pembelian tercatat resmi.',
      },
    ],
    limitations: [
      'Penerimaan parsial (sebagian barang diterima lebih dulu) belum tersedia di v0.2.0.',
      'Penyesuaian harga faktur otomatis / selisih harga saat penerimaan dialokasikan pada rilis berikutnya (G13.7).',
    ],
    relatedSlugs: ['purchase-order', 'pembelian', 'produk', 'kas'],
    keywords: ['goods receipt', 'terima barang', 'realisasi po', 'stok masuk', 'finalisasi po', 'pembelian'],
  },

  // =========================================================================
  // BAB 15 — BUKU KAS & BIAYA OPERASIONAL
  // =========================================================================
  {
    slug: 'kas',
    title: 'BAB 15 — Buku Kas & Biaya Operasional',
    category: 'keuangan',
    categoryName: 'Buku Kas & Laporan',
    order: 15,
    chapterLabel: 'BAB 15',
    description: 'Mencatat uang kas masuk/keluar, biaya listrik, sewa, gaji karyawan, dan mutasi otomatis.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Buku Kas memisahkan uang pribadi dengan uang toko. Saldo kas diperbarui secara otomatis dari transaksi kasir dan setoran cicilan piutang.',
    targetAudience: 'Pemilik toko yang ingin mengetahui arus kas nyata uang laci toko.',
    features: [
      'Pencatatan Kas Keluar untuk beban operasional: Listrik, Air, Gaji, Sewa, Plastik/Kresek',
      'Pencatatan Kas Masuk untuk modal tambahan atau pendapatan lain',
      'Mutasi kas otomatis dari penjualan tunai, kulakan tunai, dan pembayaran piutang',
    ],
    sections: [
      {
        title: '1. Mengapa Penting Mencatat Biaya Operasional?',
        paragraphs: [
          'Dengan mencatat biaya listrik, gaji, dan bensin toko pada Kas Keluar, laporan laba rugi dapat menghitung Laba Bersih riil usaha Anda setelah dikurangi beban operasional.',
        ],
      },
    ],
    screenshot: {
      src: '/img/screenshots/09_uang_kas.png',
      caption: 'Buku Kas Masuk & Kas Keluar Harian di Buku Warung',
    },
    relatedSlugs: ['laporan', 'pos', 'pembelian'],
    keywords: ['buku kas', 'arus kas', 'biaya operasional', 'kas masuk', 'kas keluar', 'laba bersih'],
  },

  // =========================================================================
  // BAB 16 — LAPORAN KEUANGAN & EKSPOR PDF
  // =========================================================================
  {
    slug: 'laporan',
    title: 'BAB 16 — Laporan Keuangan & Ekspor PDF',
    category: 'keuangan',
    categoryName: 'Buku Kas & Laporan',
    order: 16,
    chapterLabel: 'BAB 16',
    description: 'Analisis laba rugi sederhana, omzet, modal barang (HPP), dan ekspor dokumen PDF resmi.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '4 menit baca',
    summary:
      'Pantau kesehatan finansial toko Anda melalui Laporan Laba Rugi Sederhana dengan filter periode fleksibel (Hari Ini, 7 Hari, Bulan Ini) dan ekspor PDF resmi siap cetak.',
    targetAudience: 'Pemilik usaha untuk evaluasi laba rugi mingguan dan bulanan.',
    features: [
      'Laporan Laba Rugi Sederhana: Penjualan Bersih, HPP Modal Terjual, Laba Kotor, Operasional, Laba Bersih',
      'Laporan Nilai Stok Modal, Piutang Pelanggan, dan Hutang Supplier aktif',
      'Filter Periode Fleksibel: Hari Ini, Kemarin, 7 Hari Terakhir, Bulan Ini, Bulan Lalu, Semua Periode',
      'Ekspor dokumen PDF vektor standar A4 berkualitas tinggi dengan header toko resmi',
    ],
    sections: [
      {
        title: '1. Rumus Perhitungan Laba Rugi Sederhana',
        paragraphs: [
          'Buku Warung menyajikan 5 baris laporan keuangan yang mudah dipahami pedagang:',
        ],
        listItems: [
          'Penjualan Bersih: Total omzet kasir dikurangi retur penjualan.',
          'HPP (Modal Barang Terjual): Modal harga beli dari barang-barang yang laku.',
          'Laba Kotor: Penjualan Bersih dikurangi HPP Modal Terjual.',
          'Pengeluaran Operasional: Total beban listrik, sewa, gaji, dan biaya toko.',
          'Laba Bersih: Laba Kotor dikurangi Pengeluaran Operasional.',
        ],
      },
    ],
    screenshot: {
      src: '/img/screenshots/10_laporan.png',
      caption: 'Laporan Laba Rugi Sederhana & Posisi Keuangan Toko',
    },
    relatedSlugs: ['kas', 'pos', 'produk'],
    keywords: ['laporan', 'laba rugi', 'omzet', 'hpp', 'modal terjual', 'laba bersih', 'ekspor pdf'],
  },

  // =========================================================================
  // BAB 17 — KATALOG PRODUK WHATSAPP
  // =========================================================================
  {
    slug: 'katalog-whatsapp',
    title: 'BAB 17 — Katalog Produk WhatsApp',
    category: 'hardware',
    categoryName: 'Hardware & WhatsApp',
    order: 17,
    chapterLabel: 'BAB 17',
    description: 'Bagikan daftar harga barang dan stok toko ke WhatsApp pembeli dalam sekali klik.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '2 menit baca',
    summary:
      'Pilih barang dagangan dari etalase, pilih opsi tampilkan/sembunyikan stok, lalu bagikan pesan katalog rapi langsung ke kontak atau grup WhatsApp pembeli.',
    targetAudience: 'Pedagang yang sering mempromosikan barang dagangan via status atau grup WhatsApp.',
    features: [
      'Pilih beberapa produk atau gunakan tombol "Pilih Semua"',
      'Pencarian dan filter kategori produk',
      'Opsi tampilkan atau sembunyikan jumlah sisa stok',
      'Kirim ke WhatsApp atau WhatsApp Business dengan pesan otomatis rapi',
    ],
    sections: [
      {
        title: '1. Praktis Tanpa Perlu Mengetik Ulang',
        paragraphs: [
          'Katalog disusun otomatis dengan judul sesuai jenis usaha (*KATALOG PRODUK*, *KATALOG OBAT*, atau *KATALOG MATERIAL*), nama toko, rincian harga per satuan, dan kontak pemesanan.',
        ],
      },
    ],
    relatedSlugs: ['produk', 'pos'],
    keywords: ['katalog whatsapp', 'share katalog', 'broadcast promo', 'daftar harga wa', 'whatsapp'],
  },

  // =========================================================================
  // BAB 18 — PRINTER STRUK THERMAL (BLUETOOTH & USB)
  // =========================================================================
  {
    slug: 'printer',
    title: 'BAB 18 — Printer Struk Thermal (Bluetooth & USB)',
    category: 'hardware',
    categoryName: 'Hardware & WhatsApp',
    order: 18,
    chapterLabel: 'BAB 18',
    description: 'Panduan koneksi printer kasir Bluetooth dan USB OTG ukuran 58mm maupun 80mm serta cetak ulang.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '4 menit baca',
    summary:
      'Cetak nota struk kasir profesional standar ESC/POS menggunakan printer thermal Bluetooth nirkabel maupun kabel USB OTG.',
    targetAudience: 'Kasir dan pemilik toko yang menggunakan printer cetak struk kasir.',
    features: [
      'Koneksi nirkabel Bluetooth dan kabel USB OTG',
      'Dukungan kertas thermal lebar 58mm (32 kolom) dan 80mm (48 kolom)',
      'Fitur Test Print untuk memastikan printer siap pakai',
      'Fitur Cetak Ulang (Reprint) untuk mencetak salinan struk transaksi lama',
    ],
    sections: [
      {
        title: '1. Dukungan Printer ESC/POS Standar',
        paragraphs: [
          'Buku Warung mendukung hampir seluruh printer thermal kasir di pasaran yang mendukung protokol standar ESC/POS Bluetooth maupun USB.',
          'Anda dapat memilih ukuran kertas 58mm (cocok untuk printer saku portable) atau 80mm (cocok untuk printer kasir meja besar).',
        ],
      },
      {
        title: '2. Fitur Cetak Ulang (Reprint)',
        paragraphs: [
          'Jika kertas printer sempat macet atau pembeli meminta salinan struk kedua, Anda dapat mencetak ulang struk transaksi melalui riwayat penjualan tanpa mempengaruhi stok.',
        ],
      },
    ],
    steps: [
      {
        title: 'Pasangkan (Pair) Printer di Pengaturan HP',
        description: 'Nyalakan printer thermal. Buka Pengaturan Bluetooth di HP Android Anda dan pasangkan dengan printer (biasanya PIN: 0000 atau 1234).',
      },
      {
        title: 'Buka Menu Pengaturan Printer di Buku Warung',
        description: 'Buka Buku Warung -> Pengaturan -> Pengaturan Printer.',
      },
      {
        title: 'Pilih Nama Printer & Ukuran Kertas',
        description: 'Pilih printer Bluetooth yang sudah dipasangkan dan tentukan lebar kertas (58mm atau 80mm).',
      },
      {
        title: 'Tekan "Test Print"',
        description: 'Pastikan printer berhasil mengeluarkan kertas tes cetak.',
      },
    ],
    screenshot: {
      src: '/img/screenshots/11_pengaturan.png',
      caption: 'Pengaturan Koneksi Printer Bluetooth Thermal & Pengaturan Toko',
    },
    relatedSlugs: ['pos', 'kirim-po', 'beranda'],
    keywords: ['printer', 'bluetooth', 'usb otg', 'printer thermal', '58mm', '80mm', 'cetak struk', 'reprint'],
  },

  // =========================================================================
  // BAB 19 — BACKUP & RESTORE GOOGLE SHEETS
  // =========================================================================
  {
    slug: 'backup-restore',
    title: 'BAB 19 — Backup & Restore Google Sheets',
    category: 'keamanan',
    categoryName: 'Keamanan & Lisensi',
    order: 19,
    chapterLabel: 'BAB 19',
    description: 'Mencadangkan seluruh data transaksi ke Google Spreadsheet dan memulihkannya secara aman.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '4 menit baca',
    summary:
      'Amankan data pembukuan Anda ke akun Google Drive pribadi. Data disimpan dalam 18 tab tabel spreadsheet kanonikal yang dapat dipulihkan kapan saja.',
    targetAudience: 'Pemilik toko yang ingin mengamankan data dari risiko HP hilang, rusak, atau ganti baru.',
    features: [
      'Pencadangan 100% data: Produk, Kategori, Pelanggan, Supplier, Penjualan, Pembelian, Hutang, Kas, dan Mutasi Stok',
      'Tersimpan di Google Drive milik akun Google Anda sendiri',
      'Pemulihan (Restore) aman dengan validasi integritas data dan checksum otomatis',
    ],
    sections: [
      {
        title: '1. Keamanan Data Milik Anda Sendiri',
        paragraphs: [
          'Cadangan data tersimpan langsung di Google Drive akun Google Anda sendiri, bukan di server pihak ketiga. Anda memiliki kendali penuh atas file spreadsheet cadangan.',
        ],
      },
      {
        title: '2. 18 Lembar Tabel Cadangan Kanonikal',
        paragraphs: [
          'Proses pencadangan mengekspor 18 tabel Room database lokal ke lembar kerja Google Spreadsheet yang terstruktur rapi dan dapat dibuka di komputer.',
        ],
      },
    ],
    steps: [
      {
        title: 'Buka Menu Backup & Restore',
        description: 'Masuk ke menu Pengaturan -> Cadangkan & Pulihkan Data.',
      },
      {
        title: 'Hubungkan Akun Google',
        description: 'Pilih akun Google Drive Anda yang aktif.',
      },
      {
        title: 'Tekan "Cadangkan Sekarang"',
        description: 'Tunggu beberapa detik hingga muncul pesan "Pencadangan Berhasil".',
      },
    ],
    limitations: [
      'Pencadangan dilakukan secara manual atau terjadwal (bukan sinkronisasi cloud otomatis realtime multi-perangkat).',
      'Memerlukan koneksi internet saat proses unggah cadangan ke Google Drive.',
    ],
    relatedSlugs: ['pin', 'lisensi', 'mulai'],
    keywords: ['backup', 'restore', 'google sheets', 'google drive', 'cadangkan data', 'pulihkan data', 'backup google sheets'],
  },

  // =========================================================================
  // BAB 20 — KEAMANAN & PIN PEMILIK
  // =========================================================================
  {
    slug: 'pin',
    title: 'BAB 20 — Keamanan & PIN Pemilik',
    category: 'keamanan',
    categoryName: 'Keamanan & Lisensi',
    order: 20,
    chapterLabel: 'BAB 20',
    description: 'Mengamankan akses laporan keuangan dan aplikasi dengan 4 angka PIN pemilik toko.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '2 menit baca',
    summary:
      'Lindungi kerahasiaan omzet toko, laporan laba rugi, dan data pelanggan dari pihak yang tidak berwenang dengan mengaktifkan kunci PIN 4 angka.',
    targetAudience: 'Pemilik toko yang mempekerjakan karyawan kasir.',
    features: [
      'Kunci PIN 4 digit angka sederhana dan mudah diingat',
      'Enkripsi aman Salted SHA-256 pada penyimpanan internal HP',
      'Mencegah karyawan membuka laporan laba bersih toko sembarangan',
    ],
    sections: [
      {
        title: '1. Melindungi Laporan Laba Rugi dari Karyawan',
        paragraphs: [
          'Jika Anda mempercayakan HP toko kepada kasir atau penjaga warung, Anda dapat mengunci modul Laporan dan Pengaturan menggunakan PIN 4 digit.',
          'Kasir tetap dapat melayani penjualan dan mencetak struk tanpa bisa melihat nominal laba bersih atau modal kulakan barang.',
        ],
      },
    ],
    steps: [
      {
        title: 'Buka Pengaturan Keamanan',
        description: 'Masuk ke menu Pengaturan -> Keamanan PIN.',
      },
      {
        title: 'Buat 4 Angka PIN Baru',
        description: 'Masukkan 4 angka rahasia yang mudah Anda ingat lalu konfirmasi ulang.',
      },
      {
        title: 'Simpan PIN',
        description: 'Mulai sekarang, menu laporan akan meminta PIN sebelum dapat dibuka.',
      },
    ],
    limitations: [
      'Pastikan Anda mengingat PIN pemilik. Jika lupa PIN, pemulihan memerlukan bantuan verifikasi email pemilik resmi.',
    ],
    relatedSlugs: ['mulai', 'lisensi', 'backup-restore'],
    keywords: ['pin', 'keamanan', 'kunci aplikasi', 'password kasir', 'salted sha256', 'proteksi toko'],
  },

  // =========================================================================
  // BAB 21 — LISENSI RESMI & PERGANTIAN PERANGKAT
  // =========================================================================
  {
    slug: 'lisensi',
    title: 'BAB 21 — Lisensi Resmi & Pergantian Perangkat',
    category: 'keamanan',
    categoryName: 'Keamanan & Lisensi',
    order: 21,
    chapterLabel: 'BAB 21',
    description: 'Ketentuan lisensi resmi sekali beli seumur hidup dan prosedur resmi jika berganti HP baru.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Lisensi Buku Warung berlaku 1 Lisensi = 1 Email Pemilik = 1 HP Android Aktif Selamanya. Tidak ada biaya langganan bulanan maupun potongan per transaksi.',
    targetAudience: 'Semua pemilik lisensi resmi Buku Warung.',
    features: [
      'Sekali beli untuk seumur hidup (Lifetime One-Time Purchase)',
      'Tanpa biaya bulanan / tahunan (No recurring fee)',
      'Prosedur resmi pemindahan lisensi saat ganti HP baru melalui Admin CS',
    ],
    sections: [
      {
        title: '1. Prosedur Resmi Jika Ganti HP Baru',
        paragraphs: [
          'Jika Anda membeli HP baru atau HP lama rusak, lisensi Anda tidak hangus karena terdaftar atas nama email pemilik Anda.',
          'Hubungi CS WhatsApp resmi SKMNetwork dengan menyertakan Email Pemilik terdaftar. Tim admin akan membantu melepaskan ikatan perangkat lama agar kode lisensi dapat diaktifkan kembali di HP baru Anda.',
        ],
      },
    ],
    relatedSlugs: ['instalasi', 'mulai', 'backup-restore', 'faq'],
    keywords: ['lisensi', 'aktivasi', 'ganti hp', 'device binding', 'lifetime', 'sekali beli'],
  },

  // =========================================================================
  // BAB 22 — KUSTOMISASI 19 JENIS USAHA ADAPTIF
  // =========================================================================
  {
    slug: 'jenis-usaha',
    title: 'BAB 22 — Kustomisasi 19 Jenis Usaha Adaptif',
    category: 'bantuan',
    categoryName: 'Bantuan & Kustomisasi',
    order: 22,
    chapterLabel: 'BAB 22',
    description: 'Penjelasan adaptasi istilah dan fitur untuk 19 kategori profil bisnis UMKM Indonesia.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '4 menit baca',
    summary:
      'Satu aplikasi untuk beragam jenis usaha. Buku Warung otomatis menyesuaikan sebutan produk, satuan barang, dan format struk sesuai profil bisnis yang Anda pilih.',
    targetAudience: 'Pelaku berbagai sektor usaha UMKM di Indonesia.',
    features: [
      'Retail: Warung Sembako, Minimarket, Toko Pakaian, Toko Elektronik, Toko Bangunan, Apotek, Konter HP',
      'Jasa: Bengkel Motor/Mobil, Cuci Kendaraan, Service Elektronik, Laundry, Salon/Barbershop, Penjahit, Fotocopy, Teknisi',
      'Food & Beverage: Warung Makan/Resto, Kedai Kopi & Kafe, Toko Roti & Kue (Bakery)',
      'Produksi: Industri Rumahan & Kerajinan',
    ],
    sections: [
      {
        title: '1. Satu Mesin Aplikasi, Beragam Adaptasi',
        paragraphs: [
          'Buku Warung tidak memerlukan instalasi aplikasi berbeda untuk bisnis berbeda. Cukup ubah jenis usaha di menu Pengaturan -> Profil Usaha, dan seluruh antarmuka aplikasi akan otomatis beradaptasi.',
        ],
      },
    ],
    relatedSlugs: ['mulai', 'produk', 'pos'],
    keywords: ['jenis usaha', 'adaptif', 'warung sembako', 'bengkel', 'apotek', 'toko bangunan', 'laundry', 'salon'],
  },

  // =========================================================================
  // BAB 23 — TANYA JAWAB POPULER (FAQ)
  // =========================================================================
  {
    slug: 'faq',
    title: 'BAB 23 — Tanya Jawab Populer (FAQ)',
    category: 'bantuan',
    categoryName: 'Bantuan & Kustomisasi',
    order: 23,
    chapterLabel: 'BAB 23',
    description: 'Jawaban atas pertanyaan paling sering diajukan mengenai penggunaan Buku Warung v0.2.0.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '4 menit baca',
    summary:
      'Kumpulan jawaban resmi seputar mode offline, printer, barcode, kasbon pelanggan, backup data, dan lisensi resmi.',
    targetAudience: 'Calon pengguna dan pemilik toko yang membutuhkan jawaban cepat.',
    features: [
      'Pertanyaan seputar koneksi offline dan kuota internet',
      'Pertanyaan seputar kompatibilitas printer Bluetooth',
      'Pertanyaan seputar keamanan data dan backup Google Sheets',
      'Pertanyaan seputar ketentuan lisensi resmi',
    ],
    sections: [
      {
        title: 'Apakah aplikasi bisa dipakai tanpa kuota internet?',
        paragraphs: [
          'Ya, aplikasi beroperasi secara offline. Kasir POS, stok barang, cetak struk, dan laporan keuangan berjalan penuh secara lokal tanpa memerlukan koneksi internet aktif. Internet hanya digunakan saat aktivasi lisensi awal atau saat Anda melakukan pencadangan ke Google Drive.',
        ],
      },
      {
        title: 'Apakah ada biaya perpanjangan langganan bulanan?',
        paragraphs: [
          'Tidak ada. Pembelian lisensi Buku Warung adalah sekali bayar seumur hidup (Lifetime One-Time Purchase).',
        ],
      },
      {
        title: 'Apakah bisa menggunakan barcode scanner?',
        paragraphs: [
          'Ya. Anda dapat scan barcode menggunakan kamera bawaan HP atau menghubungkan barcode scanner laser eksternal (Bluetooth / USB).',
        ],
      },
      {
        title: 'Berapa banyak barang dan transaksi yang bisa dicatat?',
        paragraphs: [
          'Aplikasi tidak membatasi kuota jumlah barang maupun riwayat transaksi. Batasannya bergantung pada kapasitas ruang penyimpanan internal smartphone Android Anda.',
        ],
      },
    ],
    relatedSlugs: ['troubleshooting', 'mulai', 'lisensi', 'printer'],
    keywords: ['faq', 'tanya jawab', 'offline', 'langganan', 'kapasitas', 'printer bluetooth'],
  },

  // =========================================================================
  // BAB 24 — PANDUAN MENGATASI KENDALA (TROUBLESHOOTING)
  // =========================================================================
  {
    slug: 'troubleshooting',
    title: 'BAB 24 — Panduan Mengatasi Kendala (Troubleshooting)',
    category: 'bantuan',
    categoryName: 'Bantuan & Kustomisasi',
    order: 24,
    chapterLabel: 'BAB 24',
    description: 'Langkah mandiri mengatasi printer tidak konek, backup Google Sheets gagal, atau kendala aktivasi.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Panduan perbaikan mandiri untuk menyelesaikan kendala umum operasional sehari-hari pada printer struk, backup Google Drive, dan lisensi.',
    targetAudience: 'Pengguna yang mengalami kendala teknis saat mengoperasikan aplikasi.',
    features: [
      'Solusi printer thermal tidak mencetak atau tulisan buram',
      'Solusi pencadangan Google Sheets gagal atau koneksi terputus',
      'Solusi barcode scanner tidak mendeteksi kemasan barang',
      'Kontak layanan bantuan Customer Service resmi SKMNetwork',
    ],
    sections: [
      {
        title: '1. Printer Thermal Tidak Mau Mencetak',
        paragraphs: [
          'Pemeriksaan Cepat:',
          '1. Pastikan printer dalam posisi ON dan baterai mencukupi.',
          '2. Periksa arah gulungan kertas thermal (bagian licin kertas harus menghadap ke kepala cetak printer).',
          '3. Pastikan printer sudah di-pair di pengaturan Bluetooth Android HP.',
          '4. Buka Pengaturan -> Printer di Buku Warung, pilih nama printer Anda, lalu tekan Test Print.',
        ],
      },
      {
        title: '2. Pencadangan (Backup) Google Sheets Gagal',
        paragraphs: [
          'Pemeriksaan Cepat:',
          '1. Pastikan HP sedang terhubung ke koneksi internet yang stabil.',
          '2. Pastikan akun Google Drive Anda memiliki sisa ruang penyimpanan yang cukup.',
          '3. Jika muncul pesan izin ditolak, lakukan login ulang ke akun Google Anda pada menu Cadangkan.',
        ],
      },
      {
        title: '3. Kontak Bantuan Resmi',
        paragraphs: [
          'Jika Anda masih mengalami kendala setelah mengikuti panduan di atas, tim Customer Service resmi SKMNetwork siap membantu Anda melalui WhatsApp resmi di nomor yang tertera pada website https://bukuwarung.skmnetwork.com.',
        ],
      },
    ],
    relatedSlugs: ['faq', 'printer', 'backup-restore', 'lisensi'],
    keywords: ['troubleshooting', 'kendala', 'printer error', 'backup gagal', 'bantuan cs'],
  },
];

export function getDocArticleBySlug(slug: string): DocArticle | undefined {
  return DOC_ARTICLES.find((a) => a.slug.toLowerCase() === slug.toLowerCase());
}

export function getAdjacentDocArticles(currentOrder: number): {
  prev?: DocArticle;
  next?: DocArticle;
} {
  const prev = DOC_ARTICLES.find((a) => a.order === currentOrder - 1);
  const next = DOC_ARTICLES.find((a) => a.order === currentOrder + 1);
  return { prev, next };
}

export function searchDocArticles(query: string): DocArticle[] {
  const q = query.trim().toLowerCase();
  if (!q) return [];
  return DOC_ARTICLES.filter((article) => {
    return (
      article.title.toLowerCase().includes(q) ||
      article.description.toLowerCase().includes(q) ||
      article.summary.toLowerCase().includes(q) ||
      article.categoryName.toLowerCase().includes(q) ||
      (article.targetAudience && article.targetAudience.toLowerCase().includes(q)) ||
      (article.features && article.features.some((f) => f.toLowerCase().includes(q))) ||
      article.keywords.some((kw) => kw.toLowerCase().includes(q)) ||
      (article.limitations && article.limitations.some((lim) => lim.toLowerCase().includes(q))) ||
      article.sections.some(
        (sec) =>
          sec.title.toLowerCase().includes(q) ||
          sec.paragraphs.some((p) => p.toLowerCase().includes(q)) ||
          (sec.listItems && sec.listItems.some((li) => li.toLowerCase().includes(q)))
      ) ||
      (article.steps &&
        article.steps.some(
          (st) => st.title.toLowerCase().includes(q) || st.description.toLowerCase().includes(q)
        )) ||
      (article.example &&
        (article.example.title.toLowerCase().includes(q) ||
          article.example.scenario.toLowerCase().includes(q) ||
          article.example.details.some((d) => d.toLowerCase().includes(q))))
    );
  });
}
