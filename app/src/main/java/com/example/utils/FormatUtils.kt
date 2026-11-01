package com.example.utils

object FormatUtils {
    /**
     * Formats table numbers/names for display so that prefixes like "masa-", "masa ", or "Masa "
     * are normalized and don't produce duplicate "Masa masa-31" titles.
     */
    fun formatMasaTitle(masaNo: String): String {
        var clean = masaNo.trim()
        if (clean.isBlank()) return "Masa"

        while (true) {
            val lower = clean.lowercase()
            if (lower.startsWith("masa")) {
                clean = clean.substring(4).trimStart('-', ' ', ':', '_')
            } else {
                break
            }
        }

        return if (clean.isEmpty()) "Masa" else "Masa $clean"
    }
}
