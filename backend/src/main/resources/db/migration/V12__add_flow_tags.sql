-- V12: Add flow_tags table for arbitrary key-value metadata on flows

CREATE TABLE flow_tags (
    flow_id   UUID         NOT NULL,
    tag_key   VARCHAR(64)  NOT NULL,
    tag_value VARCHAR(256) NOT NULL DEFAULT '',
    CONSTRAINT pk_flow_tags PRIMARY KEY (flow_id, tag_key),
    CONSTRAINT fk_flow_tags_flow FOREIGN KEY (flow_id) REFERENCES flows (id) ON DELETE CASCADE
);
