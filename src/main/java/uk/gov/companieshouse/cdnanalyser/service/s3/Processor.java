package uk.gov.companieshouse.cdnanalyser.service.s3;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import uk.gov.companieshouse.cdnanalyser.configuration.Constants;
import uk.gov.companieshouse.cdnanalyser.models.AssetAccessLog;
import uk.gov.companieshouse.cdnanalyser.models.AssetUsageReport;
import uk.gov.companieshouse.cdnanalyser.service.Util;
import uk.gov.companieshouse.cdnanalyser.service.interfaces.AnalysisInputInterface;
import uk.gov.companieshouse.cdnanalyser.service.interfaces.AnalysisOutputInterface;


@Service
public class Processor {

    private final AnalysisInputInterface analysisInputInterface;

    private final AnalysisOutputInterface analysisOutputInterface;

    private final String accessLogFilterInPath;

    private static final Logger logger = LoggerFactory.getLogger(Constants.APPLICATION_NAME_SPACE);

    public Processor(AnalysisInputInterface analysisInputInterface, AnalysisOutputInterface analysisOutputInterface,@Value("${cdn.access.logs.filterinpath}") String accessLogFilterInPath){
        this.analysisOutputInterface = analysisOutputInterface;
        this.analysisInputInterface = analysisInputInterface;
        this.accessLogFilterInPath = accessLogFilterInPath;
    }

    public void handleAssets() {

        List<String> assets = analysisInputInterface.readAssets();
        logger.info("The number of assets found is: {}", assets.size());

        Set<AssetAccessLog> assetAccessLogs = analysisInputInterface.readAccessLogs();

        logger.info("The number of asset access logs found is: {}", assetAccessLogs.size());

        List<AssetAccessLog> existingLogs = analysisInputInterface.readRawAssetAccessLogs();

        logger.info("The number of existing asset access logs found is: {}", existingLogs.size());

        existingLogs.forEach(existingLog ->{
            assetAccessLogs.add(existingLog);
        } );

        if (!assetAccessLogs.isEmpty() && ! assets.isEmpty()) {
            processAssetAccessLogs(assetAccessLogs, assets);
        } else {
            logger.info("No reports will be produced due to missing data.");
        }
    }

    public Set<AssetAccessLog> convertLogsToAssetAccessLogs() {
        return analysisInputInterface.readAccessLogs();
    }

    private void saveAssetLogs(AssetUsageReport successfulAssetRequestTotals, List<AssetAccessLog> failedAssetAccessLogs, Set<AssetAccessLog> assetAccessLogs) {
        analysisOutputInterface.saveRawData(assetAccessLogs);
        analysisOutputInterface.saveFailedAssetsRequests(failedAssetAccessLogs);
        analysisOutputInterface.saveSuccessfulAssetRequests(successfulAssetRequestTotals);
    }

    private void processAssetAccessLogs(Set<AssetAccessLog> assetAccessLogs, List<String> assets) {

        List<AssetUsageReport> successfulAssetUsageReports = collectSuccessfulAssetUsageReports(assetAccessLogs, assets);
        logger.debug("logging the assetUsageReports: {}", successfulAssetUsageReports);

        List<AssetAccessLog> failedAssetAccessLogs = collectFailedRequests(assetAccessLogs);
        logger.info("Of the {} assets access logs, {} will be saved as failed asset requests", assetAccessLogs.size(), failedAssetAccessLogs.size());

        AssetUsageReport assetUsageReportTotal = Util.calculateAssetRequestTotals(successfulAssetUsageReports);

        saveAssetLogs(assetUsageReportTotal, failedAssetAccessLogs, assetAccessLogs);
    }

    private List<AssetAccessLog> collectFailedRequests(Set<AssetAccessLog> assetAccessLogs) {
        return assetAccessLogs.stream()
                              .filter(assetAccessLog -> assetAccessLog.getStatusCode() >= 400)
                              .collect(Collectors.toList());
    }

    private List<AssetUsageReport> collectSuccessfulAssetUsageReports(Set<AssetAccessLog> assetAccessLogs, List<String> assets) {

        List<AssetAccessLog> successfulAssetRequests =  assetAccessLogs.stream()
                                                            .filter(t -> t.getStatusCode() < 400)
                                                            .collect(Collectors.toList());

        logger.info("Of the {} assets access logs, {} will be saved in usage reports", assetAccessLogs.size(), successfulAssetRequests.size());


        Map<Instant, AssetUsageReport> assetUsageReportsByDate = new HashMap<>();

        successfulAssetRequests.forEach(assetAccessLog -> {
            Instant dateOfLog = assetAccessLog.getTimestamp().truncatedTo(ChronoUnit.DAYS);
            String filename = assetAccessLog.getAsset();

            if (filename.startsWith(accessLogFilterInPath)) {
                filename = assetAccessLog.getAsset().substring(1 ).substring(accessLogFilterInPath.length()-1, assetAccessLog.getAsset().length() - 1);
            }
            // Create a new assetUsageReport, initialising the hashMap of assets to 0
            AssetUsageReport assetUsageLog = assetUsageReportsByDate.computeIfAbsent(dateOfLog, key ->new AssetUsageReport(dateOfLog, assets.stream().collect(Collectors.toMap(asset -> asset, requestCount -> 0))));
            if (!assetUsageLog.getAssetAccessCount().containsKey(filename)) {
                logger.warn("Asset {} not found in the list of identified CDN assets", filename);
                return;
            }
            int value = assetUsageLog.getAssetAccessCount().get(filename);

            //updating the count for the matching key/value
            assetUsageLog.getAssetAccessCount().put(filename, ++value);
        });

        return new ArrayList<>(assetUsageReportsByDate.values());
    }
}
