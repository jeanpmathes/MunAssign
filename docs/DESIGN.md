# MunAssign Design

MunAssign is a desktop assignment tool for Model United Nations organizers. It groups all working data by conference and stores each conference either in the local application database or in an explicitly linked synchronized SQLite file. MyMUN Excel files are recurring **updates** rather than import files. The word **import** is reserved for `.munassign` conference transfer.

## Product Rules

- A conference is the top-level boundary. Deleting a conference deletes its committees, positions, slots, delegates, preferences, questions, answers, comments, delegations, reservations, and update metadata.
- MyMUN Excel updates replace MyMUN-managed data for the selected conference and preserve organizer-managed state where the referenced delegate or position still exists.
- Successful MyMUN updates establish conference-specific trusted filename signatures. Later filenames that do not match any trusted signature require explicit confirmation before the workbook is read.
- `.munassign` import/export transfers one complete conference as a self-contained SQLite snapshot.
- A synchronized conference remains authoritative in one user-selected `*.sync.munassign` file. MunAssign only manages the filesystem path; OneDrive, Google Drive, network storage, or another provider may synchronize that folder independently.
- MyMUN-confirmed assignments come from the `Countries.Delegates` column.
- App-created assignments are pending until a later MyMUN update confirms them.
- Email is stored and used as a discriminator during updates, but it is not a unique delegate key.
- Unpaid and inactive delegates are hidden from assignment views and the default delegate list. The top bar counts unpaid delegates among accepted applications only, since unpaid-but-not-accepted delegates aren't actionable yet; the markdown safety export separately counts unpaid delegates across all applications and labels that bullet accordingly so the two counts are never confused side-by-side.
- Payment tiers are fixed organizer labels, `Full` and `Reduced`; they do not store prices, currencies, or payment-provider data. Every committee has a tier and defaults to `Full`. Active paid assignment-eligible delegates require organizer classification, while unpaid or inactive delegates do not raise a missing-tier warning.
- Assigning a `Reduced` delegate to a `Full` committee raises a non-blocking warning. Assigning a `Full` delegate to a `Reduced` committee raises a non-blocking notice. These checks apply equally to pending and MyMUN-confirmed assignments and can be acknowledged per delegate and committee; a missing-tier warning remains open until classified.
- Reservations are organizer-managed accept-capacity holds. They can reserve high-school and/or university places, optionally link to one MyMUN delegation, and are not tied to specific committees, positions, or slots.
- A delegate can consume at most one reservation. Manual delegate links are explicit organizer choices. Automatic delegation links are persisted rows rebuilt from active accepted, assigned, or checked-in delegates in submission-date order regardless of payment, skipping delegates that already consume a reservation.
- Deactivated delegates never consume reservations. Extra delegates in a linked delegation simply stop consuming reservation capacity once the reservation count is full; there is no over-use state.
- Committees can be marked high-school, university, or mapped. Mapped committees are for IPC-style committees where each position corresponds to another non-mapped committee and inherits that committee's high-school or university bucket.
- Newly discovered MyMUN committees are initialized from conservative name heuristics: names containing `high school` or `highschool` are high-school, names containing case-sensitive `IPC` are mapped, and all others are university. These heuristics only run when a committee is first created; later updates preserve organizer-managed committee mode.
- Delegations default to university and can be marked high-school by the organizer. Newly discovered delegations remain marked for review across restarts until the delegation overview is explicitly closed; later MyMUN updates preserve both organizer-managed properties.

## Technology

| Layer | Choice |
|---|---|
| UI | Compose Multiplatform Desktop |
| State | ViewModel + StateFlow |
| Local DB | SQLite via JetBrains Exposed |
| Excel updates | Apache POI |
| DI | Koin |
| Build | Gradle Kotlin DSL |
| Packaging | `compose.desktop.application` portable Windows zip |

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
5. Organizers may create reservations and review MyMUN delegations before or during assignment work.
6. The board shows committees as columns, positions as cards, and slots as rows.
7. Organizers can switch a committee between high-school, university, and mapped mode, and classify it as `Full` or `Reduced`. Mapped committee positions use a per-position dropdown to select the corresponding non-mapped committee.
8. Slot controls can create or clear pending assignments, but cannot locally confirm a final assignment.
9. Later MyMUN updates reconcile confirmed assignments from `Countries.Delegates` and rebuild automatic reservation usage.

## UI Requirements

- The top bar shows conference name, active paid delegate count, unpaid-among-accepted delegate count, open slots, pending slots, assigned slots, high-school and university accept capacity after remaining reservations, and actions for delegate list, delegation overview, reservation manager, MyMUN update, and question configuration. `.munassign` import/export actions live in the conference manager.
- The main view is a horizontally scrollable board of committee columns.
- High-school committee columns show the total and remaining high-school reservation capacity reminder. University committee columns show the equivalent university reminder. The reminder opens the reservation manager and does not bind reservations to that committee.
- Mapped committee columns use a distinct header tone and do not show a reservation reminder because capacity is derived position-by-position from their correspondences.
- Mapped positions show a correspondence dropdown listing eligible high-school and university committees. Mapped committees cannot be selected as correspondence targets. Multiple positions in one mapped committee may share the same correspondence target.
- Each position shows slot rows, interested delegate count, and interested delegates inline when the position has no assignment.
- Confirmed-assigned delegates are greyed out where they appear as interested delegates elsewhere.
- Reduced committees show a percent marker. Candidate and occupied-slot rows show a red downward arrow for `Reduced` into `Full`, or a yellow upward arrow for `Full` into `Reduced`; acknowledged mismatches keep a muted marker. Candidate ordering considers availability, then tier fit (exact, notice, warning), then school fit, preference, and name.
- The issue manager offers inline `Full` and `Reduced` actions for missing classifications. Delegate detail exposes the same tier editor. The delegate list shows and filters `Full`, `Reduced`, and paid-unassigned tiers.
- Clicking a delegate opens a side panel with profile data, assignment state, reservation usage, preferences, configured question answers, comment history, and deactivate/reactivate controls. Manual reservation links can be assigned, changed, or removed there; automatic delegation links can be changed by assigning a manual reservation.
- The delegate list panel shows active paid delegates by default, with search, assignment/payment/active/committee filters, presets, and sorting by submitted date, difficulty, name, assignment status, and warning/notice count. Difficulty is a sort key only. The difficulty sort is intentionally ascending (easiest-to-place delegates first), so organizers clear easy delegates and are left with only the hard ones.
- The delegation overview is a non-modal workspace panel similar to the delegate overview. It shows delegation membership, accepted active paid counts, linked reservation capacity, and current consumption. Each delegation can be marked high-school and can open a directly prefilled reservation editor using its full expected count; absent or non-positive counts prefill as zero. New delegations show review markers on their cards and on the top-bar delegation action until the panel is explicitly closed.
- The reservation manager is a compact modal for creating, editing, deleting, and linking reservations to delegations.
- The conference manager keeps rows compact: Reload is the only optional inline conference action, while a dropdown arrow and right-click open the same overflow menu for export, replacement, trusted filename signatures, rename, storage, and removal actions. One Add conference button opens tabs for Create new local, Import as new, Create new synchronized, and Connect synchronized.
- Slot count is committee-level; every position in a committee has the same number of slots. Changing the count requires confirmation.
- An icon-only action at the end of the top bar's left action group copies a privacy-safe 1080x1080 status image to the clipboard. The conference selector remains the leftmost anchor, followed by conference management, the textual exports folder, and status-image copy. The image shows high-school and university commitments split into assigned, confirmed, accepted, reserved, and unlabelled free capacity, followed by allocations for up to twelve committees without delegate names or contact data. Committee titles are color-coded by mode without additional mode icons. The top bar uses intrinsic left, center, and right groups; at compact widths, statistics move intact to a second row instead of squeezing or clipping.
- Unexpected update filenames, every conference replacement, conference deletion, forced import of a synchronized source, and deletion of a shared file use a two-stage armed confirmation. Cancel is the prominent action; the risky action first reveals an off switch and a disabled final button, and the final action becomes available only after the organizer arms it.

## Update And Transfer

MyMUN update uses:

- `Delegates` as the primary source for assignable delegates, payment status, profile fields, questions, answers, and preferences.
- `Countries` as the complete source for committees, positions, and MyMUN-confirmed assignments.
- `Delegations` as delegation context for delegate detail, delegation overview, assignment decisions, and reservation auto-linking.

`Chairs`, `Faculty Advisors`, and `Observers` are intentionally ignored for assignment.

The parser should tolerate whitespace variation in headers and comma-separated `Countries.Delegates` values. Preference cells are expected to follow `Committee Name (Position Name)` and must resolve against positions from the `Countries` sheet; when committee or position names themselves contain parentheses, the final balanced parenthesized group is treated as the position name.

Before parsing, the update filename is normalized into meaningful parts. The extension is removed,
hyphens delimit parts, underscores are removed, whitespace is normalized, and numeric-only plus
`mymun`, `applications`, and `export` parts are ignored. Parts match case-insensitively as an exact
unordered set. The first non-empty signature and explicitly accepted mismatches become trusted only
after a successful update. Filenames with no meaningful parts require confirmation every time and
are never trusted. Organizers can delete individual trusted signatures from the conference manager.

`.munassign` export writes one complete conference to a SQLite snapshot. Import remaps conference-owned IDs, can create a new conference with a duplicate visible name, or replace a selected conference while preserving the local conference name and display order. Older transfer files are migrated from a temporary copy before import; transfer files from newer schema versions are rejected.

Payment status remains MyMUN-managed. A paid-to-paid update preserves the organizer's delegate tier, an update to unpaid clears it, and a later return to paid requires classification again. Committee tiers are organizer-managed and preserved across updates; newly discovered committees default to `Full`.

Every schema-12-or-newer SQLite file contains a stable file UUID and a content marker: `ApplicationDatabase`, `ConferenceTransfer`, or `SynchronizedConference`. File handling validates this metadata rather than relying on the filename. Markerless legacy files remain valid normal imports, but cannot be connected as synchronized conferences. Exports always create detached `ConferenceTransfer` files, including exports whose source is synchronized. Importing a synchronized source recommends Connect and requires the armed detached-import override; the source file is never changed.

## Synchronized Conferences

The conference catalog beside the local application state is authoritative for mixed local/synchronized ordering and source routing. It caches the conference name, conference ID, synchronized file UUID, path, availability, order, and last-loaded time. Catalog writes are atomic. Paths inside a portable installation are stored relative to that installation; other paths are normalized absolute paths. Existing local conferences seed the catalog from `conferences.display_order`, which remains maintained for compatibility.

Create synchronized creates one new conference at a new user-selected path. Move first creates and validates the synchronized file, records a recoverable catalog operation, then removes the local original. Connect requires a marked, supported, single-conference synchronized file with a unique file UUID and conference ID. Locate only accepts another path to the same embedded file UUID and conference identity. Unlink removes the machine-local catalog entry; Delete shared file is a separate armed action and retains the link if deletion fails.

All reads, mutations, MyMUN updates, replacements, transfers, and safety exports are routed to the authoritative database by the storage coordinator. Missing, invalid, inaccessible, or newer-version synchronized links stay visible with their cached name and recovery actions. Reload, reselecting the conference, and restoring window focus reload from the linked file. Available synchronized conferences participate in open/close markdown safety exports; unavailable links are reported without preventing exports for available conferences.

MunAssign deliberately provides no cloud API, live file watcher, offline cache, application-level lock, or conflict merge. SQLite transactions and the existing busy timeout remain the only application-side concurrency mechanism. Simultaneous edits through a synchronization provider can still conflict or lose work; provider-created conflict copies are explicit files that can be handled through Locate when identity matches or armed detached Import otherwise. Before an older synchronized file is migrated in place, MunAssign saves a timestamped backup under local application data. These backups are not automatically pruned in the first version.

Markdown safety exports are one-way recovery snapshots, not transfer files. The desktop app writes timestamped markdown files for every conference when it opens and closes, using one file per conference under the active MunAssign data directory's `markdown-exports` folder. These files summarize assignments, position interest, delegate and committee payment tiers, and high-level stats for human inspection after a failure; MunAssign does not import them.

## Implementation Notes

- The application database normally lives at `~/.munassign/data.db`. A packaged Windows build launched from a different drive than the user home directory uses an app-local `data/data.db` beside the portable app folder and does not copy existing home data automatically.
- The machine-local conference catalog and synchronized-file migration backups live beside the active application state. They describe this installation's links and must not be stored inside a synchronized conference file.
- `:app:packagePortableZip` creates the no-installer Windows distribution zip with `MunAssign.exe`, `app/`, and `runtime/` at the archive root, and `:app:assemble` depends on it so normal Gradle-backed IDE builds create the zip. The packaged runtime includes the `java.sql` module required by SQLite/JDBC startup. MSI, DMG, and deb installers are intentionally out of scope until there is a concrete installer requirement.
- Foreign keys should cascade for conference-owned data.
- Assignment difficulty is computed from current state when needed; it is not stored on `Delegate`.
- Accept capacity for each high-school/university bucket is computed from matching committee slot capacity minus active accepted, assigned, or checked-in delegates and minus remaining reservation capacity. Payment remains relevant to assignment readiness, not acceptance commitment.
- Scroll-to-position/slot navigation should be driven by ViewModel events, with each committee column owning its list state.
- Slot assignment actions should be available through visible row controls; secondary-button menus may mirror those actions for faster desktop use.
