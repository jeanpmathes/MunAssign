# Issue Handling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add persistent warning/notice resolution, a global notice counter and manager, and delegate/detail UI behavior that hides resolved markings while preserving resolution context.

**Architecture:** Keep issue detection derived from domain rules, and persist only organizer resolutions keyed by conference, delegate, optional question, and stable issue key. The UI will build one flattened notice list from the selected conference graph; open notices drive counters and warning markers, while resolved notices appear last in the manager and as neutral resolution text in delegate answer/profile sections.

**Tech Stack:** Kotlin Multiplatform domain/data/app modules, SQLite via Exposed, Compose Multiplatform Desktop, ViewModel + StateFlow, Gradle with Temurin 24.

---

## File Structure

- Create `domain/src/main/kotlin/org/munika/assignment/domain/model/AnswerIssueResolutionId.kt`: value object for persisted issue resolution IDs.
- Create `domain/src/main/kotlin/org/munika/assignment/domain/model/AnswerIssueResolution.kt`: domain model for one organizer-managed resolution.
- Modify `domain/src/main/kotlin/org/munika/assignment/domain/model/AnswerIssue.kt`: add a stable `key` derived from severity and message.
- Modify `domain/src/main/kotlin/org/munika/assignment/domain/model/QuestionAnswer.kt`: expose open/resolved issues using the owning delegate's resolutions.
- Modify `domain/src/main/kotlin/org/munika/assignment/domain/model/Delegate.kt`: own resolution collection and profile issue helpers.
- Modify `domain/src/main/kotlin/org/munika/assignment/domain/repository/ConferenceRepository.kt`: add `resolveAnswerIssue(...)`.
- Create `data/src/main/kotlin/org/munika/assignment/data/db/tables/AnswerIssueResolutionTable.kt`: Exposed table for persisted resolutions.
- Create `data/src/main/kotlin/org/munika/assignment/data/db/model/AnswerIssueResolutionRow.kt`: row DTO.
- Modify `data/src/main/kotlin/org/munika/assignment/data/db/tables/MunAssignTables.kt`: register the new table.
- Modify `data/src/main/kotlin/org/munika/assignment/data/db/DatabaseFactory.kt`: migration version 6 creates the table and indexes.
- Modify `data/src/main/kotlin/org/munika/assignment/data/db/model/ConferenceGraphRows.kt`: include resolution rows.
- Modify `data/src/main/kotlin/org/munika/assignment/data/db/mapper/ConferenceGraphMapper.kt`: attach resolutions to delegates.
- Modify `data/src/main/kotlin/org/munika/assignment/data/db/repository/ConferenceRepositoryImpl.kt`: load rows and implement resolution persistence.
- Modify `app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/DelegateDetailUiModels.kt`: separate open vs resolved issue display/counts.
- Modify `app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/DelegateListUiModels.kt`: count only open issues.
- Create `app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/NoticeManagerUiModels.kt`: flatten conference issues for the bell and manager.
- Modify `app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/MainViewModel.kt`: add manager state and resolve action.
- Modify `app/src/jvmMain/kotlin/org/munika/assignment/App.kt`: wire new callbacks.
- Modify `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/MainScreen.kt`: add bell icon and notice manager dialog.
- Modify `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/component/DelegateSidePanel.kt`: show resolved counts and resolution text.
- Create `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/dialog/NoticeManagerDialog.kt`: searchable/filter-aware issue manager.
- Test `domain/src/test/kotlin/org/munika/assignment/domain/model/QuestionTest.kt`: issue keys and resolution filtering.
- Test `data/src/test/kotlin/org/munika/assignment/data/db/repository/ConferenceRepositoryImplTest.kt`: persistence and update survival.
- Test `app/src/jvmTest/kotlin/org/munika/assignment/ui/viewmodel/DelegateDetailUiModelsTest.kt`: resolved display in detail.
- Test `app/src/jvmTest/kotlin/org/munika/assignment/ui/viewmodel/DelegateListUiModelsTest.kt`: list counts exclude resolved issues.
- Create `app/src/jvmTest/kotlin/org/munika/assignment/ui/viewmodel/NoticeManagerUiModelsTest.kt`: global counts and resolved ordering.
- Modify `docs/DATA_MODEL.md`: document answer issue resolutions.
- Modify `docs/TODO.md`: remove the completed issue-handling item or narrow it to any deferred polish discovered during implementation.

---

### Task 1: Domain Resolution Model

**Files:**
- Create: `domain/src/main/kotlin/org/munika/assignment/domain/model/AnswerIssueResolutionId.kt`
- Create: `domain/src/main/kotlin/org/munika/assignment/domain/model/AnswerIssueResolution.kt`
- Modify: `domain/src/main/kotlin/org/munika/assignment/domain/model/AnswerIssue.kt`
- Modify: `domain/src/main/kotlin/org/munika/assignment/domain/model/QuestionAnswer.kt`
- Modify: `domain/src/main/kotlin/org/munika/assignment/domain/model/Delegate.kt`
- Test: `domain/src/test/kotlin/org/munika/assignment/domain/model/QuestionTest.kt`

- [ ] **Step 1: Write failing domain tests**

Append these tests to `QuestionTest`:

```kotlin
@Test
fun `answer issue key is stable for matching severity and message`() {
    val first = AnswerIssue(" Visa support requested. ", AnswerIssueSeverity.Notice)
    val second = AnswerIssue("Visa support requested.", AnswerIssueSeverity.Notice)

    assertEquals(second.key, first.key)
    assertEquals("Notice:Visa support requested.", first.key)
}

@Test
fun `question answer separates open and resolved issues`() {
    val conference = Conference(ConferenceId("conference"), "KAMUN")
    val delegate = Delegate(
        id = DelegateId("delegate"),
        conference = conference,
        name = PersonName("Alex", "Kim"),
        email = null,
        profile = DelegateProfile(null, null, null, null, null, null, null, null, null, null),
        paymentStatus = PaymentStatus.Paid,
        isHeadDelegate = false,
    )
    val question = Question(
        id = QuestionId("visa"),
        questionText = "Visa?",
        rules = listOf(NoticeWhenNonBlank("Visa support requested.")),
    )
    val answer = QuestionAnswer(QuestionAnswerId("answer"), delegate, question, "Yes")
    delegate.answers += answer
    delegate.issueResolutions += AnswerIssueResolution(
        id = AnswerIssueResolutionId("resolution"),
        delegate = delegate,
        questionId = question.id,
        issueKey = answer.issues.single().key,
        issueMessage = "Visa support requested.",
        severity = AnswerIssueSeverity.Notice,
        justification = "Handled by visa team.",
        resolvedAt = Instant.EPOCH,
    )

    assertTrue(answer.openIssues.isEmpty())
    assertEquals("Handled by visa team.", answer.resolvedIssues.single().resolution.justification)
}
```

- [ ] **Step 2: Run domain tests and confirm failure**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :domain:test --tests org.munika.assignment.domain.model.QuestionTest --stacktrace
```

Expected: compile failure for missing `key`, `AnswerIssueResolution`, and open/resolved issue APIs.

- [ ] **Step 3: Add resolution domain types**

Create `AnswerIssueResolutionId.kt`:

```kotlin
package org.munika.assignment.domain.model

@JvmInline
value class AnswerIssueResolutionId(val value: String)
```

Create `AnswerIssueResolution.kt`:

```kotlin
package org.munika.assignment.domain.model

import java.time.Instant

/**
 * Organizer-managed resolution for a derived answer or profile issue.
 *
 * Question-backed issues use [questionId]. Profile checks use `null` and are matched by delegate
 * plus issue key.
 */
data class AnswerIssueResolution(
    override val id: AnswerIssueResolutionId,
    val delegate: Delegate,
    val questionId: QuestionId?,
    val issueKey: String,
    val issueMessage: String,
    val severity: AnswerIssueSeverity,
    val justification: String?,
    val resolvedAt: Instant,
) : DomainObject<AnswerIssueResolutionId>
```

- [ ] **Step 4: Add stable issue keys**

Change `AnswerIssue.kt` to:

```kotlin
package org.munika.assignment.domain.model

/**
 * Notice or warning produced by applying answer rules to a raw MyMUN answer.
 */
data class AnswerIssue(
    val message: String,
    val severity: AnswerIssueSeverity,
) {
    val key: String = stableKey(severity, message)

    companion object {
        fun stableKey(severity: AnswerIssueSeverity, message: String): String =
            "${severity.name}:${message.trim().replace(Regex("\\s+"), " ")}"
    }
}
```

- [ ] **Step 5: Add open/resolved answer helpers**

Change `QuestionAnswer.kt` to:

```kotlin
package org.munika.assignment.domain.model

/**
 * One delegate's raw answer to one imported MyMUN question.
 *
 * Typed interpretation and warnings are delegated to the question configuration.
 */
class QuestionAnswer(
    override val id: QuestionAnswerId,
    val delegate: Delegate,
    val question: Question,
    val rawAnswer: String?,
) : DomainObject<QuestionAnswerId> {
    val issues: List<AnswerIssue>
        get() = question.issuesFor(rawAnswer)

    val openIssues: List<AnswerIssue>
        get() = issues.filterNot { issue -> delegate.resolutionFor(question.id, issue) != null }

    val resolvedIssues: List<ResolvedAnswerIssue>
        get() = issues.mapNotNull { issue ->
            delegate.resolutionFor(question.id, issue)?.let { resolution ->
                ResolvedAnswerIssue(issue, resolution)
            }
        }
}
```

Create `ResolvedAnswerIssue` in `AnswerIssueResolution.kt` below `AnswerIssueResolution`:

```kotlin
data class ResolvedAnswerIssue(
    val issue: AnswerIssue,
    val resolution: AnswerIssueResolution,
)
```

- [ ] **Step 6: Add delegate-owned resolution helpers**

Add to the `Delegate` constructor:

```kotlin
val issueResolutions: MutableList<AnswerIssueResolution> = mutableListOf(),
```

Add these methods to `Delegate`:

```kotlin
fun resolutionFor(questionId: QuestionId?, issue: AnswerIssue): AnswerIssueResolution? =
    issueResolutions.firstOrNull { resolution ->
        resolution.questionId == questionId && resolution.issueKey == issue.key
    }

fun openProfileIssues(today: LocalDate = LocalDate.now()): List<AnswerIssue> =
    profileIssues(today).filterNot { issue -> resolutionFor(questionId = null, issue) != null }

fun resolvedProfileIssues(today: LocalDate = LocalDate.now()): List<ResolvedAnswerIssue> =
    profileIssues(today).mapNotNull { issue ->
        resolutionFor(questionId = null, issue)?.let { resolution -> ResolvedAnswerIssue(issue, resolution) }
    }
```

- [ ] **Step 7: Run domain tests**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :domain:test --tests org.munika.assignment.domain.model.QuestionTest --stacktrace
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add domain/src/main/kotlin/org/munika/assignment/domain/model domain/src/test/kotlin/org/munika/assignment/domain/model/QuestionTest.kt
git commit -m "feat: model answer issue resolutions"
```

---

### Task 2: Persist Resolutions

**Files:**
- Create: `data/src/main/kotlin/org/munika/assignment/data/db/tables/AnswerIssueResolutionTable.kt`
- Create: `data/src/main/kotlin/org/munika/assignment/data/db/model/AnswerIssueResolutionRow.kt`
- Modify: `data/src/main/kotlin/org/munika/assignment/data/db/tables/MunAssignTables.kt`
- Modify: `data/src/main/kotlin/org/munika/assignment/data/db/DatabaseFactory.kt`
- Modify: `data/src/main/kotlin/org/munika/assignment/data/db/model/ConferenceGraphRows.kt`
- Modify: `data/src/main/kotlin/org/munika/assignment/data/db/mapper/ConferenceGraphMapper.kt`
- Modify: `domain/src/main/kotlin/org/munika/assignment/domain/repository/ConferenceRepository.kt`
- Modify: `data/src/main/kotlin/org/munika/assignment/data/db/repository/ConferenceRepositoryImpl.kt`
- Test: `data/src/test/kotlin/org/munika/assignment/data/db/repository/ConferenceRepositoryImplTest.kt`

- [ ] **Step 1: Write failing repository test**

Add this test to `ConferenceRepositoryImplTest`:

```kotlin
@Test
fun `answer issue resolution persists and survives repeated update for retained delegate and question`() = runBlocking {
    val dbPath = Files.createTempDirectory("munassign-issue-resolution").resolve("data.db")
    val database = DatabaseFactory.connect(dbPath)
    val repository = ConferenceRepositoryImpl(database)
    val conference = repository.createConference("TestConf")

    assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithQuestion()).success)
    val loaded = requireNotNull(repository.getConference(conference.id.value))
    val delegate = loaded.delegates.single()
    val question = loaded.questions.single()
    val issue = delegate.answers.single().issues.single()

    val resolved = repository.resolveAnswerIssue(
        conferenceId = conference.id.value,
        delegateId = delegate.id.value,
        questionId = question.id.value,
        issueKey = issue.key,
        justification = "Reviewed and acceptable.",
    )

    assertTrue(resolved.delegates.single().answers.single().openIssues.isEmpty())
    assertEquals(
        "Reviewed and acceptable.",
        resolved.delegates.single().answers.single().resolvedIssues.single().resolution.justification,
    )

    assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithQuestion()).success)
    val afterUpdate = requireNotNull(repository.getConference(conference.id.value))
    assertTrue(afterUpdate.delegates.single().answers.single().openIssues.isEmpty())
    assertEquals(
        "Reviewed and acceptable.",
        afterUpdate.delegates.single().answers.single().resolvedIssues.single().resolution.justification,
    )
}
```

- [ ] **Step 2: Run data test and confirm failure**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :data:test --tests org.munika.assignment.data.db.repository.ConferenceRepositoryImplTest --stacktrace
```

Expected: compile failure for missing repository method and table/model wiring.

- [ ] **Step 3: Create table and row DTO**

Create `AnswerIssueResolutionTable.kt`:

```kotlin
package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object AnswerIssueResolutionTable : Table("answer_issue_resolutions") {
    val id = varchar("id", 36)
    val conferenceId = varchar("conference_id", 36).references(ConferenceTable.id, onDelete = ReferenceOption.CASCADE)
    val delegateId = varchar("delegate_id", 36).references(DelegateTable.id, onDelete = ReferenceOption.CASCADE)
    val questionId = varchar("question_id", 36).nullable().references(DelegateQuestionTable.id, onDelete = ReferenceOption.CASCADE)
    val issueKey = varchar("issue_key", 512)
    val issueMessage = text("issue_message")
    val severity = varchar("severity", 16)
    val justification = text("justification").nullable()
    val resolvedAtEpochMillis = long("resolved_at_epoch_millis")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("ux_answer_issue_resolution", conferenceId, delegateId, questionId, issueKey)
    }
}
```

Create `AnswerIssueResolutionRow.kt`:

```kotlin
package org.munika.assignment.data.db.model

data class AnswerIssueResolutionRow(
    val id: String,
    val conferenceId: String,
    val delegateId: String,
    val questionId: String?,
    val issueKey: String,
    val issueMessage: String,
    val severity: String,
    val justification: String?,
    val resolvedAtEpochMillis: Long,
)
```

- [ ] **Step 4: Register schema**

Add `AnswerIssueResolutionTable` to `MunAssignTables` after `DelegateQuestionAnswerTable`.

Add migration version 6 in `DatabaseFactory`:

```kotlin
SchemaMigration(version = 6, name = "add_answer_issue_resolutions") {
    SchemaUtils.create(AnswerIssueResolutionTable)
}
```

Add the import:

```kotlin
import org.munika.assignment.data.db.tables.AnswerIssueResolutionTable
```

- [ ] **Step 5: Add rows to graph mapper**

Add to `ConferenceGraphRows`:

```kotlin
val issueResolutions: List<AnswerIssueResolutionRow>,
```

Update every `ConferenceGraphRows(...)` construction in `ConferenceRepositoryImpl` to pass `issueResolutions = emptyList()` for list-only graphs and loaded rows for full graphs.

In `ConferenceGraphMapper`, import resolution types and after comments are attached, add:

```kotlin
rows.issueResolutions.forEach { row ->
    val delegate = requireNotNull(delegatesById[row.delegateId]) {
        "Issue resolution ${row.id} references missing delegate ${row.delegateId}."
    }
    delegate.issueResolutions += AnswerIssueResolution(
        id = AnswerIssueResolutionId(row.id),
        delegate = delegate,
        questionId = row.questionId?.let(::QuestionId),
        issueKey = row.issueKey,
        issueMessage = row.issueMessage,
        severity = AnswerIssueSeverity.valueOf(row.severity),
        justification = row.justification,
        resolvedAt = row.resolvedAtEpochMillis.toInstant(),
    )
}
```

- [ ] **Step 6: Add repository contract**

Add to `ConferenceRepository`:

```kotlin
/**
 * Marks one currently derived delegate answer or profile issue as resolved.
 *
 * Passing `null` for [questionId] resolves a profile-level issue. Blank justification is stored
 * as absent text.
 */
suspend fun resolveAnswerIssue(
    conferenceId: String,
    delegateId: String,
    questionId: String?,
    issueKey: String,
    justification: String?,
): Conference
```

- [ ] **Step 7: Implement repository persistence**

In `ConferenceRepositoryImpl`, import:

```kotlin
import org.munika.assignment.data.db.model.AnswerIssueResolutionRow
import org.munika.assignment.data.db.tables.AnswerIssueResolutionTable
import org.munika.assignment.domain.model.AnswerIssueSeverity
```

Add `resolveAnswerIssue`:

```kotlin
override suspend fun resolveAnswerIssue(
    conferenceId: String,
    delegateId: String,
    questionId: String?,
    issueKey: String,
    justification: String?,
): Conference = db {
    val conference = loadConferenceGraph(conferenceId)
    val delegate = requireNotNull(conference.delegates.firstOrNull { it.id.value == delegateId }) {
        "Delegate $delegateId does not belong to conference $conferenceId."
    }
    val currentIssue = if (questionId == null) {
        delegate.profileIssues().firstOrNull { it.key == issueKey }
    } else {
        delegate.answers
            .firstOrNull { it.question.id.value == questionId }
            ?.issues
            ?.firstOrNull { it.key == issueKey }
    }
    requireNotNull(currentIssue) { "Issue is no longer present for this delegate." }

    val trimmedJustification = justification?.trim()?.takeIf { it.isNotBlank() }
    val now = Instant.now().toEpochMilli()
    val existing = AnswerIssueResolutionTable.selectAll().where {
        (AnswerIssueResolutionTable.conferenceId eq conferenceId) and
            (AnswerIssueResolutionTable.delegateId eq delegateId) and
            (AnswerIssueResolutionTable.issueKey eq issueKey)
    }.firstOrNull { it[AnswerIssueResolutionTable.questionId] == questionId }

    if (existing == null) {
        AnswerIssueResolutionTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[AnswerIssueResolutionTable.conferenceId] = conferenceId
            it[AnswerIssueResolutionTable.delegateId] = delegateId
            it[AnswerIssueResolutionTable.questionId] = questionId
            it[AnswerIssueResolutionTable.issueKey] = issueKey
            it[issueMessage] = currentIssue.message
            it[severity] = currentIssue.severity.name
            it[AnswerIssueResolutionTable.justification] = trimmedJustification
            it[resolvedAtEpochMillis] = now
        }
    } else {
        AnswerIssueResolutionTable.update({ AnswerIssueResolutionTable.id eq existing[AnswerIssueResolutionTable.id] }) {
            it[issueMessage] = currentIssue.message
            it[severity] = currentIssue.severity.name
            it[AnswerIssueResolutionTable.justification] = trimmedJustification
            it[resolvedAtEpochMillis] = now
        }
    }
    ConferenceTable.update({ ConferenceTable.id eq conferenceId }) {
        it[updatedAtEpochMillis] = now
    }
    loadConferenceGraph(conferenceId)
}
```

Add resolution loading inside `loadConferenceGraph`:

```kotlin
issueResolutions = AnswerIssueResolutionTable.selectAll().where {
    AnswerIssueResolutionTable.conferenceId eq conferenceId
}.map { row ->
    AnswerIssueResolutionRow(
        id = row[AnswerIssueResolutionTable.id],
        conferenceId = row[AnswerIssueResolutionTable.conferenceId],
        delegateId = row[AnswerIssueResolutionTable.delegateId],
        questionId = row[AnswerIssueResolutionTable.questionId],
        issueKey = row[AnswerIssueResolutionTable.issueKey],
        issueMessage = row[AnswerIssueResolutionTable.issueMessage],
        severity = row[AnswerIssueResolutionTable.severity],
        justification = row[AnswerIssueResolutionTable.justification],
        resolvedAtEpochMillis = row[AnswerIssueResolutionTable.resolvedAtEpochMillis],
    )
},
```

- [ ] **Step 8: Run data tests**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :data:test --tests org.munika.assignment.data.db.repository.ConferenceRepositoryImplTest --stacktrace
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add domain/src/main/kotlin/org/munika/assignment/domain/repository/ConferenceRepository.kt data/src/main/kotlin/org/munika/assignment/data/db data/src/test/kotlin/org/munika/assignment/data/db/repository/ConferenceRepositoryImplTest.kt
git commit -m "feat: persist resolved answer issues"
```

---

### Task 3: Notice Manager UI Models

**Files:**
- Create: `app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/NoticeManagerUiModels.kt`
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/DelegateListUiModels.kt`
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/DelegateDetailUiModels.kt`
- Test: `app/src/jvmTest/kotlin/org/munika/assignment/ui/viewmodel/NoticeManagerUiModelsTest.kt`
- Test: `app/src/jvmTest/kotlin/org/munika/assignment/ui/viewmodel/DelegateListUiModelsTest.kt`
- Test: `app/src/jvmTest/kotlin/org/munika/assignment/ui/viewmodel/DelegateDetailUiModelsTest.kt`

- [ ] **Step 1: Write failing notice manager model test**

Create `NoticeManagerUiModelsTest.kt`:

```kotlin
package org.munika.assignment.ui.viewmodel

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.ConferenceId
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.DelegateId
import org.munika.assignment.domain.model.DelegateProfile
import org.munika.assignment.domain.model.NoticeWhenNonBlank
import org.munika.assignment.domain.model.PaymentStatus
import org.munika.assignment.domain.model.PersonName
import org.munika.assignment.domain.model.Question
import org.munika.assignment.domain.model.QuestionAnswer
import org.munika.assignment.domain.model.QuestionAnswerId
import org.munika.assignment.domain.model.QuestionId

class NoticeManagerUiModelsTest {
    @Test
    fun `notice manager counts open issues and orders resolved last`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val delegate = Delegate(
            id = DelegateId("delegate"),
            conference = conference,
            name = PersonName("Alex", "Kim"),
            email = null,
            profile = DelegateProfile(null, null, null, null, null, null, null, null, null, null),
            paymentStatus = PaymentStatus.Paid,
            isHeadDelegate = false,
        )
        val first = Question(QuestionId("first"), "First", rules = listOf(NoticeWhenNonBlank("First notice")))
        val second = Question(QuestionId("second"), "Second", rules = listOf(NoticeWhenNonBlank("Second notice")))
        conference.delegates += delegate
        conference.questions += listOf(first, second)
        delegate.answers += QuestionAnswer(QuestionAnswerId("answer-1"), delegate, first, "Yes")
        delegate.answers += QuestionAnswer(QuestionAnswerId("answer-2"), delegate, second, "Yes")
        val resolvedIssue = delegate.answers.first().issues.single()
        delegate.issueResolutions += org.munika.assignment.domain.model.AnswerIssueResolution(
            id = org.munika.assignment.domain.model.AnswerIssueResolutionId("resolution"),
            delegate = delegate,
            questionId = first.id,
            issueKey = resolvedIssue.key,
            issueMessage = resolvedIssue.message,
            severity = resolvedIssue.severity,
            justification = "Done",
            resolvedAt = Instant.EPOCH,
        )

        val manager = conference.toNoticeManagerUiState()

        assertEquals(1, manager.openNoticeCount)
        assertEquals(0, manager.openWarningCount)
        assertEquals(listOf(false, true), manager.notices.map { it.resolved })
        assertEquals(listOf("Second notice", "First notice"), manager.notices.map { it.message })
        assertEquals("Done", manager.notices.last().justification)
    }
}
```

- [ ] **Step 2: Write failing list/detail expectations**

In `DelegateListUiModelsTest`, add a resolved issue to `contested` in `board()` after its warning answer is added:

```kotlin
val contestedIssue = contested.answers.single { it.id.value == "contested-warning" }.issues.single()
contested.issueResolutions += org.munika.assignment.domain.model.AnswerIssueResolution(
    id = org.munika.assignment.domain.model.AnswerIssueResolutionId("contested-resolution"),
    delegate = contested,
    questionId = warningQuestion.id,
    issueKey = contestedIssue.key,
    issueMessage = contestedIssue.message,
    severity = contestedIssue.severity,
    justification = "Accepted short answer.",
    resolvedAt = Instant.EPOCH,
)
```

Update expected issue preset rows from:

```kotlin
assertEquals(listOf("Contested Delegate", "Pending Delegate"), issues.rows.map { it.name })
```

to:

```kotlin
assertEquals(listOf("Pending Delegate"), issues.rows.map { it.name })
```

In `DelegateDetailUiModelsTest`, resolve the visa issue in the first test and assert `resolvedAnswerIssueCount`.

- [ ] **Step 3: Run app model tests and confirm failure**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :app:jvmTest --tests org.munika.assignment.ui.viewmodel.* --stacktrace
```

Expected: compile failure for missing notice manager types and resolved detail fields.

- [ ] **Step 4: Create notice manager UI models**

Create `NoticeManagerUiModels.kt`:

```kotlin
package org.munika.assignment.ui.viewmodel

import org.munika.assignment.domain.model.AnswerIssueSeverity
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.QuestionAnswer
import org.munika.assignment.domain.model.ResolvedAnswerIssue

data class NoticeManagerUiState(
    val openWarningCount: Int,
    val openNoticeCount: Int,
    val notices: List<NoticeUiState>,
)

data class NoticeUiState(
    val delegateId: String,
    val delegateName: String,
    val questionId: String?,
    val questionLabel: String,
    val answer: String,
    val issueKey: String,
    val message: String,
    val severity: String,
    val isWarning: Boolean,
    val resolved: Boolean,
    val justification: String?,
)

fun Conference.toNoticeManagerUiState(delegateId: String? = null, resolvedOnly: Boolean = false): NoticeManagerUiState {
    val notices = delegates
        .filter { delegateId == null || it.id.value == delegateId }
        .flatMap { delegate -> delegate.noticeRows() }
        .filter { !resolvedOnly || it.resolved }
        .sortedWith(compareBy<NoticeUiState> { it.resolved }.thenByDescending { it.isWarning }.thenBy { it.delegateName }.thenBy { it.questionLabel })
    return NoticeManagerUiState(
        openWarningCount = notices.count { !it.resolved && it.isWarning },
        openNoticeCount = notices.count { !it.resolved && !it.isWarning },
        notices = notices,
    )
}

private fun Delegate.noticeRows(): List<NoticeUiState> =
    answers.flatMap { answer -> answer.openRows() + answer.resolvedRows() } +
        openProfileIssues().map { issue ->
            NoticeUiState(id.value, displayName, null, "Profile checks", "", issue.key, issue.message, issue.severity.name, issue.severity == AnswerIssueSeverity.Warning, false, null)
        } +
        resolvedProfileIssues().map { resolved ->
            NoticeUiState(id.value, displayName, null, "Profile checks", "", resolved.issue.key, resolved.issue.message, resolved.issue.severity.name, resolved.issue.severity == AnswerIssueSeverity.Warning, true, resolved.resolution.justification)
        }

private fun QuestionAnswer.openRows(): List<NoticeUiState> =
    openIssues.map { issue ->
        NoticeUiState(
            delegateId = delegate.id.value,
            delegateName = delegate.displayName,
            questionId = question.id.value,
            questionLabel = question.displayLabel,
            answer = rawAnswer?.takeIf { it.isNotBlank() } ?: "No answer",
            issueKey = issue.key,
            message = issue.message,
            severity = issue.severity.name,
            isWarning = issue.severity == AnswerIssueSeverity.Warning,
            resolved = false,
            justification = null,
        )
    }

private fun QuestionAnswer.resolvedRows(): List<NoticeUiState> =
    resolvedIssues.map { resolved: ResolvedAnswerIssue ->
        NoticeUiState(
            delegateId = delegate.id.value,
            delegateName = delegate.displayName,
            questionId = question.id.value,
            questionLabel = question.displayLabel,
            answer = rawAnswer?.takeIf { it.isNotBlank() } ?: "No answer",
            issueKey = resolved.issue.key,
            message = resolved.issue.message,
            severity = resolved.issue.severity.name,
            isWarning = resolved.issue.severity == AnswerIssueSeverity.Warning,
            resolved = true,
            justification = resolved.resolution.justification,
        )
    }
```

- [ ] **Step 5: Update list counts to use open issues**

In `DelegateListUiModels.kt`, change `warningCount`, `noticeCount`, and `issueCount` helpers to use `answer.openIssues` and `openProfileIssues(today)`.

- [ ] **Step 6: Update detail models**

In `DelegateDetailUiModels.kt`, extend:

```kotlin
data class DelegateAnswerUiState(
    val label: String,
    val answer: String,
    val issues: List<DelegateAnswerIssueUiState>,
    val resolvedIssues: List<DelegateResolvedIssueUiState>,
)

data class DelegateResolvedIssueUiState(
    val message: String,
    val severity: String,
    val justification: String?,
)
```

Add to `DelegateDetailUiState`:

```kotlin
val resolvedAnswerIssueCount: Int,
```

Map `answer.issues` from `answer.openIssues`, map `answer.resolvedIssues`, map `profileIssues` from `delegate.openProfileIssues()`, and compute `resolvedAnswerIssueCount` from answer resolved issues plus `delegate.resolvedProfileIssues().size`.

- [ ] **Step 7: Run app model tests**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :app:jvmTest --tests org.munika.assignment.ui.viewmodel.* --stacktrace
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel app/src/jvmTest/kotlin/org/munika/assignment/ui/viewmodel
git commit -m "feat: build notice manager ui state"
```

---

### Task 4: ViewModel State And Actions

**Files:**
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/MainViewModel.kt`
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/App.kt`

- [ ] **Step 1: Add state fields**

Add to `MainUiState`:

```kotlin
val noticeManagerOpen: Boolean = false,
val noticeManagerDelegateId: String? = null,
val noticeManagerResolvedOnly: Boolean = false,
```

- [ ] **Step 2: Add manager actions**

Add to `MainViewModel`:

```kotlin
fun openNoticeManager(): Unit {
    _state.update {
        it.copy(
            noticeManagerOpen = true,
            noticeManagerDelegateId = null,
            noticeManagerResolvedOnly = false,
        )
    }
}

fun openDelegateNoticeManager(delegateId: String, resolvedOnly: Boolean): Unit {
    _state.update {
        it.copy(
            noticeManagerOpen = true,
            noticeManagerDelegateId = delegateId,
            noticeManagerResolvedOnly = resolvedOnly,
        )
    }
}

fun closeNoticeManager(): Unit {
    _state.update {
        it.copy(
            noticeManagerOpen = false,
            noticeManagerDelegateId = null,
            noticeManagerResolvedOnly = false,
        )
    }
}

fun resolveAnswerIssue(
    delegateId: String,
    questionId: String?,
    issueKey: String,
    justification: String?,
) {
    val selectedId = selectedIdOrNull() ?: return
    scope.launch {
        try {
            val conference = repository.resolveAnswerIssue(selectedId, delegateId, questionId, issueKey, justification)
            _state.update { it.copy(selectedConference = conference, boardActionError = null) }
        } catch (exception: IllegalArgumentException) {
            _state.update { it.copy(boardActionError = exception.message ?: "Notice could not be resolved.") }
        }
    }
}
```

- [ ] **Step 3: Wire App callbacks**

Pass these through `App.kt` to `MainScreen`:

```kotlin
onOpenNoticeManager = viewModel::openNoticeManager,
onOpenDelegateNoticeManager = viewModel::openDelegateNoticeManager,
onCloseNoticeManager = viewModel::closeNoticeManager,
onResolveAnswerIssue = viewModel::resolveAnswerIssue,
```

- [ ] **Step 4: Compile app**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :app:compileKotlinJvm --stacktrace
```

Expected: compile errors in `MainScreen` until Task 5 adds parameters.

Do not commit this task alone unless Task 5 is completed in the same working batch.

---

### Task 5: Bell Icon And Notice Manager Dialog

**Files:**
- Create: `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/dialog/NoticeManagerDialog.kt`
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/MainScreen.kt`
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/component/DelegateSidePanel.kt`

- [ ] **Step 1: Create notice manager dialog**

Create `NoticeManagerDialog.kt`:

```kotlin
package org.munika.assignment.ui.screen.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.munika.assignment.ui.viewmodel.NoticeManagerUiState
import org.munika.assignment.ui.viewmodel.NoticeUiState

@Composable
fun NoticeManagerDialog(
    manager: NoticeManagerUiState,
    onDismiss: () -> Unit,
    onResolve: (delegateId: String, questionId: String?, issueKey: String, justification: String?) -> Unit,
) {
    var resolveTarget by remember { mutableStateOf<NoticeUiState?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notices") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(manager.notices, key = { "${it.delegateId}:${it.questionId}:${it.issueKey}:${it.resolved}" }) { notice ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (notice.resolved) 0.42f else 0.78f))) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(notice.severity, fontWeight = FontWeight.SemiBold)
                                Text(notice.delegateName)
                                if (notice.resolved) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = "Resolved")
                                }
                            }
                            Text(notice.questionLabel, style = MaterialTheme.typography.labelMedium)
                            Text(notice.message)
                            if (notice.answer.isNotBlank()) Text(notice.answer, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            notice.justification?.let { Text("Resolution: $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (!notice.resolved) {
                                TextButton(onClick = { resolveTarget = notice }) {
                                    Text("Resolve")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        },
    )

    resolveTarget?.let { target ->
        ResolveNoticeDialog(
            notice = target,
            onDismiss = { resolveTarget = null },
            onConfirm = { justification ->
                onResolve(target.delegateId, target.questionId, target.issueKey, justification)
                resolveTarget = null
            },
        )
    }
}

@Composable
private fun ResolveNoticeDialog(
    notice: NoticeUiState,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var justification by remember(notice.issueKey) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resolve notice") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(notice.message)
                OutlinedTextField(
                    value = justification,
                    onValueChange = { justification = it },
                    label = { Text("Justification") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(justification) }) {
                Text("Resolve")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
```

- [ ] **Step 2: Add bell icon in top bar**

In `MainScreen.kt`, import `Icons.Filled.Notifications` and `NoticeManagerDialog`.

Add new `MainScreen` parameters:

```kotlin
onOpenNoticeManager: () -> Unit,
onOpenDelegateNoticeManager: (delegateId: String, resolvedOnly: Boolean) -> Unit,
onCloseNoticeManager: () -> Unit,
onResolveAnswerIssue: (delegateId: String, questionId: String?, issueKey: String, justification: String?) -> Unit,
```

Thread them through `TopBar` and `ConferenceWorkspace`.

In `TopBar`, before the delegate list button, compute:

```kotlin
val noticeManager = state.selectedConference?.toNoticeManagerUiState()
val openIssueCount = (noticeManager?.openWarningCount ?: 0) + (noticeManager?.openNoticeCount ?: 0)
```

Add a tooltip icon button:

```kotlin
TooltipIconButton(
    tooltip = "Open notices ($openIssueCount open)",
    icon = Icons.Filled.Notifications,
    onClick = onOpenNoticeManager,
    enabled = !state.loading && state.selectedConference != null,
    tint = if (openIssueCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
)
```

Render the number as adjacent text:

```kotlin
if (openIssueCount > 0) {
    Text(openIssueCount.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
}
```

- [ ] **Step 3: Render notice manager**

At the bottom of `MainScreen`, add:

```kotlin
if (state.noticeManagerOpen && selectedConference != null) {
    NoticeManagerDialog(
        manager = selectedConference.toNoticeManagerUiState(
            delegateId = state.noticeManagerDelegateId,
            resolvedOnly = state.noticeManagerResolvedOnly,
        ),
        onDismiss = onCloseNoticeManager,
        onResolve = onResolveAnswerIssue,
    )
}
```

- [ ] **Step 4: Update delegate side panel**

Add `onOpenNotices: (delegateId: String, resolvedOnly: Boolean) -> Unit` to `DelegateSidePanel`.

In the Answers foldable header, after open warning/notice chips, add:

```kotlin
if (detail.resolvedAnswerIssueCount > 0) {
    TextButton(onClick = { onOpenNotices(detail.id, true) }) {
        Text("${detail.resolvedAnswerIssueCount} resolved")
    }
}
```

In `AnswersContent`, render resolved issue text under each answer:

```kotlin
answer.resolvedIssues.forEach { issue ->
    Text(
        issue.justification?.let { "Resolved: $it" } ?: "Resolved ${issue.severity.lowercase()}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
```

Keep open `answer.issues` using `AnswerIssuePill`, so resolved issues no longer get warning/notice styling.

- [ ] **Step 5: Compile app**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :app:compileKotlinJvm --stacktrace
```

Expected: PASS.

- [ ] **Step 6: Run app model tests**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :app:jvmTest --tests org.munika.assignment.ui.viewmodel.* --stacktrace
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/jvmMain/kotlin/org/munika/assignment app/src/jvmTest/kotlin/org/munika/assignment/ui/viewmodel
git commit -m "feat: add notice manager UI"
```

---

### Task 6: Documentation And Full Verification

**Files:**
- Modify: `docs/DATA_MODEL.md`
- Modify: `docs/TODO.md`

- [ ] **Step 1: Update data model docs**

In `docs/DATA_MODEL.md`, replace the current answer issue paragraph with:

```markdown
Answer issues are derived from current profile/question rules and are either:

- `Warning`, meaning the answer may justify follow-up or affect assignment;
- `Notice`, meaning the answer needs operational attention but is not a rejection problem.

Resolved answer issues are organizer-managed state. A resolution stores the delegate, optional question, stable issue key, original issue text/severity, resolution time, and optional justification. Open issue counters and warning markers ignore resolved issues. The notice manager shows open issues first and resolved issues last. MyMUN updates preserve resolutions where the delegate and question still exist; cascade deletion removes resolutions for deleted conferences, delegates, or questions.
```

In the table list, add:

```markdown
- `answer_issue_resolutions`
```

- [ ] **Step 2: Update TODO**

In `docs/TODO.md`, remove:

```markdown
- Warning and issue handling to mark them as resolved with justification, also global warning counter.
```

If implementation deliberately left any UI polish outside scope, replace it with a narrower item such as:

```markdown
- Polish notice manager keyboard/focus behavior after regular use.
```

- [ ] **Step 3: Run focused module tests**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :domain:test :data:test :app:jvmTest --stacktrace
```

Expected: PASS.

- [ ] **Step 4: Run full check**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew test --stacktrace
```

Expected: PASS.

- [ ] **Step 5: Run diff hygiene check**

Run:

```powershell
git diff --check
```

Expected: no output.

- [ ] **Step 6: Commit**

```bash
git add docs/DATA_MODEL.md docs/TODO.md
git commit -m "docs: document resolved notice handling"
```

---

## Self-Review

- Spec coverage:
  - Global bell counter: Task 5 adds a notifications icon in the top bar, fed by `toNoticeManagerUiState()` open warning/notice counts.
  - Pressing the bell shows all notices: Task 5 opens `NoticeManagerDialog` with all selected-conference notices.
  - Resolved notices disappear from warning locations: Tasks 1 and 3 split open vs resolved issues, and list/detail counters use open issues.
  - Delegate overview shows resolved count and opens filtered manager: Task 5 adds the resolved count button with delegate/resolved filter.
  - Manager always shows all with resolved last: Task 3 sorting and Task 5 dialog render use the flattened manager model.
  - Resolve with justification: Tasks 2 and 5 persist and display optional justification.
  - Resolved question/answer still shown without marking: Task 5 renders neutral resolution text under the question/answer instead of warning pills.
- Placeholder scan:
  - No implementation step contains "TBD", "fill in details", or generic "add tests" without code and commands.
- Type consistency:
  - `AnswerIssueResolution`, `ResolvedAnswerIssue`, `NoticeManagerUiState`, `NoticeUiState`, `resolveAnswerIssue`, and `toNoticeManagerUiState` are defined before later tasks reference them.
