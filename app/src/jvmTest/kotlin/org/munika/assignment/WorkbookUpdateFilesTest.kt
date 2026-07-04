package org.munika.assignment

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WorkbookUpdateFilesTest {
    @Test
    fun `accepts one regular Excel file`() {
        val excelFile = Files.createTempFile("munassign-update", ".xlsx").toFile()

        val selection = selectWorkbookUpdateFile(
            files = listOf(excelFile),
            hasSelectedConference = true,
            loading = false,
        )

        assertEquals(excelFile, assertIs<WorkbookUpdateFileSelection.Accepted>(selection).file)
    }

    @Test
    fun `rejects unsupported Excel file extension`() {
        val textFile = Files.createTempFile("munassign-update", ".txt").toFile()

        val selection = selectWorkbookUpdateFile(
            files = listOf(textFile),
            hasSelectedConference = true,
            loading = false,
        )

        assertEquals(
            "Drop an Excel file with .xlsx, .xls, or .xlsm extension.",
            assertIs<WorkbookUpdateFileSelection.Rejected>(selection).message,
        )
    }

    @Test
    fun `rejects multiple dropped files`() {
        val first = Files.createTempFile("munassign-update", ".xlsx").toFile()
        val second = Files.createTempFile("munassign-update", ".xlsm").toFile()

        val selection = selectWorkbookUpdateFile(
            files = listOf(first, second),
            hasSelectedConference = true,
            loading = false,
        )

        assertEquals("Drop only one Excel file.", assertIs<WorkbookUpdateFileSelection.Rejected>(selection).message)
    }

    @Test
    fun `rejects missing file payload with specific message`() {
        val selection = selectWorkbookUpdateFile(
            files = emptyList(),
            hasSelectedConference = true,
            loading = false,
        )

        assertEquals(
            "MunAssign could not read a file from this drop. Try dragging one Excel file from File Explorer.",
            assertIs<WorkbookUpdateFileSelection.Rejected>(selection).message,
        )
    }

    @Test
    fun `rejects Excel file selection while no conference is selected`() {
        val excelFile = Files.createTempFile("munassign-update", ".xlsx").toFile()

        val selection = selectWorkbookUpdateFile(
            files = listOf(excelFile),
            hasSelectedConference = false,
            loading = false,
        )

        assertEquals(
            "Select or create a conference before loading an update.",
            assertIs<WorkbookUpdateFileSelection.Rejected>(selection).message,
        )
    }
}
