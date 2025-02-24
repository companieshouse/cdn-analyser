package uk.gov.companieshouse.cdnanalyser.configuration;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@TestConfiguration
public class S3ClientConfigTest {


        private final Environment env;

    public S3ClientConfigTest(Environment env) {
        this.env = env;
    }

    @Primary
    @Bean("localstack.s3.client")
    public S3Client s3Client() throws URISyntaxException {
        return S3Client.builder()
                .endpointOverride(new URI(Optional.ofNullable(env.getProperty("spring.cloud.aws.s3.endpoint"))
                        .orElseThrow(() ->new IllegalArgumentException("Missing S3 endpoint"))))
                .region(Region.EU_WEST_2)
                .credentialsProvider(getCredentialsProvider())
                .forcePathStyle(true)
                .build();
    }

    private StaticCredentialsProvider getCredentialsProvider() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                "test",
                "test"));
    }
}