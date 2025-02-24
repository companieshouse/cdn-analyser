package uk.gov.companieshouse.cdnanalyser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;
import uk.gov.companieshouse.cdnanalyser.configuration.S3ClientConfigTest;

@SpringBootTest
@Import(S3ClientConfigTest.class)
@AutoConfigureMockMvc
@Tag("integration-test")
public class IntegrationTest {

    @Autowired
    public S3Client s3Client;

    @Autowired
    private Supplier<Void> processRequest;

    @DynamicPropertySource
    static void overrideProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.credentials.access-key", localStack::getAccessKey);
        registry.add("spring.cloud.aws.credentials.secret-key", localStack::getSecretKey);
        registry.add("spring.cloud.aws.s3.endpoint", () -> localStack.getEndpointOverride(S3).toString());

        registry.add("aws.region.static", localStack::getRegion);
        registry.add("aws.accessKeyId", localStack::getAccessKey);
        registry.add("aws.secretAccessKey", localStack::getSecretKey);
    }

    @SuppressWarnings("resource")
    @Container
    private static final LocalStackContainer localStack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:4.4.0"))
            .withServices(LocalStackContainer.Service.S3)
            .withCopyFileToContainer(MountableFile.forHostPath("src/test/resources/cdn-assets"),"/tmp/cdn-assets")
            .withCopyFileToContainer(MountableFile.forHostPath("src/test/resources/cdn-access-logs"),"/tmp/cdn-access-logs")
            .withEnv("DEFAULT_REGION", "eu-west-2")
            .withEnv("AWS_ACCESS_KEY_ID", "test")
            .withEnv("AWS_SECRET_ACCESS_KEY", "test");

    @BeforeAll
    static void beforeAll() throws IOException, InterruptedException {
        localStack.start();
        localStack.execInContainer("awslocal", "s3api", "create-bucket", "--bucket", "cdn-assets");
        localStack.execInContainer("awslocal", "s3api", "create-bucket", "--bucket", "cdn-access-logs");
        localStack.execInContainer("awslocal", "s3api", "create-bucket", "--bucket", "cdn-analysis-logs");
    }

    @AfterAll
    static void afterAll() {
            if (localStack.isRunning()) {
                localStack.stop();
            }
        }

    @Test
    void process2assetFilesAnd2LogFiles() throws UnsupportedOperationException, IOException, InterruptedException {
        localStack.execInContainer("awslocal", "s3", "cp", "/tmp/cdn-assets", "s3://cdn-assets/", "--recursive");
        localStack.execInContainer("awslocal", "s3", "cp", "/tmp/cdn-access-logs", "s3://cdn-access-logs", "--recursive");
        List<String> cdnAnalysisOutputLogs = s3Client
                                                .listObjectsV2(
                                                    ListObjectsV2Request.builder()
                                                        .bucket("cdn-analysis-logs")
                                                        .build())
                                                        .contents()
                                                        .stream()
                                                        .map(S3Object::key).collect(Collectors.toList());
            assertEquals(0, cdnAnalysisOutputLogs.size(), "There should be no files in the cdn-analysis-logs bucket before processing");

            processRequest.get();

            cdnAnalysisOutputLogs = s3Client
                                .listObjectsV2(
                                    ListObjectsV2Request.builder()
                                        .bucket("cdn-analysis-logs")
                                        .build())
                                        .contents()
                                        .stream()
                                        .map(S3Object::key).collect(Collectors.toList());

          assertEquals(3, cdnAnalysisOutputLogs.size(), "There should be 3 files in the cdn-analysis-logs bucket after processing");
    }
}