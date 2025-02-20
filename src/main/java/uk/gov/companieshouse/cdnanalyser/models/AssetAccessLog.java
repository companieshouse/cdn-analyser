package uk.gov.companieshouse.cdnanalyser.models;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

public class AssetAccessLog {

    String requestType;

    String asset;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    Instant timestamp;

    Integer statusCode;

    public String getRequestType() {
        return requestType;
    }
    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }
    public String getAsset() {
        return asset;
    }
    public void setAsset(String asset) {
        this.asset = asset;
    }
    public Instant getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 400;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }
    @Override
    public String toString() {
        return "Log [requestType=" + requestType + ", asset=" + asset + ", timestamp=" + timestamp + ", statusCode="
                + statusCode + "]";
    }
}