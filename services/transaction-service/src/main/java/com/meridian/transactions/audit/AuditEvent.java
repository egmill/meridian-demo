package com.meridian.transactions.audit;

public record AuditEvent(String actor, String action, String details) {
}
