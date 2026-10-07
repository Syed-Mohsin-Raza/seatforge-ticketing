package com.seatforge.payment.service;

import com.seatforge.payment.domain.Payment;
import com.seatforge.payment.domain.PaymentStatus;
import com.seatforge.payment.provider.MockPaymentProvider;
import com.seatforge.payment.repository.PaymentRepository;
import com.seatforge.payment.service.exceptions.PaymentFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private PaymentRepository repository;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        repository = mock(PaymentRepository.class);
        service = new PaymentService(repository, new MockPaymentProvider());
    }

    @Test
    void authorizeWithNewKeyCreatesPayment() {
        when(repository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        Payment p = service.authorize(100L, "user-1", 5000L, "key-1");

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(p.getBookingId()).isEqualTo(100L);
        assertThat(p.getAmountCents()).isEqualTo(5000L);
        assertThat(p.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(p.getAuthorizationId()).startsWith("auth_");
    }

    @Test
    void authorizeWithExistingKeyReturnsSame() {
        Payment existing = Payment.authorize(
                100L, "user-1", 5000L, "auth_existing", "ref", "key-1");
        when(repository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        Payment result = service.authorize(100L, "user-1", 5000L, "key-1");

        assertThat(result).isSameAs(existing);
    }

    @Test
    void authorizeWithInvalidAmountFails() {
        when(repository.findByIdempotencyKey("key-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authorize(100L, "user-1", 999L, "key-2"))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining("declined");
    }
}