# PlaudAudioRecordsList

## Description
Output contract of the `extract_records` capability. Contains a structured list of all audio records found on the page.

## Malli Schema
```clojure
[:map
 [:records
  [:sequential
   [:map
    [:name :string]
    [:duration :string]
    [:plaud-id :string]
    [:created :string]]]]]
```

## Rationale
- `records` is always a list (empty if no records are found). Using `:sequential` rather than `:vector` allows lazy sequences, though the implementation typically returns a vector.
- `duration` is a string in `"mm:ss"` format – it is intentionally not parsed into numbers to preserve source fidelity and avoid errors with unusual formats (e.g., `"01:02:03"` for long recordings).
- `created` is a textual date/time in ISO 8601, which is standard and machine‑processable.
- `plaud-id` is the unique identifier of the recording within the Plaud ecosystem.

## Invariants
- `records` is always present – even as an empty list.
- Each item in the list must contain all four keys.
- `plaud-id` must not be empty (enforced by implementation).

## Example
```clojure
{:records
 [{:name "Meeting 2025-01-15.mp3",
   :duration "02:35",
   :plaud-id "rec123",
   :created "2025-01-15T14:00:00Z"}
  {:name "Lecture.mp3",
   :duration "01:02:03",
   :plaud-id "rec456",
   :created "2025-01-14T09:20:00Z"}]}
```

## Implementation Notes
Extraction assumes a valid HTML structure of the records page. If the structure changes, the contract remains the same but the extraction strategy will need to change (and yield an `ExtractionError` if parsing is impossible).
