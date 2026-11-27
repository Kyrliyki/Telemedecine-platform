package com.telemed.common.events;

/**
 * Integration-event routing keys published by user-service.
 *
 * <p>These contracts are shared with Python services, so field names and
 * version suffixes are part of the wire protocol. Additive changes are
 * allowed; breaking changes require a new version suffix.
 */
public final class UserEventTypes {

    private UserEventTypes() {
    }

    public static final String PROFILE_UPDATED = "user.profile.updated.v1";
    public static final String DOCTOR_VERIFIED = "user.doctor.verified.v1";
    public static final String DOCTOR_REJECTED = "user.doctor.rejected.v1";
}
