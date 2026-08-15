package com.mailbaby.client.exception;

/**
 * Raised on any MailBaby communication failure: transport errors, non-2xx REST
 * responses, or non-OK gRPC statuses.
 */
public class MailBabyException extends RuntimeException {

    private final String code;
    private final int status;
    private final String details;

    public MailBabyException(String code, int status, String message, String details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = details;
    }

    public MailBabyException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = 0;
        this.details = null;
    }

    public static MailBabyException http(int status, String code, String error, String details) {
        return new MailBabyException(code, status, error, details);
    }

    public static MailBabyException grpc(String code, String message) {
        return new MailBabyException(code, 0, message, null);
    }

    /**
     * Server-side error code (e.g. {@code invalid_json}, {@code validation_error}) or gRPC status name.
     */
    public String getCode() {
        return code;
    }

    /**
     * HTTP status code for REST responses, 0 for gRPC errors.
     */
    public int getStatus() {
        return status;
    }

    public String getDetails() {
        return details;
    }

    @Override
    public String toString() {
        return "MailBabyException{code='" + code + "', status=" + status + ", message='" + getMessage()
                + "', details='" + details + "'}";
    }
}