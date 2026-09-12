package id.skmnetwork.bukuwarung.util

import java.text.NumberFormat
import java.util.Locale

fun formatRupiah(amount: Long): String {
    val locale = Locale.Builder().setLanguage("id").setRegion("ID").build()
    val formatter = NumberFormat.getInstance(locale)
    return "Rp ${formatter.format(amount)}"
}
