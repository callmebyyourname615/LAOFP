-- Current-state reporting is maintained synchronously.  Daily aggregates remain
-- historical metrics; they must not be used as the source of a live status.

CREATE TABLE reporting.current_transaction_status (
    status VARCHAR(30) PRIMARY KEY,
    total_count BIGINT NOT NULL CHECK (total_count >= 0),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT NOW()
);

CREATE TABLE reporting.current_inquiry_status (
    status VARCHAR(30) PRIMARY KEY,
    total_count BIGINT NOT NULL CHECK (total_count >= 0),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT NOW()
);

CREATE TABLE reporting.current_outbox_status (
    status VARCHAR(30) PRIMARY KEY,
    total_count BIGINT NOT NULL CHECK (total_count >= 0),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT NOW()
);

CREATE OR REPLACE FUNCTION reporting.adjust_current_status(target_table REGCLASS, target_status TEXT, delta BIGINT)
RETURNS VOID AS $$
BEGIN
    IF target_status IS NULL OR delta = 0 THEN RETURN; END IF;
    EXECUTE format('INSERT INTO %s(status, total_count, updated_at) VALUES ($1, $2, NOW())
                    ON CONFLICT (status) DO UPDATE SET total_count = %s.total_count + EXCLUDED.total_count, updated_at = NOW()',
                   target_table, target_table)
    USING target_status, delta;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION reporting.sync_current_transaction_status() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM reporting.adjust_current_status('reporting.current_transaction_status', NEW.status, 1);
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        PERFORM reporting.adjust_current_status('reporting.current_transaction_status', OLD.status, -1);
        RETURN OLD;
    ELSIF OLD.status IS DISTINCT FROM NEW.status THEN
        PERFORM reporting.adjust_current_status('reporting.current_transaction_status', OLD.status, -1);
        PERFORM reporting.adjust_current_status('reporting.current_transaction_status', NEW.status, 1);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION reporting.sync_current_inquiry_status() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM reporting.adjust_current_status('reporting.current_inquiry_status', NEW.status, 1);
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        PERFORM reporting.adjust_current_status('reporting.current_inquiry_status', OLD.status, -1);
        RETURN OLD;
    ELSIF OLD.status IS DISTINCT FROM NEW.status THEN
        PERFORM reporting.adjust_current_status('reporting.current_inquiry_status', OLD.status, -1);
        PERFORM reporting.adjust_current_status('reporting.current_inquiry_status', NEW.status, 1);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION reporting.sync_current_outbox_status() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM reporting.adjust_current_status('reporting.current_outbox_status', NEW.status, 1);
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        PERFORM reporting.adjust_current_status('reporting.current_outbox_status', OLD.status, -1);
        RETURN OLD;
    ELSIF OLD.status IS DISTINCT FROM NEW.status THEN
        PERFORM reporting.adjust_current_status('reporting.current_outbox_status', OLD.status, -1);
        PERFORM reporting.adjust_current_status('reporting.current_outbox_status', NEW.status, 1);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_current_transaction_status
AFTER INSERT OR UPDATE OF status OR DELETE ON transactions
FOR EACH ROW EXECUTE FUNCTION reporting.sync_current_transaction_status();
CREATE TRIGGER trg_current_inquiry_status
AFTER INSERT OR UPDATE OF status OR DELETE ON inquiries
FOR EACH ROW EXECUTE FUNCTION reporting.sync_current_inquiry_status();
CREATE TRIGGER trg_current_outbox_status
AFTER INSERT OR UPDATE OF status OR DELETE ON outbox_messages
FOR EACH ROW EXECUTE FUNCTION reporting.sync_current_outbox_status();

INSERT INTO reporting.current_transaction_status(status, total_count)
SELECT status, COUNT(*) FROM transactions GROUP BY status;
INSERT INTO reporting.current_inquiry_status(status, total_count)
SELECT status, COUNT(*) FROM inquiries GROUP BY status;
INSERT INTO reporting.current_outbox_status(status, total_count)
SELECT status, COUNT(*) FROM outbox_messages GROUP BY status;
