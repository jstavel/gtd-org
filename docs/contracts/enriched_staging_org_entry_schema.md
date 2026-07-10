# EnrichedStagingOrgEntrySchema

## Description
Output contract of the `write_record` capability in the `orchestrator` actor.
Defines the complete structure of a TODO entry written to `staging.org`,
including metadata properties, Decision drawer, and LOGBOOK.

## Malli Schema
```clojure
[:map
 [:hash            :string]   ; SHA-256 content identity
 [:source          :string]   ; original file path in Downloads
 [:filename        :string]   ; basename, e.g. "2026-07-07 16_53_06.mp3"
 [:headline        :string]   ; "YYYY-MM-DD HH_MM_SS.mp3"
 [:duration-min    :int]      ; ceiling(seconds / 60), for [N min] headline
 [:created         :string]   ; "YYYY-MM-DD"
 [:duration-mmss   :string]   ; "MM:SS"
 [:bitrate         :string]   ; e.g. "128k"
 [:plaud-id        :string]   ; "" (empty, future)
 [:synopsis        :string]   ; "" (empty, user fills)
 [:cleanup         :string]   ; "" (empty, user fills for REJECT)
 [:kind            :string]   ; "Plaud-voice-record"
 [:logbook-entry   :string]   ; timestamped auto-detection log line]]
```

## Rationale
- All fields are strings or integers — no nested maps. The orchestrator formats
  these into an Org-mode text block.
- `headline` is the full filename as it appears in the `* TODO` line.
- `duration-min` is displayed in the headline as `[N min]` — always visible,
  even when drawers are folded. Rounded up (ceiling) so "4m 45s" → 5 min.
- Empty string properties (`:plaud-id`, `:synopsis`, `:cleanup`) are
  placeholder fields the user fills later.
- `:logbook-entry` is a single timestamp string, formatted as
  `[YYYY-MM-DD Day HH:MM] Auto-detected by Staging Worker.`

## Generated Staging.org Entry

```org
* TODO [5 min] 2026-07-07 16_53_06.mp3
  :PROPERTIES:
  :HASH:       a1b2c3d4e5f6...
  :PLAUD-ID:   
  :CREATED:    2026-07-07
  :DURATION:   04:45
  :BITRATE:    128k
  :SOURCE:     ~/Downloads/2026-07-07 16_53_06.mp3
  :SYNOPSIS:   
  :CLEANUP:
  :KIND:       Plaud-voice-record
  :END:
  :DECISION:
  - [ ] INGEST  (Move to assets/objects/UUID/)
  - [ ] REJECT  (Delete from Downloads)
  - [ ] IGNORE  (Keep in Downloads, remove from TODO)
  :END:
  :LOGBOOK:
  - [2026-07-08 Tue 15:30] Auto-detected by Staging Worker.
  :END:
```

## Invariants
- All keys are present — empty strings for not-yet-populated fields, never `nil`.
- `hash` must match `[0-9a-fA-F]{64}`.
- `kind` is always `"Plaud-voice-record"` for `.mp3` files matching the
  `YYYY-MM-DD HH_MM_SS.mp3` naming pattern.
- The generated Org-mode entry is always appended atomically to `staging.org`.
- The original file in `~/Downloads/` is never modified by this operation.

## Implementation Notes
- The `orchestrator` formats the flat map into an Org-mode text block using
  `str` with the template structure from RFC 0001.
- `duration-min` is computed as `(int (Math/ceil (/ total-seconds 60.0)))`.
- The `:DECISION:` drawer is always present with all three checkboxes unchecked.

## References
- RFC 0001: Staging.org Metadata Format & Decision Drawer
- Use Case 0003: Staging Worker Metadata Enrichment
- ADR-0005: Asset Storage Invariants and Human-in-the-Loop Ingestion
