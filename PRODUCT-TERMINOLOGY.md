# Product Terminology

## Product-facing terms

- Customer/party and receivable workspace: **طرف‌حساب‌ها و مطالبات**.
- Branch master: **شعب / مدیریت شعب**.
- Receivable: **مطالبات**.
- Collection: **وصول مطالبات**.
- Daily Sales: **فروش روزانه**.
- Accounting Journal: **سند/دفتر ثبت حسابداری** according to screen context.

`CRM` may remain in internal package/class/route identifiers for compatibility and scope control, but it is not a promise of a marketing CRM product. No campaign, loyalty, promotion engine, or marketing-automation feature is implied or introduced by this gate.

## Branch language

`branchId` is identity. Branch name/code are user-facing labels and snapshots. A rename must not alter financial references. Deactivation blocks future operations that require an active branch but does not rewrite history.

## Units and money

- Monetary domain amounts use integer **Long Rial** unless a documented non-monetary calculation requires another type.
- Quantities use the existing quantity representation and explicit units; conversion occurs at domain boundaries rather than UI guesswork.
- Business date uses epoch-day/local business-day semantics where records are date-scoped; event/audit ordering uses epoch-millisecond timestamps.


## Top-level navigation

The Phase-3 top-level product navigation is **خانه، کنترل، عملیات، مالی، بیشتر**. POS, restaurant table, reservation, waiter ordering, KDS, and kitchen-ticket terminology must not appear as active production navigation.

## Phase 3 control terminology

- `مسائل مدیریتی` = evidence-backed management issues/anomalies requiring action.
- `وظایف` = management action items with the canonical TODO → IN_PROGRESS → WAITING_APPROVAL/COMPLETED lifecycle.
- `چک‌لیست‌ها` = operational control templates and runs with required Pass/Fail evidence and approval.
- `گزارش روزانه مدیریت` = Daily Management Brief; it is not a generic accounting report and must preserve unavailable-vs-zero semantics.
- `مطالبات` / `طرف‌حساب‌ها` are the product terms; marketing CRM terminology is not used as a user-facing module identity.
