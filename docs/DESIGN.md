# MunAssign Design

MunAssign is a desktop assignment tool for Model United Nations organizers. It keeps one local SQLite database, groups all working data by conference, and treats MyMUN Excel files as recurring **updates** rather than import files. The word **import** is reserved for `.munassign` conference transfer.

## Product Rules

- A conference is the top-level boundary. Deleting a conference deletes its committees, positions, slots, delegates, preferences, questions, answers, comments, delegations, and update metadata.
- MyMUN Excel updates replace MyMUN-managed data for the selected conference and preserve organizer-managed state where the referenced delegate or position still exists.
- `.munassign` import/export transfers one complete conference as a self-contained SQLite snapshot.
- MyMUN-confirmed assignments come from the `Countries.Delegates` column.
- App-created assignments are pending until a later MyMUN update confirms them.
- Email is stored and used as a discriminator during updates, but it is not a unique delegate key.
- Unpaid and inactive delegates are hidden from assignment views and the default delegate list. Unpaid delegates are counted separately in the top bar.

## Technology

| Layer | Choice |
|---|---|
| UI | Compose Multiplatform Desktop |
| State | ViewModel + StateFlow |
| Local DB | SQLite via JetBrains Exposed |
| Excel updates | Apache POI |
| DI | Koin |
| Build | Gradle Kotlin DSL |
| Packaging | `compose.desktop.application` |

`kotlinx.serialization` is not planned for conference transfer because `.munassign` uses the SQLite-backed schema directly.

## Modules

- `:domain` is pure Kotlin. It owns behavior-friendly model objects, repository interfaces, and use cases.
- `:data` owns SQLite/Exposed persistence, row DTOs, graph mapping, repositories, and MyMUN workbook parsing.
- `:app` owns Compose UI, ViewModels, and DI wiring. UI code should depend on domain interfaces; only DI wiring should refer directly to data implementations.

## Main Workflow

1. The organizer creates or selects a conference.
2. They drop a MyMUN workbook into the app to update the conference.
3. The parser builds a staging snapshot and validation issues before writing.
4. Blocking validation errors abort the update and leave the database unchanged.
5. The board shows committees as columns, positions as cards, and slots as rows.
6. Slot controls can create or clear pending assignments, but cannot locally confirm a final assignment.
7. Later MyMUN updates reconcile confirmed assignments from `Countries.Delegates`.

## UI Requirements

- The top bar shows conference name, active paid delegate count, unpaid delegate count, open slots, pending slots, assigned slots, and actions for delegate list plus `.munassign` import/export.
- The main view is a horizontally scrollable board of committee columns.
- Each position shows slot rows, interested delegate count, and interested delegates inline when the position has no assignment.
- Confirmed-assigned delegates are greyed out where they appear as interested delegates elsewhere.
- Clicking a delegate opens a side panel with profile data, assignment state, preferences, configured question answers, comment history, and deactivate/reactivate controls.
- The delegate list panel shows active paid delegates by default, with search, assignment/payment/active/committee filters, presets, and sorting by submitted date, difficulty, name, assignment status, and warning/notice count. Difficulty is a sort key only.
- Slot count is committee-level; every position in a committee has the same number of slots. Changing the count requires confirmation.

## Update And Transfer

MyMUN update uses:

- `Delegates` as the primary source for assignable delegates, payment status, profile fields, questions, answers, and preferences.
- `Countries` as the complete source for committees, positions, and MyMUN-confirmed assignments.
- `Delegations` as delegation context for delegate detail and assignment decisions.

`Chairs`, `Faculty Advisors`, and `Observers` are intentionally ignored for assignment.

The parser should tolerate whitespace variation in headers and comma-separated `Countries.Delegates` values. Preference cells are expected to follow `Committee Name (Position Name)` and must resolve against positions from the `Countries` sheet.

## Implementation Notes

- The application database lives at `~/.munassign/data.db`.
- Foreign keys should cascade for conference-owned data.
- Assignment difficulty is computed from current state when needed; it is not stored on `Delegate`.
- Scroll-to-position/slot navigation should be driven by ViewModel events, with each committee column owning its list state.
- Slot assignment actions should be available through visible row controls; secondary-button menus may mirror those actions for faster desktop use.
