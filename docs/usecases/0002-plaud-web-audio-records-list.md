# Use Case: Plaud Web Audio Records List Retrieval

## 1. Actor
- **User**: Starts the browser with remote debugging enabled and ensures authentication.
- **PlaudDownloader**: Automation script that connects to the browser, navigates the web interface, saves HTML pages, and extracts the list of audio records.

## 2. Trigger (Precondition)
- The user has started a Chromium-based browser with remote debugging enabled on port 9222:
  ```shell
  chromium-browser --remote-debugging-port=9222
  ```
- The user is authenticated to `web.plaud.ai` (e.g., via Google OAuth).

## 3. Flow (Main Scenario)
- **Connect**: The script connects to the browser instance via the remote debugging port 9222.
- **Navigate**: The script navigates to `https://web.plaud.ai`.
- **Save Main Page**: The script saves the HTML content of the main page.
- **Navigate to Records**: The script clicks on the "All Files" link (which contains the number of records).
- **Save Records Page**: The script saves the HTML content of the page containing the list of audio records to a local file.
- **Extract Records**: The script parses the HTML content of the records list page.
- **Return List**: The script returns a structured list of audio records conforming to the following Malli schema:
  ```clojure
  [:sequential
   [:map
    [:name string?]
    [:duration string?]
    [:plaud-id string?]
    [:created string?]]]
  ```

## 4. Alternative Flows (Exceptions)
- **G01: Connection Failed**
  - If the script cannot connect to port 9222, it terminates with an error: "Could not connect to browser on port 9222. Is Chromium running with remote debugging enabled?"
- **G02: Authentication Required**
  - If the script detects that the user is not logged in (e.g., redirected to a login page), it terminates with an error: "User is not authenticated. Please log in to web.plaud.ai in your browser."
- **G03: Navigation/Element Timeout**
  - If the "All Files" link or the records list fails to load within the timeout period, the script saves the current HTML page for debugging and terminates with an error.

## 5. Postconditions (Invariants)
- The HTML content of the records list page is successfully stored in a local file.
- A structured list of presented audio records is returned.

## 6. Compliance / Audit Requirements (Archiving)
- All navigation steps, page load times, and extraction results must be logged.
- Saved HTML files must be named deterministically (e.g., with timestamps) to allow auditing of the retrieved state.
