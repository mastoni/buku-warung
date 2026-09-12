# Buku Warung release rules.

# Room Database Keep Rules
-keep class id.skmnetwork.bukuwarung.data.local.entity.** { *; }
-keep class id.skmnetwork.bukuwarung.data.local.dao.** { *; }
-keep class id.skmnetwork.bukuwarung.data.local.database.** { *; }

# DataStore & Preferences
-keep class id.skmnetwork.bukuwarung.data.preferences.** { *; }

# ML Kit Barcode
-keep class com.google.mlkit.vision.barcode.** { *; }
