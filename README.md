# Shardwave — P2P On-Device AI Model Distribution

Shardwave is an Android application demonstrating peer-to-peer distribution of AI model weights (GGUF format) across a local device swarm using BitTorrent (libtorrent4j), with content-defined chunking (FastCDC) to transmit only delta updates between model versions, and direct hand-off into on-device inference via llama.cpp JNI bindings.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                      Shardwave App                        │
│                                                           │
│  UI (Compose Material 3 Dashboard)                       │
│     │                                                    │
│     ▼                                                    │
│  ModelRepository ──────────────┐                         │
│     │                          │                         │
│     ▼                          ▼                         │
│  ChunkStore              InferenceManager                │
│  (content-addressed         (llama.cpp JNI)               │
│   local blocks)                                          │
│     │                                                    │
│     ▼                                                    │
│  SwarmManager                                             │
│     │                                                    │
│     ▼                                                    │
│  libtorrent4j (Local Service Discovery - LSD)            │
│     │                                                    │
│     ▼                                                    │
│  Local Wi-Fi network peers                                 │
└─────────────────────────────────────────────────────────┘
```

---

## Core Technical Features

1. **Content-Defined Delta Transfers (FastCDC):**
   Uses FastCDC rolling Gear hashes (min 512 KB, avg 2 MB, max 8 MB) to chunk GGUF model files. When a device with model v1 requests v2, `ManifestDiffer` identifies shared chunks, pre-populates them locally, and fetches only missing delta pieces over P2P.
2. **Local BitTorrent Swarm (libtorrent4j):**
   Configured with Local Service Discovery (LSD) on port `6881` for zero-configuration local network discovery without external trackers or public DHT.
3. **On-Device LLM Inference (llama.cpp JNI):**
   Loads verified GGUF models and runs local streaming inference via C++ JNI native bindings.
4. **Swarm Resilience:**
   Origin seeding device can disconnect mid-transfer; receiving nodes automatically trade missing chunks with each other to complete the transfer.
5. **Live Telemetry & Instrumentation:**
   Exposes connected peer count, download/upload speeds, verified vs rejected chunk counts, and % of model reused from local chunk store.

---

## Telemetry & Instrumentation

| Metric | Description |
|---|---|
| Connected Peers | Active P2P swarm devices on local Wi-Fi |
| Download / Upload Speed | Realtime byte throughput |
| Chunks Verified | Chunks validated via SHA-256 upon arrival |
| Chunks Reused Local | Local chunks pre-populated from previous model versions |
| Local Reuse % | Percentage of model weight payload saved over network |

---

## Emulator Multi-Device Setup (adb Port Forwarding)

For testing multi-device P2P swarms across Android emulators running on one host machine:

```bash
# Forward port 6881 for Emulator 1
adb -s emulator-5554 forward tcp:6881 tcp:6881

# Forward port 6883 for Emulator 2
adb -s emulator-5556 forward tcp:6883 tcp:6881
```

---

## Build & Run Instructions

```bash
# Build app APK
./gradlew assembleDebug

# Run unit test suite (FastCDC, ChunkStore, ModelAssembler, SwarmManager, Inference)
./gradlew testDebugUnitTest
```

---

## Package Structure

```
app/src/main/java/com/yashvant/shardwave/
├── chunking/          # FastCDC Chunker, ManifestBuilder, ManifestDiffer
├── data/              # ModelRepository, SwarmStats
├── inference/         # LlamaCppBridge, ModelRunner (llama.cpp JNI)
├── storage/           # ChunkStore, ModelAssembler
├── swarm/             # SwarmManager, SwarmService, PeerStatsTracker, TorrentBuilderHelper
├── ui/                # Compose CatalogScreen, TransferScreen, InferenceScreen, MainViewModel
└── verification/      # HashVerifier (SHA-256 stream verification)
```
