package com.telemed.common.events;

/** Shared routing keys for appointment-service integration events. */
public final class AppointmentEventTypes {

    private AppointmentEventTypes() {
    }

    public static final String CREATED = "appointment.appointment.created.v1";
    public static final String CONFIRMED = "appointment.appointment.confirmed.v1";
    public static final String CANCELLED = "appointment.appointment.cancelled.v1";
    public static final String RESCHEDULED = "appointment.appointment.rescheduled.v1";
    public static final String COMPLETED = "appointment.appointment.completed.v1";
    public static final String NO_SHOW = "appointment.appointment.no_show.v1";
}
