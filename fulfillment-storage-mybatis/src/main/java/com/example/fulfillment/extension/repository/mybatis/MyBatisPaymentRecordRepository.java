package com.example.fulfillment.extension.repository.mybatis;

import com.example.fulfillment.core.repository.PaymentRecordRepository;
import com.example.fulfillment.domain.entity.PaymentRecord;
import com.example.fulfillment.domain.mapper.PaymentRecordMapper;

/**
 * MyBatis 实现的支付记录仓储（扩展层）
 * <p>
 * 由 {@link com.example.fulfillment.config.MyBatisAutoConfiguration} 自动装配。
 * 如果用户自己注册了 PaymentRecordRepository Bean，则此实现不会被创建。
 */
public class MyBatisPaymentRecordRepository implements PaymentRecordRepository {
    
    private final PaymentRecordMapper mapper;

    public MyBatisPaymentRecordRepository(PaymentRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public PaymentRecord findByPaymentNo(String paymentNo) {
        return mapper.findByPaymentNo(paymentNo);
    }

    @Override
    public void save(PaymentRecord record) {
        mapper.insert(record);
    }
}
