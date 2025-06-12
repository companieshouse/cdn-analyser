package uk.gov.companieshouse.cdnanalyser.service.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import uk.gov.companieshouse.cdnanalyser.models.AssetAccessLog;
import uk.gov.companieshouse.cdnanalyser.models.AssetUsageReport;

class WriterServiceTest {

    private S3Client s3Client;
    private WriterService writerService;
    private final String bucketName = "test-bucket";

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        writerService = new WriterService(s3Client, bucketName);
    }

    @Test
    void saveRawData_shouldPutObject_withSerializedData() throws Exception {
        AssetAccessLog log = mock(AssetAccessLog.class);
        HashSet<AssetAccessLog> logs = new HashSet<>();
        logs.add(log);

        writerService.saveRawData(logs);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);

        verify(s3Client, times(1)).putObject(requestCaptor.capture(), bodyCaptor.capture());

        PutObjectRequest req = requestCaptor.getValue();
        assertEquals(bucketName, req.bucket());
        assertEquals("raw-asset-access-data.json", req.key());

        String bodyString = new String(bodyCaptor.getValue().contentStreamProvider().newStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(bodyString.contains("[") || bodyString.contains("{"));
    }

    @Test
    void saveSuccessfulAssetRequests_shouldPutObject_withSerializedReport() throws Exception {
        AssetUsageReport report = mock(AssetUsageReport.class);

        writerService.saveSuccessfulAssetRequests(report);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);

        verify(s3Client, times(1)).putObject(requestCaptor.capture(), bodyCaptor.capture());

        PutObjectRequest req = requestCaptor.getValue();
        assertEquals(bucketName, req.bucket());
        assertEquals("successful-asset-requests.json", req.key());

        String bodyString = new String(bodyCaptor.getValue().contentStreamProvider().newStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(bodyString.contains("{"));
    }

    @Test
    void saveFailedAssetsRequests_shouldPutObject_withSerializedFailedAssets() throws Exception {
        AssetAccessLog log1 = mock(AssetAccessLog.class);
        AssetAccessLog log2 = mock(AssetAccessLog.class);

        when(log1.toString()).thenReturn("log1");
        when(log2.toString()).thenReturn("log2");

        List<AssetAccessLog> logs = Arrays.asList(log1, log2);

        // Actually call the real method (not the spy)
        writerService.saveFailedAssetsRequests(logs);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);

        verify(s3Client, times(1)).putObject(requestCaptor.capture(), bodyCaptor.capture());

        PutObjectRequest req = requestCaptor.getValue();
        assertEquals(bucketName, req.bucket());
        assertEquals("failed-asset-requests.json", req.key());

        String bodyString = new String(bodyCaptor.getValue().contentStreamProvider().newStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(bodyString.contains("{"));
        assertTrue(bodyString.contains("\n") || logs.size() == 1);
    }

    @Test
    void saveFailedAssetsRequests_shouldNotPutObject_whenListIsEmpty() {
        writerService.saveFailedAssetsRequests(Collections.emptyList());
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}