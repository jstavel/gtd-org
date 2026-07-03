# FetchPageError

## Description
Error contract for `fetch_audio_records_page`. Additionally includes optional HTML output of the page to capture debugging information even on failure.

## Malli Schema
```clojure
[:map
 [:error :keyword]
 [:message :string]
 [:page-html {:optional true} :string]]
```

## Rationale
The optional `page-html` allows capturing the page state at the moment of failure (e.g., login screen instead of the file list), which is critical for diagnostics. The contract otherwise mirrors `ConnectionError`, but extends it with a tunable debugging field for this capability. The field is optional to accommodate situations where HTML cannot be obtained (e.g., timeout before page load).

## Invariants
- If `page-html` is present, it contains the page’s HTML text (possibly empty).
- `error` and `message` are always populated.

## Example (authentication error)
```clojure
{:error :not-authenticated,
 :message "User is not authenticated. Please log in to web.plaud.ai in your browser.",
 :page-html "<html>…login form…</html>"}
```

## Implementation Notes
The implementation should prioritise saving the HTML (if available) for later audit before propagating the error.
