data "vault_generic_secret" "lambda_environment_variables" {
  path = "applications/${var.aws_profile}/${var.environment}/${var.service}/lambda_environment_variables"
}

# ------------------------------------------------------------------------------
# Lambdas
# ------------------------------------------------------------------------------
resource "aws_lambda_function" "cdn_analyser" {
  s3_bucket     = var.release_bucket_name
  s3_key        = "${var.service}/${var.service}-${var.release_version}.zip"
  function_name = "${var.service}-${var.environment}"
  role          = var.execution_role
  handler       = var.handler
  memory_size   = var.memory_megabytes
  timeout       = var.timeout_seconds
  runtime       = var.runtime

  environment {
    variables = merge(
      data.vault_generic_secret.lambda_environment_variables.data,
      var.open_lambda_environment_variables
    )
  }
}

output "lambda_arn" {
  value = aws_lambda_function.cdn_analyser.arn
}