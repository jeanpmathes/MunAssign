# IPC Mapped Committees Design

## Purpose

MunAssign needs to support International Press Corps (IPC) committees. An IPC is imported from MyMUN as a normal committee with positions, slots, preferences, and confirmed assignments, but each IPC position corresponds to another committee. The corresponding committee determines whether that IPC position should be treated as high-school or university for assignment guidance.

The existing committee-level high-school flag is too coarse because one IPC committee can contain positions mapped to several committees with different school levels. The feature should preserve current behavior for normal committees while adding an explicit mapped mode for IPC-style committees.

## Recommended Approach

Add a committee mode and per-position committee mapping:

- `HighSchool`: the committee and all positions are high-school positions.
- `University`: the committee and all positions are university positions.
- `Mapped`: the committee is an IPC-style mapped committee. Each position may map to one non-mapped committee and inherits that target committee's high-school/university level.

Mappings are organizer-managed through a dropdown. Auto-mapping is intentionally out of scope because IPC position names are not reliable enough for safe matching.

## Domain Model

Replace the effective meaning of `Committee.isHighSchoolCommittee` with a domain enum such as `CommitteeMode`.

Normal committee type:

- `CommitteeMode.HighSchool` means high-school.
- `CommitteeMode.University` means university.
- `CommitteeMode.Mapped` means the committee itself is IPC/mapped and does not provide a single school-level bucket for all positions.

Position mapping:

- `Position.mappedCommittee` is nullable.
- It is meaningful only when `position.committee.mode == CommitteeMode.Mapped`.
- The mapped committee must belong to the same conference.
- The mapped committee must not itself be `CommitteeMode.Mapped`.
- A mapped position's effective school level is inherited from `mappedCommittee.mode`.
- A mapped position with no target has no effective school level and should be surfaced as incomplete organizer configuration.

Normal positions do not need position-level mappings. If a normal committee is switched back from mapped mode, any existing position mappings should be cleared so stale IPC configuration cannot silently affect later behavior.

## Persistence And Migration

Persist committee mode on `committees`.

Migration from the current boolean:

- `is_high_school_committee = true` becomes `HighSchool`.
- `is_high_school_committee = false` becomes `University`.

The old column may remain as a compatibility column during the migration if that is simpler, but domain reads and writes should use the new mode field once migration exists.

Persist position mappings on `positions` with a nullable `mapped_committee_id` foreign key to `committees(id)`. The foreign key uses `ON DELETE SET NULL` so deleting a target committee leaves the IPC position in an explicit unmapped state.

Data constraints:

- Deleting a mapped target committee clears the mapping by setting `mapped_committee_id` to null.
- Repository validation must reject mappings to a mapped committee.
- Repository validation must reject mappings to a committee from another conference.
- Repository validation must reject assigning a mapped target to a position whose owning committee is not mapped.

MyMUN updates preserve organizer-managed committee mode and position mappings for retained committees and positions. New committees default to `University`. New positions in mapped committees start unmapped.

## Repository API

Replace or supplement the existing `setCommitteeHighSchoolStatus` operation with:

- `setCommitteeMode(conferenceId, committeeId, mode)`
- `setPositionMappedCommittee(conferenceId, positionId, mappedCommitteeId?)`

Behavior:

- `setCommitteeMode` updates organizer-managed mode.
- When switching a committee away from `Mapped`, clear mappings for its positions.
- When switching a committee to `Mapped`, keep positions and slots unchanged and require organizers to map each position through the UI.
- If a committee is changed to `Mapped`, reject the change while any position in the conference maps to it. The organizer must move or clear those mappings first.
- `setPositionMappedCommittee` accepts `null` to clear a mapping.

## Assignment Board UI

Committee header:

- High-school committees keep their high-school visual treatment.
- University committees keep the current neutral/non-HS treatment.
- Mapped committees receive a distinct IPC/mapped header color and label.
- The current high-school toggle becomes a mode selector with `HS`, `Uni`, and `Mapped`.

Mapped position card:

- Shows a compact "Corresponds to" dropdown.
- Dropdown options are committees in the same conference whose mode is `HighSchool` or `University`.
- The dropdown excludes the mapped committee itself and excludes all mapped committees.
- The selected target's school level is shown with the same HS/Uni visual language used elsewhere.
- Unmapped positions show a visible incomplete state and a tooltip explaining that the position needs a target committee.

Normal position cards do not show the mapping dropdown.

Reservation reminder:

- Normal committees keep the existing reservation reminder for their school-level bucket.
- Mapped committees do not show a single committee-level reservation reminder because their positions can belong to different buckets.

Warnings and sorting:

- Candidate sorting for a normal position uses the owning committee mode.
- Candidate sorting for a mapped position uses the mapped target's mode.
- Mismatch indicators for assigned delegates use the position's effective school level.
- If a mapped position is unmapped, sorting stays neutral and mismatch warnings are not shown for school level because the expected level is unknown.

## Top Bar And Capacity Stats

Existing high-school and university capacity calculations should count each slot by effective position school level:

- Normal committee slots count by committee mode.
- Mapped committee slots count by mapped target committee mode.
- Unmapped mapped positions are excluded from HS/Uni capacity calculations and should be represented as incomplete configuration rather than silently counted as university.

## Update And Transfer Rules

MyMUN update:

- Continue to import IPC committees and positions from `Countries` as normal committee and position rows.
- Preserve committee mode for retained committees.
- Preserve position mappings for retained positions when the mapped target committee still exists and is not mapped.
- Drop mappings whose target committee no longer exists.
- Drop mappings whose target committee became mapped during update reconciliation. Repository validation prevents this through normal organizer actions, but update reconciliation must not leave invalid graph state.

Import/export:

- `.munassign` transfer should include committee modes and position mappings when implemented.
- The mapping is organizer-managed state and belongs to the conference snapshot.

## Documentation

Update the main docs during implementation:

- `docs/DESIGN.md`: describe mapped committees in the assignment board workflow.
- `docs/DATA_MODEL.md`: record committee mode, position mapping, preservation rules, and constraints.
- `docs/TODO.md`: remove or rewrite the IPC TODO item once the feature is implemented.

No FAQ question is currently needed because the user confirmed explicit dropdown mapping.

## Testing

Follow TDD with focused tests:

- Domain tests for effective school level on normal and mapped positions.
- Repository tests for committee mode migration/read-write behavior.
- Repository tests for valid and invalid position mappings.
- Repository update tests proving retained mappings survive MyMUN updates and invalid/stale mappings are cleared or rejected.
- UI model tests for mapped committee state, dropdown options, incomplete position state, reservation reminder behavior, candidate sorting, and mismatch counts.
- Stats tests for counting mapped slots into the correct high-school/university capacity buckets.

## Out Of Scope

- Automatic IPC position-to-committee matching.
- Mapping a position to another mapped committee.
- Position-level manual HS/Uni overrides without a committee correspondence.
- Any special IPC workbook parsing beyond the existing MyMUN `Countries` import.
