begin;

create table goserve_private.recovery_settings (
 id boolean primary key default true check(id), enforce_readiness boolean not null default false,
 enforce_sessions boolean not null default false
);
insert into goserve_private.recovery_settings(id)values(true);
create table goserve_private.recovery_accounts (
 owner_id uuid primary key references public.profiles(id),version bigint not null default 1,
 generation uuid,acknowledged_at timestamptz
);
create table goserve_private.recovery_codes (
 code_digest text primary key check(code_digest ~ '^[a-f0-9]{64}$'),owner_id uuid not null references public.profiles(id),
 generation uuid not null,created_at timestamptz not null default clock_timestamp(),used_at timestamptz,revoked_at timestamptz
);
create index recovery_codes_owner on goserve_private.recovery_codes(owner_id,generation);
create table goserve_private.recovery_grants (
 grant_digest text primary key check(grant_digest ~ '^[a-f0-9]{64}$'),owner_id uuid not null references public.profiles(id),
 version bigint not null,purpose text not null default 'password_reset' check(purpose='password_reset'),
 challenge text not null check(challenge ~ '^[A-Za-z0-9_-]{43}$'),created_at timestamptz not null default clock_timestamp(),
 expires_at timestamptz not null,claimed_at timestamptz,operation_id uuid
);
create table goserve_private.recovery_jobs (
 operation_id uuid primary key,owner_id uuid not null references public.profiles(id),grant_digest text not null unique references goserve_private.recovery_grants(grant_digest),
 state text not null default 'pending' check(state in('pending','completed','failed')),created_at timestamptz not null default clock_timestamp(),finished_at timestamptz
);
create unique index recovery_one_mutation on goserve_private.recovery_jobs(owner_id)where state='pending';
create table goserve_private.recovery_rates (
 scope text not null check(scope in('account','ip')),key_digest text not null,bucket timestamptz not null,attempts integer not null,
 primary key(scope,key_digest,bucket)
);
create table goserve_private.recovery_audit (
 id uuid primary key default gen_random_uuid(),owner_id uuid not null references public.profiles(id),operation_id uuid,event text not null,created_at timestamptz not null default clock_timestamp()
);
alter table goserve_private.recovery_settings enable row level security;
alter table goserve_private.recovery_accounts enable row level security;
alter table goserve_private.recovery_codes enable row level security;
alter table goserve_private.recovery_grants enable row level security;
alter table goserve_private.recovery_jobs enable row level security;
alter table goserve_private.recovery_rates enable row level security;
alter table goserve_private.recovery_audit enable row level security;
revoke all on goserve_private.recovery_settings,goserve_private.recovery_accounts,goserve_private.recovery_codes,
 goserve_private.recovery_grants,goserve_private.recovery_jobs,goserve_private.recovery_rates,goserve_private.recovery_audit from public,anon,authenticated;

create function goserve_private.recovery_status_for(who uuid)returns jsonb language sql stable security definer set search_path='' as $$
 select jsonb_build_object('codes_configured',f.generation is not null,'codes_acknowledged',f.acknowledged_at is not null,
 'remaining_codes',remaining.total,'generation',f.generation,
 'requires_setup',f.generation is null or f.acknowledged_at is null or remaining.total=0)
 from(select who id)owner left join goserve_private.recovery_accounts f on f.owner_id=owner.id
 cross join lateral(select count(*)total from goserve_private.recovery_codes c where c.owner_id=who and c.generation=f.generation and c.used_at is null and c.revoked_at is null)remaining
$$;
create function goserve_private.recovery_budget(account_key text,ip_key text)returns boolean language plpgsql security definer set search_path='' as $$
declare window_start timestamptz:=to_timestamp(floor(extract(epoch from clock_timestamp())/900)*900);a integer;i integer;
begin
 if coalesce(account_key,'') !~ '^[a-f0-9]{64}$' or coalesce(ip_key,'') !~ '^[a-f0-9]{64}$' then perform goserve_private.fail('VALIDATION_FAILED');end if;
 insert into goserve_private.recovery_rates values('account',account_key,window_start,1)on conflict(scope,key_digest,bucket)do update set attempts=recovery_rates.attempts+1 returning attempts into a;
 insert into goserve_private.recovery_rates values('ip',ip_key,window_start,1)on conflict(scope,key_digest,bucket)do update set attempts=recovery_rates.attempts+1 returning attempts into i;
 return a<=5 and i<=30;
end$$;
create function goserve_private.recovery_context_for(who uuid)returns jsonb language plpgsql security definer set search_path='' as $$
declare f goserve_private.recovery_accounts;
begin
 select * into f from goserve_private.recovery_accounts where owner_id=who;
 if not found or f.generation is null or exists(select 1 from goserve_private.recovery_jobs where owner_id=who and state='pending')then return '{"blocked":false}'::jsonb;end if;
 return jsonb_build_object('blocked',false,'owner_id',who,'version',f.version);
end$$;
create function goserve_private.recovery_consume(who uuid,args jsonb)returns void language plpgsql security definer set search_path='' as $$
declare f goserve_private.recovery_accounts;used text;
begin
 select * into f from goserve_private.recovery_accounts where owner_id=who for update;
 if not found or f.generation is null or f.version is distinct from(args->>'version')::bigint
  or exists(select 1 from goserve_private.recovery_jobs where owner_id=who and state='pending')then perform goserve_private.fail('RECOVERY_PROOF_INVALID');end if;
 update goserve_private.recovery_codes set used_at=clock_timestamp()where owner_id=who and generation=f.generation and code_digest=args->>'code_digest' and used_at is null and revoked_at is null returning code_digest into used;
 if used is null then perform goserve_private.fail('RECOVERY_PROOF_INVALID');end if;
end$$;
create function goserve_private.recovery_new_codes(who uuid,values_json jsonb)returns jsonb language plpgsql security definer set search_path='' as $$
declare next_set uuid:=gen_random_uuid();
begin
 if jsonb_typeof(values_json) is distinct from 'array' or jsonb_array_length(values_json)<>10
  or exists(select 1 from jsonb_array_elements_text(values_json)v where v !~ '^[a-f0-9]{64}$')then perform goserve_private.fail('VALIDATION_FAILED');end if;
 update goserve_private.recovery_codes set revoked_at=clock_timestamp()where owner_id=who and revoked_at is null and used_at is null;
 insert into goserve_private.recovery_codes(code_digest,owner_id,generation)select v,who,next_set from jsonb_array_elements_text(values_json)v;
 update goserve_private.recovery_accounts set generation=next_set,acknowledged_at=null,version=version+1 where owner_id=who;
 insert into goserve_private.recovery_audit(owner_id,event)values(who,'codes_generated');
 return jsonb_build_object('generation',next_set,'remaining_codes',10,'codes_configured',true,'codes_acknowledged',false);
end$$;
create function goserve_private.recovery_account_ready(who uuid)returns void language plpgsql security definer set search_path='' as $$
begin
 if not exists(select 1 from public.profiles where id=who and profile_completed_at is not null)then perform goserve_private.fail('PROFILE_INCOMPLETE');end if;
 if not exists(select 1 from goserve_private.terms_acceptances a join public.terms_documents t on t.version=a.terms_version and t.active where a.profile_id=who)then perform goserve_private.fail('TERMS_REQUIRED');end if;
end$$;
create function goserve_private.recovery_operation(command text,args jsonb)returns jsonb language plpgsql security definer set search_path='' as $$
declare who uuid;f goserve_private.recovery_accounts;g goserve_private.recovery_grants;j goserve_private.recovery_jobs;
expires timestamptz;
begin
 if jsonb_typeof(args) is distinct from 'object' then perform goserve_private.fail('VALIDATION_FAILED');end if;
 who:=nullif(args->>'owner_id','')::uuid;
 if command='session' then
  if not exists(select 1 from auth.sessions where id=(args->>'session_id')::uuid and user_id=who)then perform goserve_private.fail('AUTH_INVALID');end if;return '{}'::jsonb;
 elsif command='context' then
  if not goserve_private.recovery_budget(args->>'account_digest',args->>'ip_digest')then return '{"blocked":true}'::jsonb;end if;
  if not(select enforce_sessions from goserve_private.recovery_settings where id)then return '{"blocked":false,"configuration_required":true}'::jsonb;end if;
  select u.id into who from auth.users u join public.profiles p on p.id=u.id where lower(u.email)=lower(args->>'email') and u.email_confirmed_at is not null and not u.is_anonymous;
  return goserve_private.recovery_context_for(who);
 elsif command='status' then return goserve_private.recovery_status_for(who);
 elsif command='generate'then
  if not exists(select 1 from public.profiles where id=who)then perform goserve_private.fail('AUTH_INVALID');end if;
  perform goserve_private.recovery_account_ready(who);
  insert into goserve_private.recovery_accounts(owner_id)values(who)on conflict do nothing;
  select * into f from goserve_private.recovery_accounts where owner_id=who for update;
  if exists(select 1 from goserve_private.recovery_jobs where owner_id=who and state='pending')then perform goserve_private.fail('RECOVERY_OPERATION_PENDING');end if;
  return goserve_private.recovery_new_codes(who,args->'digests');
 elsif command='ack' then
  perform goserve_private.recovery_account_ready(who);
  select * into f from goserve_private.recovery_accounts where owner_id=who for update;
  if not found or f.generation is null or f.generation is distinct from(args->>'generation')::uuid then perform goserve_private.fail('RECOVERY_GRANT_INVALID');end if;
  update goserve_private.recovery_accounts set acknowledged_at=coalesce(acknowledged_at,clock_timestamp())where owner_id=who;
  return goserve_private.recovery_status_for(who);
 elsif command='redeem' then
  perform goserve_private.recovery_consume(who,args);
  select * into f from goserve_private.recovery_accounts where owner_id=who;
  expires:=clock_timestamp()+interval '5 minutes';
  insert into goserve_private.recovery_grants(grant_digest,owner_id,version,challenge,expires_at)values(args->>'grant_digest',who,f.version,args->>'challenge',expires);
  insert into goserve_private.recovery_audit(owner_id,event)values(who,'proof_redeemed');return jsonb_build_object('expires_at',expires);
 elsif command in('claim','result')then
  select * into g from goserve_private.recovery_grants where grant_digest=args->>'grant_digest' for update;
  if not found or g.challenge is distinct from args->>'challenge' then perform goserve_private.fail('RECOVERY_GRANT_INVALID');end if;
  if g.claimed_at is not null then
   if g.operation_id is distinct from(args->>'operation_id')::uuid or g.claimed_at<clock_timestamp()-interval '24 hours' then perform goserve_private.fail('RECOVERY_GRANT_INVALID');end if;
   select * into j from goserve_private.recovery_jobs where operation_id=g.operation_id;
   return jsonb_build_object('state',j.state,'operation_id',j.operation_id,'owner_id',j.owner_id,'should_mutate',false);
  end if;
  if command='result' or g.expires_at<=clock_timestamp() then perform goserve_private.fail('RECOVERY_GRANT_INVALID');end if;
  if not(select enforce_sessions from goserve_private.recovery_settings where id)then perform goserve_private.fail('CONFIGURATION_REQUIRED');end if;
  select * into f from goserve_private.recovery_accounts where owner_id=g.owner_id for update;
  if g.version is distinct from f.version then perform goserve_private.fail('RECOVERY_GRANT_INVALID');end if;
  if exists(select 1 from goserve_private.recovery_jobs where owner_id=g.owner_id and state='pending')then perform goserve_private.fail('RECOVERY_OPERATION_PENDING');end if;
  insert into goserve_private.recovery_jobs(operation_id,owner_id,grant_digest)values((args->>'operation_id')::uuid,g.owner_id,g.grant_digest)returning * into j;
  update goserve_private.recovery_grants set claimed_at=clock_timestamp(),operation_id=j.operation_id where grant_digest=g.grant_digest;
  update goserve_private.recovery_accounts set version=version+1 where owner_id=g.owner_id;
  insert into goserve_private.recovery_audit(owner_id,operation_id,event)values(g.owner_id,j.operation_id,'reset_claimed');
  return jsonb_build_object('state','pending','operation_id',j.operation_id,'owner_id',j.owner_id,'should_mutate',true);
 elsif command='finish'then
  if args->>'state' not in('completed','failed')then perform goserve_private.fail('VALIDATION_FAILED');end if;
  select * into j from goserve_private.recovery_jobs where operation_id=(args->>'operation_id')::uuid for update;
  if not found then perform goserve_private.fail('RECOVERY_GRANT_INVALID');end if;
  if j.state='pending' then
   update goserve_private.recovery_jobs set state=args->>'state',finished_at=clock_timestamp()where operation_id=j.operation_id;
   insert into goserve_private.recovery_audit(owner_id,operation_id,event)values(j.owner_id,j.operation_id,'reset_'||(args->>'state'));
  elsif j.state<>args->>'state'then perform goserve_private.fail('RECOVERY_GRANT_INVALID');end if;
  return jsonb_build_object('state',args->>'state','operation_id',j.operation_id);
 end if;
 perform goserve_private.fail('VALIDATION_FAILED');return null;
end$$;
create function public.recovery_server_operation(p_command text,p_args jsonb)returns jsonb language sql security invoker set search_path='' as $$select goserve_private.recovery_operation(p_command,p_args)$$;
revoke all on function public.recovery_server_operation(text,jsonb)from public,anon,authenticated;
revoke all on function goserve_private.recovery_status_for(uuid),goserve_private.recovery_budget(text,text),goserve_private.recovery_context_for(uuid),
 goserve_private.recovery_consume(uuid,jsonb),goserve_private.recovery_new_codes(uuid,jsonb),goserve_private.recovery_operation(text,jsonb)from public,anon,authenticated;
revoke all on function goserve_private.recovery_account_ready(uuid)from public,anon,authenticated;
grant usage on schema goserve_private to service_role;
grant execute on function goserve_private.recovery_operation(text,jsonb),public.recovery_server_operation(text,jsonb)to service_role;

create or replace function public.account_view()returns jsonb language plpgsql security definer set search_path='' as $$
declare p public.profiles;t jsonb;current_terms text;r jsonb;enforced boolean;
begin
 p:=goserve_private.actor(false);
 select version into current_terms from public.terms_documents where active;
 select jsonb_build_object('accepted_version',terms_version,'accepted_at',accepted_at)into t from goserve_private.terms_acceptances where profile_id=p.id order by accepted_at desc limit 1;
 r:=goserve_private.recovery_status_for(p.id);select enforce_readiness into enforced from goserve_private.recovery_settings where id;
 return to_jsonb(p)||jsonb_build_object('profile_complete',p.profile_completed_at is not null,'terms',coalesce(t,'{}')||jsonb_build_object('current_version',current_terms),'recovery',r,
 'next_action',case when t is null or t->>'accepted_version' is distinct from current_terms then 'accept_terms' when p.profile_completed_at is null then 'complete_profile' when enforced and(r->>'requires_setup')::boolean then 'setup_recovery' else 'home'end);
end$$;

create or replace function goserve_private.session_allowed()
returns boolean language plpgsql stable security definer set search_path = '' as $function$
declare
  claims jsonb := auth.jwt();
  methods jsonb;
begin
  if auth.uid() is null or claims->'is_anonymous' is distinct from 'false'::jsonb
    or jsonb_typeof(claims->'amr') is distinct from 'array' then
    return false;
  end if;
  if (select enforce_sessions from goserve_private.recovery_settings where id) then
    if coalesce(claims->>'session_id','') !~ '^[0-9a-fA-F-]{36}$' then return false; end if;
    if not exists(select 1 from auth.sessions where id=(claims->>'session_id')::uuid and user_id=auth.uid())then return false;end if;
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
  if ready and not exists (select 1 from goserve_private.terms_acceptances a join public.terms_documents t on t.version=a.terms_version and t.active where a.profile_id = p.id) then
    perform goserve_private.fail('TERMS_REQUIRED');
  end if;
  if ready and (select enforce_readiness from goserve_private.recovery_settings where id) and (goserve_private.recovery_status_for(p.id)->>'requires_setup')::boolean then
    perform goserve_private.fail('RECOVERY_SETUP_REQUIRED');
  end if;
  return p;
end;
$$;




-- Recovery registration policy
do $policy$
begin
 if to_regprocedure('goserve_auth.before_user_created(jsonb)')is not null then
  execute $replace$create or replace function goserve_auth.before_user_created(event jsonb)returns jsonb language plpgsql security invoker set search_path='' as $hook$
begin
 if jsonb_typeof(event->'user')='object' and event #>> '{user,app_metadata,provider}'='google'
  and goserve_auth.tip_email_allowed(event #>> '{user,email}')then return '{}'::jsonb;end if;
 return '{"error":{"http_code":403,"message":"Use a verified @tip.edu.ph Google account."}}'::jsonb;
end$hook$;$replace$;
 end if;
end$policy$;

commit;
