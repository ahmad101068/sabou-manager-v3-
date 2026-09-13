# UI Standards

This is a standardization guide for the current Compose UI, not a Phase-3 redesign specification.

## Layout

- Screen horizontal padding: normally 16 dp; preserve established screen-specific density only when needed for existing grids/forms.
- Spacing scale: prefer 4/8/12/16/24 dp rather than one-off values.
- Cards: use Material 3 `Card`/existing project summary cards, with content padding typically 12–16 dp.
- Radius and semantic colors come from `MaterialTheme`; avoid hard-coded business-state colors where theme tokens already express error/primary/surface roles.
- Typography: title → section heading → body → supporting text using Material 3 typography hierarchy.

## State convention

Every data screen should be able to represent **Loading**, **Content**, **Empty**, **Error**, and **Unavailable** where the underlying domain distinguishes unavailable data from zero/empty data. Unavailable financial evidence must not be rendered as a fabricated zero/actual.

## Compose/ViewModel convention

- ViewModels expose `StateFlow` and immutable UI-state data classes.
- User actions are explicit callbacks/commands.
- Business formulas and authorization rules live outside composables and are delegated to domain/repository/application services.
- `UiErrorHandler` is the shared user-safe error conversion path; do not swallow exceptions silently.
- Stable IDs are preferred as Lazy list keys; display names must not be used as entity identity.

## Forms

- Validate required values before submit and revalidate in the domain/repository boundary.
- Branch-scoped forms select/resolve a Canonical Branch rather than inventing an ID from free text.
- Destructive/reversal actions need clear user intent and existing authorization/audit paths.

## Tables and grids

`ManagementDataGrid` is the Phase-3 canonical management grid foundation. It provides reusable header/row/cell, money/quantity cells, status/trend semantics, command/summary areas, inline edit/warning states, and stable-key lazy rendering. Desktop/tablet may use the full grid; mobile must use `MobileSmartRow`/progressive disclosure rather than compressing desktop columns. Permanent `TextField` cells and heavy Excel-style borders are forbidden.

## Shared formatting

Use shared project formatters for Rial amounts, quantities and date/time where available. Screen-local duplicate formatters should be consolidated only after runtime tests if the change spans multiple business screens.

## Phase 3 responsive ERP baseline

- `<600dp`: compact mobile layout with the five-destination bottom navigation and `MobileSmartRow`/smart cards.
- `600–839dp`: medium layout with a persistent Navigation Rail and adaptive management grids.
- `>=840dp`: expanded ERP layout with labeled rail, denser grids, and multi-column management surfaces.
- `ManagementDataGrid` is the canonical tablet/desktop tabular surface. Permanent text fields in view-mode cells are prohibited.
- Operational branch identity is always selected with `CanonicalBranchSelector`; branch names are display snapshots only.
- Management workflow has dedicated surfaces for Issues, Tasks, Checklists and Daily Brief. Loading/unavailable/error states must not fabricate financial zeroes.
