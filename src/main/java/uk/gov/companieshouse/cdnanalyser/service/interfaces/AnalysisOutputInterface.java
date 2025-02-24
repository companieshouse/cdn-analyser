package uk.gov.companieshouse.cdnanalyser.service.interfaces;

import java.util.List;
import java.util.Set;

import uk.gov.companieshouse.cdnanalyser.models.AssetAccessLog;
import uk.gov.companieshouse.cdnanalyser.models.AssetUsageReport;

public interface AnalysisOutputInterface {

    void saveFailedAssetsRequests(List<AssetAccessLog> assetAccessLogsWithErrors);

    void saveRawData(Set<AssetAccessLog> assetAccessLogs);

    void saveSuccessfulAssetRequests(AssetUsageReport assetUsageReportTotals);

}
