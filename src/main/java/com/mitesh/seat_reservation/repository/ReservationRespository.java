package com.mitesh.seat_reservation.repository;

import com.mitesh.seat_reservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReservationRespository extends JpaRepository<Reservation, Long> {
}
