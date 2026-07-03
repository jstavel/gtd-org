# HtmlPage

## Description
Output contract of the `fetch_audio_records_page` capability (and input for `save_page_html` and `extract_records`). Represents the retrieved HTML content of a page with metadata.

## Malli Schema
```clojure
[:map
 [:html-content :string]
 [:page-url :string]
 [:timestamp :string]]
```

## Rationale
`html-content` is the raw HTML, used by both extraction and saving. `page-url` captures the final URL (after any redirects) for auditability of the source. `timestamp` is the UTC time of page download in ISO 8601 text format, kept as a string to avoid loss of precision during serialization.

## Invariants
- All three fields are required.
- `timestamp` must be in ISO 8601 format (e.g., `"2025-01-15T10:30:00Z"`).

## Example
```clojure
{:html-content "<html>…</html>",
 :page-url "https://web.plaud.ai/all-files",
 :timestamp "2025-01-15T10:30:00Z"}
```

## Implementation Notes
Consumers (saving, extraction) rely on the HTML being valid and matching the expected structure – this is verified outside the contract.
