export interface FeatureItem {
  id: string;
  iconName: string;
  title: string;
  subtitle: string;
  description: string;
  badge?: string;
}

export interface ScreenshotItem {
  id: string;
  title: string;
  category: string;
  imageSrc: string;
  description: string;
}

export interface FaqItem {
  id: string;
  question: string;
  answer: string;
}

export interface AdaptiveBusinessProfile {
  id: string;
  name: string;
  iconName: string;
  badge: string;
  productTerm: string;
  catalogTerm: string;
  summary: string;
  sampleProducts: Array<{ name: string; price: string; unit: string; stock: string; type?: string }>;
  keyHighlights: string[];
}

export interface ProblemItem {
  id: string;
  icon: string;
  title: string;
  description: string;
  solution: string;
}

export interface BenefitItem {
  id: string;
  icon: string;
  title: string;
  description: string;
}

export interface HowToBuyStep {
  id: string;
  title: string;
  description: string;
}

export interface TrustItem {
  id: string;
  icon: string;
  title: string;
  description: string;
}

export const LANDING_CONFIG = {
  appName: 'Buku Warung',
  tagline: 'Aplikasi Kasir POS & Pembukuan UMKM',
  // Hero hierarchy: badge -> H1 (headline) -> H2 (subheadline) -> description -> CTAs.
  headline: 'Stop Bayar Bulanan untuk Aplikasi Kasir',
  subheadline: 'Pakai Buku Warung. Cukup Sekali Beli, Tanpa Bayar Bulanan.',
  heroBadge: 'Coba Dulu Sebelum Membeli',
  heroDescription:
    'Aplikasi sederhana namun lengkap untuk pembukuan dan penjualan. Cocok untuk berbagai macam usaha dari warung Kelontong sampai toko Material. Catat penjualan, stok, kas, pelanggan, hutang/piutang, dan laporan usaha tercatat rapi dalam satu aplikasi Android.',
  heroPrimaryCta: 'Coba Buku Warung Sebelum Membeli',
  heroSecondaryCta: 'Beli Lisensi Buku Warung',
  priceNormal: 100000,
  pricePromo: 50000,
  priceFormatted: 'Rp 50.000',
  priceNormalFormatted: 'Rp 100.000',
  discountBadge: 'Hemat 50% — Pembelian Sekali',
  downloadApkUrl: 'https://license.skmnetwork.com/download/buku-warung',
  directApkUrl: 'https://license.skmnetwork.com/download/buku-warung/apk',
  publicOrderUrl:
    'https://license.skmnetwork.com/beli/buku-warung?utm_source=bukuwarung_landing&utm_medium=hero_cta&utm_campaign=launch_v020',
  publicOrderStickyUrl:
    'https://license.skmnetwork.com/beli/buku-warung?utm_source=bukuwarung_landing&utm_medium=sticky_cta&utm_campaign=launch_v020',
  publicOrderPricingUrl:
    'https://license.skmnetwork.com/beli/buku-warung?utm_source=bukuwarung_landing&utm_medium=pricing_cta&utm_campaign=launch_v020',
  whatsappConsultationUrl:
    'https://wa.me/6285157056604?text=Halo%20Admin%20SKMNetwork,%20saya%20ingin%20tanya%20tentang%20paket%20aplikasi%20Buku%20Warung%20Android',
  privacyPolicyUrl: 'https://bukuwarung.skmnetwork.com/privacy-policy',
};

/**
 * Closed-testing campaign ("Test Dulu"). The capacity below is the campaign's design constant
 * and is used for copy only - never for the "X dari 50 slot" number, which always comes from
 * the server. See useTestCampaign.
 */
export const TEST_CAMPAIGN_CONFIG = {
  campaignId: 'BUKU_WARUNG_TEST_BATCH_1',
  capacity: 50,
  statusEndpoint: 'https://license.skmnetwork.com/v1/landing/test-campaign',
  registerEndpoint: 'https://license.skmnetwork.com/v1/landing/test-campaign/register',
  anchorId: 'program-test',
  headline: 'Coba Buku Warung Sebelum Membeli',
  sectionTitle: '🧪 Program Test Buku Warung',
  sectionBody:
    'Coba Buku Warung dalam aktivitas usaha Anda sebelum memutuskan untuk membeli. Peserta yang lolos akan mendapat akses lewat Google Play Closed Testing.',
  batchLabel: 'Batch 1 — Maksimal 50 Usaha',
  primaryCta: 'DAFTAR TEST DULU — KUOTA 50 USAHA',
  secondaryCta: 'BELI LANGSUNG',
  fullTitle: 'Batch Test 1 Sudah Penuh',
  fullBody: 'Kuota 50 usaha untuk batch testing ini telah terpenuhi.',
  waitingListCta: 'DAFTAR WAITING LIST',
  waitingListBody:
    'Pendaftaran batch test berikutnya belum dibuka. Konsultasikan jadwal batch berikutnya langsung ke tim kami.',
  businessTypes: [
    'Warung Sembako',
    'Warung Makan',
    'Toko Kelontong',
    'Toko Elektronik',
    'Laundry / Jasa',
    'Lainnya'
  ],
  dailyTransactionOptions: ['< 10 transaksi', '10–30 transaksi', '30–100 transaksi', '> 100 transaksi'],
  androidDeviceOptions: [
    'Samsung',
    'Xiaomi / Redmi',
    'Oppo / Realme',
    'Vivo',
    'Honor / Huawei',
    'Merek lain'
  ]
} as const;

export const PROBLEMS: ProblemItem[] = [
  {
    id: 'manual',
    icon: 'FileText',
    title: 'Pencatatan penjualan masih manual',
    description: 'Mencatat transaksi dengan buku, kalkulator, atau nota kertas yang mudah hilang.',
    solution: 'Buku Warung mencatat transaksi kasir secara digital dalam hitungan detik.',
  },
  {
    id: 'stok',
    icon: 'Package',
    title: 'Stok barang sering selisih atau habis mendadak',
    description: 'Barang habis tanpa sadar, atau stok fisik tidak cocok dengan catatan.',
    solution: 'Stok otomatis terpotong saat penjualan dan bertambah saat kulakan dari supplier.',
  },
  {
    id: 'kas',
    icon: 'Wallet',
    title: 'Uang kas tercampur dengan keuangan pribadi',
    description: 'Modal usaha, omzet harian, dan uang belanja rumah tangga bercampur aduk.',
    solution: 'Buku kas terpisah mencatat arus kas masuk & keluar usaha secara transparan setiap hari.',
  },
  {
    id: 'piutang',
    icon: 'BookOpen',
    title: 'Hutang & piutang pelanggan lupa ditagih',
    description: 'Buku catatan bon pelanggan tercecer dan sulit memantau siapa saja yang belum lunas.',
    solution: 'Buku piutang mencatat batas tempo dan riwayat cicilan bertahap hingga lunas.',
  },
  {
    id: 'laporan',
    icon: 'BarChart3',
    title: 'Laporan keuangan sulit dan makan waktu',
    description: 'Menghitung omzet bulanan dan estimasi keuntungan memakan waktu lama.',
    solution: 'Laporan omzet, estimasi laba, dan produk terlaris tersedia dalam satu klik siap ekspor PDF.',
  },
];

export const BENEFITS: BenefitItem[] = [
  {
    id: 'pos',
    icon: 'ShoppingCart',
    title: 'Kasir POS & Transaksi Cepat',
    description: 'Proses penjualan kasir cepat dengan barcode scanner kamera dan multi-metode bayar.',
  },
  {
    id: 'stok',
    icon: 'Package',
    title: 'Kelola Produk & Stok Real-Time',
    description: 'Ketahui stok barang yang tersedia dan pantau pengurangan stok otomatis saat jualan.',
  },
  {
    id: 'piutang',
    icon: 'BookOpen',
    title: 'Buku Hutang & Piutang Rapi',
    description: 'Pantau tagihan pelanggan dan hutang kulakan supplier dengan riwayat pembayaran cicilan.',
  },
  {
    id: 'kas',
    icon: 'Wallet',
    title: 'Pisahkan Arus Kas Usaha',
    description: 'Pantau uang kas masuk dari jualan dan pengeluaran operasional warung harian.',
  },
  {
    id: 'laporan',
    icon: 'BarChart3',
    title: 'Lihat Kondisi Usaha & Ekspor PDF',
    description: 'Pantau omzet, perkiraan laba kotor, dan ekspor laporan PDF siap cetak atau dibagikan.',
  },
  {
    id: 'printer',
    icon: 'Printer',
    title: 'Cetak Struk Thermal Bluetooth',
    description: 'Cetak nota struk kasir rapi dengan printer thermal 58mm atau 80mm.',
  },
  {
    id: 'katalog',
    icon: 'Share2',
    title: 'Katalog Produk untuk WhatsApp',
    description: 'Bagikan daftar harga & produk langsung ke WhatsApp pelanggan hanya sekali klik.',
  },
];

export const HOW_TO_BUY_STEPS: HowToBuyStep[] = [
  {
    id: '1',
    title: 'Pilih Produk',
    description: 'Klik "Beli Sekarang" di halaman ini untuk ke halaman pemesanan.',
  },
  {
    id: '2',
    title: 'Isi Pemesanan',
    description: 'Masukkan nama, WhatsApp, dan email di halaman pemesanan.',
  },
  {
    id: '3',
    title: 'Bayar Lisensi',
    description: 'Scan QRIS resmi atau transfer ke rekening yang tertera pada halaman pembayaran.',
  },
  {
    id: '4',
    title: 'Terima APK & Kode Lisensi',
    description: 'Admin mengirimkan file APK dan kode lisensi via WhatsApp setelah pembayaran diverifikasi.',
  },
  {
    id: '5',
    title: 'Aktivasi',
    description: 'Masukkan email dan kode lisensi di aplikasi untuk aktivasi pertama.',
  },
];

export const TRUST_ITEMS: TrustItem[] = [
  {
    id: 'brand',
    icon: 'ShieldCheck',
    title: 'Lisensi Resmi SKMNetwork',
    description: 'Produk resmi yang dikembangkan dan didukung oleh SKMNetwork.',
  },
  {
    id: 'data',
    icon: 'Smartphone',
    title: 'Data usaha tersimpan di HP Anda',
    description:
      'Data operasional usaha terutama disimpan secara lokal di perangkat Anda. Data tertentu untuk lisensi, pembelian, dan dukungan dapat diproses oleh SKMNetwork.',
  },
  {
    id: 'backup',
    icon: 'Cloud',
    title: 'Backup manual ke Google Sheets',
    description:
      'Opsional. Anda sendiri yang memulai backup manual ke Google Sheets pribadi milik Anda.',
  },
  {
    id: 'support',
    icon: 'MessageCircle',
    title: 'Dukungan teknis via WhatsApp',
    description: 'Tim support siap membantu melalui WhatsApp admin.',
  },
  {
    id: 'payment',
    icon: 'CheckCircle2',
    title: 'Pembelian melalui QRIS aman',
    description: 'Pembayaran melalui QRIS resmi yang terverifikasi.',
  },
];

export const CORE_FEATURES: FeatureItem[] = [
  {
    id: 'pos',
    iconName: 'ShoppingCart',
    title: 'Kasir POS & Transaksi',
    subtitle: 'Cepat & Praktis',
    description:
      'Proses penjualan kasir super cepat dengan scanner barcode, pencarian produk instan, diskon per item/total, serta dukungan bayar Tunai, QRIS, maupun Tempo.',
    badge: 'Multi-Metode Bayar',
  },
  {
    id: 'stok',
    iconName: 'Package',
    title: 'Manajemen Stok Real-Time',
    subtitle: 'Otomatis & Akurat',
    description:
      'Stok berkurang otomatis saat penjualan dan bertambah saat kulakan. Dilengkapi peringatan dini stok menipis agar jualan tidak pernah kehabisan barang.',
  },
  {
    id: 'kulakan',
    iconName: 'Truck',
    title: 'Pembelian & Kulakan',
    subtitle: 'Catat Barang Masuk',
    description:
      'Catat faktur belanja kulakan dari supplier dengan opsi bayar tunai maupun hutang supplier secara rapi dan terstruktur.',
  },
  {
    id: 'piutang',
    iconName: 'BookOpen',
    title: 'Buku Hutang & Piutang',
    subtitle: 'Tagihan Terpantau',
    description:
      'Catat buku piutang pelanggan dan hutang supplier secara terpisah. Catat cicilan bertahap hingga lunas dengan riwayat pembayaran yang transparan.',
  },
  {
    id: 'kas',
    iconName: 'Wallet',
    title: 'Buku Kas Masuk & Keluar',
    subtitle: 'Pisahkan Uang Pribadi',
    description:
      'Pisahkan uang usaha dengan uang pribadi. Pantau arus kas masuk dan pengeluaran operasional warung harian dengan saldo yang selalu sinkron.',
  },
  {
    id: 'laporan',
    iconName: 'BarChart3',
    title: 'Laporan Keuangan & PDF',
    subtitle: 'Analisis Omzet & Profit',
    description:
      'Laporan omzet, estimasi laba kotor, barang terlaris, dan rekap hutang yang dapat diekspor langsung ke dokumen PDF siap cetak atau dibagikan.',
  },
  {
    id: 'katalog',
    iconName: 'Share2',
    title: 'Katalog Produk WhatsApp',
    subtitle: 'Promosi Lebih Mudah',
    description:
      'Pilih produk dari etalase dan bagikan daftar harga serta stok langsung ke WhatsApp pelanggan dalam format pesan rapi hanya dalam sekali klik.',
    badge: 'Fitur Baru',
  },
  {
    id: 'printer',
    iconName: 'Printer',
    title: 'Cetak Struk Bluetooth',
    subtitle: 'Standar ESC/POS',
    description:
      'Cetak nota struk kasir profesional menggunakan printer thermal Bluetooth 58mm atau 80mm dengan logo toko, rincian diskon, dan catatan terima kasih.',
  },
  {
    id: 'multi-usaha',
    iconName: 'Store',
    title: 'Multi-Tipe Usaha (Adaptif)',
    subtitle: 'Fleksibel untuk Berbagai Bisnis',
    description:
      'Terminologi aplikasi otomatis menyesuaikan jenis usaha: Warung Sembako (Produk), Apotek (Obat/Alkes), Toko Bangunan (Material), dan Bengkel (Sparepart & Jasa).',
    badge: 'Adaptif',
  },
];

export const SCREENSHOTS: ScreenshotItem[] = [
  {
    id: '03_beranda',
    title: 'Beranda Kasir Utama',
    category: 'Dashboard',
    imageSrc: '/img/screenshots/03_beranda.png',
    description: 'Akses cepat ke 7 modul utama: Jualan, Produk, Pembelian, Hutang, Kas, Laporan, dan Pengaturan.',
  },
  {
    id: '04_jualan_pos',
    title: 'Kasir POS & Checkout',
    category: 'POS Kasir',
    imageSrc: '/img/screenshots/04_jualan_pos.png',
    description: 'Antarmuka kasir responsif dengan keranjang belanja, kalkulasi diskon, dan pemilihan metode pembayaran.',
  },
  {
    id: '05_produk_stok',
    title: 'Katalog Produk & Stok',
    category: 'Inventori',
    imageSrc: '/img/screenshots/05_produk_stok.png',
    description: 'Manajemen master produk, kategori, harga beli, harga jual, barcode scanner, dan limit stok minimum.',
  },
  {
    id: '06_pembelian',
    title: 'Kulakan & Barang Masuk',
    category: 'Pembelian',
    imageSrc: '/img/screenshots/06_pembelian.png',
    description: 'Pencatatan pembelian barang masuk dari supplier dengan integrasi stok otomatis.',
  },
  {
    id: '07_pelanggan_piutang',
    title: 'Buku Piutang Pelanggan',
    category: 'Piutang',
    imageSrc: '/img/screenshots/07_pelanggan_piutang.png',
    description: 'Pantau siapa saja pelanggan yang belum lunas beserta batas tempo dan tombol pembayaran cicilan.',
  },
  {
    id: '09_uang_kas',
    title: 'Buku Kas Masuk & Keluar',
    category: 'Buku Kas',
    imageSrc: '/img/screenshots/09_uang_kas.png',
    description: 'Rekapitulasi uang kas masuk dari penjualan dan pengeluaran beban harian secara transparan.',
  },
  {
    id: '10_laporan',
    title: 'Laporan Keuangan & Laba Rugi',
    category: 'Laporan',
    imageSrc: '/img/screenshots/10_laporan.png',
    description: 'Grafik omzet, ringkasan profit, produk terlaris, dan tombol ekspor dokumen PDF laporan.',
  },
];

export const ADAPTIVE_BUSINESS_PROFILES: AdaptiveBusinessProfile[] = [
  {
    id: 'WARUNG_SEMBAKO',
    name: 'Warung Sembako & Kelontong',
    iconName: 'Store',
    badge: 'Paling Populer',
    productTerm: 'Produk',
    catalogTerm: 'Katalog Produk',
    summary: 'Optimal untuk pencatatan barang kebutuhan pokok sehari-hari dengan transaksi cepat dan barcode scanner.',
    sampleProducts: [
      { name: 'Minyak Goreng 1L', price: 'Rp 18.000', unit: 'liter', stock: '24 liter' },
      { name: 'Beras Premium 5kg', price: 'Rp 72.000', unit: 'karung', stock: '15 karung' },
      { name: 'Gula Pasir 1kg', price: 'Rp 17.500', unit: 'kg', stock: '40 kg' },
    ],
    keyHighlights: [
      'Terminologi standar: Produk & Stok',
      'Scan barcode instan saat kasir',
      'Peringatan otomatis saat sembako menipis',
      'Katalog WhatsApp: *KATALOG PRODUK*',
    ],
  },
  {
    id: 'APOTEK_OBAT',
    name: 'Apotek & Toko Obat',
    iconName: 'Pill',
    badge: 'Farmasi & Alkes',
    productTerm: 'Obat / Alkes',
    catalogTerm: 'Katalog Obat / Alkes',
    summary: 'Terminologi otomatis beralih menjadi Obat / Alkes untuk pelacakan obat strip, botol, dan peralatan medis.',
    sampleProducts: [
      { name: 'Paracetamol 500mg', price: 'Rp 6.500', unit: 'strip', stock: '50 strip' },
      { name: 'Amoxicillin 500mg', price: 'Rp 12.000', unit: 'strip', stock: '30 strip' },
      { name: 'Vitamin C 1000mg', price: 'Rp 35.000', unit: 'botol', stock: '20 botol' },
    ],
    keyHighlights: [
      'Terminologi adaptif: Obat / Alkes & Stok',
      'Dukungan satuan strip, blister, botol, box',
      'Catatan pemakaian & struk obat untuk pasien',
      'Katalog WhatsApp: *KATALOG OBAT / ALKES*',
    ],
  },
  {
    id: 'TOKO_BANGUNAN',
    name: 'Toko Bangunan & Material',
    iconName: 'Hammer',
    badge: 'Material & Konstruksi',
    productTerm: 'Material',
    catalogTerm: 'Katalog Material',
    summary: 'Mendukung ragam satuan material proyek seperti sak, batang, lembar, meter, dan piutang tukang.',
    sampleProducts: [
      { name: 'Semen Tiga Roda 50kg', price: 'Rp 65.000', unit: 'sak', stock: '80 sak' },
      { name: 'Cat Tembok Putih 5kg', price: 'Rp 115.000', unit: 'kaleng', stock: '12 kaleng' },
      { name: 'Paku Kayu 5cm', price: 'Rp 22.000', unit: 'kg', stock: '25 kg' },
    ],
    keyHighlights: [
      'Terminologi adaptif: Material & Stok',
      'Dukungan satuan proyek (sak, kaleng, batang, kg)',
      'Buku piutang khusus langganan kontraktor/tukang',
      'Katalog WhatsApp: *KATALOG MATERIAL*',
    ],
  },
  {
    id: 'BENGKEL_MOTOR_MOBIL',
    name: 'Bengkel Motor & Mobil',
    iconName: 'Wrench',
    badge: 'Oli, Sparepart & Servis',
    productTerm: 'Sparepart & Oli',
    catalogTerm: 'Katalog Sparepart & Oli',
    summary: 'Mengkombinasikan barang fisik (sparepart, oli) dan jasa servis non-stok dalam satu nota struk kasir.',
    sampleProducts: [
      { name: 'Oli MPX2 0.8L', price: 'Rp 55.000', unit: 'botol', stock: '15 botol', type: 'Fisik' },
      { name: 'Busi NGK CR6HSA', price: 'Rp 25.000', unit: 'pcs', stock: '20 pcs', type: 'Fisik' },
      { name: 'Jasa Ganti Oli & Servis Ringan', price: 'Rp 25.000', unit: 'jasa', stock: 'Tanpa Stok', type: 'Jasa' },
    ],
    keyHighlights: [
      'Terminologi adaptif: Sparepart & Oli & Jasa Servis',
      'Dukungan item Fisik (berkurang stok) & Jasa (tanpa stok)',
      'Struk kasir mencantumkan jasa montir & onderdil',
      'Katalog WhatsApp: *KATALOG SPAREPART & OLI*',
    ],
  },
];

export const COMPARISON_POINTS = [
  {
    feature: 'Model Pembayaran',
    bukuWarung: 'Pembelian sekali, tanpa langganan bulanan',
    others: 'Aplikasi kasir/Android lain umumnya berlangganan bulanan',
    highlight: true,
  },
  {
    feature: 'Koneksi Internet',
    bukuWarung: 'Offline-First (Tidak perlu kuota untuk jualan)',
    others: 'Wajib Online (Tidak bisa jualan saat internet mati)',
    highlight: true,
  },
  {
    feature: 'Keamanan Data Transaksi',
    bukuWarung: 'Tersimpan di HP Anda Sendiri + Backup Google Sheets',
    others: 'Tersimpan di Cloud Server Pihak Ketiga',
    highlight: false,
  },
  {
    feature: 'Cetak Struk Bluetooth',
    bukuWarung: 'Gratis Semua Ukuran (58mm & 80mm)',
    others: 'Seringkali Dibatasi / Harus Akun Pro',
    highlight: false,
  },
  {
    feature: 'Katalog Produk WhatsApp',
    bukuWarung: 'Sudah Termasuk (Sekali Klik)',
    others: 'Add-on Berbayar Terpisah',
    highlight: false,
  },
  {
    feature: 'Batas Jumlah Transaksi / Produk',
    bukuWarung: 'Tanpa Batas (Unlimited)',
    others: 'Dibatasi Tier Paket',
    highlight: false,
  },
];

export const FAQS: FaqItem[] = [
  {
    id: 'faq-1',
    question: 'Apakah Buku Warung memerlukan kuota internet setiap hari?',
    answer:
      'Untuk transaksi harian, tidak. Fitur transaksi utama dirancang offline-first dan data operasional utama disimpan secara lokal di perangkat. Penjualan, pemindaian barcode, cetak struk, buku kas, dan laporan berjalan normal tanpa paket internet. Aktivasi lisensi, verifikasi lisensi manual, fitur online tertentu, dan backup yang tersedia membutuhkan koneksi internet. Lihat Kebijakan Privasi untuk rinciannya.',
  },
  {
    id: 'faq-2',
    question: 'HP Android apa saja yang bisa digunakan?',
    answer:
      'Aplikasi Buku Warung dapat dipasang di hampir semua HP Android (Android versi 7.0 Nougat ke atas). Ukuran aplikasi ringan dan hemat memori.',
  },
  {
    id: 'faq-3',
    question: 'Berapa harga lisensi resmi Buku Warung?',
    answer:
      'Lisensi resmi Buku Warung berharga {price} saat promo peluncuran aktif, atau {priceNormal} setelah promo berakhir. Pembayaran bersifat sekali beli untuk satu perangkat Android, tanpa iuran bulanan maupun tahunan, dan tanpa potongan komisi transaksi.',
  },
  {
    id: 'faq-4',
    question: 'Bagaimana cara mencoba atau mengunduh aplikasi?',
    answer:
      'Anda dapat mengunduh file APK installer langsung dari halaman ini melalui tombol "Download Buku Warung". Anda bisa memasang langsung di HP Android Anda.',
  },
  {
    id: 'faq-5',
    question: 'Bagaimana cara pembayaran lisensi?',
    answer:
      'Pembayaran dilakukan melalui QRIS resmi yang mendukung BCA, Mandiri, BRI, GoPay, OVO, Dana, ShopeePay, atau transfer bank.',
  },
  {
    id: 'faq-6',
    question: 'Bagaimana cara aktivasi lisensi setelah membeli?',
    answer:
      'Setelah pemesanan dan pembayaran, Anda akan menerima kode lisensi resmi 16-karakter. Buka aplikasi Buku Warung di HP Anda, masukkan email dan kode lisensi tersebut pada layar aktivasi.',
  },
  {
    id: 'faq-7',
    question: 'Apakah data pembukuan usaha saya aman?',
    answer:
      'Data operasional usaha Anda terutama disimpan secara lokal di perangkat, di dalam penyimpanan internal aplikasi Android. Namun aplikasi ini tidak sepenuhnya tanpa server: data tertentu yang diperlukan untuk lisensi, pembelian, dukungan, dan operasional layanan dapat diproses oleh SKMNetwork. Anda juga dapat memakai backup manual ke Google Sheets pribadi bila fitur tersebut sudah tersedia. Rincian lengkap ada di Kebijakan Privasi.',
  },
  {
    id: 'faq-8',
    question: 'Bagaimana jika saya ganti HP di kemudian hari?',
    answer:
      'Lisensi terikat pada email terdaftar Anda. Jika mengganti HP, Anda dapat memindahkan lisensi ke perangkat baru melalui fitur Pemulihan (Recovery) yang dibantu oleh Administrator SKMNetwork.',
  },
  {
    id: 'faq-9',
    question: 'Printer thermal apa saja yang didukung untuk cetak struk?',
    answer:
      'Buku Warung mendukung semua printer thermal Bluetooth standar ESC/POS ukuran 58mm maupun 80mm.',
  },
  {
    id: 'faq-10',
    question: 'Bagaimana jika saya butuh bantuan atau panduan teknis?',
    answer:
      'Tersedia buku panduan lengkap di website ini serta layanan bantuan teknis langsung melalui WhatsApp Admin SKMNetwork.',
  },
];
