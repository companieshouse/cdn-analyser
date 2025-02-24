package uk.gov.companieshouse.cdnanalyser.service.interfaces;

import java.util.List;
import java.util.Set;

import uk.gov.companieshouse.cdnanalyser.models.AssetAccessLog;

public interface AnalysisInputInterface {

    List<String> readAssets ();

    Set<AssetAccessLog> readAccessLogs();

    List<AssetAccessLog> readRawAssetAccessLogs();
}
