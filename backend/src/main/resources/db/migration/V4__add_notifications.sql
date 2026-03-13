-- V3: Notifications and notification rules tables

CREATE TABLE notification_rules (
    id                     UUID         NOT NULL,
    name                   VARCHAR(255) NOT NULL,
    is_active              BOOLEAN      NOT NULL DEFAULT TRUE,
    trigger_event          VARCHAR(50)  NOT NULL,
    channel_type           VARCHAR(50)  NOT NULL,
    channel_config         TEXT         NOT NULL DEFAULT '{}',
    filter_flow_id         UUID,
    filter_environment_id  UUID,
    created_at             TIMESTAMPTZ  NOT NULL,
    updated_at             TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_notification_rules PRIMARY KEY (id)
);

CREATE TABLE notification_deliveries (
    id            UUID         NOT NULL,
    rule_id       UUID         NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    payload       TEXT,
    status        VARCHAR(20)  NOT NULL,
    sent_at       TIMESTAMPTZ  NOT NULL,
    error         TEXT,
    attempt_count INTEGER      NOT NULL DEFAULT 1,
    CONSTRAINT pk_notification_deliveries PRIMARY KEY (id),
    CONSTRAINT fk_notification_deliveries_rule FOREIGN KEY (rule_id) REFERENCES notification_rules (id) ON DELETE CASCADE
);

CREATE TABLE notifications (
    id          UUID         NOT NULL,
    title       VARCHAR(255) NOT NULL,
    message     TEXT         NOT NULL,
    severity    VARCHAR(20)  NOT NULL DEFAULT 'INFO',
    is_read     BOOLEAN      NOT NULL DEFAULT FALSE,
    entity_type VARCHAR(100),
    entity_id   UUID,
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id)
);

CREATE INDEX idx_notifications_is_read ON notifications (is_read);
CREATE INDEX idx_notifications_severity ON notifications (severity);
CREATE INDEX idx_notification_deliveries_rule_id ON notification_deliveries (rule_id);
