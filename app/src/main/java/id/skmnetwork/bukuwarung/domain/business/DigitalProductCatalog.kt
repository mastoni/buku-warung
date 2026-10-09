package id.skmnetwork.bukuwarung.domain.business

/**
 * Release 3 — Standard Digital Product Denomination
 * Represents a standard digital item for PPOB transactions.
 */
data class DigitalProductItem(
    val code: String,
    val providerId: String,
    val category: String, // "PULSA", "DATA", "PLN", "EWALLET"
    val name: String,
    val nominal: Long,
    val purchasePrice: Long,
    val sellingPrice: Long,
    val adminFee: Long = 0L,
    val description: String = ""
)

object DigitalProductCatalog {

    val PRESET_ITEMS = listOf(
        // Telkomsel Pulsa
        DigitalProductItem("TSEL5", "TELKOMSEL", "PULSA", "Telkomsel 5.000", 5_000, 5_300, 7_000, 0, "Masa aktif 7 hari"),
        DigitalProductItem("TSEL10", "TELKOMSEL", "PULSA", "Telkomsel 10.000", 10_000, 10_300, 12_000, 0, "Masa aktif 15 hari"),
        DigitalProductItem("TSEL20", "TELKOMSEL", "PULSA", "Telkomsel 20.000", 20_000, 20_150, 22_000, 0, "Masa aktif 30 hari"),
        DigitalProductItem("TSEL25", "TELKOMSEL", "PULSA", "Telkomsel 25.000", 25_000, 25_100, 27_000, 0, "Masa aktif 30 hari"),
        DigitalProductItem("TSEL50", "TELKOMSEL", "PULSA", "Telkomsel 50.000", 50_000, 49_800, 52_000, 0, "Masa aktif 45 hari"),
        DigitalProductItem("TSEL100", "TELKOMSEL", "PULSA", "Telkomsel 100.000", 100_000, 98_500, 102_000, 0, "Masa aktif 60 hari"),

        // Indosat Pulsa
        DigitalProductItem("ISAT5", "INDOSAT", "PULSA", "Indosat 5.000", 5_000, 5_350, 7_000, 0, "Masa aktif 7 hari"),
        DigitalProductItem("ISAT10", "INDOSAT", "PULSA", "Indosat 10.000", 10_000, 10_350, 12_000, 0, "Masa aktif 15 hari"),
        DigitalProductItem("ISAT25", "INDOSAT", "PULSA", "Indosat 25.000", 25_000, 25_050, 27_000, 0, "Masa aktif 30 hari"),
        DigitalProductItem("ISAT50", "INDOSAT", "PULSA", "Indosat 50.000", 50_000, 49_700, 52_000, 0, "Masa aktif 45 hari"),

        // XL / Axis Pulsa
        DigitalProductItem("XL5", "XL", "PULSA", "XL 5.000", 5_000, 5_400, 7_000, 0, "Masa aktif 7 hari"),
        DigitalProductItem("XL10", "XL", "PULSA", "XL 10.000", 10_000, 10_350, 12_000, 0, "Masa aktif 15 hari"),
        DigitalProductItem("XL25", "XL", "PULSA", "XL 25.000", 25_000, 25_100, 27_000, 0, "Masa aktif 30 hari"),
        DigitalProductItem("XL50", "XL", "PULSA", "XL 50.000", 50_000, 49_900, 52_000, 0, "Masa aktif 45 hari"),

        // Tri Pulsa
        DigitalProductItem("TRI5", "TRI", "PULSA", "Tri 5.000", 5_000, 5_200, 7_000, 0, "Masa aktif 7 hari"),
        DigitalProductItem("TRI10", "TRI", "PULSA", "Tri 10.000", 10_000, 10_150, 12_000, 0, "Masa aktif 15 hari"),
        DigitalProductItem("TRI25", "TRI", "PULSA", "Tri 25.000", 25_000, 24_900, 27_000, 0, "Masa aktif 30 hari"),
        DigitalProductItem("TRI50", "TRI", "PULSA", "Tri 50.000", 50_000, 49_500, 52_000, 0, "Masa aktif 45 hari"),

        // PLN Token
        DigitalProductItem("PLN20", "PLN", "PLN", "Token Listrik PLN 20.000", 20_000, 20_000, 22_500, 2_500, "Token 20 digit kWh"),
        DigitalProductItem("PLN50", "PLN", "PLN", "Token Listrik PLN 50.000", 50_000, 50_000, 52_500, 2_500, "Token 20 digit kWh"),
        DigitalProductItem("PLN100", "PLN", "PLN", "Token Listrik PLN 100.000", 100_000, 100_000, 102_500, 2_500, "Token 20 digit kWh"),
        DigitalProductItem("PLN200", "PLN", "PLN", "Token Listrik PLN 200.000", 200_000, 200_000, 202_500, 2_500, "Token 20 digit kWh")
    )

    fun detectProvider(destination: String): String? {
        val clean = destination.trim().replace("+62", "0").replace("-", "").replace(" ", "")
        if (clean.length < 4) return null
        val prefix = clean.take(4)
        return when {
            prefix in listOf("0811", "0812", "0813", "0821", "0822", "0823", "0851", "0852", "0853") -> "TELKOMSEL"
            prefix in listOf("0814", "0815", "0816", "0855", "0856", "0857", "0858") -> "INDOSAT"
            prefix in listOf("0817", "0818", "0819", "0859", "0877", "0878") -> "XL"
            prefix in listOf("0831", "0832", "0833", "0838") -> "AXIS"
            prefix in listOf("0895", "0896", "0897", "0898", "0899") -> "TRI"
            prefix in listOf("0881", "0882", "0883", "0884", "0885", "0886", "0887", "0888", "0889") -> "SMARTFREN"
            clean.length in 11..12 && (clean.startsWith("1") || clean.startsWith("2") || clean.startsWith("3") || clean.startsWith("4") || clean.startsWith("5")) -> "PLN"
            else -> null
        }
    }
}
