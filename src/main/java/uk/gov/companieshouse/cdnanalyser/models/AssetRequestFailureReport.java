package uk.gov.companieshouse.cdnanalyser.models;

public class  AssetRequestFailureReport {

    private String asset;
    private int failureCount;
    private int failureCode;

    public AssetRequestFailureReport(){}

    public AssetRequestFailureReport(String asset, int failureCode, int failureCount) {
        this.asset = asset;
        this.failureCount = failureCount;
        this.failureCode = failureCode;
    }

    public String getAsset() {
        return asset;
    }

    public void setAsset(String asset) {
        this.asset = asset;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public int getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(int failureCode) {
        this.failureCode = failureCode;
    }

    @Override
    public String toString() {
        return "AssetFailureCount [asset=" + asset + ", failureCount=" + failureCount + ", failureCode=" + failureCode + "]";
    }
}
