package com.mitesh.seat_reservation.service;

import com.mitesh.seat_reservation.dto.ReservationResponse;
import com.mitesh.seat_reservation.dto.ReserveSeatsRequest;
import com.mitesh.seat_reservation.entity.Reservation;
import com.mitesh.seat_reservation.entity.Seat;
import com.mitesh.seat_reservation.entity.Show;
import com.mitesh.seat_reservation.entity.User;
import com.mitesh.seat_reservation.metrics.ReservationMetrics;
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

import java.time.Instant;
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
    private final ReservationMetrics reservationMetrics;

    public ReservationService(ReservationRespository reservationRespository, ShowRepository showRepository, SeatRepository seatRepository, UserRepository userRepository, ReservationMetrics reservationMetrics) {
        this.reservationRespository = reservationRespository;
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.userRepository = userRepository;
        this.reservationMetrics = reservationMetrics;
    }

    @Transactional
    public ReservationResponse reserveSeats(Long showId, Long userId, ReserveSeatsRequest request){
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Reservation idempotentReservation = reservationRespository.findByIdempotencyKeyAndUser(request.idempotencyKey(), user);
        if(idempotentReservation != null){
            List<String> idempotentSeats = idempotentReservation.getSeats().stream().map(Seat::getSeat_number).toList();
            if(idempotentReservation.getShow().getId() == showId && idempotentSeats.equals(request.seats())) {
                reservationMetrics.recordIdempotentReplay();
                return toReservationResponse(idempotentReservation);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid request");
        }
        List<Seat> seats = seatRepository.findAllByShow_IdAndSeat_numberInOrderByIdAsc(showId, request.seats());
        if(seats.size() != request.seats().size() ){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid number of seats");
        }
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found"));
        List<Reservation> userBookedReservations = reservationRespository.findByUserAndShow_IdAndStatus(user, showId, ReservationStatus.CONFIRMED);
        long bookedSeats = 0;
        for (Reservation reservation : userBookedReservations) {
            bookedSeats += reservation.getSeats().size();
        }
        if(bookedSeats + request.seats().size() > show.getUser_limit()){
            reservationMetrics.recordPerUserLimitDecline();
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat limit exceeded");
        }
        checkAvailability(seats, request.seats());
        Reservation reservation = new Reservation();
        reservation.setShow(show);
        reservation.setAmountPaise((long) show.getPrice() * request.seats().size());
        reservation.setSeats(seats);
        reservation.setIdempotencyKey(request.idempotencyKey());
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setUser(user);
        Reservation savedReservation = reservationRespository.save(reservation);
        seats.forEach(seat -> {
            seat.setState(SeatState.CONFIRMED);
            seat.setUser(user);
        });
        seatRepository.saveAll(seats);
        reservationMetrics.recordConfirmedReservation();
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
                    reservationMetrics.recordSeatTakenDecline();
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Seats are not available");
                };
            }else{
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Seat not found");
            }
        });
    }

    @Transactional
    public ReservationResponse cancelReservation(Long id, Long userId) {
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Reservation reservation = reservationRespository.findByIdAndUser(id, user);
        if(reservation == null){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found");
        }
        if(reservation.getStatus().equals(ReservationStatus.CANCELLED)){
            return toReservationResponse(reservation);
        }
        List<String> seatNumbers = reservation.getSeats()
                .stream()
                .map(Seat::getSeat_number)
                .toList();

        List<Seat> seats =
                seatRepository.findAllByShow_IdAndSeat_numberInOrderByIdAsc(
                        reservation.getShow().getId(),
                        seatNumbers
                );
        if (seats.size() != seatNumbers.size()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Reservation seats are not in a cancellable state"
            );
        }
        validateSeatsForCancellation(seats, reservation.getUser());
        seats.forEach(seat -> {
            seat.setState(SeatState.AVAILABLE);
            seat.setUser(null);
        });
        seatRepository.saveAll(seats);
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelledAt(Instant.now());
        Reservation savedReservation = reservationRespository.save(reservation);
        return  toReservationResponse(savedReservation);
    }

    private void validateSeatsForCancellation(List<Seat> seats, User user) {
        for (Seat seat : seats) {
            if (seat.getState() != SeatState.CONFIRMED
                    || seat.getUser() == null
                    || !seat.getUser().getId().equals(user.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Reservation seats are not in a cancellable state"
                );
            }
        }
    }
}
