package com.mitesh.seat_reservation.repository;

import com.mitesh.seat_reservation.entity.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select s from Seat s
        where s.show.id = :showId
          and s.seat_number in :seatNumbers
        order by s.id
        """)
    List<Seat> findAllByShow_IdAndSeat_numberInOrderByIdAsc(
            @Param("showId") Long showId,
            @Param("seatNumbers") List<String> seatNumbers
    );
}
