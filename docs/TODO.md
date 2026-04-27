# TODO

## Done

- Implemented the pure `:domain` model for conferences, committees, positions, slots, delegates, delegations, preferences, comments, questions, answers, and typed answer mappings.
- Implemented domain assignment invariants and focused domain tests.
- Split domain classes, database row DTOs, and database table definitions into separate files.
- Implemented the initial SQLite table definitions and conference graph mapper.
- Implemented initial MyMUN workbook parsing into staging objects for delegates, countries/positions, delegations, questions, answers, preferences, confirmed assignment candidates, and validation issues.

## Next - SQLite Foundation

- Implement `DatabaseFactory`, database location, migrations, and connection lifecycle.
- Implement conference create, rename, list, select, and delete.
- Add cascade deletes for all conference-owned data.
- Add repository tests for conference CRUD and cascade delete behavior.
- Seed question role, question handling type, and answer rule type lookup rows with standard defaults.
- Add tests that verify default lookup seed data is present.

## Next - MyMUN Update Validation And Persistence

- Convert `MymunWorkbookSnapshot` staging rows into validated update candidates before writing to the database.
- Show validation errors for missing columns, duplicate delegates, invalid payment values, unresolved preferences, and malformed dates.
- Validate confirmed assignment candidates against delegate matching and configured committee slot counts.
- Apply question heuristics for expected roles, handling types, default mappings, and default rules for newly detected questions.
- Abort the update on committee slot-count mismatches and explain which committee needs adjustment.
- Implement full-overwrite delegate and preference updates for the current conference.
- Sync MyMUN-confirmed assignments from staged `Countries.Delegates` candidates.
- Preserve organizer-managed fields according to `docs/DATA_MODEL.md`.
- Persist MyMUN update run metadata and blocking validation issues.
- Add focused parser/update tests, including an anonymized workbook fixture.

## Later - Assignment UI

- Support drag-and-drop of a MyMUN workbook anywhere in the app.
- Surface staging and validation failures as user-facing update errors.
- Build the main assignment board with top stats, committee columns, position cards, and slot rows.
- Render empty, pending, and assigned slot states.
- Show interested delegate counts and inline interested delegates when no delegate is assigned.
- Apply unpaid/inactive visibility rules and grey out confirmed-assigned delegates elsewhere.
- Add empty states for no conference, no update data, no committees, and no visible delegates.

## Later - Assignment Actions

- Add right-click slot context menus.
- Support pending assignment from interested delegates.
- Support manual assignment of active paid delegates even without expressed interest.
- Support clearing and replacing pending assignments.
- Keep confirmed assignments controlled by MyMUN updates only.
- Persist actions through repository/use case calls and refresh board state.

## Later - Delegate Detail And List

- Make visible delegate names clickable.
- Add side panel with imported profile data, assignment state, preferences, configured question answers, comments, delegation context, and deactivate/reactivate controls.
- Make assignments and preferences navigate back to the board.
- Add autosaved organizer comment history.
- Add delegate list drawer with unassigned, difficulty, and committee-interest filters plus sorting by difficulty, name, and assignment status.

## Later - Transfer And Polish

- Implement `.munassign` SQLite export for a single conference.
- Implement `.munassign` SQLite import that can replace the selected conference or create a new conference.
- Add file picker dialogs and user-facing error states for incompatible files, write failures, and overwrite confirmation.
- Implement scroll-to-position/slot navigation from side panel links.
- Preserve scroll position after board updates.
- Add keyboard/focus handling for menus, dialogs, drawers, and side panel navigation.
- Configure native packaging metadata for MSI, DMG, and deb outputs.
