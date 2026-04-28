# TODO

## Next - Conference Management UI

- Move the temporary conference/workbook screen toward the planned app shell instead of growing it further.
- Add rename flow for conferences.
- Add delete confirmation for conferences.
- Show update progress, blocking errors, and warnings in a dedicated update result surface.
- Add empty states for no conference, no update data, no committees, and no visible delegates.
- Keep MyMUN workbook actions consistently labeled as **updates**, never import/export.

## Next - Assignment Board

- Build the main horizontally scrollable board with top stats, committee columns, position cards, and slot rows.
- Render empty, pending, and assigned slot states with clear visual distinction.
- Show interested delegate counts and inline interested delegates when no delegate is assigned.
- Apply unpaid/inactive visibility rules and grey out confirmed-assigned delegates where they appear as interested elsewhere.
- Preserve board scroll position after updates and assignment actions.
- Drive scroll-to-position/slot navigation through ViewModel events, with each committee column owning its list state.

## Next - Assignment Actions

- Add right-click slot context menus using Compose pointer input for secondary-button release.
- Support pending assignment from interested delegates.
- Support manual assignment of active paid delegates even without expressed interest.
- Support clearing and replacing pending assignments.
- Keep confirmed assignments controlled by MyMUN updates only.
- Persist assignment actions through repository/use-case calls and refresh board state.
- Add tests for slot mutation invariants and persistence behavior.

## Later - Delegate Detail And List

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
