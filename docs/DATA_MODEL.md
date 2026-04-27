# Data Model

This document records persistence and update rules that are not obvious from the Kotlin model classes. The detailed domain shape lives in `domain/src/main/kotlin/org/munika/assignment/domain/model`; the database row and table shape lives in `data/src/main/kotlin/org/munika/assignment/data/db`.

## Ownership Boundaries

- `Conference` owns all assignment data.
- `Committee` owns positions and the uniform `slotsPerPosition` value.
- `Position` represents one assignable country or role from `Countries.Member Name`.
- `Slot` stores the current delegate reference and assignment source.
- `Delegate` stores MyMUN profile data plus organizer-managed active/inactive state.
- `Question` stores dynamic MyMUN question text plus local handling configuration.
- `Preference` resolves a delegate's ranked MyMUN preference to a position.
- `Delegation` stores delegation context from MyMUN where available.

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

## Assignment Rules

Slot status is derived from slot data:

- No delegate means `Empty`.
- Delegate plus `Application` source means `Pending`.
- Delegate plus `Mymun` source means `Assigned`.

One delegate can occupy at most one slot in a conference. A position can have multiple slots because slot count is configured per committee.

During an update:

- Non-empty `Countries.Delegates` values become confirmed MyMUN assignments.
- Existing pending assignments are preserved where possible when MyMUN does not contradict them.
- If `Countries.Delegates` implies more assigned delegates for a position than the committee slot count allows, the update must abort before writing and name the affected committee/counts.
- If a confirmed delegate cannot be matched, the update must abort before writing.

`Countries.Delegates` is comma-separated and may contain inconsistent whitespace, trailing commas, or single names.

## Question Handling

Questions are stable imported objects with editable local configuration:

- expected role, such as experience level, high-schooler status, or language level;
- handling type, such as default, committee-choice explanation, visa support, or delegation membership;
- display visibility and organizer label;
- answer rules;
- role-specific raw-answer mappings.

Role-specific mapping tables are intentionally separate so language-level values cannot be mapped into experience-level values, or similar.

Answer issues are either:

- `Warning`, meaning the answer may justify follow-up or affect assignment;
- `Notice`, meaning the answer needs operational attention but is not a rejection problem.

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
- question handling configuration.

MyMUN updates remove delegates, preferences, assignments, and answers that no longer exist in the workbook source.

## Tables

The current schema is represented by Exposed table objects under `data/src/main/kotlin/org/munika/assignment/data/db/tables`.

Core tables:

- `conferences`
- `committees`
- `positions`
- `slots`
- `delegations`
- `delegates`
- `preferences`
- `delegate_comments`
- `delegate_questions`
- `delegate_question_answers`

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
- Current slot assignments enforce at most one occupied slot per delegate per conference.
- Conference-owned foreign keys cascade on delete.
