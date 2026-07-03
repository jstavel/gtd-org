# SavePageError

## Description

Error contract for `save_page_html`.

## Malli Schema

```clojure
[:map
 [:error :keyword]
 [:message :string]]
```

## Rationale

Identical format to other error contracts (except for the optional extension in `FetchPageError`). Uniformity simplifies high‑level processing. Error states include, for example, disk-full or permission issues.

## Invariants

- `error` and `message` are always present.
- Output is mutually exclusive with `SavePageResult`.

## Example

```clojure
{:error :disk-full, :message "Cannot write HTML file: no space left on device."}
```

## Implementation Notes

In the future the contract could also carry the path where the write was attempted (`attempted-path`), but this is not currently needed.
