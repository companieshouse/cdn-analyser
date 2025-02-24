package uk.gov.companieshouse.cdnanalyser.service.s3;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import uk.gov.companieshouse.cdnanalyser.configuration.Constants;
import uk.gov.companieshouse.cdnanalyser.models.AssetAccessLog;
import uk.gov.companieshouse.cdnanalyser.models.S3File;
import uk.gov.companieshouse.cdnanalyser.service.Util;
import uk.gov.companieshouse.cdnanalyser.service.interfaces.AnalysisInputInterface;

@Service
public class ReaderService implements AnalysisInputInterface{

    private final S3Client s3Client;

    private final String accessLogFileBucket;

    private final String cdnAssetBucket;

    private final String accessLogFilterInPath;

    private final String cdnAssetFilterInPath;

    private final String cdnAnalysisBucket;

    private static final Logger logger = LoggerFactory.getLogger(Constants.APPLICATION_NAME_SPACE);

    private final ObjectMapper objectMapper = new ObjectMapper()
                                    .enable(SerializationFeature.INDENT_OUTPUT)
                                    .findAndRegisterModules()
                                    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    public ReaderService(S3Client s3Client, @Value("${cdn.access.logs.bucket}") String accessLogFileBucket,  @Value("${cdn.assets.bucket}")
     String cdnAssetBucket,
    @Value("${cdn.access.logs.filterinpath}") String accessLogFilterInPath, @Value("${cdn.assets.filterinpath}") String cdnAssetFilterInPath, @Value("${cdn.analysis.bucket}") String cdnAnalysisBucket) {
        this.s3Client = s3Client;
        this.accessLogFileBucket = accessLogFileBucket;
        this.cdnAssetBucket = cdnAssetBucket;
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
        } catch (UncheckedIOException| S3Exception e) {
            logger.error("Error processing files in bucket {} : {}", cdnAssetBucket, e.getMessage());
        }
        return assetKeys;
    }

    @Override
    public  Set<AssetAccessLog> readAccessLogs() {
        List<S3File> logFiles = parseAccessLogBucket();
        Set<AssetAccessLog> assetAccessLogs = logFiles.stream()
                .flatMap(s3File -> s3File.getContent().lines())
                .map(log -> Util.parseLogEntry(log, accessLogFilterInPath))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
                return assetAccessLogs;
    }

    private List<S3File> parseAccessLogBucket() {
        List<S3File> files = new ArrayList<>();

        String continuationToken = null;
         try {
            do {
                ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder()
                                                                .bucket(accessLogFileBucket)
                                                                .continuationToken(continuationToken).maxKeys(1000)
                                                                .build();

                ListObjectsV2Response listObjResponse = s3Client.listObjectsV2(listObjectsV2Request);

                List<S3File> batchFiles = listObjResponse.contents().parallelStream()
                        .map(s3Object -> processS3File(s3Object, accessLogFileBucket))
                        .collect(Collectors.toList());
                files.addAll(batchFiles);
                continuationToken = listObjResponse.nextContinuationToken();
            } while (continuationToken != null);
        }catch (NoSuchBucketException e){
            logger.error("Bucket {} does not exist", accessLogFileBucket);
        } catch (SdkClientException e) {
            logger.error("Error listing objects in bucket {} :", accessLogFileBucket, e.getMessage());
        } catch (UncheckedIOException e) {
            logger.error("Error processing files in bucket {} :", accessLogFileBucket, e.getMessage());
        }

        logger.info("" + files.size() + " access log files found in bucket: " + accessLogFileBucket);

        return files;
    }

    @Override
    public List<AssetAccessLog> readRawAssetAccessLogs() {
        List<AssetAccessLog> assetAccessLogs = new ArrayList<>();
        GetObjectRequest objectRequest = GetObjectRequest
            .builder()
            .key("raw-asset-access-data.json")
            .bucket(cdnAnalysisBucket)
            .build();
        try{
            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObject(objectRequest, ResponseTransformer.toBytes());
            byte[] data = objectBytes.asByteArray();

            assetAccessLogs = objectMapper.readValue(data, new TypeReference<List<AssetAccessLog>>(){});

        } catch (IOException e) {
            logger.error("Error has occurred converting the data into an object");
        } catch(NoSuchKeyException e){
            logger.debug("{} doesn't exist so will be created.", objectRequest.key());
        } catch(NoSuchBucketException e){
            logger.error("Bucket {} does not exist", cdnAnalysisBucket);
        } catch(SdkClientException e){
            logger.error("Error obtaining data from the bucket {}", cdnAnalysisBucket);
        }

        return assetAccessLogs;
    }

    private S3File processS3File(S3Object content,String bucket) {
        String filePath = content.key();
        S3File file = new S3File(filePath, content.lastModified());
        try (InputStream inputStream = s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(filePath).build());
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                file.setContent(reader.lines().collect(Collectors.joining(System.lineSeparator())));
        } catch (IOException e) {
            logger.error("Error reading content from S3 object {} : {}", filePath, e.getMessage());
            throw new UncheckedIOException(e);
        }
        return file;
    }
}