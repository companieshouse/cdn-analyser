package uk.gov.companieshouse.cdnanalyser.models;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

public class AssetAccessLog {

    private String requestType;

    private String asset;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant timestamp;

    private Integer statusCode;

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

    public void setTimestamp(String timestamp) {
        this.timestamp = Instant.parse(timestamp);
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = Integer.valueOf(statusCode);
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }
    @Override
    public String toString() {
        return "Log [requestType=" + requestType + ", asset=" + asset + ", timestamp=" + timestamp + ", statusCode="
                + statusCode + "]";
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((requestType == null) ? 0 : requestType.hashCode());
        result = prime * result + ((asset == null) ? 0 : asset.hashCode());
        result = prime * result + ((timestamp == null) ? 0 : timestamp.hashCode());
        result = prime * result + ((statusCode == null) ? 0 : statusCode.hashCode());
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AssetAccessLog other = (AssetAccessLog) obj;
        if (requestType == null) {
            if (other.requestType != null)
                return false;
        } else if (!requestType.equals(other.requestType))
            return false;
        if (asset == null) {
            if (other.asset != null)
                return false;
        } else if (!asset.equals(other.asset))
            return false;
        if (timestamp == null) {
            if (other.timestamp != null)
                return false;
        } else if (!timestamp.equals(other.timestamp))
            return false;
        if (statusCode == null) {
            if (other.statusCode != null)
                return false;
        } else if (!statusCode.equals(other.statusCode))
            return false;
        return true;
    }


}