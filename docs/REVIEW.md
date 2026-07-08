# Project Review (2026-07-08)

Full review of the MunAssign codebase as the project nears completion. All three modules were
read (domain, data, app), all tests were run fresh (`:domain:test`, `:data:test`, `:app:jvmTest` —
all pass), and the bundled sample workbook in `samples/` was inspected to validate parser
assumptions against real MyMUN export data.

Overall the project is in good shape: the layered architecture is followed consistently, MyMUN
parsing stages and validates before writing, updates are transactional, `.munassign` transfer
remaps IDs correctly, the markdown exporter escapes table cells, and the migration system is
sound. The findings below are ordered by severity. Line numbers refer to the current `main`
(commit `ee8a513`).

## High

### H1. Update matching can merge two different people who share a name

`ConferenceRepositoryImpl.resolveExistingDelegate` (ConferenceRepositoryImpl.kt:1267) returns the
single same-name match immediately, without ever checking email:

```kotlin
val sameName = existingDelegates.filter { ... }
if (sameName.size == 1) return sameName.single()
```

If the database contains exactly one "John Smith" and a later workbook contains **two** John
Smiths (a second one registered between updates — the parser's `duplicate_delegate` check does not
fire because their full identities differ), **both workbook rows resolve to the same stored
delegate**. Both rows then write preferences/answers under one delegate ID:

- If both rows have preferences, the unique index `ux_preferences_delegate_rank` is violated →
  the whole update fails with a raw SQL exception instead of a validation message.
- If not, the rows silently overwrite each other: one real person disappears, and the surviving
  record is a merge of two people (last row wins for profile, both rows' answers attached).

Duplicate names are common at real conferences. Suggested fix: when several *workbook* rows map to
the same existing delegate (or the same matching identity tier), demand the email/zip/DOB
discriminators before accepting a match, and emit a blocking `ambiguous_delegate_match` issue when
they cannot disambiguate. A cheap safety net: after building `delegateIdsByRow`, verify the IDs are
unique and abort with a proper validation error otherwise.

### H2. "Checked In" delegates become unusable everywhere

Eligibility is hard-coded to `status == "Accepted" || "Assigned"` in five separately duplicated
helpers:

- `ConferenceRepositoryImpl.isAcceptedOrAssigned` (ConferenceRepositoryImpl.kt:2217) — manual
  assignment is **rejected** for anyone else (ConferenceRepositoryImpl.kt:356);
- `ReservationReconciler` (ReservationReconciler.kt:123) — reservation auto-links;
- `AssignmentBoardUiModels.kt:269` — board candidates and interested delegates;
- `DelegationOverviewUiModels.kt:95` — accepted counts;
- `MainScreen.hasActionableApplicationStatus` (MainScreen.kt:1321) — top-bar stats and capacity.

The sample workbook contains delegates with status **"Checked In"**, which in the MyMUN pipeline
comes *after* Assigned (confirmed by the project owner). Loading an update once check-in has begun
makes those delegates vanish from assignment views, releases their automatic reservation
consumption, inflates the acceptance-capacity stat, and blocks manual assignment for them.

Suggested fix: introduce one domain-level predicate (e.g. `ApplicationStatus.isUsable`) that
accepts Accepted, Assigned, and Checked In, and use it from all five call sites. This also removes
the five-way duplication, which is itself a divergence risk.

### H3. Board/list UI models are rebuilt at full cost on every recomposition

`ConferenceContent` calls `conference.toAssignmentBoardUiState()` and
`conference.toDelegateListUiState(...)` directly in composition (MainScreen.kt:908, 924) with no
`remember`/`derivedStateOf`. The cost of one rebuild is roughly
`positions × delegates × slots`:

- `Position.toBoardUiState()` builds `assignmentCandidates` from **all** conference delegates for
  **every position** (AssignmentBoardUiModels.kt, `toBoardUiState`);
- each candidate calls `pendingAssignmentLabel()`/`confirmedAssignmentLabel()`, and
  `Delegate.currentSlot` (Delegate.kt) linearly scans every slot in the conference;
- `warningCount()`/`noticeCount()` re-evaluate every answer rule for every delegate, again per
  position;
- `Position.interestedDelegates` (Position.kt) scans all delegates × their preferences, per
  position, and `delegateDifficultyScores()` (DelegateListUiModels.kt) is quadratic in delegates.

With a few hundred delegates and slots this is tens of millions of operations per recomposition —
and recomposition happens on every keystroke in the delegate-list search field. This directly
threatens the open "stress tests with many people" TODO.

Suggested fixes (in increasing order of impact):
1. `remember(conference) { conference.toAssignmentBoardUiState() }` and
   `remember(conference, delegateListControls) { ... }` in `MainScreen`.
2. Build a `delegateId → slot` map once per conversion instead of `Delegate.currentSlot` scans.
3. Compute per-delegate issue counts and labels once and share them across positions.

## Medium

### M1. A pending assignment is dropped when MyMUN confirms someone else at the same slot index

During an update, confirmed assignments are written to a fixed `(position, slotIndex)` derived
from the position's order in the `Countries.Delegates` cell
(ConferenceRepositoryImpl.kt:1165–1177). `clearMymunAssignments` clears only MyMUN-sourced slots,
so a *pending* delegate sitting at that exact index is silently overwritten even when another slot
of the same position is free. Example: organizer pends delegate A into slot 1 of an empty
two-slot position; MyMUN later confirms delegate B for that position; the update writes B to
slot 1 and A's pending assignment is lost although slot 2 was open. DATA_MODEL.md promises pending
assignments are preserved "where possible when MyMUN does not contradict them" — here it is
contradicted only by slot arithmetic. Suggested fix: when writing a confirmed assignment, prefer
an empty/Mymun slot of the position, or relocate a displaced pending delegate to a free slot of
the same position before overwriting.

### M2. Reservation "remaining" counts can go negative and inflate capacity

`Reservation.remainingHighSchoolCount/remainingUniversityCount` (Reservation.kt) are unclamped,
and usage is bucketed by the delegate's *current* `isHighSchooler` value. A manual link created
while a delegate mapped as high-school keeps consuming after an update or mapping change flips
them to university, so `usedUniversityCount` can exceed `universityCount`, making the remaining
count negative. `Conference.remainingUniversityReservationCount` (Conference.kt:56) sums these,
so one negative reservation *cancels out* another reservation's real remaining capacity, and the
top-bar acceptance capacity (MainScreen.kt:672) silently increases. Suggested fix: clamp remaining
values at zero for aggregation/display, and/or have reconciliation validate manual links after
bucket changes and surface the overflow.

### M3. Deleted delegates leave slots with a dangling `assignment_source`

`SlotTable.assignedDelegateId` uses `ON DELETE SET NULL` but nothing clears `assignment_source`
(SlotTable.kt). When an update prunes a delegate that held a pending assignment, the slot ends up
with `assigned_delegate_id = NULL, assignment_source = 'Application'`. The domain mapper treats
the slot as empty, but `countPendingAssignments` (ConferenceRepositoryImpl.kt:1452) counts by
source only, so the `pendingAssignmentsPreserved` summary over-counts, and inconsistent rows
accumulate. Suggested fix: clear both columns together (either explicitly before pruning or by
counting only slots with both fields set).

### M4. Duplicate question headers crash the update with a raw SQL error

The parser derives question columns from headers without de-duplicating by normalized text
(MyMunWorkbookParser.kt:118), and the update decides insert-vs-update against a *pre-update*
snapshot of existing questions (ConferenceRepositoryImpl.kt:1117–1148). A workbook with two
`Question: ...` headers that normalize to the same text (duplicated column, or case/whitespace
variants) inserts two rows and violates `ux_delegate_questions_conf_normalized_text`, aborting
the whole update with an unfriendly constraint-violation message. Suggested fix: detect duplicate
normalized question headers at parse time and emit a blocking validation issue.

### M5. Several ViewModel actions swallow or miss failures

- `createConference`, `renameConference`, `deleteConference`, `reorderConferences`
  (MainViewModel.kt:136, 163, 179, 213) have no try/catch: any repository exception kills the
  coroutine silently and the UI shows nothing.
- Comment and issue-resolution actions (MainViewModel.kt:641–726) catch only
  `IllegalArgumentException`; an `SQLException`/`IllegalStateException` is unhandled.
- `updateQuestionConfigurations` (MainViewModel.kt:754) applies configurations with one repository
  call per question plus a reorder call — not atomic. A mid-sequence failure leaves half the
  configurations saved while the dialog reports an error.

Suggested fix: a small `runAction { ... }` helper that wraps every mutation with consistent error
mapping, and a repository-level batch operation for question configuration saves.

### M6. Date parsing accepts only ISO local dates or native Excel dates

`Cell.dateValue` (MyMunWorkbookParser.kt:387) falls back to `LocalDate.parse` (strict
`yyyy-MM-dd`); anything else becomes a **blocking** `invalid_date` error. The sample export uses
ISO strings, so this works today, but a MyMUN format change (e.g. `2025-12-05 14:30` or a locale
format) would hard-block all updates. Also, time-of-day is discarded, so reservation auto-link
ordering among same-day submissions degrades to alphabetical order. Consider tolerating a datetime
suffix and keeping the time component for `submittedAt`.

### M7. Transfer/versioning robustness

- `CURRENT_SCHEMA_VERSION = 9` (DatabaseFactory.kt:29) is maintained by hand and only used to
  reject newer `.munassign` files. Adding migration 10 without bumping it silently breaks the
  check. Derive it: `migrations.maxOf { it.version }`.
- Imports/exports open a second raw JDBC connection against the live database
  (ConferenceTransferService.kt:62) with no busy timeout/WAL configured; a concurrent write (e.g.
  the startup markdown export) can produce an `SQLITE_BUSY` failure. The transaction rolls back
  safely, but the user sees "not a valid MunAssign transfer file" because the generic catch wraps
  every unexpected exception into `TransferException` with that message (ConferenceTransferService.kt:74).
- Each import/export calls `DatabaseFactory.connect(tempPath)`, registering a new Exposed
  `Database` that is never closed/unregistered — a slow leak over many transfers.
- `insertRow` interpolates column names taken from the imported file into SQL
  (ConferenceTransferService.kt:378). Column names come from a file the user chose, so a crafted
  `.munassign` can inject SQL into the local database. The desktop threat model makes this low
  risk, but quoting identifiers is cheap.

### M8. Root `test` task does not run the app module's tests

`gradlew test` executes only `:domain:test` and `:data:test`; the app module is Kotlin
Multiplatform, so its tests live under `:app:jvmTest`, which the aggregate `test` task name does
not match. Anyone (or any future CI) running `gradlew test` gets a green build without the ~40
ViewModel/UI-model tests. Suggested fix: document `gradlew check`, or wire
`tasks.named("test") { dependsOn("jvmTest") }` equivalents.

## Low

- **L1. Dead placeholder composables.** `ui/screen/ConferenceScreen.kt` and
  `ui/screen/component/TopStatsBar.kt` are empty `@Composable fun ...() {}` bodies with no
  references. Delete them (the real UI lives in `MainScreen.kt`).
- **L2. `docs/UI_TEXT_REVIEW.md` is stale.** It states "no Kotlin text has been changed yet", but
  many proposed strings are already applied (`Select update file`, `Update failed: ...`,
  `Textual safety export failed...`). Either mark it done or delete it.
- **L3. Head-delegate detection.** Any non-blank value in `Head Delegate/Faculty Advisor` marks a
  delegate as head delegate (MyMunWorkbookParser.kt:177). Real data contains exactly
  `Head Delegate` or blank — match that value exactly for resilience (owner agreed).
- **L4. Grammar/edge cases in `Delegate.applicationSubmittedText`** (Delegate.kt): produces
  "submitted 1 days ago" and can go negative with clock skew.
- **L5. `Delegation.addDelegate` does not detach from a previous delegation** (Delegation.kt:31),
  and `ReservationDelegateLink`'s constructor mutates `delegate.reservationLink` as a side effect
  (ReservationDelegateLink.kt). Both are latent (the mapper always builds fresh graphs), but they
  make the domain objects unsafe to move/rebuild in memory.
- **L6. Markdown safety exports grow without bound** — two files per conference per session, no
  retention/cleanup. Same for `mymun_update_runs`/`mymun_update_issues` rows. Consider a simple
  retention policy (e.g. keep last N).
- **L7. Shutdown blocks on the markdown export.** `MainViewModel.close()` runs the full export via
  `runBlocking` on the AWT event thread (MainViewModel.kt:1056). With many/large conferences the
  window freezes on close, with no timeout.
- **L8. Startup double work.** `refreshConferences(exportMarkdownOnComplete = true)` loads every
  conference graph for the markdown export and loads the selected conference again;
  `reconcileReservations` also re-loads the full graph inside every mutating repository call that
  then loads it a third time for the return value. Harmless today; relevant to the stress-test
  goal.
- **L9. Repository boundary uses raw `String` IDs** while the domain has typed IDs
  (`ConferenceId`, `DelegateId`, ...). Typed IDs at the `ConferenceRepository` interface would
  prevent swapped-argument bugs at compile time.
- **L10. Rejected drags still call `acceptDrag`** (main.kt:230) — the drop cursor signals "copy"
  even when the overlay shows the drop will be rejected. Cosmetic.
- **L11. Migration 3 interpolates IDs into SQL strings** (DatabaseFactory.kt:119). The IDs are
  self-generated UUIDs so it is safe, but parameter binding would match the rest of the codebase.
- **L12. "Unpaid" is defined differently across surfaces.** The top bar counts unpaid among
  *accepted* applications (MainScreen.kt:621), the markdown export counts all unpaid delegates
  (ConferenceMarkdownExporter.kt), and DESIGN.md just says "unpaid delegates are counted
  separately". Pick one definition and state it in the tooltip.

## Answered during review (no action needed beyond docs)

- **Difficulty sort direction is intentional**: ascending (easiest first) so organizers clear easy
  delegates and are left with only the difficult ones. Worth one sentence in DESIGN.md so a future
  pass does not "fix" it.
- **Mapped-committee invariants** (no chains, one target per position, mode-switch guards) are
  enforced consistently in domain (`Position.setMappedCommittee`), repository
  (`setCommitteeMode`/`setPositionMappedCommittee`), and update cleanup
  (`clearInvalidPositionMappings`). No issues found.
- **`.munassign` export/import** correctly remaps all conference-owned IDs, migrates older files
  via a temp copy, and rejects newer schema versions (subject to M7).

## Test/verification notes

- `:domain:test`, `:data:test`, `:app:jvmTest` all pass (fresh run with `--rerun-tasks`,
  Temurin 24, 2026-07-08).
- Sample workbook (`samples/mymun-2026-04-25-1010-KAMUN 2025-Applications-Export.xlsx`) validated
  parser assumptions: ISO date strings, `Yes`/`No` fee values, `Head Delegate` marker values,
  nested-parentheses preference cells (`G20 (Italy (Giorgia Meloni, Prime Minister))`) all parse
  correctly with the current code; it also surfaced the `Checked In` status behind H2.
