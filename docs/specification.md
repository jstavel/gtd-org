# Technical Specification: GTD & Second Brain Ecosystem (`gtd-org`)

## 1. Global System Vision
`gtd-org` is a distributed, AI-compatible personal operating system designed to implement David Allen's GTD (Getting Things Done) framework combined with a Second Brain methodology. The ecosystem is built around a unified, immutable ledger of assets, heavily relying on functional programming paradigms.

The system is split into multiple independent sub-projects:
1. **Ingest Sub-project (Babashka Core: org-staging):** Orchestrates the initial capture and preparation of raw assets (e.g., audio files) from local sources into a structured staging area, ensuring cryptographic identity and readiness for cloud processing.
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
- **Context:** Working with file paths as raw strings frequently breaks in unix environments when folders or filenames contain spaces (e.g., `My Personal`,).
- **Decision:** All internal Clojure/Babashka components must pass files as `java.io.File` or `java.nio.file.Path` objects. Turning paths into pure strings is strictly forbidden except at the very edge of I/O output.
- **Consequence:** Eliminates shell-expansion errors and `xargs` splitting issues across the entire ingest pipeline.

### ADR 0004: Frontend State Architecture (Re-frame)
- **Context:** The rich client application for the GTD desktop/web dashboard needs to handle high-frequency filtering, multi-perspective agenda views, and rapid graph navigation.
- **Decision:** The UI layer will be built exclusively as a ClojureScript Single Page Application powered by `re-frame`. All global application state must reside in a single, immutable database atom (`app-db`).
- **Consequence:** Ensures predictable, unidirectional data flow, rapid semantic searching across the inbox, and deterministic UI component testing with zero side effects.
### ADR 0005: 

### `fp` (File Provider)
- **Responsibility:** Local filesystem disk discovery and I/O.
- **Key Invariant:** Must safely walk directories using Java NIO, handle spaces in paths, filter raw audio extensions (`.mp3`, `.wav`, `.m4a`), and compute deterministic cryptographic identities.

### `db` (Database / Storage Provider)
- **Responsibility:** Manage local transaction state and interact with Google Cloud Storage (GCS).
- **Key Invariant:** Enforce storage structures and operations within the `europe-west3` region.

### Binary Content-Based Identity (File Signature)
Every ingested asset is uniquely and permanently identified by its raw
file SHA-256 hash. This acts as the immutable primary key across the
entire life cycle of the file (local, cloud, and DB). If the content
of the file doesn't change, its identity remains identical.


---

## 3. Component Design (Polylith Bricks)
The workspace is split into isolated bricks under `components/` and executed via development `bases/`.

---

## 4. Metadata Extraction Rules (Staging Worker)

When processing files from `~/Downloads/`, the worker must prioritize information gathering to support mindful decision-making. The extraction follows this hierarchy:

1. **Duration:** - Extract via `ffprobe`.
   - Convert numerical value to `HH:MM:SS` format.

2. **Temporal Data (Date/Time):**
   - **Primary source:** Regex match on filename (e.g., `YYYY-MM-DD HH_MM_SS.mp3`).
   - **Fallback:** If filename does not contain a timestamp, use file creation time (`ctime`).

3. **Classification (KIND property):**
   - If filename matches Plaud/Journal pattern (Timestamp format), set `KIND: Plaud/Journal`.
   - If ID3 tags are present, extract `TITLE`, `ARTIST`, `ALBUM`.
   - If ID3 tags are empty, set `KIND: Generic`.

4. **Formatting:**
   - All extracted values must be mapped to Org-mode Properties in the `staging.org` entry.
   
## Subprojects

### Subproject: staging-worker (org-staging)

The `staging-worker` is a Babashka-based automation utility responsible for the transition of assets from the `Downloads` transit zone into the `org-assets` management system.

#### Operational Logic
1. **Scanning:** Monitor `$ORG_ROOT/Downloads/` for files (specifically `.mp3` and media formats).
2. **Metadata Inspection:**
   - Execute `ffprobe` to obtain technical metadata (duration, codec).
   - Attempt ID3 tag extraction.
   - Fallback: Use filename regex pattern `YYYY-MM-DD HH_MM_SS.ext` to extract `:DATE:` and `:TIME:` properties if tags are empty.
3. **Staging:**
   - Generate a `TODO` entry in `$ORG_ROOT/staging.org`.
   - Ensure the entry includes mandatory properties: `:HASH:`, `:SOURCE:`, `:DATE:`, `:DURATION:`, and `:KIND:`.
4. **Safety Invariant:** - The worker is **read-only** regarding file movement. It identifies and registers; it never moves or deletes files without a user-triggered `INGEST` action in `staging.org`.

#### Implementation Requirements (Babashka)
- **Tooling:** Use `babashka.process` for system calls and `cheshire` for JSON parsing.
- **Atomic Operations:** Ensure appending to `staging.org` is thread-safe and preserves existing content.
- **Configuration:** The worker must read the `$ORG_ROOT` environment variable to locate assets.

#### Metadata Extraction Schema
| Property   | Source         | Fallback   |
|:-----------|:---------------|:-----------|
| `DATE`     | Filename Regex | `ctime`    |
| `DURATION` | `ffprobe`      | `00:00:00` |
| `KIND`     | Filename/Tags  | `Generic`  |

#### References 
- This specification must be implemented in strict adherence to: [ADR 0005: Asset Storage Invariants](./adr/005-asset-storage-and-ingestion-invariants.md)

---

## 5. Execution Pipelines
- **Pipeline 1: Ingest & Staging (M1):** `org-staging` component discovers new items in local staging (e.g., Downloads), calculates SHA-256 hash, and stages them to `assets/objects/UUID/raw/` - when they are accepted.
- **Pipeline 2: Distill & Transcribe (M2):** Monitoring of `assets/objects/UUID/raw/` for new assets -> Gemini API (`gemini-2.5-flash`) direct audio transcription -> Structured output (e.g., to `assets/objects/UUID/generated/`).
- **Pipeline 3: Org Integration (M3):** Structured output -> Org-mode agenda injection.
