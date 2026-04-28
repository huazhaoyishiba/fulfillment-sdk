-- SDK 核心表结构（MySQL 版本）
-- 适用于 MySQL 5.7+ / MariaDB 10.2+

-- 1. 支付记录表
CREATE TABLE IF NOT EXISTS sdk_payment_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    biz_order_no VARCHAR(64) NOT NULL COMMENT '业务订单号',
    payment_no VARCHAR(64) NOT NULL COMMENT '支付流水号',
    channel_code VARCHAR(32) NOT NULL COMMENT '支付渠道编码',
    paid_amount DECIMAL(18,2) NOT NULL COMMENT '支付金额',
    status VARCHAR(32) NOT NULL COMMENT '状态',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_payment_no (payment_no),
    KEY idx_order_no (biz_order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SDK-支付记录表';

-- 2. 幂等记录表
CREATE TABLE IF NOT EXISTS sdk_idempotent_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    scene_code VARCHAR(32) NOT NULL COMMENT '场景码',
    idempotent_key VARCHAR(128) NOT NULL COMMENT '幂等键',
    biz_order_no VARCHAR(64) COMMENT '业务订单号',
    task_no VARCHAR(64) COMMENT '关联任务编号（INSERT 时即绑定，消除并发空窗期）',
    task_id BIGINT COMMENT '关联任务ID（任务创建后回填）',
    status VARCHAR(32) NOT NULL COMMENT '状态（DONE/EXPIRED）',
    expired_at DATETIME COMMENT '过期时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_scene_key (scene_code, idempotent_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SDK-幂等记录表';

-- 3. 发放任务表
CREATE TABLE IF NOT EXISTS sdk_fulfillment_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_no VARCHAR(64) NOT NULL COMMENT '任务编号',
    biz_order_no VARCHAR(64) NOT NULL COMMENT '业务订单号',
    biz_user_ref VARCHAR(64) NOT NULL COMMENT '用户业务标识（由调用方定义，如 userId）',
    benefit_type_code VARCHAR(32) NOT NULL COMMENT '权益类型编码',
    action_code VARCHAR(32) NOT NULL DEFAULT 'GRANT' COMMENT '生命周期动作编码（GRANT/REVOKE/RENEW等）',
    benefit_config_snapshot TEXT NOT NULL COMMENT '权益配置快照（JSON），确保重试/补发时配置一致',
    status VARCHAR(32) NOT NULL COMMENT '任务状态（PROCESSING/SUCCESS/RETRY_WAIT/FAILED）',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    max_retry_count INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    last_error_code VARCHAR(64) COMMENT '最近错误码',
    last_error_msg VARCHAR(512) COMMENT '最近错误信息',
    next_retry_at DATETIME COMMENT '下次允许重试时间（退避策略：baseSeconds * 2^(retryCount-1)）',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，防止并发修改冲突',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_task_no (task_no),
    KEY idx_order_no (biz_order_no),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SDK-发放任务表';

-- 4. 发放日志表
CREATE TABLE IF NOT EXISTS sdk_fulfillment_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id BIGINT NOT NULL COMMENT '关联任务ID',
    attempt_no INT NOT NULL COMMENT '第几次尝试',
    action_code VARCHAR(32) COMMENT '生命周期动作编码（冗余存储便于查询）',
    handler_name VARCHAR(128) COMMENT '处理器名称',
    status VARCHAR(32) NOT NULL COMMENT '执行结果（SUCCESS/FAIL）',
    error_code VARCHAR(64) COMMENT '错误码',
    error_msg VARCHAR(512) COMMENT '错误信息',
    result_summary VARCHAR(512) COMMENT '结果摘要',
    start_time DATETIME COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    duration_ms BIGINT COMMENT '耗时（毫秒）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SDK-发放日志表';
