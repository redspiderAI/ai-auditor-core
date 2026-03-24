-- ============================================================
-- AI Auditor - PostgreSQL 初始化脚本
-- 容器首次启动时自动执行，创建所需表结构
-- ============================================================

-- 审计历史记录表
CREATE TABLE IF NOT EXISTS audit_history (
    id              BIGSERIAL PRIMARY KEY,
    doc_id          VARCHAR(255) NOT NULL,
    doc_title       VARCHAR(500),
    rule_set        VARCHAR(100) DEFAULT 'GB/T7714',
    total_issues    INTEGER DEFAULT 0,
    score_impact    FLOAT DEFAULT 0.0,
    audit_status    VARCHAR(50) DEFAULT 'COMPLETED',
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 审计问题详情表
CREATE TABLE IF NOT EXISTS audit_issue (
    id              BIGSERIAL PRIMARY KEY,
    audit_id        BIGINT REFERENCES audit_history(id) ON DELETE CASCADE,
    issue_code      VARCHAR(100) NOT NULL,
    message         TEXT,
    section_id      INTEGER,
    severity        VARCHAR(20) DEFAULT 'INFO',
    suggestion      TEXT,
    original_snippet TEXT,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 索引（加速按 doc_id 和时间范围查询）
CREATE INDEX IF NOT EXISTS idx_audit_history_doc_id ON audit_history(doc_id);
CREATE INDEX IF NOT EXISTS idx_audit_history_created_at ON audit_history(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_issue_audit_id ON audit_issue(audit_id);
CREATE INDEX IF NOT EXISTS idx_audit_issue_severity ON audit_issue(severity);

-- 插入一条示例记录，验证数据库初始化成功
INSERT INTO audit_history (doc_id, doc_title, rule_set, total_issues, score_impact, audit_status)
VALUES ('init-check', 'DB初始化验证记录', 'SYSTEM', 0, 0.0, 'INIT')
ON CONFLICT DO NOTHING;

-- 输出初始化完成信息
DO $$
BEGIN
    RAISE NOTICE '✅ AI Auditor 数据库初始化完成';
    RAISE NOTICE '   - 表 audit_history 已创建';
    RAISE NOTICE '   - 表 audit_issue 已创建';
END $$;
