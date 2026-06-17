# IPC Mapped Committees Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add explicit IPC mapped committee handling, where a mapped committee's positions choose a target normal committee and inherit that target's high-school/university assignment guidance.

**Architecture:** Replace the binary committee high-school flag with a `CommitteeMode` enum while preserving `isHighSchoolCommittee` as a compatibility derived property. Store per-position mapped committee IDs in persistence, hydrate them after all committees/positions exist, and drive UI colors, sorting, mismatch counts, and capacity stats from each position's effective school level.

**Tech Stack:** Kotlin Multiplatform, Compose Desktop, Exposed SQLite, Gradle tests with Temurin 24 JDK.

---

### Task 1: Domain Model For Committee Modes And Mapped Positions

**Files:**
- Create: `domain/src/main/kotlin/org/munika/assignment/domain/model/CommitteeMode.kt`
- Modify: `domain/src/main/kotlin/org/munika/assignment/domain/model/Committee.kt`
- Modify: `domain/src/main/kotlin/org/munika/assignment/domain/model/Position.kt`
- Test: `domain/src/test/kotlin/org/munika/assignment/domain/model/PositionTest.kt`

- [ ] **Step 1: Write failing domain tests**

Add `PositionTest.kt` with tests that construct normal HS, normal Uni, mapped, and unmapped positions:

```kotlin
package org.munika.assignment.domain.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PositionTest {
    @Test
    fun `normal position inherits owning committee school level`() {
        val conference = conference()
        val highSchool = committee(conference, "hs", CommitteeMode.HighSchool)
        val university = committee(conference, "uni", CommitteeMode.University)
        val hsPosition = Position(PositionId("hs-position"), highSchool, "France")
        val uniPosition = Position(PositionId("uni-position"), university, "Germany")

        assertEquals(CommitteeMode.HighSchool, hsPosition.effectiveCommitteeMode)
        assertEquals(CommitteeMode.University, uniPosition.effectiveCommitteeMode)
        assertTrue(hsPosition.isEffectivelyHighSchool)
        assertFalse(uniPosition.isEffectivelyHighSchool)
    }

    @Test
    fun `mapped position inherits mapped target school level`() {
        val conference = conference()
        val ipc = committee(conference, "ipc", CommitteeMode.Mapped)
        val target = committee(conference, "target", CommitteeMode.HighSchool)
        val position = Position(PositionId("ipc-position"), ipc, "UNSC", mappedCommittee = target)

        assertEquals(CommitteeMode.HighSchool, position.effectiveCommitteeMode)
        assertTrue(position.isEffectivelyHighSchool)
        assertFalse(position.mappingIncomplete)
    }

    @Test
    fun `unmapped mapped position has incomplete school level`() {
        val conference = conference()
        val ipc = committee(conference, "ipc", CommitteeMode.Mapped)
        val position = Position(PositionId("ipc-position"), ipc, "UNSC")

        assertNull(position.effectiveCommitteeMode)
        assertNull(position.isEffectivelyHighSchool)
        assertTrue(position.mappingIncomplete)
    }

    private fun conference(): Conference =
        Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    private fun committee(conference: Conference, id: String, mode: CommitteeMode): Committee =
        Committee(CommitteeId(id), conference, id.uppercase(), slotsPerPosition = 1, mode = mode).also {
            conference.committees += it
        }
}
```

- [ ] **Step 2: Run domain test and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :domain:test --tests org.munika.assignment.domain.model.PositionTest --stacktrace
```

Expected: compile failure for missing `CommitteeMode`, `mode`, `mappedCommittee`, and effective-mode properties.

- [ ] **Step 3: Implement minimal domain model**

Create `CommitteeMode.kt`:

```kotlin
package org.munika.assignment.domain.model

/**
 * Organizer-managed assignment guidance for a committee.
 *
 * `Mapped` is used by IPC-style committees whose positions inherit their school level from
 * another non-mapped committee.
 */
enum class CommitteeMode {
    HighSchool,
    University,
    Mapped,
}
```

Update `Committee` constructor to accept `mode: CommitteeMode = CommitteeMode.University`, keep `isHighSchoolCommittee` as a derived compatibility property, and reject blank names/invalid slot counts as before.

Update `Position` constructor to accept `var mappedCommittee: Committee? = null`, and add:

```kotlin
val effectiveCommitteeMode: CommitteeMode?
    get() = when (committee.mode) {
        CommitteeMode.HighSchool -> CommitteeMode.HighSchool
        CommitteeMode.University -> CommitteeMode.University
        CommitteeMode.Mapped -> mappedCommittee?.mode?.takeIf { it != CommitteeMode.Mapped }
    }

val isEffectivelyHighSchool: Boolean?
    get() = effectiveCommitteeMode?.let { it == CommitteeMode.HighSchool }

val mappingIncomplete: Boolean
    get() = committee.mode == CommitteeMode.Mapped && mappedCommittee == null
```

- [ ] **Step 4: Run domain test and verify GREEN**

Run the same `:domain:test --tests org.munika.assignment.domain.model.PositionTest --stacktrace` command.

Expected: PASS.

### Task 2: Persistence Schema, Mapping, And Repository Operations

**Files:**
- Modify: `data/src/main/kotlin/org/munika/assignment/data/db/tables/CommitteeTable.kt`
- Modify: `data/src/main/kotlin/org/munika/assignment/data/db/tables/PositionTable.kt`
- Modify: `data/src/main/kotlin/org/munika/assignment/data/db/DatabaseFactory.kt`
- Modify: `data/src/main/kotlin/org/munika/assignment/data/db/model/CommitteeRow.kt`
- Modify: `data/src/main/kotlin/org/munika/assignment/data/db/model/PositionRow.kt`
- Modify: `data/src/main/kotlin/org/munika/assignment/data/db/mapper/ConferenceGraphMapper.kt`
- Modify: `domain/src/main/kotlin/org/munika/assignment/domain/repository/ConferenceRepository.kt`
- Modify: `data/src/main/kotlin/org/munika/assignment/data/db/repository/ConferenceRepositoryImpl.kt`
- Test: `data/src/test/kotlin/org/munika/assignment/data/db/repository/ConferenceRepositoryImplTest.kt`
- Test: `data/src/test/kotlin/org/munika/assignment/data/db/mapper/ConferenceGraphMapperTest.kt`

- [ ] **Step 1: Write failing repository tests**

Add tests to `ConferenceRepositoryImplTest`:

```kotlin
@Test
fun `committee mode and position mapped committee are persisted`() = runBlocking {
    val dbPath = Files.createTempDirectory("munassign-ipc-persist").resolve("data.db")
    val database = DatabaseFactory.connect(dbPath)
    val repository = ConferenceRepositoryImpl(database)
    val conference = repository.createConference("TestConf")
    assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithTwoCommittees()).success)
    val loaded = requireNotNull(repository.getConference(conference.id.value))
    val ipc = loaded.committees.single { it.mymunName == "IPC" }
    val target = loaded.committees.single { it.mymunName == "UNSC" }

    repository.setCommitteeMode(conference.id.value, target.id.value, CommitteeMode.HighSchool)
    repository.setCommitteeMode(conference.id.value, ipc.id.value, CommitteeMode.Mapped)
    val mapped = repository.setPositionMappedCommittee(
        conferenceId = conference.id.value,
        positionId = ipc.positions.single().id.value,
        mappedCommitteeId = target.id.value,
    )

    val mappedPosition = mapped.committees.single { it.mymunName == "IPC" }.positions.single()
    assertEquals(CommitteeMode.Mapped, mappedPosition.committee.mode)
    assertEquals(target.id, mappedPosition.mappedCommittee?.id)
    assertEquals(CommitteeMode.HighSchool, mappedPosition.effectiveCommitteeMode)

    val reloaded = requireNotNull(repository.getConference(conference.id.value))
    assertEquals(CommitteeMode.Mapped, reloaded.committees.single { it.mymunName == "IPC" }.mode)
    assertEquals(
        reloaded.committees.single { it.mymunName == "UNSC" }.id,
        reloaded.committees.single { it.mymunName == "IPC" }.positions.single().mappedCommittee?.id,
    )
}

@Test
fun `position mapping rejects mapped target and normal owning committee`() = runBlocking {
    val dbPath = Files.createTempDirectory("munassign-ipc-reject").resolve("data.db")
    val database = DatabaseFactory.connect(dbPath)
    val repository = ConferenceRepositoryImpl(database)
    val conference = repository.createConference("TestConf")
    assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithTwoCommittees()).success)
    val loaded = requireNotNull(repository.getConference(conference.id.value))
    val ipc = loaded.committees.single { it.mymunName == "IPC" }
    val target = loaded.committees.single { it.mymunName == "UNSC" }

    assertFailsWith<IllegalArgumentException> {
        repository.setPositionMappedCommittee(conference.id.value, target.positions.single().id.value, ipc.id.value)
    }

    repository.setCommitteeMode(conference.id.value, ipc.id.value, CommitteeMode.Mapped)
    repository.setCommitteeMode(conference.id.value, target.id.value, CommitteeMode.Mapped)

    assertFailsWith<IllegalArgumentException> {
        repository.setPositionMappedCommittee(conference.id.value, ipc.positions.single().id.value, target.id.value)
    }
}

@Test
fun `switching mapped committee back to normal clears position mappings`() = runBlocking {
    val dbPath = Files.createTempDirectory("munassign-ipc-clear").resolve("data.db")
    val database = DatabaseFactory.connect(dbPath)
    val repository = ConferenceRepositoryImpl(database)
    val conference = repository.createConference("TestConf")
    assertTrue(repository.applyMymunUpdateSnapshot(conference.id.value, snapshotWithTwoCommittees()).success)
    val loaded = requireNotNull(repository.getConference(conference.id.value))
    val ipc = loaded.committees.single { it.mymunName == "IPC" }
    val target = loaded.committees.single { it.mymunName == "UNSC" }
    repository.setCommitteeMode(conference.id.value, target.id.value, CommitteeMode.HighSchool)
    repository.setCommitteeMode(conference.id.value, ipc.id.value, CommitteeMode.Mapped)
    repository.setPositionMappedCommittee(conference.id.value, ipc.positions.single().id.value, target.id.value)

    val updated = repository.setCommitteeMode(conference.id.value, ipc.id.value, CommitteeMode.University)

    assertNull(updated.committees.single { it.mymunName == "IPC" }.positions.single().mappedCommittee)
}
```

Add helper:

```kotlin
private fun snapshotWithTwoCommittees(): MymunWorkbookSnapshot =
    MymunWorkbookSnapshot(
        workbookName = "ipc.xlsx",
        delegates = listOf(delegate()),
        countryPositions = listOf(
            MymunCountryPositionRow(2, "IPC", "UNSC Reporter", null, null),
            MymunCountryPositionRow(3, "UNSC", "France", null, null),
        ),
        delegations = emptyList(),
        questionColumns = emptyList(),
        questionAnswers = emptyList(),
        preferenceCandidates = emptyList(),
        confirmedAssignmentCandidates = emptyList(),
        issues = emptyList(),
    )
```

- [ ] **Step 2: Run repository tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :data:test --tests org.munika.assignment.data.db.repository.ConferenceRepositoryImplTest --stacktrace
```

Expected: compile failure for missing `CommitteeMode`, repository methods, and table/row fields.

- [ ] **Step 3: Implement schema and mapper**

Add `CommitteeTable.mode = varchar("mode", 32).default(CommitteeMode.University.name)`.

Add `PositionTable.mappedCommitteeId = varchar("mapped_committee_id", 36).nullable().references(CommitteeTable.id, onDelete = ReferenceOption.SET_NULL)`.

Add migration version 8 in `DatabaseFactory`:

```kotlin
SchemaMigration(version = 8, name = "add_committee_mode_and_position_mapping") {
    addColumnIfMissing("committees", "mode", "VARCHAR(32) NOT NULL DEFAULT 'University'")
    addColumnIfMissing("positions", "mapped_committee_id", "VARCHAR(36) NULL REFERENCES committees(id) ON DELETE SET NULL")
    TransactionManager.current().exec(
        "UPDATE committees SET mode = CASE WHEN is_high_school_committee THEN 'HighSchool' ELSE 'University' END WHERE mode IS NULL OR mode = 'University'",
    )
}
```

Update `CommitteeRow` with `mode: String`; update `PositionRow` with `mappedCommitteeId: String?`.

In `ConferenceGraphMapper`, construct `Committee(mode = CommitteeMode.valueOf(row.mode))`. Construct all positions first, then loop over `rows.positions` and set `position.mappedCommittee = row.mappedCommitteeId?.let { committeesById[it] }`.

- [ ] **Step 4: Implement repository API**

In `ConferenceRepository`, add KDoc and methods:

```kotlin
suspend fun setCommitteeMode(conferenceId: String, committeeId: String, mode: CommitteeMode): Conference

suspend fun setPositionMappedCommittee(
    conferenceId: String,
    positionId: String,
    mappedCommitteeId: String?,
): Conference
```

Keep `setCommitteeHighSchoolStatus` as a compatibility method that delegates to `setCommitteeMode` in implementations.

In `ConferenceRepositoryImpl`:

- Write `setCommitteeMode` to validate ownership.
- Reject switching a committee to `Mapped` if any `PositionTable.mappedCommitteeId` points to it.
- Update `CommitteeTable.mode` and `CommitteeTable.isHighSchoolCommittee`.
- If mode is not `Mapped`, clear `PositionTable.mappedCommitteeId` for that committee's positions.
- Write `setPositionMappedCommittee` to validate the position's owning committee is mapped, validate target committee belongs to the same conference and is not mapped, then update `PositionTable.mappedCommitteeId`.
- When inserting committees during MyMUN update, set mode to `University` and old boolean to `false`.
- When updating retained committees, preserve mode.
- When inserting/updating positions, preserve existing mapped IDs for retained positions.
- After pruning update rows, clear position mappings whose target is missing or now mapped.
- Update `loadGraphRows` to read new fields.

- [ ] **Step 5: Run repository tests and verify GREEN**

Run the same `:data:test --tests org.munika.assignment.data.db.repository.ConferenceRepositoryImplTest --stacktrace` command.

Expected: PASS.

### Task 3: Assignment Board UI Models And Capacity Stats

**Files:**
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/AssignmentBoardUiModels.kt`
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/MainScreen.kt`
- Test: `app/src/jvmTest/kotlin/org/munika/assignment/ui/viewmodel/AssignmentBoardUiModelsTest.kt`
- Test: `app/src/jvmTest/kotlin/org/munika/assignment/ui/screen/ConferenceStatsTest.kt`

- [ ] **Step 1: Write failing UI model tests**

Add tests asserting:

```kotlin
@Test
fun `mapped committee exposes no reservation reminder and position mapping options`() {
    val board = mappedBoard()

    val ipc = board.conference.toAssignmentBoardUiState().committees.single { it.name == "IPC" }

    assertEquals(CommitteeMode.Mapped, ipc.mode)
    assertNull(ipc.reservationReminder)
    assertEquals(listOf("UNSC"), ipc.positions.single().mappingOptions.map { it.name })
    assertEquals("UNSC", ipc.positions.single().mappedCommittee?.name)
    assertEquals(CommitteeMode.HighSchool, ipc.positions.single().effectiveMode)
}

@Test
fun `mapped position candidate sorting uses mapped target mode`() {
    val board = mappedBoard()
    markHighSchooler(board.available)

    val candidates = board.conference.toAssignmentBoardUiState()
        .committees.single { it.name == "IPC" }
        .positions.single()
        .assignmentCandidates

    assertEquals(listOf("Available Delegate", "Neutral Delegate"), candidates.map { it.name })
}
```

Add a `mappedBoard()` helper that creates `IPC` as `CommitteeMode.Mapped`, `UNSC` as `CommitteeMode.HighSchool`, maps the IPC position to UNSC, and reuses existing delegate helpers.

In `ConferenceStatsTest`, add a mapped capacity test:

```kotlin
@Test
fun `acceptance capacity counts mapped slots by mapped target mode`() {
    val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
    val target = committee(conference, "target", CommitteeMode.HighSchool, positionCount = 1)
    val ipc = committee(conference, "ipc", CommitteeMode.Mapped, positionCount = 1)
    ipc.positions.single().mappedCommittee = target
    conference.committees += listOf(target, ipc)

    val capacity = conference.toAcceptanceCapacityUiState()

    assertEquals(2, capacity.highSchoolCount)
    assertEquals(0, capacity.universityCount)
}
```

- [ ] **Step 2: Run app UI tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :app:jvmTest --tests org.munika.assignment.ui.viewmodel.AssignmentBoardUiModelsTest --tests org.munika.assignment.ui.screen.ConferenceStatsTest --stacktrace
```

Expected: compile failures for missing UI fields and old helper signatures.

- [ ] **Step 3: Implement UI model fields and logic**

In `CommitteeBoardUiState`, replace or supplement `isHighSchoolCommittee` with `mode: CommitteeMode`, `modeLabel: String`, and `isMapped: Boolean`. Keep `isHighSchoolCommittee` if needed for UI compatibility.

Add:

```kotlin
data class CommitteeMappingOptionUiState(val id: String, val name: String, val mode: CommitteeMode)
data class PositionMappedCommitteeUiState(val id: String, val name: String, val mode: CommitteeMode)
```

In `PositionBoardUiState`, add `effectiveMode`, `mappingIncomplete`, `mappedCommittee`, and `mappingOptions`.

Build mapping options from `conference.committees.filter { it.mode != CommitteeMode.Mapped }`.

Change candidate sorting to call `position.effectiveCommitteeMode` instead of `committee.isHighSchoolCommittee`; neutral sort rank when mode is null or mapped.

Change committee mismatch counts to count per position effective mode.

In `MainScreen.toAcceptanceCapacityUiState`, count slots by `slot.position.effectiveCommitteeMode`.

- [ ] **Step 4: Run app UI tests and verify GREEN**

Run the same `:app:jvmTest` command.

Expected: PASS.

### Task 4: Compose Controls, ViewModel Wiring, Docs, And Verification

**Files:**
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/App.kt`
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/MainViewModel.kt`
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/MainScreen.kt`
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/component/AssignmentBoard.kt`
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/component/CommitteeColumn.kt`
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/component/PositionCard.kt`
- Modify: `app/src/jvmTest/kotlin/org/munika/assignment/ui/viewmodel/MainViewModelTest.kt`
- Modify: `docs/DESIGN.md`
- Modify: `docs/DATA_MODEL.md`
- Modify: `docs/TODO.md`

- [ ] **Step 1: Write failing ViewModel wiring test**

In `MainViewModelTest`, update the fake repository to implement `setCommitteeMode` and `setPositionMappedCommittee`, then add a test that calls the new ViewModel methods and asserts repository calls are made.

- [ ] **Step 2: Run ViewModel test and verify RED**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :app:jvmTest --tests org.munika.assignment.ui.viewmodel.MainViewModelTest --stacktrace
```

Expected: compile failure for missing ViewModel methods and callback parameters.

- [ ] **Step 3: Implement ViewModel and callback wiring**

Add `MainViewModel.setCommitteeMode(committeeId: String, mode: CommitteeMode)` and `MainViewModel.setPositionMappedCommittee(positionId: String, mappedCommitteeId: String?)`. Preserve `setCommitteeHighSchoolStatus` as a compatibility wrapper if app call sites still need it during the transition.

Thread callbacks through `App.kt`, `MainScreen.kt`, `AssignmentBoard.kt`, `CommitteeColumn.kt`, and `PositionCard.kt`.

In `CommitteeColumn`, replace the school icon toggle with a compact mode selector showing `HS`, `Uni`, and `Mapped`.

In `PositionCard`, show a compact dropdown for mapped positions only. Use existing Material3 dropdown patterns from `MainScreen.kt`; call `onSetPositionMappedCommittee(position.id, option.id)` when selected and `null` for clear.

- [ ] **Step 4: Run ViewModel test and verify GREEN**

Run the same `MainViewModelTest` command.

Expected: PASS.

- [ ] **Step 5: Update docs and TODO**

Update:

- `docs/DESIGN.md`: assignment board workflow includes mapped committees and per-position correspondence dropdowns.
- `docs/DATA_MODEL.md`: committee mode, position `mapped_committee_id`, MyMUN preservation, and constraints.
- `docs/TODO.md`: remove the completed IPC handling item.

- [ ] **Step 6: Run full verification**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :domain:test :data:test :app:jvmTest --stacktrace
git diff --check
```

Expected: all tests PASS and `git diff --check` has no output.
