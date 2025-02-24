resource "aws_cloudwatch_event_rule" "cdn_analyser" {
  name                = "${var.service}-${var.environment}"
  description         = "Call CDN Analyser lambda"
  schedule_expression = var.cron_schedule
}

resource "aws_cloudwatch_event_target" "call_cdn_analyser_lambda" {
  rule      = aws_cloudwatch_event_rule.cdb_analyser.name
  target_id = "${var.service}-${var.environment}"
  arn       = var.lambda_arn
}

resource "aws_lambda_permission" "allow_cloudwatch_to_call_cdn_analyser" {
  statement_id  = "AllowLambdaExecutionFromCloudWatch"
  action        = "lambda:InvokeFunction"
  function_name = "${var.service}-${var.environment}"
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.cdn_analyser.arn
}
