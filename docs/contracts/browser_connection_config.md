# BrowserConnectionConfig
## Description
Input contract passed to the `connect_to_browser` capability. Contains configuration for establishing a connection to the browser via Chrome DevTools protocol.

## Malli Schema
```clojure
[:map
 [:remote-debug-port {:optional true} :int]]
```

## Rationale
The port is optional – the standard port `9222` is used by default. The value is an integer because ports are strictly numeric. Using a map wrapper allows future extension with additional connection parameters (e.g., `host`, `timeout`).

## Invariants
- If no port is provided, the implementation defaults to `9222`.
- The map may contain only the declared keys (validation is not enforced by the schema but is expected from the implementation).

## Example
```clojure
{:remote-debug-port 9222}
```

## Implementation Notes
The configuration is minimal because the only variable parameter is the port. Any future extensions (e.g., authentication) can be added without breaking backward compatibility.
