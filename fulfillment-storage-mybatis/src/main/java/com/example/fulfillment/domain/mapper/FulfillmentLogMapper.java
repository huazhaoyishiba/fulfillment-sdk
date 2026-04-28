package com.example.fulfillment.domain.mapper;

import com.example.fulfillment.domain.entity.FulfillmentLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface FulfillmentLogMapper {

    @Insert("insert into sdk_fulfillment_log(task_id, attempt_no, action_code, handler_name, status, error_code, error_msg, result_summary, " +
            "start_time, end_time, duration_ms, created_at) " +
            "values(#{log.taskId}, #{log.attemptNo}, #{log.actionCode}, #{log.handlerName}, #{log.status}, #{log.errorCode}, #{log.errorMsg}, #{log.resultSummary}, " +
            "#{log.startTime}, #{log.endTime}, #{log.durationMs}, now())")
    @Options(useGeneratedKeys = true, keyProperty = "log.id")
    int insert(@Param("log") FulfillmentLog log);

    @Select("select * from sdk_fulfillment_log where task_id = #{taskId} order by attempt_no asc")
    List<FulfillmentLog> findByTaskId(@Param("taskId") Long taskId);
}
