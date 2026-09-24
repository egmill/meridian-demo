package com.meridian.transactions.web;

import com.meridian.transactions.model.Account;
import com.meridian.transactions.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountRepository accountRepository;

    @Test
    void list_returns_200_with_all_accounts_in_repository_order() throws Exception {
        when(accountRepository.findAll()).thenReturn(List.of(
                new Account("ACC-1001", "Everyday Checking", 2500.00),
                new Account("ACC-1002", "High-Yield Savings", 10000.00)));

        mockMvc.perform(get("/accounts"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value("ACC-1001"))
                .andExpect(jsonPath("$[0].name").value("Everyday Checking"))
                .andExpect(jsonPath("$[0].balance").value(closeTo(2500.00, 0.0)))
                .andExpect(jsonPath("$[1].id").value("ACC-1002"))
                .andExpect(jsonPath("$[1].balance").value(closeTo(10000.00, 0.0)));

        verify(accountRepository).findAll();
    }

    @Test
    void list_returns_empty_array_when_no_accounts() throws Exception {
        when(accountRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/accounts"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void get_returns_200_with_the_requested_account() throws Exception {
        when(accountRepository.findById("ACC-1003"))
                .thenReturn(Optional.of(new Account("ACC-1003", "Vendor Payee Account", 0.00)));

        mockMvc.perform(get("/accounts/ACC-1003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ACC-1003"))
                .andExpect(jsonPath("$.name").value("Vendor Payee Account"))
                .andExpect(jsonPath("$.balance").value(closeTo(0.0, 0.0)));

        verify(accountRepository).findById("ACC-1003");
    }

    @Test
    void get_unknown_account_returns_404_with_error_message() throws Exception {
        when(accountRepository.findById("ACC-9999")).thenReturn(Optional.empty());

        mockMvc.perform(get("/accounts/ACC-9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Account not found: ACC-9999"));
    }

    @Test
    void post_to_accounts_is_not_allowed() throws Exception {
        mockMvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void cors_preflight_from_allowed_origin_is_permitted_for_get_and_post() throws Exception {
        mockMvc.perform(options("/accounts")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST"));
    }

    @Test
    void cors_preflight_from_other_origin_is_rejected() throws Exception {
        mockMvc.perform(options("/accounts")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void cors_preflight_for_disallowed_method_is_rejected() throws Exception {
        mockMvc.perform(options("/accounts/ACC-1001")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isForbidden());
    }
}
