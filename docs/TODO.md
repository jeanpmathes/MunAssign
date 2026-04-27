# TODO

## Phase 1 - Data Model and Visibility Rules

- Establish domain models for conferences, committees, positions, slots, delegates, preferences, comments, and assignment status.
- Establish delegate question and delegate question answer models.
- Establish per-conference question handling configuration.
- Include delegate age and relative application submission timing in delegate overview data.
- Define the core visibility rules:
  - active and paid delegates appear in assignment views.
  - unpaid delegates are hidden from assignment views but counted in the top bar.
  - inactive delegates are hidden from assignment views and the delegate list.
  - assigned delegates remain visible in their own slot and greyed out elsewhere.
- Define update behavior for MyMUN data versus organizer-managed data.
- Preserve comments, inactive state, question handling configuration, and pending assignments where not contradicted by MyMUN-confirmed assignments.
- Remove delegates and related data when they disappear from a MyMUN update.
- Add domain-level tests for visibility rules and update preservation rules.

## Phase 2 - SQLite Foundation

- Build the SQLite schema for conferences, committees, positions, slots, delegates, preferences, comments, assignment state, delegate questions, question answers, and MyMUN update metadata.
- Implement `DatabaseFactory`, database location, migrations, and connection lifecycle.
- Implement conference create, rename, list, select, and delete.
- Add cascade deletes for all conference-owned data.
- Add repository tests for conference CRUD, cascade delete behavior, and visibility queries.

## Phase 3 - MyMUN Update Validation and Persistence

- Convert `MymunWorkbookSnapshot` staging rows into validated update candidates before writing to the database.
- Show validation errors for missing columns, duplicate delegates, invalid payment values, and unresolved preferences.
- Validate confirmed assignment candidates against delegate matching and configured committee slot counts.
- Abort the update on committee slot-count mismatches and explain which committee needs adjustment.
- Implement full-overwrite delegate and preference update for the current conference.
- Sync MyMUN-confirmed assignments from staged `Countries.Delegates` candidates.
- Preserve organizer-managed fields according to the Phase 1 rules.
- Recompute delegate difficulty scores after preferences are updated.
- Persist MyMUN update run metadata and blocking validation issues.
- Add focused parser validation tests for missing sheets/columns, duplicate delegates, invalid payment values, unresolved preferences, and malformed dates.
- Add integration tests using the anonymized workbook fixture.

## Phase 4 - App-Level MyMUN Update Flow

- Support drag-and-drop of a MyMUN workbook anywhere in the app.
- Surface staging and validation failures as user-facing update errors.
- Ensure the UI uses **update** terminology for MyMUN Excel files and reserves import/export for `.munassign` transfer.
- Add loading and atomic-failure states for the update flow.

## Phase 5 - Read-Only Assignment Board

- Build the main screen shell with top bar, horizontally scrollable committee columns, and position cards.
- Render one slot row per slot, including empty, pending, and assigned states.
- Show interested delegate counts per position.
- Show interested delegates inline when no delegate is assigned.
- Apply early visibility rules so inactive and unpaid delegates do not appear in the board.
- Grey out delegates who are already confirmed assigned elsewhere.
- Show top bar counts for active paid delegates, unpaid delegates, open slots, pending slots, and assigned slots.
- Add empty states for no conference, no update data, no committees, and no visible delegates.

## Phase 6 - Slot Context Actions

- Add right-click detection on slot rows.
- Show state-specific context menu options:
  - empty slot: set pending from interested delegates, add manually.
  - pending slot: clear slot, replace manually.
  - assigned slot: no local confirmation action; confirmed assignments come from MyMUN updates.
- Persist each action immediately through repository/use case calls.
- Refresh board state after each action.
- Add tests for each context action using realistic board data.

## Phase 7 - Delegate Side Panel

- Make every visible delegate name clickable.
- Open a side panel with imported personal information, payment status, active status, comments, assignments, and preferences.
- Show current pending and confirmed assignments first.
- Make assignments and preferences clickable navigation targets back to the board.
- Add autosaved organizer comments.
- Add delegate deactivate/reactivate behavior and immediately apply visibility rules.
- Show a crown marker for head delegates.
- Show configured question answers in the chosen display order, including hidden/special-handled questions.
- Ensure unpaid and inactive status changes update top bar counts and board/list visibility.

## Phase 8 - Manual Assignment and Delegate Picking

- Build the delegate picker used by pending and manual assignment actions.
- In interested-delegate mode, show only visible delegates interested in the position.
- In manual mode, show all active paid delegates, including delegates without interest in the position.
- Clearly indicate already assigned, pending, unpaid, and inactive delegates where relevant.
- Prevent invalid duplicate confirmed assignments unless explicitly supported by the model.
- Support double delegation by assigning one delegate per slot while keeping position-level context visible.
- Add search and sorting to the picker if the delegate list is long enough to need it.

## Phase 9 - Delegate List Panel

- Build the delegate list drawer from the top bar.
- Show all active, paid delegates by default.
- Add filters for unassigned only, assignment difficulty, and committee interest.
- Add sorting by difficulty score, name, and assignment status.
- Clicking a delegate opens the delegate side panel.
- Keep unpaid and inactive delegates out of the default list while preserving unpaid counts in the top bar.

## Phase 10 - Board Navigation and Workflow Polish

- Implement scroll-to-position and scroll-to-slot navigation from side panel links.
- Preserve scroll position when context actions update the board.
- Add visual feedback for pending, assigned, unavailable, manually assigned, and recently changed slots.
- Add undo-like clear/revert affordances where the workflow needs them.
- Add keyboard and focus handling for menus, dialogs, drawers, and side panel navigation.

## Phase 11 - Conference Import and Export

- Implement `.munassign` SQLite export for a single conference.
- Implement `.munassign` SQLite import that can replace the selected conference or create a new conference.
- Default `.munassign` import behavior should replace the selected conference.
- Keep MyMUN update terminology separate from conference import/export terminology in UI text and code names.
- Add file picker dialogs for `.munassign` import/export.
- Add user-facing error states for incompatible files, write failures, and overwrite confirmation.

## Phase 12 - Packaging and Final Polish

- Add loading, saving, validation, and destructive-action confirmation states.
- Add broader workflow tests for update, assignment, comments, deactivate, and export/import.
- Configure native packaging metadata for MSI, DMG, and deb outputs.
- Review UI spacing, text overflow, empty states, and accessibility labels.
