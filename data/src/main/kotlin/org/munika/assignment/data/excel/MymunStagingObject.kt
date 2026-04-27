package org.munika.assignment.data.excel

/**
 * Base type for parsed MyMUN update staging records.
 *
 * `toString()` is intentionally concise for debugger output and is not a stable user-facing string.
 */
abstract class MymunStagingObject {
    final override fun toString(): String = displayString()

    protected abstract fun displayString(): String
}
