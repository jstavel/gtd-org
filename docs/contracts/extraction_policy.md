# ExtractionPolicy

## Description
Input contract for `extract_records`. Allows selection of the HTML parsing strategy.

## Malli Schema
```clojure
[:map
 [:parse-strategy {:optional true} :keyword]]
```

## Rationale
Parsing is intentionally parameterised via an optional strategy. As the page structure may change in the future, a different parser (e.g., `:cheerio`, `:regex`, `:html-parser`) can be introduced. The default strategy is `:plaud-web-default`. The map wrapper allows expansion with additional configuration (selectors, timeouts).

## Invariants
- When the strategy is not provided, the default is used.

## Example
```clojure
{:parse-strategy :plaud-web-default}
```

## Implementation Notes
Currently the only valid strategy is the default, but the map structure does not block adding others.
