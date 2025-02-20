package uk.gov.companieshouse.cdnanalyser.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.gov.companieshouse.cdnanalyser.configuration.Constants;
import uk.gov.companieshouse.cdnanalyser.models.AssetAccessLog;
import uk.gov.companieshouse.cdnanalyser.models.AssetUsageReport;
import uk.gov.companieshouse.cdnanalyser.service.interfaces.AnalysisInputInterface;

public abstract class AbstractProcessor {

    private final AnalysisInputInterface analysisInputInterface;

    private static final Logger logger = LoggerFactory.getLogger("cdnAnalyser");

    private final int dataRetentionPeriod;

    public abstract List<AssetAccessLog> convertLogsToAssetAccessLogs();

    public abstract void logProcessorType();

    public abstract void saveAssetLogs(AssetUsageReport successfulAssetRequestTotals, AssetUsageReport successfulAssetRequestTotalsForPeriod, List<AssetAccessLog> failedAssetAccessLogs, List<AssetAccessLog> assetAccessLogs);

    public AbstractProcessor(AnalysisInputInterface analysisInputInterface, int dataRetentionPeriod) {
        this.analysisInputInterface = analysisInputInterface;
        this.dataRetentionPeriod = dataRetentionPeriod;
    }

    public void handleAssets() {

        logProcessorType();

        List<String> assets = analysisInputInterface.readAssets();
        logger.info("The number of assets found is: {}", assets.size());

        List<AssetAccessLog> assetAccessLogs = analysisInputInterface.readAccessLogs();
        logger.info("The number of asset access logs found is: {}", assetAccessLogs.size());

        List<AssetUsageReport> assetUsageReports = analysisInputInterface.readAnalysisReports();

        if (!assetAccessLogs.isEmpty() || ! assets.isEmpty()) {
            processAssetAccessLogs(assetUsageReports, assetAccessLogs, assets);
        } else {
            logger.info("No asset requests or assets have been found");
        }
    }

    private void processAssetAccessLogs(List<AssetUsageReport> assetUsageReports, List<AssetAccessLog> assetAccessLogs, List<String> assets) {

        assetAccessLogs = filterLogsBasedOnRetentionPeriod(assetAccessLogs);
        logger.debug("logging the assetAccessLogs: {}", assetAccessLogs);

        List<AssetUsageReport> successfulAssetUsageReports = collectSuccessfulAssetUsageReports(assetAccessLogs, assets);
        logger.debug("logging the assetUsageReports: {}", successfulAssetUsageReports);

        int count = calculateTotalAssetAccessCount(successfulAssetUsageReports);
        logger.info("Of the {} assets access logs, {} will be saved in usage reports", assetAccessLogs.size(), count);

        List<AssetAccessLog> failedAssetAccessLogs = collectFailedRequests(assetAccessLogs);
        logger.info("Of the {} assets access logs, {} will be saved as failed asset requests", assetAccessLogs.size(), failedAssetAccessLogs.size());

        AssetUsageReport assetUsageReportForPeriod = calculateAssetRequestTotalsForPeriod(successfulAssetUsageReports);

        AssetUsageReport assetUsageReportTotal = calculateAssetRequestTotals(successfulAssetUsageReports, assetUsageReportForPeriod);

        saveAssetLogs(assetUsageReportTotal, assetUsageReportForPeriod, failedAssetAccessLogs, assetAccessLogs);
    }

    private AssetUsageReport calculateAssetRequestTotalsForPeriod(List<AssetUsageReport> assetUsageReports) {
        Map<String, Integer> assetAccessCount = new HashMap<>();

        assetUsageReports.forEach(assetUsageReport -> {
            assetUsageReport.getAssetAccessCount().forEach((asset, count) -> {
                assetAccessCount.merge(asset, count, Integer::sum);
            });
        });

        String today = LocalDate.ofInstant(Instant.now(), Constants.LONDON_ZONE_ID).toString();
        return new AssetUsageReport("total-"+today, assetAccessCount);
    }

    private AssetUsageReport calculateAssetRequestTotals(List<AssetUsageReport> assetUsageReports, AssetUsageReport assetUsageReportTotal) {
        Map<String, Integer> assetAccessCount = new HashMap<>();

        assetUsageReports.forEach(assetUsageReport -> {
            assetUsageReport.getAssetAccessCount().forEach((asset, count) -> {
                assetAccessCount.merge(asset, count, Integer::sum);
            });
        });


        assetAccessCount.forEach((asset, count) -> {
            assetUsageReportTotal.getAssetAccessCount().merge(asset, count, Integer::sum);
        });

        return new AssetUsageReport("total", new HashMap<>(assetUsageReportTotal.getAssetAccessCount()));
    }

    private List<AssetAccessLog> filterLogsBasedOnRetentionPeriod(List<AssetAccessLog> assetAccessLogs) {

        Instant startOfRetentionPeriod = Instant.now().minus(dataRetentionPeriod, ChronoUnit.DAYS);

        logger.debug("startOfRetentionPeriod: {}", startOfRetentionPeriod);

        return assetAccessLogs.stream()
                .filter(assetAccessLog -> assetAccessLog.getTimestamp().isAfter(startOfRetentionPeriod))
                .sorted((assetAccessLog1, assetAccessLog2) -> assetAccessLog1.getTimestamp().compareTo(assetAccessLog2.getTimestamp()))
                .collect(Collectors.toList());
    }

    private int calculateTotalAssetAccessCount(List<AssetUsageReport> assetUsageReports) {
        return assetUsageReports.stream()
                .flatMap(assetUsageReport -> assetUsageReport.getAssetAccessCount().entrySet().stream())
                .peek(entry -> logger.debug("Asset: {} Count: {}", entry.getKey(), entry.getValue()))
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    private List<AssetAccessLog> collectFailedRequests(List<AssetAccessLog> assetAccessLogs) {
        return assetAccessLogs.stream()
                              .filter(assetAccessLog -> !assetAccessLog.isSuccessful())
                              .collect(Collectors.toList());
    }

    private List<AssetUsageReport> collectSuccessfulAssetUsageReports(List<AssetAccessLog> assetAccessLogs, List<String> assets) {

        List<AssetAccessLog> successfulAssetRequests =  assetAccessLogs.stream()
                                                            .filter(AssetAccessLog::isSuccessful)
                                                            .collect(Collectors.toList());

        Map<Instant, AssetUsageReport> assetUsageReportsByDate = new HashMap<>();

        successfulAssetRequests.forEach(assetAccessLog -> {
            Instant dateOfLog = assetAccessLog.getTimestamp().truncatedTo(ChronoUnit.DAYS);
            String filename = assetAccessLog.getAsset();

            // Create a new assetUsageReport, initialising the hashMap of assets to 0
            AssetUsageReport assetUsageLog = assetUsageReportsByDate.computeIfAbsent(dateOfLog, key ->new AssetUsageReport(dateOfLog, assets.stream().collect(Collectors.toMap(asset -> asset, requestCount -> 0))));
            Integer value = assetUsageLog.getAssetAccessCount().get(filename);

            //updating the count for the matching key/value
            assetUsageLog.getAssetAccessCount().put(filename, ++value);
        });

        return new ArrayList<>(assetUsageReportsByDate.values());
    }
}
