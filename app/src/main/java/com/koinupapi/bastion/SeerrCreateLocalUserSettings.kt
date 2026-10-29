package com.koinupapi.bastion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SeerrCreateLocalUserSettings(instance: ServiceConfig) {
    val scope = rememberCoroutineScope()
    var email by remember(instance.id) { mutableStateOf("") }
    var username by remember(instance.id) { mutableStateOf("") }
    var password by remember(instance.id) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var succeeded by remember { mutableStateOf(false) }

    Card(shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Create local Seerr user", style = MaterialTheme.typography.titleLarge)
            Text("Account will be created on ${instance.name}. It will not expire automatically.",
                style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(email, { email = it; message = ""; succeeded = false },
                label = { Text("Email address") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(), enabled = !busy)
            OutlinedTextField(username, { username = it; message = ""; succeeded = false },
                label = { Text("Username") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), enabled = !busy)
            OutlinedTextField(password, { password = it; message = ""; succeeded = false },
                label = { Text("Initial password (8+ characters)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
            Text("Permissions: Request media (no administrator privileges).",
                style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    busy = true
                    message = ""
                    scope.launch {
                        SeerrClient.createLocalUser(instance, email, username, password)
                            .onSuccess {
                                message = "User created successfully (ID $it)."
                                succeeded = true
                                email = ""
                                username = ""
                                password = ""
                            }
                            .onFailure {
                                message = it.message ?: "Could not create user"
                                password = ""
                            }
                        busy = false
                    }
                },
                enabled = !busy && email.contains("@") && username.isNotBlank() &&
                    password.length >= 8 && !succeeded,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Create user")
            }
            if (message.isNotBlank()) Text(message,
                color = if (succeeded) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error)
        }
    }
}
