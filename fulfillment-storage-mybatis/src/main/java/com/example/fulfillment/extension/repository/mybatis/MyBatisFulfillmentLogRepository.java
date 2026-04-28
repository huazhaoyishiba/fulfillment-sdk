package com.example.fulfillment.extension.repository.mybatis;

import com.example.fulfillment.core.repository.FulfillmentLogRepository;
import com.example.fulfillment.domain.entity.FulfillmentLog;
import com.example.fulfillment.domain.mapper.FulfillmentLogMapper;

import java.util.List;

/**
 * MyBatis 实现的发放日志仓储（扩展层）
 */
public class MyBatisFulfillmentLogRepository implements FulfillmentLogRepository {
    
    private final FulfillmentLogMapper mapper;

    public MyBatisFulfillmentLogRepository(FulfillmentLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(FulfillmentLog log) {
        mapper.insert(log);
    }

    @Override
    public List<FulfillmentLog> findByTaskId(Long taskId) {
        return mapper.findByTaskId(taskId);
    }
}
