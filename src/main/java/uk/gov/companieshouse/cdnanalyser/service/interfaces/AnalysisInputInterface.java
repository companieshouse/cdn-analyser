package uk.gov.companieshouse.cdnanalyser.service.interfaces;

import java.util.List;

import uk.gov.companieshouse.cdnanalyser.models.AssetAccessLog;
import uk.gov.companieshouse.cdnanalyser.models.AssetUsageReport;

public interface AnalysisInputInterface {

    public List<String> readAssets ();

    public List<AssetAccessLog> readAccessLogs();

    public List<AssetUsageReport> readAnalysisReports();
}
