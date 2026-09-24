package com.meridian.transactions.audit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpAuditClient implements AuditClient {

    private final RestClient restClient;

    public HttpAuditClient(@Value("${meridian.audit-log.url}") String auditLogUrl) {
        this.restClient = RestClient.create(auditLogUrl);
    }

    @Override
    public void record(AuditEvent event) {
        restClient.post()
                .uri("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body(event)
                .retrieve()
                .toBodilessEntity();
    }
}
