package uk.gov.companieshouse.cdnanalyser.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import uk.gov.companieshouse.cdnanalyser.configuration.Constants;
import uk.gov.companieshouse.cdnanalyser.models.AssetAccessLog;
import uk.gov.companieshouse.cdnanalyser.models.AssetUsageReport;
import uk.gov.companieshouse.cdnanalyser.service.interfaces.AnalysisInputInterface;
import uk.gov.companieshouse.cdnanalyser.service.interfaces.AnalysisOutputInterface;
import uk.gov.companieshouse.cdnanalyser.service.s3.Processor;

class ProcessorTest {

    @Mock
    private AnalysisInputInterface analysisInputInterface;

    @Mock
    private AnalysisOutputInterface analysisOutputInterface;

    @InjectMocks
    private Processor processor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        processor = new Processor(analysisInputInterface, analysisOutputInterface, "/cidev/");
    }

    @Test
    void testConvertLogsToAssetAccessLogs() {
        HashSet<AssetAccessLog> mockLogs = new HashSet<>();
        AssetAccessLog log1 = new AssetAccessLog();
        AssetAccessLog log2 = new AssetAccessLog();
        mockLogs.add(log1);
        mockLogs.add(log2);

        when(analysisInputInterface.readAccessLogs()).thenReturn(mockLogs);

        Set<AssetAccessLog> result = processor.convertLogsToAssetAccessLogs();

        assertEquals(mockLogs, result);
        verify(analysisInputInterface).readAccessLogs();
    }

    @Test
    void testHandleAssetsWithNoAssetsOrLogs() {
        when(analysisInputInterface.readAssets()).thenReturn(Collections.emptyList());
        when(analysisInputInterface.readAccessLogs()).thenReturn(Collections.emptySet());
        when(analysisInputInterface.readRawAssetAccessLogs()).thenReturn(Collections.emptyList());

        processor.handleAssets();

        verify(analysisInputInterface).readAssets();
        verify(analysisInputInterface).readAccessLogs();
        verify(analysisInputInterface).readRawAssetAccessLogs();
        // No further interactions expected
        verifyNoMoreInteractions(analysisOutputInterface);
    }

    @Test
    void testHandleAssetsProcessesAndSaves() {
        List<String> assets = Arrays.asList("file1", "file2");
        AssetAccessLog log1 = new AssetAccessLog();
        log1.setAsset("/cidev/file1");
        log1.setStatusCode(200);
        log1.setTimestamp(Instant.now());
        AssetAccessLog log2 = new AssetAccessLog();
        log2.setAsset("/cidev/file2");
        log2.setStatusCode(404);
        log2.setTimestamp(Instant.now());


        Set<AssetAccessLog> accessLogs = Set.of(log1, log2);
        List<AssetAccessLog> existingLogs = Collections.emptyList();

        when(analysisInputInterface.readAssets()).thenReturn(assets);
        when(analysisInputInterface.readAccessLogs()).thenReturn(accessLogs);
        when(analysisInputInterface.readRawAssetAccessLogs()).thenReturn(existingLogs);

        doNothing().when(analysisOutputInterface).saveRawData(any());
        doNothing().when(analysisOutputInterface).saveFailedAssetsRequests(any());
        doNothing().when(analysisOutputInterface).saveSuccessfulAssetRequests(any());

        processor.handleAssets();

        verify(analysisOutputInterface).saveRawData(any());
        verify(analysisOutputInterface).saveFailedAssetsRequests(any());
        verify(analysisOutputInterface).saveSuccessfulAssetRequests(any());
    }

    @Test
    void testCollectFailedRequests() throws Exception {
        // Reflection to access private method for coverage
        AssetAccessLog log1 = new AssetAccessLog();
        log1.setAsset("file1");
        log1.setStatusCode(200);
        log1.setTimestamp(Instant.now());
        AssetAccessLog log2 = new AssetAccessLog();
        log2.setAsset("file2");
        log2.setStatusCode(404);
        log2.setTimestamp(Instant.now());

        Set<AssetAccessLog> logs = Set.of(log1, log2);

        var method = Processor.class.getDeclaredMethod("collectFailedRequests", Set.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<AssetAccessLog> failed = (List<AssetAccessLog>) method.invoke(processor, logs);

        assertEquals(1, failed.size());
        assertEquals(404, failed.get(0).getStatusCode());
    }

    @Test
    void testCollectSuccessfulAssetUsageReports() throws Exception {
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, java.time.temporal.ChronoUnit.DAYS);
        List<String> assets = Arrays.asList("file1", "file2");
        AssetAccessLog log1 = new AssetAccessLog();
        log1.setAsset("/cidev/file1");
        log1.setStatusCode(200);
        log1.setTimestamp(now);
        AssetAccessLog log2 = new AssetAccessLog();
        log2.setAsset("/cidev/file2");
        log2.setStatusCode(200);
        log2.setTimestamp(now);
        AssetAccessLog log3 = new AssetAccessLog();
        log3.setAsset("/cidev/file2");
        log3.setStatusCode(200);
        log3.setTimestamp(yesterday);

        Set<AssetAccessLog> logs = Set.of(log1, log2,log3);

        var method = Processor.class.getDeclaredMethod("collectSuccessfulAssetUsageReports", Set.class, List.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<AssetUsageReport> reports = (List<AssetUsageReport>) method.invoke(processor, logs, assets);

        assertFalse(reports.isEmpty());

        reports.forEach(report -> {
            assertEquals(assets.size(), report.getAssetAccessCount().size());
            if(report.getId().equals(LocalDate.ofInstant(now, Constants.LONDON_ZONE_ID).toString())){
                Map<String, Integer> counts = report.getAssetAccessCount();
                assertEquals(1, counts.get("file1"));
                assertEquals(1, counts.get("file2"));
            } else if(report.getId().equals(LocalDate.ofInstant(yesterday, Constants.LONDON_ZONE_ID).toString())){
                Map<String, Integer> counts = report.getAssetAccessCount();
                assertEquals(0, counts.get("file1"));
                assertEquals(1, counts.get("file2"));
            } else {
                throw new IllegalStateException("Unexpected report date: " + report.getId());

        }
    });
    }
}
