package uk.gov.companieshouse.cdnanalyser.service;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Object;
import uk.gov.companieshouse.cdnanalyser.configuration.Constants;
import uk.gov.companieshouse.cdnanalyser.models.AssetAccessLog;
import uk.gov.companieshouse.cdnanalyser.models.AssetUsageReport;
import uk.gov.companieshouse.cdnanalyser.service.s3.ReaderService;

public class AwsS3BucketServiceTest {

    private static final String accessLogFileBucket = "accessLogFileBucket";

    private static final String cdnAssetS3Bucket  =  "cdnAssetS3Bucket";

    private final static String cdnAnalysisBucket = "nonExistentBucket";

        private static final String accessLogFilterInPath = "";

        private static final String cdnAssetFilterInPath = "";

        private static S3Client s3ClientMock;

        private static ReaderService awsS3BucketService;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d/MMM/yyyy:HH:mm:ss ZZZ", Locale.ENGLISH);

        Instant now = Instant.now();

        ZonedDateTime today = now.atZone(Constants.LONDON_ZONE_ID);

        String todayString = today.format(formatter);

        String yesterdayString = today.minus(1, ChronoUnit.DAYS).format(formatter);

        @BeforeAll
        public static void setUp() {
            s3ClientMock = mock(S3Client.class);
            awsS3BucketService = new ReaderService(s3ClientMock, accessLogFileBucket, cdnAssetS3Bucket, true, accessLogFilterInPath, cdnAssetFilterInPath, cdnAnalysisBucket);
    }

    @Test
    public void testReadAssets() {
        List<S3Object>  mockS3Object =  Arrays.asList(S3Object.builder().key("asset1.txt").size(123L).build(),S3Object.builder().key("folder/asset2.txt").size(963L).build());
        ListObjectsV2Response mockResponse = ListObjectsV2Response.builder().contents(mockS3Object).build();

        when(s3ClientMock.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(mockResponse);
        List<String> result = awsS3BucketService.readAssets();
        assertEquals(2, result.size(), "The number of assets found is incorrect");
        assertEquals("asset1.txt", result.get(0), "The first asset is incorrect");
        assertEquals("folder/asset2.txt", result.get(1), "The second asset is incorrect");
    }

    @Test
    public void testReadAssetsSdkClientException() {
        when(s3ClientMock.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow(SdkClientException.create("test exception"));

        List<String> result = awsS3BucketService.readAssets();

        assertEquals(0, result.size(), "The number of assets found is incorrect");
    }

    @Test
    public void testReadAssetsUncheckedIOException() {
        when(s3ClientMock.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow(new UncheckedIOException(new IOException("test exception")));

        List<String> result = awsS3BucketService.readAssets();

        assertEquals(0, result.size(), "The number of assets found is incorrect");
    }


    @Test
    public void testReadLogSdkClientException() {
        when(s3ClientMock.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow(SdkClientException.create("test exception"));

        List<AssetAccessLog> result = awsS3BucketService.readAccessLogs();

        assertEquals(0, result.size(), "The number of logs found is incorrect");
    }

    @Test
    public void testReadLogUncheckedIOException() {
        when(s3ClientMock.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow(new UncheckedIOException(new IOException("test exception")));

        List<AssetAccessLog> result = awsS3BucketService.readAccessLogs();

        assertEquals(0, result.size(), "The number of logs found is incorrect");
    }

    @Test
    public void testReadLogsProcessOnlyTodaysLogs() {
        ResponseInputStream<GetObjectResponse> responseInputStreamFirstFile = createResponseInputStreamWithDate(todayString);
        ResponseInputStream<GetObjectResponse> responseInputStreamSecondFile = createResponseInputStreamWithDate(yesterdayString);

        List<S3Object> mockS3Object = Arrays.asList(
            S3Object.builder().key("logfile1.txt").size(123L).lastModified(now).build(),
            S3Object.builder().key("folder/logfile2.txt").size(963L).lastModified(now.minus(1, ChronoUnit.DAYS)).build()
        );
        ListObjectsV2Response mockResponse = ListObjectsV2Response.builder().contents(mockS3Object).build();

        when(s3ClientMock.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(mockResponse);
        when(s3ClientMock.getObject(any(GetObjectRequest.class)))
            .thenReturn(responseInputStreamFirstFile)
            .thenReturn(responseInputStreamSecondFile);

        List<AssetAccessLog> result = awsS3BucketService.readAccessLogs();

        assertEquals(1, result.size(), "The number of logs found is incorrect");
    }

    private ResponseInputStream<GetObjectResponse> createResponseInputStreamWithDate(String dateString) {
        String fileContent = "57f2f030b6e5545bca67c0389164fd7e495aef43831451cc8275ca1bcc012683 chs-cdn.development.ch.gov.uk [" + dateString + "] - svc:cloudfront.amazonaws.com J3QN4GNXQHX5GJTN REST.GET.OBJECT cidev/assets/fonts/bold.woff2 \"GET /cidev/assets/fonts/bold.woff2 HTTP/1.1\" 404 AccessDenied 243 - 21 - \"-\" \"-\" - YhkhaTdI2pOv2YNnWZiJ4bUCoQ5G0KveIQ2dqMRAtGAhB01cntX9mrp6vSMamiLw1IrKoEhG3xg= SigV4 ECDHE-RSA-AES128-GCM-SHA256 AuthHeader chs-cdn.development.ch.gov.uk.s3.eu-west-2.amazonaws.com TLSv1.2 - -";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8));
        GetObjectResponse getObjectResponse = GetObjectResponse.builder().build();
        return new ResponseInputStream<>(getObjectResponse, inputStream);
    }

    private ResponseInputStream<GetObjectResponse> createResponseInputStreamWithRequiredContent(String content) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        GetObjectResponse getObjectResponse = GetObjectResponse.builder().build();
        return new ResponseInputStream<>(getObjectResponse, inputStream);
    }

    @Test
    public void testReadLogsProcessAllLogs() {

        ResponseInputStream<GetObjectResponse> responseInputStreamFirstFile = createResponseInputStreamWithDate(todayString);
        ResponseInputStream<GetObjectResponse> responseInputStreamSecondFile = createResponseInputStreamWithDate(yesterdayString);

        List<S3Object> mockS3Object = Arrays.asList(
            S3Object.builder().key("logfile1.txt").size(123L).lastModified(now).build(),
            S3Object.builder().key("folder/logfile2.txt").size(963L).lastModified(now.minus(1, ChronoUnit.DAYS)).build()
        );
        ListObjectsV2Response mockResponse = ListObjectsV2Response.builder().contents(mockS3Object).build();

        when(s3ClientMock.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(mockResponse);
        when(s3ClientMock.getObject(any(GetObjectRequest.class)))
            .thenReturn(responseInputStreamFirstFile)
            .thenReturn(responseInputStreamSecondFile);
        ReaderService readerService = new ReaderService(s3ClientMock, accessLogFileBucket, cdnAssetS3Bucket, false, accessLogFilterInPath, cdnAssetFilterInPath, "");
        List<AssetAccessLog> result = readerService.readAccessLogs();

        assertEquals(2, result.size(), "The number of logs found is incorrect");
    }

    @Test
    public void testInvalidDate() {
        String fileContentSecondFile = "57f2f030b6e5545bca67c0389164fd7e495aef43831451cc8275ca1bcc012683 chs-cdn.development.ch.gov.uk [yh/Feb/2025:09:20:04 +0000] - svc:cloudfront.amazonaws.com J3QN4GNXQHX5GJTN REST.GET.OBJECT cidev/assets/fonts/bold.woff2 \"GET /cidev/assets/fonts/bold.woff2 HTTP/1.1\" 404 AccessDenied 243 - 21 - \"-\" \"-\" - YhkhaTdI2pOv2YNnWZiJ4bUCoQ5G0KveIQ2dqMRAtGAhB01cntX9mrp6vSMamiLw1IrKoEhG3xg= SigV4 ECDHE-RSA-AES128-GCM-SHA256 AuthHeader chs-cdn.development.ch.gov.uk.s3.eu-west-2.amazonaws.com TLSv1.2 - -";

        ResponseInputStream<GetObjectResponse> responseInputStreamSecondFile = createResponseInputStreamWithRequiredContent(fileContentSecondFile);

        List<S3Object>  mockS3Object =  Arrays.asList(
                                                S3Object.builder().key("logfile1.txt").size(123L).lastModified(now).build(),
                                                S3Object.builder().key("folder/logfile2.txt").size(963L).lastModified(now).build());
        ListObjectsV2Response mockResponse = ListObjectsV2Response.builder().contents(mockS3Object).build();

        when(s3ClientMock.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(mockResponse);
        when(s3ClientMock.getObject(any(GetObjectRequest.class)))
            .thenReturn(responseInputStreamSecondFile);

        List<AssetAccessLog> result = awsS3BucketService.readAccessLogs();

        assertEquals(0, result.size(), "The number of logs found is incorrect");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    public void testInvalidLogFormat(CapturedOutput output) {

        String logMissingStatusCode = "57f2f030b6e5545bca67c0389164fd7e495aef43831451cc8275ca1bcc012683 chs-cdn.development.ch.gov.uk [" + todayString +  "] - svc:cloudfront.amazonaws.com J3QN4GNXQHX5GJTN REST.GET.OBJECT cidev/assets/fonts/bold.woff2 \"GET /cidev/assets/fonts/bold.woff2 HTTP/1.1\" AccessDenied 243 - 21 - \"-\" \"-\" - YhkhaTdI2pOv2YNnWZiJ4bUCoQ5G0KveIQ2dqMRAtGAhB01cntX9mrp6vSMamiLw1IrKoEhG3xg= SigV4 ECDHE-RSA-AES128-GCM-SHA256 AuthHeader chs-cdn.development.ch.gov.uk.s3.eu-west-2.amazonaws.com TLSv1.2 - -";
        String logWithInvalidRequestSection = "57f2f030b6e5545bca67c0389164fd7e495aef43831451cc8275ca1bcc012683 chs-cdn.development.ch.gov.uk [" + todayString +  "] - svc:cloudfront.amazonaws.com J3QN4GNXQHX5GJTN REST.GET.OBJECT cidev/assets/fonts/bold.woff2 \"/cidev/assets/fonts/bold.woff2 HTTP/1.1\" 404 AccessDenied 243 - 21 - \"-\" \"-\" - YhkhaTdI2pOv2YNnWZiJ4bUCoQ5G0KveIQ2dqMRAtGAhB01cntX9mrp6vSMamiLw1IrKoEhG3xg= SigV4 ECDHE-RSA-AES128-GCM-SHA256 AuthHeader chs-cdn.development.ch.gov.uk.s3.eu-west-2.amazonaws.com TLSv1.2 - -";
        String logMissingAllRequiredStructure = "57f2f030b6e5545bca67c0389164fd7e495aef43831451cc8275ca1bcc012683";

        ResponseInputStream<GetObjectResponse> logMissingStatusCodeResponseStream = createResponseInputStreamWithRequiredContent(logMissingStatusCode);
        ResponseInputStream<GetObjectResponse> logWithInvalidRequestSectionResponseStream =createResponseInputStreamWithRequiredContent(logWithInvalidRequestSection);
        ResponseInputStream<GetObjectResponse> logMissingAllRequiredStructureResponseStream = createResponseInputStreamWithRequiredContent(logMissingAllRequiredStructure);

        List<S3Object>  mockS3Object =  Arrays.asList(
                                                S3Object.builder().key("logfile1.txt").size(123L).lastModified(now).build(),
                                                S3Object.builder().key("folder/logfile2.txt").size(963L).lastModified(now).build(),
                                                S3Object.builder().key("folder/logfile3.txt").size(963L).lastModified(now).build());
        ListObjectsV2Response mockResponse = ListObjectsV2Response.builder().contents(mockS3Object).build();

        when(s3ClientMock.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(mockResponse);
        when(s3ClientMock.getObject(any(GetObjectRequest.class)))
            .thenReturn(logMissingStatusCodeResponseStream)
            .thenReturn(logWithInvalidRequestSectionResponseStream)
            .thenReturn(logMissingAllRequiredStructureResponseStream);

        List<AssetAccessLog> result = awsS3BucketService.readAccessLogs();

        assertEquals(0, result.size(), "The number of logs found is incorrect");
        assertTrue("The log entry with missing status code was not detected" ,output.getAll().contains("Invalid log entry, STATUS_CODE not present"));
        assertTrue("The log entry with invalid request section was not detected",output.getAll().contains("Invalid log entry, request type not present"));
        assertTrue("The log entry with missing all required structure was not detected", output.getAll().contains("Invalid log entry, log can't be parsed"));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    public void testInvalidDateMissingBrackets(CapturedOutput output) {

        String fileContentFirstFile = "57f2f030b6e5545bca67c0389164fd7e495aef43831451cc8275ca1bcc012683 chs-cdn.development.ch.gov.uk 19/Feb/2025:09:20:04 +0000] - svc:cloudfront.amazonaws.com J3QN4GNXQHX5GJTN REST.GET.OBJECT cidev/assets/fonts/bold.woff2 \"GET /cidev/assets/fonts/bold.woff2 HTTP/1.1\" 404 AccessDenied 243 - 21 - \"-\" \"-\" - YhkhaTdI2pOv2YNnWZiJ4bUCoQ5G0KveIQ2dqMRAtGAhB01cntX9mrp6vSMamiLw1IrKoEhG3xg= SigV4 ECDHE-RSA-AES128-GCM-SHA256 AuthHeader chs-cdn.development.ch.gov.uk.s3.eu-west-2.amazonaws.com TLSv1.2 - -";
        String fileContentSecondFile = "57f2f030b6e5545bca67c0389164fd7e495aef43831451cc8275ca1bcc012683 chs-cdn.development.ch.gov.uk [11/Feb/2025:09:20:04 +0000 - svc:cloudfront.amazonaws.com J3QN4GNXQHX5GJTN REST.GET.OBJECT cidev/assets/fonts/bold.woff2 \"GET /cidev/assets/fonts/bold.woff2 HTTP/1.1\" 404 AccessDenied 243 - 21 - \"-\" \"-\" - YhkhaTdI2pOv2YNnWZiJ4bUCoQ5G0KveIQ2dqMRAtGAhB01cntX9mrp6vSMamiLw1IrKoEhG3xg= SigV4 ECDHE-RSA-AES128-GCM-SHA256 AuthHeader chs-cdn.development.ch.gov.uk.s3.eu-west-2.amazonaws.com TLSv1.2 - -";

        ResponseInputStream<GetObjectResponse> responseInputStreamFirstFile = createResponseInputStreamWithRequiredContent(fileContentFirstFile);
        ResponseInputStream<GetObjectResponse> responseInputStreamSecondFile = createResponseInputStreamWithRequiredContent(fileContentSecondFile);

        List<S3Object>  mockS3Object =  Arrays.asList(
                                                S3Object.builder().key("logfile1.txt").size(123L).lastModified(now).build(),
                                                S3Object.builder().key("folder/logfile2.txt").size(963L).lastModified(now).build());
        ListObjectsV2Response mockResponse = ListObjectsV2Response.builder().contents(mockS3Object).build();

        when(s3ClientMock.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(mockResponse);
        when(s3ClientMock.getObject(any(GetObjectRequest.class)))
            .thenReturn(responseInputStreamFirstFile)
            .thenReturn(responseInputStreamSecondFile);

        List<AssetAccessLog> result = awsS3BucketService.readAccessLogs();

        assertEquals(0, result.size());
        assertTrue("The log entry with missing start bracket was not detected", output.getAll().contains("Invalid log entry, start marker for timestamp not present"));
        assertTrue("The log entry with missing end bracket was not detected", output.getAll().contains("Invalid log entry, end marker for timestamp not present"));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    public void testReadingAnalysisReportsFromBucket(CapturedOutput output) {

        String fileContentFirstFile = "{ \"id\" : \"period\",\n" +
                "  \"assetAccessCount\" : {\n" +
                "    \"asset-folder/asset-js-file.js\" : 2,\n" +
                "    \"asset-html-file.html\" : 2\n" +
                "  }\n" +
                "}";

        ResponseInputStream<GetObjectResponse> responseInputStreamFirstFile = createResponseInputStreamWithRequiredContent(fileContentFirstFile);


        List<S3Object>  mockS3Object =  Arrays.asList(
                                                S3Object.builder().key("logfile1.txt").size(123L).lastModified(now).build());
        ListObjectsV2Response mockResponse = ListObjectsV2Response.builder().contents(mockS3Object).build();

        when(s3ClientMock.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(mockResponse);
        when(s3ClientMock.getObject(any(GetObjectRequest.class)))
            .thenReturn(responseInputStreamFirstFile);

        List<AssetUsageReport> result = awsS3BucketService.readAnalysisReports();

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getAssetAccessCount().get("asset-html-file.html"));
    }


    @Test
    @ExtendWith(OutputCaptureExtension.class)
    public void testAnalysisReportsSdkException(CapturedOutput output) {
        when(s3ClientMock.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow(SdkClientException.create("test exception"));

        List<AssetUsageReport> result = awsS3BucketService.readAnalysisReports();

        assertEquals(0, result.size(), "The number of assets found is incorrect");

        String errorMessage ="Error listing objects in bucket: " + cdnAnalysisBucket;

        assertTrue("sdk exception not thrown", output.getAll().contains(errorMessage));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    public void testAnalysisReportsNoBucket(CapturedOutput output) {

        when(s3ClientMock.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow( NoSuchBucketException.builder().message("test exception").build());

        List<AssetUsageReport> result = awsS3BucketService.readAnalysisReports();

        String errorMessage ="Bucket does not exist: " + cdnAnalysisBucket;

        assertEquals(0, result.size(), "The number of assets found is incorrect");
        assertTrue("No such bucket exception not thrown", output.getAll().contains(errorMessage));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    public void testReadAnalysisUncheckedIOException(CapturedOutput output) {
        when(s3ClientMock.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow(new UncheckedIOException(new IOException("test exception")));

        List<AssetUsageReport> result = awsS3BucketService.readAnalysisReports();

        String errorMessage ="Error processing files in bucket: " + cdnAnalysisBucket;

        assertEquals(0, result.size(), "The number of assets found is incorrect");
        assertTrue("No such bucket exception not thrown", output.getAll().contains(errorMessage));

    }

}