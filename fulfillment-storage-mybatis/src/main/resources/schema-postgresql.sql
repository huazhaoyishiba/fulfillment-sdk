-- SDK 核心表结构（PostgreSQL 版本）
-- 适用于 PostgreSQL 9.5+
-- 人大金仓 (KingbaseES) 兼容 PostgreSQL 语法，也可使用此脚本

-- 1. 支付记录表
create table if not exists sdk_payment_record (
    id bigserial primary key,
    biz_order_no varchar(64) not null,
    payment_no varchar(64) not null,
    channel_code varchar(32) not null,
    paid_amount numeric(18,2) not null,
    status varchar(32) not null,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);
comment on table sdk_payment_record is 'SDK-支付记录表';
comment on column sdk_payment_record.id is '主键ID';
comment on column sdk_payment_record.biz_order_no is '业务订单号';
comment on column sdk_payment_record.payment_no is '支付流水号';
comment on column sdk_payment_record.channel_code is '支付渠道编码';
comment on column sdk_payment_record.paid_amount is '支付金额';
comment on column sdk_payment_record.status is '状态';
comment on column sdk_payment_record.created_at is '创建时间';
comment on column sdk_payment_record.updated_at is '更新时间';
create unique index if not exists uk_sdk_payment_record_payment_no on sdk_payment_record(payment_no);
create index if not exists idx_sdk_payment_record_order_no on sdk_payment_record(biz_order_no);

-- 2. 幂等记录表
create table if not exists sdk_idempotent_record (
    id bigserial primary key,
    scene_code varchar(32) not null,
    idempotent_key varchar(128) not null,
    biz_order_no varchar(64),
    task_no varchar(64),
    task_id bigint,
    status varchar(32) not null,
    expired_at timestamp,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);
comment on table sdk_idempotent_record is 'SDK-幂等记录表';
comment on column sdk_idempotent_record.id is '主键ID';
comment on column sdk_idempotent_record.scene_code is '场景码';
comment on column sdk_idempotent_record.idempotent_key is '幂等键';
comment on column sdk_idempotent_record.biz_order_no is '业务订单号';
comment on column sdk_idempotent_record.task_no is '关联任务编号（INSERT 时即绑定，消除并发空窗期）';
comment on column sdk_idempotent_record.task_id is '关联任务ID（任务创建后回填）';
comment on column sdk_idempotent_record.status is '状态（DONE/EXPIRED）';
comment on column sdk_idempotent_record.expired_at is '过期时间';
comment on column sdk_idempotent_record.created_at is '创建时间';
comment on column sdk_idempotent_record.updated_at is '更新时间';
create unique index if not exists uk_sdk_idempotent_scene_key on sdk_idempotent_record(scene_code, idempotent_key);

-- 3. 发放任务表
create table if not exists sdk_fulfillment_task (
    id bigserial primary key,
    task_no varchar(64) not null,
    biz_order_no varchar(64) not null,
    biz_user_ref varchar(64) not null,
    benefit_type_code varchar(32) not null,
    action_code varchar(32) not null default 'GRANT',
    benefit_config_snapshot text not null,
    status varchar(32) not null,
    retry_count int not null default 0,
    max_retry_count int not null default 3,
    last_error_code varchar(64),
    last_error_msg varchar(512),
    next_retry_at timestamp,
    version int not null default 0,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);
comment on table sdk_fulfillment_task is 'SDK-发放任务表';
comment on column sdk_fulfillment_task.id is '主键ID';
comment on column sdk_fulfillment_task.task_no is '任务编号';
comment on column sdk_fulfillment_task.biz_order_no is '业务订单号';
comment on column sdk_fulfillment_task.biz_user_ref is '用户业务标识（由调用方定义，如 userId）';
comment on column sdk_fulfillment_task.benefit_type_code is '权益类型编码';
comment on column sdk_fulfillment_task.action_code is '生命周期动作编码（GRANT/REVOKE/RENEW等）';
comment on column sdk_fulfillment_task.benefit_config_snapshot is '权益配置快照（JSON），确保重试/补发时配置一致';
comment on column sdk_fulfillment_task.status is '任务状态（PROCESSING/SUCCESS/RETRY_WAIT/FAILED）';
comment on column sdk_fulfillment_task.retry_count is '已重试次数';
comment on column sdk_fulfillment_task.max_retry_count is '最大重试次数';
comment on column sdk_fulfillment_task.last_error_code is '最近错误码';
comment on column sdk_fulfillment_task.last_error_msg is '最近错误信息';
comment on column sdk_fulfillment_task.next_retry_at is '下次允许重试时间（退避策略）';
comment on column sdk_fulfillment_task.version is '乐观锁版本号，防止并发修改冲突';
comment on column sdk_fulfillment_task.created_at is '创建时间';
comment on column sdk_fulfillment_task.updated_at is '更新时间';
create unique index if not exists uk_sdk_fulfillment_task_task_no on sdk_fulfillment_task(task_no);
create index if not exists idx_sdk_fulfillment_task_order_no on sdk_fulfillment_task(biz_order_no);
create index if not exists idx_sdk_fulfillment_task_status on sdk_fulfillment_task(status);

-- 4. 发放日志表
create table if not exists sdk_fulfillment_log (
    id bigserial primary key,
    task_id bigint not null,
    attempt_no int not null,
    action_code varchar(32),
    handler_name varchar(128),
    status varchar(32) not null,
    error_code varchar(64),
    error_msg varchar(512),
    result_summary varchar(512),
    start_time timestamp,
    end_time timestamp,
    duration_ms bigint,
    created_at timestamp not null default now()
);
comment on table sdk_fulfillment_log is 'SDK-发放日志表';
comment on column sdk_fulfillment_log.id is '主键ID';
comment on column sdk_fulfillment_log.task_id is '关联任务ID';
comment on column sdk_fulfillment_log.attempt_no is '第几次尝试';
comment on column sdk_fulfillment_log.action_code is '生命周期动作编码（冗余存储便于查询）';
comment on column sdk_fulfillment_log.handler_name is '处理器名称';
comment on column sdk_fulfillment_log.status is '执行结果（SUCCESS/FAIL）';
comment on column sdk_fulfillment_log.error_code is '错误码';
comment on column sdk_fulfillment_log.error_msg is '错误信息';
comment on column sdk_fulfillment_log.result_summary is '结果摘要';
comment on column sdk_fulfillment_log.start_time is '开始时间';
comment on column sdk_fulfillment_log.end_time is '结束时间';
comment on column sdk_fulfillment_log.duration_ms is '耗时（毫秒）';
comment on column sdk_fulfillment_log.created_at is '创建时间';
create index if not exists idx_sdk_fulfillment_log_task_id on sdk_fulfillment_log(task_id);
