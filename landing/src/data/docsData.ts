export type DocCategoryKey =
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

export interface DocArticle {
  slug: string;
  title: string;
  category: DocCategoryKey;
  categoryName: string;
  order: number;
  description: string;
  version: string;
  updatedAt: string;
  readTime: string;
  summary: string;
  sections: DocSection[];
  steps?: DocStep[];
  callouts?: DocCallout[];
  example?: DocExample;
  limitations?: string[];
  relatedSlugs: string[];
  keywords: string[];
}

export const DOC_CATEGORIES: DocCategoryMeta[] = [
  {
    key: 'mulai',
    name: 'Mulai Menggunakan',
    description: 'Aktivasi lisensi, profil usaha, dan adaptasi jenis bisnis.',
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

export const DOC_ARTICLES: DocArticle[] = [
  // 1. Mulai
  {
    slug: 'mulai',
    title: 'Mulai Menggunakan Buku Warung',
    category: 'mulai',
    categoryName: 'Mulai Menggunakan',
    order: 1,
    description: 'Langkah awal menjalankan aplikasi Buku Warung, aktivasi lisensi, dan mengatur profil toko.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Panduan lengkap memulai aplikasi Buku Warung saat pertama kali diinstal di HP Android, mengatur identitas toko, serta aktivasi lisensi resmi.',
    sections: [
      {
        title: 'Filosofi 100% Offline-First',
        paragraphs: [
          'Buku Warung dirancang dengan arsitektur 100% Offline-First. Semua data transaksi kasir, stok, kas, dan buku hutang tersimpan secara aman di memori internal HP Anda.',
          'Koneksi internet hanya dibutuhkan satu kali saat aktivasi lisensi awal atau saat Anda memilih mencadangkan data ke Google Drive.',
        ],
      },
      {
        title: 'Pengaturan Identitas Usaha',
        paragraphs: [
          'Identitas usaha seperti nama toko, nama pemilik, nomor telepon, dan alamat yang Anda masukkan saat setup awal akan otomatis dicetak pada header struk kasir thermal dan lembar laporan PDF resmi.',
        ],
      },
    ],
    steps: [
      {
        title: 'Buka Aplikasi & Masukkan Lisensi',
        description:
          'Masukkan email pemilik dan kode lisensi resmi Anda (format: BW-XXXX-XXXX-XXXX) lalu tekan tombol Aktivasi Lisensi.',
      },
      {
        title: 'Pilih Jenis Usaha Utama',
        description:
          'Pilih dari 19 kategori usaha (Warung Sembako, Apotek, Bengkel, Toko Bangunan, Laundry, dsb) agar istilah aplikasi otomatis menyesuaikan.',
      },
      {
        title: 'Pilih Aktivitas Tambahan (Opsional)',
        description:
          'Jika warung Anda juga melayani isi pulsa atau ganti oli, centang aktivitas tambahan untuk membuka fitur multi-produk.',
      },
      {
        title: 'Konfirmasi Profil Toko',
        description:
          'Lengkapi nama warung, nomor kontak WhatsApp, dan alamat toko, lalu tekan Selesai & Masuk ke Beranda.',
      },
    ],
    callouts: [
      {
        type: 'info',
        title: 'Lisensi 1 Perangkat Seumur Hidup',
        text: 'Satu lisensi berlaku selamanya untuk 1 perangkat Android aktif. Jika ganti HP, hubungi admin resmi untuk verifikasi pemindahan.',
      },
    ],
    example: {
      title: 'Contoh Setup Warung Sembako Berkah',
      scenario: 'Ibu Siti membuka usaha warung kelontong yang juga melayani jualan pulsa & token listrik.',
      details: [
        'Jenis Usaha Utama: Warung Sembako / Kelontong',
        'Aktivitas Tambahan: Konter Pulsa & Token',
        'Hasil: Aplikasi menampilkan menu stok sembako fisik sekaligus mendukung produk digital tanpa stok.',
      ],
    },
    limitations: [
      'Aplikasi beroperasi secara mandiri di 1 perangkat (bukan sistem multi-kasir realtime cloud tanpa backup).',
    ],
    relatedSlugs: ['beranda', 'produk', 'lisensi', 'pin'],
    keywords: ['mulai', 'onboarding', 'aktivasi lisensi', 'profil toko', 'offline', 'setup'],
  },

  // 2. Beranda & Navigasi
  {
    slug: 'beranda',
    title: 'Mengenal Beranda & Navigasi',
    category: 'mulai',
    categoryName: 'Mulai Menggunakan',
    order: 2,
    description: 'Memahami tampilan ringkasan usaha di layar utama dan navigasi 7 modul operasional.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Layar Beranda menyajikan ringkasan omzet harian, saldo kas aktif, peringatan stok menipis, dan akses cepat ke seluruh menu.',
    sections: [
      {
        title: 'Kartu Ringkasan Real-Time',
        paragraphs: [
          'Beranda Buku Warung secara otomatis memperbarui angka penjualan hari ini, estimasi laba kotor, sisa kas, total piutang pelanggan, dan tagihan supplier tanpa perlu refresh manual.',
        ],
        listItems: [
          'Penjualan Hari Ini: Total omzet dari kasir POS',
          'Saldo Kas: Uang tunai yang saat ini tersedia di laci kasir',
          'Peringatan Stok Menipis: Daftar barang yang perlu segera dikulak',
          'Total Piutang: Uang toko yang masih dibawa pelanggan (kasbon)',
        ],
      },
      {
        title: 'Bilah Navigasi Bawah (Bottom Navigation)',
        paragraphs: [
          'Aplikasi dilengkapi 5 tab utama: Beranda, Kasir (POS), Produk, Laporan, dan Pengaturan. Modul Pembelian, Kas, dan Hutang dapat diakses langsung melalui kartu pintasan di Beranda.',
        ],
      },
    ],
    callouts: [
      {
        type: 'tip',
        title: 'Peringatan Stok Otomatis',
        text: 'Klik pada kartu "Stok Menipis" di Beranda untuk langsung melihat produk apa saja yang sudah di bawah batas minimum.',
      },
    ],
    relatedSlugs: ['mulai', 'pos', 'laporan', 'kas'],
    keywords: ['beranda', 'dashboard', 'ringkasan usaha', 'navigasi', 'stok menipis'],
  },

  // 3. Produk & Stok
  {
    slug: 'produk',
    title: 'Produk & Manajemen Stok',
    category: 'inventori',
    categoryName: 'Produk & Inventori',
    order: 3,
    description: 'Cara menambah produk fisik, mengatur harga beli/jual, stok minimum, dan scan barcode.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '4 menit baca',
    summary:
      'Kelola master produk barang fisik dengan mudah. Setiap transaksi penjualan otomatis memotong stok dan setiap kulakan otomatis menambah stok.',
    sections: [
      {
        title: 'Karakteristik Produk Fisik (PHYSICAL)',
        paragraphs: [
          'Produk berjenis fisik (PHYSICAL) memiliki pelacakan stok ketat. Setiap kali terjadi penjualan di kasir, jumlah stok akan langsung berkurang secara atomik.',
          'Anda dapat menentukan batas minimum stok agar aplikasi memberikan peringatan sebelum barang dagangan habis.',
        ],
      },
      {
        title: 'Pengaturan Barcode',
        paragraphs: [
          'Setiap produk dapat didaftarkan kode barcode-nya dengan cara memindai (scan) langsung kemasan barang menggunakan kamera HP atau laser scanner Bluetooth.',
        ],
      },
    ],
    steps: [
      {
        title: 'Buka Menu Produk',
        description: 'Tekan tab Produk pada bilah bawah, lalu tekan tombol "+ Tambah Produk".',
      },
      {
        title: 'Isi Informasi Barang',
        description: 'Masukkan nama barang, kategori (misal: Sembako / Minuman), dan satuan (pcs, botol, kg, sak).',
      },
      {
        title: 'Tentukan Harga & Stok',
        description:
          'Isi harga modal (harga beli), harga jual ke pelanggan, jumlah stok saat ini, dan batas minimum stok.',
      },
      {
        title: 'Scan Barcode (Opsional)',
        description: 'Arahkan kamera ke barcode kemasan barang jika ingin mendata barcode untuk kasir cepat.',
      },
      {
        title: 'Simpan',
        description: 'Tekan Simpan. Produk langsung muncul di katalog dan siap dijual di kasir POS.',
      },
    ],
    example: {
      title: 'Contoh Input Beras Premium 5kg',
      scenario: 'Menambah stok 20 karung beras @ Rp 68.000 dengan harga jual Rp 75.000.',
      details: [
        'Nama Produk: Beras Rojolele 5kg',
        'Harga Beli: Rp 68.000',
        'Harga Jual: Rp 75.000',
        'Stok Awal: 20 karung',
        'Minimum Stok: 3 karung',
      ],
    },
    relatedSlugs: ['fuel', 'digital', 'jasa', 'pos', 'pembelian'],
    keywords: ['produk', 'stok', 'barcode', 'harga beli', 'harga jual', 'kategori', 'inventori'],
  },

  // 4. FUEL
  {
    slug: 'fuel',
    title: 'Produk Khusus: Bahan Bakar (FUEL)',
    category: 'inventori',
    categoryName: 'Produk & Inventori',
    order: 4,
    description: 'Dukungan pecahan desimal (contoh: 12,5 Liter) untuk usaha bensin eceran dan Pertamini.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Produk bertipe FUEL memungkinkan pengisian jumlah desimal dengan perhitungan harga akurat dan pengurangan stok literan yang presisi.',
    sections: [
      {
        title: 'Pecahan Desimal & Perhitungan Otomatis',
        paragraphs: [
          'Berbeda dengan produk retail biasa yang menggunakan bilangan bulat, produk FUEL mendukung input angka pecahan (contoh: 2,5 L, 10,75 L, 15 L).',
          'Subtotal harga dihitung secara presisi sesuai rumus: Qty Desimal × Harga per Liter.',
        ],
      },
    ],
    example: {
      title: 'Contoh Transaksi Pertalite Eceran',
      scenario: 'Pembeli mengisi bensin Pertalite sebanyak 12,5 liter @ Rp 10.000 per liter.',
      details: [
        'Produk: Pertalite (Tipe FUEL)',
        'Jumlah: 12.5 liter',
        'Harga per Liter: Rp 10.000',
        'Total Pembayaran: Rp 125.000',
        'Efek Stok: Tangki berkurang tepat 12.5 liter.',
      ],
    },
    relatedSlugs: ['produk', 'pos', 'jenis-usaha'],
    keywords: ['fuel', 'bensin', 'pertalite', 'pertamini', 'desimal', 'liter'],
  },

  // 5. DIGITAL
  {
    slug: 'digital',
    title: 'Produk Digital: Pulsa & Token',
    category: 'inventori',
    categoryName: 'Produk & Inventori',
    order: 5,
    description: 'Mencatat penjualan pulsa, paket data, token PLN, dan voucher game tanpa pelacakan stok fisik.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Produk digital ditandai badge Non-Stok sehingga Anda dapat mencatat keuntungan penjualan tanpa khawatir peringatan stok habis.',
    sections: [
      {
        title: 'Bebas Stok Fisik',
        paragraphs: [
          'Produk dengan tipe DIGITAL tidak memotong stok inventori fisik di gudang. Keuntungan dihitung dari selisih harga modal agen dengan harga jual ke pembeli.',
        ],
      },
    ],
    relatedSlugs: ['produk', 'jasa', 'pos'],
    keywords: ['digital', 'pulsa', 'token pln', 'paket data', 'non stok'],
  },

  // 6. JASA
  {
    slug: 'jasa',
    title: 'Produk Jasa & Layanan',
    category: 'inventori',
    categoryName: 'Produk & Inventori',
    order: 6,
    description: 'Pencatatan jasa servis motor, ongkos pangkas rambut, laundry, dan biaya instalasi teknisi.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Produk tipe SERVICE memungkinkan bisnis jasa mencatat pendapatan tenaga kerja tanpa stok barang fisik.',
    sections: [
      {
        title: 'Kombinasi Jasa & Sparepart dalam Satu Struk',
        paragraphs: [
          'Di bengkel atau toko servis, Anda dapat menggabungkan item fisik (seperti Oli dan Busi) dengan item jasa (seperti Jasa Servis Ringan) dalam satu keranjang belanja kasir.',
        ],
      },
    ],
    relatedSlugs: ['produk', 'pos', 'printer'],
    keywords: ['jasa', 'servis', 'laundry', 'salon', 'ongkos kerja'],
  },

  // 7. POS / Jualan
  {
    slug: 'pos',
    title: 'Transaksi Kasir (POS / Jualan)',
    category: 'pos',
    categoryName: 'Kasir & Penjualan',
    order: 7,
    description: 'Panduan lengkap transaksi kasir cepat, barcode scan, keranjang belanja, dan pembayaran.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '4 menit baca',
    summary:
      'Layar kasir POS yang cepat dan intuitif. Mendukung pencarian instan, scan barcode kamera, diskon, pembayaran Tunai, QRIS statis, dan Kasbon tempo.',
    sections: [
      {
        title: 'Metode Pembayaran yang Didukung',
        paragraphs: [
          'Buku Warung menyediakan 3 metode pembayaran resmi:',
        ],
        listItems: [
          'Tunai (Cash): Input nominal uang pembeli, sistem otomatis menghitung kembalian dan menambah saldo kas masuk.',
          'QRIS: Pembeli scan QRIS merchant, transaksi tercatat resmi tanpa menambah uang fisik di laci kas.',
          'Hutang / Kasbon: Penjualan tempo atas nama pelanggan terdaftar, otomatis masuk ke buku piutang.',
        ],
      },
    ],
    steps: [
      {
        title: 'Pilih Produk atau Scan Barcode',
        description: 'Ketik nama barang di kolom pencarian atau tekan ikon barcode untuk memindai kemasan.',
      },
      {
        title: 'Atur Jumlah & Diskon',
        description: 'Ubah kuantitas barang sesuai pesanan pembeli dan tambahkan diskon jika ada promo.',
      },
      {
        title: 'Pilih Metode Pembayaran',
        description: 'Tekan tombol "Bayar" lalu pilih Tunai, QRIS, atau Kasbon Pelanggan.',
      },
      {
        title: 'Cetak Struk Transaksi',
        description: 'Setelah pembayaran sukses, struk dapat langsung dicetak ke printer Bluetooth thermal.',
      },
    ],
    callouts: [
      {
        type: 'info',
        title: 'QRIS Statis Mandiri',
        text: 'QRIS di Buku Warung adalah QRIS statis milik merchant sendiri (dana langsung masuk ke rekening bank/e-wallet Anda).',
      },
    ],
    relatedSlugs: ['diskon', 'pelanggan', 'printer', 'beranda'],
    keywords: ['pos', 'kasir', 'jualan', 'tunai', 'qris', 'struk', 'kembalian'],
  },

  // 8. Diskon
  {
    slug: 'diskon',
    title: 'Diskon Transaksi',
    category: 'pos',
    categoryName: 'Kasir & Penjualan',
    order: 8,
    description: 'Menerapkan potongan harga nominal (Rp) maupun persentase (%) pada kasir belanja.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '2 menit baca',
    summary:
      'Berikan diskon kepada pelanggan setia dengan perhitungan transparan yang langsung tercantum di struk dan laporan laba rugi.',
    sections: [
      {
        title: 'Perhitungan Diskon Universal',
        paragraphs: [
          'Diskon dapat diinput dalam bentuk nominal rupiah (misal Rp 5.000) atau persentase (misal 10%). Total penjualan bersih akan dikurangi diskon sebelum menghitung laba bersih.',
        ],
      },
    ],
    relatedSlugs: ['pos', 'laporan', 'printer'],
    keywords: ['diskon', 'potongan harga', 'promo', 'persen', 'nominal'],
  },

  // 9. Pelanggan & Piutang
  {
    slug: 'pelanggan',
    title: 'Pelanggan & Buku Piutang',
    category: 'pos',
    categoryName: 'Kasir & Penjualan',
    order: 9,
    description: 'Mencatat bon kasbon pelanggan, melihat rekap piutang, dan menerima cicilan pelunasan.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Kelola buku piutang pelanggan secara rapi. Catat cicilan bertahap hingga lunas dengan riwayat transaksi yang jelas.',
    sections: [
      {
        title: 'Pencatatan Cicilan Piutang',
        paragraphs: [
          'Ketika pelanggan membayar sebagian bon kasbon, masukkan jumlah setoran cicilan. Status piutang akan otomatis diperbarui (UNPAID -> PARTIAL -> PAID).',
        ],
      },
    ],
    relatedSlugs: ['pos', 'kas', 'laporan'],
    keywords: ['pelanggan', 'piutang', 'kasbon', 'hutang pelanggan', 'cicilan'],
  },

  // 10. Supplier
  {
    slug: 'supplier',
    title: 'Supplier & Pemasok',
    category: 'pembelian',
    categoryName: 'Pembelian & PO',
    order: 10,
    description: 'Mengelola direktori supplier, kontak sales, dan memantau saldo hutang kulakan.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Simpan data supplier kulakan dan pantau riwayat pasokan barang serta jadwal pelunasan faktur supplier.',
    sections: [
      {
        title: 'Buku Hutang Supplier (Hutang Usaha)',
        paragraphs: [
          'Jika kulakan dilakukan secara tempo/kredit, saldo hutang supplier akan tercatat otomatis. Pelunasan hutang akan mengurangi saldo kas operasional toko.',
        ],
      },
    ],
    relatedSlugs: ['pembelian', 'purchase-order', 'kas'],
    keywords: ['supplier', 'pemasok', 'sales', 'hutang supplier', 'kulakan tempo'],
  },

  // 11. Pembelian / Kulakan
  {
    slug: 'pembelian',
    title: 'Pembelian Langsung (Kulakan)',
    category: 'pembelian',
    categoryName: 'Pembelian & PO',
    order: 11,
    description: 'Mencatat barang masuk hasil belanja kulakan tunai maupun kredit dari supplier.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Pencatatan kulakan langsung otomatis menambah stok barang di etalase dan mengurangi saldo kas toko (atau mencatat hutang supplier).',
    sections: [
      {
        title: 'Efek Otomatis Pembelian Langsung',
        paragraphs: [
          '1. Stok produk langsung bertambah di master inventori.',
          '2. Jika dibayar Tunai, kas keluar dicatat otomatis di Buku Kas.',
          '3. Jika Tempo, tercatat sebagai Hutang Supplier di buku hutang usaha.',
        ],
      },
    ],
    relatedSlugs: ['supplier', 'purchase-order', 'produk', 'kas'],
    keywords: ['pembelian', 'kulakan', 'barang masuk', 'faktur beli'],
  },

  // 12. Purchase Order
  {
    slug: 'purchase-order',
    title: 'Purchase Order (PO / Pesanan Pembelian)',
    category: 'pembelian',
    categoryName: 'Pembelian & PO',
    order: 12,
    description: 'Membuat dokumen pesanan pembelian resmi sebelum barang dikirim oleh supplier.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '4 menit baca',
    summary:
      'Purchase Order (PO) berfungsi sebagai surat pesanan formal ke supplier. Membuat PO belum mengubah stok maupun kas hingga barang diterima.',
    sections: [
      {
        title: 'Siklus Status Purchase Order',
        paragraphs: [
          'PO memiliki 4 status tahapan:',
        ],
        listItems: [
          'DRAFT: Draf pesanan yang masih bisa diedit atau dihapus.',
          'ORDERED: Pesanan resmi yang telah dikirim ke supplier (bisa via WhatsApp/cetak).',
          'RECEIVED: Barang telah tiba dan diterima via Goods Receipt (stok bertambah).',
          'CANCELLED: Pesanan dibatalkan.',
        ],
      },
      {
        title: 'Penting: PO ≠ Realisasi Pembelian',
        paragraphs: [
          'Membuat PO status DRAFT atau ORDERED tidak memotong kas dan tidak menambah stok. Stok dan catatan keuangan baru terealisasi saat Anda memproses "Terima Barang" (Goods Receipt).',
        ],
      },
    ],
    steps: [
      {
        title: 'Buka Menu Purchase Order',
        description: 'Masuk ke menu Pembelian -> Purchase Order lalu tekan "+ Buat PO".',
      },
      {
        title: 'Pilih Supplier & Produk',
        description: 'Pilih supplier tujuan dan tambahkan item barang beserta estimasi jumlah yang ingin dipesan.',
      },
      {
        title: 'Simpan sebagai ORDERED',
        description: 'Simpan pesanan dengan status ORDERED agar nomor PO resmi diterbitkan.',
      },
      {
        title: 'Kirim ke Supplier',
        description: 'Gunakan tombol Kirim WhatsApp atau Cetak PO untuk meneruskan pesanan ke pihak supplier.',
      },
    ],
    callouts: [
      {
        type: 'warning',
        title: 'Penerimaan Utuh di v0.2.0',
        text: 'Pada Buku Warung v0.2.0, proses Terima Barang (Goods Receipt) mencatat penerimaan seluruh item pesanan sekaligus. Pastikan draf PO sudah sesuai sebelum finalisasi.',
      },
    ],
    limitations: [
      'Penerimaan parsial bertahap (partial receiving) dialokasikan pada pembaruan masa depan (G13.7).',
      'Penyesuaian selisih harga faktur saat barang tiba dialokasikan pada rilis berikutnya.',
    ],
    relatedSlugs: ['kirim-po', 'goods-receipt', 'supplier', 'pembelian'],
    keywords: ['purchase order', 'po', 'pesanan pembelian', 'draft', 'ordered', 'supplier'],
  },

  // 13. Mengirim & Mencetak PO
  {
    slug: 'kirim-po',
    title: 'Mengirim & Mencetak Purchase Order',
    category: 'pembelian',
    categoryName: 'Pembelian & PO',
    order: 13,
    description: 'Kirim format pesanan PO rapi ke WhatsApp supplier atau cetak ke printer thermal.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Bagikan lembar PO ke sales supplier melalui pesan WhatsApp berformat rapi, ekspor dokumen PDF, atau cetak ke printer thermal.',
    sections: [
      {
        title: 'Format WhatsApp Rapi & Otomatis',
        paragraphs: [
          'Aplikasi secara otomatis menyusun nomor PO, nama toko, daftar item pesanan, jumlah, estimasi harga, dan catatan pesanan menjadi teks siap kirim ke WhatsApp supplier.',
        ],
      },
    ],
    relatedSlugs: ['purchase-order', 'goods-receipt', 'printer'],
    keywords: ['kirim po', 'whatsapp po', 'cetak po', 'thermal po'],
  },

  // 14. Goods Receipt
  {
    slug: 'goods-receipt',
    title: 'Penerimaan Barang (Goods Receipt)',
    category: 'pembelian',
    categoryName: 'Pembelian & PO',
    order: 14,
    description: 'Proses finalisasi kedatangan barang dari PO menjadi stok aktif dan pencatatan keuangan.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Ketika barang pesanan tiba di toko, proses Goods Receipt untuk merealisasikan penambahan stok fisik dan pencatatan kas/hutang usaha.',
    sections: [
      {
        title: 'Langkah Realisasi Barang Masuk',
        paragraphs: [
          '1. Buka detail PO yang berstatus ORDERED.',
          '2. Tekan tombol "Terima Barang".',
          '3. Pilih metode pembayaran: Tunai (potong kas) atau Hutang Supplier (tempo).',
          '4. Konfirmasi: Status PO berubah menjadi RECEIVED, stok bertambah, dan transaksi pembelian tercatat resmi.',
        ],
      },
    ],
    relatedSlugs: ['purchase-order', 'pembelian', 'produk', 'kas'],
    keywords: ['goods receipt', 'terima barang', 'realisasi po', 'stok masuk'],
  },

  // 15. Buku Kas
  {
    slug: 'kas',
    title: 'Buku Kas & Biaya Operasional',
    category: 'keuangan',
    categoryName: 'Buku Kas & Laporan',
    order: 15,
    description: 'Mencatat uang masuk/keluar, biaya listrik, sewa, gaji karyawan, dan mutasi otomatis.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Buku Kas memisahkan uang usaha dengan uang pribadi. Saldo kas diperbarui otomatis dari transaksi penjualan dan pelunasan piutang.',
    sections: [
      {
        title: 'Pencatatan Beban Operasional',
        paragraphs: [
          'Gunakan fitur Kas Keluar untuk mencatat pengeluaran operasional toko seperti tagihan listrik, air, sewa tempat, bensin kurir, dan gaji pembantu warung.',
        ],
      },
    ],
    relatedSlugs: ['laporan', 'pos', 'pembelian'],
    keywords: ['buku kas', 'arus kas', 'biaya operasional', 'kas masuk', 'kas keluar'],
  },

  // 16. Laporan & PDF
  {
    slug: 'laporan',
    title: 'Laporan Keuangan & Ekspor PDF',
    category: 'keuangan',
    categoryName: 'Buku Kas & Laporan',
    order: 16,
    description: 'Menganalisis laba rugi sederhana, ringkasan omzet, modal barang (HPP), dan ekspor PDF.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '4 menit baca',
    summary:
      'Pantau kesehatan finansial toko melalui Laporan Laba Rugi Sederhana dengan filter periode fleksibel dan ekspor PDF resmi siap cetak.',
    sections: [
      {
        title: 'Struktur Laba Rugi Sederhana',
        paragraphs: [
          'Laporan keuangan Buku Warung menghitung:',
        ],
        listItems: [
          'Penjualan Bersih: Penjualan Bruto - Retur Penjualan',
          'HPP (Modal Terjual): Modal pokok dari barang-barang yang laku',
          'Laba Kotor: Penjualan Bersih - HPP',
          'Pengeluaran Operasional: Biaya listrik, gaji, dan beban toko',
          'Laba Bersih: Laba Kotor - Pengeluaran Operasional',
        ],
      },
      {
        title: 'Ekspor Dokumen PDF Vektor',
        paragraphs: [
          'Laporan dapat diekspor langsung menjadi file PDF standar A4 dengan header toko, tabel rapi, dan tanda tangan cetak.',
        ],
      },
    ],
    relatedSlugs: ['kas', 'pos', 'produk'],
    keywords: ['laporan', 'laba rugi', 'hpp', 'laba bersih', 'ekspor pdf', 'omzet'],
  },

  // 17. Katalog WhatsApp
  {
    slug: 'katalog-whatsapp',
    title: 'Katalog Produk WhatsApp',
    category: 'hardware',
    categoryName: 'Hardware & WhatsApp',
    order: 17,
    description: 'Bagikan daftar harga barang dan stok toko langsung ke WhatsApp pelanggan dalam sekali klik.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '2 menit baca',
    summary:
      'Pilih produk yang ingin dipromosikan, pilih opsi tampilkan/sembunyikan stok, lalu bagikan pesan katalog rapi ke kontak atau grup WhatsApp.',
    sections: [
      {
        title: 'Kemudahan Promosi Pelanggan',
        paragraphs: [
          'Katalog disusun otomatis dengan nama toko, daftar harga per unit, dan kontak pemesanan. Cocok untuk broadcast promo harian warung sembako atau apotek.',
        ],
      },
    ],
    relatedSlugs: ['produk', 'pos'],
    keywords: ['katalog whatsapp', 'share katalog', 'broadcast promo', 'daftar harga wa'],
  },

  // 18. Printer Thermal
  {
    slug: 'printer',
    title: 'Printer Struk Thermal (Bluetooth & USB)',
    category: 'hardware',
    categoryName: 'Hardware & WhatsApp',
    order: 18,
    description: 'Menghubungkan printer kasir Bluetooth dan USB OTG ukuran kertas 58mm maupun 80mm.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '4 menit baca',
    summary:
      'Cetak nota struk kasir profesional standar ESC/POS menggunakan printer thermal Bluetooth nirkabel maupun kabel USB OTG.',
    sections: [
      {
        title: 'Panduan Koneksi Printer Bluetooth',
        paragraphs: [
          '1. Pastikan Bluetooth di HP Android aktif dan printer thermal sudah dipasangkan (paired) di pengaturan Bluetooth HP.',
          '2. Buka Buku Warung -> Pengaturan -> Pengaturan Printer.',
          '3. Pilih jenis koneksi Bluetooth, pilih nama printer Anda, lalu tentukan lebar kertas (58mm atau 80mm).',
          '4. Tekan "Test Print" untuk memastikan kertas mencetak struk uji coba dengan sukses.',
        ],
      },
    ],
    relatedSlugs: ['pos', 'kirim-po', 'beranda'],
    keywords: ['printer', 'bluetooth', 'usb otg', 'thermal 58mm', 'thermal 80mm', 'cetak struk'],
  },

  // 19. Backup & Restore Google Sheets
  {
    slug: 'backup-restore',
    title: 'Backup & Restore Google Sheets',
    category: 'keamanan',
    categoryName: 'Keamanan & Lisensi',
    order: 19,
    description: 'Mencadangkan seluruh data transaksi ke Google Spreadsheet dan memulihkannya dengan aman.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '4 menit baca',
    summary:
      'Amankan data pembukuan Anda ke akun Google Drive pribadi. Data disimpan dalam 18 tab tabel spreadsheet kanonikal yang dapat dipulihkan kapan saja.',
    sections: [
      {
        title: 'Keamanan Data Milik Anda Sendiri',
        paragraphs: [
          'Cadangan data tersimpan langsung di Google Drive akun Google Anda sendiri, bukan di server pihak ketiga. Anda memiliki kendali penuh atas file spreadsheet cadangan.',
        ],
      },
      {
        title: 'Proses Pemulihan (Restore) Atomik',
        paragraphs: [
          'Fitur Restore memvalidasi integritas data dan checksum sebelum mengganti data database, menjamin tidak ada data rusak atau separuh tersimpan saat proses pemulihan.',
        ],
      },
    ],
    relatedSlugs: ['pin', 'lisensi', 'mulai'],
    keywords: ['backup', 'restore', 'google sheets', 'google drive', 'cadangkan data', 'pulihkan'],
  },

  // 20. PIN Keamanan
  {
    slug: 'pin',
    title: 'Keamanan & PIN Pemilik',
    category: 'keamanan',
    categoryName: 'Keamanan & Lisensi',
    order: 20,
    description: 'Mengamankan akses laporan keuangan dan kasir dengan 4 angka PIN terenkripsi.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '2 menit baca',
    summary:
      'Lindungi kerahasiaan omzet dan data pelanggan dari pihak yang tidak berhak dengan mengaktifkan proteksi PIN 4 angka pemilik.',
    sections: [
      {
        title: 'Enkripsi Salted SHA-256',
        paragraphs: [
          'PIN pemilik dienkripsi secara aman menggunakan algoritma Salted SHA-256 pada penyimpanan internal, sehingga tidak dapat dibaca sembarangan.',
        ],
      },
    ],
    relatedSlugs: ['mulai', 'lisensi', 'backup-restore'],
    keywords: ['pin', 'keamanan', 'kunci aplikasi', 'password kasir'],
  },

  // 21. Lisensi
  {
    slug: 'lisensi',
    title: 'Lisensi Resmi & Pergantian Perangkat',
    category: 'keamanan',
    categoryName: 'Keamanan & Lisensi',
    order: 21,
    description: 'Ketentuan lisensi resmi sekali beli seumur hidup dan prosedur jika ganti HP baru.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Lisensi Buku Warung berlaku 1 Lisensi = 1 Email Pemilik = 1 HP Aktif Selamanya. Tidak ada biaya langganan bulanan maupun tahunan.',
    sections: [
      {
        title: 'Prosedur Ganti HP Baru',
        paragraphs: [
          'Jika Anda mengganti perangkat HP atau melakukan factory reset, lisensi Anda tidak hangus. Hubungi Customer Service resmi SKMNetwork dengan melampirkan email pemilik terdaftar untuk verifikasi dan pelepasan binding perangkat lama.',
        ],
      },
    ],
    relatedSlugs: ['mulai', 'backup-restore', 'faq'],
    keywords: ['lisensi', 'aktivasi', 'ganti hp', 'device binding', 'lifetime'],
  },

  // 22. Jenis Usaha
  {
    slug: 'jenis-usaha',
    title: 'Kustomisasi 19 Jenis Usaha Adaptif',
    category: 'bantuan',
    categoryName: 'Bantuan & Kustomisasi',
    order: 22,
    description: 'Penjelasan adaptasi terminologi dan fitur untuk 19 kategori profil usaha UMKM Indonesia.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '4 menit baca',
    summary:
      'Satu aplikasi untuk berbagai bisnis. Buku Warung otomatis menyesuaikan sebutan produk, satuan, dan struk sesuai jenis usaha yang Anda pilih.',
    sections: [
      {
        title: 'Daftar Kategori Usaha yang Didukung',
        paragraphs: [
          '1. Retail: Warung Sembako, Minimarket, Toko Pakaian, Toko Elektronik, Toko Bangunan, Apotek, Konter Pulsa.',
          '2. Services: Bengkel Motor/Mobil, Cuci Kendaraan, Service Elektronik, Laundry, Barbershop/Salon, Penjahit, Fotocopy/Percetakan, Jasa Teknisi.',
          '3. Food & Beverage: Warung Makan/Resto, Kedai Kopi & Kafe, Toko Roti & Kue (Bakery).',
          '4. Production: Industri Rumahan & Kerajinan.',
        ],
      },
    ],
    relatedSlugs: ['mulai', 'produk', 'pos'],
    keywords: ['jenis usaha', 'adaptif', 'warung sembako', 'bengkel', 'apotek', 'toko bangunan', 'laundry'],
  },

  // 23. FAQ
  {
    slug: 'faq',
    title: 'Tanya Jawab Populer (FAQ)',
    category: 'bantuan',
    categoryName: 'Bantuan & Kustomisasi',
    order: 23,
    description: 'Jawaban atas pertanyaan yang paling sering diajukan mengenai penggunaan Buku Warung.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '4 menit baca',
    summary:
      'Kumpulan jawaban resmi seputar offline mode, printer, backup, lisensi, dan batasan operasional aplikasi Buku Warung v0.2.0.',
    sections: [
      {
        title: 'Apakah Buku Warung bisa dipakai tanpa kuota internet?',
        paragraphs: [
          'Ya, 100% bisa offline. Transaksi kasir, cetak nota, cek stok, dan laporan keuangan berjalan penuh tanpa koneksi internet. Internet hanya digunakan saat aktivasi awal atau backup Google Drive.',
        ],
      },
      {
        title: 'Apakah ada biaya perpanjangan langganan bulanan?',
        paragraphs: [
          'Tidak ada. Pembelian lisensi Buku Warung adalah sekali beli untuk selamanya (Lifetime One-Time Purchase).',
        ],
      },
      {
        title: 'Berapa kapasitas penyimpanan data produk dan transaksi?',
        paragraphs: [
          'Kapasitas tidak dibatasi oleh aplikasi (unlimited), melainkan hanya dibatasi oleh memori internal HP Android Anda.',
        ],
      },
    ],
    relatedSlugs: ['troubleshooting', 'mulai', 'lisensi', 'printer'],
    keywords: ['faq', 'tanya jawab', 'offline', 'langganan', 'kapasitas data'],
  },

  // 24. Troubleshooting
  {
    slug: 'troubleshooting',
    title: 'Panduan Mengatasi Kendala (Troubleshooting)',
    category: 'bantuan',
    categoryName: 'Bantuan & Kustomisasi',
    order: 24,
    description: 'Solusi cepat saat printer tidak konek, backup gagal, atau lupa nomor lisensi.',
    version: DOC_VERSION,
    updatedAt: DOC_LAST_UPDATED,
    readTime: '3 menit baca',
    summary:
      'Panduan perbaikan mandiri untuk mengatasi kendala umum operasional sehari-hari pada printer, backup Google Sheets, dan lisensi.',
    sections: [
      {
        title: 'Printer Thermal Tidak Mau Mencetak',
        paragraphs: [
          '1. Pastikan printer dalam kondisi menyala dan kertas terpasang dengan arah gulungan yang benar.',
          '2. Pastikan printer sudah dipasangkan (paired) di pengaturan Bluetooth Android.',
          '3. Buka Pengaturan -> Printer di Buku Warung, pilih nama printer Anda, lalu tekan Test Print.',
        ],
      },
      {
        title: 'Backup Google Sheets Gagal',
        paragraphs: [
          '1. Pastikan HP sedang terhubung ke internet yang stabil.',
          '2. Pastikan akun Google Drive memiliki ruang penyimpanan yang mencukupi.',
          '3. Lakukan login ulang akun Google jika masa otorisasi telah kedaluwarsa.',
        ],
      },
    ],
    relatedSlugs: ['faq', 'printer', 'backup-restore', 'lisensi'],
    keywords: ['troubleshooting', 'kendala', 'printer error', 'backup gagal', 'bantuan'],
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
      article.categoryName.toLowerCase().includes(q) ||
      article.keywords.some((kw) => kw.toLowerCase().includes(q))
    );
  });
}
