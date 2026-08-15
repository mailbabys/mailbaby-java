package com.mailbaby.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A file attached to an email. {@code data} is serialized as a base64 string,
 * matching the Go {@code sender.Attachment} struct.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Attachment {

    @JsonProperty("filename")
    private String filename;

    @JsonProperty("content_type")
    private String contentType;

    @JsonProperty("data")
    private byte[] data;

    @JsonProperty("inline")
    private boolean inline;

    @JsonProperty("content_id")
    private String contentId;

    public Attachment() {
    }

    public Attachment(String filename, String contentType, byte[] data) {
        this.filename = filename;
        this.contentType = contentType;
        this.data = data;
    }

    public Attachment(String filename, String contentType, byte[] data, boolean inline, String contentId) {
        this.filename = filename;
        this.contentType = contentType;
        this.data = data;
        this.inline = inline;
        this.contentId = contentId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public boolean isInline() {
        return inline;
    }

    public void setInline(boolean inline) {
        this.inline = inline;
    }

    public String getContentId() {
        return contentId;
    }

    public void setContentId(String contentId) {
        this.contentId = contentId;
    }
}