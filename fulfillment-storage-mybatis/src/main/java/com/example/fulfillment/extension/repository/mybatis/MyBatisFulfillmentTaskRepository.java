package com.example.fulfillment.extension.repository.mybatis;

import com.example.fulfillment.core.repository.FulfillmentTaskRepository;
import com.example.fulfillment.domain.entity.FulfillmentTask;
import com.example.fulfillment.domain.mapper.FulfillmentTaskMapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MyBatis 实现的发放任务仓储（扩展层）
 */
public class MyBatisFulfillmentTaskRepository implements FulfillmentTaskRepository {
    
    private final FulfillmentTaskMapper mapper;

    public MyBatisFulfillmentTaskRepository(FulfillmentTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public FulfillmentTask findByTaskNo(String taskNo) {
        return mapper.findByTaskNo(taskNo);
    }

    @Override
    public FulfillmentTask findById(Long id) {
        return mapper.findById(id);
    }

    @Override
    public FulfillmentTask findByBizOrderNo(String bizOrderNo) {
        return mapper.findByBizOrderNo(bizOrderNo);
    }

    @Override
    public FulfillmentTask findByBizOrderNoAndActionCode(String bizOrderNo, String actionCode) {
        return mapper.findByBizOrderNoAndActionCode(bizOrderNo, actionCode);
    }

    @Override
    public List<FulfillmentTask> findAllByBizOrderNoAndBenefitTypeCode(String bizOrderNo, String benefitTypeCode) {
        return mapper.findAllByBizOrderNoAndBenefitTypeCode(bizOrderNo, benefitTypeCode);
    }

    @Override
    public void save(FulfillmentTask task) {
        mapper.insert(task);
    }

    @Override
    public int updateStatusWithVersion(FulfillmentTask task) {
        return mapper.updateStatusWithVersion(task);
    }

    @Override
    public List<FulfillmentTask> findRetryableTasks(int limit) {
        return mapper.findRetryableTasks(limit);
    }

    @Override
    public List<FulfillmentTask> findStuckProcessingTasks(LocalDateTime timeoutThreshold, int limit) {
        return mapper.findStuckProcessingTasks(timeoutThreshold, limit);
    }
}
