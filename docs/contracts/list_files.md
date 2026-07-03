# List Files Contract Documentation
## Status: Draft

## Input Contract

```clojure
;; InboxDirectoryFilesConfig (example)
[:map
  [:directory_path string?]  ; Required: the absolute directory path where files will be listed.
  [:extensions {:optional true :default [".mp3"]} [:sequential string?]]]
```

## Output Contract

```clojure
;; ListFilesResult (example)
[:map
  [:files [:sequential [:and string? [:re #"^/.*"]]]]]  ; Each file path must be an absolute path.
```
