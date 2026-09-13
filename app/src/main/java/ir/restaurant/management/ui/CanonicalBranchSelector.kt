package ir.restaurant.management.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.restaurant.management.domain.branch.BranchRecord

/** Canonical operational selector: branchId is identity; branch name is display-only. */
@Composable
internal fun CanonicalBranchSelector(
    branches: List<BranchRecord>,
    selectedBranchId: Long?,
    onBranchSelected: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "شعبه",
    allowAllBranches: Boolean = false,
    enabled: Boolean = true,
    tag: String = "canonical_branch_selector",
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val activeBranches = remember(branches) { branches.filter { it.isActive } }
    val selected = selectedBranchId?.let { id -> activeBranches.firstOrNull { it.id == id } }
    val display = when {
        selected != null -> selected.name
        allowAllBranches && selectedBranchId == null -> "همه شعب"
        activeBranches.isEmpty() -> "شعبه‌ای تعریف نشده"
        else -> "انتخاب شعبه"
    }

    Box(modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag(tag),
        ) {
            Text("$label: $display", modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = "انتخاب شعبه")
        }
    }

    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("${tag}_dialog"),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
            ) {
                Column(
                    Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    if (activeBranches.isEmpty() && !allowAllBranches) {
                        Text(
                            "هنوز شعبه‌ای تعریف نشده است.",
                            modifier = Modifier.fillMaxWidth().testTag("${tag}_empty"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .testTag("${tag}_options"),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (allowAllBranches) {
                                item(key = "all-branches") {
                                    OutlinedButton(
                                        onClick = {
                                            expanded = false
                                            onBranchSelected(null)
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("${tag}_all"),
                                    ) { Text("همه شعب") }
                                }
                            }
                            items(activeBranches, key = { it.id }) { branch ->
                                val isSelected = selectedBranchId == branch.id
                                if (isSelected) {
                                    Button(
                                        onClick = { expanded = false },
                                        modifier = Modifier.fillMaxWidth().testTag("${tag}_branch_${branch.id}"),
                                    ) { Text(branch.name) }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            expanded = false
                                            onBranchSelected(branch.id)
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("${tag}_branch_${branch.id}"),
                                    ) { Text(branch.name) }
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { expanded = false },
                        modifier = Modifier.fillMaxWidth().testTag("${tag}_close"),
                    ) { Text("بستن") }
                }
            }
        }
    }
}
