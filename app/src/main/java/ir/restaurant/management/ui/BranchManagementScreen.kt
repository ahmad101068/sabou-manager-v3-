package ir.restaurant.management.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.restaurant.management.domain.branch.BranchRecord

@Composable
internal fun BranchManagementScreen(
    state: BranchManagementUiState,
    onCreate: (String, String?, () -> Unit) -> Unit,
    onRename: (Long, String, () -> Unit) -> Unit,
    onSetActive: (Long, Boolean) -> Unit,
    onNavigateTopLevel: (AppScreen) -> Unit,
    onBack: () -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<BranchRecord?>(null) }

    Scaffold(
        topBar = { ProfessionalTopBar("مدیریت شعب", "هویت شعبه بر پایه شناسه مرجع یکتا", onBack) },
        bottomBar = { ErpBottomNavigation(AppScreen.BRANCHES, onNavigateTopLevel) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).testTag("branch_management"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (state.branches.isEmpty()) "هنوز شعبه‌ای تعریف نشده است. اولین شعبه را با نام واقعی ایجاد کنید." else "شعب ثبت‌شده",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = { showCreate = true }, enabled = !state.busy, modifier = Modifier.testTag("branch_create")) {
                        Text(if (state.branches.isEmpty()) "ایجاد اولین شعبه" else "ایجاد شعبه")
                    }
                    state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                }
            }
            items(state.branches, key = { it.id }) { branch ->
                Card(Modifier.fillMaxWidth().testTag("branch_${branch.id}")) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(branch.name, fontWeight = FontWeight.Bold)
                                Text("شناسه: ${branch.id}${branch.code?.let { " · کد: $it" }.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(if (branch.isActive) "فعال" else "غیرفعال")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { renameTarget = branch }, enabled = !state.busy) { Text("تغییر نام") }
                            OutlinedButton(onClick = { onSetActive(branch.id, !branch.isActive) }, enabled = !state.busy) {
                                Text(if (branch.isActive) "غیرفعال‌سازی" else "فعال‌سازی")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        BranchEditDialog(
            title = if (state.branches.isEmpty()) "ایجاد اولین شعبه" else "ایجاد شعبه",
            initialName = "",
            allowCode = true,
            busy = state.busy,
            onDismiss = { showCreate = false },
            onConfirm = { name, code -> onCreate(name, code) { showCreate = false } },
        )
    }
    renameTarget?.let { branch ->
        BranchEditDialog(
            title = "تغییر نام شعبه",
            initialName = branch.name,
            allowCode = false,
            busy = state.busy,
            onDismiss = { renameTarget = null },
            onConfirm = { name, _ -> onRename(branch.id, name) { renameTarget = null } },
        )
    }
}

@Composable
private fun BranchEditDialog(
    title: String,
    initialName: String,
    allowCode: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var code by remember { mutableStateOf("") }
    val validName = name.trim().length in 2..120
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(120) }, label = { Text("نام شعبه") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("branch_name"))
                if (allowCode) {
                    OutlinedTextField(code, { code = it.take(32) }, label = { Text("کد اختیاری") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("branch_code"))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name, code.trim().takeIf { allowCode && it.isNotEmpty() }) }, enabled = validName && !busy) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("انصراف") } },
    )
}
