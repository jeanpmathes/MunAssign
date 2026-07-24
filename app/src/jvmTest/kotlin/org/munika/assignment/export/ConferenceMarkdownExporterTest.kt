package org.munika.assignment.export

import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.munika.assignment.domain.model.AssignmentSource
import org.munika.assignment.domain.model.Committee
import org.munika.assignment.domain.model.CommitteeId
import org.munika.assignment.domain.model.CommitteeMode
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.ConferenceId
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.DelegateId
import org.munika.assignment.domain.model.DelegateProfile
import org.munika.assignment.domain.model.HighSchoolerAnswerMapping
import org.munika.assignment.domain.model.HighSchoolerStatus
import org.munika.assignment.domain.model.LanguageLevel
import org.munika.assignment.domain.model.LanguageLevelAnswerMapping
import org.munika.assignment.domain.model.MunExperienceAnswerMapping
import org.munika.assignment.domain.model.MunExperienceLevel
import org.munika.assignment.domain.model.PaymentStatus
import org.munika.assignment.domain.model.PaymentTier
import org.munika.assignment.domain.model.PersonName
import org.munika.assignment.domain.model.Position
import org.munika.assignment.domain.model.PositionId
import org.munika.assignment.domain.model.Preference
import org.munika.assignment.domain.model.PreferenceId
import org.munika.assignment.domain.model.Question
import org.munika.assignment.domain.model.QuestionAnswer
import org.munika.assignment.domain.model.QuestionAnswerId
import org.munika.assignment.domain.model.QuestionId
import org.munika.assignment.domain.model.Slot
import org.munika.assignment.domain.model.SlotId
import org.munika.assignment.domain.model.StandardQuestionHandlingTypes
import org.munika.assignment.domain.model.StandardQuestionRoles
import org.munika.assignment.domain.model.TypedAnswerMappings

class ConferenceMarkdownExporterTest {
    @Test
    fun `render includes assignments interests delegates and continuous application-order numbers`() {
        val conference = sampleConference()
        val markdown = ConferenceMarkdownExporter.render(
            conference = conference,
            exportedAt = Instant.parse("2026-06-25T12:30:00Z"),
            trigger = MarkdownSafetyExportTrigger.Open,
            zoneId = ZoneId.of("UTC"),
        )

        assertContains(markdown, "# KAMUN")
        assertContains(markdown, "- Exported at: 25.06.2026 12:30:00")
        assertContains(markdown, "- Last MyMUN update: 20.06.2026 10:00:00")
        assertFalse(markdown.contains("2026-06-25T12:30:00Z"))
        assertContains(markdown, "## Statistics")
        assertFalse(markdown.contains("Export trigger"))
        assertContains(markdown, "- Active paid delegates: 4")
        assertContains(markdown, "- Pending slots: 1")
        assertContains(markdown, "- Confirmed slots: 1")
        assertFalse(markdown.contains("| Metric | Value |"))
        assertContains(markdown, "## Committees")
        assertFalse(markdown.contains("## Assignments"))
        assertContains(markdown, "### General Assembly (University)")
        assertContains(markdown, "- Payment tier: Reduced")
        assertContains(markdown, "| Position | Slot | Status | Delegate | Preferences |")
        assertContains(markdown, "| Germany | 1 | Confirmed | Assigned Delegate [2] |  |")
        assertContains(markdown, "| Brazil | 1 | Pending | Pending Delegate [3] |  |")
        assertContains(markdown, "| Japan | 1 | Empty |  | 1st: Interested Delegate [1], Zed Delegate [5]; 2nd: Pending Delegate [3] |")
        assertFalse(markdown.contains("## Position Interest"))
        assertContains(
            markdown,
            "| 1 | Interested Delegate | Empty | Paid | Full | Active |  |  | High school | Beginner | B1/B2 | Accepted | 02.01.2026 | 1st: General Assembly (Japan) |",
        )
        assertContains(markdown, "| 2 | Assigned Delegate | Confirmed | Paid | Reduced | Active |")
        assertContains(markdown, "| 3 | Pending Delegate | Pending | Paid | Unassigned | Active |")
        assertContains(markdown, "| 4 | Unpaid Delegate | Empty | Unpaid | Unassigned | Active |")
        assertContains(markdown, "| 6 | Inactive Delegate | Empty | Paid | Full | Inactive |")
        assertFalse(markdown.contains("@example.test"))
        assertFalse(markdown.contains("Hidden Street"))
        assertFalse(markdown.contains("+49"))
    }

    @Test
    fun `render spells out mapped committee and position mapping labels`() {
        val conference = Conference(
            id = ConferenceId("conference"),
            name = "KAMUN",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val target = Committee(
            id = CommitteeId("target"),
            conference = conference,
            mymunName = "Human Rights Council",
            slotsPerPosition = 1,
            mode = CommitteeMode.HighSchool,
        )
        val mapped = Committee(
            id = CommitteeId("ipc"),
            conference = conference,
            mymunName = "International Press Corps",
            slotsPerPosition = 1,
            mode = CommitteeMode.Mapped,
        )
        val mappedPosition = position(mapped, "journalist", "Journalist")
        val incompletePosition = position(mapped, "photographer", "Photographer")
        mapped.positions += listOf(mappedPosition, incompletePosition)
        conference.committees += listOf(target, mapped)
        mappedPosition.setMappedCommittee(target)

        val markdown = ConferenceMarkdownExporter.render(
            conference = conference,
            exportedAt = Instant.parse("2026-06-25T12:30:00Z"),
            trigger = MarkdownSafetyExportTrigger.Close,
            zoneId = ZoneId.of("UTC"),
        )

        assertContains(markdown, "### International Press Corps (Mapped)")
        assertContains(markdown, "| Journalist (High school) | 1 | Empty |  |  |")
        assertContains(markdown, "| Photographer (Incomplete) | 1 | Empty |  |  |")
    }

    @Test
    fun `service writes unique timestamped markdown files without overwriting existing files`() {
        val directory = Files.createTempDirectory("munassign-markdown-export-test")
        val service = MarkdownSafetyExportService(
            exportDirectory = directory,
            clock = Clock.fixed(Instant.parse("2026-06-25T12:30:00Z"), ZoneId.of("UTC")),
            zoneId = ZoneId.of("UTC"),
        )
        val conference = sampleConference()

        val first = kotlinx.coroutines.runBlocking {
            service.exportAll(listOf(conference), MarkdownSafetyExportTrigger.Open)
        }
        val second = kotlinx.coroutines.runBlocking {
            service.exportAll(listOf(conference), MarkdownSafetyExportTrigger.Open)
        }

        assertEquals(emptyList(), first.failures)
        assertEquals(emptyList(), second.failures)
        assertEquals("20260625-123000-open-kamun.md", first.writtenFiles.single().fileName.toString())
        assertEquals("20260625-123000-open-kamun-2.md", second.writtenFiles.single().fileName.toString())
        assertTrue(Files.exists(first.writtenFiles.single()))
        assertTrue(Files.exists(second.writtenFiles.single()))
    }

    @Test
    fun `service creates export directory when there are no conferences`() {
        val directory = Files.createTempDirectory("munassign-markdown-empty-test").resolve("exports")
        val service = MarkdownSafetyExportService(
            exportDirectory = directory,
            clock = Clock.fixed(Instant.parse("2026-06-25T12:30:00Z"), ZoneId.of("UTC")),
            zoneId = ZoneId.of("UTC"),
        )

        val result = kotlinx.coroutines.runBlocking {
            service.exportAll(emptyList(), MarkdownSafetyExportTrigger.Close)
        }

        assertTrue(Files.isDirectory(directory))
        assertEquals(emptyList(), result.writtenFiles)
        assertEquals(emptyList(), result.failures)
    }

    @Test
    fun `service prunes exports beyond the retention limit per conference and trigger`() {
        val directory = Files.createTempDirectory("munassign-markdown-retention-test")
        val conference = sampleConference()
        val baseInstant = Instant.parse("2026-06-25T12:30:00Z")

        repeat(25) { index ->
            val service = MarkdownSafetyExportService(
                exportDirectory = directory,
                clock = Clock.fixed(baseInstant.plusSeconds(index.toLong()), ZoneId.of("UTC")),
                zoneId = ZoneId.of("UTC"),
            )
            kotlinx.coroutines.runBlocking {
                service.exportAll(listOf(conference), MarkdownSafetyExportTrigger.Open)
            }
        }

        val remaining = Files.newDirectoryStream(directory).use { stream -> stream.toList() }
        assertEquals(20, remaining.size)
        assertTrue(remaining.all { it.fileName.toString().contains("-open-kamun") })
        assertTrue(remaining.none { it.fileName.toString().startsWith("20260625-123000-") })
        assertTrue(remaining.any { it.fileName.toString().startsWith("20260625-123024-") })
    }

    private fun sampleConference(): Conference {
        val conference = Conference(
            id = ConferenceId("conference"),
            name = "KAMUN",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            lastMymunUpdateAt = Instant.parse("2026-06-20T10:00:00Z"),
        )
        val committee = Committee(
            id = CommitteeId("ga"),
            conference = conference,
            mymunName = "General Assembly",
            slotsPerPosition = 1,
            paymentTier = PaymentTier.Reduced,
        )
        val germany = position(committee, "germany", "Germany")
        val brazil = position(committee, "brazil", "Brazil")
        val japan = position(committee, "japan", "Japan")
        committee.positions += listOf(germany, brazil, japan)
        conference.committees += committee

        val interested = delegate(
            conference,
            "interested",
            "Interested",
            "Delegate",
            days = 1,
            isHighSchool = true,
            paymentTier = PaymentTier.Full,
        )
        val assigned = delegate(
            conference,
            "assigned",
            "Assigned",
            "Delegate",
            days = 2,
            paymentTier = PaymentTier.Reduced,
        )
        val pending = delegate(conference, "pending", "Pending", "Delegate", days = 3)
        val unpaid = delegate(conference, "unpaid", "Unpaid", "Delegate", days = 4, paymentStatus = PaymentStatus.Unpaid)
        val zed = delegate(conference, "zed", "Zed", "Delegate", days = 5, paymentTier = PaymentTier.Full)
        val inactive = delegate(
            conference,
            "inactive",
            "Inactive",
            "Delegate",
            days = null,
            paymentTier = PaymentTier.Full,
            active = false,
        )
        conference.delegates += listOf(pending, inactive, interested, assigned, unpaid, zed)

        addPreference(interested, japan, 1)
        addPreference(zed, japan, 1)
        addPreference(pending, japan, 2)
        germany.slots.single().assign(assigned, AssignmentSource.Mymun, Instant.EPOCH)
        brazil.slots.single().assign(pending, AssignmentSource.Application, Instant.EPOCH)
        return conference
    }

    private fun position(committee: Committee, id: String, name: String): Position {
        val position = Position(PositionId(id), committee, name)
        position.slots += Slot(SlotId("$id-slot"), position, 1, Instant.EPOCH)
        return position
    }

    private fun delegate(
        conference: Conference,
        id: String,
        firstName: String,
        lastName: String,
        days: Long?,
        isHighSchool: Boolean = false,
        paymentStatus: PaymentStatus = PaymentStatus.Paid,
        paymentTier: PaymentTier? = null,
        active: Boolean = true,
    ): Delegate {
        val delegate = Delegate(
            id = DelegateId(id),
            conference = conference,
            name = PersonName(firstName, lastName),
            email = "$id@example.test",
            profile = DelegateProfile(
                country = "DE",
                city = "Berlin",
                streetAddress = "Hidden Street",
                zipCode = "10000",
                diet = null,
                phone = "+49",
                nationality = "German",
                dateOfBirth = null,
                delegationNameFromMymun = null,
                applicationStatus = "Accepted",
            ),
            paymentStatus = paymentStatus,
            paymentTier = paymentTier,
            active = active,
            isHeadDelegate = false,
            submittedAt = days?.let { Instant.parse("2026-01-01T00:00:00Z").plusSeconds(it * 86_400) },
        )
        delegate.answers += QuestionAnswer(
            id = QuestionAnswerId("$id-school-answer"),
            delegate = delegate,
            question = highSchoolQuestion(),
            rawAnswer = if (isHighSchool) "Yes" else "No",
        )
        delegate.answers += QuestionAnswer(
            id = QuestionAnswerId("$id-experience-answer"),
            delegate = delegate,
            question = experienceQuestion(),
            rawAnswer = "Beginner",
        )
        delegate.answers += QuestionAnswer(
            id = QuestionAnswerId("$id-language-answer"),
            delegate = delegate,
            question = languageQuestion(),
            rawAnswer = "B1",
        )
        return delegate
    }

    private fun addPreference(delegate: Delegate, position: Position, rank: Int) {
        delegate.preferences += Preference(
            id = PreferenceId("${delegate.id.value}-${position.id.value}"),
            delegate = delegate,
            position = position,
            rank = rank,
            rawPreferenceText = "${position.committee.displayName} (${position.name})",
        )
    }

    private fun highSchoolQuestion(): Question =
        Question(
            id = QuestionId("school"),
            questionText = "High school?",
            role = StandardQuestionRoles.HighSchoolerStatus,
            handling = StandardQuestionHandlingTypes.ExpectedTypedAnswer,
            mappings = TypedAnswerMappings(
                highSchooler = listOf(
                    HighSchoolerAnswerMapping("Yes", HighSchoolerStatus.Yes),
                    HighSchoolerAnswerMapping("No", HighSchoolerStatus.No),
                ),
            ),
        )

    private fun experienceQuestion(): Question =
        Question(
            id = QuestionId("experience"),
            questionText = "Experience?",
            role = StandardQuestionRoles.ExperienceLevel,
            handling = StandardQuestionHandlingTypes.ExpectedTypedAnswer,
            mappings = TypedAnswerMappings(
                munExperience = listOf(MunExperienceAnswerMapping("Beginner", MunExperienceLevel.Beginner)),
            ),
        )

    private fun languageQuestion(): Question =
        Question(
            id = QuestionId("language"),
            questionText = "Language?",
            role = StandardQuestionRoles.LanguageLevel,
            handling = StandardQuestionHandlingTypes.ExpectedTypedAnswer,
            mappings = TypedAnswerMappings(
                languageLevel = listOf(LanguageLevelAnswerMapping("B1", LanguageLevel.IndependentUserB1B2)),
            ),
        )
}
