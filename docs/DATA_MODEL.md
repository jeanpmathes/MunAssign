# Data Model

This document records persistence and update rules that are not obvious from the Kotlin model classes. The detailed domain shape lives in `domain/src/main/kotlin/org/munika/assignment/domain/model`; the database row and table shape lives in `data/src/main/kotlin/org/munika/assignment/data/db`.

## Ownership Boundaries

- `Conference` owns all assignment data and stores organizer-managed display order.
- `Committee` owns positions, the uniform `slotsPerPosition` value, and the organizer-managed committee mode.
- `Position` represents one assignable country or role from `Countries.Member Name` and may store a mapped target committee when its owning committee is in mapped mode.
- `Slot` stores the current delegate reference and assignment source.
- `Delegate` stores MyMUN profile data plus organizer-managed active/inactive state.
- `Question` stores dynamic MyMUN question text plus local handling configuration.
- `Preference` resolves a delegate's ranked MyMUN preference to a position.
- `Delegation` stores delegation context from MyMUN where available.
- `Reservation` stores organizer-managed high-school and university capacity holds, with an optional linked delegation.
- `ReservationDelegateLink` stores current reservation consumption by one delegate, either manual or delegation-automatic.

Domain objects expose derived properties such as slot status, current assignment, display labels, typed answers, and interested delegates. Do not duplicate those derivations in documentation unless the rule affects persistence or updates.

## MyMUN Workbook Scope

| Sheet | Use |
|---|---|
| `Delegates` | Assignable delegates, profile fields, payment state, questions, answers, preferences, current MyMUN assignment hints. |
| `Countries` | Complete source of committees, positions, and confirmed assignments. |
| `Delegations` | Delegation context. |
| `Chairs` | Ignored. |
| `Faculty Advisors` | Ignored, except delegate/head-delegate flags on assignable delegates. |
| `Observers` | Ignored. |

The MyMUN workbook action is called an **update**. `.munassign` file transfer is called **import/export**.

## Update Matching

There is no stable MyMUN delegate ID. Matching should use:

1. Normalized first name and last name.
2. Normalized email when names collide.
3. Zip code and date of birth when name and email still collide.

Email is useful but not authoritative by itself. Duplicate delegates with the same full matching identity are blocking validation errors.

Committee and position matching use normalized workbook names. Committee aliases are display-only and never replace the MyMUN name for update matching.
When an update creates a committee that is not already present in the conference, the initial committee mode is inferred from the MyMUN committee name: `high school` and `highschool` match case-insensitively as `HighSchool`, `IPC` matches case-sensitively as `Mapped`, and `HighSchool` wins when both patterns are present. Existing committees keep their organizer-managed mode on later updates.
Position flags are display-only derived data. They are resolved from bundled Unicode emoji flag names and conservative matching rules at runtime, rendered with bundled Twemoji image assets where available, and are never persisted or used for MyMUN update matching.

## Assignment Rules

Slot status is derived from slot data:

- No delegate means `Empty`.
- Delegate plus `Application` source means `Pending`.
- Delegate plus `Mymun` source means `Assigned`.

One delegate can occupy at most one slot in a conference. A position can have multiple slots because slot count is configured per committee.

Committee mode determines the high-school/university bucket used by its positions:

- `HighSchool` positions count as high-school slots.
- `University` positions count as university slots.
- `Mapped` positions inherit the mode of their mapped target committee.

Mapped targets must be committees in the same conference whose mode is not `Mapped`. This prevents chained or circular IPC correspondences. Multiple positions in one mapped committee may share the same target committee. Unmapped positions in mapped committees are treated as incomplete for board state and do not contribute to high-school or university accept capacity until a target is selected. A mapped committee cannot switch back to high-school or university mode until all of its position correspondences are cleared.

During an update:

- Non-empty `Countries.Delegates` values become confirmed MyMUN assignments.
- Existing pending assignments are preserved where possible when MyMUN does not contradict them.
- If `Countries.Delegates` implies more assigned delegates for a position than the committee slot count allows, the update must abort before writing and name the affected committee/counts.
- If a confirmed delegate cannot be matched, the update must abort before writing.

`Countries.Delegates` is comma-separated and may contain inconsistent whitespace, trailing commas, or single names.

## Question Handling

Questions are stable imported objects with editable local configuration:

- expected role, such as experience level, high-schooler status, or language level;
- internal handling type, such as default, committee-choice explanation, visa support, or delegation membership;
- display visibility and organizer label;
- organizer-managed display order for general questions in delegate detail;
- answer rules;
- role-specific raw-answer mappings.

Questions with an expected role are special profile fields. They are shown in the typed profile area
of delegate detail, not in the general answer overview, and are pinned above reorderable general
questions in the question editor.

General questions can opt into handling archetypes. The visa archetype raises a notice only for
meaningful affirmative answers, while blank values, dashes, and explicit no-style answers are
ignored. The committee-choice explanation archetype warns when the answer has fewer than three
sentences. Role-backed special questions use their role handling and do not expose these general
handling archetypes.

Role-specific mapping tables are intentionally separate so language-level values cannot be mapped into experience-level values, or similar.

High-school status deliberately defaults to university/non-high-school when the high-school role is
not configured, the delegate has no answer, or the raw answer has no configured mapping. This
conservative fallback is intentional; organizers can correct observed raw values through the
question configuration workflow.

Language level produces a derived profile warning when the configured raw answer maps to A1/A2.
B1/B2, C1/C2, missing answers, and unmapped raw values do not warn.

The question configuration dialog includes every distinct nonblank observed raw answer for each
role-backed question. Answers are matched using the same normalization as domain mapping lookup;
configured values remain selected and are not duplicated, while newly observed values are shown as
unmapped. Observed raw values are read-only and cannot be removed from the dialog. Configuration
cannot be saved until every observed value has an explicit typed mapping.

When an update first introduces a distinct nonblank raw answer for a role-backed question and that
normalized value has no role-specific mapping, the update succeeds with an `unmapped_role_answer`
warning naming the question and raw value. Values already present in the conference's persisted
pre-update answers do not warn again, and mapped values never warn.

Answer issues are derived from current profile/question rules and are either:

- `Warning`, meaning the answer may justify follow-up or affect assignment;
- `Notice`, meaning the answer needs operational attention but is not a rejection problem.

Resolved answer issues are organizer-managed state. A resolution stores the delegate, optional question, stable issue key, original issue text/severity, resolution time, and optional justification. Open issue counters and warning markers ignore resolved issues. The issue manager shows open issues first and resolved issues last. MyMUN updates preserve resolutions where the delegate and question still exist; cascade deletion removes resolutions for deleted conferences, delegates, or questions.

## MyMUN-Managed vs Organizer-Managed Data

MyMUN updates replace:

- delegate profile fields;
- payment status;
- committee and position source data;
- preferences;
- question answers;
- confirmed assignments from `Countries.Delegates`.

MyMUN updates preserve where the referenced objects still exist:

- delegate comments;
- delegate active/inactive state;
- pending app assignments not contradicted by confirmed MyMUN assignments;
- committee aliases;
- committee mode and position correspondences;
- question handling configuration.
- organizer-managed reservations;
- manual reservation links for retained active delegates;

MyMUN updates remove delegates, preferences, assignments, and answers that no longer exist in the workbook source.
Automatic reservation links are rebuilt after updates. If a reservation's linked delegation no longer exists, the reservation is preserved but its delegation link is cleared.

## Reservations

Reservations reduce accept capacity before delegates have applied. They are owned by one conference and can hold high-school count, university count, or both. Counts must be zero or greater, and at least one count must be greater than zero.

A reservation may link to one MyMUN delegation. The linked delegation identity itself remains MyMUN-managed; organizers do not edit delegation names or contact fields in MunAssign.

Reservation consumption is persisted in `reservation_delegate_links` so the application does not have to infer current usage from scratch at load time. A delegate can have at most one reservation link in a conference:

- `Manual` links are explicit organizer choices from delegate detail and are preserved while the delegate remains active.
- `DelegationAuto` links are maintained by reconciliation for reservations linked to delegations.

Delegation reconciliation considers active, paid, accepted delegates in submission-date order. It skips delegates that already have any reservation link, fills high-school or university capacity according to the delegate's high-schooler answer, and stops when the relevant reservation bucket is full. When multiple reservations link the same delegation, the oldest-created reservation consumes delegates first; equal creation times are ordered by stable reservation ID. Extra eligible delegates do not consume a reservation and are not an error.

Reducing a reservation count below current manual usage is rejected. Reducing below automatic usage is allowed; reconciliation removes excess automatic links while preserving manual links. Deactivating a delegate removes any manual or automatic reservation consumption for that delegate.

## Tables

The current schema is represented by Exposed table objects under `data/src/main/kotlin/org/munika/assignment/data/db/tables`.

Core tables:

- `conferences`
- `committees`
- `positions`
- `slots`
- `delegations`
- `delegates`
- `reservations`
- `reservation_delegate_links`
- `preferences`
- `delegate_comments`
- `delegate_questions`
- `delegate_question_answers`
- `answer_issue_resolutions`

Question configuration tables:

- `question_roles`
- `question_handling_types`
- `answer_rule_types`
- `delegate_question_rules`
- `mun_experience_answer_mappings`
- `high_schooler_answer_mappings`
- `language_level_answer_mappings`

Update metadata tables:

- `mymun_update_runs`
- `mymun_update_issues`

## Constraints And Indexes

- `committees(conference_id, normalized_name)` unique.
- `positions(committee_id, normalized_name)` unique.
- `slots(position_id, slot_index)` unique.
- `preferences(delegate_id, rank)` unique.
- `preferences(delegate_id, position_id)` unique.
- `delegate_questions(conference_id, question_text)` unique.
- `delegate_questions(conference_id, expected_role)` unique when role is not null.
- Role-specific answer mappings unique by `(question_id, normalized_raw_value)`.
- `reservations(conference_id, delegation_id)` is indexed for delegation-linked reservation lookup.
- `reservation_delegate_links(delegate_id)` unique so one delegate can consume at most one reservation.
- `reservation_delegate_links(reservation_id, delegate_id)` unique.
- Current slot assignments enforce at most one occupied slot per delegate per conference.
- Conference-owned foreign keys cascade on delete.
- `conferences.display_order` stores the organizer-managed order used by the top-bar selector and management screen.
- `committees.mode` stores `HighSchool`, `University`, or `Mapped`; the legacy high-school boolean is derived from the mode for compatibility.
- `positions.mapped_committee_id` stores the optional mapped target committee. The repository clears this value when the owning committee leaves mapped mode, when the target disappears, or when the target becomes mapped.
