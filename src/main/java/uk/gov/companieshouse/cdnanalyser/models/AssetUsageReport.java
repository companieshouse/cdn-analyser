package uk.gov.companieshouse.cdnanalyser.models;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import uk.gov.companieshouse.cdnanalyser.configuration.Constants;

public class AssetUsageReport {

    private String id;

    private Map<String, Integer> assetAccessCount;

    public AssetUsageReport(String id, Map<String, Integer>  assetAccessCount) {
        this.id = id;
        this.assetAccessCount = assetAccessCount;
    }

    public AssetUsageReport(Instant date, Map<String, Integer> assetAccessCount) {
        this.id = LocalDate.ofInstant(date, Constants.LONDON_ZONE_ID).toString();
        this.assetAccessCount = assetAccessCount;
    }

    public AssetUsageReport(){

    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, Integer> getAssetAccessCount() {
        return assetAccessCount;
    }

    public void setAssetAccessCount(HashMap<String, Integer> assetAccessCount) {
        this.assetAccessCount = assetAccessCount;
    }

    @Override
    public String toString() {
        return "AssetUsageLog [id=" + id + ", assetAccessCount=" + assetAccessCount+ "]";
    }
}