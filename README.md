# gtd-org Ecosystem

`gtd-org` is a complete, distributed personal operating system designed to implement David Allen's **GTD (Getting Things Done)** methodology combined with a **Second Brain** (Zettelkasten) knowledge architecture. The goal of this ecosystem is to create a reactive, robust, and AI-compatible environment for capturing, distilling, and reflecting on ideas, tasks, and personal knowledge.

The architecture is built from the ground up to support seamless, hands-free information capture in the field, systematically refining raw inputs into a structured, executable state within Emacs (Org-mode) and a future reactive web client.

## System Architecture & Sub-projects

1. **Ingest Node (Current Babashka Core):**
   - A CLI subsystem built with Babashka following the Polylith architecture workspace.
   - Automatically monitors and discovers raw voice recordings (from hardware recorders or phone apps) in local staging areas.
   - Guarantees data integrity using **Content-Based Identity** (SHA-256) and safely automates synchronization into transient cloud storage.

2. **Distill & Structure Engine:**
   - Monitors cloud staging buckets and processes raw audio assets using the **Gemini API (`gemini-2.5-flash`)**.
   - Leverages native multi-modal capabilities for direct audio transcription, immediate metadata extraction, and semantic context structuring without intermediate rendering steps.

3. **Reflect & Organize Layer (Emacs / Org-mode):**
   - Automatically injects distilled payloads directly into local `org-agenda` and personal knowledge bases.
   - Provides infrastructure for daily synchronization protocols, weekly reviews, and clean Inbox clearance.

4. **Rich Client SPA (Re-frame):**
   - A planned ClojureScript Single Page Application powered by the `re-frame` architecture.
   - A reactive, ultra-fast user interface for managing the complete GTD workflow, navigating the knowledge graph, and performing rapid inbox clearing over a unified app database state (`app-db`).

## Tech Stack
- **Backend / Ingest Node:** Babashka (Clojure), Java NIO Interop
- **Frontend / Dashboard:** ClojureScript, Reagent, Re-frame SPA (Future Phase)
- **Cloud Infrastructure:** Google Cloud Storage (GCS) — Region: `europe-west3` (TTL Invariant = 1 Day)
- **AI Core:** Gemini API (`gemini-2.5-flash`)
- **Development Tooling:** Aider (CLI AI assistant), Emacs Org-mode

## Kanban Status Conventions
To maintain strict visual alignment across the entire ecosystem (from terminal tools and Org-mode agenda to the Re-frame UI dashboard), all components adhere to the following status tracking matrix:

| Status | Emoji Option | Modern / Minimalist | Meaning / Context |
| :--- | :---: | :---: | :--- |
| **Planned** | 📅 | ⚪ | Scheduled for later execution / Backlog |
| **In Progress** | 🚧 | 🔵 | Under active construction; current WIP limit focus |
| **On Review** | 👀 | 🔍 | Waiting for verification, feedback, or test compilation |
| **Done** | ✅ | 🟢 | Task finalized, verified, and safely merged |
| **Blocked** | 🛑 | 🔴 | Execution halted; requires intervention or resolution |

## Project Runway (Ingest Sub-project Milestones)

### 🏁 Milestone 1 (M1): H0: Runway Distill Pipeline
- [ ] **Slice 1:** Local Ingest & Binary Identity (Component `fp`) `[WIP]`
- [ ] **Slice 2:** GCP GCS Bucket Initialization & TTL Invariant
- [ ] **Slice 3:** Cloud Sync Automation & State Tracking

### 🌀 Milestone 2 (M2): H1: Transcription & State
- [ ] **Slice 1:** Gemini API (`gemini-2.5-flash`) Audio Ingestion via Babashka
- [ ] **Slice 2:** Native AI Ingestion & Structuring Pipeline
- [ ] **Slice 3:** Ingestion State Ledger & Deduplication Check

### 🪵 Milestone 3 (M3): H2: Org-mode Generation & Inbox Cleanup
- [ ] **Slice 1:** Markdown/Org-mode Payload Formatter
- [ ] **Slice 2:** Emacs Org-Agenda Automation Protocol
- [ ] **Slice 3:** Automated Staging Cleansing & Verification

## Development
This project strictly adheres to **Specification-Driven Development** (ADR 0002). Before invoking `aider`, always verify the current task scope and requirements in `docs/current_slice.md`.
