terraform {
  # Tady říkáš Terraformu, kde má hledat "pravdu" (state)
  backend "gcs" {
    bucket  = "gtd-org-terraform-state"
    prefix  = "terraform/state"
  }

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 7.0"
    }
  }
}

provider "google" {
  project = "gtd-org-2026"
  region  = "europe-central2"
}

# 1. Bucket pro audio
resource "google_storage_bucket" "audio_input" {
  name          = "gtd-org-audio-input-bucket"
  location      = "EUROPE-CENTRAL2"
  force_destroy = true
  public_access_prevention    = "enforced"
  uniform_bucket_level_access = true
  lifecycle_rule {
    action { type = "Delete" }
    condition { age = 1 }
  }
}

# 2. Pub/Sub Téma
resource "google_pubsub_topic" "audio_events" {
  name = "audio-upload-topic"
}

# 3. Zjisti identitu servisního účtu úložiště
data "google_storage_project_service_account" "gcs_account" {
}

# 4. Dej tomuto účtu právo publikovat zprávy do tvého Pub/Sub tématu
resource "google_pubsub_topic_iam_member" "gcs_pubsub_publishing" {
  topic  = google_pubsub_topic.audio_events.name
  role   = "roles/pubsub.publisher"
  member = "serviceAccount:${data.google_storage_project_service_account.gcs_account.email_address}"
}

# 5. Notifikace z bucketu do Pub/Sub
resource "google_storage_notification" "notification" {
  bucket         = google_storage_bucket.audio_input.name
  payload_format = "JSON_API_V1"
  topic          = google_pubsub_topic.audio_events.id
  event_types    = ["OBJECT_FINALIZE"]
}

# 6. Předplatné pro Transcriber worker
resource "google_pubsub_subscription" "transcription_sub" {
  name  = "transcription-sub"
  topic = google_pubsub_topic.audio_events.name
  ack_deadline_seconds = 60
}

# 7. Service Account pro worker
resource "google_service_account" "transcription_sa" {
  account_id   = "transcription-worker"
}

resource "google_storage_bucket_iam_member" "sa_bucket_access" {
  bucket = google_storage_bucket.audio_input.name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.transcription_sa.email}"
}
