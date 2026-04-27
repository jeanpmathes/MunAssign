package org.munika.assignment.data.excel

abstract class MymunStagingObject {
    final override fun toString(): String = displayString()

    protected abstract fun displayString(): String
}
