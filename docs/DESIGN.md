# MUN Assignment Manager — Technical Design Document

---

## 1. Requirements

### 1.1 Conference Management
- The application stores data per conference. Conferences can be created and deleted. Deleting a conference removes all associated data.

### 1.2 Excel Import
- The user can upload an Excel file exported from MyMUN. The application parses the file and fully overwrites the delegate and preference data for the current conference. No merging or conflict resolution is performed — the import is a clean replace. Email is stored as a field and used as the resolution key when matching delegates during conference import (see 1.9).

### 1.3 Main View
- The main view shows all committees as vertical columns. Each column contains a list of positions. Each position shows its slots, how many delegates are interested in it, and — if no delegate is assigned — lists those interested delegates inline.
- Double delegation is supported: a position can have multiple slots.

### 1.4 Slot Assignment
- A slot can be in one of three states: **Empty**, **Pending**, or **Assigned**.
- Delegates can be set as pending via right-click context menu, and later confirmed as assigned the same way.
- It must also be possible to manually add a delegate to a slot even if they did not express interest in it.
- A delegate who is assigned (confirmed) is visually greyed out wherever else they appear in the UI.

### 1.5 Delegate Side Panel
- Clicking a delegate anywhere in the UI opens a side panel showing all stored information about them.
- The panel shows their current assignments first, then their preferences. Clicking an assignment or preference navigates to that slot in the main view.
- It is possible to add free-text comments on a delegate.
- A delegate can be deactivated. Deactivated delegates are hidden from the main view and list panel.

### 1.6 Unpaid Delegates
- Some delegates may not have paid their conference fee. Unpaid delegates are hidden from all assignment views and the delegate list panel. The top bar shows their count separately so the organiser is aware.

### 1.7 Top Bar Statistics
- The top bar permanently shows: total active delegates, unpaid delegate count, number of open slots, pending slots, and assigned slots.

### 1.8 Delegate List Panel
- A panel can be opened showing all active, paid delegates as a filterable and sortable list.
- Filter options include: unassigned only, by assignment difficulty (computed score), by committee interest.
- Clicking a delegate in this panel opens the side panel for them.

### 1.9 Conference Export / Import
- A conference — including all committees, positions, slots, delegates, preferences, assignments, and comments — can be exported to a `.munassign` file to be shared with another organiser.
- Importing a `.munassign` file fully overwrites the current conference with the contents of the file.
- Export and import are always scoped to a single conference.

---

## 2. Technology Stack

| Layer | Technology | Rationale |
|---|---|---|
| UI | Compose Multiplatform (Desktop) | Kotlin-native declarative UI, rich state management |
| State | ViewModel + StateFlow | Unidirectional data flow, testable |
| Local DB | SQLite via **JetBrains Exposed** ORM | Lightweight, embedded, works offline |
| Excel Parsing | **Apache POI** | Industry standard for .xlsx/.xls |
| DI | **Koin** | Lightweight, Kotlin-first |
| Build | **Gradle (Kotlin DSL)** | Standard for Compose Desktop |
| Packaging | `compose.desktop.application` (MSI/DMG/deb) | Native installers per platform |

Note: `kotlinx.serialization` is no longer needed — the exchange format is SQLite, not JSON.

---

## 3. Project Structure

Three Gradle modules. `:domain` is pure Kotlin with no framework dependencies. `:data` implements the domain interfaces. `:app` owns all UI code and wires everything together via Koin.

```
MunAssign/
├── settings.gradle.kts
├── build.gradle.kts                 # root: shared plugin versions only
├── gradle/
├── docs/                            # architecture notes, screenshots, user guide
│
├── domain/                          # pure Kotlin — no framework deps
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/org/munika/assignment/domain/
│       │   ├── model/               # Conference, Committee, Position, Slot, Delegate, Preference
│       │   ├── repository/          # interfaces only
│       │   └── usecase/
│       └── test/
│
├── data/                            # Exposed + SQLite + Apache POI
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/org/munika/assignment/data/
│       │   ├── db/
│       │   │   ├── DatabaseFactory.kt
│       │   │   ├── tables/
│       │   │   └── repository/      # implements domain interfaces
│       │   └── excel/
│       └── test/
│
└── app/                             # Compose UI + ViewModels + DI wiring
    ├── build.gradle.kts
    └── src/
        ├── jvmMain/kotlin/org/munika/assignment/
        │   ├── main.kt
        │   ├── App.kt
        │   ├── Platform.kt
        │   ├── di/
        │   │   └── AppModule.kt     # Koin: binds *Impl to interfaces
        │   └── ui/
        │       ├── theme/
        │       ├── viewmodel/
        │       └── screen/
        │           ├── ConferenceScreen.kt
        │           ├── MainScreen.kt
        │           ├── component/
        │           │   ├── TopStatsBar.kt
        │           │   ├── CommitteeColumn.kt
        │           │   ├── PositionCard.kt
        │           │   ├── SlotRow.kt
        │           │   ├── DelegateSidePanel.kt
        │           │   ├── DelegateListPanel.kt
        │           │   └── SlotContextMenu.kt
        │           └── dialog/
        │               ├── AddDelegateToSlotDialog.kt
        │               └── ImportExportDialog.kt
        └── jvmTest/
```

**Module dependency rule**: `:app` declares a dependency on both `:domain` and `:data`, but only `AppModule.kt` references `:data` for binding. All other `:app` code depends only on `:domain` interfaces.

| Module | Depends on | Key deps |
|---|---|---|
| `:domain` | — | kotlinx-coroutines-core |
| `:data` | `:domain` | Exposed, sqlite-jdbc, Apache POI |
| `:app` | `:domain`, `:data` | Compose Desktop, Koin, mpfilepicker |

---

## 4. Domain Model

The exact fields of each model are determined once the actual MyMUN export format is known. The following describes the structure and responsibilities of each model, not its concrete fields.

- **Conference** — top-level container; all other data belongs to a conference.
- **Committee** — a committee within a conference (e.g. Security Council). Has a name and abbreviation.
- **Position** — a seat within a committee (e.g. a country or named role). A position has one or more slots to support double or larger delegations. Derived data such as interest counts are computed from preferences at query time, not stored on the model.
- **Slot** — one assignable seat within a position. Carries status (Empty / Pending / Assigned) and an optional reference to the assigned delegate.
- **Delegate** — a person registered for the conference. Carries personal information from the MyMUN export, a free-text comments field managed by the organiser, an active/inactive flag, a paid/unpaid flag, and a numerical difficulty score recomputed whenever preferences change.
- **Preference** — a ranked expression of interest from a delegate toward a position. The rank ordering comes from the MyMUN export.

---

## 5. Excel Import

The exact column structure of the MyMUN export is not assumed here — it must be inspected against a real export before implementation. The parser should map columns flexibly using a configurable alias table rather than hardcoded column indices.

The import is a **full overwrite**: all delegates and preferences for the current conference are replaced by the contents of the file. No merging or conflict resolution is performed.

---

## 6. Difficulty Score

Each delegate carries a single integer difficulty score. A lower score means the delegate is easier to place. The score is recomputed and persisted whenever preferences change. It is one of several sort and filter options in the delegate list panel and is not displayed directly to the user.

---

## 7. Conference Export / Import

Export produces a `.munassign` file, which is a self-contained SQLite database holding a complete snapshot of a single conference — committees, positions, slots, delegates, preferences, assignments, and comments.

This format is chosen because it is identical in structure to the application's own database. Export is a filtered copy of the relevant conference rows; import is a bulk insert that overwrites the current conference. There is no translation layer and no separate serialization format to maintain.

Import fully overwrites the target conference. The operation is scoped to one conference on both sides: one conference is exported, and it replaces exactly one conference on the receiving end.

---

## 8. UI Layout

### 8.1 Top Bar
Always visible. Shows: conference name, total active paid delegates, count of unpaid delegates (as a separate muted indicator), open slot count, pending slot count, assigned slot count. Also contains buttons to open the delegate list panel and the import/export dialog.

### 8.2 Main View
Horizontally scrollable row of committee columns. Each column contains a vertically scrollable list of position cards.

Each position card shows:
- Position name and flag (if applicable)
- One row per slot, showing its status and assigned delegate name if any
- Number of interested delegates (those who listed this position as a preference)
- If no delegate is assigned, the interested delegates are listed inline

A delegate who is confirmed-assigned elsewhere is shown greyed out with a strikethrough wherever they appear in other position cards.

### 8.3 Slot Context Menu (right-click)
Available actions depend on current slot state:

| Slot state | Available actions |
|---|---|
| Empty | Set Pending (pick from interested), Add Manually |
| Pending | Confirm Assignment, Clear Slot, Add Manually |
| Assigned | Unassign, Set Back to Pending |

### 8.4 Delegate Side Panel
Opens when any delegate name is clicked. Shows:
1. Personal information from the import
2. Current assignments (confirmed and pending), each clickable to scroll the main view to that slot
3. Preferences in rank order, each clickable to scroll the main view to that position
4. Free-text comments field (auto-saved)
5. Deactivate button

### 8.5 Delegate List Panel
A drawer opened from the top bar. Shows all active, paid delegates.

Filters: unassigned only, by difficulty, by specific committee interest.
Sort options: difficulty score, name, assignment status.

Each entry shows the delegate's name and current assignment status. Clicking opens the side panel.

---

## 9. Key Implementation Notes

### Database location
The SQLite file is stored at `~/.munassign/data.db`. The directory is created on first launch if it does not exist.

### Cascade deletes
All foreign keys use cascade delete. Removing a conference removes all committees, positions, slots, delegates, and preferences in one operation.

### Scroll-to-slot navigation
Each committee column owns a `LazyListState`. When a preference or assignment is clicked in the side panel, the ViewModel emits a navigation event carrying the target position ID; the corresponding column scrolls to it via `animateScrollToItem`.

### Right-click detection
Use `Modifier.pointerInput` with `PointerEventType.Release` and `PointerButton.Secondary` to detect right-clicks and show a `DropdownMenu`.

### Greyed-out rendering
Each delegate displayed in the main view carries its assignment state inline in the UI model. The check for whether to render greyed-out is done locally in the composable without any global lookup.

---

## 10. Development Phases

| Phase | Scope |
|---|---|
| **1 — Foundation** | Gradle multi-module setup, DB schema, `DatabaseFactory`, Conference CRUD |
| **2 — Import** | Inspect real MyMUN export, implement Apache POI parser, full-overwrite import, difficulty score computation |
| **3 — Main UI** | Committee columns, position cards, slot rows, status colours, greyed-out delegates |
| **4 — Assignment** | Right-click context menu, slot state transitions, stats bar |
| **5 — Delegate Panel** | Side panel, scroll-to-slot navigation, comments, deactivate, paid/unpaid flag |
| **6 — List Panel** | Filterable/sortable delegate list |
| **7 — Export/Import** | `.munassign` SQLite export/import, file picker dialogs |
| **8 — Polish** | Keyboard navigation, error states, empty states, native packaging |