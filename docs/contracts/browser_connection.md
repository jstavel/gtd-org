# BrowserConnection

## Description
Output contract of the `connect_to_browser` capability. Represents a successfully established connection and carries the session identifier and browser version.

## Malli Schema
```clojure
[:map
 [:session-id :string]
 [:browser-version :string]]
```

## Rationale
`session-id` is a textual identifier obtained from the DevTools session (WebSocket ID) – it is stored as a string because it is an alphanumeric token. `browser-version` is used for logging and diagnostics, not for decision-making, hence a plain string.

## Invariants
- Both keys are always present when the connection succeeds.
- `session-id` must not be empty (enforced by implementation, not the schema).

## Example
```clojure
{:session-id "abc123def456", :browser-version "120.0.6099.130"}
```

## Implementation Notes
This contract is used as input for subsequent capabilities (`fetch_audio_records_page`). Its separation from `ConnectionError` enables clear and safe error handling in client code.
