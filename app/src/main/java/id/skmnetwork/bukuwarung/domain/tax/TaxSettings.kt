package id.skmnetwork.bukuwarung.domain.tax

import java.math.RoundingMode

data class TaxSettings(
    val enabled: Boolean,
    val rate: Double,
    val priceMode: TaxPriceMode,
    val roundingMode: RoundingMode
)
