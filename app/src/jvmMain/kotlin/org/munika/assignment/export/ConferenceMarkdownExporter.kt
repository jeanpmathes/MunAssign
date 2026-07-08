package org.munika.assignment.export

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.munika.assignment.domain.model.AssignmentStatus
import org.munika.assignment.domain.model.Committee
import org.munika.assignment.domain.model.CommitteeMode
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.DelegateId
import org.munika.assignment.domain.model.HighSchoolerStatus
import org.munika.assignment.domain.model.LanguageLevel
import org.munika.assignment.domain.model.MunExperienceLevel
import org.munika.assignment.domain.model.PaymentStatus
import org.munika.assignment.domain.model.Position
import org.munika.assignment.domain.model.Slot

enum class MarkdownSafetyExportTrigger(val fileLabel: String, val displayLabel: String) {
    Open(fileLabel = "open", displayLabel = "Open"),
    Close(fileLabel = "close", displayLabel = "Close"),
}

/**
 * Renders a human-readable recovery snapshot for one complete conference aggregate.
 */
object ConferenceMarkdownExporter {
    fun render(
        conference: Conference,
        exportedAt: Instant,
        trigger: MarkdownSafetyExportTrigger,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val delegateNumbers = conference.delegateNumbers()
        return buildString {
            appendLine("# ${conference.name}")
            appendLine()
            appendLine("- Exported at: ${exportedAt.exportDateTime(zoneId)}")
            appendLine("- Last MyMUN update: ${conference.lastMymunUpdateAt?.exportDateTime(zoneId) ?: "Never"}")
            appendLine()
            appendHeaderStats(conference)
            appendLine()
            appendCommittees(conference, delegateNumbers)
            appendLine()
            appendDelegates(conference, delegateNumbers, zoneId)
        }
    }

    private fun StringBuilder.appendHeaderStats(conference: Conference) {
        val activePaidCount = conference.delegates.count { it.active && it.paymentStatus == PaymentStatus.Paid }
        val unpaidCount = conference.delegates.count { it.paymentStatus == PaymentStatus.Unpaid }
        val inactiveCount = conference.delegates.count { !it.active }
        appendLine("## Statistics")
        appendLine()
        appendBullet("Delegates", conference.delegates.size)
        appendBullet("Active paid delegates", activePaidCount)
        appendBullet("Unpaid delegates (all applications)", unpaidCount)
        appendBullet("Inactive delegates", inactiveCount)
        appendBullet("Committees", conference.committees.size)
        appendBullet("Positions", conference.positions.size)
        appendBullet("Reservations", conference.reservations.size)
        appendBullet("Open slots", conference.openSlots.size)
        appendBullet("Pending slots", conference.pendingSlots.size)
        appendBullet("Confirmed slots", conference.assignedSlots.size)
    }

    private fun StringBuilder.appendCommittees(
        conference: Conference,
        delegateNumbers: Map<DelegateId, Int>,
    ) {
        appendLine("## Committees")
        conference.committees.forEach { committee ->
            appendLine()
            appendLine("### ${committee.exportHeader}")
            appendLine()
            appendLine("| Position | Slot | Status | Delegate | Preferences |")
            appendLine("| --- | ---: | --- | --- | --- |")
            committee.positions.forEach { position ->
                position.slots.sortedBy { it.index }.forEach { slot ->
                    appendTableRow(
                        position.exportLabel,
                        slot.index.toString(),
                        slot.status.exportLabel(),
                        slot.delegate?.exportLabel(delegateNumbers) ?: "",
                        position.interestedDelegateLabels(delegateNumbers),
                    )
                }
            }
        }
    }

    private fun StringBuilder.appendDelegates(
        conference: Conference,
        delegateNumbers: Map<DelegateId, Int>,
        zoneId: ZoneId,
    ) {
        appendLine("## Delegates")
        appendLine()
        appendLine(
            "| # | Delegate | Assignment | Payment | Active | Current assignment | Delegation | School level | Experience | Language | Application status | Submitted | Preferences |",
        )
        appendLine("| ---: | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
        conference.delegates.sortedForExport().forEach { delegate ->
            appendTableRow(
                delegateNumbers.getValue(delegate.id).toString(),
                delegate.displayName,
                delegate.assignmentStatus.exportLabel(),
                delegate.paymentStatus.exportLabel(),
                if (delegate.active) "Active" else "Inactive",
                delegate.currentAssignmentLabel(),
                delegate.delegation?.displayLabel.orEmpty(),
                delegate.schoolLevelLabel(),
                delegate.experienceLevel.exportLabel(),
                delegate.languageLevel.exportLabel(),
                delegate.profile.applicationStatus.orEmpty(),
                delegate.submittedAt?.exportDate(zoneId).orEmpty(),
                delegate.preferenceLabels(),
            )
        }
    }

    private fun Conference.delegateNumbers(): Map<DelegateId, Int> =
        delegates.sortedForExport()
            .mapIndexed { index, delegate -> delegate.id to index + 1 }
            .toMap()

    private fun List<Delegate>.sortedForExport(): List<Delegate> =
        sortedWith(
            compareBy<Delegate> { it.submittedAt ?: Instant.MAX }
                .thenBy { it.displayName }
                .thenBy { it.id.value },
        )

    private fun Position.interestedDelegateLabels(delegateNumbers: Map<DelegateId, Int>): String =
        interestedDelegates
            .mapNotNull { delegate ->
                val rank = delegate.preferences.firstOrNull { it.position === this }?.rank ?: return@mapNotNull null
                rank to delegate
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .toSortedMap()
            .map { (rank, delegates) ->
                val labels = delegates.sortedBy { it.displayName }.joinToString(", ") { it.exportLabel(delegateNumbers) }
                "${rank.ordinalLabel()}: $labels"
            }
            .joinToString("; ")

    private fun Delegate.preferenceLabels(): String =
        preferences.sortedBy { it.rank }
            .joinToString("; ") { preference ->
                "${preference.rank.ordinalLabel()}: ${preference.position.committee.displayName} (${preference.position.exportLabel})"
            }

    private fun Delegate.currentAssignmentLabel(): String {
        val slot = currentSlot ?: return ""
        val position = slot.position
        return "${position.committee.displayName} (${position.exportLabel}), slot ${slot.index}"
    }

    private val Committee.exportHeader: String
        get() = "$displayName (${mode.exportLabel()})"

    private val Position.exportLabel: String
        get() {
            if (committee.mode != CommitteeMode.Mapped) return name
            val mapping = mappedCommittee?.mode?.exportLabel() ?: "Incomplete"
            return "$name ($mapping)"
        }

    private fun CommitteeMode.exportLabel(): String =
        when (this) {
            CommitteeMode.HighSchool -> "High school"
            CommitteeMode.University -> "University"
            CommitteeMode.Mapped -> "Mapped"
        }

    private fun Delegate.schoolLevelLabel(): String =
        when (highSchoolerStatus) {
            HighSchoolerStatus.Yes -> "High school"
            HighSchoolerStatus.No -> "University"
            null -> "Unknown"
        }

    private fun Delegate.exportLabel(delegateNumbers: Map<DelegateId, Int>): String =
        "$displayName [${delegateNumbers.getValue(id)}]"

    private fun AssignmentStatus.exportLabel(): String =
        when (this) {
            AssignmentStatus.Empty -> "Empty"
            AssignmentStatus.Pending -> "Pending"
            AssignmentStatus.Assigned -> "Confirmed"
        }

    private fun PaymentStatus.exportLabel(): String =
        when (this) {
            PaymentStatus.Paid -> "Paid"
            PaymentStatus.Unpaid -> "Unpaid"
        }

    private fun MunExperienceLevel?.exportLabel(): String =
        when (this) {
            MunExperienceLevel.FirstTime -> "First time"
            MunExperienceLevel.Beginner -> "Beginner"
            MunExperienceLevel.Intermediate -> "Intermediate"
            MunExperienceLevel.Advanced -> "Advanced"
            null -> ""
        }

    private fun LanguageLevel?.exportLabel(): String =
        when (this) {
            LanguageLevel.BeginnerA1A2 -> "A1/A2"
            LanguageLevel.IndependentUserB1B2 -> "B1/B2"
            LanguageLevel.ProficientUserC1C2 -> "C1/C2"
            null -> ""
        }

    private fun StringBuilder.appendBullet(label: String, value: Int) {
        appendLine("- $label: $value")
    }

    private fun Instant.exportDateTime(zoneId: ZoneId): String =
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale.ROOT)
            .withZone(zoneId)
            .format(this)

    private fun Instant.exportDate(zoneId: ZoneId): String =
        DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)
            .withZone(zoneId)
            .format(this)

    private fun Int.ordinalLabel(): String {
        val suffix = if (this % 100 in 11..13) {
            "th"
        } else {
            when (this % 10) {
                1 -> "st"
                2 -> "nd"
                3 -> "rd"
                else -> "th"
            }
        }
        return "$this$suffix"
    }

    private fun StringBuilder.appendTableRow(vararg cells: String) {
        append("| ")
        append(cells.joinToString(" | ") { it.markdownCell() })
        appendLine(" |")
    }

    private fun String.markdownCell(): String =
        replace("\\", "\\\\")
            .replace("|", "\\|")
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
}
