-- #186: WORM storage for the event log, enforced by the database itself.
--
-- A domain_events row can never be updated, deleted or truncated, by the application or by
-- anyone else with SQL access. The one exception is sealing (#184): the transaction that inserts
-- a row writes its signature columns once, before any other transaction can see the row. That
-- update may not change a single content column. Ledger checkpoints (#188) are fully immutable.
-- See docs/adr/0001-append-only-postgres-event-log.md.

CREATE OR REPLACE FUNCTION factstore_domain_events_append_only() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND OLD.signature_status = 'UNSIGNED'
       AND OLD.signature IS NULL
       AND NEW.signature_status = 'SIGNED'
       -- Only the transaction that inserted the row may seal it.
       AND OLD.xmin = pg_current_xact_id()::xid
       AND NEW.sequence_number = OLD.sequence_number
       AND NEW.event_id = OLD.event_id
       AND NEW.aggregate_id = OLD.aggregate_id
       AND NEW.aggregate_type = OLD.aggregate_type
       AND NEW.event_type = OLD.event_type
       AND NEW.payload = OLD.payload
       AND NEW.metadata IS NOT DISTINCT FROM OLD.metadata
       AND NEW.occurred_at = OLD.occurred_at
       AND NEW.unsigned_since IS NOT DISTINCT FROM OLD.unsigned_since
    THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'domain_events is append-only: % of sequence_number % refused', TG_OP, OLD.sequence_number
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER domain_events_append_only
    BEFORE UPDATE OR DELETE ON domain_events
    FOR EACH ROW EXECUTE FUNCTION factstore_domain_events_append_only();

CREATE OR REPLACE FUNCTION factstore_refuse_truncate() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% is append-only: TRUNCATE refused', TG_TABLE_NAME
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER domain_events_no_truncate
    BEFORE TRUNCATE ON domain_events
    FOR EACH STATEMENT EXECUTE FUNCTION factstore_refuse_truncate();

CREATE OR REPLACE FUNCTION factstore_ledger_checkpoints_immutable() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'ledger_checkpoints is append-only: % of checkpoint % refused', TG_OP, OLD.checkpoint_index
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER ledger_checkpoints_append_only
    BEFORE UPDATE OR DELETE ON ledger_checkpoints
    FOR EACH ROW EXECUTE FUNCTION factstore_ledger_checkpoints_immutable();

CREATE TRIGGER ledger_checkpoints_no_truncate
    BEFORE TRUNCATE ON ledger_checkpoints
    FOR EACH STATEMENT EXECUTE FUNCTION factstore_refuse_truncate();
