### **ADR 0006: Local-First Data Storage & Cloud as Transient Processing Layer**

*   **Status:** Accepted
*   **Date:** 2026-06-03

#### **Context**
The initial architectural documents (`README.md`, `docs/specification.md`) described the `gtd-org` ecosystem with an implication that some primary assets or indices might be stored in cloud storage (GCS `assets/objects/`). However, the fundamental principle of the system is to manage all executive functions and the "Second Brain" locally within the user's `~/org/` directory. The cloud's primary role is to serve as a transient (temporary) layer for processes requiring external resources (e.g., AI transcription, web user interface).

#### **Decision**
All primary data, including Org-mode files, all assets, and their indices (e.g., `assets/objects/UUID/raw/`), will be permanently stored **locally** within the `~/org/` directory structure (or the directory specified by `$ORG_ROOT`).

The cloud infrastructure (Google Cloud Platform) will serve **only as a transient staging area for raw inputs (e.g., audio for transcription) and as a backend for the web user interface.** No permanent, critical user data or asset indices will be stored persistently in cloud storage buckets (GCS).

Specific implications:
*   `$ORG_ROOT/assets/objects/` is a **local directory** containing the permanent asset store after ingestion.
*   The GCS bucket `gtd-org-audio-input-bucket` is a **temporary staging zone** for sending data to AI (Gemini API) and will have a short TTL (e.g., 1 day, per ADR 0001). After processing, data from the cloud will either be deleted or allowed to expire.
*   The web UI will interact with a cloud database for displaying/manipulating data, but this data will always be synchronized with the primary local storage.

#### **Consequences**
1.  **Reduced Cloud Dependence:** Critical data remains under user control and accessible offline.
2.  **Simplified Cloud Data Model:** Cloud storage serves as a "pipe" or "cache," not as the "source of truth."
3.  **Impact on `org-staging`:** The `org-staging` component will reference the **local** `$ORG_ROOT/assets/objects/` for checking existing assets and the **local** `staging.org`. Integration with the GCS `gtd-org-audio-input-bucket` will occur in later phases and will only involve transient synchronization for AI processing, not permanent asset storage.
4.  **Change in understanding of `db` component:** The role of the `db` component shifts towards interacting with transient cloud storage and potentially a cloud database for the web UI, while the primary "database" is the local file system (Org-mode files and `assets/objects/`).
5.  **Re-evaluation of ADR 0001:** ADR 0001 (TTL Invariant) remains valid, but it is now clear that it applies *only* to transient cloud staging buckets, not to permanent asset storage.

---
