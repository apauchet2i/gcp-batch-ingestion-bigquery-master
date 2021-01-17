terraform {
  backend "gcs" {
    bucket = "tf-state-gcp-batch-ingestion"
    prefix = "terraform/state"
  }
}

provider "google" {
  project = "dkt-us-data-lake-a1xq"
  region = "us-central1"
}

resource "google_storage_bucket" "funky-bucket" {
  name = "dkt-us-ldp-baptiste-test"
  storage_class = "REGIONAL"
  location  = "us-central1"
}
