# Technical Specification: GTD & Second Brain Ecosystem (`gtd-org`)

## 1. Global System Vision
`gtd-org` is a distributed, AI-compatible personal operating system designed to implement David Allen's GTD (Getting Things Done) framework combined with a Second Brain methodology. The ecosystem is built around a unified, immutable ledger of assets, heavily relying on functional programming paradigms.

The system is split into multiple independent sub-projects:
1. **Ingest Sub-project (Current Babashka Core):** Hands-free capturing of audio memos from physical life, enforcing cryptographic identity and cloud staging.
2. **Distill & Structure Sub-project:** Ingestion of audio into Gemini API (`gemini-2.5-flash`) for direct native transcription, structuring, and metadata extraction.
3. **Reflect & Organize Sub-project (Emacs/Org-mode):** Automated injection into the local Org-agenda, implementing processing and reviewing phases.
4. **Rich Client Sub-project (Re-frame SPA):** A ClojureScript Single Page Application using `re-frame` to provide a reactive, lightning-fast UI for reviewing the GTD workflow, managing the inbox, and visualizing the Second Brain knowledge graph.

---

## 2. Architectural Decision Records (ADRs)

### ADR 0001: Wipe-out on Staging (TTL Invariant)
- **Context:** Local incoming audio captures can accumulate quickly and clutter the environment.
- **Decision:** The cloud ingestion bucket (Google Cloud Storage) will have a strict 1-day Time-To-Live (TTL) lifecycle rule. 
- **Consequence:** Data must be processed or moved out of the staging area within 24 hours. The staging area is treated as volatile, forcing the system to maintain a high-velocity throughput.

### ADR 0002: Specification-Driven Development (WIP Limit)
- **Context:** LLMs and CLI tools like Aider can easily drift or introduce bloat if not strictly constrained.
- **Decision:** No production code is to be written without an active, isolated specification in `docs/current_slice.md`. The Work-In-Progress (WIP) limit is set to exactly 1 slice at a time.
- **Consequence:** The developer (and AI) must completely finish, test, and mark the active slice as done before moving to any other task on the Kanban board.

### ADR 0003: Immutable Native Paths
- **Context:** Working with file paths as raw strings frequently breaks in unix environments when folders or filenames contain spaces (e.g., `My RedHat`, `lenovo-P1-zbytky`).
- **Decision:** All internal Clojure/Babashka components must pass files as `java.io.File` or `java.nio.file.Path` objects. Turning paths into pure strings is strictly forbidden except at the very edge of I/O output.
- **Consequence:** Eliminates shell-expansion errors and `xargs` splitting issues across the entire ingest pipeline.

### ADR 0004: Frontend State Architecture (Re-frame)
- **Context:** The rich client application for the GTD desktop/web dashboard needs to handle high-frequency filtering, multi-perspective agenda views, and rapid graph navigation.
- **Decision:** The UI layer will be built exclusively as a ClojureScript Single Page Application powered by `re-frame`. All global application state must reside in a single, immutable database atom (`app-db`).
- **Consequence:** Ensures predictable, unidirectional data flow, rapid semantic searching across the inbox, and deterministic UI component testing with zero side effects.

---

## 3. Component Design (Polylith Bricks)
The workspace is split into isolated bricks under `components/` and executed via development `bases/`.

### `fp` (File Provider)
- **Responsibility:** Local filesystem disk discovery and I/O.
- **Key Invariant:** Must safely walk directories using Java NIO, handle spaces in paths, filter raw audio extensions (`.mp3`, `.wav`, `.m4a`), and compute deterministic cryptographic identities.

### `db` (Database / Storage Provider)
- **Responsibility:** Manage local transaction state and interact with Google Cloud Storage (GCS).
- **Key Invariant:** Enforce storage structures and operations within the `europe-west3` region.

---

## 4. Data Schema & Identity Models

### Binary Content-Based Identity (File Signature)
Every ingested asset is uniquely and permanently identified by its raw file SHA-256 hash. This acts as the immutable primary key across the entire life cycle of the file (local, cloud, and DB). If the content of the file doesn't change, its identity remains identical.

---

## 5. Execution Pipelines
- **Pipeline 1: Ingest & Cloud Sync (M1):** Local discovery -> SHA-256 calculation -> Push to GCS.
- **Pipeline 2: Distill & Transcribe (M2):** GCS trigger -> Gemini API (`gemini-2.5-flash`) direct audio transcription -> Structured output.
- **Pipeline 3: Org Integration (M3):** Structured payload -> Org-mode agenda injection.
