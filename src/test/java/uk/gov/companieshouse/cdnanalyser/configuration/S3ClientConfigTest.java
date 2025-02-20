package uk.gov.companieshouse.cdnanalyser.configuration;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import software.amazon.awssdk.services.s3.S3Client;

@ExtendWith(MockitoExtension.class)
public class S3ClientConfigTest {

    @InjectMocks
    private S3ClientConfig s3ClientConfig;

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(s3ClientConfig, "s3endpoint", "http://localhost:4566");
        ReflectionTestUtils.setField(s3ClientConfig, "region", "us-west-2");
        ReflectionTestUtils.setField(s3ClientConfig, "accessKey", "test");
        ReflectionTestUtils.setField(s3ClientConfig, "secretKey", "test");
    }

    @Test
    public void testS3Client() {
        S3Client s3Client = s3ClientConfig.s3Client();
        assertNotNull(s3Client);
    }

    @Test
    public void testS3ClientWithDefaultCredentials() {
        ReflectionTestUtils.setField(s3ClientConfig, "accessKey", "default");
        ReflectionTestUtils.setField(s3ClientConfig, "pathStyleAccess", true);
        S3Client s3Client = s3ClientConfig.s3Client();
        assertNotNull(s3Client);
    }
}