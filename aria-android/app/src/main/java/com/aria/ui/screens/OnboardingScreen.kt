package com.aria.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aria.data.repository.SecureStorage

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val step by viewModel.step.collectAsState()

    when (step) {
        is OnboardingStep.Welcome -> WelcomeStep(onNext = { viewModel.nextStep() })
        is OnboardingStep.Permissions -> PermissionsStep(onNext = { viewModel.nextStep() })
        is OnboardingStep.AccountSetup -> AccountSetupStep(viewModel = viewModel, step = step as OnboardingStep.AccountSetup)
        is OnboardingStep.ApiKeys -> ApiKeysStep(viewModel = viewModel)
        is OnboardingStep.Interview -> InterviewStep(viewModel = viewModel)
        is OnboardingStep.Seeding -> SeedingStep(viewModel = viewModel)
        is OnboardingStep.Complete -> CompleteStep(onComplete = onComplete)
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "A",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 96.sp
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Meet ARIA",
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Your personal AI life layer",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Get Started")
        }
    }
}

private data class PermissionItem(
    val label: String,
    val description: String,
    val icon: ImageVector
)

@Composable
private fun PermissionsStep(onNext: () -> Unit) {
    val permissionItems = listOf(
        PermissionItem("Microphone", "So ARIA can hear your voice commands", Icons.Default.Mic),
        PermissionItem("Notifications", "So ARIA can send you reminders and updates", Icons.Default.Notifications),
        PermissionItem("Battery Optimization", "So ARIA can run in the background", Icons.Default.BatteryChargingFull)
    )

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        onNext()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Permissions",
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "ARIA needs a few permissions to work properly",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        permissionItems.forEach { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(text = item.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                launcher.launch(
                    arrayOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Permissions")
        }
        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Skip for now")
        }
    }
}

@Composable
private fun AccountSetupStep(viewModel: OnboardingViewModel, step: OnboardingStep.AccountSetup) {
    var name by remember { mutableStateOf(step.name) }
    var phone by remember { mutableStateOf(step.phone) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "About You", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Help ARIA get to know you",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Your Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Your Phone (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) }
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                viewModel.saveAccountInfo(name, phone)
                viewModel.nextStep()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank()
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun ApiKeysStep(viewModel: OnboardingViewModel) {
    var claudeKey by remember { mutableStateOf("") }
    var mem0Key by remember { mutableStateOf("") }
    var telegramToken by remember { mutableStateOf("") }

    var showClaude by remember { mutableStateOf(false) }
    var showMem0 by remember { mutableStateOf(false) }
    var showTelegram by remember { mutableStateOf(false) }

    var claudeError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "API Keys", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Connect ARIA to your AI services",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = claudeKey,
            onValueChange = {
                claudeKey = it
                claudeError = false
            },
            label = { Text("Claude API Key *") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = claudeError,
            supportingText = if (claudeError) {
                { Text("Claude API Key is required", color = MaterialTheme.colorScheme.error) }
            } else null,
            visualTransformation = if (showClaude) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showClaude = !showClaude }) {
                    Icon(
                        if (showClaude) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null
                    )
                }
            }
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = mem0Key,
            onValueChange = { mem0Key = it },
            label = { Text("Memory API Key (Mem0)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (showMem0) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showMem0 = !showMem0 }) {
                    Icon(
                        if (showMem0) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null
                    )
                }
            }
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = telegramToken,
            onValueChange = { telegramToken = it },
            label = { Text("Telegram Bot Token (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (showTelegram) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showTelegram = !showTelegram }) {
                    Icon(
                        if (showTelegram) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null
                    )
                }
            }
        )
        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                if (claudeKey.isBlank()) {
                    claudeError = true
                } else {
                    viewModel.saveApiKey(SecureStorage.KEY_CLAUDE_API, claudeKey)
                    if (mem0Key.isNotBlank()) {
                        viewModel.saveApiKey(SecureStorage.KEY_MEM0_API, mem0Key)
                    }
                    if (telegramToken.isNotBlank()) {
                        viewModel.saveApiKey(SecureStorage.KEY_TELEGRAM_BOT_TOKEN, telegramToken)
                    }
                    viewModel.nextStep()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InterviewStep(viewModel: OnboardingViewModel) {
    val messages by viewModel.chatMessages.collectAsState()
    val streaming by viewModel.streamingResponse.collectAsState()
    val interviewComplete by viewModel.interviewComplete.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) { viewModel.startInterview() }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, streaming) {
        val targetIndex = messages.size + (if (streaming.isNotBlank()) 1 else 0)
        if (targetIndex > 0) {
            listState.animateScrollToItem(targetIndex - 1)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Getting to know you", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (!interviewComplete) {
                var input by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type your answer...") },
                        maxLines = 4,
                        enabled = !isLoading
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (input.isNotBlank() && !isLoading) {
                                viewModel.sendAnswer(input.trim())
                                input = ""
                            }
                        },
                        enabled = input.isNotBlank() && !isLoading
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                    }
                }
            } else {
                Button(
                    onClick = { viewModel.nextStep() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Continue")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = listState,
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(messages) { (role, content) ->
                val isUser = role == "user"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isUser)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Text(
                            text = content,
                            modifier = Modifier.padding(12.dp),
                            color = if (isUser)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (streaming.isNotBlank()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Text(
                                text = "$streaming\u258b",
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (isLoading && streaming.isBlank() && messages.isEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SeedingStep(viewModel: OnboardingViewModel) {
    LaunchedEffect(Unit) { viewModel.completeOnboarding() }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Building your profile...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompleteStep(onComplete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = Color(0xFF4CAF50)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "You're all set!",
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "ARIA is ready to learn about you.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open ARIA")
        }
    }
}
