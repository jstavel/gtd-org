# Architectural Assignment – KAN-0001
## Script for downloading the audio records list from web.plaud.ai

This document follows the specification-driven development methodology defined in [CONTRIBUTING.md](../CONTRIBUTING.md).
It implements the use case described in [0002 – Plaud Web Audio Records List Retrieval](usecases/0002-plaud-web-audio-records-list.md)
and references the contracts and workflows present in the project.

### 1. Goal
Create an automated script that, via Chromium browser (remote debugging), performs:
- connecting to the browser
- navigating to the web application web.plaud.ai
- saving the HTML page with the “All Files” list
- extracting a structured list of audio recordings (name, duration, plaud-id, creation date)

The script will be part of the ClojureScript `plaud-downloader` component and will satisfy the defined contracts (see `docs/contracts/`).

### 2. Technology stack
- **Playwright-js** – library for controlling the browser via the Chrome DevTools Protocol (JS API). It will be called from ClojureScript using interop.
- **ClojureScript** – main language for logic, compiled to JavaScript using **shadow-cljs**.
- **Node.js** – runtime for executing the script.
- **Malli** – data validation according to contracts (optional, if we want to verify outputs).
- **Tests** – using `cljs.test` and the Playwright API for integration scenarios.

### 3. Scope and expected functionality
The script will implement the following capabilities defined in `workflow.hcl`:

1. `connect_to_browser`
   - Input: `BrowserConnectionConfig` (map with an optional port)
   - Output: `BrowserConnection` (session-id, browser-version) or `ConnectionError` (error, message)
   - Implementation: `(connect! {:remote-debug-port 9222})` opens a Playwright `chromium.connectOverCDP()`, obtains session-id and version.

2. `fetch_audio_records_page`
   - Input: `BrowserConnection` + `FetchPageConfig` (session-id, optional base-url, timeout)
   - Output: `HtmlPage` (html-content, page-url, timestamp) or `FetchPageError` (error, message, optional page-html)
   - Implementation: using Playwright, navigates, waits for the page to load, clicks on “All Files”, extracts HTML.

3. `save_page_html`
   - Input: `HtmlPage`
   - Output: `SavePageResult` (output-path, bytes-written) or `SavePageError`
   - Implementation: saves HTML to a file with a deterministic name (using a timestamp), returns path and size.

4. `extract_records`
   - Input: `HtmlPage` + `ExtractionPolicy` (parse-strategy)
   - Output: `PlaudAudioRecordsList` (list of records) or `ExtractionError` (error, message, optional partial-records)
   - Implementation: parses HTML using cheerio (or playwright's `page.evaluate`) according to the chosen strategy. In the current version we will use the default strategy `:plaud-web-default` (CSS selectors).

The script must handle errors:
- `:connection-refused` – browser is not available
- `:not-authenticated` – user is not logged in
- `:timeout` – element not found / page didn't load
- `:partial-parse` – only a portion of records was recognised

### 4. Architecture and integration
The `plaud-downloader` component will be located in the repository at `components/plaud-downloader/`. Contents:
- `src/jstavel/plaud_downloader/interface.clj` – public functions
- `src/jstavel/plaud_downloader/core.cljs` – implementation using Playwright (currently a skeleton with atoms and comments)
- `test/jstavel/plaud_downloader/interface_test.clj` – unit tests

**Playwright interop**:
- ClojureScript will call the Playwright JavaScript API using `js/require` or shadow-cljs npm deps.
- In `deps.edn` the `:npm-deps` will be defined as: `"playwright": "^1.40.0"`.
- Code will use functions like `(let [browser (js/playwright.chromium.connectOverCDP #js{:endpointURL ...})] ...)` and similar.

**Data flow**:
- The user (or caller) calls `(interface/run-download)`, which starts the whole process.
- Each step calls asynchronous operations (Promises) and returns either data or an error map.
- Data are returned as Clojure/JS maps conforming to the contracts.

**Logging and archiving**:
- All steps log to the console (for debugging) and may write to a file.
- Saved HTML has a deterministic name with a timestamp for auditing.

### 5. Contracts
The implementation must follow the specifications in `docs/contracts/`. The main contracts used:
- `BrowserConnectionConfig`, `BrowserConnection`, `ConnectionError`
- `FetchPageConfig`, `HtmlPage`, `FetchPageError`
- `SavePageResult`, `SavePageError`
- `PlaudAudioRecordsList`, `ExtractionError`, `ExtractionPolicy`

The contracts define the expected structures of inputs and outputs.

### 6. Testing and verification
- **Unit tests** for HTML parsing (mock HTML in tests), functions for building output maps.
- **Property tests** verify that the output structure conforms to Malli schemas (if validation is integrated).
- **Manual testing** by running the script with Chromium in remote debugging mode.

### 7. Development environment requirements
- Node.js (version >=18)
- Clojure CLI (`tools.deps`)
- shadow-cljs (`npm install shadow-cljs`)
- Playwright installed in the project (`npx playwright install`)

### 8. Deliverables
- Functional implementation in `components/plaud-downloader` covering all capabilities.
- Test coverage for key scenarios.
- Documentation – file `docs/architecture.md` (or update of current_slice) describing usage and execution.

### 9. Schedule / phases (proposal)
1. Implementation of `connect_to_browser`
2. Implementation of `fetch_audio_records_page`
3. Implementation of `save_page_html`
4. Implementation of `extract_records`
5. Error handling and logging
6. Unit tests
7. Integration tests
8. Documentation
