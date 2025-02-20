# CDN Analyser

The purpose of the `CDN Analyser` is to analyse the asset requests made to the CDN from web applications. The `CDN Analyser` will run as an AWS Lambda and will run periodically, the lambda can also be triggered manually if required.

## Useful Diagrams

### Component Diagram
![](./diagrams/component_diagram.png)

## Requirements
In order to run this App locally you will need to install:

- [Java 17](https://www.oracle.com/uk/java/technologies/downloads/#java17)
- [Maven](https://maven.apache.org/download.cgi)
- [Git](https://git-scm.com/downloads)

## Configuration

#### Local Configuration
|                   Key                    |           Example           |                        Notes                        |
|------------------------------------------|-----------------------------|-----------------------------------------------------|
| aws.endpoint                             | http://localstack-main:4566 | localstack-main is the name of the docker container |
| aws.region                               | eu-west-2                   |                                                     |
| aws.access.key.id                        | test                        |                                                     |
| aws.secret.access.key                    | test                        |                                                     |
| aws.s3.path-style-access                 | true                        | Required for local dev use only                     |
| cdn.access.logs.bucket                   | cdn-access-logs             |                                                     |
| cdn.access.logs.filterinpath             | cidev                       |                                                     |
| cdn.access.logs.processlogsfromtodayonly | TRUE                        |                                                     |
| cdn.assets.bucket                        | cdn-assets                  |                                                     |
| cdn.assets.filterinpath                  | cidev                       |                                                     |
| cdn.analysis.data.retention              | 365                         |                                                     |


## Terraform deployment
All dependent AWS resources are provisioned by Terraform and deployed from a concourse pipeline.
Click "plan" then "apply" jobs with desired environment to deploy the lambda.
The pipeline is capable of deploying everything so manual deployment should not be necessary. For
instructions on Terraform provisioning, see [here](/terraform/README.md).

## Running the application

### Locally

1. Read through [developmentNotes.md](/development/developmentNotes.md)
1. `mvn clean package`
2. `cd development`
3. `sam local invoke cdnanalyser -e properties.json --docker-network lambda-local`

### Remotely

#### Cron Job

#### Manually

## Testing the application

## Useful Scripts

[SearchCompaniesHouseRepos](./scripts/SearchCompaniesHouseRepos.js)
```javascript
node SearchCompaniesHouseRepos.js --searchStrings "CDN_URL" "cdnUrlJs" "CDN_URL_JS" "CDN_HOST"
```