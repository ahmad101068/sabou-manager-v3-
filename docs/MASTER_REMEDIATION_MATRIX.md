# MASTER REMEDIATION MATRIX
Phase-1 discovery only. No business logic/Room/schema/migration edits.

## Baseline
`REPO=ahmad101068/sabou-manager-v3-`; `SOURCE=codex/phase3-part3b-20260816`; `PR#3=OPEN,DRAFT,NOT_MERGED`; `BASE=da08692da8b77dfddb3ad129e905f8402f245e5c`; `BRANCH=remediation/canonical-hardening`; `BASELINE_GREEN=YES`; `ROOM=55`; `SCHEMA_CHANGED=NO`; `MIGRATION_ADDED=NO`.
Runs: PR=`32256566511`; TR=`32256566471`; SH=`32256566541`; Runtime=`32256566485`; JVM/API23/API35/16KB PASS.
`AUDIT_PAGES=106`; `OWNER_UAT=15`; `FINDINGS=505`. Evidence wins. NP=no implementation. DB=discovery-only schema risk.
Scope: ERP management only; POS/table/waiter/KDS/order lifecycle/reservation/POS receipt OOS; Daily Sales stays financial aggregate; independent marketing CRM excluded, AR/collections in scope.

## Code legends
CLASS: A=CONFIRMED_ACTIVE_BUG; I=CONFIRMED_INTEGRITY_GAP; S=CONFIRMED_SECURITY_GAP; H=CONFIRMED_HISTORY_GAP; P=CONFIRMED_PERFORMANCE_GAP; PF=PARTIALLY_FIXED; FX=ALREADY_FIXED; FP=FALSE_POSITIVE; D=DUPLICATE_OF_OTHER_FINDING; OOS=OUT_OF_SCOPE_BY_PRODUCT_DECISION; FR=FEATURE_REQUEST; EE=ENTERPRISE_ENHANCEMENT; PD=PRODUCT_DECISION_REQUIRED; T=TEST_REQUIREMENT; DOC=DOCUMENTATION_ONLY; NP=NOT_PROVEN; DB=DB_CHANGE_POSSIBLE.
SEV: C=Critical; H=High; M=Medium; 0=None; ?=Unranked. IMPACT: H/M/L. DB: P=possible, 0=no Phase1 schema change. R=B means same-SHA baseline green and no heavy Phase1 rerun.

### Source aliases
`M03`=HR / Attendance / Payroll :: app/src/main/java/ir/restaurant/management/data/repository/PersonnelAttendanceService.kt :: PersonnelAttendanceService
`M04`=Inventory :: app/src/main/java/ir/restaurant/management/data/repository/LocalInventoryCommandEngine.kt :: LocalInventoryCommandEngine
`M05`=Accounting / Treasury :: app/src/main/java/ir/restaurant/management/data/treasury/LocalTreasuryServiceV2.kt :: LocalTreasuryServiceV2
`M06`=Procurement / Suppliers :: app/src/main/java/ir/restaurant/management/data/repository/LocalProcurementRepository.kt :: LocalProcurementRepository
`M07`=Daily Sales / Revenue :: app/src/main/java/ir/restaurant/management/data/repository/LocalDailySalesRepository.kt :: LocalDailySalesRepository
`M08`=Recipe / Costing / Food Cost :: app/src/main/java/ir/restaurant/management/data/repository/LocalRecipeRepository.kt :: LocalRecipeRepository
`M09`=Receivables / Collections / Customer :: app/src/main/java/ir/restaurant/management/data/repository/LocalReceivableService.kt :: LocalReceivableService
`M10`=Assets :: app/src/main/java/ir/restaurant/management/data/repository/LocalAssetRepository.kt :: LocalAssetRepository
`M11`=Dashboard / Reports :: app/src/main/java/ir/restaurant/management/data/db/DashboardAnalyticsDao.kt :: DashboardAnalyticsDao
`M12`=Branches :: app/src/main/java/ir/restaurant/management/data/repository/LocalBranchRepository.kt :: LocalBranchRepository
`M13`=Users / Permissions / Security :: app/src/main/java/ir/restaurant/management/data/security/SessionAuthorizer.kt :: SessionAuthorizer
`M14`=Audit :: app/src/main/java/ir/restaurant/management/data/repository/LocalAuditEventWriter.kt :: LocalAuditEventWriter
`M15`=Alerts :: app/src/main/java/ir/restaurant/management/data/repository/LocalAlertRepository.kt :: LocalAlertRepository
`M16`=Management Control :: app/src/main/java/ir/restaurant/management/data/repository/LocalManagementWorkflowService.kt :: LocalManagementWorkflowService
`M17`=Settings / Backup / Sync :: app/src/main/java/ir/restaurant/management/domain/operations/CloudSyncConfig.kt :: SyncSafetyGate/Settings
`M18`=Search :: app/src/main/java/ir/restaurant/management/ui/NavigationSettingsScreens.kt :: GlobalSearch UI
`M19`=Supplier Master :: app/src/main/java/ir/restaurant/management/data/db/SupplierDao.kt :: SupplierDao
`M20`=UI / UX :: app/src/main/java/ir/restaurant/management/ui :: Compose UI
`M21`=Performance :: app/src/main/java/ir/restaurant/management/data/db/DashboardAnalyticsDao.kt :: Read paths
`M22`=Architecture / Database / Tests / CI :: app/src/main/java/ir/restaurant/management/data/db/AppDatabase.kt :: AppDatabase/CI

### Evidence aliases
`EA`=current source directly exhibits behavior; `EI`=missing/parallel canonical invariant proven; `ES`=backend permission/scope gap proven; `EH`=current-state leakage/historical gap proven; `EP`=unbounded/heavy/scalability gap; `EPF`=partial fix; `EFX`=audit superseded; `EFP`=defect not supported by current source/tests; `ED`=root-cause duplicate; `EOOS`=product lock; `EFR`=feature; `EEE`=enterprise enhancement; `EPD`=product/domain decision needed; `ET`=test/evidence requirement; `EDOC`=preserved strength; `ENP`=not proven; `EDB`=schema risk only.
Exact overrides: X03-01=ManagementControlDao.kt:68,81 + HrPayrollEntities.kt (legacy payroll_runs vs PayrollV2); X03-02=PersonnelAttendanceService.kt:176-184 (first-in/last-out fallback); X04-14=LocalInventoryCommandEngine.kt:45-49,743-761 (backend auth now present); X07-01=PRODUCT_SCOPE_LOCK; X07-02=DashboardAnalyticsDao.kt:18-28 + LocalDailySalesRepository.kt (parallel sales read); X07-25=NumberAllocationConcurrencyIntegrationTest + DailySalesRepository (cash-reconciliation concurrency passes); X08-01=LocalRecipeRepository.kt:41-89,190-209 (activation partially hardened); X08-08=concurrency test + LocalRecipeRepository (revision concurrency passes); X10-07=LocalAssetRepository.kt:108-127,302-324; X11-01=DashboardAnalyticsDao.kt:18-28 (legacy sales dashboard); X13-06=AppScreenAccess.kt:10-18; X15-01=LocalAlertRepository.kt:17-28,111-132; X15-05=LocalAlertRepository.kt:60-65,80-105; X15-06=LocalAlertRepository.kt:80-86; X15-10=LocalAlertRepository.kt:34-55 + AlertEntities.kt:18-31; X17-15=CloudSyncConfig.kt:5-19 + CloudSyncWorker.kt:10-21; X17-19=NumberAllocationConcurrencyIntegrationTest + LocalSyncRepository; X18-01=NavigationSettingsScreens.kt:113-170; X18-06=AppScreenAccess.kt:10-18; X20-02=ui/* literals; X21-01=data/db + repositories (paging gap); X22-11=AppDatabase.kt:11,138,187; X22-22=.github/workflows + current run IDs.

## Counts
I=26
PF=10
D=165
NP=151
EE=20
FX=5
DB=72
OOS=1
H=9
FP=3
FR=3
PD=2
P=3
S=5
DOC=13
A=2
T=15
TOTAL=505

## Root causes
RC-01|Financial Single Source of Truth / Treasury Ownership|N=52|U=UAT-08|B=H S=M F=H H=M|PHASE 2|DB=M
RC-02|Receivables Atomic Collection / Allocation|N=30|U=-|B=H S=L F=H H=M|PHASE 2|DB=H
RC-03|AP Subledger / Payables Truth|N=0|U=-|B=H S=L F=H H=M|PHASE 2|DB=H
RC-04|Branch & Warehouse Data Scope|N=28|U=-|B=H S=H F=M H=H|PHASE 3 / PHASE 6|DB=H
RC-05|Inventory Canonical Location/Lot/Count|N=17|U=-|B=H S=L F=H H=M|PHASE 3|DB=M
RC-06|Procurement Canonical Workflow & Match|N=33|U=UAT-05|B=H S=M F=H H=M|PHASE 3|DB=H
RC-07|Payroll Single Source of Truth|N=11|U=-|B=H S=L F=H H=H|PHASE 4|DB=M
RC-08|Attendance Single Source of Truth|N=1|U=UAT-12|B=H S=L F=M H=H|PHASE 4|DB=L
RC-09|Personnel Number Allocation|N=0|U=UAT-11,UAT-13|B=M S=L F=L H=M|PHASE 4|DB=L
RC-10|Recipe / Costing Historical Integrity|N=29|U=-|B=H S=M F=H H=H|PHASE 5|DB=M
RC-11|Asset Lifecycle / Accounting|N=39|U=UAT-14,UAT-15|B=H S=M F=H H=H|PHASE 5|DB=H
RC-12|Customer Credit / Merge Historical Integrity|N=0|U=-|B=H S=M F=H H=H|PHASE 2|DB=M
RC-13|Security Authorization / Data Scope|N=23|U=-|B=H S=H F=M H=M|PHASE 6|DB=H
RC-14|Audit Forensic Integrity / Restore-Aware History|N=33|U=-|B=H S=H F=M H=H|PHASE 6|DB=H
RC-15|Alert Domain/Branch Scope & Lifecycle|N=20|U=-|B=H S=H F=M H=M|PHASE 6|DB=H
RC-16|Management Maker-Checker / Immutability|N=28|U=-|B=H S=H F=L H=M|PHASE 6|DB=M
RC-17|Historical As-Of Reporting|N=37|U=-|B=H S=M F=H H=H|PHASE 6|DB=M
RC-18|Backup / Settings Governance + Sync Safety|N=25|U=-|B=H S=H F=L H=H|PHASE 6|DB=M
RC-19|Dashboard / Reporting Canonical Read Models|N=2|U=UAT-10|B=H S=M F=H H=H|PHASE 2 / PHASE 7|DB=M
RC-20|UI Navigation / RTL / Localization / Print / Feedback|N=18|U=UAT-01,UAT-02,UAT-03,UAT-04,UAT-06,UAT-07,UAT-08,UAT-09|B=M S=L F=L H=L|PHASE 7|DB=0
RC-21|Search / Paging / Performance|N=35|U=-|B=M S=M F=L H=M|PHASE 7|DB=M
RC-22|Supplier Master Governance|N=18|U=-|B=M S=M F=M H=M|PHASE 3|DB=H
RC-23|Architecture / Database Integrity / Layer Boundaries|N=25|U=-|B=M S=M F=M H=M|PHASE 6|DB=M
RC-24|Release Evidence / Same-SHA Gates|N=1|U=-|B=M S=L F=L H=L|PHASE 8|DB=0

## Owner UAT
UAT-01|A|NavigationSettingsScreens.kt:359-377|Settings Operations duplicates execution|P7
UAT-02|A|ManagementWorkflowScreens.kt:504-507|English Brief labels|P7
UAT-03|PF|NavigationSettingsScreens.kt + SupplierOperationsScreens.kt|Procurement/Supplier IA partly separated|P7
UAT-04|FX|InventoryWorkspaceScreen.kt:147-149|counts/waste/transfers now distinct|P7 regression
UAT-05|FX|OperationsViewModel.kt:377-385 + ProcurementUseCases.kt + LocalProcurementRepository.kt:388-438|approve/reject chain wired with permission/transaction/SoD|P7 regression
UAT-06|A|PurchaseOperationsScreens.kt:381+|purchase-card contrast|P7
UAT-07|NP|OperationsViewModel.kt|reported refresh control not statically reproduced|P7 targeted UAT
UAT-08|A|TreasuryScreen.kt:57+|raw CUSTOMER_RECEIVABLE + RTL/formatting|P2 then P7
UAT-09|A|ReportPrinter.kt:25-71|ERP print presentation|P7
UAT-10|NP|DailySalesScreens.kt:329-364|exact valid-row rejection cause unproven|P7 targeted regression
UAT-11|A|PersonnelSchedulingScreens.kt:164|manual shift code; use canonical allocator|P4
UAT-12|PF|PersonnelAttendanceService.kt:176-184|timestamps exist but aggregation/display need canonicalization|P4/P7
UAT-13|A|PersonnelSchedulingScreens.kt:219|manual schedule code; use canonical allocator|P4
UAT-14|PD|AssetModels.kt:70|quantity/reason semantics undecided; DB possible|P5
UAT-15|PF|AssetViewModel.kt:57+ + LocalAssetRepository.kt|success/posting path exists; owner-visible reload needs regression|P7

## Full finding ledger
Fields: AUDIT_ID|MODULE|TITLE|SEVERITY_FROM_AUDIT|ACTUAL_CLASSIFICATION|ACTUAL_SEVERITY|SOURCE_PATH|CLASS_OR_FUNCTION|CODE_EVIDENCE|RUNTIME_OR_UAT_EVIDENCE|ROOT_CAUSE|DUPLICATES|RELATED_OWNER_UAT|TARGET_PHASE|DB_IMPACT|SECURITY_IMPACT|FINANCIAL_IMPACT|BRANCH_IMPACT|HISTORICAL_DATA_IMPACT
03-01|M03|Payroll V2روی payroll_payslipsاست ،اما بعضی Management/Reportمسیرها هنوز|C|I|C|X03-01|M03|X|B|RC-07|-|—|P4|0|L|H|L|L
03-02|M03|وجود AttendanceEventAggregatorنقطه قوت است ،ولی چند مسیر هنوز First-In/Last-Out|C|PF|H|X03-02|M03|X|B|RC-08|-|UAT-12|P4|0|L|H|L|L
03-03|M03|زنجیره Canonicalحضور باید واحد شود|H|D|H|M03|M03|ED|B|RC-07|RC-07|—|P4|0|L|H|L|L
03-04|M03|Tax/Insuranceقانونمحور و Versionedنیست|H|NP|?|M03|M03|ENP|B|RC-07|-|—|P4|0|L|H|L|L
03-05|M03|ناقصNight/Friday/Holiday Rules|H|NP|?|M03|M03|ENP|B|RC-07|-|—|P4|0|L|H|L|L
03-06|M03|نداردSegmentation وسط دورهPolicy/تغییر قرارداد|H|NP|?|M03|M03|ENP|B|RC-07|-|—|P4|0|L|H|L|L
03-07|M03|ناقصRecruitment Workflow|H|EE|?|M03|M03|EEE|B|RC-07|-|—|P4|0|L|H|L|L
03-08|M03|دو منبع بالقوهVersioned Contract وLegacy Base Salary|H|D|H|M03|M03|ED|B|RC-07|RC-07|—|P4|0|L|H|L|L
03-09|M03|باشدSnapshot بایدHistorical Branch Payroll|H|D|H|M03|M03|ED|B|RC-07|RC-07|—|P4|0|L|H|H|H
03-10|M03|باشدSnapshot باید کامل وPayroll Explainability|H|NP|?|M03|M03|ENP|B|RC-07|-|—|P4|0|L|H|L|H
03-11|M03|HR/Payroll درGod Class|H|NP|?|M03|M03|ENP|B|RC-07|-|—|P4|0|L|H|L|L
03-12|M03|کامل نیستRecruitment/Document/Evaluation linkage|M|NP|?|M03|M03|ENP|B|RC-07|-|—|P4|0|L|H|L|L
04-01|M04|صریح ندارندLocation فروش و خرید|C|PF|H|M04|M04|EPF|B|RC-05|-|UAT-04|P3|0|L|H|H|L
04-02|M04|نداردCanonical مقصدGoods Receipt|C|D|H|M04|M04|ED|B|RC-05|RC-05|—|P3|0|L|H|L|L
04-03|M04|انبار را دور بزندGovernance میتواندDirect Purchase|C|PF|H|M04|M04|EPF|B|RC-05|-|—|P3|0|L|H|H|L
04-04|M04|ایجاد شودLot میتواند بدونLot-controlled stock|C|I|C|M04|M04|EI|B|RC-05|-|—|P3|P|L|H|L|L
04-05|M04|اثر حسابداری کامل نداردInventory Count|C|I|C|M04|M04|EI|B|RC-05|-|—|P3|0|L|H|L|L
04-06|M04|کامل نیستCount Lot-level|H|NP|?|M04|M04|ENP|B|RC-05|-|—|P3|0|L|H|L|L
04-07|M04|ناقصTransfer variance Workflow|H|NP|?|M04|M04|ENP|B|RC-05|-|—|P3|0|L|H|L|L
04-08|M04|ناقصReservation Workflow|H|D|H|M04|M04|ED|B|RC-05|RC-05|—|P3|0|L|H|L|L
04-09|M04|ناقصDamaged/Quarantine Workflow|H|D|H|M04|M04|ED|B|RC-05|RC-05|—|P3|0|L|H|L|L
04-10|M04|بماندACTIVE ممکن استExpired Lot|H|D|H|M04|M04|ED|B|RC-05|RC-05|—|P3|0|L|H|L|L
04-11|M04|نیستندRequisition Location-aware وReplenishment|H|D|H|M04|M04|ED|B|RC-05|RC-05|—|P3|0|L|H|H|L
04-12|M04|فعال استInventory Write دو نسل|H|D|H|M04|M04|ED|B|RC-05|RC-05|—|P3|0|L|H|L|L
04-13|M04|نمیکندSnapshot راItem×Location/Lot همهPeriod Close|H|D|H|M04|M04|ED|B|RC-05|RC-05|—|P3|0|L|H|H|H
04-14|M04|داخلی نداردLocalInventoryCommandEngine Authorization|H|FX|0|X04-14|M04|X|B|RC-05|-|—|P3|0|H|L|L|L
04-15|M04|میپذیردCaller ارزش را ازIssueInventoryCommand|H|D|H|M04|M04|ED|B|RC-05|RC-05|—|P3|0|L|H|L|L
04-16|M04|باید صریح تفکیک شوندFinancial Weighted Average وPhysical FEFO|H|NP|?|M04|M04|ENP|B|RC-05|-|—|P3|0|L|H|L|L
04-17|M04|عمومی چندسطحی ناقصUnit conversion|M|NP|?|M04|M04|ENP|B|RC-05|-|—|P3|0|L|H|L|L
05-01|M05|Treasury Accountبه GL Mappingیکبهیک نیست|C|I|C|M05|M05|EI|B|RC-01|-|—|P2|P|L|H|L|L
05-02|M05|Petty Cashو Card Terminalحساب مستقل الزم دارند|C|D|H|M05|M05|ED|B|RC-01|RC-01|—|P2|P|L|H|L|L
05-03|M05|Dual Truthنقدینگی|C|I|C|M05|M05|EI|B|RC-01|-|—|P2|0|L|H|L|L
05-04|M05|Treasury sourceTypeمتن آزاد است|C|I|C|M05|M05|EI|B|RC-01|-|UAT-08|P2|0|L|H|L|L
05-05|M05|Generic Receipt/Paymentنباید پیشفرض Income/Expenseشود|C|I|C|M05|M05|EI|B|RC-01|-|—|P2|0|L|H|L|L
05-06|M05|واقعی لینک کامل نداردSource بهSettlement Supplier/Customer|H|D|H|M05|M05|ED|B|RC-01|RC-01|—|P2|0|L|H|L|L
05-07|M05|ناقصTreasury Branch Scope|H|D|H|M05|M05|ED|B|RC-01|RC-01|—|P2|0|H|H|H|L
05-08|M05|نداردManual Journal maker-checker|H|D|H|M05|M05|ED|B|RC-01|RC-01|—|P2|0|L|H|L|L
05-09|M05|حسابداری ریزدانهتر الزم استPermission|H|D|H|M05|M05|ED|B|RC-01|RC-01|—|P2|0|H|H|L|L
05-10|M05|نیستDate Lock فقطPeriod Close|H|D|H|M05|M05|ED|B|RC-01|RC-01|—|P2|0|L|H|L|L
05-11|M05|کاملReason/Approval/Reauth نیازمندReopen|H|D|H|M05|M05|ED|B|RC-01|RC-01|—|P2|0|L|H|L|L
05-12|M05|استفاده میکندhard-coded از حسابهایBranch P&L|H|D|H|M05|M05|ED|B|RC-01|RC-01|—|P2|0|L|H|H|L
05-13|M05|بازه یکسان ندارندP&L وTrial Balance|H|NP|?|M05|M05|ENP|B|RC-01|-|—|P2|0|L|H|L|L
05-14|M05|حذف میشوندHistorical Report ازInactive Accounts|H|NP|?|M05|M05|ENP|B|RC-01|-|—|P2|0|L|H|L|H
05-15|M05|واقعی نداردLedger/Journal Paging|H|NP|?|M05|M05|ENP|B|RC-01|-|—|P2|0|L|H|L|L
05-16|M05|ناقصChart of Accounts hierarchy/normalBalance/allowPosting|H|EE|?|M05|M05|EEE|B|RC-01|-|—|P2|P|L|H|L|L
05-17|M05|کامل نیستJournal روی خطوطCost Center Dimension|H|EE|?|M05|M05|EEE|B|RC-01|-|—|P2|P|L|H|L|L
05-18|M05|کامل نیستFiscal Year Close/Carry Forward|H|EE|?|M05|M05|EEE|B|RC-01|-|—|P2|P|L|H|L|L
05-19|M05|مرکزی وجود نداردReconciliation|C|D|H|M05|M05|ED|B|RC-01|RC-01|—|P2|0|L|H|L|L
05-20|M05|شودIncome/Expense نباید مستقیمReconciliation Difference|H|NP|?|M05|M05|ENP|B|RC-01|-|—|P2|0|L|H|L|L
05-21|M05|کامل مشاهده نشدBalance Sheet|H|EE|?|M05|M05|EEE|B|RC-01|-|—|P2|0|L|H|L|L
05-22|M05|است تا صورت جریان وجوه نقدForecast بیشترCashFlowCalculator|H|NP|?|M05|M05|ENP|B|RC-01|-|—|P2|0|L|H|L|L
05-23|M05|مشترک داشته باشندTrace ID بایدAutomated Journals|H|NP|?|M05|M05|ENP|B|RC-01|-|—|P2|0|L|H|L|L
06-01|M06|Zero Estimated Price Approval/Variance Bypass|C|I|C|M06|M06|EI|B|RC-06|-|—|P3|0|L|H|L|L
06-02|M06|را دور میزندProcurement مسیرDirect Purchase|C|PF|H|M06|M06|EPF|B|RC-06|-|UAT-05|P3|0|L|H|L|L
06-03|M06|نیستDirect Purchase idempotent|C|D|H|M06|M06|ED|B|RC-06|RC-06|—|P3|0|L|H|L|L
06-04|M06|از ابتدای زنجیره نیستBranch/Location|C|D|H|M06|M06|ED|B|RC-06|RC-06|—|P3|P|L|H|H|L
06-05|M06|میشودGoods Receipt Accounting Organization scope|H|NP|?|M06|M06|ENP|B|RC-06|-|—|P3|0|H|H|L|L
06-06|M06|صحیح نداردBranch/Location نیزPurchase Return|H|NP|?|M06|M06|ENP|B|RC-06|-|—|P3|0|L|H|H|L
06-07|M06|واقعی نیستBudget Actual Cost-center scoped|H|DB|?|M06|M06|EDB|B|RC-06|-|—|P3|P|H|H|L|L
06-08|M06|قابل دستکاری متکی استEstimate برApproval|H|NP|?|M06|M06|ENP|B|RC-06|-|—|P3|0|L|H|L|L
06-09|M06|هستندprice variance hard-coded وApproval threshold|H|NP|?|M06|M06|ENP|B|RC-06|-|—|P3|0|L|H|L|L
06-10|M06|استفاده میکندPermission.AUDIT ازPrice variance approval|H|NP|?|M06|M06|ENP|B|RC-06|-|—|P3|0|H|H|L|L
06-11|M06|کامل نیستMaker-checker|H|NP|?|M06|M06|ENP|B|RC-06|-|—|P3|0|L|H|L|L
06-12|M06|را مقایسه میکندTotal Amount فقطThree-Way Match|C|D|H|M06|M06|ED|B|RC-06|RC-06|—|P3|0|L|H|L|L
06-13|M06|طراحی شدهFull Receipt فقط پس ازMatch|H|DB|?|M06|M06|EDB|B|RC-06|-|—|P3|P|L|H|L|L
06-14|M06|Invoice per PO فقط یک|H|DB|?|M06|M06|EDB|B|RC-06|-|—|P3|P|L|H|L|L
06-15|M06|صفحه|?|DB|?|M06|M06|EDB|B|RC-06|-|—|P3|P|L|H|L|L
06-16|M06|صفحه|?|D|M|M06|M06|ED|B|RC-06|RC-06|—|P3|P|L|H|L|L
06-17|M06|اجباری نیستSupplier override reason|H|NP|?|M06|M06|ENP|B|RC-06|-|—|P3|0|L|H|L|L
06-18|M06|میشودSupplier offer history overwrite|H|NP|?|M06|M06|ENP|B|RC-06|-|—|P3|0|L|H|L|H
06-19|M06|ناقصAuto sourcing supplierScore|H|NP|?|M06|M06|ENP|B|RC-06|-|—|P3|0|L|H|L|L
06-20|M06|نیستReplenishment/Pending Requisition location-aware|H|NP|?|M06|M06|ENP|B|RC-06|-|—|P3|0|L|H|H|L
06-21|M06|ناقصPO cancel/amend/change-order lifecycle|H|NP|?|M06|M06|ENP|B|RC-06|-|—|P3|0|L|H|L|L
06-22|M06|وجود نداردRequisition withdraw|H|NP|?|M06|M06|ENP|B|RC-06|-|—|P3|0|L|H|L|L
06-23|M06|کامل نیستSplit quantity across suppliers|H|DB|?|M06|M06|EDB|B|RC-06|-|—|P3|P|L|H|L|L
06-24|M06|متفاوت استDirect Purchase Lot policy|C|D|H|M06|M06|ED|B|RC-06|RC-06|—|P3|0|L|H|L|L
06-25|M06|نداردPurchase Return lotId/location|H|DB|?|M06|M06|EDB|B|RC-06|-|—|P3|P|L|H|H|L
06-26|M06|استفاده میکندPO price ازReturn valuation|H|D|H|M06|M06|ED|B|RC-06|RC-06|—|P3|0|L|H|L|L
06-27|M06|کامل نیستTax/Discount/Freight/Landed Cost|H|DB|?|M06|M06|EDB|B|RC-06|-|—|P3|P|L|H|L|L
06-28|M06|واقعی خرید کامل نیستAttachments|H|DB|?|M06|M06|EDB|B|RC-06|-|—|P3|P|L|H|L|L
06-29|M06|ناقصSupplier Credit application workflow|H|DB|?|M06|M06|EDB|B|RC-06|-|—|P3|P|L|H|L|L
06-30|M06|را دور بزندTreasury میتواندPurchase settlement|C|D|H|M06|M06|ED|B|RC-06|RC-06|—|P3|0|L|H|L|L
06-31|M06|کامل نیستRead Permission boundary|H|NP|?|M06|M06|ENP|B|RC-06|-|—|P3|0|H|H|L|L
06-32|M06|ناقصProduction date/Lot UI semantics|H|NP|?|M06|M06|ENP|B|RC-06|-|—|P3|0|L|H|L|L
06-33|M06|ناقصHistorical unit/supplier snapshots|H|NP|?|M06|M06|ENP|B|RC-06|-|—|P3|0|L|H|L|H
07-01|M07|واقعی نیستPOS سیستم فعال|C|OOS|0|X07-01|M07|X|B|RC-01|-|—|P2|0|L|H|L|L
07-02|M07|فروشTruth دو|C|PF|H|X07-02|M07|X|B|RC-19|-|—|P2|0|L|H|L|L
07-03|M07|و گزارشها ممکن است فروش جدید را نبینندCRM|C|D|H|M07|M07|ED|B|RC-01|RC-01|—|P2|0|L|H|L|L
07-04|M07|نداردlocation دارد ولیDailySalesDraft branch|C|D|H|M07|M07|ED|B|RC-01|RC-01|—|P2|P|L|H|H|L
07-05|M07|میآیدaggregate inventory current state ازCOGS|C|I|C|M07|M07|EI|B|RC-01|-|—|P2|0|L|H|L|L
07-06|M07|استفاده میکندcurrent/future inventory valuation ازBackdated sale|C|H|C|M07|M07|EH|B|RC-01|-|—|P2|0|L|H|L|H
07-07|M07|نیستConfirm frozen snapshot|C|I|C|M07|M07|EI|B|RC-01|-|—|P2|0|L|H|L|H
07-08|M07|استConfirmed cost mutable|H|NP|?|M07|M07|ENP|B|RC-01|-|—|P2|0|L|H|L|L
07-09|M07|در فروش نادیده گرفته میشوندsubstitution وNested recipe|C|I|C|M07|M07|EI|B|RC-01|-|—|P2|0|L|H|L|L
07-10|M07|میزنندGL مستقیمCash/Card/Bank|C|D|H|M07|M07|ED|B|RC-01|RC-01|—|P2|0|L|H|L|L
07-11|M07|یکی میشودbank transfer semantic mapping وCard|H|NP|?|M07|M07|ENP|B|RC-01|-|—|P2|0|L|H|L|L
07-12|M07|نیمهکارهاندCashbox/bank/card IDs|H|D|H|M07|M07|ED|B|RC-01|RC-01|—|P2|P|L|H|L|L
07-13|M07|واقعی کامل نیستCorporate contract|H|DB|?|M07|M07|EDB|B|RC-01|-|—|P2|P|L|H|L|L
07-14|M07|بگیردcredit میتواندON_HOLD customer|C|NP|?|M07|M07|ENP|B|RC-01|-|—|P2|0|L|H|L|L
07-15|M07|همیشه اجباری نیستDue date credit|H|NP|?|M07|M07|ENP|B|RC-01|-|—|P2|0|L|H|L|L
07-16|M07|ناقصCredit override decision snapshot|H|DB|?|M07|M07|EDB|B|RC-01|-|—|P2|P|L|H|L|H
07-17|M07|درج میشودSales مستقیمًا ازReceivable|H|D|H|M07|M07|ED|B|RC-01|RC-01|—|P2|0|L|H|L|L
07-18|M07|دستی استTax/service charge/discount policy|H|NP|?|M07|M07|ENP|B|RC-01|-|—|P2|0|L|H|L|L
07-19|M07|ناقصMenu price validation|H|DB|?|M07|M07|EDB|B|RC-01|-|—|P2|P|L|H|L|L
07-20|M07|صفحه|?|NP|?|M07|M07|ENP|B|RC-01|-|—|P2|0|L|H|L|L
07-21|M07|صفحه|?|DB|?|M07|M07|EDB|B|RC-01|-|—|P2|P|L|H|L|L
07-22|M07|صفحه|?|D|M|M07|M07|ED|B|RC-01|RC-01|—|P2|0|L|H|L|L
07-23|M07|کامل نیستBranch/day uniqueness concurrency-safe|H|DB|?|M07|M07|EDB|B|RC-01|-|—|P2|P|L|H|H|L
07-24|M07|branch ambiguity وclose بعد ازCash reconciliation|H|D|H|M07|M07|ED|B|RC-01|RC-01|—|P2|0|L|H|H|L
07-25|M07|Cash reconciliation revision MAX+1|H|FP|0|X07-25|M07|X|B|RC-01|-|—|P2|0|L|L|L|L
07-26|M07|نداردshift/register/cashier separation ؛summary per day یک|H|EE|?|M07|M07|EEE|B|RC-01|-|—|P2|P|L|H|L|L
07-27|M07|نداردSource/Z-report evidence/hash|H|D|H|M07|M07|ED|B|RC-01|RC-01|—|P2|P|L|H|L|L
07-28|M07|ناقصPrice list/versioning|H|EE|?|M07|M07|EEE|B|RC-01|-|—|P2|P|L|H|L|L
07-29|M07|مدل نشدهOnline delivery commission/tip/receivable|H|FR|?|M07|M07|EFR|B|RC-01|-|—|P2|0|L|H|L|L
07-30|M07|کامل نیستVOID lifecycle|H|NP|?|M07|M07|ENP|B|RC-01|-|—|P2|0|L|H|L|L
08-01|M08|Activation Permission Bypass|C|PF|H|X08-01|M08|X|B|RC-10|-|—|P5|0|H|H|L|L
08-02|M08|را نمیبیندcomponents اصلیEditor|C|NP|?|M08|M08|ENP|B|RC-10|-|—|P5|0|L|H|L|L
08-03|M08|Coverage/Print direct ingredients-only|H|NP|?|M08|M08|ENP|B|RC-10|-|—|P5|0|L|H|L|L
08-04|M08|را مصرف نمیکندSales nested components/substitutions|C|D|H|M08|M08|ED|B|RC-10|RC-10|—|P5|0|L|H|L|L
08-05|M08|میشودActive Version append رویSubstitution|C|D|H|M08|M08|ED|B|RC-10|RC-10|—|P5|0|L|H|L|L
08-06|M08|ناقصSubstitution lifecycle|H|DB|?|M08|M08|EDB|B|RC-10|-|—|P5|P|L|H|L|L
08-07|M08|نداردcomponents/substitutions برایDB immutability trigger|H|D|H|M08|M08|ED|B|RC-10|RC-10|—|P5|P|L|H|L|L
08-08|M08|Recipe revision MAX+1|H|FP|0|X08-08|M08|X|B|RC-10|-|—|P5|0|L|L|L|L
08-09|M08|کندrewrite راhistory میتواندBackdated activation|C|H|C|M08|M08|EH|B|RC-10|-|—|P5|0|L|H|L|H
08-10|M08|را بررسی نمیکندActivation closed period|H|NP|?|M08|M08|ENP|B|RC-10|-|—|P5|0|L|H|L|L
08-11|M08|کندnull راhistorical effective lookup میتواندactive آخرینRetire|C|H|C|M08|M08|EH|B|RC-10|-|—|P5|0|L|H|L|H
08-12|M08|Future-dated activation unsupported|H|FR|?|M08|M08|EFR|B|RC-10|-|—|P5|0|L|H|L|L
08-13|M08|عملیاتی نیستyieldMicros|C|DB|?|M08|M08|EDB|B|RC-10|-|—|P5|P|L|H|L|L
08-14|M08|مبهم استsemantic درصدیWaste|H|NP|?|M08|M08|ENP|B|RC-10|-|—|P5|0|L|H|L|L
08-15|M08|واقعی وجود نداردSemi-finished production|H|EE|?|M08|M08|EEE|B|RC-10|-|—|P5|P|L|H|L|L
08-16|M08|واحد خروجی صریح نداردComponent quantity|H|DB|?|M08|M08|EDB|B|RC-10|-|—|P5|P|L|H|L|L
08-17|M08|استCost preview aggregate inventory current state|H|NP|?|M08|M08|ENP|B|RC-10|-|—|P5|0|L|H|L|L
08-18|M08|نمیشودRecipe Version snapshot درStandard raw cost|H|D|H|M08|M08|ED|B|RC-10|RC-10|—|P5|P|L|H|L|H
08-19|M08|نیستrecipe باMenu sale price versioned|H|D|H|M08|M08|ED|B|RC-10|RC-10|—|P5|0|L|H|L|L
08-20|M08|ناقصIngredient name/unit/conversion snapshot|H|D|H|M08|M08|ED|B|RC-10|RC-10|—|P5|P|L|H|L|H
08-21|M08|مبهمPackaging cost policy|H|NP|?|M08|M08|ENP|B|RC-10|-|—|P5|0|L|H|L|L
08-22|M08|استLabor/overhead manual management cost|H|NP|?|M08|M08|ENP|B|RC-10|-|—|P5|0|L|H|L|L
08-23|M08|ضعیفCostingEngine Actual/Standard production path|H|D|H|M08|M08|ED|B|RC-10|RC-10|—|P5|0|L|H|L|L
08-24|M08|ناقصActual Food Cost coverage|H|NP|?|M08|M08|ENP|B|RC-10|-|—|P5|0|L|H|L|L
08-25|M08|صفحه|?|D|M|M08|M08|ED|B|RC-10|RC-10|—|P5|0|L|H|L|L
08-26|M08|صفحه|?|D|M|M08|M08|ED|B|RC-10|RC-10|—|P5|0|L|H|L|L
08-27|M08|صفحه|?|NP|?|M08|M08|ENP|B|RC-10|-|—|P5|0|L|H|L|L
08-28|M08|کامل نمیکندrecheck راActivate stale draft child status|H|NP|?|M08|M08|ENP|B|RC-10|-|—|P5|0|L|H|L|L
08-29|M08|صریح نداردparentVersionId FK|M|DB|?|M08|M08|EDB|B|RC-10|-|—|P5|P|L|H|L|L
09-01|M09|نیستCollection idempotent|C|I|C|M09|M09|EI|B|RC-02|-|—|P2|0|L|H|L|L
09-02|M09|دو مسیر دریافت پول مشتری|C|D|H|M09|M09|ED|B|RC-02|RC-02|—|P2|0|L|H|L|L
09-03|M09|نداردreceivableId بهReceipt Allocation|C|I|C|M09|M09|EI|B|RC-02|-|—|P2|P|L|H|H|L
09-04|M09|تغذیه شودMaster متفاوت ازLedger میتواند ازCredit Limit|C|D|H|M09|M09|ED|B|RC-02|RC-02|—|P2|0|L|H|L|L
09-05|M09|را میبیندLedger balance فقطDeactivate customer|H|NP|?|M09|M09|ENP|B|RC-02|-|—|P2|0|L|H|L|L
09-06|M09|ایجاد نمیکندOpening Balance Master Receivable|C|DB|?|M09|M09|EDB|B|RC-02|-|—|P2|P|L|H|L|L
09-07|M09|نداردMaster Receivable نیزAdjustment|H|DB|?|M09|M09|EDB|B|RC-02|-|—|P2|P|L|H|L|L
09-08|M09|ندارندOpening/Adjustment branchId|C|D|H|M09|M09|ED|B|RC-02|RC-02|—|P2|P|L|H|H|L
09-09|M09|استOpening/Adjustment accounting Organization scope|H|D|H|M09|M09|ED|B|RC-02|RC-02|—|P2|P|H|H|L|L
09-10|M09|استفاده میکندcustomer.branch legacy ازDashboard AR|C|D|H|M09|M09|ED|B|RC-02|RC-02|—|P2|0|L|H|H|L
09-11|M09|شروع میشودsales_invoices legacy ازAlert overdue|C|D|H|M09|M09|ED|B|RC-02|RC-02|—|P2|0|L|H|L|L
09-12|M09|Overdue alert N+1|H|NP|?|M09|M09|ENP|B|RC-02|-|—|P2|0|L|H|L|L
09-13|M09|نیستCRM در فرمpartyType|C|NP|?|M09|M09|ENP|B|RC-02|-|—|P2|0|L|H|L|L
09-14|M09|کندPERSON میتواند آن راCOMPANY ویرایش|C|NP|?|M09|M09|ENP|B|RC-02|-|—|P2|0|L|H|L|L
09-15|M09|نداردMerge Person/Company compatibility|H|DB|?|M09|M09|EDB|B|RC-02|-|—|P2|P|L|H|L|L
09-16|M09|میکندUPDATE راMerge Posted Sales Invoice|C|H|C|M09|M09|EH|B|RC-02|-|—|P2|0|L|H|L|H
09-17|M09|نمیکندcanonical راreferences همهMerge|H|NP|?|M09|M09|ENP|B|RC-02|-|—|P2|0|L|H|L|L
09-18|M09|ناقصDuplicate Detection mobile|H|DB|?|M09|M09|EDB|B|RC-02|-|—|P2|P|L|H|L|L
09-19|M09|استlength فقطNational ID validation|H|NP|?|M09|M09|ENP|B|RC-02|-|—|P2|0|L|H|L|L
09-20|M09|آزاد استStatus UI text|H|NP|?|M09|M09|ENP|B|RC-02|-|—|P2|0|L|H|L|L
09-21|M09|نمیکندBlock راON_HOLD credit sale|C|D|H|M09|M09|ED|B|RC-02|RC-02|—|P2|0|L|H|L|L
09-22|M09|تخصصی نداردPermission/Approval تغییرCredit limit/payment terms|H|DB|?|M09|M09|EDB|B|RC-02|-|—|P2|P|H|H|L|L
09-23|M09|داشته باشدdueDate null میتواندCredit sale|H|D|H|M09|M09|ED|B|RC-02|RC-02|—|P2|0|L|H|L|L
09-24|M09|میشودOther Income/Expense map بهAdjustment debit/credit|C|I|C|M09|M09|EI|B|RC-02|-|—|P2|0|L|H|L|L
09-25|M09|منفی بسازدReceivable میتواندCredit adjustment|H|DB|?|M09|M09|EDB|B|RC-02|-|—|P2|P|L|H|L|L
09-26|M09|ناقصCollection date/reversal chronology|H|NP|?|M09|M09|ENP|B|RC-02|-|—|P2|0|L|H|L|L
09-27|M09|واقعی الزام نمیکندCollection method treasury account|H|D|H|M09|M09|ED|B|RC-02|RC-02|—|P2|0|L|H|L|L
09-28|M09|داخلی ندارندobserveOpen/observeLedger read authorization|H|D|H|M09|M09|ED|B|RC-02|RC-02|—|P2|0|H|H|L|L
09-29|M09|متفاوت وجود داردAging bucket policy دو|H|D|H|M09|M09|ED|B|RC-02|RC-02|—|P2|0|L|H|L|L
09-30|M09|صفحه|?|EE|?|M09|M09|EEE|B|RC-02|-|—|P2|P|L|H|L|L
10-01|M10|را دور میزندCash/Bank Treasury خرید|C|D|H|M10|M10|ED|B|RC-11|RC-11|—|P5|0|L|H|L|L
10-02|M10|نمیسازدTreasury Receipt فروش دارایی|C|D|H|M10|M10|ED|B|RC-11|RC-11|—|P5|0|L|H|L|L
10-03|M10|Maintenance Cash/Bank Treasury bypass|C|D|H|M10|M10|ED|B|RC-11|RC-11|—|P5|0|L|H|L|L
10-04|M10|نداردAcquisition/Maintenance PAYABLE AP Subledger|C|I|C|M10|M10|EI|B|RC-11|-|—|P5|P|L|H|L|L
10-05|M10|نداردAccounting Reclassification بین شعبAsset انتقال|C|D|H|M10|M10|ED|B|RC-11|RC-11|—|P5|0|L|H|H|L
10-06|M10|جدید ثبت میشودBranch درTransfer استهالک بعد از|C|D|H|M10|M10|ED|B|RC-11|RC-11|—|P5|0|L|H|H|L
10-07|M10|را تغییر میدهدBranch/Location/Responsible سادهEdit|C|PF|H|X10-07|M10|X|B|RC-11|-|—|P5|0|L|H|H|L
10-08|M10|نداردLifecycle Event branchId canonical|H|DB|?|M10|M10|EDB|B|RC-11|-|—|P5|P|L|H|H|L
10-09|M10|آزاد استstring شخصResponsible|H|DB|?|M10|M10|EDB|B|RC-11|-|—|P5|P|L|H|L|L
10-10|M10|آزاد استLocation string|H|DB|?|M10|M10|EDB|B|RC-11|-|—|P5|P|L|H|H|L
10-11|M10|نمیشودenforce تقریبًاLifecycle chronology|C|NP|?|M10|M10|ENP|B|RC-11|-|—|P5|0|L|H|L|L
10-12|M10|استفاده میکندcurrent state including future impairment ازBackdated sale|C|H|C|M10|M10|EH|B|RC-11|-|—|P5|0|L|H|L|H
10-13|M10|ممکن استpurchase/placed-in-service قبل ازDepreciation|C|NP|?|M10|M10|ENP|B|RC-11|-|—|P5|0|L|H|L|L
10-14|M10|period/posting-date mismatch وFuture depreciation|H|NP|?|M10|M10|ENP|B|RC-11|-|—|P5|0|L|H|L|L
10-15|M10|کنترل نمیشودDepreciation sequence gap|H|NP|?|M10|M10|ENP|B|RC-11|-|—|P5|0|L|H|L|L
10-16|M10|وجود نداردprorata policy وPlaced-in-service date|H|DB|?|M10|M10|EDB|B|RC-11|-|UAT-14|P5|P|L|H|L|L
10-17|M10|straight-line فقط|M|PD|?|M10|M10|EPD|B|RC-11|-|—|P5|0|L|H|L|L
10-18|M10|depreciation دو منطق|H|D|H|M10|M10|ED|B|RC-11|RC-11|—|P5|0|L|H|L|L
10-19|M10|نمیکندrecalc آینده راImpairment depreciation|C|I|C|M10|M10|EI|B|RC-11|-|UAT-15|P5|0|L|H|L|L
10-20|M10|ناقصDepreciation/Maintenance/Impairment/Sale/Disposal reversal workflow|H|D|H|M10|M10|ED|B|RC-11|RC-11|—|P5|P|L|H|L|L
10-21|M10|نیستندLifecycle commands idempotent|C|I|C|M10|M10|EI|B|RC-11|-|—|P5|0|L|H|L|L
10-22|M10|نمیآوردGL راLegacy recognition accumulated depreciation/impairment|C|I|C|M10|M10|EI|B|RC-11|-|—|P5|0|L|H|L|L
10-23|M10|Owner Capital hard-coded امروز وRecognition|H|NP|?|M10|M10|ENP|B|RC-11|-|—|P5|0|L|H|L|L
10-24|M10|ناقصDisposal date/reason/type|H|NP|?|M10|M10|ENP|B|RC-11|-|—|P5|0|L|H|L|L
10-25|M10|ناقصSale tax/expenses/customer linkage|H|NP|?|M10|M10|ENP|B|RC-11|-|—|P5|0|L|H|L|L
10-26|M10|فرض میشودexpense همیشهMaintenance|H|NP|?|M10|M10|ENP|B|RC-11|-|—|P5|0|L|H|L|L
10-27|M10|ندارندUseful life/salvage revision controlled|H|DB|?|M10|M10|EDB|B|RC-11|-|—|P5|P|L|H|L|L
10-28|M10|ناقصquantity>1 lifecycle|C|NP|?|M10|M10|ENP|B|RC-11|-|—|P5|0|L|H|L|L
10-29|M10|ناقصSerial/model/manufacturer/warranty/supplier/invoice/attachments|H|DB|?|M10|M10|EDB|B|RC-11|-|—|P5|P|L|H|L|L
10-30|M10|نداردCategory accounting mapping|H|DB|?|M10|M10|EDB|B|RC-11|-|—|P5|P|L|H|L|L
10-31|M10|ناقصDB immutability guards وbranchId FK|H|DB|?|M10|M10|EDB|B|RC-11|-|—|P5|P|L|H|H|L
10-32|M10|را شامل نمیکندLifecycle timeline depreciation|H|NP|?|M10|M10|ENP|B|RC-11|-|—|P5|0|L|H|L|L
10-33|M10|را جمع میکندAsset Dashboard sold/disposed book value|C|NP|?|M10|M10|ENP|B|RC-11|-|—|P5|0|L|H|L|L
10-34|M10|نیستHistorical asset dashboard As-of|H|NP|?|M10|M10|ENP|B|RC-11|-|—|P5|0|L|H|L|H
10-35|M10|صفحه|?|D|M|M10|M10|ED|B|RC-11|RC-11|—|P5|0|L|H|L|L
10-36|M10|صفحه|?|DB|?|M10|M10|EDB|B|RC-11|-|—|P5|P|L|H|L|L
10-37|M10|صفحه|?|NP|?|M10|M10|ENP|B|RC-11|-|—|P5|0|L|H|L|L
10-38|M10|صفحه|?|NP|?|M10|M10|ENP|B|RC-11|-|—|P5|0|L|H|L|L
10-39|M10|صفحه|?|D|M|M10|M10|ED|B|RC-11|RC-11|—|P5|0|L|H|L|L
11-01|M11|استLegacy فروشDashboard|C|I|C|X11-01|M11|X|B|RC-19|-|UAT-10|P2|0|L|L|L|L
11-02|M11|درآمد متناقض ممکن است/ دو عدد فروشHome در همان|C|D|H|M11|M11|ED|B|RC-17|RC-17|—|P6|0|L|L|L|L
11-03|M11|داخلی نداردDashboardRepository Authorization|H|PF|H|M11|M11|EPF|B|RC-17|-|—|P6|0|H|L|L|L
11-04|M11|را ببیندOrganization میتواند نقدینگیCashier|C|NP|?|M11|M11|ENP|B|RC-17|-|—|P6|0|L|L|L|L
11-05|M11|نیستندCash/Bank branch-specific|C|D|H|M11|M11|ED|B|RC-17|RC-17|—|P6|0|L|L|H|L
11-06|M11|Treasury canonical میآید نهGL ازLiquidity|C|D|H|M11|M11|ED|B|RC-17|RC-17|—|P6|0|L|L|L|L
11-07|M11|قاطی شدهاندSnapshot metrics وFlow|C|NP|?|M11|M11|ENP|B|RC-17|-|—|P6|0|L|L|L|H
11-08|M11|را نشان میدهدHistorical Cash/Bank/Inventory/AR/AP current state|C|H|C|M11|M11|EH|B|RC-17|-|—|P6|0|L|L|L|H
11-09|M11|customer.branch legacy ازCustomer AR dashboard|C|D|H|M11|M11|ED|B|RC-17|RC-17|—|P6|0|L|L|H|L
11-10|M11|را میبیندPurchase Table فقطAP dashboard|H|NP|?|M11|M11|ENP|B|RC-17|-|—|P6|0|L|L|L|L
11-11|M11|ها نداردKPI رویReconciliation status|H|NP|?|M11|M11|ENP|B|RC-17|-|—|P6|0|L|L|L|L
11-12|M11|non-location policy وLow Stock onHand-only|H|NP|?|M11|M11|ENP|B|RC-17|-|—|P6|0|L|L|H|L
11-13|M11|داردSlow Stock historical query future leak|C|H|C|M11|M11|EH|B|RC-17|-|—|P6|0|L|L|L|H
11-14|M11|را استفاده میکنندSlow/Expiry historical current state|H|NP|?|M11|M11|ENP|B|RC-17|-|—|P6|0|L|L|L|H
11-15|M11|قابل انتخاب استBranch A + Warehouse B|H|NP|?|M11|M11|ENP|B|RC-17|-|—|P6|0|L|L|H|L
11-16|M11|فقط روز انتهای بازه را میبیندAttendance KPI week/month|H|NP|?|M11|M11|ENP|B|RC-17|-|—|P6|0|L|L|L|L
11-17|M11|استpending correction در واقعAttendance anomaly|H|NP|?|M11|M11|ENP|B|RC-17|-|—|P6|0|L|L|L|L
11-18|M11|میشودemployee current branch join بهPayroll outstanding|H|D|H|M11|M11|ED|B|RC-17|RC-17|—|P6|0|L|L|H|L
11-19|M11|عمًال نداردAccumulated depreciation dashboard date filter|C|I|C|M11|M11|EI|B|RC-17|-|—|P6|0|L|L|L|L
11-20|M11|میشودcurrent asset branch join بهDepreciation branch history|C|D|H|M11|M11|ED|B|RC-17|RC-17|—|P6|0|L|L|H|H
11-21|M11|واقعی نیستReports Center Report Engine|H|NP|?|M11|M11|ENP|B|RC-17|-|—|P6|0|L|L|L|L
11-22|M11|نمیکندupdate راmetrics واقعًا همهUnified range|C|NP|?|M11|M11|ENP|B|RC-17|-|—|P6|0|L|L|L|L
11-23|M11|ممکن استreport در یکOrganization sales + branch inventory|C|NP|?|M11|M11|ENP|B|RC-17|-|—|P6|0|L|L|H|L
11-24|M11|outstanding است نهgross credit sales مطالبات چاپی از|C|D|H|M11|M11|ED|B|RC-17|RC-17|—|P6|0|L|L|L|L
11-25|M11|استnetPay وpayroll_runs Legacy ازPayroll ratio|C|D|H|M11|M11|ED|B|RC-17|RC-17|—|P6|0|L|L|L|L
11-26|M11|ناقص استCash flow report forecast|H|NP|?|M11|M11|ENP|B|RC-17|-|—|P6|0|L|L|L|L
11-27|M11|نیستReorder/waste/supplier insight scope unified|H|NP|?|M11|M11|ENP|B|RC-17|-|—|P6|0|H|L|L|L
11-28|M11|بیش از حد گسترده استPermission REPORTS|C|D|H|M11|M11|ED|B|RC-17|RC-17|—|P6|0|H|L|L|L
11-29|M11|تبدیل میشودemptySnapshot/zero بهDashboard Error|C|NP|?|M11|M11|ENP|B|RC-17|-|—|P6|0|L|L|L|H
11-30|M11|سراسری نیستData Quality state|H|NP|?|M11|M11|ENP|B|RC-17|-|—|P6|0|L|L|L|L
11-31|M11|P&L period کنارTrial Balance all-time|H|D|H|M11|M11|ED|B|RC-17|RC-17|—|P6|0|L|L|L|L
11-32|M11|ناقصPrint branch/user/timestamp/reportRun/page no|H|NP|?|M11|M11|ENP|B|RC-17|-|—|P6|0|L|L|H|L
11-33|M11|نمیشودReprint/print Audit|H|NP|?|M11|M11|ENP|B|RC-17|-|—|P6|0|H|L|L|L
11-34|M11|نداردReportPrinter permission boundary|H|NP|?|M11|M11|ENP|B|RC-17|-|—|P6|0|H|L|L|L
11-35|M11|چاپ وصل نیستsettings ثابت وA4|H|NP|?|M11|M11|ENP|B|RC-17|-|—|P6|0|L|L|L|L
11-36|M11|نداردReport snapshot immutable|H|NP|?|M11|M11|ENP|B|RC-17|-|—|P6|0|L|L|L|H
11-37|M11|سنگینsubqueries وGiant dashboard SQL|H|P|H|M11|M11|EP|B|RC-17|-|—|P6|0|L|L|L|L
11-38|M11|را حفظ نمیکندDrill-down scope/date/warehouse context|H|NP|?|M11|M11|ENP|B|RC-17|-|—|P6|0|H|L|H|L
12-01|M12|وجود نداردUser Branch Scope|C|S|C|M12|M12|ES|B|RC-04|-|—|P3|P|H|L|H|L
12-02|M12|کاربر وجود نداردWarehouse Scope|C|D|H|M12|M12|ED|B|RC-04|RC-04|—|P3|P|H|L|H|L
12-03|M12|ها تمام شعب فعال را نشان میدهندSelector|C|D|H|M12|M12|ED|B|RC-04|RC-04|—|P3|0|L|L|H|L
12-04|M12|نداردDeactivate branch dependency check|C|I|C|M12|M12|EI|B|RC-04|-|—|P3|0|L|L|H|L
12-05|M12|میتواند عملیات را گیر بیندازدworkflow وسطDeactivate|C|NP|?|M12|M12|ENP|B|RC-04|-|—|P3|0|L|L|H|L
12-06|M12|فعال باقی میمانندInactive branch warehouse/employees|H|NP|?|M12|M12|ENP|B|RC-04|-|—|P3|0|L|L|H|L
12-07|M12|استisActive فقطBranch lifecycle|H|EE|?|M12|M12|EEE|B|RC-04|-|—|P3|P|L|L|H|L
12-08|M12|نداردEffective close date/reason/reauth/approval|H|DB|?|M12|M12|EDB|B|RC-04|-|—|P3|P|L|L|H|L
12-09|M12|ناپدید میشودhistorical selector ازInactive branch|C|H|C|M12|M12|EH|B|RC-04|-|—|P3|0|L|L|H|H
12-10|M12|واقعی ندارندBusiness tables branch FK|C|DB|?|M12|M12|EDB|B|RC-04|-|—|P3|P|L|L|H|L
12-11|M12|را استفاده نمیکندCanonicalBranchResolver alias table|H|NP|?|M12|M12|ENP|B|RC-04|-|—|P3|0|L|L|H|L
12-12|M12|کندambiguous راhistorical attribution میتواندAlias collision|C|H|C|M12|M12|EH|B|RC-04|-|—|P3|0|L|L|H|H
12-13|M12|مبهمDuplicate branch name UI|H|NP|?|M12|M12|ENP|B|RC-04|-|—|P3|0|L|L|H|L
12-14|M12|ضعیفorgId NULL باDB uniqueness وBranch code optional|H|DB|?|M12|M12|EDB|B|RC-04|-|—|P3|P|L|L|H|L
12-15|M12|نداردCreate branch idempotency|H|NP|?|M12|M12|ENP|B|RC-04|-|—|P3|0|L|L|H|L
12-16|M12|نداردRename/activate rowVersion|H|DB|?|M12|M12|EDB|B|RC-04|-|—|P3|P|L|L|H|L
12-17|M12|نیستUI-managed عمًالorganizationId branch|H|NP|?|M12|M12|ENP|B|RC-04|-|—|P3|0|L|L|H|L
12-18|M12|مستقیم مجاز استWarehouse branch reassign|C|D|H|M12|M12|ED|B|RC-04|RC-04|—|P3|0|L|L|H|L
12-19|M12|صریح نیستorganization-level location scope|H|DB|?|M12|M12|EDB|B|RC-04|-|—|P3|P|H|L|H|L
12-20|M12|نداردSales default warehouse mapping branch|C|D|H|M12|M12|ED|B|RC-04|RC-04|—|P3|P|L|L|H|L
12-21|M12|ناقصTreasury cashbox/card/bank branch ownership|H|D|H|M12|M12|ED|B|RC-04|RC-04|—|P3|P|L|L|H|L
12-22|M12|ناقصProcurement branch propagation|C|D|H|M12|M12|ED|B|RC-04|RC-04|—|P3|P|L|L|H|L
12-23|M12|نداردAsset transfer branch accounting|C|D|H|M12|M12|ED|B|RC-04|RC-04|—|P3|0|L|L|H|L
12-24|M12|الزمPayroll historical branch snapshot|H|D|H|M12|M12|ED|B|RC-04|RC-04|—|P3|P|L|L|H|H
12-25|M12|نداردAudit branch context|H|D|H|M12|M12|ED|B|RC-04|RC-04|—|P3|P|H|L|H|L
12-26|M12|محدودPersian legacy alias normalization|H|NP|?|M12|M12|ENP|B|RC-04|-|—|P3|0|L|L|H|L
12-27|M12|نداردUnassigned legacy review UI|H|DB|?|M12|M12|EDB|B|RC-04|-|—|P3|P|L|L|H|L
12-28|M12|وجود نداردBranchIntegrityService|C|I|C|M12|M12|EI|B|RC-04|-|—|P3|P|L|L|H|L
13-01|M13|نیستUser model درBranch/Data Scope|C|S|C|M13|M13|ES|B|RC-13|-|—|P6|P|H|L|H|L
13-02|M13|نداردSession idle timeout/background lock|H|NP|?|M13|M13|ENP|B|RC-13|-|—|P6|0|H|L|L|L
13-03|M13|ندارندcustom role/override وhard-coded هاRole|H|EE|?|M13|M13|EEE|B|RC-13|-|—|P6|P|H|L|L|L
13-04|M13|استREPORTS دارایCASHIER|C|D|H|M13|M13|ED|B|RC-13|RC-13|—|P6|0|H|L|L|L
13-05|M13|ناسازگارcanOpenScreen permission semantics|H|D|H|M13|M13|ED|B|RC-13|RC-13|—|P6|0|H|L|L|L
13-06|M13|باز استlogin users برای همهSETTINGS|H|S|H|X13-06|M13|X|B|RC-13|-|—|P6|0|H|L|L|L
13-07|M13|هستندScreen در یکUser Administration وLogin|M|NP|?|M13|M13|ENP|B|RC-13|-|—|P6|0|H|L|L|L
13-08|M13|نداردCreate new OWNER fresh re-auth|H|NP|?|M13|M13|ENP|B|RC-13|-|—|P6|0|H|L|L|L
13-09|M13|نداردRecovery code change fresh re-auth|H|NP|?|M13|M13|ENP|B|RC-13|-|—|P6|0|H|L|L|L
13-10|M13|نمیشودinvalidate پس از استفادهRecovery code|C|NP|?|M13|M13|ENP|B|RC-13|-|—|P6|0|H|L|L|L
13-11|M13|نداردold PIN باSelf-service change PIN|H|NP|?|M13|M13|ENP|B|RC-13|-|—|P6|0|H|L|L|L
13-12|M13|ناقصOwner departure/edit/reactivate workflow|H|NP|?|M13|M13|ENP|B|RC-13|-|—|P6|0|H|L|L|L
13-13|M13|نیستRole change sensitive action|H|NP|?|M13|M13|ENP|B|RC-13|-|—|P6|0|H|L|L|L
13-14|M13|ناقصSensitiveAction coverage|H|NP|?|M13|M13|ENP|B|RC-13|-|—|P6|0|H|L|L|L
13-15|M13|نیستSensitive permit target-bound|H|NP|?|M13|M13|ENP|B|RC-13|-|—|P6|0|H|L|L|L
13-16|M13|کامل نیستRead authorization coverage|H|D|H|M13|M13|ED|B|RC-13|RC-13|—|P6|0|H|L|L|L
13-17|M13|Recipe activation outcome bypass|C|D|H|M13|M13|ED|B|RC-13|RC-13|—|P6|0|H|L|L|L
13-18|M13|Asset lifecycle permission outcome bypass|H|D|H|M13|M13|ED|B|RC-13|RC-13|—|P6|0|H|L|L|L
13-19|M13|بسیار گستردهRole mapping Manager/Accountant|H|EE|?|M13|M13|EEE|B|RC-13|-|—|P6|0|H|L|L|L
13-20|M13|نداردPIN policy role-aware/rotation metadata|M|DB|?|M13|M13|EDB|B|RC-13|-|—|P6|P|H|L|L|L
13-21|M13|PBKDF2-HMAC-SHA1 modernization|M|NP|?|M13|M13|ENP|B|RC-13|-|—|P6|0|H|L|L|L
13-22|M13|نداردSensitive screen screenshot protection|H|NP|?|M13|M13|ENP|B|RC-13|-|—|P6|0|H|L|L|L
13-23|M13|نداردrowVersion وUser master generic @Update|H|DB|?|M13|M13|EDB|B|RC-13|-|—|P6|P|H|L|L|L
14-01|M14|واقعی استAudit DB-level append-only|+|DOC|0|M14|M14|EDOC|B|RC-14|-|—|P6|0|H|L|L|L
14-02|M14|میشودDB reject ناقصInsert|+|DOC|0|M14|M14|EDOC|B|RC-14|-|—|P6|0|H|L|L|L
14-03|M14|نیستTamper-evident برابرAppend-only|C|DB|?|M14|M14|EDB|B|RC-14|-|—|P6|P|H|L|L|L
14-04|M14|کندrollback راAudit History قدیمی میتواندRestore|C|D|H|M14|M14|ED|B|RC-14|RC-14|—|P6|0|H|L|L|H
14-05|M14|نداردDB replace پایدار خارج ازRestore request/completion audit|C|D|H|M14|M14|ED|B|RC-14|RC-14|—|P6|0|H|L|L|L
14-06|M14|تعریف نشدهFactory reset audit boundary|H|NP|?|M14|M14|ENP|B|RC-14|-|—|P6|0|H|L|L|L
14-07|M14|نیستAudit event درbranchId|C|D|H|M14|M14|ED|B|RC-14|RC-14|—|P6|P|H|L|H|L
14-08|M14|ذخیره نمیشودactorRoleSnapshot|H|D|H|M14|M14|ED|B|RC-14|RC-14|—|P6|P|H|L|L|H
14-09|M14|کندoverride راhistorical actor snapshot فعلی میتواندactor display|H|D|H|M14|M14|ED|B|RC-14|RC-14|—|P6|0|H|L|L|H
14-10|M14|نمیشوندexpose کاملread model/UI درbusinessEpochDay/deviceId/globalId|H|NP|?|M14|M14|ENP|B|RC-14|-|—|P6|0|H|L|L|L
14-11|M14|business date است نهAudit date filter occurredAt|H|NP|?|M14|M14|ENP|B|RC-14|-|—|P6|0|H|L|L|L
14-12|M14|استlocal-android عمومیdeviceId|H|D|H|M14|M14|ED|B|RC-14|RC-14|—|P6|P|H|L|L|L
14-13|M14|استUI hide فقطAUDIT_SENSITIVE_VIEW|C|D|H|M14|M14|ED|B|RC-14|RC-14|—|P6|0|H|L|L|L
14-14|M14|داخلی نداردauditLogs read permission|C|D|H|M14|M14|ED|B|RC-14|RC-14|—|P6|0|H|L|L|L
14-15|M14|ناقصRedaction regex-based|C|NP|?|M14|M14|ENP|B|RC-14|-|—|P6|0|H|L|L|L
14-16|M14|استSnapshot free-form string|H|DB|?|M14|M14|EDB|B|RC-14|-|—|P6|P|H|L|L|H
14-17|M14|Snapshot >16K silent truncate|H|DB|?|M14|M14|EDB|B|RC-14|-|—|P6|P|H|L|L|H
14-18|M14|ها کامل نیستupdate در همهBefore/After|H|NP|?|M14|M14|ENP|B|RC-14|-|—|P6|0|H|L|L|L
14-19|M14|استgeneric description ولی غالبًاReason mandatory|H|NP|?|M14|M14|ENP|B|RC-14|-|—|P6|0|H|L|L|L
14-20|M14|دارندSyncRecorder reason generic بعضًاProcurement/HR|H|NP|?|M14|M14|ENP|B|RC-14|-|—|P6|0|H|L|L|L
14-21|M14|نمیشودAlert lifecycle Audit|H|NP|?|M14|M14|ENP|B|RC-14|-|—|P6|0|H|L|L|L
14-22|M14|نمیشوندAudit عادیAuthorization denials|H|PF|H|M14|M14|EPF|B|RC-14|-|—|P6|0|H|L|L|L
14-23|M14|محدود استSensitive data read audit|H|NP|?|M14|M14|ENP|B|RC-14|-|—|P6|0|H|L|L|L
14-24|M14|کامل نداردexport path ولیAUDIT_EXPORT defined|H|NP|?|M14|M14|ENP|B|RC-14|-|—|P6|0|H|L|L|L
14-25|M14|ناقصPrint/Reprint/Backup import/export/delete audit|H|D|H|M14|M14|ED|B|RC-14|RC-14|—|P6|0|H|L|L|L
14-26|M14|نمیشوندSync/Organization settings audit|H|D|H|M14|M14|ED|B|RC-14|RC-14|—|P6|0|H|L|L|L
14-27|M14|آزاد استSeverity/Action taxonomy|M|DB|?|M14|M14|EDB|B|RC-14|-|—|P6|P|H|L|L|L
14-28|M14|محدود استreference per event یک|M|DB|?|M14|M14|EDB|B|RC-14|-|—|P6|P|H|L|L|L
14-29|M14|نیستCorrelation unique/exactly-once|H|DB|?|M14|M14|EDB|B|RC-14|-|—|P6|P|H|L|L|L
14-30|M14|paging بدونAudit UI LIMIT 300|H|NP|?|M14|M14|ENP|B|RC-14|-|—|P6|0|H|L|L|L
14-31|M14|نیستLIKE %query% search scalable|H|NP|?|M14|M14|ENP|B|RC-14|-|—|P6|0|H|L|L|L
14-32|M14|مدل نداردSystem principal|H|DB|?|M14|M14|EDB|B|RC-14|-|—|P6|P|H|L|L|L
14-33|M14|وجود نداردAuditIntegrityService|C|DB|?|M14|M14|EDB|B|RC-14|-|—|P6|P|H|L|L|L
15-01|M15|نداردLocalAlertRepository Authorization|C|FX|0|X15-01|M15|X|B|RC-15|-|—|P6|0|H|L|L|L
15-02|M15|نیستAlert permission domain-specific|H|D|H|M15|M15|ED|B|RC-15|RC-15|—|P6|0|H|L|L|L
15-03|M15|نداردAlert branchId/location|C|D|H|M15|M15|ED|B|RC-15|RC-15|—|P6|P|L|L|H|L
15-04|M15|Low Stock aggregate/onHand-only|H|D|H|M15|M15|ED|B|RC-15|RC-15|—|P6|0|L|L|L|L
15-05|M15|sales_invoices Legacy ازReceivable alert|C|FX|0|X15-05|M15|X|B|RC-15|-|—|P6|0|L|L|L|L
15-06|M15|Receivable alert N+1|H|FX|0|X15-06|M15|X|B|RC-15|-|—|P6|0|L|L|L|L
15-07|M15|ناقصPayroll/attendance/asset alert branch semantics|H|D|H|M15|M15|ED|B|RC-15|RC-15|—|P6|0|L|L|H|L
15-08|M15|نام غلطATTENDANCE_ANOMALY|H|D|H|M15|M15|ED|B|RC-15|RC-15|—|P6|0|L|L|L|L
15-09|M15|واقعی نداردMaintenance alert preventive plan|H|NP|?|M15|M15|ENP|B|RC-15|-|—|P6|0|L|L|L|L
15-10|M15|پایدار نیستDismiss|C|A|C|X15-10|M15|X|B|RC-15|-|—|P6|0|L|L|L|L
15-11|M15|ندارندDismiss/Snooze/recurrence semantics|H|DB|?|M15|M15|EDB|B|RC-15|-|—|P6|P|L|L|L|L
15-12|M15|clearDismissed physical delete history|H|NP|?|M15|M15|ENP|B|RC-15|-|—|P6|0|L|L|L|H
15-13|M15|نداردAlert state actor/reason/history|H|D|H|M15|M15|ED|B|RC-15|RC-15|—|P6|P|L|L|L|H
15-14|M15|ناقصPolicy version/typed severity|H|DB|?|M15|M15|EDB|B|RC-15|-|—|P6|P|L|L|L|L
15-15|M15|اشتباهLot alert drill-down ID|C|D|H|M15|M15|ED|B|RC-15|RC-15|—|P6|0|L|L|L|L
15-16|M15|نیستInventory discrepancy/PO drill-down type-safe|H|D|H|M15|M15|ED|B|RC-15|RC-15|—|P6|0|L|L|L|L
15-17|M15|نمیکندfocus دقیق راCustomer/payroll/asset drill-down entity|H|D|H|M15|M15|ED|B|RC-15|RC-15|—|P6|0|L|L|L|L
15-18|M15|نداریمReconciliation/backup failure alerts|H|NP|?|M15|M15|ENP|B|RC-15|-|—|P6|0|L|L|L|L
15-19|M15|دیر استcritical alerts برای بعضیhour refresh-6|M|NP|?|M15|M15|ENP|B|RC-15|-|—|P6|0|L|L|L|L
15-20|M15|استAndroid notification privacy-friendly count-only|+|DOC|0|M15|M15|EDOC|B|RC-15|-|—|P6|0|L|L|L|L
16-01|M16|انجام میدهدCONTROL_VIEW write باrecordDetectedIssues|H|NP|?|M16|M16|ENP|B|RC-16|-|—|P6|0|L|L|L|L
16-02|M16|branchId=0 باUnscoped issues|H|D|H|M16|M16|ED|B|RC-16|RC-16|—|P6|P|H|L|H|L
16-03|M16|Issue dedup stale severity/metrics|H|DB|?|M16|M16|EDB|B|RC-16|-|—|P6|P|L|L|L|L
16-04|M16|نداردResolved issue recurrence model|H|DB|?|M16|M16|EDB|B|RC-16|-|—|P6|P|L|L|L|L
16-05|M16|ناقصIssue assignee user/employee validation|H|NP|?|M16|M16|ENP|B|RC-16|-|—|P6|0|L|L|L|L
16-06|M16|ناقصIssue due date chronology/resolve assignee policy|H|NP|?|M16|M16|ENP|B|RC-16|-|—|P6|0|L|L|L|L
16-07|M16|نداردTask assignee enforcement|C|S|C|M16|M16|ES|B|RC-16|-|—|P6|0|H|L|L|L
16-08|M16|نداردTask approval maker-checker|C|S|C|M16|M16|ES|B|RC-16|-|—|P6|0|H|L|L|L
16-09|M16|نداردTask source issue branch compatibility|H|D|H|M16|M16|ED|B|RC-16|RC-16|—|P6|0|L|L|H|L
16-10|M16|نداردTask create/transition idempotency|H|DB|?|M16|M16|EDB|B|RC-16|-|—|P6|P|L|L|L|L
16-11|M16|ناقصReject/Cancel reason/history|H|DB|?|M16|M16|EDB|B|RC-16|-|—|P6|P|L|L|L|H
16-12|M16|string reference فقطAttachment|H|DB|?|M16|M16|EDB|B|RC-16|-|—|P6|P|L|L|L|L
16-13|M16|ناقصaudit قابل اضافه شدن وfinal task پس ازAttachment|H|DB|?|M16|M16|EDB|B|RC-16|-|—|P6|P|H|L|L|L
16-14|M16|ناقصChecklist template version lifecycle|H|DB|?|M16|M16|EDB|B|RC-16|-|—|P6|P|L|L|L|L
16-15|M16|اجرا کردBranch B را میتوان درTemplate Branch A|C|D|H|M16|M16|ED|B|RC-16|RC-16|—|P6|0|L|L|H|L
16-16|M16|کامل نیستInactive template start guard|H|NP|?|M16|M16|ENP|B|RC-16|-|—|P6|0|L|L|L|L
16-17|M16|نداردChecklist run idempotency/unique business key|C|I|C|M16|M16|EI|B|RC-16|-|—|P6|P|L|L|L|L
16-18|M16|ناقصAssigned employee checklist validation|H|D|H|M16|M16|ED|B|RC-16|RC-16|—|P6|0|L|L|L|L
16-19|M16|را بررسی نمیکندcompleteChecklistItem parent run status|C|I|C|M16|M16|EI|B|RC-16|-|—|P6|0|L|L|L|L
16-20|M16|نیستChecklist item update + audit atomic|C|I|C|M16|M16|EI|B|RC-16|-|—|P6|0|H|L|L|L
16-21|M16|نمیشودPerformer enforce|H|D|H|M16|M16|ED|B|RC-16|RC-16|—|P6|0|L|L|L|L
16-22|M16|نمیکندvalidate فایل واقعیPhoto requirement|H|DB|?|M16|M16|EDB|B|RC-16|-|—|P6|P|L|L|L|L
16-23|M16|نداردChecklist approval maker-checker|C|D|H|M16|M16|ED|B|RC-16|RC-16|—|P6|0|L|L|L|L
16-24|M16|نداردApproved checklist DB immutability trigger|H|I|H|M16|M16|EI|B|RC-16|-|—|P6|P|L|L|L|L
16-25|M16|را استفاده میکندHistorical Daily Brief current AR/issues/tasks|H|D|H|M16|M16|ED|B|RC-16|RC-16|—|P6|0|L|L|L|H
16-26|M16|کامل نیستManagement snapshot branch scope/read authorization|H|D|H|M16|M16|ED|B|RC-16|RC-16|—|P6|0|H|L|H|H
16-27|M16|عمومیBudget permission ACCOUNTING|H|D|H|M16|M16|ED|B|RC-16|RC-16|—|P6|0|H|L|L|L
16-28|M16|استفاده میکندPermission.AUDIT ازShift swap approval|C|D|H|M16|M16|ED|B|RC-16|RC-16|—|P6|0|H|L|L|L
17-01|M17|قوی استBackup/Restore core|+|DOC|0|M17|M17|EDOC|B|RC-18|-|—|P6|0|L|L|L|L
17-02|M17|باز میشودRoom+migrations+cipher/integrity_check باRestore candidate|+|DOC|0|M17|M17|EDOC|B|RC-18|-|—|P6|0|L|L|L|L
17-03|M17|باز استlogin users برای همهSettings|H|D|H|M17|M17|ED|B|RC-18|RC-18|—|P6|0|L|L|L|L
17-04|M17|نداردOrganizationSettingsStore permission/audit|H|D|H|M17|M17|ED|B|RC-18|RC-18|—|P6|0|H|L|L|L
17-05|M17|نداردBackup policy store domain permission/audit|H|D|H|M17|M17|ED|B|RC-18|RC-18|—|P6|0|H|L|L|L
17-06|M17|ناقصBackup create/export/import/delete audit|H|D|H|M17|M17|ED|B|RC-18|RC-18|—|P6|0|H|L|L|L
17-07|M17|محدودRetention prune evidence|H|DB|?|M17|M17|EDB|B|RC-18|-|—|P6|P|L|L|L|L
17-08|M17|مشکلRestore audit persistence/rollback|C|D|H|M17|M17|ED|B|RC-18|RC-18|—|P6|0|H|L|L|L
17-09|M17|صریح نداردRestore foreign_key_check|H|NP|?|M17|M17|ENP|B|RC-18|-|—|P6|0|L|L|L|L
17-10|M17|نمیشوندManifest counts verify after open|M|NP|?|M17|M17|ENP|B|RC-18|-|—|P6|0|L|L|L|L
17-11|M17|ضعیفترLegacy backup trust path|M|NP|?|M17|M17|ENP|B|RC-18|-|—|P6|0|L|L|L|L
17-12|M17|محدودAutomatic backup work constraints|M|NP|?|M17|M17|ENP|B|RC-18|-|—|P6|0|L|L|L|L
17-13|M17|ناقصFactory reset phases across DB/preferences crash consistency|H|DB|?|M17|M17|EDB|B|RC-18|-|—|P6|P|L|L|L|L
17-14|M17|هستندTheme/currency/font preferences global device-level|M|PD|?|M17|M17|EPD|B|RC-18|-|—|P6|0|L|L|L|L
17-15|M17|SyncSafetyGate.isProductionReady=false|+|DOC|0|X17-15|M17|X|B|RC-18|-|—|P6|0|L|L|L|L
17-16|M17|نداردremote apply وSync push-only|C|NP|?|M17|M17|ENP|B|RC-18|-|—|P6|0|L|L|L|L
17-17|M17|کافی نیستERP برایConflict resolution timestamp-based|C|NP|?|M17|M17|ENP|B|RC-18|-|—|P6|0|L|L|L|L
17-18|M17|کامل نداردentity اغلبOutbox payload|C|NP|?|M17|M17|ENP|B|RC-18|-|—|P6|0|L|L|L|L
17-19|M17|Sync revision MAX+1|C|FP|0|X17-19|M17|X|B|RC-18|-|—|P6|0|L|L|L|L
17-20|M17|ناقصUnique revision business key|H|DB|?|M17|M17|EDB|B|RC-18|-|—|P6|P|L|L|L|L
17-21|M17|Dead-letter requeue authorization bypass|H|D|H|M17|M17|ED|B|RC-18|RC-18|—|P6|0|H|L|L|L
17-22|M17|استفاده میکندBACKUP ازSync permission|H|D|H|M17|M17|ED|B|RC-18|RC-18|—|P6|0|H|L|L|L
17-23|M17|محلی حدس زده میشودAccess token expiry|H|NP|?|M17|M17|ENP|B|RC-18|-|—|P6|0|L|L|L|L
17-24|M17|regex-based/ دستیTransport JSON parsing|H|NP|?|M17|M17|ENP|B|RC-18|-|—|P6|0|L|L|L|L
17-25|M17|خوب استKeystore token storage وHTTPS validation|+|DOC|0|M17|M17|EDOC|B|RC-18|-|—|P6|0|L|L|L|L
18-01|M18|واقعی نیستDB Search|H|P|H|X18-01|M18|X|B|RC-21|-|—|P7|0|L|L|L|L
18-02|M18|debounce و بدونComposable درSearch|H|D|H|M18|M18|ED|B|RC-21|RC-21|—|P7|0|L|L|L|L
18-03|M18|list کلfilter بعد ازtake(8)|H|D|H|M18|M18|ED|B|RC-21|RC-21|—|P7|0|L|L|L|L
18-04|M18|نداردBranch/Warehouse scope|C|D|H|M18|M18|ED|B|RC-21|RC-21|—|P7|P|H|L|H|L
18-05|M18|search-specific authorization بدونresult درFinancial data|H|D|H|M18|M18|ED|B|RC-21|RC-21|—|P7|0|H|L|L|L
18-06|M18|مستقل نداردGlobal Search permission|H|D|H|X18-06|M18|X|B|RC-21|RC-21|—|P7|P|H|L|L|L
18-07|M18|نیستglobal واقعًا|H|FR|?|M18|M18|EFR|B|RC-21|-|—|P7|0|L|L|L|L
18-08|M18|پیدا نمیشودrecent-state limit به دلیلOld stock movement|H|D|H|M18|M18|ED|B|RC-21|RC-21|—|P7|0|L|L|L|L
18-09|M18|Purchase/employee/journal state unbounded|H|D|H|M18|M18|ED|B|RC-21|RC-21|—|P7|0|L|L|L|L
18-10|M18|محدودPersian normalization|M|DB|?|M18|M18|EDB|B|RC-21|-|—|P7|P|L|L|L|L
18-11|M18|واقعی نداردRanking|M|EE|?|M18|M18|EEE|B|RC-21|-|—|P7|P|L|L|L|L
18-12|M18|نمایش داده نمیشودBranch/date context result|H|D|H|M18|M18|ED|B|RC-21|RC-21|—|P7|P|L|L|H|L
18-13|M18|نمیکندselect راStock movement drill-down movement exact|H|NP|?|M18|M18|ENP|B|RC-21|-|—|P7|0|L|L|L|L
18-14|M18|حفظ نمیشودDrill-down scope/date context|H|D|H|M18|M18|ED|B|RC-21|RC-21|—|P7|0|H|L|L|L
18-15|M18|را پوشش میدهندtext matcher ها فقطTest|H|NP|?|M18|M18|ENP|B|RC-21|-|—|P7|0|L|L|L|L
19-01|M19|نداردsupplierCode/globalId|H|DB|?|M19|M19|EDB|B|RC-22|-|—|P3|P|L|L|L|L
19-02|M19|ناقصLegal identity|H|DB|?|M19|M19|EDB|B|RC-22|-|—|P3|P|L|L|L|L
19-03|M19|name فقطDuplicate detection|H|DB|?|M19|M19|EDB|B|RC-22|-|—|P3|P|L|L|L|L
19-04|M19|کامل نیستName unique/lookup collation semantics|H|DB|?|M19|M19|EDB|B|RC-22|-|—|P3|P|L|L|L|L
19-05|M19|میکندCreate same inactive name implicit reactivate|H|NP|?|M19|M19|ENP|B|RC-22|-|—|P3|0|L|L|L|L
19-06|M19|نداردBank accounts|H|DB|?|M19|M19|EDB|B|RC-22|-|—|P3|P|L|L|L|L
19-07|M19|ناقصMultiple contacts/email/addresses/contract master|H|EE|?|M19|M19|EEE|B|RC-22|-|—|P3|P|L|L|L|L
19-08|M19|نداردPayment terms version/history|H|DB|?|M19|M19|EDB|B|RC-22|-|—|P3|P|L|L|L|H
19-09|M19|نداردRowVersion/idempotent create|H|DB|?|M19|M19|EDB|B|RC-22|-|—|P3|P|L|L|L|L
19-10|M19|استPermission SUPPLIERS coarse|H|D|H|M19|M19|ED|B|RC-22|RC-22|—|P3|0|H|L|L|L
19-11|M19|نداردDeactivate dependency check|C|D|H|M19|M19|ED|B|RC-22|RC-22|—|P3|0|L|L|L|L
19-12|M19|میکندbulk clear راDeactivate preferredSupplier references|H|D|H|M19|M19|ED|B|RC-22|RC-22|—|P3|0|L|L|L|L
19-13|M19|نداردSupplier merge|H|DB|?|M19|M19|EDB|B|RC-22|-|—|P3|P|L|L|L|L
19-14|M19|نیستsnapshot یکدستRename historical display|H|D|H|M19|M19|ED|B|RC-22|RC-22|—|P3|P|L|L|L|H
19-15|M19|نیستSupplier audit business-rich|H|D|H|M19|M19|ED|B|RC-22|RC-22|—|P3|P|H|L|L|L
19-16|M19|میشودOffer history replace|H|DB|?|M19|M19|EDB|B|RC-22|-|—|P3|P|L|L|L|H
19-17|M19|نداردUI جامعSupplier ledger|H|EE|?|M19|M19|EEE|B|RC-22|-|—|P3|0|L|L|L|L
19-18|M19|محدودInactive supplier historical archive view|H|D|H|M19|M19|ED|B|RC-22|RC-22|—|P3|0|L|L|L|H
20-01|M20|پیاده شدهresponsive navigation وRTL|+|DOC|0|M20|M20|EDOC|B|RC-20|-|—|P7|0|L|L|L|L
20-02|M20|مستقیمText literal 1000 بیش از|H|A|H|X20-02|M20|X|B|RC-20|-|UAT-02|P7|0|L|L|L|L
20-03|M20|app_name فقطstrings.xml تقریبًا صفر وstringResource|H|D|H|M20|M20|ED|B|RC-20|RC-20|—|P7|P|L|L|L|L
20-04|M20|terminology mix انگلیسی/فارسی|H|D|H|M20|M20|ED|B|RC-20|RC-20|—|P7|0|L|L|L|L
20-05|M20|دو سیستم رنگ مستقل|H|D|H|M20|M20|ED|B|RC-20|RC-20|—|P7|0|L|L|L|L
20-06|M20|نیستErpPalette dark-mode aware|C|PF|H|M20|M20|EPF|B|RC-20|-|—|P7|0|L|L|L|L
20-07|M20|shell/navigation درColor.White/Canvas hard-coded|H|D|H|M20|M20|ED|B|RC-20|RC-20|—|P7|0|L|L|L|L
20-08|M20|ثابت/حدود صد رنگ مستقیم|H|D|H|M20|M20|ED|B|RC-20|RC-20|—|P7|0|L|L|L|L
20-09|M20|مشخص نیستtypography اختصاصی درPersian font family|M|NP|?|M20|M20|ENP|B|RC-20|-|—|P7|0|L|L|L|L
20-10|M20|کافی نیستAccessibility automated test|H|T|?|M20|M20|ET|B|RC-20|-|—|P7|0|L|L|L|L
20-11|M20|الزمLarge font/tablet/grid semantics system test|H|T|?|M20|M20|ET|B|RC-20|-|—|P7|0|L|L|L|L
20-12|M20|small-height/large-font tests های بلند نیاز بهDialog|M|T|?|M20|M20|ET|B|RC-20|-|—|P7|0|L|L|L|L
20-13|M20|شودindependent buttons enforce باید رویIcon accessibility|M|T|?|M20|M20|ET|B|RC-20|-|—|P7|0|L|L|L|L
20-14|M20|وجود نداردDesign token/string static gate|H|D|H|M20|M20|ED|B|RC-20|RC-20|—|P7|P|L|L|L|L
20-15|M20|کامًال یکدست نیستLoading/Error/Empty pattern|H|NP|?|M20|M20|ENP|B|RC-20|-|—|P7|0|L|L|L|L
20-16|M20|دیده میشودbackend دیر درbusiness rules بعضیValidation|H|NP|?|M20|M20|ENP|B|RC-20|-|—|P7|0|L|L|L|L
20-17|M20|یکی نیستapp typography باPrint typography|M|D|M|M20|M20|ED|B|RC-20|RC-20|—|P7|0|L|L|L|L
20-18|M20|ناقصPrint/settings profile integration|H|D|H|M20|M20|ED|B|RC-20|RC-20|—|P7|0|L|L|L|L
21-01|M21|تقریبًا صفرProduction واقعی درPaging|C|P|H|X21-01|M21|X|B|RC-21|-|—|P7|P|L|L|L|L
21-02|M21|Purchase/Employee/Sales closures/search lists unbounded|H|D|H|M21|M21|ED|B|RC-21|RC-21|—|P7|0|L|L|L|L
21-03|M21|میکندload راdaily sales lines تمامobserveAllLines|C|D|H|M21|M21|ED|B|RC-21|RC-21|—|P7|0|L|L|L|L
21-04|M21|Profitability projection full line table group-by|H|D|H|M21|M21|ED|B|RC-21|RC-21|—|P7|0|L|L|L|L
21-05|M21|%Accounting/Audit text search LIKE %query|H|D|H|M21|M21|ED|B|RC-21|RC-21|—|P7|P|H|L|L|L
21-06|M21|Global Search in-memory full list scan|H|D|H|M21|M21|ED|B|RC-21|RC-21|—|P7|0|L|L|L|L
21-07|M21|Procurement multiple large flows in-memory combine/sort|H|D|H|M21|M21|ED|B|RC-21|RC-21|—|P7|0|L|L|L|L
21-08|M21|های متعددPayroll preparation groupBy dataset|H|D|H|M21|M21|ED|B|RC-21|RC-21|—|P7|0|L|L|L|L
21-09|M21|Treasury per-account balance flow|M|NP|?|M21|M21|ENP|B|RC-21|-|—|P7|0|L|L|L|L
21-10|M21|Dashboard giant subqueries|H|D|H|M21|M21|ED|B|RC-21|RC-21|—|P7|0|L|L|L|L
21-11|M21|Supplier price insights correlated subqueries|H|D|H|M21|M21|ED|B|RC-21|RC-21|—|P7|0|L|L|L|L
21-12|M21|Room Flow full-list emits allocation/GC cost|H|D|H|M21|M21|ED|B|RC-21|RC-21|—|P7|0|L|L|H|L
21-13|M21|استPerformance test 500-row source/in-memory contract|C|T|?|M21|M21|ET|B|RC-21|-|—|P7|0|L|L|L|L
21-14|M21|نداریم100k/1M row tests|H|T|?|M21|M21|ET|B|RC-21|-|—|P7|P|L|L|L|L
21-15|M21|نداردMacrobenchmark/cold start/scroll/search evidence|H|EE|?|M21|M21|EEE|B|RC-21|-|—|P7|0|L|L|L|L
21-16|M21|نقاط مثبتInventory Load Planner/LazyColumn/stable keys|+|DOC|0|M21|M21|EDOC|B|RC-21|-|—|P7|0|L|L|L|L
21-17|M21|نداریمRealistic dataset profiles/query budget|H|T|?|M21|M21|ET|B|RC-21|-|—|P7|0|L|L|L|L
21-18|M21|نداردExplain Query Plan/Index effectiveness CI|H|T|?|M21|M21|ET|B|RC-21|-|—|P7|0|L|L|L|L
21-19|M21|نداردBackup/Restore large DB stress evidence|H|T|?|M21|M21|ET|B|RC-21|-|—|P7|0|L|L|L|L
21-20|M21|ناقصAudit growth/archive/search strategy performance|H|D|H|M21|M21|ED|B|RC-21|RC-21|—|P7|P|H|L|L|L
22-01|M22|Single :app module|H|EE|?|M22|M22|EEE|B|RC-23|-|—|P6|0|L|L|L|L
22-02|M22|داردdata imports مستقیمUI|H|EE|?|M22|M22|EEE|B|RC-23|-|—|P6|0|L|L|L|L
22-03|M22|میشناسدDB Entity حداقل یکDomain|C|NP|?|M22|M22|ENP|B|RC-23|-|—|P6|0|L|L|L|L
22-04|M22|بزرگAppContainer service locator|H|EE|?|M22|M22|EEE|B|RC-23|-|—|P6|0|L|L|L|L
22-05|M22|متعددGod classes|H|EE|?|M22|M22|EEE|B|RC-23|-|—|P6|0|L|L|L|L
22-06|M22|blocker های موازی مهمترینCanonical Truth|C|D|H|M22|M22|ED|B|RC-23|RC-23|—|P6|0|L|L|L|L
22-07|M22|دو نسلCoarse+granular permission|H|D|H|M22|M22|ED|B|RC-23|RC-23|—|P6|0|H|L|L|L
22-08|M22|واحد نداردBusiness Command Envelope|H|D|H|M22|M22|ED|B|RC-23|RC-23|—|P6|0|L|L|L|L
22-09|M22|باقیماندهMAX()+1|H|D|H|M22|M22|ED|B|RC-23|RC-23|—|P6|0|L|L|L|L
22-10|M22|backend ضعیفتر ازUI resource architecture|H|D|H|M22|M22|ED|B|RC-23|RC-23|—|P6|0|L|L|L|L
22-11|M22|نداردdestructive migration کامل وMigration chain 1→55|+|DOC|0|X22-11|M22|X|B|RC-23|-|—|P6|0|L|L|L|L
22-12|M22|کامل نیستHistorical schema exports|H|T|?|M22|M22|ET|B|RC-23|-|—|P6|P|L|L|L|H
22-13|M22|را دستی میسازندmigration tests old schema بعضی|H|T|?|M22|M22|ET|B|RC-23|-|—|P6|0|L|L|L|L
22-14|M22|دورهای جامع نیستStartup full integrity/fk checks|H|T|?|M22|M22|ET|B|RC-23|-|—|P6|0|L|L|L|L
22-15|M22|یکسان نیستmodules بینDB guard strength|H|D|H|M22|M22|ED|B|RC-23|RC-23|—|P6|0|L|L|L|L
22-16|M22|Ignore@ بدونtest 522 حدود|+|DOC|0|M22|M22|EDOC|B|RC-23|-|—|P6|0|L|L|L|L
22-17|M22|نامتوازنBusiness invariant coverage|H|T|?|M22|M22|ET|B|RC-23|-|—|P6|0|L|L|L|L
22-18|M22|میکنندreinforce راAlert tests legacy source|H|D|H|M22|M22|ED|B|RC-23|RC-23|—|P6|0|L|L|L|L
22-19|M22|static/source contract بیشترPerformance tests|H|T|?|M22|M22|ET|B|RC-23|-|—|P6|0|L|L|L|L
22-20|M22|متعددstatic gates وCI API23/API35/16KB|+|DOC|0|M22|M22|EDOC|B|RC-23|-|—|P6|0|L|L|L|L
22-21|M22|نمیکندgate را لزومًاtests.yml direct push main|C|NP|?|M22|M22|ENP|B|RC-23|-|—|P6|0|L|L|L|L
22-22|M22|ندارندtests workflow same-SHA final gate وProduction readiness|H|FX|0|X22-22|M22|X|B|RC-24|-|—|P8|0|L|L|L|L
22-23|M22|PHASE-3-CI-REPORT placeholders|H|NP|?|M22|M22|ENP|B|RC-23|-|—|P6|0|L|L|L|L
22-24|M22|unsigned release کوتاه وArtifact retention evidence|H|NP|?|M22|M22|ENP|B|RC-23|-|—|P6|0|L|L|L|L
22-25|M22|رسمی مشاهده نشدSBOM/vulnerability/SAST|M|T|?|M22|M22|ET|B|RC-23|-|—|P6|0|L|L|L|L
22-26|M22|هستندGitHub Actions commit-pinned|+|DOC|0|M22|M22|EDOC|B|RC-23|-|—|P6|0|L|L|L|L

## P0 revalidation
P0-01|VALID_P0|.Historical Read-only فقطsales_invoices Legacy ؛Canonical Sales Truth فقط یک
P0-02|VALID_P0|Direct GL liquidity ؛ حذفTreasury-owned ها فقطCash/Bank movement تمام .paths
P0-03|VALID_P0|درReceivable Master + Allocation + Ledger + Treasury + GL .idempotent اتمیک وCollectReceivableCommand
P0-04|VALID_P0|.Purchase/Asset/Maintenance payables برایAP Subledger canonical
P0-05|VALID_P0|.Backend واقعی درWarehouse Scope وUser Branch Scope
P0-06|VALID_P0|. مخفیdefault warehouse ؛ حذفbranch-compatible صریح وSales Location
P0-07|VALID_P0|.GRN/Invoice/Settlement تاRequisition ازProcurement branch/location
P0-08|VALID_P0|.zero-price approval bypass وDirect Purchase bypass کنترل/حذف
P0-09|VALID_P0|.partial receipt/multiple invoice باThree-Way Match line-level
P0-10|VALID_P0|. صحیحPurchase Return lot/location غیرممکن؛Lot بدونLot-controlled stock
P0-11|VALID_P0|.Lot-level integrity وGL posting باInventory Count
P0-12|VALID_P0|.Reservation/Damaged/Expired available stock canonical
P0-13|VALID_P0|. واقعیSales/COGS درApproved Substitution وNested Recipe
P0-14|VALID_P0|.immutable کامًالactive version بسته وRecipe Activation permission bypass
P0-15|VALID_P0|. کندrewrite راhistory نبایدBackdated Recipe Activation/Retire
P0-16|VALID_P0|.KPI/management خارج ازpayroll_runs Legacy ؛Truth تنهاPayroll V2
P0-17|VALID_P0|.Payroll تنها ورودیAttendance canonical aggregator
P0-18|DOWNGRADED_TO_P1|.Payroll legal rules versioned/explainable
P0-19|VALID_P0|. جداcard/petty cash ؛Treasury Account→GL semantic one-to-one
P0-20|VALID_P0|.typed commands حذف وTreasury sourceType free-text
P0-21|VALID_P0|.prechecks/approval/reauth باAccounting Period Close/Reopen
P0-22|VALID_P0|.Asset branch transfer accounting reclassification
P0-23|VALID_P0|.lifecycle commands idempotent بسته وAsset edit lifecycle bypass
P0-24|VALID_P0|.ON_HOLD credit block وCustomer COMPANY/PERSON edit bug
P0-25|VALID_P0|.rewrite posted documents بدونCustomer Merge
P0-26|VALID_P0|.stock/employee/payroll/AR/AP/assets/sales precheck باBranch closure workflow
P0-27|VALID_P0|. شودbranch direct reassign نتواندstock دارایWarehouse
P0-28|VALID_P0|. حساسbusiness tables برایBranch FK/integrity
P0-29|VALID_P0|.Sales day close DB guards branch-aware
P0-30|VALID_P0|.error≠zero وcanonical sources فقطDashboard
P0-31|VALID_P0|. صفرfuture data leak واقعی؛Historical dashboard/reports As-of
P0-32|DOWNGRADED_TO_P1|. نبیندCashier executive/payroll/assets data تفکیک؛Report permission
P0-33|VALID_P0|. محافظت شوندdata boundary درAudit sensitive snapshots
P0-34|VALID_P0|.persistent restore trace وAudit restore/tamper evidence
P0-35|VALID_P0|.Task/Checklist assignee enforcement + maker-checker + final immutability
P0-36|VALID_P0|.Alert canonical/branch/domain permission + persistent dismiss
P0-37|VALID_P0|.backup export/import/delete trace ؛permission/audit حساسSettings
P0-38|ALREADY_FIXED|. بماندdisabled کاملpush+pull+remote apply+conflict/idempotency تاSync
P0-39|DOWNGRADED_TO_P1|.sensitive results permission-aware وGlobal Search DB-based/scoped
P0-40|DOWNGRADED_TO_P1|.financial/legal identity master وSupplier deactivate dependency check
P0-41|DOWNGRADED_TO_P1|.unbounded hot queries و حذفgrowing ledgers/lists واقعی برایPaging
P0-42|DOWNGRADED_TO_P1|.production قبل ازLarge dataset performance evidence
P0-43|VALID_P0|. قابل دورزدن نباشندraw engines ؛Layer outcome authorization
P0-44|ALREADY_FIXED|.static+unit+instrumentation+release برایSame-commit CI Gate
P0-45|DOWNGRADED_TO_P1|.signed production release ؛SHA/Run IDs/artifact hash واقعیCI Evidence

## DB impact
DB-01|User branch/warehouse persisted scope|current session has no canonical persisted assignment|prefer policy mapping first|HIGH|P3/P6
DB-02|AP subledger completeness|not one payable master for every payable event|temporary canonical read model only|HIGH|P2
DB-03|Audit hash/branch/role/device context|full forensic context not proven|external anchor possible|HIGH|P6
DB-04|Alert branch/location/lifecycle|AppAlertEntity lacks canonical scope/history|derive only where historically reliable|MEDIUM|P6
DB-05|Supplier legal/financial master|full legal/bank/terms history absent|avoid enterprise fields unless required|MEDIUM|P3
DB-06|Historical as-of snapshots|some reads join current state|derive from immutable events first|HIGH|P6
DB-07|Depreciation Quantity/Reason UAT|DepreciationDraft has no per-event fields|keep asset quantity + audited reason if sufficient|HIGH|P5
DB-08|Paging/FTS|large-list/search may need indexes/FTS|measure PagingSource/indexed query first|MEDIUM|P7

## Product locks / preserve
OOS: POS/table/waiter/KDS/hold/split/order lifecycle/POS receipt/reservation. Daily Sales=Financial Aggregate. Independent marketing CRM excluded; AR/collections retained. No hard-coded brand.
PRESERVE_DO_NOT_REWRITE: double-entry/balance; posted immutability/reversal/idempotency; Room1→55/no destructive; SQLCipher/Keystore; fixed-point Rial; inventory ledger/auth boundary; append-only audit/mandatory actor; Payroll V2 snapshots/payment reversal; numeric branchId; transaction boundaries; attendance raw-event immutability; sync fail-closed; same-SHA CI.

## Phase map
P2=FINANCIAL TRUTH; P3=BRANCH+INVENTORY+PROCUREMENT; P4=PERSONNEL+ATTENDANCE+PAYROLL; P5=RECIPE+COSTING+ASSETS; P6=GOVERNANCE+SECURITY+DATA TRUST; P7=UAT+UI/UX+REPORTING+PERFORMANCE; P8=FINAL VERIFICATION+RELEASE.

## Acceptance
AUDIT_PAGES_REVIEWED=106; OWNER_UAT_REVIEWED=15; TOTAL_FINDINGS_REVIEWED=505; VALID_P0_COUNT=36; P0_ALREADY_FIXED=2; P0_DOWNGRADED=7; P0_OUT_OF_SCOPE=0; BUSINESS_LOGIC_CHANGED=NO; ROOM_VERSION=55; SCHEMA_CHANGED=NO; MIGRATION_ADDED=NO; POS_REINTRODUCED=NO; INDEPENDENT_CRM_REINTRODUCED=NO.
NEXT_PHASE=PHASE 2 — FINANCIAL TRUTH
