package uk.gov.companieshouse.cdnanalyser.models;

import java.time.Instant;

public class S3File {

    private String filename;

    private Instant modifiedDate;

    private String content = "";

    public S3File(String filename, Instant modifiedDate) {
        this.filename = filename;
        this.modifiedDate = modifiedDate;
    }

    public String getFilename() {
        return filename;
    }

    public Instant getModifiedDate() {
        return modifiedDate;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public String toString() {
        return "S3File [filename=" + filename + ", modifiedDate=" + modifiedDate + ", content=" + content + "]";
    }
}
