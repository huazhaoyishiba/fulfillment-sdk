package com.example.fulfillment.domain.mapper;

import com.example.fulfillment.domain.entity.PaymentRecord;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PaymentRecordMapper {

    @Select("select * from sdk_payment_record where payment_no = #{paymentNo}")
    PaymentRecord findByPaymentNo(@Param("paymentNo") String paymentNo);

    @Insert("insert into sdk_payment_record(biz_order_no, payment_no, channel_code, paid_amount, status, created_at, updated_at) " +
            "values(#{record.bizOrderNo}, #{record.paymentNo}, #{record.channelCode}, #{record.paidAmount}, #{record.status}, now(), now())")
    @Options(useGeneratedKeys = true, keyProperty = "record.id")
    int insert(@Param("record") PaymentRecord record);

    @Update("update sdk_payment_record set status = #{record.status}, updated_at = now() where id = #{record.id}")
    int updateStatus(@Param("record") PaymentRecord record);
}
