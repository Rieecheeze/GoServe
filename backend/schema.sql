begin;

create schema goserve_private;
revoke all on schema goserve_private from public, anon, authenticated;
grant usage on schema goserve_private to service_role;

create table public.offices (
    id text primary key,
    name text not null check (pg_catalog.btrim(name) <> ''),
    campus text not null,
    status text not null check (status in ('Open', 'On Lunch Break', 'Cut-Off Reached', 'Temporarily Closed')),
    note text not null default '',
    category text not null default '',
    active boolean not null default true,
    is_demo boolean not null default true,
    status_changed_by uuid references auth.users(id) on delete set null,
    status_changed_at timestamptz not null default pg_catalog.now(),
    created_at timestamptz not null default pg_catalog.now()
);

create table public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    email text not null check (pg_catalog.btrim(email) <> ''),
    display_name text not null default '',
    role text not null check (role in ('student', 'staff', 'teller')),
    office_id text references public.offices(id),
    staff_capability text,
    is_demo boolean not null default false,
    consent_accepted_at timestamptz,
    created_at timestamptz not null default pg_catalog.now(),
    updated_at timestamptz not null default pg_catalog.now(),
    check (role <> 'student' or (office_id is null and staff_capability is null))
);

create table goserve_private.privacy_consent_audit (
    profile_id uuid primary key references public.profiles(id) on delete cascade,
    accepted_at timestamptz not null
);

alter table public.offices enable row level security;
alter table public.profiles enable row level security;
alter table goserve_private.privacy_consent_audit enable row level security;

revoke all on table public.offices, public.profiles from public, anon, authenticated;
grant select on table public.offices, public.profiles to authenticated;
grant all on table public.offices, public.profiles to service_role;
revoke all on table goserve_private.privacy_consent_audit from public, anon, authenticated;
grant all on table goserve_private.privacy_consent_audit to service_role;

create policy profiles_select_own on public.profiles
    for select to authenticated
    using (id = (select auth.uid()));

create policy offices_select_members on public.offices
    for select to authenticated
    using (
        active
        and exists (
            select 1 from public.profiles as member
            where member.id = (select auth.uid())
        )
    );

create function public.accept_privacy_consent()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $function$
declare
    viewer uuid := auth.uid();
    own_profile public.profiles%rowtype;
    accepted timestamptz;
begin
    if viewer is null then
        raise exception 'Authentication required.' using errcode = '42501';
    end if;

    select * into own_profile
    from public.profiles
    where id = viewer
    for update;

    if not found then
        raise exception 'A provisioned GoServe profile is required.' using errcode = '42501';
    end if;

    if own_profile.consent_accepted_at is null then
        accepted := pg_catalog.clock_timestamp();
        update public.profiles
        set consent_accepted_at = accepted, updated_at = accepted
        where id = viewer
        returning * into own_profile;

        insert into goserve_private.privacy_consent_audit(profile_id, accepted_at)
        values (viewer, accepted);
    end if;

    return pg_catalog.jsonb_build_object(
        'id', own_profile.id,
        'email', own_profile.email,
        'display_name', own_profile.display_name,
        'role', own_profile.role,
        'office_id', own_profile.office_id,
        'staff_capability', own_profile.staff_capability,
        'is_demo', own_profile.is_demo,
        'consent_accepted_at', own_profile.consent_accepted_at
    );
end;
$function$;

revoke all on function public.accept_privacy_consent() from public, anon, authenticated, service_role;
grant execute on function public.accept_privacy_consent() to authenticated;

-- These seed rows are samples, not official office availability or policies.
insert into public.offices(id, name, campus, status, note, category, is_demo)
values
    ('OFF-REG', 'Registrar', 'Casal / Arlegui', 'Open', 'Non-official demo data', 'Documents', true),
    ('OFF-ACC', 'Student Accounting', 'Casal / Arlegui', 'Open', 'Non-official demo data', 'Payments', true),
    ('OFF-CASH', 'Cashiering', 'Casal / Arlegui', 'Open', 'Non-official demo data', 'Payments', true),
    ('OFF-GCC', 'Guidance and Counseling', 'Casal / Arlegui', 'Open', 'Non-official demo data', 'Consultations', true),
    ('DEP-CCS', 'College of Computer Studies', 'Casal / Arlegui', 'Open', 'Non-official demo data', 'Consultations', true);

commit;
