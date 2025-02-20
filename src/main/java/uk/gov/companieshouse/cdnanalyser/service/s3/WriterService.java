package uk.gov.companieshouse.cdnanalyser.service.s3;


import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import uk.gov.companieshouse.cdnanalyser.configuration.Constants;
import uk.gov.companieshouse.cdnanalyser.models.AssetAccessLog;
import uk.gov.companieshouse.cdnanalyser.models.AssetUsageReport;
import uk.gov.companieshouse.cdnanalyser.service.interfaces.AnalysisOutputInterface;

@Service
 public class WriterService implements AnalysisOutputInterface{

    private final S3Client s3Client;

    private final String cdnAnalysisBucket;

    private static final Logger logger = LoggerFactory.getLogger("cdnAnalyser");

    public WriterService(S3Client s3Client, @Value("${cdn.analysis.bucket}") String cdnAnalysisBucket) {
        this.s3Client = s3Client;
        this.cdnAnalysisBucket = cdnAnalysisBucket;
    }

    @Override
    public void saveFailedAssetsRequests(List<AssetAccessLog> assetAccessLogsWithErrors) {

        String startDate = LocalDate.ofInstant(assetAccessLogsWithErrors.get(0).getTimestamp(), Constants.LONDON_ZONE_ID).toString() ;
        String endDate = LocalDate.ofInstant(assetAccessLogsWithErrors.get(assetAccessLogsWithErrors.size() - 1).getTimestamp(), Constants.LONDON_ZONE_ID).toString();

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
            .bucket(cdnAnalysisBucket)
            .key("failed-asset-requests__" + startDate + "__" +endDate + ".json")
            .build();

        ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .enable(SerializationFeature.INDENT_OUTPUT);

        String failedAssetRequests = assetAccessLogsWithErrors.stream()
            .map(assetAccessLog -> {
                try {
                    return objectMapper.writeValueAsString(assetAccessLog);
                } catch (Exception e) {
                    logger.error("Failed to convert assetAccessLog to JSON {}", e.getMessage());
                    return null;
                }
            })
            .filter(json -> json != null)
            .collect(Collectors.joining("\n"));


        if (! failedAssetRequests.isEmpty()) {
            s3Client.putObject(putObjectRequest,RequestBody.fromByteBuffer(ByteBuffer.wrap(failedAssetRequests.getBytes(StandardCharsets.UTF_8))));
         } else {
            logger.error("Failed to save raw data to S3 bucket as assetRequests is empty");
         }
    }

    @Override
    public void saveRawData(List<AssetAccessLog> assetAccessLogs) {
        String startDate = LocalDate.ofInstant(assetAccessLogs.get(0).getTimestamp(), Constants.LONDON_ZONE_ID).toString() ;
        String endDate = LocalDate.ofInstant(assetAccessLogs.get(assetAccessLogs.size() - 1).getTimestamp(), Constants.LONDON_ZONE_ID).toString();

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
            .bucket(cdnAnalysisBucket)
            .key("raw-asset-access-data__" + startDate + "__" + endDate + ".json")
            .build();

        ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .enable(SerializationFeature.INDENT_OUTPUT);

        String assetRequests = assetAccessLogs.stream()
            .map(assetAccessLog -> {
                try {
                    return objectMapper.writeValueAsString(assetAccessLog);
                } catch (Exception e) {
                    logger.error("Failed to convert assetAccessLog to JSON {}", e.getMessage());
                    return null;
                }
            })
            .filter(json -> json != null)
            .collect(Collectors.joining("\n"));
        if (! assetRequests.isEmpty()) {
            s3Client.putObject(putObjectRequest,RequestBody.fromByteBuffer(ByteBuffer.wrap(assetRequests.getBytes(StandardCharsets.UTF_8))));
         } else {
            logger.error("Failed to save raw data to S3 bucket as assetRequests is empty");
         }
    }


    @Override
    public void saveSuccessfulAssetRequestsTotals(AssetUsageReport assetUsageReportTotals) {

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
        .bucket(cdnAnalysisBucket)
        .key("asset-usage-report-total.json")
        .build();

        ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .enable(SerializationFeature.INDENT_OUTPUT);

        String json;
        try {
            json = objectMapper.writeValueAsString(assetUsageReportTotals);
            s3Client.putObject(putObjectRequest,RequestBody.fromByteBuffer(ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8))));
        } catch (JsonProcessingException e) {
            logger.error("Failed to save asset usage total report S3 bucket {}", e.getMessage());
        }
    }

    @Override
    public void saveSuccessfulAssetRequestsTotalsForPeriod(AssetUsageReport assetUsageReportTotal) {
        String today = LocalDate.ofInstant(Instant.now(), Constants.LONDON_ZONE_ID).toString() ;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
        .bucket(cdnAnalysisBucket)
        .key("asset-usage-report-total"+ "-" + today + ".json")
        .build();

        ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .enable(SerializationFeature.INDENT_OUTPUT);

        String json;
        try {
            json = objectMapper.writeValueAsString(assetUsageReportTotal);
            s3Client.putObject(putObjectRequest,RequestBody.fromByteBuffer(ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8))));
        } catch (JsonProcessingException e) {
            logger.error("Failed to save asset usage total report S3 bucket {}", e.getMessage());
        }
    }
}