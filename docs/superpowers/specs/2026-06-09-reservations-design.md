# Reservations Design

## Purpose

Reservations let organizers hold capacity for groups that have promised delegates before those
delegates have fully appeared in MyMUN. They answer two operational questions:

- How many more high-school delegates can still be accepted?
- How many more university delegates can still be accepted?

Reservations are organizer-managed conference data. They are not slot assignments, not
MyMUN-confirmed assignments, and not tied to specific committees or positions.

## Product Scope

The first reservations implementation includes:

- standalone reservation creation before any delegates apply;
- a non-modal delegation overview similar to the delegate overview;
- linking reservations to MyMUN delegations;
- manually linking reservations to individual delegates;
- persisted reservation usage rows for both manual and automatic consumption;
- automatic reservation usage for linked delegations;
- HS/Uni accept-capacity counters;
- per-committee reservation reminders for matching committee types.

Reservation management may use a compact modal. Delegation management must use a side panel or
overview surface that keeps the board visible and does not block the main workflow.

## Domain Model

Add a `Reservation` domain concept owned by `Conference`.

Fields:

- stable application id;
- conference;
- organizer-managed name;
- reserved high-school count;
- reserved university count;
- optional linked `Delegation`;
- created and updated timestamps.

Both HS and university counts are allowed on one reservation, even though real delegations normally
use only one bucket.

Add a `ReservationDelegateLink` domain concept.

Fields:

- stable application id;
- reservation;
- delegate;
- source, either `Manual` or `DelegationAuto`;
- created and updated timestamps.

Delegations remain MyMUN-derived context objects. Their identity fields, such as name and country,
are read-only in MunAssign. Delegations can be managed and inspected, but organizers do not edit
their MyMUN identity.

## Reservation Usage Rules

A delegate can consume at most one reservation in a conference.

Deactivated delegates never consume reservation capacity. Deactivating a delegate removes any
manual or automatic reservation consumption for that delegate.

Manual links represent explicit organizer intent:

- an active manually linked delegate consumes the linked reservation;
- manual consumption does not require the delegate to be paid, accepted, assigned, or submitted;
- changing a delegate's reservation moves the single manual link to the new reservation.

Automatic links are maintained by reconciliation:

- only reservations linked to a delegation receive automatic links;
- only active, paid, accepted delegates in that delegation are eligible;
- high-school or university bucket is decided only by the configured high-schooler question answer;
- eligible delegates are processed by submission date, earliest first;
- delegates without a submission date sort after delegates with a submission date;
- ties are broken deterministically by delegate display name, then delegate id;
- delegates that already consume any reservation are skipped;
- automatic links stop when the relevant reservation bucket is full;
- extra eligible delegates simply do not consume the reservation.

There is no over-use state. If a linked delegation has seven eligible delegates and five reserved
places, the first five consume the reservation and the remaining two are ordinary accepted delegates.

When a reservation count is reduced, the repository must reject reductions below current manual
usage for that bucket. Valid reductions preserve manual links and reconciliation removes excess
`DelegationAuto` links above the new capacity.

## Reconciliation

Reservation reconciliation runs after:

- MyMUN update;
- reservation creation, edit, or deletion;
- reservation-to-delegation link changes;
- manual delegate reservation assignment, movement, or removal;
- delegate deactivation or reactivation.

Reconciliation must:

- preserve organizer-managed reservations across MyMUN updates;
- preserve manual reservation links for retained active delegates;
- remove links for deleted or deactivated delegates;
- clear automatic links for reservations whose delegation link no longer exists;
- rebuild `DelegationAuto` links from current reservation, delegation, and delegate data.

If a linked MyMUN delegation disappears during an update, keep the reservation and clear the
delegation link. The reservation remains available for manual relinking.

Persisted `ReservationDelegateLink` rows are the inspectable current usage state. Reconciliation is
the maintenance rule that keeps automatic rows aligned with current data.

## Capacity Counters

The UI must show clear HS/Uni accept-capacity numbers.

Accepted active paid delegates consume capacity even if they are not assigned to a slot yet. This is
because accepted delegates are organizer commitments.

Accepted means the same actionable MyMUN application states already used by the app's top-bar
application count: `Accepted` or `Assigned`.

Remaining reservation capacity also consumes accept capacity. For each bucket:

```text
can_accept = total_slots_for_bucket - active_paid_accepted_delegates_for_bucket - remaining_reserved_capacity_for_bucket
```

Where:

- bucket is high school or university;
- total slots for the high-school bucket are slots in committees marked as high-school committees;
- total slots for the university bucket are slots in committees not marked as high-school committees;
- delegate bucket is based only on the configured high-schooler question answer;
- deactivated delegates consume no capacity;
- consumed reservation links reduce the remaining reserved capacity.

The exact label should emphasize acceptance, not only assignment, for example:

```text
Can accept: 12 HS · 18 Uni
```

## UI Design

Add top-level access to delegation/reservation workflows near the existing delegate and issue
actions.

Delegation overview:

- opens as a right-side overview/panel, not a blocking modal;
- follows the delegate list pattern closely enough to feel native to the app;
- supports search and useful filters;
- lists delegation name, country, MyMUN expected/current counts, active paid accepted delegate
  count, linked reservations, consumed reservation count, and delegates;
- shows MyMUN identity fields as read-only;
- lets organizers open the reservation manager for a delegation and link or unlink reservations.

Reservation manager:

- may be a compact modal;
- supports creating, editing, and deleting reservations;
- edits reservation name, HS count, university count, and optional linked delegation;
- shows consumed delegates grouped by `Manual` and `DelegationAuto`;
- allows removing manual delegate links;
- allows moving a delegate to a different reservation;
- shows automatic links as generated by reconciliation, not directly edited one by one.

Delegate detail:

- shows the delegate's current reservation, if any;
- lets organizers assign, change, or remove a manual reservation link;
- if an automatic link exists, changing it creates an explicit manual link to the chosen
  reservation and reconciliation will skip that delegate for automatic consumption elsewhere.

Board reminders:

- high-school committee columns show a compact high-school reservation reminder;
- university committee columns show a compact university reservation reminder;
- the reminder should include total and remaining reservation capacity for that bucket;
- clicking the reminder opens the reservation manager;
- reminders do not bind reservations to committee slots.

## Persistence

Add tables for reservations and reservation delegate links.

`reservations`:

- `id` primary key;
- `conference_id` with cascade delete;
- `name`;
- `high_school_count`;
- `university_count`;
- nullable `delegation_id`;
- `created_at_epoch_millis`;
- `updated_at_epoch_millis`.

`reservation_delegate_links`:

- `id` primary key;
- `reservation_id` with cascade delete;
- `delegate_id` with cascade delete;
- `source`;
- `created_at_epoch_millis`;
- `updated_at_epoch_millis`.

Constraints:

- reservation counts must be zero or greater;
- at least one reservation count should be greater than zero;
- a delegate can have at most one reservation link in a conference;
- manual reservation assignment and reservation count edits must not create over-use;
- source must be one of the known reservation link source values.

Repository operations:

- create reservation;
- update reservation;
- delete reservation;
- link or unlink reservation to delegation;
- manually assign delegate to reservation;
- move delegate reservation;
- remove delegate reservation;
- reconcile reservation links;
- load complete conference graph including reservations and links.

## Documentation Updates

When implementing, update:

- `docs/DESIGN.md` for reservation product rules and UI requirements;
- `docs/DATA_MODEL.md` for persistence and MyMUN update behavior;
- `docs/TODO.md` to remove or rewrite the reservation TODO item.

No FAQ question is currently needed because the open decisions in this design have been answered.

## Testing

Add focused domain and repository/UI model tests for:

- manually linked active delegates consume reservations even if unpaid or not accepted;
- deactivation removes manual and automatic reservation consumption;
- delegation auto-links use active paid accepted delegates by submission date;
- manually linked delegates are skipped by auto-fill;
- one delegate cannot consume two reservations;
- reducing reservation count below manual usage is rejected;
- valid reservation count reductions remove excess automatic links while preserving manual links;
- MyMUN update preserves reservations and rebuilds automatic links;
- disappeared linked delegation clears the reservation's delegation link but preserves the
  reservation;
- HS/Uni capacity counters use high-schooler question answers and remaining reservation capacity;
- board committee reminders show only the matching bucket.

## Out Of Scope

This design does not include:

- editing MyMUN delegation identity fields;
- unspecified or mixed reservation buckets beyond the existing HS and university counts;
- tying reservations to specific committees, positions, or slots;
- warning about over-use, because automatic consumption simply stops at the reservation count;
- changing or overriding a delegate's high-schooler answer.
