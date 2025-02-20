package uk.gov.companieshouse.cdnanalyser.service;


import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import uk.gov.companieshouse.cdnanalyser.models.AssetAccessLog;
import uk.gov.companieshouse.cdnanalyser.models.AssetUsageReport;
import uk.gov.companieshouse.cdnanalyser.service.interfaces.AnalysisInputInterface;


class AbstractProcessorTest {

    @Mock
    private AnalysisInputInterface analysisInputInterface;


    private AbstractProcessor abstractProcessor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
            abstractProcessor = new AbstractProcessor(analysisInputInterface, 30) {

            private static final Logger logger = LoggerFactory.getLogger("cdnAnalyser");

            @Override
            public List<AssetAccessLog> convertLogsToAssetAccessLogs() {
                return Collections.emptyList();
            }

            @Override
            public void logProcessorType() {
                logger.info("Processor type logged");
            }

            @Override
            public void saveAssetLogs(AssetUsageReport successfulAssetRequestTotals, AssetUsageReport successfulAssetRequestTotalsForPeriod, List<AssetAccessLog> failedAssetAccessLogs, List<AssetAccessLog> assetAccessLogs) {
                logger.info("Asset logs saved");
            }
        };
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void handleAssets_withEmptyAccessLogs_logsNoAssetRequestsFound(CapturedOutput output) {
        when(analysisInputInterface.readAssets()).thenReturn(Collections.emptyList());
        when(analysisInputInterface.readAccessLogs()).thenReturn(Collections.emptyList());
        when(analysisInputInterface.readAnalysisReports()).thenReturn(Collections.emptyList());

        abstractProcessor.handleAssets();

        assertTrue("Processor type logged", output.getAll().contains("Processor type logged"));
        assertTrue("", output.getAll().contains("No asset requests or assets have been found"));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void handleAssets_withNonEmptyAccessLogs_processesAssetAccessLogs(CapturedOutput output) {

        List<String> assets = List.of("asset1", "asset2");

        AssetAccessLog log1 = new AssetAccessLog();
        log1.setTimestamp(Instant.now());
        log1.setStatusCode(200);
        log1.setAsset("asset2");
        log1.setRequestType("GET");

        AssetAccessLog log2 = new AssetAccessLog();
        log2.setTimestamp(Instant.now());
        log2.setStatusCode(404);
        log2.setAsset("asset2");
        log2.setRequestType("GET");

        List<AssetAccessLog> mockLogs = Arrays.asList(log1, log2);
        List<AssetUsageReport> assetUsageReports = List.of(mock(AssetUsageReport.class));

        when(analysisInputInterface.readAssets()).thenReturn(assets);
        when(analysisInputInterface.readAccessLogs()).thenReturn(mockLogs);
        when(analysisInputInterface.readAnalysisReports()).thenReturn(assetUsageReports);

        abstractProcessor.handleAssets();

        assertTrue("Processor type logged", output.getAll().contains("Processor type logged"));
        assertTrue("", output.getAll().contains("The number of assets found is: 2"));
        assertTrue("", output.getAll().contains("Of the 2 assets access logs, 1 will be saved as failed asset requests"));
        verify(analysisInputInterface).readAssets();
        verify(analysisInputInterface).readAccessLogs();
        verify(analysisInputInterface).readAnalysisReports();
    }
}