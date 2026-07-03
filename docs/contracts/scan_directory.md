# Scan Directory Contract Documentation
## Status: Draft

## Input Contract

The input schema for the `scan_directory` capability is the `InboxDirectoryConfig` Malli schema. It contains the directory path and any related configuration needed for scanning.

```clojure
;; InboxDirectoryConfig (example)
[:map
 [:directory_path string?]
 [:recursive boolean?]]
```

## Output Contract

The output from the `scan_directory` capability is represented by the `DirectoryScanResult` Malli schema. It describes a list of found files with their properties.

```clojure
;; DirectoryScanResult (example)
[:map
 [:files [:sequential string?]]
 [:scanned_at inst?]]
```

These are sample definitions and should adhere to the specifications defined in the overall project documentation. If the output schema does not yet exist, it is documented here as a proposal and may be refined as the implementation evolves.
