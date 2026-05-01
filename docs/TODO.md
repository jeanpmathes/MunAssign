# TODO

## Next - Delegate Detail And List

- Make visible delegate names clickable.
- Add side panel with imported profile data, assignment state, preferences, configured question answers, comments, delegation context, and deactivate/reactivate controls.
- Make assignments and preferences navigate back to the board.
- Add autosaved organizer comment history.
- Add delegate list drawer with active paid delegates by default.
- Add delegate filters for unassigned delegates, difficulty, and committee interest.
- Add delegate sorting by difficulty, name, and assignment status.

## Later - Question Configuration

- Build UI for question roles, handling types, visibility, organizer labels, answer rules, and typed-answer mappings.
- Surface answer warnings/notices in delegate detail and assignment workflows.
- Add safe defaults for newly detected questions while preserving organizer configuration across updates.

## Later - Transfer And Polish

- Implement `.munassign` SQLite export for a single conference.
- Implement `.munassign` SQLite import that can replace the selected conference or create a new conference.
- Add file picker dialogs and user-facing error states for incompatible transfer files, write failures, and overwrite confirmation.
- Add keyboard/focus handling for menus, dialogs, drawers, and side panel navigation.
- Configure native packaging metadata for MSI, DMG, and deb outputs.
- Add a splash screen and loading indicator.
- Add a dark mode toggle.
- Drag and drop of excel files.
- Emojis for countries.
- Fix all warnings.
- Improve upload experience (no weird message) and the UI when conference is empty.

## Experimental

- Consider drag and drop for assignment workflows. Interested delegate bubbles could be dragged onto open slots, and pending assignments could be dragged between slots with the same MyMUN-confirmed assignment protections used by the current buttons/dialogs.
