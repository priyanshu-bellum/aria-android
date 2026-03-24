# ARIA — Personal AI Life Assistant

## Project Overview

ARIA is a proactive personal AI assistant for Android. It is not a chat app — it is a life layer that listens, learns who the user is, and acts on their behalf. The app is **thin wrapper, not a platform**: heavy lifting delegated to specialist APIs.

## Tech Stack

| Concern | Technology |
|---|---|
| App | Kotlin + Jetpack Compose, API 26+ |
| DI | Hilt |
| DB | Room (SQLite) |
| Orchestrator | PicoClaw (Go binary, ARM64, foreground service) |
| AI Brain | Claude API (`claude-sonnet-4-6`) |
| Personality Memory | Mem0 API |
| MCP Tools | PicoClaw native MCP → Gmail + Google Calendar |
| Messaging | Telegram Bot (via PicoClaw gateway) |
| Offline STT | Vosk (~50MB model) |
| Offline TTS | Sherpa ONNX (Piper/VITS) |
| Cloud STT fallback | Deepgram nova-2 |
| Voice Calls | LiveKit (user's existing implementation) |
| Secrets | Android Keystore via EncryptedSharedPreferences |

## Architecture

```
com.aria/
├── AriaApplication.kt        # @HiltAndroidApp, WorkManager setup
├── MainActivity.kt            # Compose host with splash screen
├── di/AppModule.kt            # Hilt singletons
├── data/
│   ├── local/                 # Room DB: AriaDatabase, entities, DAOs
│   ├── memory/                # Mem0ApiClient, Mem0Repository, models
│   ├── claude/                # ClaudeApiClient, PromptBuilder, models
│   └── repository/SecureStorage.kt
├── picoclaw/                  # ConfigWriter, PicoClawManager
├── service/
│   ├── PicoClawService.kt     # Foreground service for orchestrator
│   ├── AudioCaptureService.kt # Voice listener (TODO)
│   └── SynthesisWorker.kt     # Nightly synthesis at 11pm
├── voice/                     # VoskSttEngine, SherpaTtsEngine, SpeechManager (TODO)
└── ui/
    ├── theme/                 # Color, Type, Theme (dark-first violet palette)
    ├── navigation/AriaNavHost.kt
    └── screens/               # Home, Mirror, Notes, Settings, Onboarding
```

## Key Implementation Details

### API Keys
All keys stored in `SecureStorage` (EncryptedSharedPreferences). Constants defined in `SecureStorage.kt`. Never hardcoded anywhere.

### PicoClaw Lifecycle
- Binary bundled at `assets/picoclaw-arm64` → copied to `filesDir/picoclaw/` on first run
- `PicoClawService` starts it as foreground service, polls health every 30s
- Config written to temp file at startup, deleted after PicoClaw reads it
- Auto-restart with exponential backoff (max 3 retries)

### Personality Memory (5 Layers)
1. Core Identity — values, goals, fears, worldview
2. Behavioral Patterns — decision style, stress response, work patterns
3. Communication Style — tone, vocabulary, sentence structure
4. Key Relationships — important people and dynamics
5. Current Context — active goals, mood, stressors, wins

### Confidence Badge Rules
- 0.0–0.39 → Amber "Still learning"
- 0.40–0.69 → Blue "Confident"
- 0.70–1.00 → Green "Strong"

### Prompt Templates (in PromptBuilder.kt)
- `buildAssistantPrompt()` — everyday assistant with personality context
- `buildProxyPrompt()` — write AS the user
- `buildSynthesisPrompt()` — nightly profile analysis
- `buildOnboardingPrompt()` — 10-question interview
- `buildTodoExtractionPrompt()` — voice → todos

## Current Status

### ✅ Completed
- Project scaffolding (Gradle, manifest, dependencies)
- Room DB (4 entities, 4 DAOs)
- Mem0 API client + repository
- Claude API client (streaming + retry)
- PromptBuilder (all 5 templates)
- PicoClaw integration (ConfigWriter, Manager, Service)
- SecureStorage (encrypted API keys)
- SynthesisWorker (nightly 11pm job)
- UI theme (premium dark palette)
- Navigation (bottom bar + onboarding flow)
- Screen stubs (Home, Mirror, Notes, Settings, Onboarding)

### 🔧 TODO (Screens need full implementation)
- OnboardingScreen — multi-step wizard with Claude interview
- MirrorScreen — 5-layer personality cards, confidence badges, edit bottom sheet
- HomeScreen — greeting, todos, events, PicoClaw status
- NotesScreen — todos + notes lists with filtering
- SettingsScreen — listening mode, call schedule, API keys, export, wipe
- AudioCaptureService — Vosk offline STT + Deepgram fallback
- VoskSttEngine, SherpaTtsEngine, SpeechManager — offline voice
- LiveKit morning call integration

## Agent Team

Each specialized agent owns a distinct layer. Tag tasks to the right agent to avoid context bleed.

### `agent:frontend`
**Scope:** All Jetpack Compose UI — screens, components, theme, navigation, animations.
Files: `ui/screens/`, `ui/theme/`, `ui/navigation/`, `MainActivity.kt`
Stack: Compose Material 3, Hilt Navigation, ViewModel, StateFlow/collectAsState
Rules: Dark-first palette only; no XML layouts; all state via ViewModel; no business logic in composables.

### `agent:kotlin-backend`
**Scope:** Kotlin service layer, API clients, workers, receivers, DI wiring.
Files: `service/`, `data/claude/`, `data/memory/`, `data/repository/`, `di/`, `AriaApplication.kt`
Stack: Hilt, WorkManager, OkHttp, Moshi, Coroutines, EncryptedSharedPreferences
Rules: All API calls wrapped in try/catch with retry; keys only via SecureStorage; never hardcode credentials.

### `agent:data-arch`
**Scope:** Room database schema, DAOs, migrations, Mem0 memory layers, system architecture decisions.
Files: `data/local/`, `data/memory/models/`, `gradle/libs.versions.toml`, `build.gradle.kts` files
Stack: Room + KSP, SQLite, Mem0 API, Moshi models
Rules: UUID primary keys on all entities; all queries return Flow; migrations required for schema changes; 5-layer personality model must stay intact.

### `agent:picoclaw-integrations`
**Scope:** PicoClaw binary lifecycle, MCP tools, Telegram gateway, LiveKit calls, Vosk/Sherpa voice engines.
Files: `picoclaw/`, `service/PicoClawService.kt`, `service/AudioCaptureService.kt`, `voice/`, `app/src/main/assets/picoclaw-arm64`
Stack: Go binary (ARM64), MCP protocol, LiveKit Android SDK, Vosk, Sherpa ONNX
Rules: Binary at `assets/picoclaw-arm64` → copied to `filesDir/picoclaw/` on first run; config file deleted after PicoClaw reads it; max 3 restart retries with exponential backoff; Deepgram only as STT fallback when offline STT fails.

### `agent:tester`
**Scope:** Writing and running tests across all layers — unit, integration, and UI.
Files: `app/src/test/` (unit), `app/src/androidTest/` (instrumented/UI), any `*Test.kt` or `*Spec.kt` files
Stack: JUnit 4/5, Mockk, Turbine (Flow testing), Compose UI Test, Hilt testing (`HiltAndroidTest`), Room in-memory DB, WorkManager test helpers, Robolectric
Rules:
- Never mock the database — use Room's in-memory builder for DAO tests
- Every new screen gets at least one Compose UI test covering happy path and empty state
- Every API client gets an error-path unit test (network failure, malformed JSON, 4xx/5xx)
- PicoClaw install/lifecycle tests use a fake binary (empty file) to avoid bundling test assets
- ViewModel tests use Turbine to assert StateFlow emissions in order
- No test should read from real EncryptedSharedPreferences — inject a fake SecureStorage
- Tests must pass offline; no real network calls in unit or instrumented tests

## Build Order
Follow Phase 1 items 1–10, then Phase 2–4 sequentially. See the master prompt for full build order.

## Rules
- Never hardcode API keys
- All API calls need try/catch with retry
- Room entities use UUID primary keys
- Screens observe Room via Flow for reactivity
- PicoClaw config deleted after startup (security)
- Offline mode: Mirror loads from cached Room data, Notes/Todos work fully offline
