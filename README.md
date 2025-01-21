# CDN Analyser

The purpose of the `CDN Analyser` is to analyse the asset requests made to the CDN from web applications. The `CDN Analyser` will run as an AWS Lambda and will run periodically, the lambda can also be triggered manually if required.

## Useful Diagrams

### Component Diagram
![](./diagrams/component_diagram.png)

## Requirements
In order to run this App locally you will need to install:

- [Java 21](https://www.oracle.com/uk/java/technologies/downloads/#java21)
- [Maven](https://maven.apache.org/download.cgi)
- [Git](https://git-scm.com/downloads)

## Configuration

| Key          | Description |
|--------------|-------------|
|     TBC      |     TBC     |

## Terraform deployment
All dependent AWS resources are provisioned by Terraform and deployed from a concourse pipeline.
Click "plan" then "apply" jobs with desired environment to deploy the lambda.
The pipeline is capable of deploying everything so manual deployment should not be necessary. For
instructions on Terraform provisioning, see [here](/terraform/README.md).

## Running the application

### Locally

1. [Configure your service](#configuration) if you want to override any of the defaults.
2. Run `make`
3. Run `./start.sh`

### Remotely

#### Cron Job

#### Manually

## Testing the application

## Useful Scripts

[SearchCompaniesHouseRepos](./scripts/SearchCompaniesHouseRepos.js)
```javascript
node SearchCompaniesHouseRepos.js --searchStrings "CDN_URL" "cdnUrlJs", "CDN_URL_JS" "CDN_HOST"
```