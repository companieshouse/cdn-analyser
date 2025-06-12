package uk.gov.companieshouse.cdnanalyser.configuration;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Configuration
public class ServiceConfiguration {

@Value("${aws.endpoint}")
private String s3endpoint;

@Value("${aws.region}")
private String region;

@Value("${aws.secret.access.key}")
private String accessKey;

@Value("${aws.secret.access.key}")
private String secretKey;

@Value("${aws.s3.path-style-access}")
private boolean pathStyleAccess;

    private static final Logger logger = LoggerFactory.getLogger(Constants.APPLICATION_NAME_SPACE);

    @Bean
    public S3Client s3Client(){
        S3ClientBuilder builder =  S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(
                pathStyleAccess
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
                : DefaultCredentialsProvider.create()
            );

        if (pathStyleAccess){
            builder.serviceConfiguration(t -> t.pathStyleAccessEnabled(true));
            builder.endpointOverride(URI.create(s3endpoint));
        }
        else{
            logger.info("USING PRODUCTION CONFIGS");
        }
        return builder.build();
    }
}
