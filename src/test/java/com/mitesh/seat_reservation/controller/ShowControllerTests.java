package com.mitesh.seat_reservation.controller;

import com.mitesh.seat_reservation.config.SecurityConfig;
import com.mitesh.seat_reservation.entity.Seat;
import com.mitesh.seat_reservation.entity.Show;
import com.mitesh.seat_reservation.repository.ShowRepository;
import com.mitesh.seat_reservation.repository.UserRepository;
import com.mitesh.seat_reservation.service.ShowService;
import com.mitesh.seat_reservation.utility.SeatState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ShowController.class)
@Import({ShowService.class, SecurityConfig.class})
class ShowControllerTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private ShowRepository repository;
    @MockitoBean private UserRepository users;
    @MockitoBean private JwtDecoder decoder;

    @Test
    void authenticatedUserCanGetShowWithSeatCounts() throws Exception {
        Show show = new Show(7L, "friday-night", 25000, 4, new ArrayList<>());
        show.getSeats().add(new Seat(1L, SeatState.AVAILABLE, "A1", null, show));
        show.getSeats().add(new Seat(2L, SeatState.CONFIRMED, "A2", null, show));
        when(repository.findById(7L)).thenReturn(Optional.of(show));

        mvc.perform(get("/web/v1/show/7").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.price_paise").value(25000))
                .andExpect(jsonPath("$.per_user_limit").value(4))
                .andExpect(jsonPath("$.seats[0].seat_number").value("A1"))
                .andExpect(jsonPath("$.seats[0].status").value("available"))
                .andExpect(jsonPath("$.seats[1].status").value("confirmed"))
                .andExpect(jsonPath("$.counts.available").value(1))
                .andExpect(jsonPath("$.counts.held").value(0))
                .andExpect(jsonPath("$.counts.confirmed").value(1))
                .andExpect(jsonPath("$.counts.total_seats").value(2));
    }

    @Test
    void missingShowReturns404() throws Exception {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        mvc.perform(get("/web/v1/show/99").with(jwt())).andExpect(status().isNotFound());
    }

    @Test
    void invalidIdReturns400WithoutQueryingDatabase() throws Exception {
        for (String id : new String[]{"0", "-1", "invalid"}) {
            mvc.perform(get("/web/v1/show/" + id).with(jwt())).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(repository);
    }

    @Test
    void missingAuthenticationReturns401() throws Exception {
        mvc.perform(get("/web/v1/show/7")).andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }
}
