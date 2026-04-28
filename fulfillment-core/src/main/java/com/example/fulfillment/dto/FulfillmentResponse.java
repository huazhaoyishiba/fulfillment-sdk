package com.example.fulfillment.dto;

/**
 * 发放结果响应
 */
public class FulfillmentResponse {
    /** 是否成功 */
    private boolean success;
    /** 任务编号 */
    private String taskNo;
    /** 当前任务状态（SUCCESS / RETRY_WAIT / FAILED / PROCESSING） */
    private String status;
    /** 错误码（仅失败时有值） */
    private String errorCode;
    /** 结果摘要或错误信息 */
    private String message;

    public FulfillmentResponse() {}

    public static FulfillmentResponse success(String taskNo, String message) {
        FulfillmentResponse r = new FulfillmentResponse();
        r.success = true;
        r.taskNo = taskNo;
        r.status = "SUCCESS";
        r.message = message;
        return r;
    }

    public static FulfillmentResponse fail(String taskNo, String status, String errorCode, String message) {
        FulfillmentResponse r = new FulfillmentResponse();
        r.success = false;
        r.taskNo = taskNo;
        r.status = status;
        r.errorCode = errorCode;
        r.message = message;
        return r;
    }

    public static FulfillmentResponse replay(String taskNo, String status) {
        FulfillmentResponse r = new FulfillmentResponse();
        r.success = "SUCCESS".equals(status);
        r.taskNo = taskNo;
        r.status = status;
        r.message = "idempotent replay";
        return r;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    @Override
    public String toString() {
        return "FulfillmentResponse{" +
                "success=" + success +
                ", taskNo='" + taskNo + '\'' +
                ", status='" + status + '\'' +
                ", errorCode='" + errorCode + '\'' +
                '}';
    }
}
