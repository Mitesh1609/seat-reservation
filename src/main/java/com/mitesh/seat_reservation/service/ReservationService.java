package com.mitesh.seat_reservation.service;

import com.mitesh.seat_reservation.dto.ReservationResponse;
import com.mitesh.seat_reservation.dto.ReserveSeatsRequest;
import com.mitesh.seat_reservation.entity.Reservation;
import com.mitesh.seat_reservation.entity.Seat;
import com.mitesh.seat_reservation.entity.Show;
import com.mitesh.seat_reservation.entity.User;
import com.mitesh.seat_reservation.repository.ReservationRespository;
import com.mitesh.seat_reservation.repository.SeatRepository;
import com.mitesh.seat_reservation.repository.ShowRepository;
import com.mitesh.seat_reservation.repository.UserRepository;
import com.mitesh.seat_reservation.utility.ReservationStatus;
import com.mitesh.seat_reservation.utility.SeatState;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class ReservationService {

    private final ReservationRespository reservationRespository;
    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;

    public ReservationService(ReservationRespository reservationRespository, ShowRepository showRepository, SeatRepository seatRepository, UserRepository userRepository) {
        this.reservationRespository = reservationRespository;
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ReservationResponse reserveSeats(Long showId, Long userId, ReserveSeatsRequest request){
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found"));
        checkAvailability(show.getSeats(), request.seats());
        Reservation reservation = new Reservation();
        reservation.setShow(show);
        reservation.setAmountPaise((long) show.getPrice() * request.seats().size());
        List<Seat> seats = new ArrayList<>();
        request.seats().forEach(seat -> {
            seats.add(seatRepository.findByShow_IdAndSeat_number(showId, seat));
        });
        User user = userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        seats.forEach(seat -> {
            seat.setState(SeatState.CONFIRMED);
            seat.setUser(user);
            seatRepository.save(seat);
        });
        reservation.setSeats(seats);
        reservation.setIdempotencyKey(request.idempotencyKey());
        reservation.setStatus(ReservationStatus.CONFIRMED);

        reservation.setUser(user);
        Reservation savedReservation = reservationRespository.save(reservation);
        return toReservationResponse(savedReservation);
    }

    private ReservationResponse toReservationResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getShow().getId(),
                reservation.getUser().getId(),
                reservation.getSeats().stream().map(Seat::getSeat_number).toList(),
                reservation.getAmountPaise(),
                reservation.getStatus().name().toLowerCase(Locale.ROOT),
                reservation.getCreatedAt(),
                reservation.getCancelledAt()
        );
    }

    private void checkAvailability(List<Seat> showSeats, List<String> requestSeats){
        Map<String, String> allSeats = showSeats.stream().collect(Collectors.toMap(
                Seat::getSeat_number,
                seat -> seat.getState().toString()
        ));

        requestSeats.forEach(requestSeat -> {
            if (allSeats.containsKey(requestSeat)) {
                if(!SeatState.AVAILABLE.toString().equals(allSeats.get(requestSeat))){
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Seats are not available");
                };
            }else{
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Seat not found");
            }
        });
    }
}
