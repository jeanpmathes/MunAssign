# TODO

## Done - Domain and Schema Draft

- Drafted the conference-scoped domain model in `docs/DOMAIN_MODEL_DRAFT.md`.
- Implemented pure `:domain` model classes/value objects for conferences, committees, positions, slots, delegates, delegations, preferences, comments, questions, answers, and typed answer mappings.
- Implemented invariant-preserving domain operations for assigning and clearing slots.
- Added focused domain and graph-mapper tests for current model invariants.
- Split domain classes, database row DTOs, and database table definitions into separate files.
- Drafted the initial SQLite table definitions and database row graph mapper.

## Next - SQLite Foundation

- Implement `DatabaseFactory`, database location, migrations, and connection lifecycle.
- Implement conference create, rename, list, select, and delete.
- Add cascade deletes for all conference-owned data.
- Add repository tests for conference CRUD and cascade delete behavior.
- Seed question role, question handling type, and answer rule type lookup rows with the standard application defaults.
- Add tests that verify default question role/handling/rule seed data is present.

## Next - MyMUN Update Validation and Persistence

- Convert `MymunWorkbookSnapshot` staging rows into validated update candidates before writing to the database.
- Show validation errors for missing columns, duplicate delegates, invalid payment values, and unresolved preferences.
- Validate confirmed assignment candidates against delegate matching and configured committee slot counts.
- Use question heuristics during update to suggest expected roles, handling types, default typed mappings, and rule defaults for newly detected questions.
- Abort the update on committee slot-count mismatches and explain which committee needs adjustment.
- Implement full-overwrite delegate and preference update for the current conference.
- Sync MyMUN-confirmed assignments from staged `Countries.Delegates` candidates.
- Preserve organizer-managed fields according to the preservation rules in `docs/DATA_MODEL.md`.
- Ensure updated preferences provide the data needed for later on-demand difficulty scoring.
- Persist MyMUN update run metadata and blocking validation issues.
- Add focused parser validation tests for missing sheets/columns, duplicate delegates, invalid payment values, unresolved preferences, and malformed dates.
- Add integration tests using the anonymized workbook fixture.

## Later - App-Level MyMUN Update Flow

- Support drag-and-drop of a MyMUN workbook anywhere in the app.
- Surface staging and validation failures as user-facing update errors.
- Ensure the UI uses **update** terminology for MyMUN Excel files and reserves import/export for `.munassign` transfer.
- Add loading and atomic-failure states for the update flow.

## Later - Read-Only Assignment Board

- Build the main screen shell with top bar, horizontally scrollable committee columns, and position cards.
- Render one slot row per slot, including empty, pending, and assigned states.
- Show interested delegate counts per position.
- Show interested delegates inline when no delegate is assigned.
- Apply early visibility rules so inactive and unpaid delegates do not appear in the board.
- Grey out delegates who are already confirmed assigned elsewhere.
- Show top bar counts for active paid delegates, unpaid delegates, open slots, pending slots, and assigned slots.
- Add empty states for no conference, no update data, no committees, and no visible delegates.

## Later - Slot Context Actions

- Add right-click detection on slot rows.
- Show state-specific context menu options:
  - empty slot: set pending from interested delegates, add manually.
  - pending slot: clear slot, replace manually.
  - assigned slot: no local confirmation action; confirmed assignments come from MyMUN updates.
- Persist each action immediately through repository/use case calls.
- Refresh board state after each action.
- Add tests for each context action using realistic board data.

## Later - Delegate Side Panel

- Make every visible delegate name clickable.
- Open a side panel with imported personal information, payment status, active status, comments, assignments, and preferences.
- Show current pending and confirmed assignments first.
- Make assignments and preferences clickable navigation targets back to the board.
- Add autosaved organizer comment history.
- Add delegate deactivate/reactivate behavior and immediately apply visibility rules.
- Show a crown marker for head delegates.
- Show configured question answers in the chosen display order, including hidden/special-handled questions.
- Ensure unpaid and inactive status changes update top bar counts and board/list visibility.

## Later - Manual Assignment and Delegate Picking

- Build the delegate picker used by pending and manual assignment actions.
- In interested-delegate mode, show only visible delegates interested in the position.
- In manual mode, show all active paid delegates, including delegates without interest in the position.
- Clearly indicate already assigned, pending, unpaid, and inactive delegates where relevant.
- Prevent invalid duplicate confirmed assignments unless explicitly supported by the model.
- Support double delegation by assigning one delegate per slot while keeping position-level context visible.
- Add search and sorting to the picker if the delegate list is long enough to need it.

## Later - Delegate List Panel

- Build the delegate list drawer from the top bar.
- Show all active, paid delegates by default.
- Add filters for unassigned only, assignment difficulty, and committee interest.
- Add sorting by difficulty score, name, and assignment status.
- Clicking a delegate opens the delegate side panel.
- Keep unpaid and inactive delegates out of the default list while preserving unpaid counts in the top bar.

## Later - Board Navigation and Workflow Polish

- Implement scroll-to-position and scroll-to-slot navigation from side panel links.
- Preserve scroll position when context actions update the board.
- Add visual feedback for pending, assigned, unavailable, manually assigned, and recently changed slots.
- Add undo-like clear/revert affordances where the workflow needs them.
- Add keyboard and focus handling for menus, dialogs, drawers, and side panel navigation.

## Later - Conference Import and Export

- Implement `.munassign` SQLite export for a single conference.
- Implement `.munassign` SQLite import that can replace the selected conference or create a new conference.
- Default `.munassign` import behavior should replace the selected conference.
- Keep MyMUN update terminology separate from conference import/export terminology in UI text and code names.
- Add file picker dialogs for `.munassign` import/export.
- Add user-facing error states for incompatible files, write failures, and overwrite confirmation.

## Later - Packaging and Final Polish

- Add loading, saving, validation, and destructive-action confirmation states.
- Add broader workflow tests for update, assignment, comments, deactivate, and export/import.
- Configure native packaging metadata for MSI, DMG, and deb outputs.
- Review UI spacing, text overflow, empty states, and accessibility labels.
