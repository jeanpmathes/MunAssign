# TODO

## Bugs

- A multi slot position does not show interested delegates when only one slot is already assigned.
- A multi slot position tooltip when using it on slot 1 to assign the delegate that is pending in slot 2, does not show the move question modal but instead shows an error message in the top.

## Next - Delegate Detail And List

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
- HS handling.
- Improve upload experience (no weird message) and the UI when conference is empty.

## Experimental

- Consider drag and drop for assignment workflows. Interested delegate bubbles could be dragged onto open slots, and pending assignments could be dragged between slots with the same MyMUN-confirmed assignment protections used by the current buttons/dialogs.
