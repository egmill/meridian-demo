package com.meridian.transactions.service;

import com.meridian.transactions.audit.AuditClient;
import com.meridian.transactions.audit.AuditEvent;
import com.meridian.transactions.model.Account;
import com.meridian.transactions.model.Transfer;
import com.meridian.transactions.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final String CHECKING = "ACC-1001";
    private static final String SAVINGS = "ACC-1002";
    private static final String PAYEE = "ACC-1003";

    @Mock
    private AuditClient auditClient;

    private AccountRepository repository;
    private TransferService service;

    @BeforeEach
    void setUp() {
        repository = new AccountRepository();
        service = new TransferService(repository, auditClient);
    }

    private double balance(String id) {
        return repository.findById(id).orElseThrow().getBalance();
    }

    // ---- happy path ----

    @Test
    void transfer_debits_source_and_credits_destination_by_exact_amount() {
        service.transfer(CHECKING, SAVINGS, 500.00, "rent");

        assertEquals(2000.00, balance(CHECKING), 0.0);
        assertEquals(10500.00, balance(SAVINGS), 0.0);
        assertEquals(0.00, balance(PAYEE), 0.0);
    }

    @Test
    void transfer_returns_record_echoing_inputs() {
        Instant before = Instant.now();
        Transfer transfer = service.transfer(CHECKING, SAVINGS, 125.50, "gift");
        Instant after = Instant.now();

        assertEquals(CHECKING, transfer.fromAccountId());
        assertEquals(SAVINGS, transfer.toAccountId());
        assertEquals(125.50, transfer.amount(), 0.0);
        assertEquals("gift", transfer.memo());
        assertNotNull(transfer.createdAt());
        assertTrue(!transfer.createdAt().isBefore(before) && !transfer.createdAt().isAfter(after),
                "createdAt should be captured at transfer time");
        assertTrue(Duration.between(before, transfer.createdAt()).abs().toSeconds() < 5);
    }

    @Test
    void first_reference_is_TX_1001_and_subsequent_references_increment_by_one() {
        Transfer first = service.transfer(CHECKING, SAVINGS, 1.00, null);
        Transfer second = service.transfer(CHECKING, SAVINGS, 1.00, null);
        Transfer third = service.transfer(SAVINGS, CHECKING, 1.00, null);

        assertEquals("TX-1001", first.reference());
        assertEquals("TX-1002", second.reference());
        assertEquals("TX-1003", third.reference());
        assertNotEquals(first.reference(), second.reference());
    }

    @Test
    void transfer_with_null_memo_is_accepted_and_preserved_as_null() {
        Transfer transfer = service.transfer(CHECKING, PAYEE, 10.00, null);

        assertEquals(null, transfer.memo());
        assertEquals(10.00, balance(PAYEE), 0.0);
    }

    // ---- audit ----

    @Test
    void transfer_writes_exactly_one_audit_event_with_actor_action_and_details() {
        service.transfer(CHECKING, PAYEE, 42.50, "invoice 7");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditClient, times(1)).record(captor.capture());
        AuditEvent event = captor.getValue();
        assertEquals("transaction-service", event.actor());
        assertEquals("TRANSFER", event.action());
        assertEquals("TX-1001 ACC-1001 -> ACC-1003 amount=42.5 memo=invoice 7", event.details());
    }

    @Test
    void audit_details_include_the_returned_reference() {
        Transfer transfer = service.transfer(CHECKING, PAYEE, 5.00, "m");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditClient).record(captor.capture());
        assertTrue(captor.getValue().details().startsWith(transfer.reference() + " "),
                "details should start with reference: " + captor.getValue().details());
    }

    @Test
    void audit_failure_propagates_and_leaves_balances_unchanged() {
        doThrow(new RestClientException("audit-log down")).when(auditClient).record(any());

        RestClientException ex = assertThrows(RestClientException.class,
                () -> service.transfer(CHECKING, SAVINGS, 100.00, "x"));

        assertEquals("audit-log down", ex.getMessage());
        assertEquals(2500.00, balance(CHECKING), 0.0);
        assertEquals(10000.00, balance(SAVINGS), 0.0);
    }

    @Test
    void audit_failure_does_not_consume_a_reference_visible_to_next_transfer() {
        doThrow(new RestClientException("down")).when(auditClient).record(any());
        assertThrows(RestClientException.class, () -> service.transfer(CHECKING, SAVINGS, 1.00, "x"));

        // The sequence is bumped before the audit call; the next successful
        // transfer must still get a unique, strictly increasing reference.
        org.mockito.Mockito.reset(auditClient);
        Transfer next = service.transfer(CHECKING, SAVINGS, 1.00, "y");
        assertEquals("TX-1002", next.reference());
    }

    @Test
    void audit_is_written_before_balances_change() {
        Account from = repository.findById(CHECKING).orElseThrow();
        double[] balanceAtAuditTime = new double[1];
        org.mockito.Mockito.doAnswer(inv -> {
            balanceAtAuditTime[0] = from.getBalance();
            return null;
        }).when(auditClient).record(any());

        service.transfer(CHECKING, SAVINGS, 300.00, "order");

        assertEquals(2500.00, balanceAtAuditTime[0], 0.0);
        assertEquals(2200.00, from.getBalance(), 0.0);
        InOrder order = inOrder(auditClient);
        order.verify(auditClient).record(any());
    }

    // ---- validation failures ----

    @Test
    void same_source_and_destination_is_rejected_without_audit_or_balance_change() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.transfer(CHECKING, CHECKING, 10.00, "self"));

        assertEquals("Source and destination accounts must differ", ex.getMessage());
        assertEquals(2500.00, balance(CHECKING), 0.0);
        verifyNoInteractions(auditClient);
    }

    @Test
    void unknown_source_account_is_rejected_with_its_id_in_message() {
        AccountNotFoundException ex = assertThrows(AccountNotFoundException.class,
                () -> service.transfer("ACC-9999", SAVINGS, 10.00, "x"));

        assertEquals("Account not found: ACC-9999", ex.getMessage());
        assertEquals(10000.00, balance(SAVINGS), 0.0);
        verifyNoInteractions(auditClient);
    }

    @Test
    void unknown_destination_account_is_rejected_with_its_id_in_message() {
        AccountNotFoundException ex = assertThrows(AccountNotFoundException.class,
                () -> service.transfer(CHECKING, "ACC-0000", 10.00, "x"));

        assertEquals("Account not found: ACC-0000", ex.getMessage());
        assertEquals(2500.00, balance(CHECKING), 0.0);
        verifyNoInteractions(auditClient);
    }

    @Test
    void null_source_account_id_is_rejected() {
        assertThrows(RuntimeException.class, () -> service.transfer(null, SAVINGS, 10.00, "x"));
        verifyNoInteractions(auditClient);
    }

    // ---- boundary values: funds ----

    @Test
    void transfer_of_entire_balance_is_allowed_and_leaves_zero() {
        service.transfer(CHECKING, SAVINGS, 2500.00, "all in");

        assertEquals(0.00, balance(CHECKING), 0.0);
        assertEquals(12500.00, balance(SAVINGS), 0.0);
        verify(auditClient).record(any());
    }

    @Test
    void transfer_one_cent_over_balance_is_rejected_without_audit() {
        InsufficientFundsException ex = assertThrows(InsufficientFundsException.class,
                () -> service.transfer(CHECKING, SAVINGS, 2500.01, "too much"));

        assertEquals("Insufficient funds in account ACC-1001", ex.getMessage());
        assertEquals(2500.00, balance(CHECKING), 0.0);
        assertEquals(10000.00, balance(SAVINGS), 0.0);
        verify(auditClient, never()).record(any());
    }

    @Test
    void transfer_from_empty_account_is_rejected() {
        assertThrows(InsufficientFundsException.class,
                () -> service.transfer(PAYEE, CHECKING, 0.01, "x"));
        assertEquals(0.00, balance(PAYEE), 0.0);
        verifyNoInteractions(auditClient);
    }

    @Test
    void smallest_positive_amount_is_transferred() {
        service.transfer(CHECKING, PAYEE, 0.01, "penny");

        assertEquals(2499.99, balance(CHECKING), 1e-9);
        assertEquals(0.01, balance(PAYEE), 1e-9);
    }

    @Test
    void multiple_transfers_accumulate_correctly() {
        service.transfer(CHECKING, PAYEE, 100.00, "a");
        service.transfer(SAVINGS, PAYEE, 200.00, "b");
        service.transfer(PAYEE, CHECKING, 50.00, "c");

        assertEquals(2450.00, balance(CHECKING), 0.0);
        assertEquals(9800.00, balance(SAVINGS), 0.0);
        assertEquals(250.00, balance(PAYEE), 0.0);
        verify(auditClient, times(3)).record(any());
    }

    // ---- suspected bugs (business rules: "Transfer amounts must be greater than zero";
    //      "Round to 2 decimal places using banker's rounding") ----

    @Test
    @Disabled("SUSPECTED BUG: TransferService.transfer accepts a zero amount; business rule requires amount > 0")
    void zero_amount_is_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.transfer(CHECKING, SAVINGS, 0.00, "zero"));
        verifyNoInteractions(auditClient);
    }

    @Test
    @Disabled("SUSPECTED BUG: TransferService.transfer accepts a negative amount and moves money in reverse; business rule requires amount > 0")
    void negative_amount_is_rejected_and_does_not_move_money_in_reverse() {
        assertThrows(IllegalArgumentException.class,
                () -> service.transfer(PAYEE, CHECKING, -100.00, "reverse"));

        assertEquals(0.00, balance(PAYEE), 0.0);
        assertEquals(2500.00, balance(CHECKING), 0.0);
        verifyNoInteractions(auditClient);
    }

    @Test
    @Disabled("SUSPECTED BUG: TransferService.transfer accepts NaN amount (balance < NaN is false) and corrupts both balances; business rule requires amount > 0")
    void nan_amount_is_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.transfer(CHECKING, SAVINGS, Double.NaN, "nan"));
        assertEquals(2500.00, balance(CHECKING), 0.0);
        verifyNoInteractions(auditClient);
    }

    @Test
    @Disabled("SUSPECTED BUG: balances are double, not BigDecimal, so 0.10 + 0.20 leaves 0.30000000000000004 instead of 0.30 rounded HALF_EVEN")
    void amounts_are_rounded_to_two_decimals_with_bankers_rounding() {
        service.transfer(CHECKING, PAYEE, 0.10, "a");
        service.transfer(CHECKING, PAYEE, 0.20, "b");

        BigDecimal expected = new BigDecimal("0.30").setScale(2, RoundingMode.HALF_EVEN);
        assertEquals(expected, BigDecimal.valueOf(balance(PAYEE)));
    }

    @Test
    @Disabled("SUSPECTED BUG: sub-cent amounts (e.g. 10.005) are accepted unrounded; business rule requires 2dp HALF_EVEN rounding")
    void sub_cent_amount_is_rounded_half_even_before_posting() {
        service.transfer(CHECKING, PAYEE, 10.005, "sub-cent");

        assertEquals(new BigDecimal("10.00"), BigDecimal.valueOf(balance(PAYEE)));
    }
}
