# TODO

- Replace raw `String` IDs with typed IDs (`ConferenceId`, `DelegateId`, `CommitteeId`, `PositionId`, `SlotId`, `ReservationId`) at the `ConferenceRepository` interface boundary and all call sites. Mechanical but wide-reaching: 93+ occurrences across 15+ files in `app/src/jvmMain` alone, plus the repository interface, implementation, and both modules' tests. Deferred as its own dedicated session rather than bundled into the docs/REVIEW.md fix batch; approach if picked up: change interface signatures first, let the compiler drive call-site fixes module by module (domain → data → app → tests), running `gradlew check` between modules rather than only at the end.
- Reword the "Move pending assignment?" dialog body in `PositionCard.kt` and `AddDelegateToSlotDialog.kt` from "... is already pending for .... Move that pending assignment to ... slot ...?" to something like "... already has a pending assignment for .... Move it to ... instead?" (last outstanding item from the retired `docs/UI_TEXT_REVIEW.md` copy pass).
- Warning for low language level.
- Larger window size / use size at last close.
- Icon
- Export nice PNGs with current assignment status.
- Stress tests with many people.
