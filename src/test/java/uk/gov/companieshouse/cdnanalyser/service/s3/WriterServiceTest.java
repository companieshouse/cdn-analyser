package uk.gov.companieshouse.cdnanalyser.service.s3;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import uk.gov.companieshouse.cdnanalyser.models.AssetAccessLog;
import uk.gov.companieshouse.cdnanalyser.models.AssetUsageReport;

public class WriterServiceTest {

    @Mock
    private S3Client s3Client;

    @Value("${cdn.analysis.bucket}")
    private String cdnAnalysisBucket = "test-bucket";

    @InjectMocks
    private WriterService writerService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        writerService = new WriterService(s3Client, cdnAnalysisBucket);
    }

    @Test
    public void testSaveRawData() {
        AssetAccessLog log1 = new AssetAccessLog();
        log1.setTimestamp(Instant.now());
        AssetAccessLog log2 = new AssetAccessLog();
        log2.setTimestamp(Instant.now().plusSeconds(3600));
        List<AssetAccessLog> logs = Arrays.asList(log1, log2);

        writerService.saveRawData(logs);

        verify(s3Client, times(0)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    public void testSaveSuccessfulAssetRequestsTotals() {
        AssetUsageReport report = new AssetUsageReport();

        writerService.saveSuccessfulAssetRequestsTotals(report);

        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    public void testSaveSuccessfulAssetRequestsTotalsForPeriod() {
        AssetUsageReport report = new AssetUsageReport();

        writerService.saveSuccessfulAssetRequestsTotalsForPeriod(report);

        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    public void testSaveFailedAssetsRequestsFailedJsonConversion() {
        AssetAccessLog log1 = new AssetAccessLog();
        log1.setTimestamp(Instant.now());
        List<AssetAccessLog> logs = Arrays.asList(log1);

        writerService.saveFailedAssetsRequests(logs);

        verify(s3Client, times(0)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    public void testFailedAssetRequestsJsonConversion() throws IOException {
        AssetAccessLog log1 = new AssetAccessLog();
        log1.setStatusCode(200);
        log1.setTimestamp(Instant.now());
        AssetAccessLog log2 = new AssetAccessLog();
        log2.setStatusCode(404);
        log2.setTimestamp(Instant.now().plusSeconds(3600));
        List<AssetAccessLog> logs = Arrays.asList(log1, log2);

        ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

        String expectedJson1 = objectMapper.writeValueAsString(log1);
        String expectedJson2 = objectMapper.writeValueAsString(log2);
        writerService.saveFailedAssetsRequests(logs);

        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        ArgumentCaptor<PutObjectRequest> putObjectRequestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> requestBodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(putObjectRequestCaptor.capture(), requestBodyCaptor.capture());

        RequestBody capturedRequestBody = requestBodyCaptor.getValue();
        byte[] byteBuffer = capturedRequestBody.contentStreamProvider().newStream().readAllBytes();
        String actualJson = new String(byteBuffer, StandardCharsets.UTF_8);

        assertTrue(actualJson.contains(expectedJson1));
        assertTrue(actualJson.contains(expectedJson2));
    }

    @Test
    public void testRawDataJsonConversion() throws IOException {
        AssetAccessLog log1 = new AssetAccessLog();
        log1.setStatusCode(200);
        log1.setTimestamp(Instant.now());
        AssetAccessLog log2 = new AssetAccessLog();
        log2.setStatusCode(404);
        log2.setTimestamp(Instant.now().plusSeconds(3600));
        List<AssetAccessLog> logs = Arrays.asList(log1, log2);

        ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

        String expectedJson1 = objectMapper.writeValueAsString(log1);
        String expectedJson2 = objectMapper.writeValueAsString(log2);
        writerService.saveRawData(logs);

        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        ArgumentCaptor<PutObjectRequest> putObjectRequestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> requestBodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(putObjectRequestCaptor.capture(), requestBodyCaptor.capture());

        RequestBody capturedRequestBody = requestBodyCaptor.getValue();
        byte[] byteBuffer = capturedRequestBody.contentStreamProvider().newStream().readAllBytes();
        String actualJson = new String(byteBuffer, StandardCharsets.UTF_8);

        assertTrue(actualJson.contains(expectedJson1));
        assertTrue(actualJson.contains(expectedJson2));
    }
}