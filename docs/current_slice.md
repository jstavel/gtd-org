# Current Slice: [M1/Slice 2] GCP GCS Bucket Initialization & TTL Invariant

## Context
Following the completion of the local ingestion logic in the `fp`
(File Provider) component, we are now shifting focus to cloud
infrastructure. The goal is to prepare a secure and automated storage
solution for raw audio recordings that adheres to strict data
lifecycle policies.

## Goal
1. Provision and configure a Google Cloud Storage (GCS) bucket in the `europe-central2` region.
2. Implement a TTL (Time-To-Live) invariant according to ADR 0001 (automatic deletion after 1 day).
3. Manage this infrastructure using Infrastructure as Code (Terraform).

## Target Files
- `infra/main.tf`
- `docs/current_slice.md` (this file)

## Technical Steps

### 1. Infrastructure Setup
- Initialize Terraform in the new `infra/` directory.
- Define a `google_storage_bucket` resource set to the `europe-central2` region.
- Configure lifecycle rules for automatic object deletion (TTL) set to 1 day.

### 2. Implementation & Import
- (Optional) Create the bucket manually in the GCP Console to validate parameters.
- Import the bucket into the Terraform state (`terraform import`).
- Reconcile the configuration using `terraform plan` and `terraform apply`.

### 3. Verification
- Confirm the bucket exists in `europe-central2`.
- Verify the 1-day lifecycle rule is correctly applied.
- Ensure the code is versioned in Git within the `infra/` folder.

## Definition of Done (DoD)
- [x] `infra/main.tf` contains the definition for the ingestion bucket.
- [x] Terraform state is initialized, and the bucket is under IaC management.
- [x] TTL invariant (automatic cleanup after 1 day) is active.
- [x] Verified that the bucket is not publicly accessible (strict IAM).
