package com.example.fulfillment.core.repository;

import com.example.fulfillment.domain.entity.PaymentRecord;

/**
 * 支付记录仓储接口（核心层）
 * 不绑定具体 ORM 实现
 */
public interface PaymentRecordRepository {
    
    /**
     * 根据支付流水号查询
     */
    PaymentRecord findByPaymentNo(String paymentNo);
    
    /**
     * 保存支付记录
     */
    void save(PaymentRecord record);
}
