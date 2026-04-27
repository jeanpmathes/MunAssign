package org.munika.assignment.data.excel

internal fun Int.toExcelColumnName(): String {
    require(this >= 1) { "Excel column numbers are 1-based." }

    var remaining = this
    val name = StringBuilder()
    while (remaining > 0) {
        remaining--
        name.append(('A'.code + (remaining % 26)).toChar())
        remaining /= 26
    }
    return name.reverse().toString()
}
