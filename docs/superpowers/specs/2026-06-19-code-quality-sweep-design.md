# Code Quality Sweep Design

## Goal

Improve code quality in two sequential passes while preserving intentional product behavior.

The first pass is a bounded, layered sweep. It removes current build warnings and reviews the most important architectural boundaries for clear defects. After that pass is complete and verified, a separate exhaustive audit will review the remaining codebase file by file.

## Pass 1: Layered Sweep

### Build warnings

- Force fresh compilation of `:domain`, `:data`, and `:app` so cached tasks cannot hide warnings.
- Replace deprecated Compose APIs with their supported equivalents without changing visible behavior.
- Resolve the deprecated Exposed JDBC transaction call using an approach appropriate for the project's JDBC persistence model. Do not migrate the application to R2DBC as incidental cleanup.

### Targeted review

Review code in architectural risk order:

1. Domain invariants and derived state.
2. MyMUN staging, validation, reconciliation, and persistence boundaries.
3. Repository transaction and mutation behavior.
4. ViewModel state transitions and coroutine ownership.
5. Compose state handling and interaction wiring.

Fix issues only when existing documentation, tests, or internal invariants make the intended behavior clear. If a possible issue admits multiple reasonable product behaviors, stop and ask the user before changing it. Record an implementation-affecting unresolved question in `docs/FAQ.md` when it cannot be answered immediately.

### Change constraints

- Preserve existing behavior unless a defect is provable from project rules.
- Keep refactors local to the warning or defect being addressed.
- Avoid formatting churn and unrelated renaming.
- Add or strengthen focused tests before fixing behavioral defects.
- Update `docs/TODO.md` when the warning-cleanup work is completed or materially reshaped.

## Pass 2: Exhaustive Audit

Start only after Pass 1 is complete and verified. Review every production Kotlin file and relevant build configuration, looking for:

- violations of documented architecture or Kotlin style;
- invalid-state opportunities and misplaced invariants;
- lifecycle, concurrency, transaction, or state-flow hazards;
- persistence and update edge cases;
- dead, duplicated, overly coupled, or misleading code;
- missing tests around high-risk behavior.

The same behavior-change gate applies: clear defects may be fixed with tests, while ambiguous behavior must be presented to the user first. Findings should be handled in small coherent batches rather than one repository-wide rewrite.

## Verification

Each implementation batch must run the closest focused tests. At the end of each pass, force a fresh all-module test run with Temurin 24 and warnings enabled:

```powershell
$env:JAVA_HOME='C:\Users\jeanp\.jdks\temurin-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :domain:test :data:test :app:jvmTest --warning-mode all --rerun-tasks
```

Completion also requires `git diff --check`, zero Kotlin compiler warnings attributable to project source, and a concise report of changes, verification evidence, and any intentionally unresolved concerns.
