package com.vegamstories.app.core.util

/** Short count for small labels: 999 → "999", 1540 → "1.5K", 12_400 → "12K", 2_300_000 → "2.3M". */
fun Int.toCompactCount(): String = when {
    this < 1_000 -> toString()
    this < 1_000_000 -> compact(this, 1_000, "K")
    else -> compact(this, 1_000_000, "M")
}

private fun compact(value: Int, unit: Int, suffix: String): String {
    val whole = value / unit
    // One decimal only below 10 ("1.5K"), and never a ".0" ("2K", not "2.0K"). Rounds down,
    // so 999_999 shows "999K" instead of a confusing "1000K".
    val tenth = (value % unit) / (unit / 10)
    return if (whole < 10 && tenth > 0) "$whole.$tenth$suffix" else "$whole$suffix"
}
