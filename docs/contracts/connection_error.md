# ConnectionError

## Description
Error contract for `connect_to_browser`. Carries an error identifier and a human-readable message for debugging.

## Malli Schema
```clojure
[:map
 [:error :keyword]
 [:message :string]]
```

## Rationale
`:error` as a keyword allows categorization and machine processing (e.g., `:connection-refused`). `:message` is a readable text suitable for logs and user output. Both elements are required – an error without categorization or a description loses diagnostic value.

## Invariants
- `:error` must be one of a predefined set of keywords (enforced by implementation).
- This contract is never returned together with `BrowserConnection` – they are mutually exclusive results.

## Example
```clojure
{:error :connection-refused,
 :message "Could not connect to browser on port 9222. Is Chromium running with remote debugging enabled?"}
```

## Implementation Notes
The error contract is used by all capabilities, often with error identifiers specific to the context. Pairing output as `BrowserConnection` / `ConnectionError` is a standard result handling pattern.
