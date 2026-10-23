begin;

create function goserve_private.session_allowed()
returns boolean language plpgsql stable security definer set search_path = '' as $function$
declare
  claims jsonb := auth.jwt();
  methods jsonb;
begin
  if auth.uid() is null or claims->'is_anonymous' is distinct from 'false'::jsonb
    or jsonb_typeof(claims->'amr') is distinct from 'array' then
    return false;
  end if;
  methods := claims->'amr';

  -- AMR identifies OAuth, but not its provider. Ambiguous linked social identities fail closed.
  if methods @> '[{"method":"oauth"}]'::jsonb then
    return exists (
      select 1
      from auth.users u
      join public.profiles p on p.id = u.id
      join auth.identities i on i.user_id = u.id and i.provider = 'google'
      where u.id = auth.uid() and p.role = 'student'
        and u.is_anonymous is false and u.email_confirmed_at is not null
        and length(split_part(u.email, '@', 1)) between 1 and 64
        and u.email ~* $email$^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@tip\.edu\.ph$$email$
        and i.identity_data->'email_verified' = 'true'::jsonb
        and lower(i.identity_data->>'email') = lower(u.email)
        and not exists (
          select 1 from auth.identities other
          where other.user_id = u.id and other.provider not in ('email', 'phone', 'google')
        )
    );
  end if;
  return methods @> '[{"method":"password"}]'::jsonb;
end;
$function$;

create or replace function goserve_private.actor(ready boolean default true)
returns public.profiles language plpgsql security definer set search_path = '' as $$
declare
  p public.profiles;
begin
  if auth.uid() is null or auth.jwt()->'is_anonymous' is distinct from 'false'::jsonb then
    perform goserve_private.fail('AUTH_REQUIRED');
  end if;
  if not goserve_private.session_allowed() then
    perform goserve_private.fail('FORBIDDEN');
  end if;
  select * into p from public.profiles where id = auth.uid();
  if not found then perform goserve_private.fail('FORBIDDEN'); end if;
  if ready and p.profile_completed_at is null then
    perform goserve_private.fail('PROFILE_INCOMPLETE');
  end if;
  if ready and not exists (select 1 from goserve_private.terms_acceptances where profile_id = p.id) then
    perform goserve_private.fail('TERMS_REQUIRED');
  end if;
  return p;
end;
$$;

alter policy profiles_select_own on public.profiles
using (id = auth.uid() and goserve_private.session_allowed());

create or replace function public.accept_privacy_consent()
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  own_profile public.profiles;
  accepted timestamptz;
begin
  own_profile := goserve_private.actor(false);
  select * into own_profile from public.profiles where id = own_profile.id for update;
  if own_profile.consent_accepted_at is null then
    accepted := clock_timestamp();
    update public.profiles set consent_accepted_at = accepted, updated_at = accepted
      where id = own_profile.id returning * into own_profile;
    insert into goserve_private.privacy_consent_audit(profile_id, accepted_at)
      values (own_profile.id, accepted);
  end if;
  return jsonb_build_object(
    'id', own_profile.id, 'email', own_profile.email, 'display_name', own_profile.display_name,
    'role', own_profile.role, 'office_id', own_profile.office_id,
    'staff_capability', own_profile.staff_capability, 'is_demo', own_profile.is_demo,
    'consent_accepted_at', own_profile.consent_accepted_at
  );
end;
$$;

revoke all on function goserve_private.session_allowed() from public, anon, authenticated;
grant execute on function goserve_private.session_allowed() to authenticated;
commit;
