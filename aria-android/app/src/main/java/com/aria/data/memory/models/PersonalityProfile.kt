package com.aria.data.memory.models

data class PersonalityProfile(
    val userId: String = "",
    val layers: ProfileLayers = ProfileLayers(),
    val confidenceScores: ConfidenceScores = ConfidenceScores(),
    val lastUpdated: Map<String, String> = emptyMap()
)

data class ProfileLayers(
    val coreIdentity: CoreIdentity = CoreIdentity(),
    val behavioralPatterns: BehavioralPatterns = BehavioralPatterns(),
    val communicationStyle: CommunicationStyle = CommunicationStyle(),
    val keyRelationships: List<KeyRelationship> = emptyList(),
    val currentContext: CurrentContext = CurrentContext()
)

data class CoreIdentity(
    val values: List<String> = emptyList(),
    val longTermGoals: List<String> = emptyList(),
    val fears: List<String> = emptyList(),
    val worldview: String = "",
    val identityMarkers: List<String> = emptyList()
)

data class BehavioralPatterns(
    val decisionStyle: String = "",
    val stressResponse: String = "",
    val workStyle: String = "",
    val riskTolerance: String = "",
    val energyPattern: String = ""
)

data class CommunicationStyle(
    val toneProfessional: String = "",
    val tonePersonal: String = "",
    val vocabularyMarkers: List<String> = emptyList(),
    val sentenceStructure: String = "",
    val humorStyle: String = "",
    val formalityDefault: String = ""
)

data class KeyRelationship(
    val name: String = "",
    val role: String = "",
    val relationshipQuality: String = "",
    val communicationNotes: String = "",
    val contextHistory: List<String> = emptyList()
)

data class CurrentContext(
    val activeGoals: List<String> = emptyList(),
    val moodTrend: String = "",
    val currentStressors: List<String> = emptyList(),
    val recentWins: List<String> = emptyList(),
    val pendingDecisions: List<String> = emptyList()
)

data class ConfidenceScores(
    val coreIdentity: Float = 0f,
    val behavioralPatterns: Float = 0f,
    val communicationStyle: Float = 0f,
    val keyRelationships: Float = 0f,
    val currentContext: Float = 0f
)
