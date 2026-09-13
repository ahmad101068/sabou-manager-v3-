#!/usr/bin/env python3
from pathlib import Path
import re, sys
ROOT = Path(__file__).resolve().parents[1]
errors=[]

def require(path, patterns):
    p=ROOT/path
    if not p.exists():
        errors.append(f'missing:{path}'); return
    text=p.read_text(encoding='utf-8')
    for pattern in patterns:
        if pattern not in text: errors.append(f'missing-token:{path}:{pattern}')

require(Path('app/src/main/java/ir/restaurant/management/ui/ResponsiveErpLayout.kt'), ['ErpWindowClass','COMPACT','MEDIUM','EXPANDED','ErpResponsiveNavigationFrame','NavigationRail'])
require(Path('app/src/main/java/ir/restaurant/management/ui/ManagementDataGrid.kt'), ['ManagementDataGrid','MobileSmartRow','AdaptiveManagementList','GridSummaryFooter'])
require(Path('app/src/main/java/ir/restaurant/management/ui/ManagementWorkflowScreens.kt'), ['MANAGEMENT_ISSUES','MANAGEMENT_TASKS','CHECKLISTS','DAILY_BRIEF','CanonicalBranchSelector','AdaptiveManagementList','ChecklistItemDialog'])
require(Path('app/src/main/java/ir/restaurant/management/ui/NavigationHubScreens.kt'), ['خانه' if False else 'مسائل مدیریتی','وظایف','چک‌لیست‌ها','گزارش روزانه مدیریت','فروش روزانه','مطالبات'])
require(Path('app/src/main/java/ir/restaurant/management/ui/DashboardScreen.kt'), ['home_management_overview','HomeRevenueTrendCard','home_no_branch_state','درآمد','سود ناخالص','درصد بهای مواد غذایی','سود عملیاتی تخمینی','مطالبات جدید','وصول مطالبات','مسائل بحرانی','وظایف معوق'])

dashboard=(ROOT/'app/src/main/java/ir/restaurant/management/ui/DashboardScreen.kt').read_text(encoding='utf-8')
for forbidden in ['val revenue =', 'val grossProfit =', 'val foodCostPercent =', 'از گزارش روزانه', 'از مرکز وظایف', 'بعداً تکمیل می‌شود']:
    if forbidden in dashboard: errors.append(f'home-canonical-read-model-bypass:{forbidden}')
if 'managementOverview.readModel' not in dashboard: errors.append('home-canonical-read-model-missing')

grid=(ROOT/'app/src/main/java/ir/restaurant/management/ui/ManagementDataGrid.kt').read_text(encoding='utf-8')
for token in ['LazyColumn', 'items(rows, key = key)', 'onPreviewKeyEvent', 'Key.Tab', 'Key.Enter', 'Key.Escape', 'onCommitAll']:
    if token not in grid: errors.append(f'management-grid-incomplete:{token}')

navigation=(ROOT/'app/src/main/java/ir/restaurant/management/ui/AppRoutes.kt').read_text(encoding='utf-8')
for screen in ['MANAGEMENT_ISSUES', 'MANAGEMENT_TASKS', 'CHECKLISTS', 'DAILY_BRIEF']:
    mapping = navigation[navigation.find('fun AppScreen.topLevelDestination'):navigation.find('fun AppScreen.topLevelDestination') + 1200]
    if f'AppScreen.{screen}' not in mapping or '-> AppScreen.CONTROL_HUB' not in mapping:
        errors.append(f'control-hub-mapping-missing:{screen}')

# Operational branch identity must use canonical selector, not editable free-text branch fields.
ui=ROOT/'app/src/main/java/ir/restaurant/management/ui'
for p in ui.glob('*.kt'):
    if p.name == 'BranchManagementScreen.kt':
        continue
    text=p.read_text(encoding='utf-8')
    for pat in [r'label\s*=\s*\{\s*Text\("نام شعبه"\)', r'var\s+branchName\s+by\s+remember', r'var\s+branch\s+by\s+remember']:
        if re.search(pat,text): errors.append(f'free-text-branch:{p.name}:{pat}')

for path in [
    'PurchaseOperationsScreens.kt','ProcurementControlUi.kt','PersonnelScreens.kt','AssetScreens.kt','InventoryItemCenterScreen.kt'
]:
    text=(ui/path).read_text(encoding='utf-8')
    if 'CanonicalBranchSelector' not in text: errors.append(f'canonical-selector-missing:{path}')

sales=(ui/'DailySalesScreens.kt').read_text(encoding='utf-8')
for token in ['CanonicalBranchSelector', 'Amount To Settle', 'Settlement Total', 'Difference']:
    if token not in sales: errors.append(f'daily-sales-ui-missing:{token}')

crm=(ui/'CrmScreen.kt').read_text(encoding='utf-8')
for token in ['receivables_branch_selector', 'کل مطالبات باز', 'وصول امروز', 'ReceivableCollectionDialog']:
    if token not in crm: errors.append(f'receivables-ui-missing:{token}')

routes=(ui/'AppRoutes.kt').read_text(encoding='utf-8')
for forbidden in ['TABLES(', 'HALL(', 'RESERVATION(', 'WAITER(', 'KDS(', 'KITCHEN_TICKET(']:
    if forbidden in routes: errors.append(f'forbidden-route:{forbidden}')

if errors:
    print('PHASE3_UI_FOUNDATION=FAIL')
    for e in errors: print(e)
    sys.exit(1)
print('PHASE3_UI_FOUNDATION=PASS')
