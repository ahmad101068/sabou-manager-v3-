# UI Component Inventory

The inventory records the current reusable surface; it does not create a new design system.

| Category | Current pattern/components | Standardization rule |
|---|---|---|
| App bars | `ProfessionalTopBar` and route-specific top bars | reuse consistent back/title/subtitle behavior |
| Cards | Material 3 `Card` plus existing summary/metric cards | use for grouped content; no duplicate business calculation inside card |
| Buttons | `Button`, `OutlinedButton`, `TextButton` | primary/secondary/destructive intent must be visually distinct through existing theme semantics |
| Chips | Material 3 filter/status chips already used in screens | status text must map to domain state, not infer state from color |
| Dialogs | `AlertDialog` and existing confirmation/edit dialogs | validate input and call authorized command path |
| Fields | `OutlinedTextField` and established selectors | branch input should resolve to canonical Branch master |
| Tables/lists | existing `LazyColumn`, row/table helpers, report layouts | stable numeric IDs as keys when available; empty/unavailable distinguished |
| Navigation | `AppScreen`, route-group hubs, access mapping | screen visibility complements, never replaces, application authorization |
| Empty states | screen-specific explanatory text/cards | tell user the next valid action; first Branch setup is a functional example |
| Error states | `UiErrorHandler`, screen message/state | no silent `catch`; no raw sensitive exception dump |
| Summary components | dashboard/financial summary models and cards | source values from canonical services; no UI financial formula fork |

`ManagementDataGrid`: **NOT CREATED — Phase 3 design/consolidation item**.
