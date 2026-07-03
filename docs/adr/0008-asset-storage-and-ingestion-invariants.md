# ADR-0008: Asset Storage Architecture and Pipeline Structure

**Status:** Accepted
**Context:** Previous iteration (ADR-0005) lacked a clear definition for derived assets. To support the M1 (Ingest), M2 (Distill), and M3 (Integration) pipelines, we require a standardized layout within the content-addressable store.

## Decision
We will adopt a hierarchical, UUID-centric storage model within the `assets/objects/` namespace. Each asset is isolated in its own directory, containing segregated subdirectories for raw source material and processed outputs.

## Directory Structure Specification
All assets will follow this pattern:
`assets/objects/<shard-a>/<shard-b>/<UUID>/`

Inside each `<UUID>/` directory, the following structure is mandated:
* `raw/`: **Source of Truth.** Contains the original imported asset (e.g., `audio.mp3`). This directory is immutable for the pipeline once ingested.
* `generated/`: **Derived Data.** Contains AI-processed outputs, transcriptions, and metadata (e.g., `transcription.txt`, `summary.json`).

## Rationale
1. **Locality:** Keeping all data (raw and generated) under a single UUID simplifies lifecycle management. Moving, backing up, or deleting an asset becomes a single filesystem operation.
2. **Namespace Isolation:** The `assets/objects/` prefix distinguishes primary storage from potential future top-level directories (like `assets/config/` or `assets/templates/`), adhering to content-addressable storage practices.
3. **Pipeline Integrity:**
    * **M1 (Ingest):** Only indexes contents within `raw/`.
    * **M2 (Distill):** Writes outputs exclusively to `generated/`, ensuring no pollution of the source data.
    * **M3 (Integration):** Monitors `generated/` for new outputs to inject into Org-mode, keeping integration logic clean and isolated from ingestion logic.

## Consequences
* **Positive:** Consistent directory layout allows for trivial "garbage collection" of derived data by simply clearing the `generated/` folder.
* **Negative:** The indexing logic in `build-asset-index` must be updated to explicitly ignore `generated/` and focus solely on `raw/` to maintain the integrity of the hash-to-path mapping.
