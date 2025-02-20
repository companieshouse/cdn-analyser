package uk.gov.companieshouse.cdnanalyser.configuration;

import java.net.URI;

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
public class S3ClientConfig {

@Value("${aws.endpoint:empty}")
private String s3endpoint;

@Value("${aws.region:eu-west-2}")
private String region;

@Value("${aws.secret.access.key:empty}")
private String accessKey;

@Value("${aws.secret.access.key:empty}")
private String secretKey;

@Value("${aws.s3.path-style-access:false}")
private boolean pathStyleAccess;

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
        return builder.build();
    }
}
