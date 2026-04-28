package com.example.fulfillment.service.handler;

public class HandlerResult {
    private final boolean success;
    private final String errorCode;
    private final String errorMessage;
    private final String summary;

    private HandlerResult(boolean success, String errorCode, String errorMessage, String summary) {
        this.success = success;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.summary = summary;
    }

    public static HandlerResult success(String summary) {
        return new HandlerResult(true, null, null, summary);
    }

    public static HandlerResult fail(String errorCode, String errorMessage) {
        return new HandlerResult(false, errorCode, errorMessage, null);
    }

    public boolean isSuccess() { return success; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public String getSummary() { return summary; }
}
