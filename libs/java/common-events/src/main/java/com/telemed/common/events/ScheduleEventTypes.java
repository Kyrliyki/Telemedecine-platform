package com.telemed.common.events;

/** Shared routing keys for schedule-service integration events. */
public final class ScheduleEventTypes {

    private ScheduleEventTypes() {
    }

    public static final String SLOT_RESERVED = "schedule.slot.reserved.v1";
    public static final String SLOT_CONFIRMED = "schedule.slot.confirmed.v1";
    public static final String SLOT_RELEASED = "schedule.slot.released.v1";
    public static final String SLOT_HOLD_EXPIRED = "schedule.slot.hold_expired.v1";
    public static final String AVAILABILITY_CHANGED = "schedule.availability.changed.v1";
}
