package uk.gov.companieshouse.cdnanalyser.service.s3;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import uk.gov.companieshouse.cdnanalyser.configuration.Constants;
import uk.gov.companieshouse.cdnanalyser.models.AssetAccessLog;
import uk.gov.companieshouse.cdnanalyser.models.AssetUsageReport;
import uk.gov.companieshouse.cdnanalyser.service.AbstractProcessor;
import uk.gov.companieshouse.cdnanalyser.service.interfaces.AnalysisInputInterface;
import uk.gov.companieshouse.cdnanalyser.service.interfaces.AnalysisOutputInterface;

@Service
public class Processor extends AbstractProcessor {

    private static final Logger logger = LoggerFactory.getLogger("cdnAnalyser");

    private AnalysisOutputInterface analysisOutputInterface;

    private AnalysisInputInterface analysisInputInterface;

    private final int dataRetentionPeriod;


    public Processor(AnalysisInputInterface analysisInputInterface, AnalysisOutputInterface analysisOutputInterface, @Value("${cdn.analysis.data.retention}") int dataRetentionPeriod){
        super(analysisInputInterface, dataRetentionPeriod);
        this.analysisOutputInterface = analysisOutputInterface;
        this.analysisInputInterface = analysisInputInterface;
        this.dataRetentionPeriod = dataRetentionPeriod;
    }

    @Override
    public List<AssetAccessLog> convertLogsToAssetAccessLogs() {
        return analysisInputInterface.readAccessLogs();
    }

    @Override
    public void logProcessorType() {
        LocalDate today = LocalDate.ofInstant(Instant.now(), Constants.LONDON_ZONE_ID);
        LocalDate startDate = LocalDate.ofInstant(Instant.now().minus(dataRetentionPeriod, ChronoUnit.DAYS), Constants.LONDON_ZONE_ID);
        logger.info("Processing S3 bucket for CDN assets and requests made to retrieve them from access logs between the dates {} <---> {})",startDate, today);
    }

    @Override
    public void saveAssetLogs(AssetUsageReport successfulAssetRequestTotals, AssetUsageReport successfulAssetRequestTotalsForPeriod, List<AssetAccessLog> failedAssetAccessLogs, List<AssetAccessLog> assetAccessLogs) {
        analysisOutputInterface.saveRawData(assetAccessLogs);
        analysisOutputInterface.saveFailedAssetsRequests(failedAssetAccessLogs);
        analysisOutputInterface.saveSuccessfulAssetRequestsTotals(successfulAssetRequestTotals);
        analysisOutputInterface.saveSuccessfulAssetRequestsTotalsForPeriod(successfulAssetRequestTotalsForPeriod);
    }
}
