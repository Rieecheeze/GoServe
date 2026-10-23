begin;

create function goserve_private.egovpay_enabled() returns boolean
language plpgsql stable security definer set search_path='' as $$
declare configuration jsonb;
begin
  if to_regclass('goserve_private.egovpay_configuration') is null
    or to_regprocedure('public.get_egovpay_server_configuration()') is null then return false;end if;
  execute 'select public.get_egovpay_server_configuration()' into configuration;
  return coalesce(configuration->'demo_only'='true'::jsonb and configuration->'integration_enabled'='true'::jsonb
    and configuration->>'base_url'='https://platforms-api.e.gov.ph/egovpay'
    and jsonb_typeof(configuration->'token')='string' and length(configuration->>'token')>0
    and configuration->>'token'<>'test_'
    and (configuration->>'token')!~'[[:space:][:cntrl:]]'
    and (configuration->>'settlement_template_uuid')~*'^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$',false);
exception when others then return false;
end $$;

create function public.workflow_options_v2(p_quote_id uuid default null,p_request_id uuid default null)
returns jsonb language plpgsql stable security definer set search_path='' as $$
declare result jsonb; available boolean;
begin
  result:=public.workflow_options(p_quote_id,p_request_id);
  if p_quote_id is not null then
    select s.is_demo and (select is_demo from public.profiles where id=auth.uid())
      and s.fee_state='required' and s.currency='PHP' and goserve_private.egovpay_enabled()
      and exists(select 1 from goserve_private.terms_acceptances ta join public.terms_documents td on td.version=ta.terms_version and td.active where ta.profile_id=auth.uid())
      into available from public.request_quotes q join public.services s on s.id=q.service_id
      where q.id=p_quote_id;
    result:=result||jsonb_build_object('egovpay',coalesce(available,false));
  end if;
  return result;
end $$;

create or replace function public.submit_request(p_quote_id uuid,p_evidence_file_ids uuid[],p_notes text,p_payment_method text,p_idempotency_key uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
declare a public.profiles;q public.request_quotes;s public.services;r public.requests;cached jsonb;
begin
  a:=goserve_private.actor();if a.role<>'student' then perform goserve_private.fail('FORBIDDEN');end if;
  cached:=goserve_private.begin_operation('submit',p_idempotency_key,jsonb_build_array(p_quote_id,p_evidence_file_ids,p_notes,p_payment_method));if cached is not null then return cached;end if;
  select * into q from public.request_quotes where id=p_quote_id and owner_id=a.id for update;if not found then perform goserve_private.fail('NOT_FOUND');end if;
  select * into s from public.services where id=q.service_id for share;
  if not s.active or s.version<>q.service_version or not q.delivery_mode=any(s.delivery_modes) then perform goserve_private.fail('QUOTE_STALE');end if;
  if s.fee_state='pending' then perform goserve_private.fail('CONFIGURATION_REQUIRED');end if;
  if s.fee_state='free' and p_payment_method is not null then perform goserve_private.fail('VALIDATION_FAILED');end if;
  if s.fee_state='required' then
    if p_payment_method='egovpay' then
      if not a.is_demo or not s.is_demo or s.currency<>'PHP' or not goserve_private.egovpay_enabled() then perform goserve_private.fail('PROVIDER_UNAVAILABLE');end if;
      if not exists(select 1 from goserve_private.terms_acceptances ta join public.terms_documents td on td.version=ta.terms_version and td.active where ta.profile_id=a.id) then perform goserve_private.fail('TERMS_REQUIRED');end if;
    elsif p_payment_method='cash' then
      if not exists(select 1 from goserve_private.policies where name='cash_offices' and value ? s.office_id) then perform goserve_private.fail('CONFIGURATION_REQUIRED');end if;
    else perform goserve_private.fail('VALIDATION_FAILED');end if;
  end if;
  insert into public.requests(owner_id,office_id,service_id,snapshot,delivery_mode,notes,status) values(a.id,s.office_id,s.id,to_jsonb(s)||jsonb_build_object('amount',s.amount::text),q.delivery_mode,coalesce(p_notes,''),'Submitted') returning * into r;
  perform goserve_private.bind_evidence(r.id,p_evidence_file_ids);
  perform goserve_private.request_event(r.id,null,'Submitted',null);
  if s.fee_state='required' then
    insert into public.payments(request_id,method,amount,currency) values(r.id,p_payment_method,s.amount,s.currency);
    update public.requests set status='Awaiting Payment',version=2 where id=r.id returning * into r;
    perform goserve_private.request_event(r.id,'Submitted',r.status,null);
  end if;
  return goserve_private.end_operation('submit',p_idempotency_key,to_jsonb(r));
end $$;

create table goserve_private.egovpay_checkouts(
  payment_id uuid primary key references public.payments(id),
  owner_id uuid not null references public.profiles(id),
  request_id uuid not null unique references public.requests(id),
  idempotency_key uuid not null,
  txnid text not null unique default ('GS-'||gen_random_uuid()::text),
  amount numeric not null check(amount>0),currency text not null check(currency='PHP'),
  callback_token uuid not null unique default gen_random_uuid(),
  provider_uuid uuid unique,checkout_url text,
  state text not null default 'creating' check(state in('creating','ready','unknown')),
  observed_status text not null default 'pending' check(observed_status in('pending','paid','unknown')),
  raw_status text,created_at timestamptz not null default now(),checked_at timestamptz,
  unique(owner_id,idempotency_key),
  check((provider_uuid is null and checkout_url is null) or (provider_uuid is not null and checkout_url='https://egovpay-pgi-dev.oueg.info/'||provider_uuid::text))
);
alter table goserve_private.egovpay_checkouts enable row level security;
revoke all on goserve_private.egovpay_checkouts from public,anon,authenticated,service_role;

create table goserve_private.egovpay_checkout_keys(
  owner_id uuid not null references public.profiles(id),key uuid not null,
  payment_id uuid not null references public.payments(id),primary key(owner_id,key)
);
alter table goserve_private.egovpay_checkout_keys enable row level security;
revoke all on goserve_private.egovpay_checkout_keys from public,anon,authenticated,service_role;

create function public.egovpay_authorize(p_payment_id uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
declare a public.profiles;p public.payments;
begin
  a:=goserve_private.actor();
  select pmt.* into p from public.payments pmt join public.requests r on r.id=pmt.request_id
    where pmt.id=p_payment_id and r.owner_id=a.id and pmt.method='egovpay';
  if not found then perform goserve_private.fail('NOT_FOUND');end if;
  return jsonb_build_object('payment_id',p.id);
end $$;

create function public.egovpay_server_operation(p_command text,p_payment_id uuid,p_actor uuid,
  p_key uuid default null,p_token uuid default null,p_details jsonb default null)
returns jsonb language plpgsql security definer set search_path='' as $$
declare c goserve_private.egovpay_checkouts;p public.payments;r public.requests;fresh boolean:=false;status text;provider_id uuid;callback_authorized boolean:=false;key_payment uuid;
begin
  if p_command='callback' or (p_command='observe' and p_actor is null) then
    select * into c from goserve_private.egovpay_checkouts where callback_token=p_token;
    if not found then perform goserve_private.fail('NOT_FOUND');end if;
    if p_payment_id is not null and p_payment_id<>c.payment_id then perform goserve_private.fail('NOT_FOUND');end if;
    p_payment_id:=c.payment_id;
    callback_authorized:=true;
  elsif p_actor is null then perform goserve_private.fail('FORBIDDEN');end if;
  select req.* into r from public.requests req join public.payments pmt on pmt.request_id=req.id
    where pmt.id=p_payment_id for update of req;
  if not found or (not callback_authorized and r.owner_id is distinct from p_actor) then perform goserve_private.fail('NOT_FOUND');end if;
  select * into p from public.payments where id=p_payment_id for update;
  if p.method<>'egovpay' then perform goserve_private.fail('NOT_FOUND');end if;
  if not callback_authorized and not exists(select 1 from public.profiles a join auth.users u on u.id=a.id
    where a.id=p_actor and a.role='student' and a.is_demo and a.profile_completed_at is not null
      and u.is_anonymous is false and exists(select 1 from goserve_private.terms_acceptances t where t.profile_id=a.id)) then perform goserve_private.fail('FORBIDDEN');end if;
  if p.amount is distinct from (r.snapshot->>'amount')::numeric or p.currency is distinct from r.snapshot->>'currency'
    or p.currency<>'PHP' or r.snapshot->>'fee_state'<>'required' or r.snapshot->>'is_demo' is distinct from 'true' then perform goserve_private.fail('QUOTE_STALE');end if;
  if p_command='observe' and not goserve_private.egovpay_enabled() then perform goserve_private.fail('PROVIDER_UNAVAILABLE');end if;
  if p_command='claim' then
    if r.terminal_at is not null or r.status in('Cancelled','Rejected','Completed') or p.status<>'pending' then perform goserve_private.fail('INVALID_STATE');end if;
    if not goserve_private.egovpay_enabled() then perform goserve_private.fail('PROVIDER_UNAVAILABLE');end if;
    if not exists(select 1 from goserve_private.terms_acceptances ta join public.terms_documents td on td.version=ta.terms_version and td.active where ta.profile_id=p_actor) then perform goserve_private.fail('TERMS_REQUIRED');end if;
    if p_key is null then perform goserve_private.fail('VALIDATION_FAILED');end if;
    insert into goserve_private.egovpay_checkout_keys(owner_id,key,payment_id) values(p_actor,p_key,p.id) on conflict do nothing;
    select payment_id into key_payment from goserve_private.egovpay_checkout_keys where owner_id=p_actor and key=p_key;
    if key_payment is distinct from p.id then perform goserve_private.fail('IDEMPOTENCY_CONFLICT');end if;
    begin
      insert into goserve_private.egovpay_checkouts(payment_id,owner_id,request_id,idempotency_key,amount,currency)
        values(p.id,p_actor,r.id,p_key,p.amount,p.currency) on conflict(payment_id) do nothing returning * into c;
      fresh:=found;
    exception when unique_violation then perform goserve_private.fail('IDEMPOTENCY_CONFLICT');end;
  end if;
  select * into c from goserve_private.egovpay_checkouts where payment_id=p.id for update;
  if not found then
    if p_command='lookup' then return jsonb_build_object('status',case when r.terminal_at is not null then 'cancelled' else 'unknown' end);end if;
    perform goserve_private.fail('NOT_FOUND');
  end if;
  if c.idempotency_key is distinct from p_key and (p_command in('attach','ambiguous')
    or (p_command='claim' and c.state<>'ready')) then perform goserve_private.fail('IDEMPOTENCY_CONFLICT');end if;
  if p_command='attach' then
    if jsonb_typeof(p_details) is distinct from 'object' or p_details-ARRAY['uuid','url']<>'{}'::jsonb
      or jsonb_typeof(p_details->'uuid') is distinct from 'string' or jsonb_typeof(p_details->'url') is distinct from 'string' then perform goserve_private.fail('VALIDATION_FAILED');end if;
    begin provider_id:=(p_details->>'uuid')::uuid;exception when invalid_text_representation then perform goserve_private.fail('VALIDATION_FAILED');end;
    if provider_id is null or p_details->>'url' is distinct from 'https://egovpay-pgi-dev.oueg.info/'||provider_id::text
      or (c.provider_uuid is not null and c.provider_uuid<>provider_id) then perform goserve_private.fail('VALIDATION_FAILED');end if;
    update goserve_private.egovpay_checkouts set provider_uuid=provider_id,checkout_url=p_details->>'url',state='ready' where payment_id=p.id returning * into c;
  elsif p_command='ambiguous' then
    update goserve_private.egovpay_checkouts set state=case when provider_uuid is null then 'unknown' else state end where payment_id=p.id returning * into c;
  elsif p_command='observe' then
    if jsonb_typeof(p_details) is distinct from 'object' or c.provider_uuid is null
      or p_details->>'uuid' is distinct from c.provider_uuid::text or p_details->>'txnid' is distinct from c.txnid
      or p_details->>'environment_type' is distinct from 'TEST' or p_details->>'currency' is distinct from c.currency
      or jsonb_typeof(p_details->'amount') is distinct from 'string' or length(p_details->>'amount')>80
      or (p_details->>'amount')!~'^[0-9]+([.][0-9]+)?$'
      or jsonb_typeof(p_details->'payment_status') is distinct from 'string'
      or length(p_details->>'payment_status') not between 1 and 80 then perform goserve_private.fail('VALIDATION_FAILED');end if;
    if (p_details->>'amount')::numeric<>c.amount or c.amount<>p.amount or c.request_id<>r.id or c.owner_id<>r.owner_id then perform goserve_private.fail('VALIDATION_FAILED');end if;
    status:=case when p_details->>'payment_status'='INITIAL' then 'pending'
      when p_details->>'payment_status'='PAID' and jsonb_typeof(p_details->'paid_at')='string'
        and length(btrim(p_details->>'paid_at')) between 1 and 128 then 'paid' else 'unknown' end;
    if status in('pending','paid') then
      insert into goserve_private.provider_payment_events(provider_transaction_id,event_id,payment_id,verified_status,amount,currency)
        values(c.provider_uuid::text,'status:'||(p_details->>'payment_status'),p.id,status,c.amount,c.currency) on conflict do nothing;
      if not exists(select 1 from goserve_private.provider_payment_events ev
        where ev.provider_transaction_id=c.provider_uuid::text and ev.event_id='status:'||(p_details->>'payment_status')
          and ev.payment_id=p.id and ev.verified_status=status and ev.amount=c.amount and ev.currency=c.currency) then perform goserve_private.fail('VALIDATION_FAILED');end if;
      if status='paid' and p.status<>'paid' then
        update public.payments set status='paid',version=version+1,confirmed_at=now(),confirmed_by=null where id=p.id returning * into p;
      end if;
    end if;
    update goserve_private.egovpay_checkouts set observed_status=status,raw_status=p_details->>'payment_status',checked_at=now() where payment_id=p.id returning * into c;
  elsif p_command not in('claim','lookup','callback') then perform goserve_private.fail('VALIDATION_FAILED');end if;
  status:=case when p.status in('paid','failed','cancelled') then p.status else c.observed_status end;
  return jsonb_build_object('payment_id',p.id,'request_id',r.id,'owner_id',r.owner_id,'amount',c.amount::text,
    'currency',c.currency,'txnid',c.txnid,'provider_uuid',c.provider_uuid,'checkout_url',c.checkout_url,
    'callback_token',c.callback_token,'state',c.state,'may_create',fresh,'status',status,
    'request_terminal',r.terminal_at is not null or r.status in('Cancelled','Rejected','Completed'));
end $$;

revoke all on function goserve_private.egovpay_enabled() from public,anon,authenticated;
revoke all on function public.workflow_options_v2(uuid,uuid),public.egovpay_authorize(uuid) from public,anon;
grant execute on function public.workflow_options_v2(uuid,uuid),public.egovpay_authorize(uuid) to authenticated;
revoke all on function public.egovpay_server_operation(text,uuid,uuid,uuid,uuid,jsonb) from public,anon,authenticated;
grant execute on function public.egovpay_server_operation(text,uuid,uuid,uuid,uuid,jsonb) to service_role;
commit;
