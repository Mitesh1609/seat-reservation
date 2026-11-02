package com.mitesh.seat_reservation.service;

import com.mitesh.seat_reservation.dto.CreateShowRequest;
import com.mitesh.seat_reservation.dto.SeatCountsResponse;
import com.mitesh.seat_reservation.entity.Seat;
import com.mitesh.seat_reservation.entity.Show;
import com.mitesh.seat_reservation.repository.ShowRepository;
import com.mitesh.seat_reservation.utility.SeatState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShowServiceTests {
    @Mock private ShowRepository repository;
    @InjectMocks private ShowService service;

    @Test
    void getMapsEachStateAndReconcilesCounts() {
        Show show = new Show(7L, "friday-night", 25000, 4, new ArrayList<>());
        show.getSeats().add(new Seat(1L, SeatState.AVAILABLE, "A1", null, show));
        show.getSeats().add(new Seat(2L, SeatState.HELD, "A2", null, show));
        show.getSeats().add(new Seat(3L, SeatState.CONFIRMED, "A3", null, show));
        when(repository.findById(7L)).thenReturn(Optional.of(show));

        var response = service.getShowDetails(7L);

        assertEquals(7L, response.id());
        assertEquals("friday-night", response.name());
        assertEquals(25000, response.pricePaise());
        assertEquals(4, response.perUserLimit());
        assertEquals(List.of("available", "held", "confirmed"),
                response.seats().stream().map(seat -> seat.status()).toList());
        assertEquals(List.of("A1", "A2", "A3"),
                response.seats().stream().map(seat -> seat.seatNumber()).toList());
        assertEquals(new SeatCountsResponse(1, 1, 1, 3), response.counts());
        assertEquals(response.seats().size(), response.counts().totalSeats());
    }

    @Test
    void missingShowReturnsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        var exception = assertThrows(ResponseStatusException.class, () -> service.getShowDetails(99L));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void createUsesPersistedSeatIdsAndAvailableCounts() {
        when(repository.save(any(Show.class))).thenAnswer(invocation -> {
            Show input = invocation.getArgument(0);
            assertEquals("friday-night", input.getName());
            assertEquals(25000, input.getPrice());
            assertEquals(4, input.getUser_limit());
            assertEquals(List.of("A1", "A2"), input.getSeats().stream().map(Seat::getSeat_number).toList());
            for (Seat seat : input.getSeats()) {
                assertSame(input, seat.getShow());
                assertEquals(SeatState.AVAILABLE, seat.getState());
            }
            Show saved = new Show(7L, input.getName(), input.getPrice(), input.getUser_limit(), new ArrayList<>());
            saved.getSeats().add(new Seat(10L, SeatState.AVAILABLE, "A1", null, saved));
            saved.getSeats().add(new Seat(11L, SeatState.AVAILABLE, "A2", null, saved));
            return saved;
        });

        var response = service.createShow(new CreateShowRequest("friday-night", List.of("A1", "A2"), 25000, null));

        assertEquals(7L, response.id());
        assertEquals(List.of(10L, 11L), response.seats().stream().map(seat -> seat.id()).toList());
        assertEquals(new SeatCountsResponse(2, 0, 0, 2), response.counts());
    }

    @Test
    void duplicateSeatLabelsAreRejectedBeforeSaving() {
        var exception = assertThrows(ResponseStatusException.class, () -> service.createShow(
                new CreateShowRequest("friday-night", List.of("A1", "A1"), 25000, 4)));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(repository);
    }
}
