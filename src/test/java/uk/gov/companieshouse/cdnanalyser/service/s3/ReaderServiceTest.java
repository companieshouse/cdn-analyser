package uk.gov.companieshouse.cdnanalyser.service.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.util.Set;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Object;
import uk.gov.companieshouse.cdnanalyser.configuration.Constants;
import uk.gov.companieshouse.cdnanalyser.models.AssetAccessLog;

@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "logging.level.root=DEBUG")
public class ReaderServiceTest {

    private static final String accessLogFileBucket = "accessLogFileBucket";

    private static final String cdnAssetS3Bucket  =  "cdnAssetS3Bucket";

    private final static String cdnAnalysisBucket = "nonExistentBucket";

    private static final String accessLogFilterInPath = "";

    private static final String cdnAssetFilterInPath = "";

    private static S3Client s3ClientMock;

    private static ReaderService readerService;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d/MMM/yyyy:HH:mm:ss ZZZ", Locale.ENGLISH);

    private final Instant now = Instant.now();

    private final ZonedDateTime today = now.atZone(Constants.LONDON_ZONE_ID);

    private final String todayString = today.format(formatter);

    private final String yesterdayString = today.minus(1, ChronoUnit.DAYS).format(formatter);

    @BeforeAll
    public static void setUp() {
            s3ClientMock = mock(S3Client.class);
            readerService = new ReaderService(s3ClientMock, accessLogFileBucket, cdnAssetS3Bucket, accessLogFilterInPath, cdnAssetFilterInPath, cdnAnalysisBucket);
    }

    @Test
    public void testReadAssets() {
        List<S3Object>  mockS3Object =  Arrays.asList(S3Object.builder().key("asset1.txt").size(123L).build(),S3Object.builder().key("folder/asset2.txt").size(963L).build());
        ListObjectsV2Response mockResponse = ListObjectsV2Response.builder().contents(mockS3Object).build();

        when(s3ClientMock.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(mockResponse);
        List<String> result = readerService.readAssets();
        assertEquals(2, result.size(), "The number of assets found is incorrect");
        assertEquals("asset1.txt", result.get(0), "The first asset is incorrect");
        assertEquals("folder/asset2.txt", result.get(1), "The second asset is incorrect");
    }

    @Test
    public void testReadAssetsSdkClientException() {
        when(s3ClientMock.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow(SdkClientException.create("test exception"));

        List<String> result = readerService.readAssets();

        assertEquals(0, result.size(), "The number of assets found is incorrect");
    }

    @Test
    public void testReadAssetsUncheckedIOException() {
        when(s3ClientMock.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow(new UncheckedIOException(new IOException("test exception")));

        List<String> result = readerService.readAssets();

        assertEquals(0, result.size(), "The number of assets found is incorrect");
    }


    @Test
    public void testReadLogSdkClientException() {
        when(s3ClientMock.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow(SdkClientException.create("test exception"));

        Set<AssetAccessLog> result = readerService.readAccessLogs();

        assertEquals(0, result.size(), "The number of logs found is incorrect");
    }

    @Test
    public void testReadLogUncheckedIOException() {
        when(s3ClientMock.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow(new UncheckedIOException(new IOException("test exception")));

        Set<AssetAccessLog> result = readerService.readAccessLogs();

        assertEquals(0, result.size(), "The number of logs found is incorrect");
    }

    private ResponseBytes<Object> createResponseInputStreamWithRequiredContent(String content) {
        GetObjectResponse getObjectResponse = GetObjectResponse.builder().build();
        return ResponseBytes.fromByteArray(getObjectResponse,content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testReadLogsProcessAllLogs() {

        String fileContent = "57f2f030b6e5545bca67c0389164fd7e495aef43831451cc8275ca1bcc012683 chs-cdn.development.ch.gov.uk [" + todayString + "] - svc:cloudfront.amazonaws.com J3QN4GNXQHX5GJTN REST.GET.OBJECT cidev/assets/fonts/bold.woff2 \"GET /cidev/assets/fonts/bold.woff2 HTTP/1.1\" 404 AccessDenied 243 - 21 - \"-\" \"-\" - YhkhaTdI2pOv2YNnWZiJ4bUCoQ5G0KveIQ2dqMRAtGAhB01cntX9mrp6vSMamiLw1IrKoEhG3xg= SigV4 ECDHE-RSA-AES128-GCM-SHA256 AuthHeader chs-cdn.development.ch.gov.uk.s3.eu-west-2.amazonaws.com TLSv1.2 - -";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8));
        GetObjectResponse getObjectResponse = GetObjectResponse.builder().build();

        String fileContent2 = "57f2f030b6e5545bca67c0389164fd7e495aef43831451cc8275ca1bcc012683 chs-cdn.development.ch.gov.uk [" + yesterdayString + "] - svc:cloudfront.amazonaws.com J3QN4GNXQHX5GJTN REST.GET.OBJECT cidev/assets/fonts/bold.woff2 \"GET /cidev/assets/fonts/bold.woff2 HTTP/1.1\" 404 AccessDenied 243 - 21 - \"-\" \"-\" - YhkhaTdI2pOv2YNnWZiJ4bUCoQ5G0KveIQ2dqMRAtGAhB01cntX9mrp6vSMamiLw1IrKoEhG3xg= SigV4 ECDHE-RSA-AES128-GCM-SHA256 AuthHeader chs-cdn.development.ch.gov.uk.s3.eu-west-2.amazonaws.com TLSv1.2 - -";
        ByteArrayInputStream inputStream2 = new ByteArrayInputStream(fileContent2.getBytes(StandardCharsets.UTF_8));
        GetObjectResponse getObjectResponse2 = GetObjectResponse.builder().build();

        List<S3Object> mockS3Object = Arrays.asList(
            S3Object.builder().key("logfile1.txt").size(123L).lastModified(now).build(),
            S3Object.builder().key("folder/logfile2.txt").size(963L).lastModified(now.minus(1, ChronoUnit.DAYS)).build()
        );
        ListObjectsV2Response mockResponse = ListObjectsV2Response.builder().contents(mockS3Object).build();

        when(s3ClientMock.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(mockResponse);
        when(s3ClientMock.getObject(any(GetObjectRequest.class)))
            .thenReturn(new ResponseInputStream<>(getObjectResponse, inputStream))
            .thenReturn(new ResponseInputStream<>(getObjectResponse2, inputStream2));

        ReaderService readerService = new ReaderService(s3ClientMock, accessLogFileBucket, cdnAssetS3Bucket, accessLogFilterInPath, cdnAssetFilterInPath, "");
        Set<AssetAccessLog> result = readerService.readAccessLogs();

        assertEquals(2, result.size(), "The number of logs found is incorrect");
    }

    @Test
    public void testInvalidDate() {
        String fileContentFirstFile = "57f2f030b6e5545bca67c0389164fd7e495aef43831451cc8275ca1bcc012683 chs-cdn.development.ch.gov.uk 19/Feb/2025:09:20:04 +0000] - svc:cloudfront.amazonaws.com J3QN4GNXQHX5GJTN REST.GET.OBJECT cidev/assets/fonts/bold.woff2 \"GET /cidev/assets/fonts/bold.woff2 HTTP/1.1\" 404 AccessDenied 243 - 21 - \"-\" \"-\" - YhkhaTdI2pOv2YNnWZiJ4bUCoQ5G0KveIQ2dqMRAtGAhB01cntX9mrp6vSMamiLw1IrKoEhG3xg= SigV4 ECDHE-RSA-AES128-GCM-SHA256 AuthHeader chs-cdn.development.ch.gov.uk.s3.eu-west-2.amazonaws.com TLSv1.2 - -";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(fileContentFirstFile.getBytes(StandardCharsets.UTF_8));
        GetObjectResponse getObjectResponse = GetObjectResponse.builder().build();


        List<S3Object>  mockS3Object =  Arrays.asList(
                                                S3Object.builder().key("logfile1.txt").size(123L).lastModified(now).build(),
                                                S3Object.builder().key("folder/logfile2.txt").size(963L).lastModified(now).build());
        ListObjectsV2Response mockResponse = ListObjectsV2Response.builder().contents(mockS3Object).build();

        when(s3ClientMock.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(mockResponse);
        when(s3ClientMock.getObject(any(GetObjectRequest.class)))
            .thenReturn(new ResponseInputStream<>(getObjectResponse, inputStream));

        Set<AssetAccessLog> result = readerService.readAccessLogs();

        assertEquals(0, result.size(), "The number of logs found is incorrect");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    public void testInvalidLogFormat(CapturedOutput output) {

        String logMissingStatusCode = "57f2f030b6e5545bca67c0389164fd7e495aef43831451cc8275ca1bcc012683 chs-cdn.development.ch.gov.uk [" + todayString +  "] - svc:cloudfront.amazonaws.com J3QN4GNXQHX5GJTN REST.GET.OBJECT cidev/assets/fonts/bold.woff2 \"GET /cidev/assets/fonts/bold.woff2 HTTP/1.1\" AccessDenied 243 - 21 - \"-\" \"-\" - YhkhaTdI2pOv2YNnWZiJ4bUCoQ5G0KveIQ2dqMRAtGAhB01cntX9mrp6vSMamiLw1IrKoEhG3xg= SigV4 ECDHE-RSA-AES128-GCM-SHA256 AuthHeader chs-cdn.development.ch.gov.uk.s3.eu-west-2.amazonaws.com TLSv1.2 - -";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(logMissingStatusCode.getBytes(StandardCharsets.UTF_8));
        GetObjectResponse getObjectResponse = GetObjectResponse.builder().build();

        String logWithInvalidRequestSection = "57f2f030b6e5545bca67c0389164fd7e495aef43831451cc8275ca1bcc012683 chs-cdn.development.ch.gov.uk [" + todayString +  "] - svc:cloudfront.amazonaws.com J3QN4GNXQHX5GJTN REST.GET.OBJECT cidev/assets/fonts/bold.woff2 \"/cidev/assets/fonts/bold.woff2 HTTP/1.1\" 404 AccessDenied 243 - 21 - \"-\" \"-\" - YhkhaTdI2pOv2YNnWZiJ4bUCoQ5G0KveIQ2dqMRAtGAhB01cntX9mrp6vSMamiLw1IrKoEhG3xg= SigV4 ECDHE-RSA-AES128-GCM-SHA256 AuthHeader chs-cdn.development.ch.gov.uk.s3.eu-west-2.amazonaws.com TLSv1.2 - -";
        ByteArrayInputStream inputStream1 = new ByteArrayInputStream(logWithInvalidRequestSection.getBytes(StandardCharsets.UTF_8));

        String logMissingAllRequiredStructure = "57f2f030b6e5545bca67c0389164fd7e495aef43831451cc8275ca1bcc012683";
        ByteArrayInputStream inputStream2 = new ByteArrayInputStream(logMissingAllRequiredStructure.getBytes(StandardCharsets.UTF_8));

        List<S3Object>  mockS3Object =  Arrays.asList(
                                                S3Object.builder().key("logfile1.txt").size(123L).lastModified(now).build(),
                                                S3Object.builder().key("folder/logfile2.txt").size(963L).lastModified(now).build(),
                                                S3Object.builder().key("folder/logfile3.txt").size(963L).lastModified(now).build());
        ListObjectsV2Response mockResponse = ListObjectsV2Response.builder().contents(mockS3Object).build();

        when(s3ClientMock.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(mockResponse);
        when(s3ClientMock.getObject(any(GetObjectRequest.class)))
            .thenReturn(new ResponseInputStream<>(getObjectResponse, inputStream))
            .thenReturn(new ResponseInputStream<>(getObjectResponse, inputStream1))
            .thenReturn(new ResponseInputStream<>(getObjectResponse, inputStream2));
        Set<AssetAccessLog> result = readerService.readAccessLogs();

        assertEquals(0, result.size(), "The number of logs found is incorrect");
        assertTrue(output.getAll().contains("Invalid log entry, STATUS_CODE not present"));
        assertTrue(output.getAll().contains("Invalid log entry, request type not present"));
        assertTrue(output.getAll().contains("Log entry not of the required type, log can't be parsed"));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    public void testInvalidDateMissingBrackets(CapturedOutput output) {

        String fileContentFirstFile = "57f2f030b6e5545bca67c0389164fd7e495aef43831451cc8275ca1bcc012683 chs-cdn.development.ch.gov.uk 19/Feb/2025:09:20:04 +0000] - svc:cloudfront.amazonaws.com J3QN4GNXQHX5GJTN REST.GET.OBJECT cidev/assets/fonts/bold.woff2 \"GET /cidev/assets/fonts/bold.woff2 HTTP/1.1\" 404 AccessDenied 243 - 21 - \"-\" \"-\" - YhkhaTdI2pOv2YNnWZiJ4bUCoQ5G0KveIQ2dqMRAtGAhB01cntX9mrp6vSMamiLw1IrKoEhG3xg= SigV4 ECDHE-RSA-AES128-GCM-SHA256 AuthHeader chs-cdn.development.ch.gov.uk.s3.eu-west-2.amazonaws.com TLSv1.2 - -";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(fileContentFirstFile.getBytes(StandardCharsets.UTF_8));
        GetObjectResponse getObjectResponse = GetObjectResponse.builder().build();

        String fileContentSecondFile = "57f2f030b6e5545bca67c0389164fd7e495aef43831451cc8275ca1bcc012683 chs-cdn.development.ch.gov.uk [11/Feb/2025:09:20:04 +0000 - svc:cloudfront.amazonaws.com J3QN4GNXQHX5GJTN REST.GET.OBJECT cidev/assets/fonts/bold.woff2 \"GET /cidev/assets/fonts/bold.woff2 HTTP/1.1\" 404 AccessDenied 243 - 21 - \"-\" \"-\" - YhkhaTdI2pOv2YNnWZiJ4bUCoQ5G0KveIQ2dqMRAtGAhB01cntX9mrp6vSMamiLw1IrKoEhG3xg= SigV4 ECDHE-RSA-AES128-GCM-SHA256 AuthHeader chs-cdn.development.ch.gov.uk.s3.eu-west-2.amazonaws.com TLSv1.2 - -";
        ByteArrayInputStream inputStream1= new ByteArrayInputStream(fileContentSecondFile.getBytes(StandardCharsets.UTF_8));

        List<S3Object>  mockS3Object =  Arrays.asList(
                                                S3Object.builder().key("logfile1.txt").size(123L).lastModified(now).build(),
                                                S3Object.builder().key("folder/logfile2.txt").size(963L).lastModified(now).build());
        ListObjectsV2Response mockResponse = ListObjectsV2Response.builder().contents(mockS3Object).build();

        when(s3ClientMock.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(mockResponse);

        when(s3ClientMock.getObject(any(GetObjectRequest.class)))
            .thenReturn(new ResponseInputStream<>(getObjectResponse, inputStream))
            .thenReturn(new ResponseInputStream<>(getObjectResponse, inputStream1));

        Set<AssetAccessLog> result = readerService.readAccessLogs();

        assertEquals(0, result.size());
        assertTrue(output.getAll().contains("Invalid log entry, start marker for timestamp not present"));
        assertTrue( output.getAll().contains("Invalid log entry, end marker for timestamp not present"));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    public void testReadingAnalysisReportsFromBucket(CapturedOutput output) {
        String fileContentFirstFile = "[{ \n" +
                                        " \"requestType\" : \"GET\", \n" +
                                        " \"asset\" : \"asset-folder/asset-js-file.js\", \n" +
                                        " \"timestamp\" : \"2025-05-22T13:23:28.061Z\", \n" +
                                        " \"statusCode\" : 200 \n" +
                                      "}]";

        ResponseBytes<Object> responseInputStreamFirstFile = createResponseInputStreamWithRequiredContent(fileContentFirstFile);

        List<S3Object>  mockS3Object =  Arrays.asList(
                                                S3Object.builder().key("logfile1.txt").size(123L).lastModified(now).build());
        ListObjectsV2Response mockResponse = ListObjectsV2Response.builder().contents(mockS3Object).build();

        when(s3ClientMock.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(mockResponse);
        when(s3ClientMock.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
            .thenReturn(responseInputStreamFirstFile);

        List<AssetAccessLog> result = readerService.readRawAssetAccessLogs();

        assertEquals(1, result.size());
        assertEquals("asset-folder/asset-js-file.js", result.get(0).getAsset());
    }


    @Test
    @ExtendWith(OutputCaptureExtension.class)
    public void testAnalysisReportsSdkException(CapturedOutput output) {
        when(s3ClientMock.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
                .thenThrow( SdkClientException.builder().message("test exception").build());

        List<AssetAccessLog> result = readerService.readRawAssetAccessLogs();

        String errorMessage ="Error obtaining data from the bucket " + cdnAnalysisBucket;

        assertEquals(0, result.size(), "The number of assets found is incorrect");
        assertTrue(output.getAll().contains(errorMessage));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    public void testAnalysisReportsNoBucket(CapturedOutput output) {

        when(s3ClientMock.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class))).thenThrow( NoSuchBucketException.builder().message("test exception").build());

        List<AssetAccessLog> result = readerService.readRawAssetAccessLogs();

        String errorMessage ="Bucket " + cdnAnalysisBucket + " does not exist";

        assertEquals(0, result.size(), "The number of assets found is incorrect");
        assertTrue(output.getAll().contains(errorMessage));
    }
}