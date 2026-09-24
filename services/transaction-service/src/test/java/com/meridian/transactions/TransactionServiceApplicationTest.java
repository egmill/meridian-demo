package com.meridian.transactions;

import com.meridian.transactions.audit.AuditClient;
import com.meridian.transactions.audit.HttpAuditClient;
import com.meridian.transactions.service.TransferService;
import com.meridian.transactions.web.AccountController;
import com.meridian.transactions.web.TransferController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "meridian.audit-log.url=http://127.0.0.1:1")
class TransactionServiceApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void context_wires_controllers_service_and_http_audit_client() {
        assertNotNull(context.getBean(TransferController.class));
        assertNotNull(context.getBean(AccountController.class));
        assertNotNull(context.getBean(TransferService.class));
        assertInstanceOf(HttpAuditClient.class, context.getBean(AuditClient.class));
    }

    @Test
    void main_starts_the_application_without_a_web_server() {
        TransactionServiceApplication.main(new String[] {
                "--spring.main.web-application-type=none",
                "--meridian.audit-log.url=http://127.0.0.1:1"
        });
    }
}
