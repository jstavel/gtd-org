# FetchPageConfig

## Description
Input contract for `fetch_audio_records_page`. Combines the session identifier from `BrowserConnection` with optional navigation parameters.

## Malli Schema
```clojure
[:map
 [:session-id :string]
 [:base-url {:optional true} :string]
 [:timeout-ms {:optional true} :int]]
```

## Rationale
`base-url` is optional to avoid repeating the domain – the default value is `https://web.plaud.ai`. `timeout-ms` allows customisation of the page load timeout; a default is taken from configuration. Both parameters improve testability and flexibility without code changes.

## Invariants
- When omitted, optional keys have fixed defaults in the implementation.
- `session-id` must not be empty.

## Example
```clojure
{:session-id "abc123",
 :base-url "https://web.plaud.ai",
 :timeout-ms 30000}
```

## Implementation Notes
Passed directly to the browser automation library; separation from the `BrowserConnection` contract simplifies composition using the output of the previous step.
