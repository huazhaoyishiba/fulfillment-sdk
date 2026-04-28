package com.example.fulfillment.core.repository;

import com.example.fulfillment.domain.entity.FulfillmentLog;

import java.util.List;

/**
 * 发放日志仓储接口（核心层）
 * 不绑定具体 ORM 实现
 */
public interface FulfillmentLogRepository {
    
    /**
     * 保存日志
     */
    void save(FulfillmentLog log);
    
    /**
     * 根据任务ID查询所有日志
     */
    List<FulfillmentLog> findByTaskId(Long taskId);
}
