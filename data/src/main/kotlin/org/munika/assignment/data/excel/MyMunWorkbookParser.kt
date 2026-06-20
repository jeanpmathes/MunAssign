package org.munika.assignment.data.excel

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Parses MyMUN Excel workbooks into staging data without writing to the database.
 */
class MyMunWorkbookParser {
    private val formatter = DataFormatter(Locale.ROOT)

    /** Parses the workbook at [path] into staging data without persisting it. */
    fun parse(path: Path): MymunWorkbookSnapshot =
        Files.newInputStream(path).use { input -> parse(input, path.fileName.toString()) }

    /** Parses [input] into staging data and reports validation issues in the returned snapshot. */
    fun parse(input: InputStream, workbookName: String? = null): MymunWorkbookSnapshot {
        val issues = mutableListOf<MymunValidationIssue>()

        WorkbookFactory.create(input).use { workbook ->
            val delegatesSheet = workbook.getSheet(DelegatesSheetName)
            val countriesSheet = workbook.getSheet(CountriesSheetName)
            val delegationsSheet = workbook.getSheet(DelegationsSheetName)

            if (delegatesSheet == null) {
                issues += missingSheet(DelegatesSheetName)
            }
            if (countriesSheet == null) {
                issues += missingSheet(CountriesSheetName)
            }

            val countries = countriesSheet?.let { parseCountries(it, issues) }.orEmpty()
            val countryLookup = countries.associateBy {
                PositionKey(normalizeLookup(it.committeeName), normalizeLookup(it.memberName))
            }

            val delegateParse = delegatesSheet?.let { parseDelegates(it, countryLookup, issues) } ?: DelegateParseResult()
            val delegations = delegationsSheet?.let { parseDelegations(it, issues) }.orEmpty()

            issues += duplicateDelegateIssues(delegateParse.delegates)

            return MymunWorkbookSnapshot(
                workbookName = workbookName,
                delegates = delegateParse.delegates,
                countryPositions = countries,
                delegations = delegations,
                questionColumns = delegateParse.questionColumns,
                questionAnswers = delegateParse.questionAnswers,
                preferenceCandidates = delegateParse.preferenceCandidates,
                confirmedAssignmentCandidates = parseConfirmedAssignments(countries),
                issues = issues,
            )
        }
    }

    /** Reads each workbook sheet's normalized header row for diagnostics without parsing rows. */
    fun inspectHeaders(path: Path): Map<String, List<String>> =
        Files.newInputStream(path).use { input ->
            WorkbookFactory.create(input).use { workbook ->
                workbook.sheetIterator().asSequence().associate { sheet ->
                    sheet.sheetName to headerMap(sheet).orderedHeaders
                }
            }
        }

    private fun parseCountries(
        sheet: Sheet,
        issues: MutableList<MymunValidationIssue>,
    ): List<MymunCountryPositionRow> {
        val headers = headerMap(sheet)
        val requiredHeaders = listOf(CountryCommittee, CountryMember, CountryDelegation, CountryDelegates)
        issues += missingColumnIssues(sheet.sheetName, headers, requiredHeaders)

        return dataRows(sheet).mapNotNull { row ->
            val committeeName = row.text(headers, CountryCommittee).trimToNull()
            val memberName = row.text(headers, CountryMember).trimToNull()
            if (committeeName == null && memberName == null) return@mapNotNull null

            if (committeeName == null) {
                issues += blankRequiredCell(sheet.sheetName, row, CountryCommittee, headers)
                return@mapNotNull null
            }
            if (memberName == null) {
                issues += blankRequiredCell(sheet.sheetName, row, CountryMember, headers)
                return@mapNotNull null
            }

            MymunCountryPositionRow(
                rowNumber = row.excelRowNumber,
                committeeName = committeeName,
                memberName = memberName,
                delegationName = row.text(headers, CountryDelegation).trimToNull(),
                delegatesRaw = row.text(headers, CountryDelegates).trimToNull(),
            )
        }.toList()
    }

    private fun parseDelegates(
        sheet: Sheet,
        countryLookup: Map<PositionKey, MymunCountryPositionRow>,
        issues: MutableList<MymunValidationIssue>,
    ): DelegateParseResult {
        val headers = headerMap(sheet)
        val requiredHeaders = listOf(DelegateFirstName, DelegateLastName, DelegateEmail, DelegateFeePaid)
        issues += missingColumnIssues(sheet.sheetName, headers, requiredHeaders)

        val questionColumns = headers.orderedHeaders.mapIndexedNotNull { index, header ->
            if (!header.startsWith(QuestionPrefix, ignoreCase = true)) return@mapIndexedNotNull null
            MymunQuestionColumn(
                sheetName = sheet.sheetName,
                columnNumber = headers.columnIndex(header) + 1,
                displayOrder = index,
                questionText = header,
            )
        }
        val preferenceColumns = headers.orderedHeaders.mapNotNull { header ->
            val rank = PreferenceHeader.matchEntire(header)?.groupValues?.get(1)?.toIntOrNull()
            if (rank == null) null else PreferenceColumn(header, rank)
        }.sortedBy { it.rank }

        val delegates = mutableListOf<MymunDelegateRow>()
        val questionAnswers = mutableListOf<MymunQuestionAnswerCell>()
        val preferences = mutableListOf<MymunPreferenceCandidate>()

        dataRows(sheet).forEach { row ->
            val firstName = row.text(headers, DelegateFirstName).trimToNull()
            val lastName = row.text(headers, DelegateLastName).trimToNull()
            val email = row.text(headers, DelegateEmail).trimToNull()

            if (firstName == null && lastName == null && email == null) return@forEach

            if (firstName == null) issues += blankRequiredCell(sheet.sheetName, row, DelegateFirstName, headers)
            if (lastName == null) issues += blankRequiredCell(sheet.sheetName, row, DelegateLastName, headers)
            if (email == null) issues += blankRequiredCell(sheet.sheetName, row, DelegateEmail, headers)

            val feePaidRaw = row.text(headers, DelegateFeePaid).trimToNull()
            val feePaid = parseBoolean(feePaidRaw)
            if (feePaidRaw != null && feePaid == null) {
                issues += MymunValidationIssue(
                    severity = MymunValidationSeverity.Error,
                    code = "invalid_fee_paid",
                    message = "Delegates row ${row.excelRowNumber} has an unrecognized Fee Paid value: $feePaidRaw",
                    location = row.location(sheet.sheetName, DelegateFeePaid, headers),
                )
            }

            val delegate = MymunDelegateRow(
                rowNumber = row.excelRowNumber,
                firstName = firstName.orEmpty(),
                lastName = lastName.orEmpty(),
                email = email.orEmpty(),
                normalizedEmail = email?.trim()?.lowercase(Locale.ROOT).orEmpty(),
                applicationStatus = row.text(headers, DelegateApplicationStatus).trimToNull(),
                submittedAt = row.date(headers, DelegateSubmittedAt, sheet.sheetName, issues),
                createdAt = row.date(headers, DelegateCreatedAt, sheet.sheetName, issues),
                feePaid = feePaid ?: false,
                country = row.text(headers, DelegateCountry).trimToNull(),
                city = row.text(headers, DelegateCity).trimToNull(),
                streetAddress = row.text(headers, DelegateStreet).trimToNull(),
                zipCode = row.text(headers, DelegateZip).trimToNull(),
                diet = row.text(headers, DelegateDiet).trimToNull(),
                phone = row.text(headers, DelegatePhone).trimToNull(),
                nationality = row.text(headers, DelegateNationality).trimToNull(),
                dateOfBirth = row.date(headers, DelegateDateOfBirth, sheet.sheetName, issues),
                delegationName = row.text(headers, DelegateDelegation).trimToNull(),
                isHeadDelegate = row.text(headers, DelegateHeadDelegate).trimToNull() != null,
                mymunAssignedPositionName = row.text(headers, DelegateAssignedPosition).trimToNull(),
                mymunAssignedCommitteeName = row.text(headers, DelegateCommittee).trimToNull(),
            )
            delegates += delegate

            questionColumns.forEach { questionColumn ->
                val answer = row.text(questionColumn.columnNumber - 1).trimToNull()
                questionAnswers += MymunQuestionAnswerCell(
                    delegateRowNumber = row.excelRowNumber,
                    questionColumn = questionColumn,
                    answer = answer,
                    location = CellLocation(sheet.sheetName, row.excelRowNumber, questionColumn.columnNumber, questionColumn.questionText),
                )
            }

            preferenceColumns.forEach { preferenceColumn ->
                val rawPreference = row.text(headers, preferenceColumn.header).trimToNull() ?: return@forEach
                val parsed = parsePreference(rawPreference)
                val resolved = parsed?.let {
                    countryLookup[PositionKey(normalizeLookup(it.committeeName), normalizeLookup(it.positionName))]
                }

                if (parsed == null || resolved == null) {
                    issues += MymunValidationIssue(
                        severity = MymunValidationSeverity.Error,
                        code = "unresolved_preference",
                        message = "Delegates row ${row.excelRowNumber} preference ${preferenceColumn.rank} does not match a Countries position: $rawPreference",
                        location = row.location(sheet.sheetName, preferenceColumn.header, headers),
                    )
                }

                preferences += MymunPreferenceCandidate(
                    delegateRowNumber = row.excelRowNumber,
                    rank = preferenceColumn.rank,
                    rawPreferenceText = rawPreference,
                    parsedCommitteeName = parsed?.committeeName,
                    parsedPositionName = parsed?.positionName,
                    resolvedCountryRowNumber = resolved?.rowNumber,
                    location = row.location(sheet.sheetName, preferenceColumn.header, headers),
                )
            }
        }

        return DelegateParseResult(delegates, questionColumns, questionAnswers, preferences)
    }

    private fun parseDelegations(
        sheet: Sheet,
        issues: MutableList<MymunValidationIssue>,
    ): List<MymunDelegationRow> {
        val headers = headerMap(sheet)
        issues += missingColumnIssues(sheet.sheetName, headers, listOf(DelegationName))

        return dataRows(sheet).mapNotNull { row ->
            val name = row.text(headers, DelegationName).trimToNull() ?: return@mapNotNull null
            MymunDelegationRow(
                rowNumber = row.excelRowNumber,
                name = name,
                country = row.text(headers, DelegationCountry).trimToNull(),
                currentDelegates = row.text(headers, DelegationCurrentDelegates).trimToNull()?.toIntOrNull(),
                expectedDelegates = row.text(headers, DelegationExpectedDelegates).trimToNull()?.toIntOrNull(),
                applicationStatus = row.text(headers, DelegationApplicationStatus).trimToNull(),
                submittedAt = row.date(headers, DelegationSubmittedAt, sheet.sheetName, issues),
                createdAt = row.date(headers, DelegationCreatedAt, sheet.sheetName, issues),
            )
        }.toList()
    }

    private fun parseConfirmedAssignments(countries: List<MymunCountryPositionRow>): List<MymunConfirmedAssignmentCandidate> =
        countries.flatMap { country ->
            parseAssignedDelegateNames(country.delegatesRaw).mapIndexed { index, delegateName ->
                MymunConfirmedAssignmentCandidate(
                    countryRowNumber = country.rowNumber,
                    committeeName = country.committeeName,
                    positionName = country.memberName,
                    slotIndex = index + 1,
                    delegateDisplayName = delegateName,
                    rawDelegatesText = country.delegatesRaw.orEmpty(),
                )
            }
        }

    private fun duplicateDelegateIssues(delegates: List<MymunDelegateRow>): List<MymunValidationIssue> =
        delegates.groupBy {
            listOf(
                normalizeLookup(it.firstName),
                normalizeLookup(it.lastName),
                it.normalizedEmail,
                normalizeLookup(it.zipCode.orEmpty()),
                it.dateOfBirth?.toString().orEmpty(),
            )
        }.values.filter { it.size > 1 }.map { duplicateRows ->
            MymunValidationIssue(
                severity = MymunValidationSeverity.Error,
                code = "duplicate_delegate",
                message = "Delegates rows ${duplicateRows.joinToString { row -> row.rowNumber.toString() }} have the same update matching identity.",
                location = CellLocation(DelegatesSheetName, duplicateRows.first().rowNumber, 1, DelegateFirstName),
            )
        }

    private fun headerMap(sheet: Sheet): HeaderMap {
        val headerRow = sheet.getRow(sheet.firstRowNum) ?: return HeaderMap(emptyMap(), emptyList())
        val headers = mutableMapOf<String, Int>()
        val orderedHeaders = mutableListOf<String>()
        headerRow.cellIterator().forEach { cell ->
            val header = cell.text().trim()
            if (header.isNotEmpty()) {
                headers[normalizeHeader(header)] = cell.columnIndex
                orderedHeaders += header
            }
        }
        return HeaderMap(headers, orderedHeaders)
    }

    private fun dataRows(sheet: Sheet): Sequence<Row> =
        ((sheet.firstRowNum + 1)..sheet.lastRowNum)
            .asSequence()
            .mapNotNull { sheet.getRow(it) }

    private fun missingColumnIssues(
        sheetName: String,
        headers: HeaderMap,
        requiredHeaders: List<String>,
    ): List<MymunValidationIssue> =
        requiredHeaders.filterNot { headers.contains(it) }.map { header ->
            MymunValidationIssue(
                severity = MymunValidationSeverity.Error,
                code = "missing_column",
                message = "$sheetName is missing required column: $header",
                location = CellLocation(sheetName, 1, 1, header),
            )
        }

    private fun missingSheet(sheetName: String) = MymunValidationIssue(
        severity = MymunValidationSeverity.Error,
        code = "missing_sheet",
        message = "Workbook is missing required sheet: $sheetName",
        location = null,
    )

    private fun blankRequiredCell(
        sheetName: String,
        row: Row,
        header: String,
        headers: HeaderMap,
    ) = MymunValidationIssue(
        severity = MymunValidationSeverity.Error,
        code = "blank_required_cell",
        message = "$sheetName row ${row.excelRowNumber} has a blank required value for $header",
        location = row.location(sheetName, header, headers),
    )

    private fun parsePreference(rawPreference: String): ParsedPreference? {
        val match = PreferenceValue.matchEntire(rawPreference.trim()) ?: return null
        return ParsedPreference(
            committeeName = match.groupValues[1].trim(),
            positionName = match.groupValues[2].trim(),
        )
    }

    private fun parseAssignedDelegateNames(rawDelegates: String?): List<String> =
        rawDelegates.orEmpty()
            .split(",")
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

    private fun parseBoolean(value: String?): Boolean? =
        when (value?.trim()?.lowercase(Locale.ROOT)) {
            null, "" -> null
            "true", "yes", "y", "paid", "1" -> true
            "false", "no", "n", "unpaid", "0" -> false
            else -> null
        }

    private fun normalizeLookup(value: String): String =
        normalizeHeader(value)

    private fun String.trimToNull(): String? = trim().takeIf { it.isNotEmpty() }

    private fun Row.text(headers: HeaderMap, header: String): String =
        headers.indexOrNull(header)?.let { text(it) }.orEmpty()

    private fun Row.text(columnIndex: Int): String =
        getCell(columnIndex)?.text().orEmpty()

    private fun Row.date(
        headers: HeaderMap,
        header: String,
        sheetName: String,
        issues: MutableList<MymunValidationIssue>,
    ): LocalDate? =
        headers.indexOrNull(header)?.let { columnIndex ->
            getCell(columnIndex)?.dateValue(
                location = CellLocation(sheetName, excelRowNumber, columnIndex + 1, header),
                issues = issues,
            )
        }

    private fun Row.location(sheetName: String, header: String, headers: HeaderMap): CellLocation =
        CellLocation(sheetName, excelRowNumber, (headers.indexOrNull(header) ?: 0) + 1, header)

    private fun Cell.text(): String =
        when (cellType) {
            CellType.BLANK -> ""
            else -> formatter.formatCellValue(this)
        }

    private fun Cell.dateValue(
        location: CellLocation,
        issues: MutableList<MymunValidationIssue>,
    ): LocalDate? {
        if (cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(this)) {
            return localDateTimeCellValue.toLocalDate()
        }

        val text = text().trim()
        if (text.isEmpty()) return null

        return try {
            LocalDate.parse(text)
        } catch (_: DateTimeParseException) {
            issues += MymunValidationIssue(
                severity = MymunValidationSeverity.Error,
                code = "invalid_date",
                message = "${location.sheetName} row ${location.rowNumber} has an invalid date in ${location.columnName}: $text",
                location = location,
            )
            null
        }
    }

    private val Row.excelRowNumber: Int get() = rowNum + 1

    private data class HeaderMap(
        private val normalizedHeaders: Map<String, Int>,
        val orderedHeaders: List<String>,
    ) {
        fun contains(header: String): Boolean = normalizedHeaders.containsKey(normalizeHeader(header))

        fun indexOrNull(header: String): Int? = normalizedHeaders[normalizeHeader(header)]

        fun columnIndex(header: String): Int = indexOrNull(header) ?: error("Missing header: $header")
    }

    private data class DelegateParseResult(
        val delegates: List<MymunDelegateRow> = emptyList(),
        val questionColumns: List<MymunQuestionColumn> = emptyList(),
        val questionAnswers: List<MymunQuestionAnswerCell> = emptyList(),
        val preferenceCandidates: List<MymunPreferenceCandidate> = emptyList(),
    )

    private data class PreferenceColumn(val header: String, val rank: Int)

    private data class ParsedPreference(val committeeName: String, val positionName: String)

    private data class PositionKey(val committeeName: String, val positionName: String)

    private companion object {
        const val DelegatesSheetName = "Delegates"
        const val CountriesSheetName = "Countries"
        const val DelegationsSheetName = "Delegations"

        const val QuestionPrefix = "Question:"

        const val CountryCommittee = "Committee Name"
        const val CountryMember = "Member Name"
        const val CountryDelegation = "Delegation Name"
        const val CountryDelegates = "Delegates"

        const val DelegateFirstName = "First Name"
        const val DelegateLastName = "Last Name"
        const val DelegateCountry = "Country"
        const val DelegateCity = "City"
        const val DelegateStreet = "address_street"
        const val DelegateZip = "Zip Code"
        const val DelegateEmail = "Email"
        const val DelegateApplicationStatus = "Application Status"
        const val DelegateSubmittedAt = "Submitted At"
        const val DelegateCreatedAt = "Created At"
        const val DelegateDiet = "Diet"
        const val DelegatePhone = "Phone"
        const val DelegateNationality = "Nationality"
        const val DelegateFeePaid = "Fee Paid"
        const val DelegateDateOfBirth = "Date of Birth"
        const val DelegateDelegation = "Delegation"
        const val DelegateHeadDelegate = "Head Delegate/Faculty Advisor"
        const val DelegateAssignedPosition = "Model Country assigned"
        const val DelegateCommittee = "Committee"

        const val DelegationName = "Delegation Name"
        const val DelegationCountry = "Country"
        const val DelegationCurrentDelegates = "Current Delegates"
        const val DelegationExpectedDelegates = "Expected Delegates"
        const val DelegationApplicationStatus = "Application Status"
        const val DelegationSubmittedAt = "Submitted At"
        const val DelegationCreatedAt = "Created At"

        val Whitespace = Regex("\\s+")
        val PreferenceHeader = Regex("""Preference\s+(\d+)""", RegexOption.IGNORE_CASE)
        val PreferenceValue = Regex("""(.+?)\s*\((.+)\)\s*""")

        fun normalizeHeader(value: String): String =
            value.trim().replace(Whitespace, " ").lowercase(Locale.ROOT)
    }
}
