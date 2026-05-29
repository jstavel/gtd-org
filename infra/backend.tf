resource "google_storage_bucket" "terraform_state" {
  name     = "gtd-org-terraform-state"
  location = "EUROPE-CENTRAL2"
  public_access_prevention = "enforced"
  # Důležité pro bezpečnost a historii
  versioning {
    enabled = true
  }
}