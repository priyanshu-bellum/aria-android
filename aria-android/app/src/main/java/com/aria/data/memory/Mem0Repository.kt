package com.aria.data.memory

import com.aria.data.memory.models.BehavioralPatterns
import com.aria.data.memory.models.CommunicationStyle
import com.aria.data.memory.models.ConfidenceScores
import com.aria.data.memory.models.CoreIdentity
import com.aria.data.memory.models.CurrentContext
import com.aria.data.memory.models.KeyRelationship
import com.aria.data.memory.models.MemoryDelta
import com.aria.data.memory.models.PersonalityProfile
import com.aria.data.memory.models.ProfileLayers
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Mem0Repository(
    private val apiClient: Mem0ApiClient,
    private val moshi: Moshi
) {
    // Moshi adapter for the top-level Mem0 API response (a generic JSON object).
    private val mapAdapter by lazy {
        val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        moshi.adapter<Map<String, Any>>(type)
    }

    // Adapter for List<Any> (used when deserialising nested arrays).
    private val listAnyAdapter by lazy {
        val type = Types.newParameterizedType(List::class.java, Any::class.java)
        moshi.adapter<List<Any>>(type)
    }

    /**
     * Fetch relevant memories before every Claude call.
     */
    suspend fun getRelevantMemories(query: String): String =
        withContext(Dispatchers.IO) {
            apiClient.searchMemories(query, limit = 10)
        }

    /**
     * After onboarding interview — seed the full profile.
     */
    suspend fun seedProfile(profileJson: String) =
        withContext(Dispatchers.IO) {
            apiClient.addMemory(
                text = "User personality profile: $profileJson",
                metadata = mapOf("type" to "personality_profile", "source" to "onboarding")
            )
        }

    /**
     * After nightly synthesis — apply individual deltas.
     */
    suspend fun applyDeltas(deltas: List<MemoryDelta>) =
        withContext(Dispatchers.IO) {
            for (delta in deltas) {
                if (delta.confidenceDelta > 0.05f) {
                    apiClient.addMemory(
                        text = "Profile update — ${delta.layer}.${delta.field}: ${delta.newValue}. Evidence: ${delta.evidence}",
                        metadata = mapOf(
                            "type" to "profile_delta",
                            "layer" to delta.layer,
                            "field" to delta.field,
                            "confidence_delta" to delta.confidenceDelta
                        )
                    )
                }
            }
        }

    /**
     * Mirror screen — load full profile for display.
     */
    suspend fun getFullProfile(): PersonalityProfile =
        withContext(Dispatchers.IO) {
            val raw = apiClient.getAllMemories()
            parseProfileFromMemories(raw)
        }

    /**
     * User corrects a Mirror card field.
     */
    suspend fun correctField(layer: String, field: String, value: String) =
        withContext(Dispatchers.IO) {
            apiClient.addMemory(
                text = "User correction — $layer.$field: $value (confidence: 1.0)",
                metadata = mapOf(
                    "type" to "user_correction",
                    "layer" to layer,
                    "field" to field,
                    "confidence" to 1.0
                )
            )
        }

    /**
     * User requests data export.
     */
    suspend fun exportProfile(): String =
        withContext(Dispatchers.IO) {
            apiClient.getAllMemories()
        }

    /**
     * User requests profile wipe.
     */
    suspend fun wipeProfile() =
        withContext(Dispatchers.IO) {
            apiClient.deleteAllMemories()
        }

    // -------------------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------------------

    /**
     * Parse the raw JSON string returned by Mem0's GET /memories endpoint into
     * a structured [PersonalityProfile].
     *
     * The Mem0 API response looks like:
     * ```json
     * {
     *   "results": [
     *     {
     *       "id": "...",
     *       "memory": "<text stored when addMemory() was called>",
     *       "metadata": { "type": "profile_delta", "layer": "core_identity", "field": "values", ... }
     *     },
     *     ...
     *   ]
     * }
     * ```
     *
     * Two memory shapes are handled:
     *  1. **Profile delta / user correction** — metadata has "layer" + "field" keys written by
     *     [applyDeltas] and [correctField]. The most-recently-written entry for each layer+field
     *     pair wins (later entries in the list override earlier ones).
     *  2. **Full personality profile blob** — metadata has type == "personality_profile", stored
     *     by [seedProfile]. The `memory` text starts with "User personality profile: " followed
     *     by a JSON object whose top-level keys mirror the layer names. This is used as a
     *     baseline that individual deltas can then override.
     *
     * For list fields (values, longTermGoals, fears, etc.) the stored text may be:
     *  - A JSON array string: `["a","b","c"]`
     *  - A comma-separated string: `"a, b, c"`
     *  - A newline-separated string: `"a\nb\nc"`
     */
    private fun parseProfileFromMemories(rawJson: String): PersonalityProfile {
        if (rawJson.isBlank()) return PersonalityProfile()

        // Decode the top-level response object.
        val root: Map<String, Any> = try {
            mapAdapter.fromJson(rawJson) ?: return PersonalityProfile()
        } catch (_: Exception) {
            return PersonalityProfile()
        }

        // The Mem0 API wraps results in a "results" key; fall back to "memories" or treat the
        // root itself as a list container (search endpoint returns results the same way).
        @Suppress("UNCHECKED_CAST")
        val resultsList: List<Any> = when {
            root.containsKey("results") -> root["results"] as? List<Any> ?: emptyList()
            root.containsKey("memories") -> root["memories"] as? List<Any> ?: emptyList()
            else -> emptyList()
        }

        if (resultsList.isEmpty()) return PersonalityProfile()

        // Mutable accumulators — fields are keyed by "layer:field".
        // We store only the last value seen for each key so that more-recent deltas win.
        val fieldValues = mutableMapOf<String, String>()

        // Baseline profile from the full-blob seed (may be absent).
        var baseProfile: PersonalityProfile? = null

        for (item in resultsList) {
            @Suppress("UNCHECKED_CAST")
            val entry = item as? Map<String, Any> ?: continue

            val memoryText = (entry["memory"] as? String)?.trim() ?: continue

            @Suppress("UNCHECKED_CAST")
            val metadata = entry["metadata"] as? Map<String, Any> ?: emptyMap()
            val type = metadata["type"] as? String ?: ""

            when {
                // --- Full profile blob written by seedProfile() ---
                type == "personality_profile" -> {
                    val prefix = "User personality profile: "
                    val jsonPart = if (memoryText.startsWith(prefix)) {
                        memoryText.removePrefix(prefix)
                    } else {
                        memoryText
                    }
                    baseProfile = tryParseProfileJson(jsonPart)
                }

                // --- Individual delta / correction written by applyDeltas() / correctField() ---
                type == "profile_delta" || type == "user_correction" -> {
                    val layer = (metadata["layer"] as? String)?.trim() ?: continue
                    val field = (metadata["field"] as? String)?.trim() ?: continue
                    // Extract the actual value from the text.
                    // Format: "Profile update — <layer>.<field>: <value>. Evidence: ..."
                    //      or "User correction — <layer>.<field>: <value> (confidence: 1.0)"
                    val value = extractValueFromDeltaText(memoryText, layer, field)
                    if (value.isNotBlank()) {
                        fieldValues["$layer:$field"] = value
                    }
                }

                // --- Fallback: try to detect layer/field from metadata even without a type ---
                else -> {
                    val layer = (metadata["layer"] as? String)?.trim()
                    val field = (metadata["field"] as? String)?.trim()
                    if (!layer.isNullOrBlank() && !field.isNullOrBlank()) {
                        fieldValues["$layer:$field"] = memoryText
                    }
                }
            }
        }

        // Merge: start from the baseline (if any) then apply delta overrides.
        return buildProfile(baseProfile, fieldValues)
    }

    /**
     * Attempt to parse a JSON blob (written by [seedProfile]) into a [PersonalityProfile].
     * The blob is expected to be a JSON object whose structure mirrors [ProfileLayers] but
     * may use either camelCase or snake_case key names produced by whatever serialiser the
     * caller used. We use a lenient key-lookup approach rather than strict Moshi deserialization
     * so we don't need to register a full adapter chain.
     */
    private fun tryParseProfileJson(json: String): PersonalityProfile? {
        val root: Map<String, Any> = try {
            mapAdapter.fromJson(json) ?: return null
        } catch (_: Exception) {
            return null
        }

        fun anyMap(vararg keys: String): Map<String, Any>? {
            for (k in keys) {
                @Suppress("UNCHECKED_CAST")
                val v = root[k] as? Map<String, Any>
                if (v != null) return v
            }
            return null
        }

        val ciMap = anyMap("coreIdentity", "core_identity")
        val bpMap = anyMap("behavioralPatterns", "behavioral_patterns")
        val csMap = anyMap("communicationStyle", "communication_style")
        val ccMap = anyMap("currentContext", "current_context")

        fun Map<String, Any>?.str(vararg keys: String): String {
            if (this == null) return ""
            for (k in keys) {
                val v = this[k] as? String
                if (!v.isNullOrBlank()) return v
            }
            return ""
        }

        fun Map<String, Any>?.list(vararg keys: String): List<String> {
            if (this == null) return emptyList()
            for (k in keys) {
                val v = this[k]
                if (v != null) return parseListValue(v.toString())
            }
            return emptyList()
        }

        val coreIdentity = CoreIdentity(
            values = ciMap.list("values"),
            longTermGoals = ciMap.list("longTermGoals", "long_term_goals"),
            fears = ciMap.list("fears"),
            worldview = ciMap.str("worldview"),
            identityMarkers = ciMap.list("identityMarkers", "identity_markers")
        )

        val behavioralPatterns = BehavioralPatterns(
            decisionStyle = bpMap.str("decisionStyle", "decision_style"),
            stressResponse = bpMap.str("stressResponse", "stress_response"),
            workStyle = bpMap.str("workStyle", "work_style"),
            riskTolerance = bpMap.str("riskTolerance", "risk_tolerance"),
            energyPattern = bpMap.str("energyPattern", "energy_pattern")
        )

        val communicationStyle = CommunicationStyle(
            toneProfessional = csMap.str("toneProfessional", "tone_professional"),
            tonePersonal = csMap.str("tonePersonal", "tone_personal"),
            vocabularyMarkers = csMap.list("vocabularyMarkers", "vocabulary_markers"),
            sentenceStructure = csMap.str("sentenceStructure", "sentence_structure"),
            humorStyle = csMap.str("humorStyle", "humor_style"),
            formalityDefault = csMap.str("formalityDefault", "formality_default")
        )

        val currentContext = CurrentContext(
            activeGoals = ccMap.list("activeGoals", "active_goals"),
            moodTrend = ccMap.str("moodTrend", "mood_trend"),
            currentStressors = ccMap.list("currentStressors", "current_stressors"),
            recentWins = ccMap.list("recentWins", "recent_wins"),
            pendingDecisions = ccMap.list("pendingDecisions", "pending_decisions")
        )

        // Key relationships is a list-of-objects at the top level or nested.
        @Suppress("UNCHECKED_CAST")
        val krList = (root["keyRelationships"] ?: root["key_relationships"]) as? List<Any>
        val keyRelationships: List<KeyRelationship> = krList?.mapNotNull { item ->
            @Suppress("UNCHECKED_CAST")
            val m = item as? Map<String, Any> ?: return@mapNotNull null
            fun str(vararg keys: String): String {
                for (k in keys) { val v = m[k] as? String; if (!v.isNullOrBlank()) return v }
                return ""
            }
            KeyRelationship(
                name = str("name"),
                role = str("role"),
                relationshipQuality = str("relationshipQuality", "relationship_quality"),
                communicationNotes = str("communicationNotes", "communication_notes"),
                contextHistory = m.list("contextHistory", "context_history")
            )
        } ?: emptyList()

        return PersonalityProfile(
            layers = ProfileLayers(
                coreIdentity = coreIdentity,
                behavioralPatterns = behavioralPatterns,
                communicationStyle = communicationStyle,
                keyRelationships = keyRelationships,
                currentContext = currentContext
            )
        )
    }

    /**
     * Extract the payload value from a delta/correction memory text.
     *
     * Stored formats:
     *  - "Profile update — core_identity.values: courage, growth. Evidence: ..."
     *  - "User correction — core_identity.values: courage, growth (confidence: 1.0)"
     */
    private fun extractValueFromDeltaText(text: String, layer: String, field: String): String {
        // Attempt structured extraction: look for "layer.field: <value>" pattern.
        val prefix = "$layer.$field:"
        val idx = text.indexOf(prefix, ignoreCase = true)
        if (idx >= 0) {
            var value = text.substring(idx + prefix.length).trim()
            // Strip trailing ". Evidence: ..." suffix (profile_delta format).
            val evidenceIdx = value.indexOf(". Evidence:", ignoreCase = true)
            if (evidenceIdx >= 0) value = value.substring(0, evidenceIdx).trim()
            // Strip trailing " (confidence: ...)" suffix (user_correction format).
            val confIdx = value.indexOf(" (confidence:", ignoreCase = true)
            if (confIdx >= 0) value = value.substring(0, confIdx).trim()
            return value
        }
        // Fallback: just return the whole text — it's better than nothing.
        return text
    }

    /**
     * Build the final [PersonalityProfile] from an optional baseline profile (parsed from the
     * full-blob seed) and a map of individual field overrides keyed as "layer:field".
     *
     * Confidence scores are calculated per layer based on how many fields carry non-empty data:
     *  - 0 fields populated → 0.0
     *  - 1–(n/2) fields      → 0.5 (partial)
     *  - >(n/2) fields        → 0.8 (most fields present)
     */
    private fun buildProfile(
        base: PersonalityProfile?,
        fieldValues: Map<String, String>
    ): PersonalityProfile {
        // Start from the base if available, otherwise start empty.
        val baseLayers = base?.layers ?: ProfileLayers()

        // Helper to fetch the most recent override for a given layer+field pair (or null if
        // no override exists, in which case the baseline value will be kept as-is).
        fun override(layer: String, field: String): String? = fieldValues["$layer:$field"]

        // ---- Core Identity ----
        val ciBase = baseLayers.coreIdentity
        val coreIdentity = CoreIdentity(
            values = override("core_identity", "values")
                ?.let { parseListValue(it) } ?: ciBase.values,
            longTermGoals = override("core_identity", "long_term_goals")
                ?.let { parseListValue(it) } ?: ciBase.longTermGoals,
            fears = override("core_identity", "fears")
                ?.let { parseListValue(it) } ?: ciBase.fears,
            worldview = override("core_identity", "worldview") ?: ciBase.worldview,
            identityMarkers = override("core_identity", "identity_markers")
                ?.let { parseListValue(it) } ?: ciBase.identityMarkers
        )

        // ---- Behavioral Patterns ----
        val bpBase = baseLayers.behavioralPatterns
        val behavioralPatterns = BehavioralPatterns(
            decisionStyle = override("behavioral_patterns", "decision_style") ?: bpBase.decisionStyle,
            stressResponse = override("behavioral_patterns", "stress_response") ?: bpBase.stressResponse,
            workStyle = override("behavioral_patterns", "work_style") ?: bpBase.workStyle,
            riskTolerance = override("behavioral_patterns", "risk_tolerance") ?: bpBase.riskTolerance,
            energyPattern = override("behavioral_patterns", "energy_pattern") ?: bpBase.energyPattern
        )

        // ---- Communication Style ----
        val csBase = baseLayers.communicationStyle
        val communicationStyle = CommunicationStyle(
            toneProfessional = override("communication_style", "tone_professional") ?: csBase.toneProfessional,
            tonePersonal = override("communication_style", "tone_personal") ?: csBase.tonePersonal,
            vocabularyMarkers = override("communication_style", "vocabulary_markers")
                ?.let { parseListValue(it) } ?: csBase.vocabularyMarkers,
            sentenceStructure = override("communication_style", "sentence_structure") ?: csBase.sentenceStructure,
            humorStyle = override("communication_style", "humor_style") ?: csBase.humorStyle,
            formalityDefault = override("communication_style", "formality_default") ?: csBase.formalityDefault
        )

        // ---- Key Relationships ---- (deltas don't currently update individual relationships;
        // we keep the baseline list and do not attempt partial merge here).
        val keyRelationships = baseLayers.keyRelationships

        // ---- Current Context ----
        val ccBase = baseLayers.currentContext
        val currentContext = CurrentContext(
            activeGoals = override("current_context", "active_goals")
                ?.let { parseListValue(it) } ?: ccBase.activeGoals,
            moodTrend = override("current_context", "mood_trend") ?: ccBase.moodTrend,
            currentStressors = override("current_context", "current_stressors")
                ?.let { parseListValue(it) } ?: ccBase.currentStressors,
            recentWins = override("current_context", "recent_wins")
                ?.let { parseListValue(it) } ?: ccBase.recentWins,
            pendingDecisions = override("current_context", "pending_decisions")
                ?.let { parseListValue(it) } ?: ccBase.pendingDecisions
        )

        // ---- Confidence Scores ----
        // For each layer count how many fields carry non-empty data, then normalise.
        fun confidenceFor(populated: Int, total: Int): Float = when {
            populated == 0 -> 0.0f
            populated <= total / 2 -> 0.5f
            else -> 0.8f
        }

        val ciPopulated = listOf(
            coreIdentity.values.isNotEmpty(),
            coreIdentity.longTermGoals.isNotEmpty(),
            coreIdentity.fears.isNotEmpty(),
            coreIdentity.worldview.isNotBlank(),
            coreIdentity.identityMarkers.isNotEmpty()
        ).count { it }

        val bpPopulated = listOf(
            behavioralPatterns.decisionStyle.isNotBlank(),
            behavioralPatterns.stressResponse.isNotBlank(),
            behavioralPatterns.workStyle.isNotBlank(),
            behavioralPatterns.riskTolerance.isNotBlank(),
            behavioralPatterns.energyPattern.isNotBlank()
        ).count { it }

        val csPopulated = listOf(
            communicationStyle.toneProfessional.isNotBlank(),
            communicationStyle.tonePersonal.isNotBlank(),
            communicationStyle.vocabularyMarkers.isNotEmpty(),
            communicationStyle.sentenceStructure.isNotBlank(),
            communicationStyle.humorStyle.isNotBlank(),
            communicationStyle.formalityDefault.isNotBlank()
        ).count { it }

        val krPopulated = if (keyRelationships.isNotEmpty()) 1 else 0

        val ccPopulated = listOf(
            currentContext.activeGoals.isNotEmpty(),
            currentContext.moodTrend.isNotBlank(),
            currentContext.currentStressors.isNotEmpty(),
            currentContext.recentWins.isNotEmpty(),
            currentContext.pendingDecisions.isNotEmpty()
        ).count { it }

        val confidenceScores = ConfidenceScores(
            coreIdentity = confidenceFor(ciPopulated, 5),
            behavioralPatterns = confidenceFor(bpPopulated, 5),
            communicationStyle = confidenceFor(csPopulated, 6),
            keyRelationships = confidenceFor(krPopulated, 1),
            currentContext = confidenceFor(ccPopulated, 5)
        )

        return PersonalityProfile(
            userId = base?.userId ?: "",
            layers = ProfileLayers(
                coreIdentity = coreIdentity,
                behavioralPatterns = behavioralPatterns,
                communicationStyle = communicationStyle,
                keyRelationships = keyRelationships,
                currentContext = currentContext
            ),
            confidenceScores = confidenceScores,
            lastUpdated = base?.lastUpdated ?: emptyMap()
        )
    }

    /**
     * Parse a string value that may represent a list in any of three formats:
     *  1. JSON array:           `["a","b","c"]`
     *  2. Comma-separated:      `"a, b, c"`
     *  3. Newline-separated:    `"a\nb\nc"`
     *
     * Returns an empty list for blank input.
     */
    private fun parseListValue(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return emptyList()

        // JSON array format.
        if (trimmed.startsWith("[")) {
            return try {
                listAnyAdapter.fromJson(trimmed)
                    ?.mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotBlank() } }
                    ?: splitText(trimmed)
            } catch (_: Exception) {
                splitText(trimmed)
            }
        }

        return splitText(trimmed)
    }

    /**
     * Split plain text on newlines or ", " (comma-space). Newlines take priority — if the text
     * contains any newline characters we split on those, otherwise we split on ", ".
     */
    private fun splitText(text: String): List<String> {
        val separator = if (text.contains('\n')) "\n" else ", "
        return text.split(separator)
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    // Extension helper used inside tryParseProfileJson to call parseListValue on a Map.
    private fun Map<String, Any>.list(vararg keys: String): List<String> {
        for (k in keys) {
            val v = this[k]
            if (v != null) return parseListValue(v.toString())
        }
        return emptyList()
    }
}
