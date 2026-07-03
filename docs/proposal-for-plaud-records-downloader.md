## Proposed Capabilities for "Plaud Web Audio Records List Retrieval"

Based on the use-case verbs and the methodology in `CONTRIBUTING.md`, the following **business-level capabilities** are identified. Each is implementation‑independent and will later be coupled with a Malli contract.

---

### 1. `connect-to-plaud-browser`
**Description**  
Establish a session with a Chromium browser via its remote‑debugging endpoint.

**Inputs**  
- `remote-debug-port` (integer, default 9222)  
**Outputs**  
- `browser-session` (opaque connection handle)  
**Precondition**  
- Browser is running with `--remote-debugging-port=<port>`  
**Postcondition**  
- A valid CDP (Chrome DevTools Protocol) session is open.  

---

### 2. `fetch-audio-records-page`
**Description**  
Navigate to `https://web.plaud.ai`, click the “All Files” link, and return the rendered HTML of the records list page.  
*Includes waiting for the target element and handling navigation timeouts.*

**Inputs**  
- `browser-session` (from capability 1)  
- `base-url` (string, `"https://web.plaud.ai"`)  
**Outputs**  
- `html-content` (string)  
**Precondition**  
- User is authenticated (manual login already performed).  
**Postcondition**  
- The records page HTML is available for extraction and archiving.  

---

### 3. `extract-audio-records-list`
**Description**  
Parse the HTML of the records page and produce a structured list of audio records conforming to the shared Malli schema.

**Inputs**  
- `html-content` (string from capability 2)  
**Outputs**  
- `records` – sequential map with keys `:name`, `:duration`, `:plaud-id`, `:created` (see contract in use‑case)  
**Postcondition**  
- The returned list captures all audio records displayed on the page.  

---

### Cross‑Cutting Concern: Archiving
The use‑case requires **saving the HTML page locally** for audit. This is handled separately as a cross‑cutting activity (`archive-html`), not a core capability. It will be applied to all fetch operations.

---

### Traceability to Use‑Case Steps

| Use‑Case Step                                | Capability                   |
|----------------------------------------------|------------------------------|
| Connect to browser (port 9222)               | `connect-to-plaud-browser`   |
| Navigate to web.plaud.ai & click “All Files” | `fetch-audio-records-page`   |
| Save HTML page (archive)                     | cross‑cutting                |
| Parse HTML and return list                   | `extract-audio-records-list` |

This decomposition follows the methodology: capabilities are *what* the system does, not *how*. Contracts will be defined next, then the state machine and workflows.

### design contracts for the given capabilities


``` reasoning
We need to design Malli schemas (contracts) for the three capabilities identified: connect-to-plaud-browser, fetch-audio-records-page, extract-audio-records-list. Also consider cross-cutting archive if needed but not as capability. Follow CONTRIBUTING.md: contracts define inputs, outputs, validation rules. Use Malli syntax. Ensure schemas are independent and precise.

Also note the use case already provides a schema for the output list of records. We should reference that or reproduce it.

We'll produce a markdown file with contracts per capability.
```

## Contracts for Plaud Web Audio Capabilities

Each capability is defined by its input and output schemas using Malli.  
All schemas are stored in `contracts/plaud_web.clj` (or equivalent).

---

### 1. `connect-to-plaud-browser`

**Input**  
```clojure
[:map
 [:remote-debug-port {:optional true} :int]]
```
- `remote-debug-port` defaults to `9222`; validated as integer.

**Output**  
```clojure
[:map
 [:session-id :string]
 [:browser-version :string]]
```
- The session handle is represented as a string identifier (e.g., WebSocket URL).  
- `browser-version` is returned for logging/audit.

**Error (failure)**  
```clojure
[:map
 [:error :keyword]     ;; ::connection-failed , ::timeout
 [:message :string]]
```

---

### 2. `fetch-audio-records-page`

**Input**  
```clojure
[:map
 [:session-id :string]
 [:base-url {:optional true} :string]   ;; default "https://web.plaud.ai"
 [:timeout-ms {:optional true} :int]]   ;; default 30000
```

**Output**  
```clojure
[:map
 [:html-content :string]
 [:page-url :string]                    ;; final URL after navigation (for audit)
 [:timestamp :string]]                  ;; ISO 8601 of fetch
```

**Error**  
```clojure
[:map
 [:error :keyword]    ;; ::navigation-failed , ::element-not-found , ::timeout
 [:message :string]
 [:pag-html :string]]  ;; saved for debugging (optional)
```

**Validation rules**  
- `session-id` must be a non‑empty string.  
- `html-content` must not be empty after navigation.  
- Archive is a cross‑cutting concern (not part of this contract).

---

### 3. `extract-audio-records-list`

**Input**  
```clojure
[:map
 [:html-content :string]
 [:parse-strategy {:optional true} :keyword]]  ;; e.g., :css-selectors, :xpath
```

**Output**  
```clojure
[:map
 [:records
  [:sequential
   [:map
    [:name :string]
    [:duration :string]           ;; formatted "MM:SS"
    [:plaud-id :string]
    [:created :string]]]]]         ;; ISO 8601 or "YYYY-MM-DD HH:MM"
```

**Error**  
```clojure
[:map
 [:error :keyword]            ;; ::parse-failure , ::invalid-html
 [:message :string]
 [:partial-records
  {:optional true}
  [:sequential
   [:map
    [:name {:optional true} :string]
    [:duration {:optional true} :string]
    [:plaud-id {:optional true} :string]
    [:created {:optional true} :string]]]]]
```

**Validation rules**  
- `html-content` must be valid HTML (parsing should not throw).  
- All records must have non‑empty `:name` and `:plaud-id`.  
- `:duration` must match `\d{2}:\d{2}` or `\d+:\d{2}:\d{2}` (HH:MM:SS).  
- `:created` must be parseable as a date/time (unless null allowed? – not per use case).

---

### Cross‑Cutting Concern: Archiving

Not a capability, but a contract for the archiving *action* can be defined:

```clojure
[:map
 [:html-content :string]
 [:page-url :string]
 [:timestamp :string]
 [:output-path :string]]    ;; deterministic filename
```

This is applied as a side‑effect by the workflow, not a separate capability.  

