# Agent Notes

Always read these project docs before substantial work:

- `docs/DESIGN.md`
- `docs/DATA_MODEL.md`
- `docs/TODO.md`
- `docs/FAQ.md`

## Terminology

- MyMUN Excel ingestion is called an **update**.
- `.munassign` file transfer is called **import/export**.
- MyMUN-confirmed assignments come from the `Countries.Delegates` column.
- App-created assignments are pending until MyMUN confirms them in a later update.

## Kotlin Style

- Follow official Kotlin coding conventions.
- Use four-space indentation and keep files ASCII unless the file already requires Unicode.
- Keep one top-level domain concept per file where practical. File names should match the primary type.
- Use `val` by default. Use `var` only for intentional state transitions owned by the object.
- Give public functions and properties explicit return types, except trivial Compose entry points where the framework convention is clear.
- Prefer expression bodies for short pure transformations. Prefer block bodies when validation, mutation, or sequencing matters.
- Avoid `!!`, `lateinit`, global mutable state, `GlobalScope`, blocking sleeps, and hidden I/O in domain code.
- Use `require` for caller contract violations and `check` for violated internal state.
- Use nullable types deliberately. Do not encode absence as blank strings, sentinel IDs, or magic values.
- Keep imports explicit. Do not use wildcard imports.

## Architecture

- `:domain` is pure Kotlin. It owns business concepts, invariants, repository interfaces, and use cases. It must not depend on Compose, Koin, Exposed, SQLite, or Apache POI.
- `:data` owns SQLite/Exposed persistence, row DTOs, graph mapping, repository implementations, and MyMUN workbook parsing.
- `:app` owns Compose UI, ViewModels, and DI wiring. UI code should depend on domain interfaces; only DI wiring should refer directly to data implementations.
- Domain models should make invalid states hard to represent and expose derived facts such as slot status, display names, current assignments, and answer issues as computed properties.
- Mutating operations should live on the concept that owns the invariant. For example, slot assignment rules belong on `Slot`.
- MyMUN parsing should produce staging objects and validation issues before persistence. Blocking issues must prevent writes.
- Compose functions should be small, stateless where practical, and driven by UI state from ViewModels.
- UI controls and compact status indicators should have helpful tooltips unless their meaning is completely obvious from visible text.

## Documentation Comments

Use KDoc (`/** ... */`) for:

- public domain classes, value objects, enums, and repository/use-case contracts;
- data boundaries such as parsers, staging snapshots, mappers, and database factory objects;
- public functions whose behavior includes persistence, I/O, validation, mutation, or non-obvious invariants;
- constants/standard objects when their IDs or values are persisted externally.

Do not add KDoc for obvious private helpers, simple row DTOs, placeholder UI composables, or comments that merely repeat the declaration. Good comments explain why the type exists, what owns the invariant, what can safely change, or what external contract it satisfies.

## FAQ Workflow

Use `docs/FAQ.md` as the shared question inbox:

- If a decision is unclear and affects implementation, add a concise question to `docs/FAQ.md`.
- If the user answers a FAQ question in chat, incorporate the answer into the relevant project docs or code, then remove that question from `docs/FAQ.md`.
- If the user answers directly in `docs/FAQ.md`, treat that answer as project guidance, incorporate it where needed, then remove or rewrite the question.
- Keep FAQ questions specific and actionable. Do not leave answered questions lingering.

## Running Gradle

The default shell `JAVA_HOME` may point to JDK 25, which currently makes Gradle/Kotlin fail before project compilation with an error like `JavaVersion.parse("25.0.2")`. Run Gradle with the installed Temurin 24 JDK instead:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew check
```

Use `gradlew check` (not `gradlew test`) as the canonical full-verification command: `:app` is a Kotlin Multiplatform module with no `test` task of its own (only `jvmTest`/`allTests`), so `gradlew test` silently skips it and only runs `:domain:test` + `:data:test`. `check` depends on `allTests` and correctly covers all three modules.

If sandboxing blocks access to that JDK or the Gradle cache, request escalation and rerun the same command. Do not report Gradle as blocked until this JDK override has been tried.

## Change Hygiene

- Keep changes scoped to the task.
- Do not rewrite unrelated files for formatting churn.
- Do not revert user changes unless explicitly asked.
- Prefer focused tests near the invariant or parser/mapper behavior being changed.
- Update `docs/TODO.md` whenever a change completes, removes, or materially reshapes listed work.
- Run `git diff --check` before finishing.
