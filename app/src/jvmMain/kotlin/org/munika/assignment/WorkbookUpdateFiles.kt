package org.munika.assignment

import java.io.File

internal sealed interface WorkbookUpdateFileSelection {
    data class Accepted(val file: File) : WorkbookUpdateFileSelection

    data class Rejected(val message: String) : WorkbookUpdateFileSelection
}

internal fun selectWorkbookUpdateFile(
    files: List<File>,
    hasSelectedConference: Boolean,
    loading: Boolean,
): WorkbookUpdateFileSelection {
    if (!hasSelectedConference) {
        return WorkbookUpdateFileSelection.Rejected("Select or create a conference before loading an update.")
    }
    if (loading) {
        return WorkbookUpdateFileSelection.Rejected("Wait for the current action to finish before loading an update.")
    }
    if (files.isEmpty()) {
        return WorkbookUpdateFileSelection.Rejected(
            "MunAssign could not read a file from this drop. Try dragging one Excel file from File Explorer.",
        )
    }
    if (files.size > 1) {
        return WorkbookUpdateFileSelection.Rejected("Drop only one Excel file.")
    }
    val file = files.single()
    if (!file.isFile) {
        return WorkbookUpdateFileSelection.Rejected("Drop a regular Excel file.")
    }
    if (!file.hasWorkbookUpdateExtension()) {
        return WorkbookUpdateFileSelection.Rejected("Drop an Excel file with .xlsx, .xls, or .xlsm extension.")
    }
    return WorkbookUpdateFileSelection.Accepted(file)
}

internal fun File.hasWorkbookUpdateExtension(): Boolean =
    name.hasWorkbookUpdateExtension()

internal fun String.hasWorkbookUpdateExtension(): Boolean =
    listOf(".xlsx", ".xls", ".xlsm").any { extension -> endsWith(extension, ignoreCase = true) }
