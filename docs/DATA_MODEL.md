# Data Model

This document describes the application data model derived from `docs/MYMUN.md`, `docs/DESIGN.md`, and project decisions recorded in `docs/FAQ.md`.

The model has two concerns:

- Use only the MyMUN workbook data needed for assignment work.
- Keep MyMUN-managed data separate from organizer-managed data so workbook updates do not destroy comments, pending assignments, or UI configuration.

## Workbook Scope

The MyMUN export contains more sheets than MunAssign needs. The app should not model every sheet simply because it exists in the workbook.

| Sheet | Use |
|---|---|
| `Delegates` | Primary source for assignable delegates, payment status, profile fields, questions, preferences, current MyMUN committee, and current MyMUN assigned position. |
| `Countries` | Complete source for committees, positions, and MyMUN-confirmed assignments. Each row is one position, including unassigned positions. |
| `Delegations` | Nice-to-have contextual data for delegate details. Not needed for the first assignment workflow. |
| `Chairs` | Ignore. Chairs are not relevant for assignment. |
| `Faculty Advisors` | Ignore for assignment. Faculty advisors are not relevant except that head delegate/faculty advisor flags on delegates may affect delegate display. |
| `Observers` | Ignore. Observers are not relevant for assignment. |

MyMUN workbook layout and formatting are expected to remain stable. There are no known layout, sheet-name, or preference-format variations to support initially.

The MyMUN workbook action is called an **update**. The word **import** is reserved for `.munassign` conference import/export.

## Core Entities

### Conference

A conference is the top-level data boundary. Updates, assignment work, comments, question handling configuration, and `.munassign` import/export are scoped to one conference.

| Field | Source | Notes |
|---|---|---|
| `id` | App | Stable internal ID. |
| `name` | App/user | Display name. |
| `createdAt` | App | Timestamp. |
| `updatedAt` | App | Timestamp. |
| `lastMymunUpdateAt` | App | Nullable timestamp of the last MyMUN update. |

### Committee

A committee groups positions and owns the slot count for those positions.

| Field | Source | Notes |
|---|---|---|
| `id` | App | Stable internal ID. |
| `conferenceId` | App | Parent conference. |
| `name` | MyMUN `Countries.Committee Name` | Canonical committee name. |
| `abbreviation` | App/derived | Nullable. Can be derived or entered later. |
| `slotsPerPosition` | App | Uniform slot count for every position in the committee. Defaults to 1. |
| `sortOrder` | App | Preserves display order from workbook where possible. |

Slot count changes are committee-level operations. The organizer can raise or lower `slotsPerPosition`, with confirmation both ways because changing slot counts can alter assignment data or board structure.

### Position

A position is one assignable country/role inside a committee. Each row in the MyMUN `Countries` sheet corresponds to one position, and unassigned positions are still present in that sheet.

| Field | Source | Notes |
|---|---|---|
| `id` | App | Stable internal ID. |
| `conferenceId` | App | Parent conference for scoped queries. |
| `committeeId` | App | Parent committee. |
| `name` | MyMUN `Countries.Member Name` | Board-facing position name. |
| `delegationName` | MyMUN `Countries.Delegation Name` | Nullable. Context only. |
| `sortOrder` | App | Preserves workbook row order within committee. |

The pair `committeeId + name` is expected to be unique.

### Slot

A slot is one assignable place within a position. The number of slots is derived from the committee's `slotsPerPosition`.

| Field | Source | Notes |
|---|---|---|
| `id` | App | Stable internal ID. |
| `positionId` | App | Parent position. |
| `slotIndex` | App | 1-based display order inside the position. |
| `status` | App/MyMUN | `Empty`, `Pending`, or `Assigned`. |
| `assignedDelegateId` | App/MyMUN | Nullable. Set for pending or assigned slots. |
| `assignmentSource` | App/MyMUN | `Mymun`, `Preference`, or `Manual`. |
| `updatedAt` | App | Timestamp. |

`Assigned` means confirmed in MyMUN. The app does not confirm assignments through a right-click action. App-created assignments are pending until a later MyMUN update confirms them by showing the assignment in the `Countries.Delegates` column.

The `Countries.Delegates` column is a comma-separated list of delegates assigned to the position in MyMUN. During a MyMUN update:

- If the column contains assigned delegates, those assignments become confirmed `Assigned` slots.
- If a position has no MyMUN assignment, existing pending assignments are preserved where possible.
- If the number of comma-separated assigned delegates for a committee implies more slots than `slotsPerPosition`, the update is aborted before writing data.
- The abort message should name the committee, the current configured slot count, and the slot count implied by the workbook.
- The organizer can then adjust committee slot count and retry the update.

Assignment names in `Countries.Delegates` are delegate names separated by commas. The parser must be tolerant of arbitrary extra spaces, missing spaces, trailing commas, and single-name values without commas.

### Delegate

A delegate is an assignable participant from the `Delegates` sheet.

| Field | Source | Notes |
|---|---|---|
| `id` | App | Stable internal ID. |
| `conferenceId` | App | Parent conference. |
| `firstName` | MyMUN | Required. |
| `lastName` | MyMUN | Required. |
| `displayName` | App/derived | Usually `First Name Last Name`. |
| `email` | MyMUN `Delegates.Email` | Not guaranteed unique. Store normalized lowercase for matching, but do not use as sole key. |
| `applicationStatus` | MyMUN | Raw text. |
| `submittedAt` | MyMUN | Nullable date. |
| `createdAt` | MyMUN | Nullable date. |
| `feePaid` | MyMUN `Fee Paid` | Authoritative. Unpaid delegates are never shown in assignment views. |
| `active` | App | Organizer-managed deactivate flag. Defaults to true. |
| `difficultyScore` | App | Recomputed after preferences update. |
| `comments` | App | Organizer-managed free text. |
| `isHeadDelegate` | MyMUN/App-derived | Head delegates display a crown marker in the delegate overview. |

Delegate matching during updates should use normalized first name + last name first. If names collide, use email as an additional discriminator. If first name, last name, and email still collide, use zip code and date of birth as further discriminators. There is no stable MyMUN ID available.

The delegate overview should show:

- Age, derived from `dateOfBirth`.
- Relative submitted-at text, such as `Application submitted 5 days ago`.
- Head delegate crown marker when `isHeadDelegate` is true.

`createdAt` should be stored for traceability but does not need to be shown in the primary overview.

Profile fields from MyMUN:

| Field | Source | Notes |
|---|---|---|
| `country` | MyMUN | Nullable. |
| `city` | MyMUN | Nullable. |
| `streetAddress` | MyMUN `address_street` | Nullable. |
| `zipCode` | MyMUN | Store as text. |
| `diet` | MyMUN | Nullable raw text. |
| `phone` | MyMUN | Nullable. Mostly empty in the observed export. |
| `nationality` | MyMUN | Nullable. |
| `dateOfBirth` | MyMUN | Nullable date. |
| `delegationName` | MyMUN `Delegation` | Nullable. Used later for contextual display. |
| `mymunAssignedPositionName` | MyMUN `Model Country assigned` | Nullable raw text. |
| `mymunAssignedCommitteeName` | MyMUN `Committee` | Nullable raw text. |

If a delegate disappears from a later MyMUN update, remove the delegate from the application. Their preferences, comments, pending assignments, and confirmed assignments are removed as well.

### Delegate Question

Questions are dynamic MyMUN columns. They need both stored answers and organizer-configurable display/handling rules.

| Field | Source | Notes |
|---|---|---|
| `id` | App | Stable internal ID. |
| `conferenceId` | App | Parent conference. |
| `questionText` | MyMUN header beginning with `Question:` | Full question text. |
| `handlingType` | App | `Default`, `ExperienceLevel`, `HighSchooler`, `CommitteeChoiceExplanation`, or future types. |
| `displayOrder` | App | Controls order in delegate overview. |
| `visibleInDelegateOverview` | App | Allows hiding less useful questions. |
| `minimumTextLength` | App | Used by long-answer warning handling, especially committee/country explanation. |

Known recurring special questions:

- Experience level: values such as first-timer, beginner, intermediate, advanced.
- High schooler: boolean-like yes/no answer.
- Committee/country choice explanation: long-answer field; show a warning in the UI if the answer is shorter than three sentences.

The app should eventually include a per-conference question editor. The editor does not edit the MyMUN question text or answers; it configures how questions are handled, ordered, highlighted, and hidden in delegate detail views. Special recurring questions should be selected manually in this editor rather than hardcoded by exact text. Later, configurable aliases can make repeated conference setup faster.

### Delegate Question Answer

| Field | Source | Notes |
|---|---|---|
| `id` | App | Stable internal ID. |
| `delegateId` | App | Parent delegate. |
| `questionId` | App | Parent question/config record. |
| `answer` | MyMUN cell value | Nullable or blank-preserving text. |

### Preference

A preference connects a delegate to a position in ranked order.

| Field | Source | Notes |
|---|---|---|
| `id` | App | Stable internal ID. |
| `delegateId` | App | Parent delegate. |
| `positionId` | App | Resolved position. |
| `rank` | MyMUN `Preference 1..3` | Integer rank. |
| `rawPreferenceText` | MyMUN | Original text for diagnostics. |

Preference cells refer to positions and use a format like `Committee Name (Position Name)`.

Because unassigned positions are present in the `Countries` sheet, preferences should be resolvable against the position source. Unmatched preferences should abort the update with a clear message and make no database changes.

### Delegation

Delegation data is a later nice-to-have, not part of the initial assignment workflow. It can provide context in the delegate overview.

| Field | Source | Notes |
|---|---|---|
| `id` | App | Stable internal ID. |
| `conferenceId` | App | Parent conference. |
| `name` | MyMUN `Delegation` / `Delegations.Delegation Name` | Used to group delegates. |
| `country` | MyMUN | Nullable. |

Delegation payment fields should be ignored. If delegation details are implemented later, clicking a delegate's delegation in the overview can show the other delegates in that delegation.

## Update Staging Model

The MyMUN update should parse into staging objects before database writes:

- `MymunWorkbookSnapshot`
- `MymunDelegateRow`
- `MymunCountryPositionRow`
- `MymunDelegationRow`
- `MymunQuestionColumn`
- `MymunQuestionAnswerCell`
- `MymunPreferenceCandidate`
- `MymunConfirmedAssignmentCandidate`
- `MymunValidationIssue`

The staging model should preserve raw text and row/column locations so validation errors can point back to the workbook.

A MyMUN update must be atomic: if validation finds blocking issues, the update is aborted and the database is unchanged.

The application should support drag-and-drop of a MyMUN workbook anywhere in the app as the primary update interaction.

## Visibility Rules

Assignment board:

- Show active, paid delegates.
- Hide inactive delegates.
- Hide unpaid delegates.
- Show confirmed assigned delegates in their assigned slots.
- Grey out confirmed assigned delegates where they appear as interested delegates elsewhere.

Delegate list:

- Show active, paid delegates by default.
- Hide inactive delegates.
- Hide unpaid delegates.

Top bar:

- Count active paid delegates.
- Count unpaid delegates separately.
- Count open, pending, and assigned slots.

## Update Preservation Rules

MyMUN update replaces MyMUN-managed data:

- Delegate profile fields.
- Payment status.
- Committee and position source data.
- Preferences.
- Question answers.
- Confirmed assignments from `Countries.Delegates`.
- Difficulty scores, after recomputation.

MyMUN update preserves organizer-managed data where the delegate and position still exist:

- Delegate comments.
- Delegate active/inactive flag.
- Pending app assignments not contradicted by MyMUN-confirmed assignments.
- Manual pending assignment source markers.
- Question handling configuration.

MyMUN update removes:

- Delegates no longer present in the workbook.
- Preferences for removed delegates or missing positions.
- Assignments involving removed delegates or removed positions.

## Proposed SQLite Tables

| Table | Purpose |
|---|---|
| `conferences` | Conference roots. |
| `committees` | Committee display groups and committee-level slot count. |
| `positions` | Assignable countries/roles within committees. |
| `slots` | Individual assignable places for each position. |
| `delegates` | Assignable delegate profile and organizer state. |
| `delegate_questions` | Dynamic MyMUN question definitions plus handling/display configuration. |
| `delegate_question_answers` | Delegate answers for configured/imported question columns. |
| `preferences` | Ranked delegate preferences resolved to positions. |
| `delegations` | Optional delegation context. |
| `mymun_update_runs` | Metadata about workbook updates. |
| `mymun_update_issues` | Validation warnings/errors from update runs. |

No `delegation_contacts` table is planned. Faculty advisors are not relevant for MunAssign, and head delegates are represented on delegates themselves.

## Conference Import and Export

The `.munassign` format should let an organizer continue work on another computer without losing state. Export should therefore include the complete current conference working state:

- Committees, positions, slots, delegates, questions, answers, preferences, comments, and assignments.
- Committee slot count configuration.
- Delegate active/inactive state.
- Question handling configuration.
- MyMUN update metadata and validation issues if they are stored in the local database.

Import should support two flows:

- Replace the selected conference. This is the default.
- Create a new conference from the `.munassign` file.

## Indexes and Constraints

- `delegates(conference_id, normalized_first_name, normalized_last_name, normalized_email, zip_code, date_of_birth)` should be indexed for update matching.
- `committees(conference_id, normalized_name)` should be unique.
- `positions(committee_id, normalized_name)` should be unique.
- `slots(position_id, slot_index)` should be unique.
- `preferences(delegate_id, rank)` should be unique.
- `preferences(delegate_id, position_id)` should be unique.
- `delegate_questions(conference_id, question_text)` should be unique.
- Foreign keys should cascade on conference deletion.

## Initial Implementation Boundary

Build first:

- Conferences.
- Committees with `slotsPerPosition`.
- Positions.
- Slots.
- Delegates.
- Delegate questions and answers.
- Preferences.
- MyMUN update run metadata and blocking validation issues.
- MyMUN-confirmed assignment sync from `Countries.Delegates`.

Defer unless needed immediately:

- Delegation context display.
- Question editor UI.
- Manual payment overrides, likely never needed.
- Full chair/faculty/observer persistence, likely never needed.
