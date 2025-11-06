Handoff Tasks - Pixel Prototype V2
==================================

Completed in this package:
- Android app skeleton updated with wake-word listener ("hey kin")
- Simple SQLite-based local memory store (storeMemory / queryRecent helpers)
- TFLite model stub in assets/
- UI: Internet toggle, Ask Jesy button, console logger

Remaining developer tasks (priority order):
1. Replace dummy TFLite with a real quantized model (speech-wake or small on-device encoder)
2. Integrate a proper on-device embedding encoder and vector index (FAISS/Chroma via NDK or remote service)
3. Implement efficient wake-word detection using a small TFLite keyword model (always-on) to reduce ASR usage
4. Add background Job scheduling (WorkManager) and power optimizations for continuous listening
5. Implement PolicyAgent & secure Ask-Jesy approval UI (biometric + confirm modal)
6. Wire Executor to a secure local sandbox or remote sandbox (decide based on security)
7. Create CI pipeline to build unsigned APK and sign for release
8. Security review: check permissions, keystore usage, encrypted backups
9. Testing: unit tests, integration tests, safety fuzz tests
