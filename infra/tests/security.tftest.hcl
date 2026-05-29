run "verify_bucket_is_private" {
  command = plan

  assert {
    condition     = google_storage_bucket.audio_input.public_access_prevention == "enforced"
    error_message = "Bucket není chráněn před veřejným přístupem (Public Access Prevention není ENFORCED)!"
  }


  assert {
    condition     = google_storage_bucket.audio_input.uniform_bucket_level_access == true
    error_message = "Uniform Bucket Level Access musí být povolen pro striktní IAM!"
  }

  assert {
    condition     = google_storage_bucket.terraform_state.public_access_prevention == "enforced"
    error_message = "Bucket není chráněn před veřejným přístupem (Public Access Prevention není ENFORCED)!"
  }
  assert {
    condition     = google_storage_bucket.audio_input.uniform_bucket_level_access == true
    error_message = "Uniform Bucket Level Access musí být povolen pro striktní IAM!"
  }

}