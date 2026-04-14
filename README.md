# DNA App

Android-only app for designing salwar kameez and kurtis. Stores your existing dress photos and generates new designs from an uploaded fabric photo by retrieving your past work as references and improving on them — preserving your design DNA.

UI follows Google's **Material 3 Expressive** design language.

## Status

**M1 — Scaffolding** (in progress).

See [`PLAN.md`](PLAN.md) for the full architecture, pipeline, and milestone breakdown.

## Stack

- Kotlin 2.0+ / Jetpack Compose / Material 3 Expressive (`material3 1.4.x`)
- Clean Architecture (UI → ViewModel → UseCase → Repository → Local + Remote)
- Room (local source of truth) + Paging 3 + Coil 3
- Firebase (Auth, Firestore, Storage, Cloud Functions, App Check, Analytics)
- Vertex AI: Gemini 2.5 Flash (vision + structured output) and Gemini 2.5 Flash Image (generation), called from Cloud Functions only
- Vertex AI Multimodal Embeddings + Vector Search for reference retrieval (tier-2)
- WorkManager for background uploads / sync / AI retries

## Package

`com.dna.app` (rename in `app/build.gradle.kts`, `AndroidManifest.xml`, and directory layout if desired).

## Getting started

> This repo contains Gradle project config and initial source files, but **not** the Gradle wrapper binaries or `google-services.json`. You'll generate / provide those locally.

1. Install Android Studio Koala (2024.1.2) or newer.
2. Open this folder in Android Studio. When prompted, let it generate the Gradle wrapper.
3. Create a Firebase project and download `google-services.json` into `app/`.
4. Add your Gemini / Vertex AI setup per `PLAN.md` → Firebase + Cloud Functions layout.
5. Sync Gradle, then run the `app` config on a device or emulator (API 26+).

## Layout

```
app/
├── src/main/java/com/dna/app/
│   ├── DnaApp.kt                 # @HiltAndroidApp
│   ├── MainActivity.kt
│   └── ui/theme/                 # Color, Type, Shape, MotionScheme (Expressive tokens)
├── src/main/res/
└── build.gradle.kts
gradle/libs.versions.toml          # version catalog
settings.gradle.kts
build.gradle.kts                   # root
```

Future directories (per milestones M2–M8): `domain/`, `data/` (local/remote/repo/work/di), `ui/{library,detail,upload,generate,auth}`, and `functions/` for Cloud Functions.

## License

Private project — all rights reserved.
