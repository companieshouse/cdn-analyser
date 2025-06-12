# ------------------------------------------------------------------------------
# Policy Documents
# ------------------------------------------------------------------------------
data "aws_iam_policy_document" "cdn_analyser_trust" {
  statement {
    effect = "Allow"

    actions = [
      "sts:AssumeRole",
    ]

    principals {
      type = "Service"

      identifiers = [
        "lambda.amazonaws.com",
      ]
    }
  }
}

data "aws_iam_policy_document" "cdn_analyser_execution" {
  statement {
    effect = "Allow"
    actions = ["s3:GetObject"]
    resources = [
      "arn:aws:s3:::cdn-assets/*",
      "arn:aws:s3:::cdn-logs/*"
    ]
  }

  statement {
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject"
    ]
    resources = ["arn:aws:s3:::cdn-analysis-logs/*"]
  }
}

# ------------------------------------------------------------------------------
# Roles
# ------------------------------------------------------------------------------
resource "aws_iam_role" "cdn_analyser_execution" {
  name               = "${var.service}-execution-${var.environment}"
  assume_role_policy = data.aws_iam_policy_document.cdn_analyser_trust.json
}

# ------------------------------------------------------------------------------
# Role Policies
# ------------------------------------------------------------------------------
resource "aws_iam_role_policy" "cdn_analyser_execution" {
  name   = "cdn_analyser_execution"
  role   = aws_iam_role.cdn_analyser_execution.id
  policy = data.aws_iam_policy_document.cdn_analyser_execution.json
}

# ------------------------------------------------------------------------------
# Outputs
# ------------------------------------------------------------------------------
output "execution_role" {
  value = aws_iam_role.cdn_analyser_execution.arn
}