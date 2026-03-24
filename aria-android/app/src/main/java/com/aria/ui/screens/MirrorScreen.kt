package com.aria.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aria.data.memory.models.*

private data class MirrorEditTarget(val layer: String, val field: String, val currentValue: String)

private data class MirrorLayerData(
    val name: String,
    val confidence: Float,
    val previewText: String,
    val fields: List<Pair<String, String>>
)

@Composable
fun ConfidenceBadge(score: Float) {
    val (text, color) = when {
        score < 0.4f -> "Still learning" to Color(0xFFFFC107)
        score < 0.7f -> "Confident" to Color(0xFF2196F3)
        else -> "Strong" to Color(0xFF4CAF50)
    }
    Surface(color = color.copy(alpha = 0.2f), shape = RoundedCornerShape(50)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MirrorScreen(viewModel: MirrorViewModel = hiltViewModel()) {
    val profile by viewModel.profile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        if (!errorMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(errorMessage!!)
            viewModel.clearError()
        }
    }

    var editTarget by remember { mutableStateOf<MirrorEditTarget?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Your Mirror",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.loadProfile() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (isLoading && profile == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val layers = buildMirrorLayers(profile)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(Modifier.height(8.dp)) }

                items(layers, key = { it.name }) { layer ->
                    MirrorLayerCard(
                        layer = layer,
                        onLongPressField = { field, value ->
                            editTarget = MirrorEditTarget(layer.name, field, value)
                        }
                    )
                }

                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
        }
    }

    val currentEdit = editTarget
    if (currentEdit != null) {
        MirrorEditBottomSheet(
            target = currentEdit,
            onDismiss = { editTarget = null },
            onSave = { newValue ->
                viewModel.correctField(currentEdit.layer, currentEdit.field, newValue)
                editTarget = null
            }
        )
    }
}

private fun buildMirrorLayers(profile: PersonalityProfile?): List<MirrorLayerData> {
    val scores = profile?.confidenceScores ?: ConfidenceScores()
    val layers = profile?.layers ?: ProfileLayers()
    val ci = layers.coreIdentity
    val bp = layers.behavioralPatterns
    val cs = layers.communicationStyle
    val kr = layers.keyRelationships
    val cc = layers.currentContext

    return listOf(
        MirrorLayerData(
            name = "Core Identity",
            confidence = scores.coreIdentity,
            previewText = ci.worldview.take(80).ifBlank { ci.values.firstOrNull() ?: "No data yet" },
            fields = listOf(
                "Values" to ci.values.joinToString(", ").ifBlank { "\u2014" },
                "Long-Term Goals" to ci.longTermGoals.joinToString(", ").ifBlank { "\u2014" },
                "Fears" to ci.fears.joinToString(", ").ifBlank { "\u2014" },
                "Worldview" to ci.worldview.ifBlank { "\u2014" },
                "Identity Markers" to ci.identityMarkers.joinToString(", ").ifBlank { "\u2014" }
            )
        ),
        MirrorLayerData(
            name = "Behavioral Patterns",
            confidence = scores.behavioralPatterns,
            previewText = bp.decisionStyle.take(80).ifBlank { "No data yet" },
            fields = listOf(
                "Decision Style" to bp.decisionStyle.ifBlank { "\u2014" },
                "Stress Response" to bp.stressResponse.ifBlank { "\u2014" },
                "Work Style" to bp.workStyle.ifBlank { "\u2014" },
                "Risk Tolerance" to bp.riskTolerance.ifBlank { "\u2014" },
                "Energy Pattern" to bp.energyPattern.ifBlank { "\u2014" }
            )
        ),
        MirrorLayerData(
            name = "Communication Style",
            confidence = scores.communicationStyle,
            previewText = cs.toneProfessional.take(80).ifBlank { "No data yet" },
            fields = listOf(
                "Professional Tone" to cs.toneProfessional.ifBlank { "\u2014" },
                "Personal Tone" to cs.tonePersonal.ifBlank { "\u2014" },
                "Vocabulary Markers" to cs.vocabularyMarkers.joinToString(", ").ifBlank { "\u2014" },
                "Sentence Structure" to cs.sentenceStructure.ifBlank { "\u2014" },
                "Humor Style" to cs.humorStyle.ifBlank { "\u2014" },
                "Formality Default" to cs.formalityDefault.ifBlank { "\u2014" }
            )
        ),
        MirrorLayerData(
            name = "Key Relationships",
            confidence = scores.keyRelationships,
            previewText = kr.firstOrNull()?.let { "${it.name} (${it.role})" } ?: "No data yet",
            fields = if (kr.isEmpty()) {
                listOf("Relationships" to "None recorded yet")
            } else {
                kr.map { rel -> "${rel.name} (${rel.role})" to rel.relationshipQuality.ifBlank { "\u2014" } }
            }
        ),
        MirrorLayerData(
            name = "Current Context",
            confidence = scores.currentContext,
            previewText = cc.moodTrend.take(80).ifBlank { cc.activeGoals.firstOrNull() ?: "No data yet" },
            fields = listOf(
                "Active Goals" to cc.activeGoals.joinToString(", ").ifBlank { "\u2014" },
                "Mood Trend" to cc.moodTrend.ifBlank { "\u2014" },
                "Current Stressors" to cc.currentStressors.joinToString(", ").ifBlank { "\u2014" },
                "Recent Wins" to cc.recentWins.joinToString(", ").ifBlank { "\u2014" },
                "Pending Decisions" to cc.pendingDecisions.joinToString(", ").ifBlank { "\u2014" }
            )
        )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MirrorLayerCard(
    layer: MirrorLayerData,
    onLongPressField: (field: String, value: String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "arrow"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = layer.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                ConfidenceBadge(score = layer.confidence)
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.rotate(arrowRotation)
                    )
                }
            }

            if (!expanded) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = layer.previewText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                    layer.fields.forEach { (fieldLabel, fieldValue) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {},
                                    onLongClick = { onLongPressField(fieldLabel, fieldValue) }
                                )
                        ) {
                            Text(
                                text = fieldLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = fieldValue,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MirrorEditBottomSheet(
    target: MirrorEditTarget,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var textValue by remember(target) {
        mutableStateOf(if (target.currentValue == "\u2014") "" else target.currentValue)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Edit: ${target.field}",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(target.field) },
                minLines = 2,
                maxLines = 6
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { if (textValue.isNotBlank()) onSave(textValue.trim()) },
                    modifier = Modifier.weight(1f),
                    enabled = textValue.isNotBlank()
                ) {
                    Text("Save")
                }
            }
        }
    }
}
