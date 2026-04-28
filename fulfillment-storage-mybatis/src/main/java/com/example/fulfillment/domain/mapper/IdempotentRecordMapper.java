package com.example.fulfillment.domain.mapper;

import com.example.fulfillment.domain.entity.IdempotentRecord;
import org.apache.ibatis.annotations.*;

@Mapper
public interface IdempotentRecordMapper {

    /**
     * 根据场景码和幂等键查询<strong>未过期</strong>的幂等记录
     * <p>
     * 过滤规则：{@code expired_at} 为 NULL 或尚未到期的记录才会被返回。
     * 已过期的记录相当于"幂等窗口结束"，同一操作可以被重新执行。
     */
    @Select("select * from sdk_idempotent_record " +
            "where scene_code = #{sceneCode} " +
            "and idempotent_key = #{idempotentKey} " +
            "and (expired_at is null or expired_at > now())")
    IdempotentRecord findBySceneAndKey(@Param("sceneCode") String sceneCode, 
                                       @Param("idempotentKey") String idempotentKey);

    @Insert("insert into sdk_idempotent_record(scene_code, idempotent_key, biz_order_no, task_no, task_id, status, expired_at, created_at, updated_at) " +
            "values(#{record.sceneCode}, #{record.idempotentKey}, #{record.bizOrderNo}, #{record.taskNo}, #{record.taskId}, #{record.status}, #{record.expiredAt}, now(), now())")
    @Options(useGeneratedKeys = true, keyProperty = "record.id")
    int insert(@Param("record") IdempotentRecord record);

    @Update("update sdk_idempotent_record set task_no = #{record.taskNo}, task_id = #{record.taskId}, status = #{record.status}, updated_at = now() where id = #{record.id}")
    int updateStatusAndTask(@Param("record") IdempotentRecord record);
}
