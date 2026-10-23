begin;
create schema goserve_auth;
revoke all on schema goserve_auth from public, anon, authenticated;
grant usage on schema goserve_auth to supabase_auth_admin;
create function goserve_auth.tip_email_allowed(email text) returns boolean
language sql immutable security invoker set search_path='' as $$
  select coalesce(octet_length(split_part(email,'@',1)) between 1 and 64
    and email ~ '^[A-Za-z0-9!#$%&''*+/=?^_`{|}~-]+(\.[A-Za-z0-9!#$%&''*+/=?^_`{|}~-]+)*@[tT][iI][pP]\.[eE][dD][uU]\.[pP][hH]$',false)
$$;
create function goserve_auth.before_user_created(event jsonb) returns jsonb
language plpgsql security invoker set search_path='' as $$
declare
  provider text := event #>> '{user,app_metadata,provider}';
begin
  if jsonb_typeof(event->'user')='object' then
    if provider='email' then return '{}'::jsonb; end if;
    if provider='google' and goserve_auth.tip_email_allowed(event #>> '{user,email}') then
      return '{}'::jsonb;
    end if;
  end if;
  return '{"error":{"http_code":403,"message":"Use a verified @tip.edu.ph Google account."}}'::jsonb;
end;
$$;
create function goserve_auth.access_token(event jsonb) returns jsonb
language plpgsql stable security invoker set search_path='' as $$
declare
  claims jsonb := event->'claims';
  actor uuid;
  oauth boolean;
  denied constant jsonb := '{"error":{"http_code":403,"message":"Use a verified @tip.edu.ph Google account."}}'::jsonb;
begin
  if jsonb_typeof(claims) is distinct from 'object' then return denied; end if;
  begin actor := (event->>'user_id')::uuid;
  exception when invalid_text_representation then return denied;
  end;
  if actor is null or claims->>'sub' is distinct from actor::text then return denied; end if;
  if event->>'authentication_method'='token_refresh'
    and (jsonb_typeof(claims->'amr') is distinct from 'array' or claims->'amr'='[]'::jsonb) then
    return denied;
  end if;
  select event->>'authentication_method'='oauth' or exists(
    select 1 from jsonb_array_elements(case when jsonb_typeof(claims->'amr')='array' then claims->'amr' else '[]'::jsonb end) entry
    where entry->>'method'='oauth'
  ) into oauth;
  if oauth then
    if not exists(
      select 1 from auth.users u
      join auth.identities i on i.user_id=u.id and i.provider='google'
      where u.id=actor and u.email_confirmed_at is not null and u.is_anonymous=false
        and goserve_auth.tip_email_allowed(u.email)
        and goserve_auth.tip_email_allowed(i.identity_data->>'email')
        and lower(i.identity_data->>'email')=lower(u.email)
        and i.identity_data->'email_verified'='true'::jsonb
        and not exists(select 1 from auth.identities other where other.user_id=u.id and other.provider not in ('google','email'))
    ) then return denied; end if;
  end if;
  return jsonb_build_object('claims',claims);
end;
$$;
revoke all on function goserve_auth.tip_email_allowed(text),goserve_auth.before_user_created(jsonb),goserve_auth.access_token(jsonb) from public,anon,authenticated;
grant execute on function goserve_auth.tip_email_allowed(text),goserve_auth.before_user_created(jsonb),goserve_auth.access_token(jsonb) to supabase_auth_admin;
commit;
