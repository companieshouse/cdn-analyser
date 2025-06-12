package uk.gov.companieshouse.cdnanalyser.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uk.gov.companieshouse.cdnanalyser.models.AssetAccessLog;
import uk.gov.companieshouse.cdnanalyser.models.AssetRequestFailureReport;
import uk.gov.companieshouse.cdnanalyser.models.AssetUsageReport;

class UtilTest {

    private AssetAccessLog logSuccess;
    private AssetAccessLog logFailure;
    private AssetAccessLog logOtherAsset;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2024-06-01T12:00:00Z");
        logSuccess = new AssetAccessLog();
        logSuccess.setAsset("asset1.js");
        logSuccess.setStatusCode(200);
        logSuccess.setTimestamp(now);

        logFailure = new AssetAccessLog();
        logFailure.setAsset("asset1.js");
        logFailure.setStatusCode(404);
        logFailure.setTimestamp(now);

        logOtherAsset = new AssetAccessLog();
        logOtherAsset.setAsset("asset2.css");
        logOtherAsset.setStatusCode(500);
        logOtherAsset.setTimestamp(now);
    }

    @Test
    void testCalculateFailedAssetRequests_NewFailures() {
        List<AssetAccessLog> logs = Arrays.asList(logSuccess, logFailure, logOtherAsset);
        List<AssetRequestFailureReport> existing = new ArrayList<>();

        List<AssetRequestFailureReport> result = Util.calculateFailedAssetRequests(logs, existing);

        assertEquals(2, result.size());
        Optional<AssetRequestFailureReport> failure404 = result.stream()
                .filter(r -> r.getAsset().equals("asset1.js") && r.getFailureCode() == 404)
                .findFirst();
        assertTrue(failure404.isPresent());
        assertEquals(1, failure404.get().getFailureCount());

        Optional<AssetRequestFailureReport> failure500 = result.stream()
                .filter(r -> r.getAsset().equals("asset2.css") && r.getFailureCode() == 500)
                .findFirst();
        assertTrue(failure500.isPresent());
        assertEquals(1, failure500.get().getFailureCount());
    }

    @Test
    void testCalculateFailedAssetRequests_WithExistingFailures() {
        List<AssetAccessLog> logs = Collections.singletonList(logFailure);
        AssetRequestFailureReport existingReport = new AssetRequestFailureReport("asset1.js", 404, 2);
        List<AssetRequestFailureReport> existing = Collections.singletonList(existingReport);

        List<AssetRequestFailureReport> result = Util.calculateFailedAssetRequests(logs, existing);

        assertEquals(1, result.size());
        AssetRequestFailureReport report = result.get(0);
        assertEquals("asset1.js", report.getAsset());
        assertEquals(404, report.getFailureCode());
        assertEquals(3, report.getFailureCount());
    }

    @Test
    void testCalculateAssetRequestTotals() {
        Map<String, Integer> map1 = new HashMap<>();
        map1.put("asset1.js", 2);
        map1.put("asset2.css", 1);

        Map<String, Integer> map2 = new HashMap<>();
        map2.put("asset1.js", 3);
        map2.put("asset2.css", 2);

        AssetUsageReport report1 = new AssetUsageReport("date1", map1);
        AssetUsageReport report2 = new AssetUsageReport("date2", map2);

        AssetUsageReport result = Util.calculateAssetRequestTotals(Arrays.asList(report1, report2));

        assertEquals(5, result.getAssetAccessCount().get("asset1.js"));
        assertEquals(3, result.getAssetAccessCount().get("asset2.css"));
    }

    @Test
    void testParseLogEntry_Valid() {
        String logEntry = "57f2f030b6e5545bca67c0389164fd7e495aef43831451cc8275ca1bcc012683 chs-cdn.development.ch.gov.uk [28/Nov/2024:07:30:46 +0000] - svc:cloudfront.amazonaws.com 4A0VA8BRDAQXTMR5 REST.GET.OBJECT cidev/javascripts/app/generate-document.js \"GET /cidev/javascripts/app/generate-document.js HTTP/1.1\" 200 - 6138 6138 33 32 \"-\" \"-\" - Fy2SBAMztbDT8DtgDL/Q9DTk7l46E21JhAJU8H0PhGfRQuO+iBSKb0MV9q7y5vV//pZle0NJEfM= SigV4 ECDHE-RSA-AES128-GCM-SHA256 AuthHeader chs-cdn.development.ch.gov.uk.s3.eu-west-2.amazonaws.com TLSv1.2 - -";
        AssetAccessLog log = Util.parseLogEntry(logEntry, "cidev/");

        assertNotNull(log);
        assertEquals("GET", log.getRequestType());
        assertEquals("cidev/javascripts/app/generate-document.js", log.getAsset());
        assertEquals(200, log.getStatusCode());
        assertEquals(Instant.parse("2024-11-28T07:30:46Z"), log.getTimestamp());
    }

    @Test
    void testParseLogEntry_InvalidStatusCode() {
        String logEntry = "1.2.3.4 - - [1/Jun/2024:12:00:00 +0000] \"REST.GET.OBJECT environment/asset1.js HTTP/1.1\" abc -";
        AssetAccessLog log = Util.parseLogEntry(logEntry, "environment");
        assertNull(log);
    }

    @Test
    void testParseLogEntry_InvalidTimestamp() {
        String logEntry = "1.2.3.4 - - [bad-date] \"REST.GET.OBJECT environment/asset1.js HTTP/1.1\" 200 -";
        AssetAccessLog log = Util.parseLogEntry(logEntry, "environment");
        assertNull(log);
    }

    @Test
    void testParseLogEntry_NoEnvironment() {
        String logEntry = "1.2.3.4 - - [1/Jun/2024:12:00:00 +0000] \"REST.GET.OBJECT prod/asset1.js HTTP/1.1\" 200 -";
        AssetAccessLog log = Util.parseLogEntry(logEntry, "environment");
        assertNull(log);
    }

    @Test
    void testParseLogEntry_MissingMarkers() {
        String logEntry = "no markers here";
        AssetAccessLog log = Util.parseLogEntry(logEntry, "environment");
        assertNull(log);
    }
}