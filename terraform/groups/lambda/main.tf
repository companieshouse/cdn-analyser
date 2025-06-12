provider "aws" {
  region = var.aws_region
}

terraform {
  backend "s3" {
    bucket = var.remote_state_bucket
    key    = var.remote_state_key
    region = var.aws_region
    encrypt = true
  }
}

provider "vault" {
  auth_login {
    path = "auth/userpass/login/${var.vault_username}"
    parameters = {
      password = var.vault_password
    }
  }
}

data "terraform_remote_state" "network_remote_state" {
  backend = "s3"
  config = {
    bucket = var.remote_state_bucket
    key    = var.remote_state_key
    region = var.aws_region
  }
}

module "lambda" {
  source                            = "./module-lambda"
  service                           = var.service
  handler                           = var.handler
  memory_megabytes                  = var.memory_megabytes
  runtime                           = var.runtime
  timeout_seconds                   = var.timeout_seconds
  release_version                   = var.release_version
  release_bucket_name               = var.release_bucket_name
  execution_role                    = module.lambda-roles.execution_role
  open_lambda_environment_variables = var.open_lambda_environment_variables
  aws_profile                       = var.aws_profile
  environment                       = var.environment
}

module "lambda-roles" {
  source      = "./module-lambda-roles"
  service     = var.service
  environment = var.environment
}

module "cloud-watch" {
  source        = "./module-cloud-watch"
  service       = var.service
  lambda_arn    = module.lambda.lambda_arn
  environment   = var.environment
  cron_schedule = var.cron_schedule
}provider "aws" {
  region = var.aws_region
}

terraform {
  backend "s3" {
    bucket = var.remote_state_bucket
    key    = var.remote_state_key
    region = var.aws_region
    encrypt = true
  }
}

provider "vault" {
  auth_login {
    path = "auth/userpass/login/${var.vault_username}"
    parameters = {
      password = var.vault_password
    }
  }
}

data "terraform_remote_state" "network_remote_state" {
  backend = "s3"
  config = {
    bucket = var.remote_state_bucket
    key    = var.remote_state_key
    region = var.aws_region
  }
}

module "lambda" {
  source                            = "./module-lambda"
  service                           = var.service
  handler                           = var.handler
  memory_megabytes                  = var.memory_megabytes
  runtime                           = var.runtime
  timeout_seconds                   = var.timeout_seconds
  release_version                   = var.release_version
  release_bucket_name               = var.release_bucket_name
  execution_role                    = module.lambda-roles.execution_role
  open_lambda_environment_variables = var.open_lambda_environment_variables
  aws_profile                       = var.aws_profile
  environment                       = var.environment
}

module "lambda-roles" {
  source      = "./module-lambda-roles"
  service     = var.service
  environment = var.environment
}

module "cloud-watch" {
  source        = "./module-cloud-watch"
  service       = var.service
  lambda_arn    = module.lambda.lambda_arn
  environment   = var.environment
  cron_schedule = var.cron_schedule
}