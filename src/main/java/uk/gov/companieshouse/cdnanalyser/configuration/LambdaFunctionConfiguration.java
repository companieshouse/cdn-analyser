package uk.gov.companieshouse.cdnanalyser.configuration;

import java.util.function.Supplier;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.services.s3.S3Client;
import uk.gov.companieshouse.cdnanalyser.service.s3.Processor;

@Configuration
public class LambdaFunctionConfiguration {

    @Bean
    public Supplier<Void> processRequest(S3Client s3Client, Processor processor) {
        return () -> {
            processor.handleAssets();
            return null;
        };
    }
}
