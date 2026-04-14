# DNA App — AI Dress Designer for Salwar Kameez & Kurtis (v2)

## Context

An Android-only app that (1) stores your salwar kameez / kurti photo library and (2) generates new dress designs from an uploaded fabric photo by retrieving visually-similar past designs and improving on them — preserving *your* design DNA. UI follows **Material 3 Expressive**.

The v1 plan was a solid MVP scaffold but had four load-bearing gaps that this v2 closes:

1. **AI quality & control** — raw "fabric → image" generation hallucinates and ignores brand DNA. Solved with **Image RAG** (embeddings + vector retrieval) + **structured DesignSpec** + **chain-of-thought prompting**.
2. **Security & cost** — shipping a Gemini key in the APK is indefensible. Solved by moving all AI calls to **Cloud Functions** behind Firebase Auth with per-user quotas.
3. **Performance** — cloud-only image grids jitter. Solved with **Room as source of truth** + **Paging 3** + **Coil disk cache** + **thumbnail tiers**.
4. **UX trust** — one-shot generation feels like a gimmick. Solved with a **guided generation flow** (spec → N variants → iterate/edit).

---

## Tech Stack

| Layer | Choice | Notes |
|---|---|---|
| Language | **Kotlin 2.0+** | |
| UI | **Jetpack Compose** + **Material 3 Expressive** (`material3 1.4.x`) | Expressive motion/shape/typography tokens |
| Min / Target SDK | 26 / 35 | |
| Architecture | **Clean Architecture**: UI → ViewModel → **UseCase** → Repository → (Local + Remote) | UseCase layer exists because AI orchestration is non-trivial |
| DI | **Hilt** | |
| Async | **Coroutines + Flow**, **SavedStateHandle** for config-change survival | |
| Local DB | **Room** — source of truth for dress/design metadata | UI reads only Room; repo syncs with Firestore |
| Paging | **Paging 3** with Room-backed `PagingSource` | For library grid |
| Images | **Coil 3** with disk + memory cache; 3 size tiers stored per dress (original / display ~1024px / thumbnail ~256px) | |
| Background work | **WorkManager** — uploads, embedding generation, AI retries, sync | Survives process death |
| Cloud | **Firebase Auth (Google)**, **Firestore**, **Firebase Storage**, **Cloud Functions (Node 20 / TS)**, **Firebase App Check** (attestation) | App Check blocks abuse from scraped tokens |
| AI (server-side only) | **Gemini 2.5 Flash** (vision + structured output) and **Gemini 2.5 Flash Image** (generation + editing), called from Cloud Functions via Vertex AI | No keys in the APK |
| Embeddings + retrieval | **Vertex AI `multimodalembedding@001`** → **Vertex AI Vector Search** index | Fallback path below for small libraries |
| Analytics | **Firebase Analytics** + custom events for `design_liked`, `design_regenerated`, `tag_edited` | Feedback loop for prompt tuning |
| Build | Gradle KTS + `libs.versions.toml` version catalog | |

### App Check & quotas (non-negotiable)

- Cloud Functions verify App Check token + Firebase Auth UID on every request.
- Per-user quota: `N` generations per day, `M` embeddings per day, enforced in Firestore counter doc. Soft cap surfaces in UI; hard cap returns 429.

---

## The "DNA" pipeline (core feature)

```
[1] User picks fabric photo
        │
        ▼
[2] App uploads fabric to Storage (thumbnail first, full-res via WorkManager)
        │
        ▼
[3] Cloud Function: analyzeFabric(fabricUrl)
      → Gemini 2.5 Flash returns FabricSpec
        { material: "silk"|"cotton"|"georgette"|..., weave, weight,
          pattern: "plain"|"printed"|"embroidered",
          dominantColors[], motifs[], drape: "stiff"|"flowy" }
        │
        ▼
[4] Cloud Function: retrieveReferences(fabricSpec, fabricEmbedding)
      → Vector Search top-K (K=5) from user's private index
      → Re-rank with tag filters (occasion, garment type)
      → Return 3 chosen references + why-chosen reason
        │
        ▼
[5] Cloud Function: proposeDesignSpec(fabricSpec, references)
      → Gemini 2.5 Flash with structured JSON output returns DesignSpec
        (see schema below). This is a *plan*, not an image.
        │
        ▼
[6] UI shows DesignSpec as editable chips/selectors
      (user can tweak neckline, sleeve, length, embellishments, etc.
       before burning an image-gen call)
        │
        ▼
[7] Cloud Function: generateDesign(fabricUrl, referenceUrls, designSpec)
      → Downsamples references to ≤512px to save tokens
      → Gemini 2.5 Flash Image returns N=3 variants
      → Each result stored in Storage + Firestore with
        { prompt, designSpec, referenceIds, variantIndex, parentDesignId }
        │
        ▼
[8] User: like / dislike / regenerate / edit-a-variant
      (edit = Gemini 2.5 Flash Image in *editing* mode: "change sleeve to 3/4")
        │
        ▼
[9] Saved design is embedded and added to the same vector index,
    so the user's "DNA" strengthens over time.
```

### Chain-of-thought prompt (step [7])

Server builds a layered prompt:
```
ROLE: Fashion designer specialising in salwar kameez and kurtis.
FABRIC: {fabricSpec as JSON}  [+ fabric image]
DNA REFERENCES (user's past work — preserve their silhouette sensibility):
  - {ref1 summary}  [+ 256px image]
  - {ref2 summary}  [+ 256px image]
  - {ref3 summary}  [+ 256px image]
DESIGN SPEC (user-approved): {designSpec as JSON}
TASK: Produce a studio photograph of the finished garment on a neutral
background. Match the fabric drape. Respect the design spec exactly.
Do NOT copy any single reference — synthesise.
NEGATIVE: blurry, distorted hands, watermark, text.
```

---

## Data model

```kotlin
// Local (Room) + remote (Firestore) mirror each other.
@Entity data class DressEntity(
  @PrimaryKey val id: String,
  val ownerUid: String,
  val imageOriginalUrl: String,
  val imageDisplayUrl: String,
  val imageThumbUrl: String,
  val garmentType: GarmentType,        // SALWAR_KAMEEZ | KURTI
  val designSpec: DesignSpec,          // @Embedded, structured taxonomy below
  val userTags: List<String>,          // free-form, user-added
  val source: Source,                  // UPLOADED | GENERATED
  val parentDesignId: String?,         // for generated variants / edits
  val createdAt: Long,
  val syncState: SyncState,            // PENDING_UPLOAD | SYNCED | FAILED
)

data class DesignSpec(
  val garmentType: GarmentType,
  val neckline: Neckline,              // enum: V, ROUND, BOAT, COLLAR, KEYHOLE, ...
  val sleeve: SleeveStyle,             // FULL, THREE_QUARTER, CAP, SLEEVELESS, BELL, ...
  val sleeveDetail: SleeveDetail?,     // PUFF, CUFFED, BISHOP, ...
  val length: Length,                  // SHORT, KNEE, CALF, ANKLE, FLOOR
  val silhouette: Silhouette,          // ANARKALI, STRAIGHT, A_LINE, FLARED, ...
  val fit: FitType,                    // FITTED, RELAXED, OVERSIZED
  val embellishments: List<Embellishment>, // ZARDOSI, CHIKANKARI, MIRROR, SEQUINS, ...
  val embellishmentPlacement: List<Placement>, // NECKLINE, HEM, SLEEVES, ALL_OVER, ...
  val occasion: Occasion,              // CASUAL, OFFICE, FESTIVE, BRIDAL
  val colorPalette: List<String>,      // hex
  val notes: String?,
)

data class FabricSpec(
  val material: Material,              // COTTON, SILK, GEORGETTE, CHIFFON, ...
  val pattern: FabricPattern,          // PLAIN, PRINTED, EMBROIDERED, BLOCK_PRINT, ...
  val weight: FabricWeight,            // LIGHT, MEDIUM, HEAVY
  val drape: Drape,                    // STIFF, STRUCTURED, FLOWY
  val dominantColors: List<String>,
  val motifs: List<String>,
)

@Entity data class GeneratedDesignEntity(
  @PrimaryKey val id: String,
  val ownerUid: String,
  val fabricImageUrl: String,
  val fabricSpec: FabricSpec,          // @Embedded
  val referenceDressIds: List<String>,
  val designSpec: DesignSpec,          // @Embedded
  val resultImageUrl: String,
  val variantIndex: Int,               // 0..N
  val parentGenerationId: String?,     // for edits / regenerations
  val prompt: String,                  // for reproducibility
  val liked: Boolean?,                 // null = not rated
  val createdAt: Long,
)
```

All enums are a **controlled vocabulary** — Gemini is constrained to emit one of these values via Gemini's structured-output JSON schema, so tags are never inconsistent.

---

## Architecture

```
app/
├── domain/                       # pure Kotlin, no Android deps
│   ├── model/                    # DressItem, DesignSpec, FabricSpec, GeneratedDesign (domain models)
│   ├── taxonomy/                 # Neckline, Sleeve, ... enums (single source of truth)
│   └── usecase/
│       ├── UploadDressUseCase.kt          # photo → Storage → auto-tag → embed → Room → Firestore
│       ├── AnalyzeFabricUseCase.kt
│       ├── RetrieveReferencesUseCase.kt
│       ├── ProposeDesignSpecUseCase.kt
│       ├── GenerateDesignUseCase.kt       # produces N variants
│       ├── EditDesignUseCase.kt           # change one spec field, re-gen
│       ├── RateDesignUseCase.kt           # like/dislike → analytics
│       └── ObserveLibraryUseCase.kt       # Paging source
│
├── data/
│   ├── local/
│   │   ├── db/ AppDatabase, DressDao, DesignDao, SyncQueueDao
│   │   └── mapper/ entity ↔ domain
│   ├── remote/
│   │   ├── firebase/ FirestoreSource, StorageSource, AuthSource
│   │   ├── functions/ AiFunctionsClient        # typed callable-function wrapper
│   │   └── dto/
│   ├── repo/
│   │   ├── DressRepository.kt     # NetworkBoundResource-style: Room is SoT
│   │   ├── DesignRepository.kt
│   │   └── SyncRepository.kt
│   ├── work/                      # WorkManager workers
│   │   ├── UploadImageWorker
│   │   ├── EmbedDressWorker
│   │   └── LibrarySyncWorker      # periodic bidirectional sync
│   └── di/                        # Hilt modules
│
├── ui/
│   ├── theme/                     # Color, Type, Shape, MotionScheme (Expressive tokens centralised)
│   ├── components/                # DressCard, SpecChipRow, VariantCarousel, ExpressiveFab, LoadingShape
│   ├── library/                   # LibraryScreen (Paging 3 lazy staggered grid), FilterSheet
│   ├── detail/                    # DressDetailScreen (shared-element hero)
│   ├── upload/                    # UploadDressScreen
│   ├── generate/
│   │   ├── FabricPickScreen
│   │   ├── SpecReviewScreen       # edit DesignSpec before burning an image call
│   │   ├── VariantsScreen         # 3 results, like/edit/regenerate
│   │   └── GenerateViewModel      # orchestrates use cases, exposes StateFlow
│   └── auth/
└── MainActivity, DnaApp
```

### Why UseCases

Generation is a 4-step pipeline with user-gated steps between. A ViewModel calling a Repository directly muddies ordering, retry, and testing. UseCases make each step independently testable and cacheable (e.g. `AnalyzeFabricUseCase` memoises by fabric image hash — re-picking the same fabric doesn't re-call Gemini).

### Repository pattern (Room as source of truth)

```
UI  ──observes──►  Room (Flow<PagingData<DressItem>>)
                    ▲
                    │ writes (insert/update)
                    │
Repository  ──reads/writes──►  Firestore + Storage
            ──enqueues──►  WorkManager (upload, embed, retry)
```

- Compose list scrolling never blocks on the network.
- Offline-first: uploads queue and drain when online. The UI shows `syncState` badges.

---

## Vector retrieval — two-tier strategy

The strongest counter-argument to Vector Search is over-engineering for a small private library. Resolved by a tiered plan:

- **Tier 1 (library ≤ 100 items)**: skip Vector Search. Use Firestore queries on the controlled-vocabulary tags (e.g. `material=silk AND occasion=festive`) + a small client-side similarity score over fabric dominant colors. Free, instant, good enough.
- **Tier 2 (library > 100 items)**: embed every dress via `multimodalembedding@001` on upload (WorkManager job), store vectors in **Vertex AI Vector Search** with `ownerUid` as a namespace. Retrieval uses fabric embedding → top-K, then re-rank by tag filters. Cloud Function hides the switch — app code doesn't change.

The upgrade threshold is a server-side flag (`useVectorSearch`) so it can be flipped without an app release.

---

## Material 3 Expressive specifics

- `expressive{Light,Dark}ColorScheme()` + dynamic color on API 31+.
- **`MotionScheme.expressive()`** centralised in `ui/theme/MotionScheme.kt` — bouncier spatial springs, fast effect springs. Avoids Compose code bloat from ad-hoc `spring()` calls.
- **Shape morphing** cards on selection (`MaterialShapes.Cookie9Sided` for featured / hero dresses).
- **FlexibleTopAppBar** (Library), **Button groups** (filter chips), new **LoadingIndicator** (morphing) during generation.
- Shared-element transitions for grid → detail.
- `Typography.expressive()` with larger display styles for empty states.
- `GraphicsLayer` used for blur/tint only in the variant carousel — gated behind API 31+.

---

## Firebase + Cloud Functions layout

```
functions/src/
├── index.ts
├── ai/
│   ├── analyzeFabric.ts      # HTTPS callable
│   ├── tagDress.ts           # called by EmbedDressWorker via callable
│   ├── embedImage.ts
│   ├── retrieveReferences.ts
│   ├── proposeDesignSpec.ts
│   ├── generateDesign.ts     # returns N variants
│   └── editDesign.ts
├── quota/checkAndIncrement.ts
├── schemas/                  # zod schemas mirroring the Kotlin taxonomy
└── util/downsampleImage.ts   # sharp-based, 256/512/1024 tiers
```

Security rules (sketch):
```
match /dresses/{id} {
  allow read:   if signedIn() && resource.data.ownerUid == request.auth.uid;
  allow create: if signedIn() && request.resource.data.ownerUid == request.auth.uid;
  allow update, delete: if signedIn() && resource.data.ownerUid == request.auth.uid;
}
match /designs/{id}   { /* same pattern */ }
match /quotas/{uid}   { allow read: if request.auth.uid == uid; allow write: if false; }  // server-only
```
Storage: `users/{uid}/dresses/{id}/{tier}.jpg`, owner-only.

App Check with Play Integrity provider enforced on Functions and Storage.

---

## Screens

1. **Sign In** — Google via Credential Manager.
2. **Library** — Paging 3 staggered grid. Filter chips (garment type, occasion, color). Expressive FAB split: "Add dress" / "Design new".
3. **Upload Dress** — PhotoPicker → immediate thumbnail upload → `UploadDressUseCase` runs auto-tag + embed in background; tags shown as editable chips as they arrive (progressive UI).
4. **Dress Detail** — hero image, DesignSpec as chips (editable), "use as reference" toggle, delete, version history (for generated designs).
5. **Generate — Fabric Pick** → **Spec Review** (edit DesignSpec before spending on image gen) → **Variants** (3 results, like/dislike/regenerate/edit-this-one). Saved variants appear in Library tagged as `GENERATED`.
6. **Settings** — quota usage, signed-in account, dark mode toggle, clear local cache.

---

## Files to create (scaffolding)

**Gradle / project root:** `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/google-services.json`, `firebase.json`, `firestore.rules`, `firestore.indexes.json`, `storage.rules`.

**Android app source:** all files listed in the Architecture tree above.

**Cloud Functions:** `functions/package.json`, `functions/tsconfig.json`, `functions/src/**` as laid out above.

---

## Build order (milestones)

1. **M1 — Scaffolding**: Android project (Compose + Hilt + Room + Paging + Coil + M3 1.4.x), Firebase project, App Check, Functions skeleton, CI (Gradle check + functions test).
2. **M2 — Auth + Library shell**: Google sign-in, empty Library (Room-backed Paging), Expressive theme applied.
3. **M3 — Upload pipeline**: PhotoPicker → UploadImageWorker → thumb/display/original tiers → `tagDress` Function → Room insert → grid shows item with live-updating tags. (No embedding yet — tier-1 retrieval.)
4. **M4 — Detail + edit tags + delete + sync worker** (bidirectional Room↔Firestore).
5. **M5 — Generation pipeline (tier-1 retrieval)**: FabricPick → analyzeFabric → retrieveReferences (Firestore-only) → proposeDesignSpec → SpecReview UI → generateDesign (3 variants) → VariantsScreen → save to Library.
6. **M6 — Edit + regenerate + rating** (feedback loop via Analytics).
7. **M7 — Embeddings + Vector Search (tier-2)**: `EmbedDressWorker` backfills existing library; `retrieveReferences` Function switches on feature flag.
8. **M8 — Polish**: Expressive motion, loading indicator, shared-element hero, share/export, offline states, error UX, quota UI.

Each milestone ends in a demoable build on a physical device.

---

## Verification

- **Static**: `./gradlew lint ktlintCheck detekt`, `cd functions && pnpm lint && pnpm tsc --noEmit`.
- **Unit**: Repository + UseCase tests with fake Firestore, fake Functions client, fake Room (`./gradlew testDebugUnitTest`). Functions unit tests with `firebase-functions-test`.
- **Integration**: Firebase Emulator Suite runs Auth + Firestore + Storage + Functions locally; instrumented Android tests run against emulators.
- **E2E on physical device**:
  1. Sign in.
  2. Upload 10 dresses → tags populate within 30s each → visible in grid offline after airplane-mode toggle.
  3. Upload a silk embroidered fabric → references auto-picked make sense → edit DesignSpec (change sleeve to 3/4) → 3 variants render → like variant 2 → appears in Library as `GENERATED`.
  4. Edit generated design: "change neckline to boat" → new variant inherits parent.
  5. Toggle dark mode; verify Expressive colors + motion.
  6. Kill app mid-upload → reopen → WorkManager resumes.
  7. Fresh install on second device with same account → library reconciles from Firestore.
- **Cost/quality review** at end of M5 and M7 with ~20 real fabrics — tune prompt templates, embedding re-rank weights, and quota defaults based on results.

---

## Open items to confirm during M1

- App package name (suggest `com.<yourname>.dna`).
- App/brand name (currently "DNA App").
- Firebase plan — Blaze (pay-as-you-go) required for Cloud Functions + Vertex AI.
- Default daily quotas (suggest 20 generations / 100 embeddings per user while tuning).
- Whether to build Settings → "Export my data" from day 1 (recommended — user owns their DNA).
