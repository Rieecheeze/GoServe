-- Run the entire file with a database owner connection after applying schema.sql.
-- All fixtures are synthetic and the final ROLLBACK removes every change.
-- If an assertion fails, stop and issue ROLLBACK in the same session.
begin;
set local plpgsql.check_asserts = on;
set local row_security = on;

insert into auth.users(id, aud, role, email, email_confirmed_at, raw_app_meta_data,
                       raw_user_meta_data, created_at, updated_at)
values
    ('00000000-0000-4000-8000-000000000101', 'authenticated', 'authenticated',
     'rls-student@goserve-test.invalid', pg_catalog.now(), '{"provider":"email","providers":["email"]}', '{}',
     pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-4000-8000-000000000102', 'authenticated', 'authenticated',
     'rls-staff@goserve-test.invalid', pg_catalog.now(), '{"provider":"email","providers":["email"]}', '{}',
     pg_catalog.now(), pg_catalog.now()),
    ('00000000-0000-4000-8000-000000000103', 'authenticated', 'authenticated',
     'rls-unprovisioned@goserve-test.invalid', pg_catalog.now(), '{"provider":"email","providers":["email"]}', '{}',
     pg_catalog.now(), pg_catalog.now());

insert into public.offices(id, name, campus, status, note, category, active, is_demo)
values
    ('TEST-RLS-OFFICE', 'RLS verification fixture', 'Test', 'Open', 'Rollback-only fixture', 'Test', true, true),
    ('TEST-RLS-HIDDEN', 'Inactive RLS fixture', 'Test', 'Open', 'Rollback-only fixture', 'Test', false, true);

insert into public.profiles(id, email, display_name, role, office_id, staff_capability, is_demo)
values
    ('00000000-0000-4000-8000-000000000101', 'rls-student@goserve-test.invalid', 'RLS student', 'student', null, null, true),
    ('00000000-0000-4000-8000-000000000102', 'rls-staff@goserve-test.invalid', 'RLS staff', 'staff', 'TEST-RLS-OFFICE', 'faculty', true);

set local role authenticated;
select pg_catalog.set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000101', true);
select pg_catalog.set_config('request.jwt.claims', '{"sub":"00000000-0000-4000-8000-000000000101","role":"authenticated","is_anonymous":false}', true);

do $test$
begin
    assert (select pg_catalog.count(*) from public.profiles) = 1, 'A student must see only their own profile.';
    assert (select pg_catalog.count(*) from public.profiles where id = '00000000-0000-4000-8000-000000000102') = 0,
        'Another user profile must be hidden.';
    assert (select pg_catalog.count(*) from public.offices where id = 'TEST-RLS-OFFICE') = 1,
        'A provisioned member must see active offices.';
    assert (select pg_catalog.count(*) from public.offices where id = 'TEST-RLS-HIDDEN') = 0,
        'Inactive offices must be hidden.';

    begin
        update public.profiles set role = 'teller' where id = auth.uid();
        raise exception 'Direct role update was not denied.';
    exception when insufficient_privilege then null;
    end;
    begin
        update public.profiles set staff_capability = 'faculty' where id = auth.uid();
        raise exception 'Direct capability update was not denied.';
    exception when insufficient_privilege then null;
    end;
    begin
        update public.profiles set consent_accepted_at = pg_catalog.now() where id = auth.uid();
        raise exception 'Direct consent update was not denied.';
    exception when insufficient_privilege then null;
    end;
    begin
        update public.profiles set office_id = 'TEST-RLS-OFFICE' where id = auth.uid();
        raise exception 'Direct office assignment update was not denied.';
    exception when insufficient_privilege then null;
    end;
    begin
        update public.profiles set is_demo = false where id = auth.uid();
        raise exception 'Direct demo status update was not denied.';
    exception when insufficient_privilege then null;
    end;
    begin
        update public.offices set status = 'Temporarily Closed' where id = 'TEST-RLS-OFFICE';
        raise exception 'Direct office status update was not denied.';
    exception when insufficient_privilege then null;
    end;
    begin
        perform 1 from goserve_private.privacy_consent_audit;
        raise exception 'Client access to the private audit was not denied.';
    exception when insufficient_privilege then null;
    end;
end;
$test$;

do $test$
declare
    first_response jsonb;
    second_response jsonb;
    first_timestamp timestamptz;
begin
    first_response := public.accept_privacy_consent();
    first_timestamp := (first_response ->> 'consent_accepted_at')::timestamptz;
    assert first_response ->> 'id' = '00000000-0000-4000-8000-000000000101', 'Consent must return the caller profile.';
    assert first_response ->> 'role' = 'student', 'Consent must preserve the caller role.';
    assert first_timestamp is not null, 'Consent must record an acceptance timestamp.';

    second_response := public.accept_privacy_consent();
    assert (second_response ->> 'consent_accepted_at')::timestamptz = first_timestamp,
        'Repeated consent must preserve its original timestamp.';
    assert (select consent_accepted_at from public.profiles where id = auth.uid()) = first_timestamp,
        'The recorded profile timestamp must match the RPC response.';
end;
$test$;

select pg_catalog.set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000103', true);
select pg_catalog.set_config('request.jwt.claims', '{"sub":"00000000-0000-4000-8000-000000000103","role":"authenticated","is_anonymous":false}', true);
do $test$
begin
    assert (select pg_catalog.count(*) from public.profiles) = 0, 'An unprovisioned user must have no visible profiles.';
    assert (select pg_catalog.count(*) from public.offices) = 0, 'An unprovisioned user must not read the directory.';
    begin
        perform public.accept_privacy_consent();
        raise exception 'An unprovisioned account was able to accept profile consent.';
    exception when insufficient_privilege then null;
    end;
end;
$test$;

reset role;
do $test$
begin
    assert (select pg_catalog.count(*) from goserve_private.privacy_consent_audit
            where profile_id = '00000000-0000-4000-8000-000000000101') = 1,
        'Repeated acceptance must create exactly one audit row.';
    assert (select a.accepted_at = p.consent_accepted_at
            from goserve_private.privacy_consent_audit as a
            join public.profiles as p on p.id = a.profile_id
            where p.id = '00000000-0000-4000-8000-000000000101'),
        'Audit and profile timestamps must be identical.';
    assert (select consent_accepted_at from public.profiles
            where id = '00000000-0000-4000-8000-000000000102') is null,
        'Another user consent must remain untouched.';
end;
$test$;

set local role anon;
select pg_catalog.set_config('request.jwt.claim.sub', '', true);
select pg_catalog.set_config('request.jwt.claims', '{"role":"anon"}', true);
do $test$
begin
    begin
        perform public.accept_privacy_consent();
        raise exception 'Anonymous consent execution was not denied.';
    exception when insufficient_privilege then null;
    end;
    begin
        perform 1 from public.offices;
        raise exception 'Anonymous directory access was not denied.';
    exception when insufficient_privilege then null;
    end;
end;
$test$;

reset role;
select 'PASS: GoServe RLS, write denial, consent idempotence and audit assertions' as result;
rollback;
