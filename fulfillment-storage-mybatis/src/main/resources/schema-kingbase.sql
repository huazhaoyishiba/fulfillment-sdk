-- SDK 核心表结构（人大金仓 KingbaseES 版本）
-- 适用于 KingbaseES V8
-- 人大金仓兼容 PostgreSQL 语法，本脚本基于 PostgreSQL 版本调整

-- 1. 支付记录表
CREATE TABLE IF NOT EXISTS sdk_payment_record (
    id BIGSERIAL PRIMARY KEY,
    biz_order_no VARCHAR(64) NOT NULL,
    payment_no VARCHAR(64) NOT NULL,
    channel_code VARCHAR(32) NOT NULL,
    paid_amount NUMERIC(18,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE sdk_payment_record IS 'SDK-支付记录表';
COMMENT ON COLUMN sdk_payment_record.id IS '主键ID';
COMMENT ON COLUMN sdk_payment_record.biz_order_no IS '业务订单号';
COMMENT ON COLUMN sdk_payment_record.payment_no IS '支付流水号';
COMMENT ON COLUMN sdk_payment_record.channel_code IS '支付渠道编码';
COMMENT ON COLUMN sdk_payment_record.paid_amount IS '支付金额';
COMMENT ON COLUMN sdk_payment_record.status IS '状态';
COMMENT ON COLUMN sdk_payment_record.created_at IS '创建时间';
COMMENT ON COLUMN sdk_payment_record.updated_at IS '更新时间';
CREATE UNIQUE INDEX IF NOT EXISTS uk_sdk_pr_payment_no ON sdk_payment_record(payment_no);
CREATE INDEX IF NOT EXISTS idx_sdk_pr_order_no ON sdk_payment_record(biz_order_no);

-- 2. 幂等记录表
CREATE TABLE IF NOT EXISTS sdk_idempotent_record (
    id BIGSERIAL PRIMARY KEY,
    scene_code VARCHAR(32) NOT NULL,
    idempotent_key VARCHAR(128) NOT NULL,
    biz_order_no VARCHAR(64),
    task_no VARCHAR(64),
    task_id BIGINT,
    status VARCHAR(32) NOT NULL,
    expired_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE sdk_idempotent_record IS 'SDK-幂等记录表';
COMMENT ON COLUMN sdk_idempotent_record.id IS '主键ID';
COMMENT ON COLUMN sdk_idempotent_record.scene_code IS '场景码';
COMMENT ON COLUMN sdk_idempotent_record.idempotent_key IS '幂等键';
COMMENT ON COLUMN sdk_idempotent_record.biz_order_no IS '业务订单号';
COMMENT ON COLUMN sdk_idempotent_record.task_no IS '关联任务编号（INSERT 时即绑定，消除并发空窗期）';
COMMENT ON COLUMN sdk_idempotent_record.task_id IS '关联任务ID（任务创建后回填）';
COMMENT ON COLUMN sdk_idempotent_record.status IS '状态（DONE/EXPIRED）';
COMMENT ON COLUMN sdk_idempotent_record.expired_at IS '过期时间';
COMMENT ON COLUMN sdk_idempotent_record.created_at IS '创建时间';
COMMENT ON COLUMN sdk_idempotent_record.updated_at IS '更新时间';
CREATE UNIQUE INDEX IF NOT EXISTS uk_sdk_ir_scene_key ON sdk_idempotent_record(scene_code, idempotent_key);

-- 3. 发放任务表
CREATE TABLE IF NOT EXISTS sdk_fulfillment_task (
    id BIGSERIAL PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL,
    biz_order_no VARCHAR(64) NOT NULL,
    biz_user_ref VARCHAR(64) NOT NULL,
    benefit_type_code VARCHAR(32) NOT NULL,
    action_code VARCHAR(32) NOT NULL DEFAULT 'GRANT',
    benefit_config_snapshot TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    max_retry_count INT NOT NULL DEFAULT 3,
    last_error_code VARCHAR(64),
    last_error_msg VARCHAR(512),
    next_retry_at TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE sdk_fulfillment_task IS 'SDK-发放任务表';
COMMENT ON COLUMN sdk_fulfillment_task.id IS '主键ID';
COMMENT ON COLUMN sdk_fulfillment_task.task_no IS '任务编号';
COMMENT ON COLUMN sdk_fulfillment_task.biz_order_no IS '业务订单号';
COMMENT ON COLUMN sdk_fulfillment_task.biz_user_ref IS '用户业务标识（由调用方定义，如 userId）';
COMMENT ON COLUMN sdk_fulfillment_task.benefit_type_code IS '权益类型编码';
COMMENT ON COLUMN sdk_fulfillment_task.action_code IS '生命周期动作编码（GRANT/REVOKE/RENEW等）';
COMMENT ON COLUMN sdk_fulfillment_task.benefit_config_snapshot IS '权益配置快照（JSON），确保重试/补发时配置一致';
COMMENT ON COLUMN sdk_fulfillment_task.status IS '任务状态（PROCESSING/SUCCESS/RETRY_WAIT/FAILED）';
COMMENT ON COLUMN sdk_fulfillment_task.retry_count IS '已重试次数';
COMMENT ON COLUMN sdk_fulfillment_task.max_retry_count IS '最大重试次数';
COMMENT ON COLUMN sdk_fulfillment_task.last_error_code IS '最近错误码';
COMMENT ON COLUMN sdk_fulfillment_task.last_error_msg IS '最近错误信息';
COMMENT ON COLUMN sdk_fulfillment_task.next_retry_at IS '下次允许重试时间（退避策略）';
COMMENT ON COLUMN sdk_fulfillment_task.version IS '乐观锁版本号，防止并发修改冲突';
COMMENT ON COLUMN sdk_fulfillment_task.created_at IS '创建时间';
COMMENT ON COLUMN sdk_fulfillment_task.updated_at IS '更新时间';
CREATE UNIQUE INDEX IF NOT EXISTS uk_sdk_ft_task_no ON sdk_fulfillment_task(task_no);
CREATE INDEX IF NOT EXISTS idx_sdk_ft_order_no ON sdk_fulfillment_task(biz_order_no);
CREATE INDEX IF NOT EXISTS idx_sdk_ft_status ON sdk_fulfillment_task(status);

-- 4. 发放日志表
CREATE TABLE IF NOT EXISTS sdk_fulfillment_log (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    action_code VARCHAR(32),
    handler_name VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    error_code VARCHAR(64),
    error_msg VARCHAR(512),
    result_summary VARCHAR(512),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    duration_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE sdk_fulfillment_log IS 'SDK-发放日志表';
COMMENT ON COLUMN sdk_fulfillment_log.id IS '主键ID';
COMMENT ON COLUMN sdk_fulfillment_log.task_id IS '关联任务ID';
COMMENT ON COLUMN sdk_fulfillment_log.attempt_no IS '第几次尝试';
COMMENT ON COLUMN sdk_fulfillment_log.action_code IS '生命周期动作编码（冗余存储便于查询）';
COMMENT ON COLUMN sdk_fulfillment_log.handler_name IS '处理器名称';
COMMENT ON COLUMN sdk_fulfillment_log.status IS '执行结果（SUCCESS/FAIL）';
COMMENT ON COLUMN sdk_fulfillment_log.error_code IS '错误码';
COMMENT ON COLUMN sdk_fulfillment_log.error_msg IS '错误信息';
COMMENT ON COLUMN sdk_fulfillment_log.result_summary IS '结果摘要';
COMMENT ON COLUMN sdk_fulfillment_log.start_time IS '开始时间';
COMMENT ON COLUMN sdk_fulfillment_log.end_time IS '结束时间';
COMMENT ON COLUMN sdk_fulfillment_log.duration_ms IS '耗时（毫秒）';
COMMENT ON COLUMN sdk_fulfillment_log.created_at IS '创建时间';
CREATE INDEX IF NOT EXISTS idx_sdk_fl_task_id ON sdk_fulfillment_log(task_id);
