package com.mailbaby.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An email delivery request, wire-compatible with the MailBaby server's JSON schema
 * (the Go {@code sender.Email} struct: snake_case fields, {@code data} as base64).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class EmailMessage {

    @JsonProperty("id")
    private String id;

    @JsonProperty("account")
    private String account;

    @JsonProperty("from")
    private String from;

    @JsonProperty("from_name")
    private String fromName;

    @JsonProperty("reply_to")
    private String replyTo;

    @JsonProperty("to")
    private List<String> to;

    @JsonProperty("cc")
    private List<String> cc;

    @JsonProperty("bcc")
    private List<String> bcc;

    @JsonProperty("subject")
    private String subject;

    @JsonProperty("text_body")
    private String textBody;

    @JsonProperty("html_body")
    private String htmlBody;

    @JsonProperty("headers")
    private Map<String, String> headers;

    @JsonProperty("attachments")
    private List<Attachment> attachments;

    @JsonProperty("tags")
    private List<String> tags;

    @JsonProperty("metadata")
    private Map<String, String> metadata;

    public EmailMessage() {
    }

    private EmailMessage(Builder b) {
        this.id = b.id;
        this.account = b.account;
        this.from = b.from;
        this.fromName = b.fromName;
        this.replyTo = b.replyTo;
        this.to = b.to;
        this.cc = b.cc;
        this.bcc = b.bcc;
        this.subject = b.subject;
        this.textBody = b.textBody;
        this.htmlBody = b.htmlBody;
        this.headers = b.headers;
        this.attachments = b.attachments;
        this.tags = b.tags;
        this.metadata = b.metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public String getAccount() {
        return account;
    }

    public String getFrom() {
        return from;
    }

    public String getFromName() {
        return fromName;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public List<String> getTo() {
        return to;
    }

    public List<String> getCc() {
        return cc;
    }

    public List<String> getBcc() {
        return bcc;
    }

    public String getSubject() {
        return subject;
    }

    public String getTextBody() {
        return textBody;
    }

    public String getHtmlBody() {
        return htmlBody;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public List<String> getTags() {
        return tags;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public static final class Builder {
        private String id;
        private String account;
        private String from;
        private String fromName;
        private String replyTo;
        private List<String> to;
        private List<String> cc;
        private List<String> bcc;
        private String subject;
        private String textBody;
        private String htmlBody;
        private Map<String, String> headers;
        private List<Attachment> attachments;
        private List<String> tags;
        private Map<String, String> metadata;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder account(String account) {
            this.account = account;
            return this;
        }

        public Builder from(String from) {
            this.from = from;
            return this;
        }

        public Builder fromName(String fromName) {
            this.fromName = fromName;
            return this;
        }

        public Builder replyTo(String replyTo) {
            this.replyTo = replyTo;
            return this;
        }

        public Builder to(List<String> to) {
            this.to = to;
            return this;
        }

        public Builder to(String... to) {
            this.to = to == null ? null : List.of(to);
            return this;
        }

        public Builder cc(List<String> cc) {
            this.cc = cc;
            return this;
        }

        public Builder bcc(List<String> bcc) {
            this.bcc = bcc;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder textBody(String textBody) {
            this.textBody = textBody;
            return this;
        }

        public Builder htmlBody(String htmlBody) {
            this.htmlBody = htmlBody;
            return this;
        }

        public Builder header(String name, String value) {
            if (this.headers == null) {
                this.headers = new LinkedHashMap<>();
            }
            this.headers.put(name, value);
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder attachment(Attachment attachment) {
            if (this.attachments == null) {
                this.attachments = new ArrayList<>();
            }
            this.attachments.add(attachment);
            return this;
        }

        public Builder attachments(List<Attachment> attachments) {
            this.attachments = attachments;
            return this;
        }

        public Builder tag(String tag) {
            if (this.tags == null) {
                this.tags = new ArrayList<>();
            }
            this.tags.add(tag);
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder metadata(String key, String value) {
            if (this.metadata == null) {
                this.metadata = new LinkedHashMap<>();
            }
            this.metadata.put(key, value);
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public EmailMessage build() {
            return new EmailMessage(this);
        }
    }
}