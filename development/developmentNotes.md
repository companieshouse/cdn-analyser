# Development Notes

To be able to run and test the lambda locally you'll need to follow the guide documented below as the 3 S3 buckets need to be created and joined to the localstack Docker network.

## Setting up the local S3 Buckets


```bash
  aws configure
```

When prompted use these values:
* AWS Access Key ID: test
* AWS Secret Access Key: test
* Region: us-east-1
* Output format: json


```bash
  brew install localstack aws-sam-cli
  export LOCALSTACK_ENDPOINT=http://localhost:4566
  localstack start -d
  aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 mb s3://cdn-assets; sleep 1
  aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 mb s3://cdn-access-logs ; sleep 1
  aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 mb s3://cdn-analysis-logs ; sleep 1
  aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 sync src/test/resources/mock-assets-folder s3://cdn-assets; sleep 1
  aws --endpoint-url=$LOCALSTACK_ENDPOINT s3 sync src/test/resources/cdn-access-logs s3://cdn-access-logs
```

## Create required Docker network to join
``` bash
docker network create lambda-local
docker network connect lambda-local localstack-main
```

## Running
The command below needs to be run from this folder
```
sam build
sam local invoke cdn_analyser -e properties.json --docker-network lambda-local
```