package com.example.fulfillment.extension.repository.mybatis;

import com.example.fulfillment.core.repository.IdempotentRecordRepository;
import com.example.fulfillment.domain.entity.IdempotentRecord;
import com.example.fulfillment.domain.mapper.IdempotentRecordMapper;

/**
 * MyBatis 实现的幂等记录仓储（扩展层）
 */
public class MyBatisIdempotentRecordRepository implements IdempotentRecordRepository {
    
    private final IdempotentRecordMapper mapper;

    public MyBatisIdempotentRecordRepository(IdempotentRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public IdempotentRecord findBySceneAndKey(String sceneCode, String idempotentKey) {
        return mapper.findBySceneAndKey(sceneCode, idempotentKey);
    }

    @Override
    public void save(IdempotentRecord record) {
        mapper.insert(record);
    }

    @Override
    public void updateStatusAndTask(IdempotentRecord record) {
        mapper.updateStatusAndTask(record);
    }
}
