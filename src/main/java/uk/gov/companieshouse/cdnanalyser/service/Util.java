package uk.gov.companieshouse.cdnanalyser.service;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.gov.companieshouse.cdnanalyser.configuration.Constants;
import uk.gov.companieshouse.cdnanalyser.models.AssetAccessLog;
import uk.gov.companieshouse.cdnanalyser.models.AssetRequestFailureReport;
import uk.gov.companieshouse.cdnanalyser.models.AssetUsageReport;

public class Util {

    private static final Logger logger = LoggerFactory.getLogger(Constants.APPLICATION_NAME_SPACE);

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d/MMM/yyyy:HH:mm:ss ZZZ", Locale.ENGLISH);

    private Util() {
        throw new IllegalStateException("Utility class");
    }

    public static List<AssetRequestFailureReport> calculateFailedAssetRequests(List<AssetAccessLog> assetAccessLogs, List<AssetRequestFailureReport> existingAssetFailureReport) {
        Map<String, AssetRequestFailureReport> failureMap = new HashMap<>();
        assetAccessLogs.stream()
                .filter(assetAccessLog -> assetAccessLog.getStatusCode() >= 400)
                .forEach(assetAccessLog -> {
                    String key = assetAccessLog.getAsset() + "-" + assetAccessLog.getStatusCode();
                    AssetRequestFailureReport failureCount = failureMap.computeIfAbsent(key, assetFailure -> new AssetRequestFailureReport(assetAccessLog.getAsset(), assetAccessLog.getStatusCode(), 0));
                    failureCount.setFailureCount(failureCount.getFailureCount() + 1);
                });
        return updateFailureReport(existingAssetFailureReport,failureMap);
    }

    private static List<AssetRequestFailureReport> updateFailureReport(List<AssetRequestFailureReport> existingAssetFailureReport, Map<String, AssetRequestFailureReport> failedAssetRequests) {
        existingAssetFailureReport.forEach(
            assetFailureCount -> {
                String key = assetFailureCount.getAsset()+ "-" + assetFailureCount.getFailureCode();
                AssetRequestFailureReport failureCount = failedAssetRequests.computeIfAbsent(key, assetFailure -> new AssetRequestFailureReport(assetFailureCount.getAsset(), assetFailureCount.getFailureCode(), 0));
                failureCount.setFailureCount(failureCount.getFailureCount() + assetFailureCount.getFailureCount());
        });
        return new ArrayList<>(failedAssetRequests.values());
    }

    public static AssetUsageReport calculateAssetRequestTotals(List<AssetUsageReport> assetUsageReports) {
        AssetUsageReport assetUsageReportTotal = new AssetUsageReport("total", new HashMap<>());
        Map<String, Integer> assetAccessCount = new HashMap<>();

        assetUsageReports.forEach(assetUsageReport -> {
            assetUsageReport.getAssetAccessCount().forEach((asset, count) -> {
                assetAccessCount.merge(asset, count, Integer::sum);
            });
        });

        assetAccessCount.forEach((asset, count) -> {
            assetUsageReportTotal.getAssetAccessCount().merge(asset, count, Integer::sum);
        });

        // Sort the map from high to low by value
        Map<String, Integer> sortedAssetAccessCount = assetUsageReportTotal.getAssetAccessCount().entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
            assetUsageReportTotal.setAssetAccessCount(sortedAssetAccessCount);
        return assetUsageReportTotal;
    }

    public static AssetAccessLog parseLogEntry(String logEntry, String accessLogFilterInPath) {
        if (logEntry.contains(" REST.GET.OBJECT ")){
            //Split the string based on `"` to obtain the REST request type and requested asset
            String[] logSections = logEntry.split("\"");
            String[] requestDetail = logSections[1].split(" ");

            if (requestDetail.length != 3 ){
                logger.error("Invalid log entry, request type not present : {}", logEntry);
                return null;
            }

            Instant timestamp = parseTimestamp(logSections[0]);
            if (timestamp == null) {
                return null;
            }
            String endOfRequest = "HTTP/1.1\"";
            Integer statusCode = null;
            try {

                int startOfStatusCode = logEntry.indexOf(endOfRequest) + endOfRequest.length() + 1;
                int endOfStatusCode = startOfStatusCode + 3;

                statusCode = Integer.valueOf(logEntry.substring(startOfStatusCode, endOfStatusCode));

            } catch (StringIndexOutOfBoundsException | NumberFormatException e) {
                logger.error("Invalid log entry, STATUS_CODE not present : {}", logEntry);
            }

            String asset = requestDetail[1].substring(1 );
            if (! asset.contains(accessLogFilterInPath)){
                logger.debug("The asset: {} is not a valid asset", asset);
                return null;
            }
            AssetAccessLog log = null;
            log = new AssetAccessLog();
            log.setStatusCode(statusCode);
            log.setRequestType(requestDetail[0]);
            log.setAsset(requestDetail[1].substring(1 ));
            log.setTimestamp(timestamp);

            if (statusCode != null && requestDetail[0] != null && requestDetail[1] != null && timestamp != null) {
                logger.debug("Log entry parsed successfully: {}", log);
            return log;
            } else {
                logger.error("Log entry, log can't be parsed", logEntry);
                return null;
            }
        } else {
            logger.debug("Log entry not of the required type, log can't be parsed", logEntry);
            return null;
        }
    }

    private static Instant parseTimestamp(String logEntryPart) {
        //obtain the Date/Time from within the `[ ]` brackets
        int indexOfStartOfDate = logEntryPart.indexOf("[");
        int indexOfEndStartOfDate = logEntryPart.indexOf("]");

        if (indexOfStartOfDate == -1) {
            logger.error("Invalid log entry, start marker for timestamp not present: {}", logEntryPart);
            return null;
        }

        if (indexOfEndStartOfDate == -1) {
            logger.error("Invalid log entry, end marker for timestamp not present: {}", logEntryPart);
            return null;
        }

        if (indexOfEndStartOfDate < indexOfStartOfDate) {
            logger.error("Invalid log entry, start marker is after end marker: {}", logEntryPart);
            return null;
        }

        String requiredString = logEntryPart.substring(logEntryPart.indexOf("[") + 1, logEntryPart.indexOf("]"));
        try{
            ZonedDateTime dateTime = ZonedDateTime.parse(requiredString, formatter);
            return dateTime.toInstant();
        } catch (DateTimeParseException e){
            logger.error("invalid date in log entry {}", logEntryPart);
            return null;
        }
    }
}
