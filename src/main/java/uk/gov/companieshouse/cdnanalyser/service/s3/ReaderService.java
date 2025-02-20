package uk.gov.companieshouse.cdnanalyser.service.s3;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import uk.gov.companieshouse.cdnanalyser.models.AssetAccessLog;
import uk.gov.companieshouse.cdnanalyser.models.AssetUsageReport;
import uk.gov.companieshouse.cdnanalyser.models.S3File;
import uk.gov.companieshouse.cdnanalyser.service.interfaces.AnalysisInputInterface;

@Service
public class ReaderService implements AnalysisInputInterface{

    private final S3Client s3Client;

    private final String accessLogFileBucket;

    private final String cdnAssetBucket;

    private final boolean processTodaysLogsOnly;

    private final String accessLogFilterInPath;

    private final String cdnAssetFilterInPath;

    private final String cdnAnalysisBucket;

    private static final Logger logger = LoggerFactory.getLogger("cdnAnalyser");

    public ReaderService(S3Client s3Client, @Value("${cdn.access.logs.bucket}") String accessLogFileBucket,  @Value("${cdn.assets.bucket}")
     String cdnAssetBucket, @Value("${cdn.access.logs.processlogsfromtodayonly}")boolean processTodaysLogsOnly,
    @Value("${cdn.access.logs.filterinpath}") String accessLogFilterInPath, @Value("${cdn.assets.filterinpath}") String cdnAssetFilterInPath, @Value("${cdn.analysis.bucket}") String cdnAnalysisBucket) {
        this.s3Client = s3Client;
        this.accessLogFileBucket = accessLogFileBucket;
        this.cdnAssetBucket = cdnAssetBucket;
        this.processTodaysLogsOnly = processTodaysLogsOnly;
        this.accessLogFilterInPath = accessLogFilterInPath;
        this.cdnAssetFilterInPath = cdnAssetFilterInPath;
        this.cdnAnalysisBucket = cdnAnalysisBucket;
    }

    @Override
    public List<String> readAssets() {
        List<String> assetKeys = new ArrayList<>();
        try {
            assetKeys = s3Client
                            .listObjectsV2(
                                ListObjectsV2Request.builder()
                                    .bucket(cdnAssetBucket)
                                    .build())
                                    .contents()
                                    .stream()
                                    .filter(s3Object -> s3Object.key().contains(cdnAssetFilterInPath))
                                    .map(S3Object::key).collect(Collectors.toList());
        }catch (NoSuchBucketException e){
            logger.error("Bucket {} does not exist", cdnAssetBucket);
        } catch (SdkClientException e) {
            logger.error("Error listing objects in bucket {} : {}", cdnAssetBucket, e.getMessage());
        } catch (UncheckedIOException e) {
            logger.error("Error processing files in bucket {} : {}", cdnAssetBucket, e.getMessage());
        }
        catch (S3Exception e) {
            logger.error("Bucket does not exist: {}", cdnAssetBucket);
        }
        return assetKeys;
    }

    @Override
    public List<AssetAccessLog> readAccessLogs() {
        List<S3File> logFiles = parseBucketForFiles();
        List<AssetAccessLog> assetAccessLogs = logFiles.stream()
                // Filter logs by date based on the processTodaysLogsOnly flag
                .filter(filterLogsByDate())
                .flatMap(s3File -> s3File.getContent().stream())
                .map(this::parseLogEntry)
                .filter(assetAccessLog -> assetAccessLog != null)
                .collect(Collectors.toList());

                return assetAccessLogs;
    }

    private List<S3File> parseBucketForFiles() {
        List<S3File> files = new ArrayList<>();

        try {
            ListObjectsV2Response listObjResponse = s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(accessLogFileBucket).build());
            files = listObjResponse.contents().parallelStream()
                    .filter(s3Object -> s3Object.key().contains(accessLogFilterInPath))
                    .map(s3Object -> processAccessLogFile(s3Object, accessLogFileBucket))
                    .collect(Collectors.toList());
        }catch (NoSuchBucketException e){
            logger.error("Bucket {} does not exist", accessLogFileBucket);
        } catch (SdkClientException e) {
            logger.error("Error listing objects in bucket {} :", accessLogFileBucket, e.getMessage());
        } catch (UncheckedIOException e) {
            logger.error("Error processing files in bucket {} :", accessLogFileBucket, e.getMessage());
        }
        return files;
    }

    private S3File processAccessLogFile(S3Object content,String bucket) {
        String filePath = content.key();
        S3File file = new S3File(filePath, content.lastModified());
        try (InputStream inputStream = s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(filePath).build());
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                file.setContent(reader.lines().collect(Collectors.toList()));
        } catch (IOException e) {
            logger.error("Error reading content from S3 object {} : {}", filePath, e.getMessage());
            throw new UncheckedIOException(e);
        }
        return file;
    }

    private AssetAccessLog parseLogEntry(String logEntry) {
        if (logEntry.contains("REST.GET.OBJECT")){
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
            Integer statusCode;
            try {

                int startOfStatusCode = logEntry.indexOf(endOfRequest) + endOfRequest.length() + 1;
                int endOfStatusCode = startOfStatusCode + 3;

                statusCode = Integer.valueOf(logEntry.substring(startOfStatusCode, endOfStatusCode));

            } catch (StringIndexOutOfBoundsException | NumberFormatException e) {
                logger.error("Invalid log entry, STATUS_CODE not present : {}", logEntry);
                return null;
            }
            AssetAccessLog log = null;
            log = new AssetAccessLog();
            log.setStatusCode(statusCode);
            log.setRequestType(requestDetail[0]);
            log.setAsset(requestDetail[1].substring(1 ));
            log.setTimestamp(timestamp);
            return log;
        } else {
            logger.error("Invalid log entry, log can't be parsed", logEntry);
            return null;
        }
    }

    private Instant parseTimestamp(String logEntryPart) {
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
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d/MMM/yyyy:HH:mm:ss ZZZ", Locale.ENGLISH);
        try{
            ZonedDateTime dateTime = ZonedDateTime.parse(requiredString, formatter);
            return dateTime.toInstant();
        } catch (DateTimeParseException e){
            logger.error("invalid date in log entry {}", logEntryPart);
            return null;
        }
    }

    private Predicate<? super S3File> filterLogsByDate() {
        Instant todaysDate = Instant.now().truncatedTo(ChronoUnit.DAYS);
        return s3File -> {

            if (processTodaysLogsOnly && ! s3File.getModifiedDate().truncatedTo(ChronoUnit.DAYS).equals(todaysDate)){
                logger.debug("The log entry: " + s3File + " will not be parsed.");
                return false;
            } else {
                logger.debug("The log entry: " + s3File + " will be parsed.");
                return true;
            }
        };
    }

    @Override
    public List<AssetUsageReport> readAnalysisReports() {
        List<AssetUsageReport> files = new ArrayList<>();

        try {
            ListObjectsV2Response listObjResponse = s3Client.
                                                    listObjectsV2(
                                                        ListObjectsV2Request.builder()
                                                            .bucket(cdnAnalysisBucket)
                                                            .build()
                                                        );
            files = listObjResponse.contents().parallelStream()
                .filter(s3Object -> ! s3Object.key().contains("failed-asset-requests") && ! s3Object.key().contains("asset-access-logs") )
                .map(s3Object -> processAssetUsageReportFile(s3Object, cdnAnalysisBucket))
                .collect(Collectors.toList());
        }catch (NoSuchBucketException e){
            logger.error("Bucket does not exist: {}", cdnAnalysisBucket);
        } catch (SdkClientException e) {
            logger.error("Error listing objects in bucket: {}", cdnAnalysisBucket, e.getMessage());
        } catch (UncheckedIOException e) {
            logger.error("Error processing files in bucket: {}", cdnAnalysisBucket, e.getMessage());
        }
        return files;
    }

    private AssetUsageReport processAssetUsageReportFile(S3Object content,String bucket) {
        String filePath = content.key();

        ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .enable(SerializationFeature.INDENT_OUTPUT);
        AssetUsageReport assetUsageReport = new AssetUsageReport();
        try (InputStream inputStream = s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(filePath).build());
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                assetUsageReport = objectMapper.readValue(inputStream, AssetUsageReport.class);
        } catch (IOException e) {
            logger.error("Error reading content from S3 object: {} {}", filePath, e.getMessage());
            throw new UncheckedIOException(e);
        }

        return assetUsageReport;
    }

}