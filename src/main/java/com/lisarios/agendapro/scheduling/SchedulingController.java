package com.lisarios.agendapro.scheduling;

import com.lisarios.agendapro.common.Api;
import com.lisarios.agendapro.common.Api.HoursInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class SchedulingController {
    private final SchedulingService service;
    public SchedulingController(SchedulingService service) { this.service=service; }
    @GetMapping("/professionals/{id}/working-hours") public List<Map<String,Object>> hours(@PathVariable UUID id) { return service.hours(id); }
    @PutMapping("/professionals/{id}/working-hours") public List<Map<String,Object>> hours(@PathVariable UUID id,@RequestBody @NotNull @Size(max=28) List<@NotNull @Valid HoursInput> input) { return service.hours(id,input); }
    @GetMapping("/professionals/{id}/time-off") public List<Map<String,Object>> timeOff(@PathVariable UUID id) { return service.timeOff(id); }
    @PostMapping("/professionals/{id}/time-off") @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> timeOff(@PathVariable UUID id,@Valid @RequestBody Api.TimeOffInput input) { return service.timeOff(id,input); }
    @DeleteMapping("/professionals/{id}/time-off/{offId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void removeTimeOff(@PathVariable UUID id,@PathVariable UUID offId) { service.deleteTimeOff(id,offId); }
    @GetMapping("/availability") public List<Map<String,Object>> availability(@RequestParam UUID professionalId,@RequestParam UUID serviceId,@RequestParam LocalDate date,@RequestParam(defaultValue="15") int stepMinutes) { return service.availability(professionalId,serviceId,date,stepMinutes); }
    @GetMapping("/appointments") public List<Map<String,Object>> appointments(@RequestParam OffsetDateTime from,@RequestParam OffsetDateTime to,@RequestParam(required=false) UUID professionalId,@RequestParam(required=false) Api.Status status,@RequestParam(defaultValue="100") int limit,@RequestParam(defaultValue="0") int offset) { return service.appointments(from,to,professionalId,status,limit,offset); }
    @PostMapping("/appointments") @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> create(@Valid @RequestBody Api.AppointmentInput input) { return service.create(input); }
    @GetMapping("/appointments/{id}") public Map<String,Object> appointment(@PathVariable UUID id) { return service.appointment(id); }
    @PatchMapping("/appointments/{id}/reschedule") public Map<String,Object> reschedule(@PathVariable UUID id,@Valid @RequestBody Api.RescheduleInput input) { return service.reschedule(id,input); }
    @PatchMapping("/appointments/{id}/status") public Map<String,Object> status(@PathVariable UUID id,@Valid @RequestBody Api.StatusInput input) { return service.status(id,input); }
    @GetMapping("/appointments/{id}/events") public List<Map<String,Object>> events(@PathVariable UUID id) { return service.events(id); }
}
