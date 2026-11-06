package com.mitesh.seat_reservation.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import com.mitesh.seat_reservation.repository.SeatRepository;
import com.mitesh.seat_reservation.utility.SeatState;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class ReservationMetrics {
    private final Counter confirmedReservations;
    private final Counter seatTakenDeclines;
    private final Counter perUserLimitDeclines;
    private final Counter idempotentReplays;
    private final SeatRepository seatRepository;

    public ReservationMetrics(MeterRegistry meterRegistry, SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
        confirmedReservations = Counter.builder("seat_reservation.reservations.confirmed")
                .description("Number of reservations confirmed")
                .register(meterRegistry);
        seatTakenDeclines = Counter.builder("seat_reservation.reservations.declined")
                .description("Number of reservation requests declined")
                .tag("reason", "seat_taken")
                .register(meterRegistry);
        perUserLimitDeclines = Counter.builder("seat_reservation.reservations.declined")
                .description("Number of reservation requests declined")
                .tag("reason", "per_user_limit")
                .register(meterRegistry);
        idempotentReplays = Counter.builder("seat_reservation.idempotent.replays")
                .description("Number of reservation requests served by idempotent replay")
                .register(meterRegistry);
        Gauge.builder("seat_reservation.seats.available", this, ReservationMetrics::availableSeatCount)
                .description("Current number of available seats")
                .register(meterRegistry);
    }

    public void recordConfirmedReservation() {
        incrementAfterCommit(confirmedReservations);
    }

    public void recordSeatTakenDecline() {
        seatTakenDeclines.increment();
    }

    public void recordPerUserLimitDecline() {
        perUserLimitDeclines.increment();
    }

    public void recordIdempotentReplay() {
        incrementAfterCommit(idempotentReplays);
    }

    private double availableSeatCount() {
        try {
            return seatRepository.countByState(SeatState.AVAILABLE);
        } catch (DataAccessException exception) {
            return Double.NaN;
        }
    }

    private void incrementAfterCommit(Counter counter) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            counter.increment();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                counter.increment();
            }
        });
    }
}
