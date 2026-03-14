-- V9: Coverage reports table for continuous evidence collection pipeline

CREATE TABLE coverage_reports (
    id               UUID         NOT NULL,
    trail_id         UUID         NOT NULL,
    tool             VARCHAR(255) NOT NULL,
    line_coverage    DOUBLE PRECISION,
    branch_coverage  DOUBLE PRECISION,
    min_coverage     DOUBLE PRECISION,
    passed           BOOLEAN      NOT NULL,
    report_file_name VARCHAR(255),
    report_file_hash VARCHAR(255),
    details          TEXT,
    created_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_coverage_reports PRIMARY KEY (id),
    CONSTRAINT fk_coverage_reports_trail FOREIGN KEY (trail_id) REFERENCES trails (id) ON DELETE CASCADE
);

CREATE INDEX idx_coverage_reports_trail_id ON coverage_reports (trail_id);
