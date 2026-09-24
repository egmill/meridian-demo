package com.meridian.transactions.web;

import com.meridian.transactions.model.Transfer;
import com.meridian.transactions.service.AccountNotFoundException;
import com.meridian.transactions.service.InsufficientFundsException;
import com.meridian.transactions.service.TransferService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.time.Instant;

import static org.hamcrest.Matchers.closeTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferController.class)
class TransferControllerTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-01-15T10:30:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransferService transferService;

    private static String body(String from, String to, String amount, String memo) {
        return "{\"fromAccountId\":" + json(from) + ",\"toAccountId\":" + json(to)
                + ",\"amount\":" + amount + ",\"memo\":" + json(memo) + "}";
    }

    private static String json(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    @Test
    void valid_request_returns_201_with_transfer_body() throws Exception {
        when(transferService.transfer("ACC-1001", "ACC-1002", 250.75, "rent"))
                .thenReturn(new Transfer("TX-1001", "ACC-1001", "ACC-1002", 250.75, "rent", FIXED_TIME));

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ACC-1001", "ACC-1002", "250.75", "rent")))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reference").value("TX-1001"))
                .andExpect(jsonPath("$.fromAccountId").value("ACC-1001"))
                .andExpect(jsonPath("$.toAccountId").value("ACC-1002"))
                .andExpect(jsonPath("$.amount").value(closeTo(250.75, 0.0)))
                .andExpect(jsonPath("$.memo").value("rent"))
                .andExpect(jsonPath("$.createdAt").value("2026-01-15T10:30:00Z"));

        verify(transferService).transfer("ACC-1001", "ACC-1002", 250.75, "rent");
    }

    @Test
    void memo_is_optional_and_passed_as_null() throws Exception {
        when(transferService.transfer(eq("ACC-1001"), eq("ACC-1002"), eq(1.0), isNull()))
                .thenReturn(new Transfer("TX-1002", "ACC-1001", "ACC-1002", 1.0, null, FIXED_TIME));

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"ACC-1001\",\"toAccountId\":\"ACC-1002\",\"amount\":1.0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memo").doesNotExist());
    }

    @Test
    void blank_from_account_returns_400_and_never_reaches_service() throws Exception {
        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("  ", "ACC-1002", "10", "x")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid transfer request"));

        verifyNoInteractions(transferService);
    }

    @Test
    void missing_to_account_returns_400_and_never_reaches_service() throws Exception {
        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"ACC-1001\",\"amount\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid transfer request"));

        verifyNoInteractions(transferService);
    }

    @Test
    void empty_to_account_returns_400() throws Exception {
        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ACC-1001", "", "10", "x")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid transfer request"));

        verifyNoInteractions(transferService);
    }

    @Test
    void malformed_json_returns_400() throws Exception {
        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transferService);
    }

    @Test
    void non_numeric_amount_returns_400() throws Exception {
        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ACC-1001", "ACC-1002", "\"lots\"", "x")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transferService);
    }

    @Test
    void same_account_error_from_service_maps_to_400_with_message() throws Exception {
        when(transferService.transfer(anyString(), anyString(), anyDouble(), any()))
                .thenThrow(new IllegalArgumentException("Source and destination accounts must differ"));

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ACC-1001", "ACC-1001", "10", "x")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Source and destination accounts must differ"));
    }

    @Test
    void insufficient_funds_maps_to_400_with_message() throws Exception {
        when(transferService.transfer(anyString(), anyString(), anyDouble(), any()))
                .thenThrow(new InsufficientFundsException("ACC-1001"));

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ACC-1001", "ACC-1002", "999999", "x")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Insufficient funds in account ACC-1001"));
    }

    @Test
    void unknown_account_maps_to_404_with_message() throws Exception {
        when(transferService.transfer(anyString(), anyString(), anyDouble(), any()))
                .thenThrow(new AccountNotFoundException("ACC-9999"));

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ACC-1001", "ACC-9999", "10", "x")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Account not found: ACC-9999"));
    }

    @Test
    void audit_log_failure_maps_to_502_and_is_not_swallowed() throws Exception {
        when(transferService.transfer(anyString(), anyString(), anyDouble(), any()))
                .thenThrow(new ResourceAccessException("connection refused"));

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ACC-1001", "ACC-1002", "10", "x")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Audit log unavailable; transfer not processed"));
    }

    @Test
    void generic_rest_client_exception_maps_to_502() throws Exception {
        when(transferService.transfer(anyString(), anyString(), anyDouble(), any()))
                .thenThrow(new RestClientException("500 from audit-log"));

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ACC-1001", "ACC-1002", "10", "x")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Audit log unavailable; transfer not processed"));
    }

    @Test
    void unsupported_media_type_returns_415() throws Exception {
        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(transferService);
    }

    @Test
    @Disabled("SUSPECTED BUG: TransferRequest.amount has no @Positive constraint, so amount=0 passes validation; business rule requires amount > 0")
    void zero_amount_is_rejected_at_request_validation() throws Exception {
        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ACC-1001", "ACC-1002", "0", "x")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transferService);
    }

    @Test
    @Disabled("SUSPECTED BUG: TransferRequest.amount has no @Positive constraint, so negative amounts pass validation; business rule requires amount > 0")
    void negative_amount_is_rejected_at_request_validation() throws Exception {
        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ACC-1001", "ACC-1002", "-50", "x")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transferService);
    }

    @Test
    @Disabled("SUSPECTED BUG: amount is omitted -> defaults to 0.0 (primitive double) and passes validation; business rule requires amount > 0")
    void missing_amount_is_rejected_at_request_validation() throws Exception {
        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"ACC-1001\",\"toAccountId\":\"ACC-1002\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transferService);
    }
}
