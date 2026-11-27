package com.telemed.common.events;

/** Shared routing keys for emr-service integration events. */
public final class EmrEventTypes {

    private EmrEventTypes() {
    }

    public static final String RECORD_CREATED = "emr.record.created.v1";
    public static final String RECORD_UPDATED = "emr.record.updated.v1";
    public static final String RECORD_FINALIZED = "emr.record.finalized.v1";
    public static final String RECORD_AMENDED = "emr.record.amended.v1";
    public static final String RECORD_DELETED = "emr.record.deleted.v1";
}
