package com.mitesh.seat_reservation.repository;

import com.mitesh.seat_reservation.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {
    Seat findByShow_IdAndSeat_number(Long id, String seat);
}
