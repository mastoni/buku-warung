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

/**
 * Canonical parsing of a merchant-typed quantity, the counterpart to [formatQuantityValue].
 *
 * Indonesian keyboards and receipts write "1,25", while the internal representation is a
 * Double, so a comma is accepted as the decimal separator and normalised to a dot before
 * parsing. Both spellings therefore produce the identical number, and "1.25" is never truncated
 * to "1" on the way in.
 *
 * Returns null for anything that is not a number, so a caller can reject it rather than
 * silently storing a zero. The caller still owns the business rules - this function only
 * converts text; it never clamps, rounds or invents a value.
 */
fun parseQuantityInput(text: String): Double? {
    val normalised = text.trim().replace(',', '.')
    if (normalised.isEmpty()) return null
    val parsed = normalised.toDoubleOrNull() ?: return null
    // "NaN" and "Infinity" are accepted by Double parsing, but they are not quantities: letting one
    // through would poison every total it ever reaches. Reject them here so no caller has to
    // remember to check.
    if (parsed.isNaN() || parsed.isInfinite()) return null
    return parsed
}

