package com.mitesh.seat_reservation.controller;

import com.mitesh.seat_reservation.dto.CreateShowRequest;
import com.mitesh.seat_reservation.dto.ShowResponse;
import com.mitesh.seat_reservation.service.ShowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/web/v1/show")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @PostMapping("/create")
    public ResponseEntity<ShowResponse> createShow(@Valid @RequestBody CreateShowRequest createShowRequest) {
        return new ResponseEntity<>(showService.createShow(createShowRequest), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShowResponse> getShowDetails(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(showService.getShowDetails(id));
    }
}
