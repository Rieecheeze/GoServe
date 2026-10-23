begin;
create function public.cleanup_worker_configuration(p_token text) returns jsonb
language plpgsql security definer set search_path='' as $$
declare policy jsonb; seconds integer;
begin
 if p_token is null or length(p_token)<16 or length(p_token)>256 then raise exception 'FORBIDDEN'; end if;
 select value into policy from goserve_private.policies where name='cleanup_worker';
 if policy is null or policy->>'token_digest' is distinct from encode(sha256(convert_to(p_token,'UTF8')),'hex')
 or coalesce(policy->>'lease_seconds','')!~'^[0-9]{1,3}$' then raise exception 'FORBIDDEN'; end if;
 seconds:=(policy->>'lease_seconds')::integer;
 if seconds<30 or seconds>300 then raise exception 'FORBIDDEN'; end if;
 return jsonb_build_object('lease_seconds',seconds);
end $$;
revoke all on function public.cleanup_worker_configuration(text) from public,anon,authenticated;
grant execute on function public.cleanup_worker_configuration(text) to service_role;
commit;
