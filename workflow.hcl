# Actors
actor "watcher" {
    capabilities = ["scan_directory", "list_files", "identify_plaud_record", "prepare_final_record"]
    capability "scan_directory" {
	# Contract refers to the application configuration for the directory to scan.
	contract = "InboxDirectoryConfig"
    }
    capability "list_files" {
	# Contract refers to the application configuration for filtering files (e.g., by extension).
	contract = "InboxDirectoryFilesFilterConfig"
    }
    capability "identify_plaud_record" {
	# Contract represents the Malli schema for the enriched metadata input needed to identify a Plaud record.
	contract = "EnrichedMetadataForIdentificationSchema"
    }
    capability "prepare_final_record" {
	# Contract represents the Malli schema for the identified record input needed to prepare the final structure.
	contract = "IdentifiedRecordForPreparationSchema"
    }
}

actor "metadata_provider" {
    capabilities = ["calculate_hash", "extract_metadata"]
    capability "extract_metadata" {
	# Contract represents the schema for the file record data structure to extract metadata from.
	contract = "FileRecordForMetadataExtractionSchema"
    }
    capability "calculate_hash" {
	# Contract represents the schema for the file content/path required for hash calculation.
	contract = "FileContentForHashingSchema"
    }
}

# Add the Orchestrator actor as defined in the use case.
actor "orchestrator" {
    capabilities = ["check_duplicate", "write_record"]
    capability "check_duplicate" {
	# Contract represents the schema for the hash used to check for duplicates.
	contract = "RecordHashInputSchema"
    }
    capability "write_record" {
	# Contract represents the schema for the full record structure to be written to staging.org.
	contract = "StagingOrgRecordSchema"
    }
}

actor "plaud_downloader" {
  capabilities = [
    "connect_to_browser",
    "fetch_audio_records_page",
    "save_page_html",
    "extract_records",
    "fetch_records_via_api",
    "fetch_temp_url",
    "download_file_from_url",
    "download_recording",
    "download_all_new_recordings"
  ]

  capability "connect_to_browser" {
    input_contract  = "BrowserConnectionConfig"
    output_contract = ["BrowserConnection", "ConnectionError"]
  }

  capability "fetch_audio_records_page" {
    input_contract  = ["BrowserConnection", "FetchPageConfig"]
    output_contract = ["HtmlPage", "FetchPageError"]
  }

  capability "save_page_html" {
    input_contract  = "HtmlPage"
    output_contract = ["SavePageResult", "SavePageError"]
  }

  capability "extract_records" {
    input_contract  = ["HtmlPage", "ExtractionPolicy"]
    output_contract = ["PlaudAudioRecordsList", "ExtractionError"]
  }

  capability "fetch_records_via_api" {
    input_contract  = "BrowserConnection"
    output_contract = ["PlaudAudioRecordsList", "ApiFetchError"]
  }

  capability "fetch_temp_url" {
    input_contract  = ["BrowserConnection", "PlaudRecordId"]
    output_contract = ["TempUrl", "TempUrlError"]
  }

  capability "download_file_from_url" {
    input_contract  = ["DownloadUrl", "OutputPathConfig"]
    output_contract = ["DownloadResult", "DownloadError"]
  }

  capability "download_recording" {
    input_contract  = ["BrowserConnection", "PlaudAudioRecord", "OutputDirectoryConfig"]
    output_contract = ["DownloadResult", "DownloadError"]
  }

  capability "download_all_new_recordings" {
    input_contract  = ["BrowserConnection", "PlaudAudioRecordsList", "OutputDirectoryConfig"]
    output_contract = ["BatchDownloadResult", "DownloadError"]
  }
}

# Interceptors (Cross-Cutting Concerns)
interceptor "logging" {
    description = "Logs all navigation steps, page load times, and extraction results."
    contracts   = [
        "BrowserConnectionConfig",
        "NavigationConfig",
        "SavePageHtmlSchema",
        "PlaudAudioRecordsListSchema"
    ]
}

interceptor "audit_archiving" {
    description = "Ensures saved HTML files are named deterministically with timestamps to allow auditing of the retrieved state."
    contracts   = [
        "SavePageHtmlSchema"
    ]
}

# Workflow
workflow "records_ingestion" {
    stage "init" {
        description = "start of the application"
        owner = "user"
        next_stages = ["monitor"]
    }

    stage "monitor" {
        description = "Watcher scans the directory and passes files."
        owner       = "actor.watcher"
        next_stages = ["metadata_enrichment"]
        # The watcher initiates metadata enrichment for files it finds.
    }

    stage "metadata_enrichment" {
        description = "Metadata provider enriches data."
        owner       = "actor.metadata_provider"
        next_stages = ["record_preparation"] # After enrichment, the record goes back to the watcher for final preparation.
    }

    stage "record_preparation" {
        description = "Watcher identifies Plaud records and prepares the final structure for orchestration."
        owner = "actor.watcher"
        next_stages = ["record_staging"] # The prepared record is then sent for staging.
    }

    stage "record_staging" {
        description = "Orchestrator writes the prepared record into the staging area."
        owner = "actor.orchestrator"
        next_stages = [] # This could be the final stage, or point to further processing.
    }
}

workflow "plaud_records_downloading" {
    stage "init" {
        description = "Start of the downloading process"
        owner       = "user"
        next_stages = ["connect_to_browser"]
    }

    stage "connect_to_browser" {
        description = "Plaud downloader connects to browser via CDP."
        owner       = "actor.plaud_downloader"
        next_stages = ["fetch_records_via_api"]
    }

    stage "fetch_records_via_api" {
        description = "Fetches all Plaud audio records via API (/file/simple/web). Falls back to HTML extraction (fetch_page -> save_page -> extract_records) on API failure."
        owner       = "actor.plaud_downloader"
        next_stages = ["download_all_new_recordings"]
    }

    stage "download_all_new_recordings" {
        description = "Batch downloads recordings not yet present locally with healthy filesize. Fetches presigned S3 URL per recording, downloads binary via Node.js fetch, deduplicates by filesize (API filesize is source of truth)."
        owner       = "actor.plaud_downloader"
        next_stages = []
    }
}
