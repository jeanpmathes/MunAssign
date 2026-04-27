package org.munika.assignment.data.excel

internal fun String.shortForDebugger(maxLength: Int = 48): String {
    val compact = trim().replace(Regex("\\s+"), " ")
    return if (compact.length <= maxLength) compact else compact.take(maxLength - 1) + "..."
}
