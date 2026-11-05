package com.mitesh.seat_reservation.repository;

import com.mitesh.seat_reservation.entity.Reservation;
import com.mitesh.seat_reservation.entity.User;
import com.mitesh.seat_reservation.utility.ReservationStatus;
import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRespository extends JpaRepository<Reservation, Long> {
    Reservation findByIdempotencyKeyAndUser(String idempotencyKey, User user);

    List<Reservation> findByUserAndShow_IdAndStatus(User user, Long showId, ReservationStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Reservation findByIdAndUser(Long id, User user);
}
