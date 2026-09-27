package id.skmnetwork.bukuwarung.util

/**
 * Gate H.2 - canonical quantity rendering for UI labels.
 *
 * POS previously rendered cart quantities with `quantity.toInt()`, so a 2.5 kg line was labelled
 * "2 x" while the money beside it was calculated for 2.5 kg. That silently rounded a quantity the
 * merchant is actually selling and invited a dispute at the counter.
 *
 * A whole quantity prints as a whole number ("5"), a fractional one keeps its decimals without a
 * trailing zero ("2.5", "0.25"). This is display only: it never feeds a money or stock
 * calculation, which remain in [id.skmnetwork.bukuwarung.domain.money.MoneyCalculator] and the
 * database `REAL` columns respectively.
 */
fun formatQuantityValue(quantity: Double): String {
    if (quantity.isNaN() || quantity.isInfinite()) return "0"
    if (quantity == 0.0) return "0"
    if (quantity % 1.0 == 0.0) return quantity.toLong().toString()
    val raw = quantity.toString()
    return if (raw.contains('.')) raw.trimEnd('0').trimEnd('.') else raw
}
