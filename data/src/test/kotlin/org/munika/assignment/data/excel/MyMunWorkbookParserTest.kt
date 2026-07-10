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
    fun `countries delegation column is optional`() {
        val workbookBytes = ByteArrayOutputStream().use { output ->
            XSSFWorkbook().use { workbook ->
                val countries = workbook.createSheet("Countries")
                countries.createRow(0).apply {
                    createCell(0).setCellValue("Committee Name")
                    createCell(1).setCellValue("Member Name")
                    createCell(2).setCellValue("Delegates")
                }
                countries.createRow(1).apply {
                    createCell(0).setCellValue("GA")
                    createCell(1).setCellValue("France")
                    createCell(2).setCellValue("Alex Kim")
                }

                val delegates = workbook.createSheet("Delegates")
                delegates.createRow(0).apply {
                    createCell(0).setCellValue("First Name")
                    createCell(1).setCellValue("Last Name")
                    createCell(2).setCellValue("Email")
                    createCell(3).setCellValue("Fee Paid")
                }
                delegates.createRow(1).apply {
                    createCell(0).setCellValue("Alex")
                    createCell(1).setCellValue("Kim")
                    createCell(2).setCellValue("alex@example.test")
                    createCell(3).setCellValue("yes")
                }

                workbook.write(output)
            }
            output.toByteArray()
        }

        val snapshot = parser.parse(ByteArrayInputStream(workbookBytes), "countries-without-delegation.xlsx")

        assertTrue(snapshot.issues.none { it.code == "missing_column" }, snapshot.issues.joinToString("\n") { it.message })
        assertEquals(
            MymunCountryPositionRow(2, "GA", "France", null, "Alex Kim"),
            snapshot.countryPositions.single(),
        )
        assertEquals("Alex Kim", snapshot.confirmedAssignmentCandidates.single().delegateDisplayName)
    }

    @Test
    fun `preferences resolve when committee names contain parenthesized qualifiers`() {
        val workbookBytes = ByteArrayOutputStream().use { output ->
            XSSFWorkbook().use { workbook ->
                val countries = workbook.createSheet("Countries")
                countries.createRow(0).apply {
                    createCell(0).setCellValue("Committee Name")
                    createCell(1).setCellValue("Member Name")
                    createCell(2).setCellValue("Delegates")
                }
                countries.createRow(1).apply {
                    createCell(0).setCellValue("International Labour Organization (High School)")
                    createCell(1).setCellValue("Netherlands")
                }
                countries.createRow(2).apply {
                    createCell(0).setCellValue("IAEA (Highschool)")
                    createCell(1).setCellValue("Saudi Arabia")
                }
                countries.createRow(3).apply {
                    createCell(0).setCellValue("US Constitutional Convention of 1787")
                    createCell(1).setCellValue("Benjamin Franklin (Pennsylvania)")
                }
                countries.createRow(4).apply {
                    createCell(0).setCellValue("US Constitutional Convention of 1787")
                    createCell(1).setCellValue("Alexander Hamilton (New York)")
                }
                countries.createRow(5).apply {
                    createCell(0).setCellValue("WHO")
                    createCell(1).setCellValue("Union of Soviet Socialist Republics (USSR)")
                }

                val delegates = workbook.createSheet("Delegates")
                delegates.createRow(0).apply {
                    createCell(0).setCellValue("First Name")
                    createCell(1).setCellValue("Last Name")
                    createCell(2).setCellValue("Email")
                    createCell(3).setCellValue("Fee Paid")
                    createCell(4).setCellValue("Preference 1")
                    createCell(5).setCellValue("Preference 2")
                    createCell(6).setCellValue("Preference 3")
                    createCell(7).setCellValue("Preference 4")
                }
                delegates.createRow(1).apply {
                    createCell(0).setCellValue("Alex")
                    createCell(1).setCellValue("Kim")
                    createCell(2).setCellValue("alex@example.test")
                    createCell(3).setCellValue("yes")
                    createCell(4).setCellValue("International Labour Organization (High School) (Netherlands)")
                    createCell(5).setCellValue("IAEA (Highschool) (Saudi Arabia)")
                    createCell(6).setCellValue("US Constitutional Convention of 1787 (Benjamin Franklin (Pennsylvania))")
                    createCell(7).setCellValue("WHO (Union of Soviet Socialist Republics (USSR))")
                }

                workbook.write(output)
            }
            output.toByteArray()
        }

        val snapshot = parser.parse(ByteArrayInputStream(workbookBytes), "parenthesized-committee-preferences.xlsx")

        assertTrue(snapshot.issues.none { it.code == "unresolved_preference" }, snapshot.issues.joinToString("\n") { it.message })
        assertEquals(listOf(2, 3, 4, 6), snapshot.preferenceCandidates.sortedBy { it.rank }.map { it.resolvedCountryRowNumber })
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

    @Test
    fun `datetime-suffixed date cells parse using their date component`() {
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
                    createCell(5).setCellValue("Submitted At")
                    createCell(6).setCellValue("Created At")
                }
                delegates.createRow(1).apply {
                    createCell(0).setCellValue("Alex")
                    createCell(1).setCellValue("Kim")
                    createCell(2).setCellValue("alex@example.test")
                    createCell(3).setCellValue("yes")
                    createCell(4).setCellValue("2006-01-02T00:00:00")
                    createCell(5).setCellValue("2025-12-05 14:30:00")
                    createCell(6).setCellValue("2025-12-05 14:30")
                }

                workbook.write(output)
            }
            output.toByteArray()
        }

        val snapshot = parser.parse(ByteArrayInputStream(workbookBytes), "datetime-dates.xlsx")

        assertTrue(snapshot.issues.none { it.severity == MymunValidationSeverity.Error }, snapshot.issues.joinToString("\n") { it.message })
        val delegate = snapshot.delegates.single()
        assertEquals(java.time.LocalDate.of(2006, 1, 2), delegate.dateOfBirth)
        assertEquals(java.time.LocalDate.of(2025, 12, 5), delegate.submittedAt)
        assertEquals(java.time.LocalDate.of(2025, 12, 5), delegate.createdAt)
    }

    @Test
    fun `duplicate question columns become a blocking validation issue`() {
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
                    createCell(4).setCellValue("Question: Why this committee?")
                    createCell(5).setCellValue("Question:  Why this committee?  ")
                }
                delegates.createRow(1).apply {
                    createCell(0).setCellValue("Alex")
                    createCell(1).setCellValue("Kim")
                    createCell(2).setCellValue("alex@example.test")
                    createCell(3).setCellValue("yes")
                    createCell(4).setCellValue("Because.")
                    createCell(5).setCellValue("Because.")
                }

                workbook.write(output)
            }
            output.toByteArray()
        }

        val snapshot = parser.parse(ByteArrayInputStream(workbookBytes), "duplicate-question.xlsx")

        val issue = snapshot.issues.single { it.code == "duplicate_question_column" }
        assertEquals(MymunValidationSeverity.Error, issue.severity)
        assertEquals("Delegates", issue.location?.sheetName)
        assertTrue("Why this committee?" in issue.message)
    }

    @Test
    fun `head delegate column only recognizes the exact marker value`() {
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
                    createCell(4).setCellValue("Head Delegate/Faculty Advisor")
                }
                delegates.createRow(1).apply {
                    createCell(0).setCellValue("Alex")
                    createCell(1).setCellValue("Kim")
                    createCell(2).setCellValue("alex@example.test")
                    createCell(3).setCellValue("yes")
                    createCell(4).setCellValue("Faculty Advisor")
                }
                delegates.createRow(2).apply {
                    createCell(0).setCellValue("Sam")
                    createCell(1).setCellValue("Lee")
                    createCell(2).setCellValue("sam@example.test")
                    createCell(3).setCellValue("yes")
                    createCell(4).setCellValue("Head Delegate")
                }

                workbook.write(output)
            }
            output.toByteArray()
        }

        val snapshot = parser.parse(ByteArrayInputStream(workbookBytes), "head-delegate.xlsx")

        assertTrue(snapshot.issues.none { it.severity == MymunValidationSeverity.Error }, snapshot.issues.joinToString("\n") { it.message })
        val facultyAdvisor = snapshot.delegates.single { it.firstName == "Alex" }
        val headDelegate = snapshot.delegates.single { it.firstName == "Sam" }
        assertTrue(!facultyAdvisor.isHeadDelegate)
        assertTrue(headDelegate.isHeadDelegate)
    }
}
