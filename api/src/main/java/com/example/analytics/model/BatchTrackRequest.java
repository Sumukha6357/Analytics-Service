package com.example.analytics.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class BatchTrackRequest {

    @Valid
    @NotEmpty
    private List<TrackEventRequest> events;

    public List<TrackEventRequest> getEvents() {
        return events;
    }

    public void setEvents(List<TrackEventRequest> events) {
        this.events = events;
    }
}
