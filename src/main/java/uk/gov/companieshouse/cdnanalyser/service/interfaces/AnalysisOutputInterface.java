package uk.gov.companieshouse.cdnanalyser.service.interfaces;

import java.util.List;

import uk.gov.companieshouse.cdnanalyser.models.AssetAccessLog;
import uk.gov.companieshouse.cdnanalyser.models.AssetUsageReport;

public interface AnalysisOutputInterface {

    public void saveFailedAssetsRequests(List<AssetAccessLog> assetAccessLogsWithErrors);

    public void saveRawData(List<AssetAccessLog> assetAccessLogs);

    public void saveSuccessfulAssetRequestsTotals(AssetUsageReport assetUsageReportTotals);

    public void saveSuccessfulAssetRequestsTotalsForPeriod(AssetUsageReport assetUsageReportTotals);

}
