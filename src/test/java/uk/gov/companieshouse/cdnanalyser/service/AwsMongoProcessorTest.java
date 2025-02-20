package uk.gov.companieshouse.cdnanalyser.service;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import uk.gov.companieshouse.cdnanalyser.models.AssetAccessLog;
import uk.gov.companieshouse.cdnanalyser.models.AssetUsageReport;
import uk.gov.companieshouse.cdnanalyser.service.interfaces.AnalysisInputInterface;
import uk.gov.companieshouse.cdnanalyser.service.interfaces.AnalysisOutputInterface;
import uk.gov.companieshouse.cdnanalyser.service.s3.Processor;
import uk.gov.companieshouse.cdnanalyser.service.s3.ReaderService;

public class AwsMongoProcessorTest {

    @Mock
    private AnalysisInputInterface analysisInputInterface = mock(ReaderService.class);

    @Mock
    private AnalysisOutputInterface analysisOutputInterface;

    @InjectMocks
    @Spy
    private Processor processor;

    @BeforeEach
    public void setUp() {
        processor = new Processor(analysisInputInterface, analysisOutputInterface, 0);
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testConvertLogsToAssetAccessLogs() {
        List<AssetAccessLog> mockLogs = Arrays.asList(new AssetAccessLog(), new AssetAccessLog());
        when(analysisInputInterface.readAccessLogs()).thenReturn(mockLogs);

        List<AssetAccessLog> result = processor.convertLogsToAssetAccessLogs();

        assertEquals(mockLogs, result);
        verify(analysisInputInterface, times(1)).readAccessLogs();
    }

    @Test
    public void testSaveAssetLogs() throws Exception {

        AssetUsageReport successfulAssetUsageReport = new AssetUsageReport();

        List<AssetAccessLog> mockFailedLogs = Arrays.asList(new AssetAccessLog(), new AssetAccessLog());

        processor.saveAssetLogs(successfulAssetUsageReport, successfulAssetUsageReport, mockFailedLogs, mockFailedLogs);

        verify(analysisOutputInterface, times(1)).saveFailedAssetsRequests(mockFailedLogs);
        verify(analysisOutputInterface, times(1)).saveRawData(mockFailedLogs);
        verify(analysisOutputInterface, times(1)).saveSuccessfulAssetRequestsTotals(successfulAssetUsageReport);
        verify(analysisOutputInterface, times(1)).saveSuccessfulAssetRequestsTotalsForPeriod(successfulAssetUsageReport);
    }

    @Test
    public void testRun() throws Exception {

        AssetAccessLog asset1 = new AssetAccessLog();
        asset1.setAsset("file");
        asset1.setRequestType("GET1");
        asset1.setStatusCode(404);
        asset1.setTimestamp(Instant.now());

        when(analysisInputInterface.readAccessLogs()).thenReturn(Arrays.asList(asset1));
        processor.handleAssets();
        verify(processor, times(1)).logProcessorType();
    }
}