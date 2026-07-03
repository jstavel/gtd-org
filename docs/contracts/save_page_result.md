# SavePageResult

## Description
Output contract of the `save_page_html` capability. Describes the result of saving an HTML page to disk.

## Malli Schema
```clojure
[:map
 [:output-path :string]
 [:bytes-written :int]]
```

## Rationale
`output-path` allows locating the saved file. `bytes-written` serves to verify that the written data matches expectations (e.g., the file is not empty). Both values are checkable and aid auditing.

## Invariants
- `bytes-written` ≥ 0.
- `output-path` is an absolute path to an existing file (file existence is not guaranteed by the schema, but by implementation).

## Example
```clojure
{:output-path "/tmp/plaud_records_2025-01-15.html", :bytes-written 12543}
```

## Implementation Notes
The `save_page_html` capability receives an `HtmlPage` and uses its metadata (timestamp) to construct a deterministic filename. The result contains metadata, not the data itself.
