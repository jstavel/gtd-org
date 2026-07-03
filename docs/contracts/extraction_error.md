# ExtractionError

## Description
Error contract for `extract_records`. In addition to standard error information it can carry partially extracted records.

## Malli Schema
```clojure
[:map
 [:error :keyword]
 [:message :string]
 [:partial-records {:optional true}
  [:sequential
   [:map
    [:name :string]
    [:duration :string]
    [:plaud-id :string]
    [:created :string]]]]]
```

## Rationale
`partial-records` allows returning records that were successfully parsed even if an error occurred partway through the page. This is useful for partial recovery and failure analysis. The item type inside matches `PlaudAudioRecordsList`, so they can be processed further. The field is optional – when no records could be extracted, it is clearer to omit the field rather than return an empty list (which could be misleading).

## Invariants
- `error` and `message` are always populated.
- If `partial-records` is present, it is a sequence (possibly empty) of valid maps.

## Example (with partial success)
```clojure
{:error :partial-parse,
 :message "Only 2 out of 5 records were extracted due to unexpected HTML structure.",
 :partial-records
 [{:name "good1.mp3", :duration "01:00", :plaud-id "1", :created "2025-01-15T10:00:00Z"}
  {:name "good2.mp3", :duration "01:30", :plaud-id "2", :created "2025-01-15T10:05:00Z"}]}
```

## Implementation Notes
The implementation must decide when an error is critical (no records) and when partial results can be returned. This contract provides flexibility.
