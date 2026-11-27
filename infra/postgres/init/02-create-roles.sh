#!/usr/bin/env bash

set -euo pipefail

create_service_role() {
  role="$1"
  password="$2"
  schema="$3"

  psql -v ON_ERROR_STOP=1 \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" \
    -v role="$role" -v password="$password" -v schema="$schema" \
    <<'EOSQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'role', :'password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'role')\gexec
SELECT format('ALTER ROLE %I WITH PASSWORD %L', :'role', :'password')\gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), :'role')\gexec
SELECT format('ALTER SCHEMA %I OWNER TO %I', :'schema', :'role')\gexec
SELECT format('GRANT USAGE, CREATE ON SCHEMA %I TO %I', :'schema', :'role')\gexec
SELECT format('ALTER ROLE %I SET search_path = %I', :'role', :'schema')\gexec
EOSQL
}

create_service_role "${AUTH_DB_USER:-auth_svc}"         "${AUTH_DB_PASSWORD:-auth_svc}"         auth_service
create_service_role "${USER_DB_USER:-user_svc}"         "${USER_DB_PASSWORD:-user_svc}"         user_service
create_service_role "${SCHEDULE_DB_USER:-schedule_svc}" "${SCHEDULE_DB_PASSWORD:-schedule_svc}" schedule_service
create_service_role "${EMR_DB_USER:-emr_svc}"           "${EMR_DB_PASSWORD:-emr_svc}"           emr_service

create_service_role appointment_svc  "${APPOINTMENT_DB_PASSWORD:-appointment}"   appointment_service
create_service_role chat_svc         "${CHAT_DB_PASSWORD:-chat}"                 chat_service
create_service_role file_svc         "${FILE_DB_PASSWORD:-file}"                 file_service
create_service_role video_svc        "${VIDEO_DB_PASSWORD:-video}"               video_service
create_service_role notification_svc "${NOTIFICATION_DB_PASSWORD:-notification}" notification_service
