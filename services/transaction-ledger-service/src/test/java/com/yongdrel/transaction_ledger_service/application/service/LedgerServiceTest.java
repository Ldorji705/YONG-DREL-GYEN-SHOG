package com.yongdrel.transaction_ledger_service.application.service;

import com.yongdrel.transaction_ledger_service.adapter.in.web.dto.CreateLedgerEntryRequest;
import com.yongdrel.transaction_ledger_service.adapter.persistence.LedgerEntryRepository;
import com.yongdrel.transaction_ledger_service.application.exception.LedgerReferenceConflictException;
import com.yongdrel.transaction_ledger_service.domain.model.EntryDirection;
import com.yongdrel.transaction_ledger_service.domain.model.LedgerEntry;
import com.yongdrel.transaction_ledger_service.domain.model.SourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private LedgerEntryRepository repository;

    @InjectMocks
    private LedgerService service;

    private static CreateLedgerEntryRequest request(String account, String ref, String amount) {
        return new CreateLedgerEntryRequest(
                account, ref, SourceType.WALLET, EntryDirection.DEBIT,
                new BigDecimal(amount), "BTN", "test", null);
    }

    private static LedgerEntry storedEntry(String account, String ref, String amount) {
        return new LedgerEntry(account, ref, SourceType.WALLET, EntryDirection.DEBIT,
                new BigDecimal(amount), "BTN", "test", Instant.now());
    }

    @Test
    void newReference_isSavedAndMarkedCreated() {
        when(repository.findByReferenceId("ref-1")).thenReturn(Optional.empty());
        when(repository.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        LedgerService.RecordResult result = service.record(request("1", "ref-1", "50.00"));

        assertThat(result.created()).isTrue();
        assertThat(result.entry().getReferenceId()).isEqualTo("ref-1");
        assertThat(result.entry().getAmount()).isEqualByComparingTo("50.00");
        assertThat(result.entry().getAmount().scale()).isEqualTo(4);
    }

    @Test
    void sameReferenceSameDetails_returnsExistingWithoutSaving() {
        LedgerEntry existing = storedEntry("1", "ref-1", "50.0000");
        when(repository.findByReferenceId("ref-1")).thenReturn(Optional.of(existing));

        // 50.00 and 50.0000 are the same amount, so this is a safe retry.
        LedgerService.RecordResult result = service.record(request("1", "ref-1", "50.00"));

        assertThat(result.created()).isFalse();
        assertThat(result.entry()).isSameAs(existing);
        verify(repository, never()).save(any());
    }

    @Test
    void sameReferenceDifferentAmount_isRejectedAsConflict() {
        when(repository.findByReferenceId("ref-1"))
                .thenReturn(Optional.of(storedEntry("1", "ref-1", "50.00")));

        assertThatThrownBy(() -> service.record(request("1", "ref-1", "999.00")))
                .isInstanceOf(LedgerReferenceConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void sameReferenceDifferentAccount_isRejectedAsConflict() {
        when(repository.findByReferenceId("ref-1"))
                .thenReturn(Optional.of(storedEntry("1", "ref-1", "50.00")));

        assertThatThrownBy(() -> service.record(request("2", "ref-1", "50.00")))
                .isInstanceOf(LedgerReferenceConflictException.class);
    }

    @Test
    void lostInsertRace_returnsTheWinningEntry() {
        LedgerEntry winner = storedEntry("1", "ref-1", "50.00");
        // First lookup: not there yet. Insert then hits the UNIQUE constraint.
        // Second lookup: the other request's row is now visible.
        when(repository.findByReferenceId("ref-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(repository.save(any(LedgerEntry.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry 'ref-1'"));

        LedgerService.RecordResult result = service.record(request("1", "ref-1", "50.00"));

        assertThat(result.created()).isFalse();
        assertThat(result.entry()).isSameAs(winner);
    }

    @Test
    void missingOccurredAt_defaultsToNow() {
        when(repository.findByReferenceId("ref-1")).thenReturn(Optional.empty());
        when(repository.save(any(LedgerEntry.class))).thenAnswer(inv -> inv.getArgument(0));
        Instant before = Instant.now().minusSeconds(1);

        LedgerEntry saved = service.record(request("1", "ref-1", "10")).entry();

        assertThat(saved.getOccurredAt()).isAfter(before).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @SuppressWarnings("unchecked")
    void history_clampsPageAndSize() {
        when(repository.findByAccountIdOrderByOccurredAtDesc(eq("1"), any(Pageable.class)))
                .thenReturn(mock(Page.class));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        service.findForAccount("1", -3, 5000);

        verify(repository).findByAccountIdOrderByOccurredAtDesc(eq("1"), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }
}
