# Payroll Calculation Specification

## Contract

Entry point: `PayrollCalculationService.calculate(PayrollCalculationCommand)`

این service pure و deterministic است. هیچ clock، DAO، current employee، current contract یا current policy را داخل محاسبه نمی‌خواند. تنها ورودی آن `PayrollInputSnapshot` و componentهای دستی approved و advance deduction approved است.

با ورودی یکسان، ترتیب componentها، amountها، gross/deductions/net و warningها یکسان است.

## Money و rounding

- واحد پول: Rial؛
- storage/calculation: signed `Long` با safe range `MoneyRial`؛
- نسبت‌ها: basis points؛
- تقسیم‌ها: `FixedPointRatio.multiplyDivide`؛
- rounding: `HALF_UP`؛
- overflow: checked arithmetic و typed/business failure؛
- `Float`/`Double` برای حقوق ممنوع است.

## Snapshot input

| Input | معنا |
|---|---|
| employeeId/code/displayName | هویت نمایشی frozen |
| contractId/version | قرارداد منبع |
| baseSalaryRial | حقوق پایه قرارداد |
| standardPeriodMinutes | کل دقیقه استاندارد دوره |
| eligiblePeriodMinutes | دقیقه قابل پرداخت پس از hire/termination/partial period |
| actualWorkMinutes | کارکرد مشتق‌شده از attendance |
| overtimeMinutes | اضافه‌کاری policy-resolved |
| absenceMinutes | غیبت غیرمرخصی |
| lateMinutes | تأخیر قابل کسر |
| paidLeaveMinutes | مرخصی باحقوق؛ بدون کسر |
| unpaidLeaveMinutes | مرخصی بدون حقوق؛ component کسر مستقل |
| payrollPolicyId/version | policy frozen |
| overtime rate/multiplier | منبع محاسبه اضافه‌کاری |
| insurance/tax basis points | پارامتر قانونی/سیاستی |
| calculationVersion | نسخه الگوریتم |

## فرمول‌ها

### حقوق پایه دوره

$$
BasePay = round_{HALF\_UP}\left(BaseSalary \times \frac{EligibleMinutes}{StandardPeriodMinutes}\right)
$$

این فرمول partial month، استخدام در میانه دوره و خاتمه در میانه دوره را پوشش می‌دهد. `eligiblePeriodMinutes` نمی‌تواند بیشتر از `standardPeriodMinutes` باشد.

### اضافه‌کاری

$$
HourlyOvertime = round_{HALF\_UP}\left(RatePerHour \times \frac{OvertimeMinutes}{60}\right)
$$

$$
OvertimePay = round_{HALF\_UP}\left(HourlyOvertime \times \frac{MultiplierBasisPoints}{10000}\right)
$$

Component، minutes، rate، multiplier policy snapshot و attendance source را قابل trace نگه می‌دارد.

### کسر دقیقه‌ای

برای absence، late و unpaid leave:

$$
Deduction = round_{HALF\_UP}\left(BaseSalary \times \frac{Minutes}{StandardPeriodMinutes}\right)
$$

هر کدام component type مستقل دارد. paid leave در این فرمول وارد نمی‌شود.

### بیمه و مالیات

ابتدا مجموع earning componentها محاسبه می‌شود:

$$
Insurance = round_{HALF\_UP}\left(Earnings \times \frac{InsuranceBp}{10000}\right)
$$

$$
Tax = round_{HALF\_UP}\left(Earnings \times \frac{TaxBp}{10000}\right)
$$

### مبلغ نهایی

$$Gross = \sum Earnings$$

$$Deductions = \sum Deductions$$

$$Net = Gross - Deductions$$

همه component amountها مثبت‌اند. sign در amount encode نمی‌شود؛ `direction` تعیین می‌کند در کدام جمع قرار گیرد. اگر deductions از gross بیشتر شود، `NegativeNetPay` است و Payslip تولید نمی‌شود.

## Component catalog

| Type | Direction معمول | Source |
|---|---|---|
| BASE_SALARY | EARNING | CONTRACT |
| OVERTIME | EARNING | ATTENDANCE |
| BONUS | EARNING | MANUAL_ADJUSTMENT/POLICY |
| ALLOWANCE | EARNING | MANUAL_ADJUSTMENT/POLICY |
| COMMISSION | EARNING | MANUAL_ADJUSTMENT |
| INSURANCE | DEDUCTION | POLICY |
| TAX | DEDUCTION | POLICY |
| ABSENCE_DEDUCTION | DEDUCTION | ATTENDANCE |
| LATE_DEDUCTION | DEDUCTION | ATTENDANCE |
| UNPAID_LEAVE_DEDUCTION | DEDUCTION | LEAVE |
| ADVANCE_DEDUCTION | DEDUCTION | ADVANCE |
| LOAN_DEDUCTION | DEDUCTION | ADVANCE |
| OTHER_EARNING/DEDUCTION | جهت صریح | MANUAL_ADJUSTMENT |

`LEGACY_TOTAL` فقط semantic migration marker است؛ migration component fake نمی‌سازد.

## Manual adjustment

Adjustment قبل از calculate ثبت و توسط actor متفاوت approve می‌شود. داده اجباری:

- component type/direction؛
- amount مثبت؛
- reason؛
- creator/approver/timestamps؛
- optional attachment metadata؛
- idempotency/correlation.

Adjustment `SUBMITTED` blocking exception است. adjustment approved پس از consume شدن به payslip مشخص linked می‌شود و برای batch دیگر مصرف نمی‌شود.

## Advance deduction

Command محاسبه درخواست allocation را مشخص می‌کند. allocator مجموع درخواست را با principal - settled مقایسه می‌کند. over-allocation، amount صفر/منفی و allocation تکراری رد می‌شود. allocation مالی هنگام approval نهایی ثبت می‌شود، نه هنگام preview calculation.

## Attendance و leave input resolution

برای هر business day:

1. approved correction در اولویت است؛
2. event summary واقعی در اولویت دوم؛
3. legacy daily summary فقط compatibility fallback است؛
4. approved leave paid/unpaid status را override می‌کند؛
5. anomaly blocking به Exception Center ارسال می‌شود.

Fake clock event یا policy historical ساخته نمی‌شود.

## Exceptions قبل از calculate/approve

- employee inactive/invalid termination range؛
- zero/multiple effective contract؛
- missing payroll policy؛
- attendance anomaly؛
- adjustment تأییدنشده؛
- negative net؛
- advance over-allocation؛
- old active payslip بدون revision/replacement صحیح؛
- incomplete snapshot/component.

Blocking exception اجازه transition batch به `CALCULATED` را نمی‌دهد. خطاها به‌صورت draft exception روشن باقی می‌مانند و هیچ اثر Accounting/Treasury ندارند.

## Snapshot hash و immutability

پس از calculate، snapshot + ordered components + calculation parameters + totals hash می‌شوند. Approval:

1. snapshot/detail completeness را می‌سنجد؛
2. hash را از داده ذخیره‌شده دوباره محاسبه می‌کند؛
3. equations gross-deductions=net را بررسی می‌کند؛
4. سپس accrual را post و financial rows را freeze می‌کند.

تغییر بعدی employee/contract/policy/attendance روی این hash یا history اثری ندارد.

## Accounting derivation

`PayrollAccountingPlanner` component ledger را به semantic lines تبدیل می‌کند:

- base/commission/other earning -> Salary Expense؛
- overtime -> Overtime Expense؛
- bonus -> Bonus Expense؛
- allowance -> Allowance Expense؛
- insurance/tax -> payable؛
- advance/loan -> Employee Advance Receivable offset؛
- net -> Payroll Payable.

Concrete account code فقط در Accounting semantic mapping قرار دارد.

## Warningها

Warning non-blocking فعلی:

- `NO_RECORDED_WORK` وقتی کارکرد و paid leave هر دو صفرند؛
- `EXCESSIVE_OVERTIME` وقتی overtime از standard period بیشتر است.

Anomalyهای attendance و missing contract/policy warning نیستند؛ blocking exception هستند.
