package com.meridian.transactions.audit;

/**
 * Writes events to the audit-log service. Implementations throw if the
 * event could not be recorded.
 */
public interface AuditClient {

    void record(AuditEvent event);
}
