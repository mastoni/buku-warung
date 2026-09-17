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

export const LANDING_CONFIG = {
  appName: 'Buku Warung',
  tagline: 'Aplikasi Kasir POS & Pembukuan UMKM',
  headline: 'Kelola Kasir, Stok, & Pembukuan Warung Lebih Mudah Tanpa Biaya Langganan',
  subheadline:
    'Aplikasi kasir POS offline-first yang dirancang khusus untuk Warung Sembako, Toko Obat, Toko Bangunan, Bengkel, dan UMKM. Cukup sekali beli seumur hidup, tanpa iuran bulanan.',
  priceNormal: 100000,
  pricePromo: 50000,
  priceFormatted: 'Rp 50.000',
  priceNormalFormatted: 'Rp 100.000',
  discountBadge: 'Hemat 50% — Sekali Beli Seumur Hidup',
  publicOrderUrl:
    'https://license.skmnetwork.com/beli/buku-warung?utm_source=bukuwarung_landing&utm_medium=hero_cta&utm_campaign=launch_v020',
  publicOrderStickyUrl:
    'https://license.skmnetwork.com/beli/buku-warung?utm_source=bukuwarung_landing&utm_medium=sticky_cta&utm_campaign=launch_v020',
  publicOrderPricingUrl:
    'https://license.skmnetwork.com/beli/buku-warung?utm_source=bukuwarung_landing&utm_medium=pricing_cta&utm_campaign=launch_v020',
  whatsappConsultationUrl:
    'https://wa.me/6285157056604?text=Halo%20Admin%20SKMNetwork,%20saya%20ingin%20tanya%20tentang%20aplikasi%20Buku%20Warung%20Android',
};

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
    id: '08_supplier_hutang',
    title: 'Buku Hutang Supplier',
    category: 'Hutang Usaha',
    imageSrc: '/img/screenshots/08_supplier_hutang.png',
    description: 'Kelola tagihan supplier kulakan dan catat pengeluaran pelunasan langsung ke kas operasional.',
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
  {
    id: '11_pengaturan',
    title: 'Profil Toko & Printer',
    category: 'Pengaturan',
    imageSrc: '/img/screenshots/11_pengaturan.png',
    description: 'Kustomisasi identitas toko, setting printer Bluetooth, konfigurasi PIN kasir, dan lisensi resmi.',
  },
];

export const COMPARISON_POINTS = [
  {
    feature: 'Model Pembayaran',
    bukuWarung: 'Rp 50.000 Sekali Beli Seumur Hidup',
    others: 'Rp 50.000 – Rp 150.000 / BULAN (Berlangganan)',
    highlight: true,
  },
  {
    feature: 'Koneksi Internet',
    bukuWarung: '100% Offline-First (Tanpa Kuota Harian)',
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
    question: 'Apakah aplikasi Buku Warung harus selalu terkoneksi internet?',
    answer:
      'Tidak. Buku Warung dirancang dengan arsitektur 100% Offline-First. Seluruh pencatatan kasir, stok, transaksi, kas, dan hutang piutang berjalan lancar tanpa memerlukan kuota internet. Koneksi internet hanya dibutuhkan satu kali saat aktivasi lisensi awal.',
  },
  {
    id: 'faq-2',
    question: 'Apakah aplikasi ini mendukung printer thermal Bluetooth?',
    answer:
      'Ya. Buku Warung mendukung standar printer thermal Bluetooth ESC/POS ukuran 58mm maupun 80mm. Anda dapat langsung mencetak struk transaksi kasir, detail diskon, dan catatan toko secara instan.',
  },
  {
    id: 'faq-3',
    question: 'Bagaimana sistem lisensi Rp 50.000 ini bekerja?',
    answer:
      'Lisensi Buku Warung berlaku 1 Lisensi = 1 Email Pemilik = 1 Perangkat Android Aktif. Pembayaran bersifat sekali beli untuk seumur hidup (One-Time Purchase), tanpa biaya langganan bulanan, tanpa biaya tahunan, dan tanpa potongan per transaksi.',
  },
  {
    id: 'faq-4',
    question: 'Bagaimana jika saya mengganti HP atau HP di-reset pabrik?',
    answer:
      'Jika Anda menginstal ulang pada HP yang sama, aplikasi akan langsung mengenali lisensi Anda secara otomatis. Jika Anda berganti HP baru atau melakukan reset pabrik, Anda cukup mengajukan pemindahan lisensi resmi melalui menu pemulihan (Recovery) yang akan diproses oleh Administrator.',
  },
  {
    id: 'faq-5',
    question: 'Bagaimana cara backup dan mengamankan data pembukuan saya?',
    answer:
      'Data pembukuan tersimpan aman di penyimpanan internal HP Anda. Buku Warung juga dilengkapi fitur pencadangan otomatis (Backup & Restore) ke Google Sheets pribadi milik Anda sendiri, sehingga data Anda tidak pernah hilang dan tidak bisa diintip pihak lain.',
  },
];
