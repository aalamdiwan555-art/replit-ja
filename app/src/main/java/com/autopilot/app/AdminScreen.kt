package com.autopilot.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    user: User,
    onBack: () -> Unit,
    onGrantDays: (Int) -> Unit,
    onGrantLifetime: () -> Unit,
    onExtend: () -> Unit,
    onReject: () -> Unit,
    onToggleAdFree: (Boolean) -> Unit,
    onSetAdFreeForUid: (String, Boolean) -> Boolean,
) {
    var customDays by rememberSaveable { mutableStateOf("7") }
    var uidInput by rememberSaveable { mutableStateOf(user.uid) }
    var uidMessage by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin controls") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("User Management")
            Text("UID: ${user.uid}")
            Text("This build stores one local user per device. A shared server is required to list users across devices.")
            OutlinedTextField(
                value = uidInput,
                onValueChange = { uidInput = it.uppercase().take(32) },
                label = { Text("User UID") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    uidMessage = if (onSetAdFreeForUid(uidInput, true)) {
                        "AD-FREE MODE enabled for $uidInput"
                    } else {
                        "UID not found in this local registry."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Make UID AD-FREE")
            }
            OutlinedButton(
                onClick = {
                    uidMessage = if (onSetAdFreeForUid(uidInput, false)) {
                        "AD-FREE MODE disabled for $uidInput"
                    } else {
                        "UID not found in this local registry."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Remove AD-FREE from UID")
            }
            uidMessage?.let { Text(it) }
            Text("Manage local approval for ${user.email}")
            Text("Current status: ${user.status}")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("AD-FREE MODE")
                    Text(if (user.adFreeOverride) "ON — ads are disabled" else "OFF — standard ad policy")
                }
                Switch(
                    checked = user.adFreeOverride,
                    onCheckedChange = onToggleAdFree,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onGrantDays(1) }) { Text("Grant 1 Day") }
                Button(onClick = { onGrantDays(2) }) { Text("Grant 2 Days") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onGrantDays(3) }) { Text("Grant 3 Days") }
                Button(onClick = onGrantLifetime) { Text("Grant Lifetime") }
            }
            OutlinedTextField(
                value = customDays,
                onValueChange = { customDays = it.filter(Char::isDigit).take(4) },
                label = { Text("Custom days") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { customDays.toIntOrNull()?.takeIf { it > 0 }?.let(onGrantDays) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Grant Custom")
            }
            Button(onClick = onExtend, modifier = Modifier.fillMaxWidth()) {
                Text("Extend User")
            }
            OutlinedButton(onClick = onReject, modifier = Modifier.fillMaxWidth()) {
                Text("Reject User")
            }
        }
    }
}