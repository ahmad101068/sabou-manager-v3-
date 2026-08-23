from pathlib import Path
import sys
root=Path(sys.argv[1] if len(sys.argv)>1 else 'phase8-source')
ui=root/'app/src/main/java/ir/restaurant/management/ui/ProcurementControlUi.kt'
asset=root/'app/src/main/java/ir/restaurant/management/ui/AssetScreens.kt'
test=root/'app/src/androidTest/java/ir/restaurant/management/ui/EnterpriseCoreComposeE2ETest.kt'

s=ui.read_text()
needle='''                    )\n                }).validated()'''
repl='''                    )\n                }, destinationLocationId = order.destinationLocationId).validated()'''
if needle in s: s=s.replace(needle,repl,1)
elif 'destinationLocationId = order.destinationLocationId' not in s: raise SystemExit('receipt draft anchor missing')
ui.write_text(s)

s=asset.read_text()
old='''OutlinedTextField(reason, { reason = it.take(300) }, label = { Text("دلیل / شرح استهلاک") }, modifier = Modifier.fillMaxWidth())'''
new='''OutlinedTextField(reason, { reason = it.take(300) }, label = { Text("دلیل / شرح استهلاک") }, modifier = Modifier.fillMaxWidth().testTag("asset_depreciation_reason"))'''
if old in s: s=s.replace(old,new,1)
elif 'asset_depreciation_reason' not in s: raise SystemExit('asset reason anchor missing')
asset.write_text(s)

s=test.read_text()
imp='import androidx.compose.ui.test.assertIsEnabled\n'
if imp not in s:
    s=s.replace('import androidx.compose.ui.test.assertIsDisplayed\n','import androidx.compose.ui.test.assertIsDisplayed\n'+imp,1)
old='''        composeRule.onNodeWithTag("asset_depreciation_$assetId").performClick()\n        composeRule.onNodeWithTag("asset_depreciation_submit").performClick()'''
new='''        composeRule.onNodeWithTag("asset_depreciation_$assetId").performClick()\n        composeRule.onNodeWithTag("asset_depreciation_reason").performTextReplacement("استهلاک ماهانه E2E")\n        composeRule.waitUntil(timeoutMillis = 10_000) {\n            runCatching { composeRule.onNodeWithTag("asset_depreciation_submit").assertIsEnabled(); true }.getOrDefault(false)\n        }\n        composeRule.onNodeWithTag("asset_depreciation_submit").performClick()'''
if old in s: s=s.replace(old,new,1)
elif 'استهلاک ماهانه E2E' not in s: raise SystemExit('asset test anchor missing')
old='''        composeRule.onNodeWithTag("receivable_collection_amount").performTextReplacement(receiptAmount.toString())\n        composeRule.onNodeWithTag("receivable_collection_confirm").performClick()'''
new='''        composeRule.onNodeWithTag("receivable_collection_amount").performTextReplacement(receiptAmount.toString())\n        composeRule.waitUntil(timeoutMillis = 20_000) {\n            runCatching { composeRule.onNodeWithTag("receivable_collection_confirm").assertIsEnabled(); true }.getOrDefault(false)\n        }\n        composeRule.onNodeWithTag("receivable_collection_confirm").performClick()'''
if old in s: s=s.replace(old,new,1)
elif 'receivable_collection_confirm").assertIsEnabled()' not in s: raise SystemExit('crm test anchor missing')
test.write_text(s)

checks=[
(ui,'destinationLocationId = order.destinationLocationId'),
(asset,'asset_depreciation_reason'),
(test,'استهلاک ماهانه E2E'),
(test,'receivable_collection_confirm").assertIsEnabled()'),
]
for p,n in checks:
    if n not in p.read_text(): raise SystemExit(f'missing invariant {n}')
print('PHASE8_HOTFIX_04_UI_READINESS_AND_RECEIPT_DESTINATION=PASS')
