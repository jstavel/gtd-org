# Use Case: Prepare for Ingestion of Plaud voice record into gtd-org domain
- **ID:** plaud-voice-record-input-into-gtd-org

### 1. Actor
  - Watcher
  - Metadata Provider
  - Orchestrator
  
### 2. Trigger (Precondition)
  - A new Plaud voice record file appears in the predefined watched directory (e.g., `~/Downloads`).
  - The system has access to the disk and the watched directory.

### 3. Flow (Main Scenario)
1. The watcher reads the directory and lists just *.mp3 files
2. The watcher asks metadata providers for metadata for each file
3. The provider provides properties: `hash` and `kind` (with all additional ones)
4. The watcher identifies the file as a Plaud voice record (based on file type/name/metadata).
5. The watcher prepares record for orchestrator
6. The orchestrator writes the record into staging.org file

### 4. Alternative Flows (Výjimky)
- *What if the file already exists (based on hash)?* The system skips ingestion and logs information about the duplicate.
- *What if the file is not a Plaud voice record?* The system ignores the file and logs information.
- *What if the system does not have access to the file/directory?* The system logs an error and retries later.

### 5. Postconditions (Invariants)
- The record exists in `staging.org` file.
- A record with metadata and the file's hash is created in the staging area.

### 6. Compliance / Audit Requirements (Archivace)
- Every ingestion attempt (successful or failed) must be logged to `journal.log` with a timestamp, file hash, and operation result.
