variable handler {
  type        = string
}
variable memory_megabytes {
  type        = string
}
variable release_bucket_name {
  type        = string
}
variable runtime {
  type        = string
}
variable timeout_seconds {
  type        = string
}
variable service {
  type        = string
}
variable release_version {
  type        = string
}
variable open_lambda_environment_variables {
  type        = map(string)
}
variable execution_role {
  type        = string
  description = "IAM role from lambda-roles module used for executing the Lambda."
}
variable environment {
  type        = string
}
variable aws_profile {
  type        = string
}
