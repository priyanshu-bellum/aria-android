package com.aria.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aria.data.repository.SecureStorage

@Composable
fun ApiKeyField(label: String, key: String, viewModel: SettingsViewModel) {
    val isPlainText = key == SecureStorage.KEY_USER_NAME
            || key == SecureStorage.KEY_USER_PHONE
            || key == SecureStorage.KEY_LIVEKIT_URL
    var value by remember(key) { mutableStateOf(viewModel.getKey(key)) }
    var obscure by remember { mutableStateOf(!isPlainText) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text(label) },
            modifier = Modifier.weight(1f),
            visualTransformation = if (obscure) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = {
                if (!isPlainText) {
                    IconButton(onClick = { obscure = !obscure }) {
                        Icon(
                            imageVector = if (obscure) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (obscure) "Show" else "Hide"
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (isPlainText) KeyboardType.Text else KeyboardType.Password)
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = { viewModel.saveKey(key, value) }) {
            Text("Save")
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val exportData by viewModel.exportData.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showWipeConfirm by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(exportData) {
        exportData?.let { showExportDialog = true }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                SettingsSection(title = "Profile") {
                    ApiKeyField("Your Name", SecureStorage.KEY_USER_NAME, viewModel)
                    ApiKeyField("Your Phone", SecureStorage.KEY_USER_PHONE, viewModel)
                }
            }

            item {
                SettingsSection(title = "AI Services") {
                    ApiKeyField("Claude API Key", SecureStorage.KEY_CLAUDE_API, viewModel)
                    ApiKeyField("Memory API Key", SecureStorage.KEY_MEM0_API, viewModel)
                    ApiKeyField("Deepgram API Key", SecureStorage.KEY_DEEPGRAM_API, viewModel)
                    ApiKeyField("Composio API Key", SecureStorage.KEY_COMPOSIO_API, viewModel)
                }
            }

            item {
                SettingsSection(title = "Messaging & Calls") {
                    ApiKeyField("Telegram Bot Token", SecureStorage.KEY_TELEGRAM_BOT_TOKEN, viewModel)
                    ApiKeyField("LiveKit URL", SecureStorage.KEY_LIVEKIT_URL, viewModel)
                    ApiKeyField("LiveKit API Key", SecureStorage.KEY_LIVEKIT_API_KEY, viewModel)
                    ApiKeyField("LiveKit API Secret", SecureStorage.KEY_LIVEKIT_API_SECRET, viewModel)
                }
            }

            item {
                SettingsSection(title = "Data") {
                    Button(
                        onClick = { viewModel.exportProfile() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export Profile")
                    }
                    Button(
                        onClick = { showWipeConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                    ) {
                        Text("Wipe All Data")
                    }
                }
            }
        }
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text("Wipe all data?") },
            text = { Text("This deletes your profile, all API keys, and cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.wipeAll()
                    showWipeConfirm = false
                }) {
                    Text("Wipe", color = Color(0xFFEF5350))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showExportDialog && exportData != null) {
        AlertDialog(
            onDismissRequest = {
                showExportDialog = false
                viewModel.clearExport()
            },
            title = { Text("Profile Export") },
            text = {
                SelectionContainer {
                    Text(
                        text = exportData!!,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    viewModel.clearExport()
                }) {
                    Text("Close")
                }
            }
        )
    }
}
