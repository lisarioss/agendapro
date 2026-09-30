package com.lisarios.agendapro.common;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class Api {
    private Api() {}
    public enum Role { ADMIN, ATTENDANT, PROFESSIONAL }
    public enum Status { PENDING, CONFIRMED, COMPLETED, CANCELED, NO_SHOW }
    public record Register(@NotBlank @Size(max=120) String companyName,
        @NotBlank @Pattern(regexp="[a-z0-9][a-z0-9-]{2,59}") String slug,
        @NotBlank @Size(max=80) String timezone,
        @NotBlank @Size(max=120) String name, @NotBlank @Email @Size(max=254) String email,
        @NotBlank @Size(min=12,max=72) String password) {}
    public record Login(@NotBlank @Size(max=60) String slug,
        @NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=72) String password) {}
    public record UserInput(@NotBlank @Size(max=120) String name,
        @NotBlank @Email @Size(max=254) String email, @NotBlank @Size(min=12,max=72) String password,
        @NotNull Role role, UUID professionalId) {}
    public record PasswordInput(@NotBlank @Size(max=72) String currentPassword,
        @NotBlank @Size(min=12,max=72) String newPassword) {}
    public record ClientInput(@NotBlank @Size(max=120) String name,
        @Email @Size(max=254) String email, @Size(max=30) String phone) {}
    public record ProfessionalInput(@NotBlank @Size(max=120) String name) {}
    public record ServiceInput(@NotBlank @Size(max=120) String name,
        @Min(5) @Max(480) int durationMinutes,
        @NotNull @DecimalMin("0.00") @DecimalMax("9999999999.99") @Digits(integer=10,fraction=2) BigDecimal price) {}
    public record HoursInput(@Min(1) @Max(7) int dayOfWeek,
        @NotNull LocalTime startTime, @NotNull LocalTime endTime) {}
    public record TimeOffInput(@NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt, @NotBlank @Size(max=200) String reason) {}
    public record AppointmentInput(@NotNull UUID professionalId, @NotNull UUID clientId,
        @NotNull UUID serviceId, @NotNull OffsetDateTime startsAt, @Size(max=1000) String notes) {}
    public record RescheduleInput(@NotNull OffsetDateTime startsAt) {}
    public record StatusInput(@NotNull Status status) {}
}
