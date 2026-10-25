-- Apply only after the updated Android recovery routes are available.
begin;
update goserve_private.recovery_settings set enforce_readiness=true,enforce_sessions=true where id;
commit;
