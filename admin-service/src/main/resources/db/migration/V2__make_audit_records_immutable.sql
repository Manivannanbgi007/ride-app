-- Audit records are immutable. The application offers no way to change or delete them; these triggers make the
-- database refuse it too, whoever asks (a bug, a script, a person with SQL access).
CREATE TRIGGER audit_records_no_update BEFORE UPDATE ON audit_records
    FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Audit records are immutable: they cannot be changed';

CREATE TRIGGER audit_records_no_delete BEFORE DELETE ON audit_records
    FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Audit records are immutable: they cannot be deleted';
