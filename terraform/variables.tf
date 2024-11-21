variable aws_region {}

variable handler {
  default = "cdn-analyser"
}

variable memory_megabytes {
  default = "320"
}

variable release_bucket_name {
  default = "release.ch.gov.uk"
}

variable runtime {
  default = "go1.x"
}

variable timeout_seconds {
  default = "6"
}

variable environment {}

variable release_version {}

variable aws_bucket {}

variable service {
  default = "cdn-analyser"
}

variable vpc_id {}

variable subnet_ids {}

variable cron_schedule {}

variable workspace_key_prefix {}