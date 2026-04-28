package com.example.fulfillment.core.repository;

import com.example.fulfillment.domain.entity.IdempotentRecord;

/**
 * 幂等记录仓储接口（核心层）
 * <p>
 * 不绑定具体 ORM 实现。接入方可以使用 MyBatis、JdbcTemplate、JPA 等实现此接口。
 */
public interface IdempotentRecordRepository {
    
    /**
     * 根据场景码和幂等键查询<strong>未过期</strong>的幂等记录
     * <p>
     * 实现要求：
     * <ul>
     *   <li>仅返回 {@code expiredAt} 为 NULL 或尚未到期的记录</li>
     *   <li>已过期的记录应当被忽略（相当于幂等窗口已结束，允许重新处理）</li>
     * </ul>
     *
     * @param sceneCode     场景码
     * @param idempotentKey 幂等键
     * @return 匹配且未过期的记录；不存在或已过期则返回 null
     */
    IdempotentRecord findBySceneAndKey(String sceneCode, String idempotentKey);
    
    /**
     * 保存幂等记录
     */
    void save(IdempotentRecord record);
    
    /**
     * 更新状态和任务ID
     */
    void updateStatusAndTask(IdempotentRecord record);
}
