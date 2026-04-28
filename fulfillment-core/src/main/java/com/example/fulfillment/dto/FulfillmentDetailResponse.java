package com.example.fulfillment.dto;

import java.util.List;

/**
 * 发放详情响应（包含执行历史）
 */
public class FulfillmentDetailResponse extends FulfillmentStatusResponse {
    private List<FulfillmentLogItem> logs;

    public List<FulfillmentLogItem> getLogs() { return logs; }
    public void setLogs(List<FulfillmentLogItem> logs) { this.logs = logs; }
}
