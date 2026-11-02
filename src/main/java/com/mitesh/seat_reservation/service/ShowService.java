package com.mitesh.seat_reservation.service;

import com.mitesh.seat_reservation.dto.CreateShowRequest;
import com.mitesh.seat_reservation.dto.SeatCountsResponse;
import com.mitesh.seat_reservation.dto.SeatResponse;
import com.mitesh.seat_reservation.dto.ShowResponse;
import com.mitesh.seat_reservation.entity.Seat;
import com.mitesh.seat_reservation.entity.Show;
import com.mitesh.seat_reservation.repository.ShowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.*;

@Service
@Validated
public class ShowService {

    private final ShowRepository showRepository;
    public  ShowService(ShowRepository showRepository) {
        this.showRepository = showRepository;
    }

    @Transactional
    public ShowResponse createShow(@NotNull @Valid CreateShowRequest createShowRequest) {
        validateSeatUniqueness(createShowRequest);
        Show show = new Show();
        show.setName(createShowRequest.name());
        show.setPrice(createShowRequest.pricePaise());
        show.setUser_limit(createShowRequest.perUserLimit());
        List<Seat> seats = new ArrayList<>();
        createShowRequest.seats().forEach(seatNumber -> {
            Seat seat = new Seat();
            seat.setSeat_number(seatNumber);
            seat.setShow(show);
            seats.add(seat);
        });
        show.setSeats(seats);
        return toShowResponse(showRepository.save(show));
    }

    private void validateSeatUniqueness(CreateShowRequest request) {
        Set<String> seatNumbers = new HashSet<>();
        for (String seatNumber : request.seats()) {
            if (!seatNumbers.add(seatNumber)) {
                throw badRequest("Seat numbers must be unique within a show");
            }
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    @Transactional(readOnly = true)
    public ShowResponse getShowDetails(@NotNull @Positive Long id) {
        Show show = showRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found"));
        return toShowResponse(show);
    }

    private ShowResponse toShowResponse(Show show) {
        List<SeatResponse> seatResponses = new ArrayList<>(show.getSeats().size());
        long available = 0;
        long held = 0;
        long confirmed = 0;
        // Build seat statuses and counts from the same loaded collection in one pass.
        for (Seat seat : show.getSeats()) {
            seatResponses.add(new SeatResponse(seat.getId(), seat.getSeat_number(),
                    seat.getState().name().toLowerCase(Locale.ROOT)));
            switch (seat.getState()) {
                case AVAILABLE -> available++;
                case HELD -> held++;
                case CONFIRMED -> confirmed++;
            }
        }
        SeatCountsResponse counts = new SeatCountsResponse(available, held, confirmed, seatResponses.size());
        return new ShowResponse(show.getId(), show.getName(), show.getPrice(),
                show.getUser_limit(), seatResponses, counts);
    }
}
