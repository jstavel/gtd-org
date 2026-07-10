# EnrichedRecordSchema

## Description
Input/output contract of the `extract_metadata` capability in `metadata_provider`.
Contains all metadata extracted from a single MP3 file: filename-based date,
ffprobe-extracted duration and bitrate, and content hash.

## Malli Schema
```clojure
[:map
 [:hash          :string]   ; SHA-256, 64 hex chars
 [:created       :string]   ; "YYYY-MM-DD" or "N/A"
 [:duration-mmss :string]   ; "MM:SS" or "N/A"
 [:bitrate       :string]]  ; e.g. "128k" or "N/A"
```

## Rationale
- `hash` is the content identity — always present (computation cannot fail for a
  readable file).
- `created` is extracted from filename via regex `\d{4}-\d{2}-\d{2}`. If the
  filename does not match the expected `YYYY-MM-DD HH_MM_SS.mp3` pattern, value
  is `"N/A"`.
- `duration-mmss` uses MM:SS format — ffmpeg's native representation. If ffprobe
  fails, value is `"N/A"`.
- `bitrate` is extracted from ffprobe audio stream metadata. If unavailable,
  value is `"N/A"`.
- All fields report `"N/A"` on failure rather than omitting the key — the
  staging.org entry is always created, metadata can be enriched later.

## Invariants
- All four keys are always present.
- `hash` must match the regex `[0-9a-fA-F]{64}`.
- `duration-mmss` must match `\d{1,2}:\d{2}` unless it is `"N/A"`.
- `created` must match `\d{4}-\d{2}-\d{2}` unless it is `"N/A"`.

## Example
```clojure
{:hash          "a1b2c3d4e5f6789012345678abcdef01abcdef0123456789abcdef0123456789ab"
 :created       "2026-07-07"
 :duration-mmss "04:45"
 :bitrate       "128k"}
```

## Implementation Notes
- `hash` computed via `fp/sha256-file` (existing `components/fp`).
- `created` via `re-find` on `(fs/file-name path)`.
- `duration-mmss` via `ffprobe -show_entries format=duration`.
- `bitrate` via `ffprobe -show_entries stream=bit_rate`.

## References
- RFC 0001: Staging.org Metadata Format & Decision Drawer
- Use Case 0003: Staging Worker Metadata Enrichment
