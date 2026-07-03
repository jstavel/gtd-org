### navrhni capabilities pro dany usecase
dodrzuj pravidla v CONTRIBUTING.md


``` reasoning
We need to propose capabilities for the given use case "Plaud Web Audio Records List Retrieval". Following the methodology: Capabilities are business-level operations, implementation independent. They are extracted from use case verbs. The use case describes steps: connect, navigate, save main page, navigate to records, save records page, extract records, return list.

We need to define capabilities that are high-level, not overly granular. Consider the actor (PlaudDownloader) and the user. Common capabilities: "connect-to-browser", "navigate-to-page", "save-html", "extract-records", etc. But we want to align with the spec-driven approach.

From the use case, the main goal is to retrieve a list of audio records from Plaud web. So one capability could be "retrieve-plaud-audio-records-list". But maybe we break down into:

- establish-browser-session
- navigate-to-records-page
- extract-records-list
- save-records-page

But "save-records-page" seems like a cross-cutting concern (archiving). The use case says saved HTML is for auditing. Could be a separate cross-cutting capability.

We should follow the pattern: capabilities are operations that can be implemented independently. They answer "What does the system do?".

Let's propose:

1. connect-to-browser - establishes connection to remote debugger
2. navigate-to-url - navigates browser to a given URL
3. wait-for-and-click-element - waits for an element and clicks it
4. save-page-html - saves current page HTML to file
5. parse-records-list - parses HTML to extract records
6. return-records - returns structured list

But some of these seem too low-level. Perhaps combine into higher-level capabilities: "browser-automation" or "web-scraping". However, the methodology prefers capabilities that are business-level. "connect-to-browser" is infrastructure. Maybe we reframe:

- authenticate-session (but user does that manually)
- retrieve-audio-records-list (main capability that orchestrates sub-steps)
- archive-html (cross-cutting)

Or we could have:

- connect-to-plaud-web
- fetch-records-list-page
- extract-records-from-page

Let's think: The use case is about retrieving the list. So the primary capability is "retrieve-plaud-audio-records-list". But the methodology says capabilities are business-level operations like "scan-directory", "extract-metadata", "identify-plaud-record", "write-record". So "retrieve-plaud-audio-records-list" is appropriate. However, it might be composed of smaller capabilities that could be reused. For example, "fetch-web-page", "parse-html-document", "extract-data-by-selector". But those are generic web scraping capabilities, not specific to Plaud.

Given the project context (gtd-org), it's about file management and metadata extraction. This use case is about getting data from Plaud web. So capabilities should be Plaud-specific.

I'll propose:

- connect-to-plaud-browser
- fetch-audio-records-page
- extract-audio-records-list
- archive-records-page

But "connect" is a prerequisite. Maybe we should define capabilities as per the steps in the use case flow.

Let's list the flow actions:

- Connect to browser (via remote debugging)
- Navigate to web.plaud.ai
- Save main page HTML (maybe not necessary for the capability? but use case includes it)
- Click "All Files" link
- Save records page HTML
- Parse records page
- Return list

I'd combine navigation and clicking into one capability "navigate-to-audio-records-page". Then "archive-records-page" for saving HTML. "extract-audio-records-list" for parsing.

Thus capabilities:

1. connect-to-browser (or "establish-browser-connection")
2. navigate-to-audio-records-page
3. archive-records-page (cross-cutting? but not a business capability)
4. extract-audio-records-list

Capabilities should be implementation independent. "connect-to-browser" could be abstracted as "establish-session". "navigate-to" is like "navigate-to-url". But these are still at the right level.

Given the CONTRIBUTING.md example capabilities: "scan-directory", "extract-metadata", "identify-plaud-record", "write-record". Those are high-level. So propose:

- establish-browser-session
- retrieve-audio-records-list-page
- parse-audio-records-list
- archive-html-page (cross-cutting, but maybe not a capability in the business sense; could be a cross-cutting concern)

I'll keep it simple and propose three capabilities:

- connect-browser
- fetch-records-page
- extract-records

But "fetch-records-page" includes navigation and saving? Might be too broad.

Better to follow the exact flow: The use case is "Plaud Web Audio Records List Retrieval". So the primary capability is "retrieve-plaud-audio-records-list". Under that, we can define sub-capabilities or contracts. But per methodology, capabilities should be defined at a level that can be independently contracted.

Let's produce a structured response: List capabilities with descriptions and potential contracts (inputs/outputs). Ensure they are implementation independent.

I'll propose:

1. **Connect to Plaud Web Browser**
   - Input: remote debugging port (default 9222)
   - Output: browser session handle
   - Precondition: browser is running with remote debugging enabled
   - Postcondition: session established

2. **Fetch Audio Records List Page**
   - Input: browser session handle, URL (https://web.plaud.ai)
   - Output: HTML content of records page
   - Actions: navigate, click "All Files", wait for page load
   - Postcondition: HTML is retrieved

3. **Extract Audio Records**
   - Input: HTML content
   - Output: list of audio records (name, duration, plaud-id, created)
   - Postcondition: structured list returned

4. **Archive HTML Page** (cross-cutting)
   - Input: HTML content, file path
   - Output: file saved
   - Postcondition: HTML file stored

But capability 4 is not strictly necessary for the core business; it's for audit. Could be a cross-cutting concern. The use case specifies "save HTML" as a step. So it's part of the capability? Possibly we include it in "fetch" or make separate.

Given the methodology, cross-cutting concerns are separate. So we can define capabilities that are business-level, and then cross-cutting concerns apply to all.

I'll define:

Capabilities:
- connect-to-browser
- retrieve-audio-records-list-page
- parse-audio-records-list

And cross-cutting concern: archiving (logging, saving HTML) applies to the retrieve step.

But to be consistent with the use case, I'll include a capability "archive-html-page" but note it's cross-cutting.

Let's write the answer.
```

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

---

These contracts are the foundation for state machine, workflows, and tests.  
Next step: define stages and state machine.

### can be used session-id? ie. whether playwright (implementation detail) code can join already running browser session


``` reasoning
We need to analyze the question: "can be used session-id? ie. whether playwright (implementation detail) code can join already running browser session"

The user is asking about the contract design for `connect-to-plaud-browser`. They question whether the output should be a `session-id` as a string, and whether Playwright (implementation detail) can join an already running browser session. This is about abstraction vs implementation detail.

The contract as proposed uses a `:session-id` string and `:browser-version`. The user is concerned that this might be too tied to a specific implementation (e.g., a Playwright CDP session). However, the methodology says capabilities should remain implementation independent. So the contract should not expose Playwright-specific details.

The output should be an abstract representation of a browser session, not a CDP WebSocket URL. The `:session-id` is okay if it's just an opaque identifier; but the user is asking whether it can be used, i.e., whether it's reusable. The real question is: should the contract allow joining an existing session? The use case says "The user has started a Chromium-based browser with remote debugging enabled". The script connects to it. But does the script need to join an existing session after connection? Typically, the script connects once and uses the session. The contract output could be a map with `:session` as an opaque handle. The internal implementation can use Playwright's `browser` object. The contract should not mandate that the session can be joined later by another piece of code.

The user might be implying: if we use `session-id`, it suggests a string that can be passed around, but Playwright's CDP connection is not a simple string; it's an object. However, the contract can hide that. The output `:session-id` is fine as an identifier if we later need to reference it; but the actual handle is internal.

Better: change output to `[:session :any]` or `:map` with an opaque type. But Malli doesn't have opaque types. Usually we use a `:string` for an identifier. The question is: should we model it as an opaque handle that is not serializable? For testing and contract validation, a string is fine. However, the user might be concerned about the ability to resume a session after a crash. That's an implementation detail.

The answer: The contract is abstract; `:session-id` is an opaque identifier that the implementation maps to an actual browser session object. Whether Playwright can join an existing session is an implementation decision; the contract doesn't prescribe it. The contract only states that the output includes some identifier for the session, which can be used by subsequent capabilities (like `fetch-audio-records-page`) to reference that same session. The implementation can store the Playwright `browser` object in a map keyed by this id. So yes, it works.

I'll respond concisely, clarifying that the contract remains implementation-independent and that the session-id is an opaque reference.
```

Your concern is valid: the contract should not expose implementation specifics (e.g., Playwright’s internal connection).  

The `:session-id` is an **opaque, abstract identifier** – not a CDP WebSocket URL.  
It allows the workflow to refer to the same browser session across multiple capabilities *without* leaking Playwright detail.  

Internally, the implementation can map this id to the actual Playwright `browser` object (or any other handle).  
Whether the session can be "joined" later by another agent is an **implementation choice**; the contract only guarantees that the identifier uniquely identifies the live session for the duration of the use case.

Thus, the design is sound and implementation‑agnostic.

### please translate your last answer into czech language


``` reasoning
We need to translate the previous assistant response about session-id into Czech. The user asked: "can be used session-id? ie. whether playwright (implementation detail) code can join already running browser session". The assistant's response was in English. Now the user requests to translate that answer into Czech. We'll provide the translated answer.
```

Vaše obava je oprávněná: kontrakt by neměl odhalovat implementační detaily (např. interní Playwright připojení).

`:session-id` je **abstraktní, neprůhledný identifikátor** – nikoli WebSocket URL CDP.  
Umožňuje workflow odkazovat se na stejnou relaci prohlížeče napříč více schopnostmi, aniž by unikly Playwright detaily.

Interně si implementace může toto ID namapovat na skutečný Playwright `browser` objekt (nebo jakýkoli jiný handler).  
Zda se k relaci může později připojit jiný agent, je **volba implementace**; kontrakt pouze garantuje, že identifikátor jednoznačně označuje živou relaci po dobu trvání use case.

Návrh je tedy korektní a nezávislý na implementaci.

### aha, takze pri implementaci vznikne nejake centralni uloziste browser connections. uz to zacinam chapat - kdyby byl kontrakt s 'playwright.page', tak to je implementacni detail


``` reasoning
We need to respond to the user's insight in Czech, confirming their understanding and elaborating on the concept of central storage for browser connections. The user is acknowledging that the contract should avoid implementation details like Playwright.Page, and that a central registry of connections would be used internally. Provide a concise, supportive answer in Czech.

The assistant's response should be clear and helpful, reinforcing the methodology.
```

Přesně tak.  

Kontrakt definuje **rozhraní**, nikoli **implementaci**.  
`:session-id` je abstraktní klíč, který na straně implementace může být namapován na libovolný interní objekt (např. Playwright `Browser`, `BrowserContext` nebo WebSocket spojení).  

Ano, při implementaci vznikne **centrální registr aktivních relací** – například mapa `{session-id -> browser-instance}`.  
Tento registr je však čistě interní detail, který do kontraktu nepatří.  

Díky tomu zůstávají capability a workflow **nezávislé na konkrétní knihovně** (Playwright, Puppeteer, Selenium…) a lze je v budoucnu snadno vyměnit.  

Správně jste pochopil princip – kontrakty popisují *co*, ne *jak*.

### znas metodologii SOLID? pokud ano, srovnej mi to s aktualnim CONTRIBUTING.md


``` reasoning
We need to compare SOLID principles with the methodology in CONTRIBUTING.md. SOLID is from OOP, but we can map to the spec-driven approach. Answer concisely, in Czech as requested? The user asked in Czech, but previous answer was in Czech, so respond in Czech.

We'll list SOLID and map each to the methodology's concepts.

- Single Responsibility: Capabilities and contracts separate concerns.
- Open/Closed: Capabilities are defined by contracts, open for extension via new contracts, closed for modification? The methodology emphasizes stable contracts.
- Liskov Substitution: Not directly relevant (no inheritance). Contracts define strict schemas.
- Interface Segregation: Capabilities are small, focused. Contracts are specific.
- Dependency Inversion: Implementation depends on contracts (specs), not vice versa.

The methodology has some similarities but also differences: it focuses on use cases, state machines, invariants. It's more about specification-driven development than OOP design.

Provide a comparison.
```

Ano, znám SOLID (Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, Dependency Inversion). Níže je srovnání s metodologií z `CONTRIBUTING.md` (spec‑driven development).

| SOLID princip | Jak se odráží v metodologii |
|---------------|-----------------------------|
| **S**ingle Responsibility | Každá capability má jednu odpovědnost (např. `fetch-audio-records-page`). Kontrakty definují přesný vstup/výstup bez vedlejších efektů. Workflow odděluje orchestrací. |
| **O**pen/Closed | Capability jsou otevřené pro rozšíření (lze přidat nové capability pro nové use‑casy) a uzavřené pro změny (kontrakty jsou stabilní; implementace se mění, ale rozhraní zůstává). |
| **L**iskov Substitution | Není přímo relevantní – nepoužíváme dědičnost. Každý kontrakt definuje přesný tvar dat; substitutabilita je zajištěna validací Malli schématem. |
| **I**nterface Segregation | Schopnosti jsou malé, zaměřené (např. `connect-to-plaud-browser` vs. `extract-audio-records-list`). Žádná capability nemá „tlusté“ rozhraní. |
| **D**ependency Inversion | **Základní princip metodologie.** Implementace závisí na specifikacích (kontrakty, workflow, invarianty), nikoli naopak. Doménový model vzniká **ze specifikací**, není předem navržen. |

### Shrnutí podobností a rozdílů

- **Podobnosti:** Oba přístupy usilují o oddělení odpovědností, stabilní rozhraní a závislost na abstrakcích.
- **Rozdíly:** SOLID je primárně určen pro návrh tříd v OOP. Tato metodologie je **specifikací řízená** – zdrojem pravdy jsou use‑casy, kontrakty (Malli), stavový automat a invarianty. Implementace je až downstream artifact. Navíc přidává **explicitní traceabilitu na kanban** a **AI‑asistentům** definuje, jak konzumovat specifikace.

Metodologie jde dál než SOLID: zahrnuje formální stavové modely, generování testů ze specifikací a vynucení invariant napříč celým systémem.

### trochu se ztracim v tom, jak definovat dalsi workflow. plaud-downloader je oddeleny skript od ingest. obe workflow mohou byt spojeny pomoci jednoho orchestratoru. V soucasnem workflow.hcl jsou jen 'states'. jakoby to bylo jedno workflow.


``` reasoning
Přemýšlím o tom, co uživatel popisuje. Aktuální `workflow.hcl` definuje jen stavy (states), nikoli samostatná workflow. Uživatel má pocit, že "plaud-downloader" a "ingest" jsou oddělené skripty, ale mohou být spojeny jedním orchestrátorem. Potřebujeme oddělit workflow od stavů.

V metodologii z CONTRIBUTING.md: stavy (stages) definují možné fáze systému. Workflow je cesta přes stavy. Pokud máme dva oddělené procesy (plaud-downloader a ingest), můžeme mít dvě workflow, nebo jedno složené. Ale orchestrátor může řídit obě.

Navrhuji:
- Aktuální `workflow.hcl` obsahuje stavy, ale ne explicitní workflow. Měli bychom přidat definice workflow, které propojují stavy v konkrétním pořadí.
- Pro "plaud-downloader" by workflow bylo např. `init -> connect -> fetch_records -> extract -> archive` (nebo podobně).
- Pro "ingest" by workflow bylo `init -> monitor -> metadata_enrichment -> record_preparation -> record_staging`.
- Orchestrátor pak může spouštět workflow sekvenčně nebo paralelně.

Uživatel chce vědět, jak definovat další workflow. Možná potřebujeme rozšířit `workflow.hcl` o bloky `workflow` vedle `stage`.

Odpovím česky, protože předchozí komunikace byla v češtině. Stručně vysvětlím, jak začlenit workflow do stávající struktury.
```

Rozumím. V `workflow.hcl` dosud máte jen **stavy** (stages), ale chybí explicitní definice **workflow** (cest přes stavy). Podle metodologie je workflow **validní cesta stavovým automatem** – může jich být více, každá pro jiný scénář.

### Návrh: Rozdělit na dvě workflow v jednom `workflow.hcl`

Přidejte bloky `workflow`:

```hcl
# === Workflow 1: Plaud Downloader ===
workflow "plaud_downloader" {
  stages = [
    "init",
    "connect_browser",
    "fetch_records_page",
    "extract_records",
    "archive_page",
    "done"
  ]
}

# === Workflow 2: Ingest Pipeline ===
workflow "ingest" {
  stages = [
    "init",
    "monitor",
    "metadata_enrichment",
    "record_preparation",
    "record_staging",
    "done"
  ]
}
```

Pokud chcete, aby **jeden orchestrátor** řídil obě workflow najednou (např. nejprve stáhne, pak zpracuje), můžete definovat složené workflow:

```hcl
workflow "full_pipeline" {
  stages = [
    "init",
    "connect_browser",
    "fetch_records_page",
    "extract_records",
    "archive_page",
    "monitor",
    "metadata_enrichment",
    "record_preparation",
    "record_staging",
    "done"
  ]
}
```

### Co s aktuálními stavy?

Stávající stavy (`init`, `monitor`, `metadata_enrichment`, atd.) zůstávají – jsou to **stavební bloky**. Workflow je jen **uspořádání** těchto bloků. Propojení mezi nimi (přechody) je definováno v `stage.next_stages`, ale workflow může vybrat pouze podmnožinu nebo specifické pořadí.

To odpovídá metodologii: workflow je cesta, ne seznam všech možných přechodů.

### 

<!-- Local Variables: -->
<!-- gptel-model: deepseek-reasoner -->
<!-- gptel--backend-name: "DeepSeek" -->
<!-- gptel--system-message: "You are a large language model living in Emacs and a helpful assistant. Respond concisely." -->
<!-- gptel--tool-names: nil -->
<!-- gptel--bounds: ((ignore (80 6629) (9192 9765) (12969 16008) (16791 17163) (18024 18500) (19262 20302) (22427 23689)) (response (6631 9140) (9767 12844) (16010 16729) (17165 17839) (18502 19177) (20304 22181) (23691 25218))) -->
<!-- End: -->
