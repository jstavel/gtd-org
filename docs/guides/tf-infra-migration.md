# GCP Bucket and Terraform State Migration Guide

Migrating a GCP bucket between regions (from `EUROPE-CENTRAL2` to `EUROPE-WEST3`) with Terraform while preserving data is a sensitive operation. **Directly changing the `location` in your Terraform configuration and running `apply` will destroy the old bucket and its data!** Follow this detailed plan to minimize risk and downtime.

---

### Prerequisites
* Installed and configured `gcloud CLI`
* Installed `gsutil`
* Installed `terraform`
* Access to the `gtd-org-2026` project with sufficient permissions

### Critical Warnings
* **Always back up `main.tf` and the current `tfstate` before every phase!**
* **Test in a non-production environment first.**
* Plan for temporary service unavailability during the migration.

---

### Phase 0: Preparation and Backup

1.  **Back up the current Terraform configuration:**
    ```bash
    cp main.tf main.tf.bak
    ```
2.  **Back up the current Terraform state:**
    ```bash
    terraform state pull > backup.tfstate.json
    ```
3.  **Inform users/applications** about planned maintenance.
4.  **Block writes to the old `audio_input` bucket:** Prevents new data from being created during migration.
    ```bash
    gcloud storage buckets update gs://gtd-org-audio-input-bucket --set-iam-policy-from-json=no-writes-policy.json
    ```

---

### Phase 1: Migrating Terraform State Backend

1.  **Create a new GCS bucket for Terraform state in `EUROPE-WEST3`:**
    ```bash
    gcloud storage buckets create gs://gtd-org-terraform-state-west --location=EUROPE-WEST3
    ```
2.  **Update `main.tf`** to reflect the new backend and define provider aliases:
    ```terraform
    terraform {
      backend "gcs" {
        bucket  = "gtd-org-terraform-state-west"
        prefix  = "terraform/state"
      }
    }

    provider "google" {
      project = "gtd-org-2026"
      region  = "europe-central2"
      alias   = "central"
    }

    provider "google" {
      project = "gtd-org-2026"
      region  = "europe-west3"
      alias   = "west"
    }
    ```
3.  **Copy existing `tfstate` files:**
    ```bash
    gsutil -m cp -r gs://gtd-org-terraform-state/terraform/state/* gs://gtd-org-terraform-state-west/terraform/state/
    ```
4.  **Initialize Terraform with state migration:**
    ```bash
    terraform init -migrate-state
    ```

---

### Phase 2: Migrating the Data Bucket

1.  **Create the new data bucket in `EUROPE-WEST3` via Terraform:**
    * Add a new `resource "google_storage_bucket" "audio_input_west"` using `provider = google.west`.
    * Update IAM members and notifications to point to the new resource.
    * Run `terraform plan` and `apply` to create the empty new bucket.
2.  **Migrate data:**
    ```bash
    gsutil -m rsync -r gs://gtd-org-audio-input-bucket gs://gtd-org-audio-input-bucket-west
    ```
3.  **Re-point infrastructure:** Update all references (IAM, Notifications) to use the new `audio_input_west` resource.
4.  **Update the main `google` provider** to `europe-west3` and remove aliases once the transition is verified.

---

### Phase 3: Cleanup

1.  **Remove the old bucket from Terraform state:**
    ```bash
    terraform state rm google_storage_bucket.audio_input
    ```
2.  **Delete the old bucket definition** from `main.tf`.
3.  **Rename the new resource** in `main.tf` and `tfstate` back to the original logical name:
    ```bash
    terraform state mv google_storage_bucket.audio_input_west google_storage_bucket.audio_input
    ```
4.  **Delete the old physical GCS bucket** only after verifying everything works:
    ```bash
    gsutil rm -r gs://gtd-org-audio-input-bucket
    gsutil rb gs://gtd-org-audio-input-bucket
    ```

---

### Phase 4: Verification

1.  **Full Application Test:** Ensure audio ingestion/processing functions correctly.
2.  **Final Plan Check:** Run `terraform plan`—it should report `No changes`.
3.  **Unblock writes:** Restore write permissions for relevant services on the new bucket.
