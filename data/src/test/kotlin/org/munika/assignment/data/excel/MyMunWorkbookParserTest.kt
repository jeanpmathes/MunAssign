package org.munika.assignment.data.excel

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.apache.poi.xssf.usermodel.XSSFWorkbook

class MyMunWorkbookParserTest {
    private val parser = MyMunWorkbookParser()

    @Test
    fun `parses sample workbook into staging snapshot`() {
        val resource = requireNotNull(javaClass.classLoader.getResource("export.xlsx")) {
            "Expected export.xlsx test workbook fixture"
        }

        val snapshot = parser.parse(Paths.get(resource.toURI()))

        assertEquals(6, snapshot.delegates.size)
        assertEquals(4, snapshot.countryPositions.size)
        assertEquals(1, snapshot.delegations.size)
        assertEquals(9, snapshot.questionColumns.size)
        assertEquals(6 * 9, snapshot.questionAnswers.size)
        assertEquals(15, snapshot.preferenceCandidates.size)
        assertEquals(3, snapshot.confirmedAssignmentCandidates.size)
        assertTrue(snapshot.issues.none { it.severity == MymunValidationSeverity.Error }, snapshot.issues.joinToString("\n") { it.message })

        val firstDelegate = snapshot.delegates.first()
        assertTrue(firstDelegate.firstName.isNotBlank())
        assertTrue(firstDelegate.lastName.isNotBlank())
        assertEquals(firstDelegate.email.lowercase(), firstDelegate.normalizedEmail)
        assertNotNull(firstDelegate.submittedAt)
        assertNotNull(firstDelegate.dateOfBirth)

        assertTrue(snapshot.preferenceCandidates.all { it.resolvedCountryRowNumber != null })
        assertTrue(snapshot.confirmedAssignmentCandidates.all { it.slotIndex >= 1 })

        assertEquals(
            listOf(
                MymunCountryPositionRow(2, "G20", "Germany", "Some Delegation", "Delegate A"),
                MymunCountryPositionRow(3, "G20", "France", null, null),
                MymunCountryPositionRow(4, "UNSC", "USA", null, null),
                MymunCountryPositionRow(5, "UNSC", "Russia", "Some Delegation", "Delegate B, Delegate C"),
            ),
            snapshot.countryPositions,
        )

        assertEquals("Delegate A", firstDelegate.displayName)
        assertEquals("a@mail.de", firstDelegate.email)
        assertEquals("Assigned", firstDelegate.applicationStatus)
        assertTrue(firstDelegate.feePaid)
        assertTrue(firstDelegate.isHeadDelegate)
        assertEquals("Some Delegation", firstDelegate.delegationName)
        assertEquals("Germany", firstDelegate.mymunAssignedPositionName)
        assertEquals("G20", firstDelegate.mymunAssignedCommitteeName)

        assertEquals(
            listOf("Delegate A", "Delegate B", "Delegate C"),
            snapshot.confirmedAssignmentCandidates.map { it.delegateDisplayName },
        )
        assertEquals(
            listOf("G20 (Germany)", "UNSC (Russia)", "UNSC (Russia)"),
            snapshot.confirmedAssignmentCandidates.map { "${it.committeeName} (${it.positionName})" },
        )

        assertEquals(
            listOf("G20 (Germany)", "G20 (France)", "UNSC (Russia)"),
            snapshot.preferenceCandidates
                .filter { it.delegateRowNumber == firstDelegate.rowNumber }
                .sortedBy { it.rank }
                .map { it.rawPreferenceText },
        )

        assertEquals(
            "Question: Are you part of any delegation?",
            snapshot.questionColumns.first().questionText,
        )
        assertEquals(
            "Sure",
            snapshot.questionAnswers.first {
                it.delegateRowNumber == firstDelegate.rowNumber &&
                    it.questionColumn.questionText == "Question: Are you part of any delegation?"
            }.answer,
        )

        val delegation = snapshot.delegations.single()
        assertEquals("Some Delegation", delegation.name)
        assertEquals("Germany", delegation.country)
        assertEquals(3, delegation.currentDelegates)
        assertEquals(7, delegation.expectedDelegates)
    }

    @Test
    fun `invalid date cells become blocking validation issues`() {
        val workbookBytes = ByteArrayOutputStream().use { output ->
            XSSFWorkbook().use { workbook ->
                val countries = workbook.createSheet("Countries")
                countries.createRow(0).apply {
                    createCell(0).setCellValue("Committee Name")
                    createCell(1).setCellValue("Member Name")
                    createCell(2).setCellValue("Delegation Name")
                    createCell(3).setCellValue("Delegates")
                }
                countries.createRow(1).apply {
                    createCell(0).setCellValue("GA")
                    createCell(1).setCellValue("France")
                }

                val delegates = workbook.createSheet("Delegates")
                delegates.createRow(0).apply {
                    createCell(0).setCellValue("First Name")
                    createCell(1).setCellValue("Last Name")
                    createCell(2).setCellValue("Email")
                    createCell(3).setCellValue("Fee Paid")
                    createCell(4).setCellValue("Date of Birth")
                }
                delegates.createRow(1).apply {
                    createCell(0).setCellValue("Alex")
                    createCell(1).setCellValue("Kim")
                    createCell(2).setCellValue("alex@example.test")
                    createCell(3).setCellValue("yes")
                    createCell(4).setCellValue("not-a-date")
                }

                workbook.write(output)
            }
            output.toByteArray()
        }

        val snapshot = parser.parse(ByteArrayInputStream(workbookBytes), "invalid-date.xlsx")

        val issue = snapshot.issues.single { it.code == "invalid_date" }
        assertEquals(MymunValidationSeverity.Error, issue.severity)
        assertEquals("Delegates", issue.location?.sheetName)
        assertEquals(2, issue.location?.rowNumber)
        assertEquals("Date of Birth", issue.location?.columnName)
    }
}
