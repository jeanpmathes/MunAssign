# Code Quality Pass 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove every current project-source compiler warning and perform a bounded review of the highest-risk domain, persistence, update, and UI-state boundaries.

**Architecture:** Preserve the existing domain/data/app boundaries and public behavior. Replace deprecated APIs locally, keep JDBC work on `Dispatchers.IO`, and use existing architectural rules plus focused tests as the authority for any clear defect found during the review; ambiguous behavior is reported to the user before code changes.

**Tech Stack:** Kotlin 2.3.20, Compose Multiplatform 1.10.3, Material 3 1.10.0-alpha05, Exposed JDBC 1.0.0-rc-2, kotlinx.coroutines 1.10.2, JUnit 4, Gradle.

---

### Task 1: Replace the deprecated Exposed suspended JDBC transaction helper

**Files:**
- Modify: `data/src/main/kotlin/org/munika/assignment/data/db/repository/ConferenceRepositoryImpl.kt:7-20,1755-1757`
- Test: `data/src/test/kotlin/org/munika/assignment/data/db/repository/ConferenceRepositoryImplTest.kt`

- [ ] **Step 1: Reproduce the warning while running repository regression tests**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :data:test --warning-mode all --rerun-tasks
```

Expected: tests pass and compilation reports `newSuspendedTransaction` as deprecated at `ConferenceRepositoryImpl.kt:1756`.

- [ ] **Step 2: Replace the deprecated helper with an IO-dispatched synchronous JDBC transaction**

In `ConferenceRepositoryImpl.kt`, replace:

```kotlin
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
```

with:

```kotlin
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
```

Replace the database helper with:

```kotlin
private suspend fun <T> db(block: () -> T): T =
    withContext(Dispatchers.IO) {
        transaction(database) { block() }
    }
```

The repository contract remains suspending, blocking JDBC stays on the IO dispatcher, and the transaction lambda becomes non-suspending because every call site performs synchronous Exposed work only.

- [ ] **Step 3: Run the repository regression tests and confirm the warning is gone**

Run the command from Step 1.

Expected: `BUILD SUCCESSFUL`; all data tests pass; no `newSuspendedTransaction` warning appears.

- [ ] **Step 4: Commit the JDBC warning fix**

```powershell
git add data/src/main/kotlin/org/munika/assignment/data/db/repository/ConferenceRepositoryImpl.kt
git commit -m "Replace deprecated JDBC transaction helper"
```

### Task 2: Migrate deprecated Compose dropdown, tooltip, and directional icon APIs

**Files:**
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/MainScreen.kt`
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/component/BoardTooltip.kt`
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/component/DelegateListPanel.kt`
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/dialog/NoticeManagerDialog.kt`
- Modify: `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/dialog/ReservationManagerDialog.kt`
- Test: `app/src/jvmTest/kotlin/org/munika/assignment/ui/screen/ConferenceStatsTest.kt`
- Test: `app/src/jvmTest/kotlin/org/munika/assignment/ui/viewmodel/MainViewModelTest.kt`

- [ ] **Step 1: Reproduce all thirteen Compose warnings**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :app:jvmTest --warning-mode all --rerun-tasks
```

Expected: tests pass and compilation reports three `menuAnchor()` warnings, eight `rememberPlainTooltipPositionProvider()` warnings, and two non-auto-mirrored icon warnings.

- [ ] **Step 2: Migrate read-only dropdown anchors**

Import `androidx.compose.material3.ExposedDropdownMenuAnchorType` in `MainScreen.kt`, `DelegateListPanel.kt`, and `ReservationManagerDialog.kt`.

Use the read-only anchor type at all three sites:

```kotlin
Modifier.menuAnchor(
    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
    enabled = enabled,
)
```

Use the existing local `enabled` parameter in `ConferenceDropdown`. In `SortDropdown` and `DelegationDropdown`, where the controls are always enabled, pass `enabled = true`. Preserve each modifier's existing `width(...)` or `fillMaxWidth()` call after `menuAnchor(...)`.

- [ ] **Step 3: Migrate plain tooltip position providers without changing placement**

Import `androidx.compose.material3.TooltipAnchorPosition` wherever needed. Replace each call to:

```kotlin
TooltipDefaults.rememberPlainTooltipPositionProvider()
```

with:

```kotlin
TooltipDefaults.rememberTooltipPositionProvider(
    positioning = TooltipAnchorPosition.Above,
)
```

Apply this replacement to the five sites in `MainScreen.kt`, the single shared site in `BoardTooltip.kt`, and the two sites in `NoticeManagerDialog.kt`. `Above` matches the deprecated provider's documented placement behavior.

- [ ] **Step 4: Use auto-mirrored directional icons**

In `NoticeManagerDialog.kt`, add:

```kotlin
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
```

Replace the two icon references with:

```kotlin
Icons.AutoMirrored.Filled.ArrowForward
Icons.AutoMirrored.Filled.Undo
```

Remove the obsolete `filled.ArrowForward` and `filled.Undo` imports.

- [ ] **Step 5: Run app tests and confirm all Compose warnings are gone**

Run the command from Step 1.

Expected: `BUILD SUCCESSFUL`; all app tests pass; none of the thirteen deprecated Compose API warnings appear.

- [ ] **Step 6: Commit the Compose warning fixes**

```powershell
git add app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/MainScreen.kt app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/component/BoardTooltip.kt app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/component/DelegateListPanel.kt app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/dialog/NoticeManagerDialog.kt app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/dialog/ReservationManagerDialog.kt
git commit -m "Migrate deprecated Compose APIs"
```

### Task 3: Review domain invariants and derived state

**Files:**
- Review: `domain/src/main/kotlin/org/munika/assignment/domain/model/Conference.kt`
- Review: `domain/src/main/kotlin/org/munika/assignment/domain/model/Committee.kt`
- Review: `domain/src/main/kotlin/org/munika/assignment/domain/model/Position.kt`
- Review: `domain/src/main/kotlin/org/munika/assignment/domain/model/Slot.kt`
- Review: `domain/src/main/kotlin/org/munika/assignment/domain/model/Delegate.kt`
- Review: `domain/src/main/kotlin/org/munika/assignment/domain/model/Reservation.kt`
- Review: `domain/src/main/kotlin/org/munika/assignment/domain/model/ReservationReconciler.kt`
- Review: `domain/src/main/kotlin/org/munika/assignment/domain/model/Question.kt`
- Review: `domain/src/main/kotlin/org/munika/assignment/domain/model/QuestionConfiguration.kt`
- Test: matching files under `domain/src/test/kotlin/org/munika/assignment/domain/model/`

- [ ] **Step 1: Check the documented invariants against their owning concepts**

For each reviewed type, verify constructor validation, mutation ownership, nullability, derived slot/assignment state, mapped-committee rules, reservation capacity, question configuration, and answer-issue derivation against `docs/DESIGN.md` and `docs/DATA_MODEL.md`. Search for duplicated derived facts and state represented by blank strings, sentinel values, or unchecked nullable references.

- [ ] **Step 2: Classify every concrete finding before editing**

Use exactly one classification:

- Clear defect: documentation or an existing invariant determines one expected result. Add a focused failing test beside the owning domain type, run that test to confirm the failure, make the smallest fix, and rerun the focused test plus `:domain:test`.
- Behavior ambiguity: two or more product behaviors are reasonable. Stop and ask the user; add the specific question to `docs/FAQ.md` only if it cannot be answered during the session.
- Style-only issue: fix only when local and behavior-neutral; otherwise defer it to the exhaustive pass.

- [ ] **Step 3: Verify and commit any domain fixes as one coherent batch**

Run:

```powershell
.\gradlew :domain:test --warning-mode all --rerun-tasks
```

Expected: `BUILD SUCCESSFUL` with no project-source warnings. If files changed, stage only the reviewed domain files and their focused tests, then commit with message `Fix domain quality findings`. If no clear defect exists, make no commit.

### Task 4: Review MyMUN update and persistence boundaries

**Files:**
- Review: `data/src/main/kotlin/org/munika/assignment/data/excel/MyMunWorkbookParser.kt`
- Review: `data/src/main/kotlin/org/munika/assignment/data/excel/MymunWorkbookUpdater.kt`
- Review: `data/src/main/kotlin/org/munika/assignment/data/excel/MymunWorkbookSnapshot.kt`
- Review: `data/src/main/kotlin/org/munika/assignment/data/db/repository/ConferenceRepositoryImpl.kt`
- Review: `data/src/main/kotlin/org/munika/assignment/data/db/mapper/ConferenceGraphMapper.kt`
- Review: `data/src/main/kotlin/org/munika/assignment/data/db/DatabaseFactory.kt`
- Test: `data/src/test/kotlin/org/munika/assignment/data/excel/MyMunWorkbookParserTest.kt`
- Test: `data/src/test/kotlin/org/munika/assignment/data/db/repository/ConferenceRepositoryImplTest.kt`
- Test: `data/src/test/kotlin/org/munika/assignment/data/db/mapper/ConferenceGraphMapperTest.kt`

- [ ] **Step 1: Trace update data from workbook cells through committed graph state**

Check that blocking staging issues prevent writes, normalized matching follows the documented discriminator order, confirmed assignments cannot exceed or duplicate slots, pending state is preserved only when valid, organizer-managed state survives retained rows, transaction boundaries cover each mutation atomically, and row mapping neither drops nor invents relationships.

- [ ] **Step 2: Handle findings with the same clear-defect gate**

For a clear defect, first add a focused parser, mapper, or repository test that demonstrates the documented expected result; run it alone and observe failure; implement the smallest correction; rerun the focused test and full `:data:test`. Ask the user before changing any ambiguous matching, reconciliation, deletion, or update behavior.

- [ ] **Step 3: Verify and commit any data fixes as one coherent batch**

Run:

```powershell
.\gradlew :data:test --warning-mode all --rerun-tasks
```

Expected: `BUILD SUCCESSFUL` with no project-source warnings. If files changed, stage only the affected data files and focused tests, then commit with message `Fix data quality findings`. If no clear defect exists, make no commit.

### Task 5: Review ViewModel state transitions and Compose interaction wiring

**Files:**
- Review: `app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/MainViewModel.kt`
- Review: `app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/AssignmentBoardUiModels.kt`
- Review: `app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/DelegateDetailUiModels.kt`
- Review: `app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/DelegateListUiModels.kt`
- Review: `app/src/jvmMain/kotlin/org/munika/assignment/ui/viewmodel/ReservationUiModels.kt`
- Review: `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/MainScreen.kt`
- Review: `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/ConferenceScreen.kt`
- Review: `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/component/AssignmentBoard.kt`
- Review: `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/component/CommitteeColumn.kt`
- Review: `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/component/PositionCard.kt`
- Review: `app/src/jvmMain/kotlin/org/munika/assignment/ui/screen/component/SlotRow.kt`
- Test: corresponding files under `app/src/jvmTest/kotlin/org/munika/assignment/ui/`

- [ ] **Step 1: Trace user actions through state, repository calls, and rendered controls**

Check coroutine ownership and cancellation, loading/error cleanup, stale selection after conference changes, one-shot event handling, pending versus confirmed assignment protections, control enablement, stable Compose state keys, and whether visible actions invoke the intended ViewModel mutation exactly once.

- [ ] **Step 2: Handle findings with the same clear-defect gate**

For a clear state defect, add a deterministic ViewModel or pure UI-model test before implementation, verify it fails, make the smallest fix, and rerun that test plus `:app:jvmTest`. For a purely visual or interaction behavior with more than one reasonable result, describe the observed behavior and ask the user before changing it.

- [ ] **Step 3: Verify and commit any app fixes as one coherent batch**

Run:

```powershell
.\gradlew :app:jvmTest --warning-mode all --rerun-tasks
```

Expected: `BUILD SUCCESSFUL` with no project-source warnings. If files changed, stage only the affected app files and focused tests, then commit with message `Fix app quality findings`. If no clear defect exists, make no commit.

### Task 6: Close Pass 1 with documentation and clean verification

**Files:**
- Modify: `docs/TODO.md`
- Review: all files changed since the approved design commit

- [ ] **Step 1: Remove the completed warning-cleanup item**

Delete this line from `docs/TODO.md` only after the forced all-module compilation emits no project-source warnings:

```markdown
- Fix all warnings of code.
```

- [ ] **Step 2: Force a clean all-module test run**

Run:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :domain:test :data:test :app:jvmTest --warning-mode all --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`; all three test suites pass; no Kotlin warning points to project source. The informational `Type-safe project accessors is an incubating feature` message is not a project-source compiler warning.

- [ ] **Step 3: Check patch hygiene**

Run:

```powershell
git diff --check
git status --short
git diff --stat a6d22df..HEAD
```

Expected: `git diff --check` has no output; status contains only intentional work, if any; the diff is limited to warning migrations, proven fixes with tests, and `docs/TODO.md`.

- [ ] **Step 4: Commit Pass 1 documentation**

```powershell
git add docs/TODO.md
git commit -m "Complete layered code quality pass"
```

- [ ] **Step 5: Report the handoff boundary**

Report the warning count before and after, tests run, clear defects fixed, ambiguous behaviors left unchanged, and any FAQ questions. State explicitly that the exhaustive Pass 2 audit has not yet started.
