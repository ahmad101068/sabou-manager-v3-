package ir.restaurant.management.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun SensitivePinField(
    pin: String,
    onPinChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = pin,
        onValueChange = { value -> onPinChange(value.filter(Char::isDigit).take(12)) },
        label = { Text("رمز ورود کاربر جاری") },
        supportingText = { Text("برای این عملیات حساس، رمز ۶ تا ۱۲ رقمی را دوباره وارد کنید.") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
internal fun SensitiveActionConfirmationDialog(
    title: String,
    description: String,
    confirmLabel: String,
    busy: Boolean,
    message: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var pin by remember(title) { mutableStateOf("") }
    var submitted by remember(title) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(description)
                if (submitted) message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                SensitivePinField(pin, { pin = it })
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && pin.length in 6..12,
                onClick = {
                    val submittedPin = pin
                    pin = ""
                    submitted = true
                    onConfirm(submittedPin)
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
