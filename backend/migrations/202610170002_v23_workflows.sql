begin;
create table public.services(id uuid primary key default gen_random_uuid(),office_id text not null references public.offices(id),name text not null check(btrim(name)<>''),delivery_modes text[] not null check(cardinality(delivery_modes)>0 and delivery_modes<@ARRAY['physical','digital']),requirements jsonb not null default '[]' check(jsonb_typeof(requirements)='array'),fee_state text not null check(fee_state in('pending','free','required')),amount numeric,currency text,active boolean not null default true,version bigint not null default 1,is_demo boolean not null default true,check((fee_state='pending' and amount is null and currency is null) or (fee_state='free' and amount is not null and amount=0 and currency is null) or (fee_state='required' and amount is not null and amount>0 and amount not in('NaN'::numeric,'Infinity'::numeric,'-Infinity'::numeric) and nullif(btrim(currency),'') is not null)));
create table public.announcements(id uuid primary key default gen_random_uuid(),office_id text not null references public.offices(id),body text not null check(btrim(body)<>''),active boolean not null default true,version bigint not null default 1,actor_id uuid not null references public.profiles(id),created_at timestamptz not null default now());
create table public.request_quotes(id uuid primary key default gen_random_uuid(),owner_id uuid not null references public.profiles(id),service_id uuid not null references public.services(id),service_version bigint not null,delivery_mode text not null,created_at timestamptz not null default now());
create table public.requests(id uuid primary key default gen_random_uuid(),reference text not null unique default gen_random_uuid()::text,owner_id uuid not null references public.profiles(id),office_id text not null references public.offices(id),service_id uuid not null references public.services(id),snapshot jsonb not null,delivery_mode text not null check(delivery_mode in('physical','digital')),notes text not null default '',status text not null check(status in('Submitted','Awaiting Payment','Processing','Ready for Pickup/Download','Completed','Cancelled','Rejected')),version bigint not null default 1,created_at timestamptz not null default now(),terminal_at timestamptz);
create index requests_owner on public.requests(owner_id,created_at);
create index requests_office on public.requests(office_id,status);
create table public.request_events(id uuid primary key default gen_random_uuid(),request_id uuid not null references public.requests(id),actor_id uuid not null references public.profiles(id),from_status text,to_status text not null,reason text,sequence bigint not null,created_at timestamptz not null default now(),unique(request_id,sequence));
create table public.payments(id uuid primary key default gen_random_uuid(),request_id uuid not null unique references public.requests(id),method text not null check(method in('cash','egovpay')),status text not null default 'pending' check(status in('pending','paid','failed','expired','cancelled')),amount numeric not null check(amount>0 and amount not in('NaN'::numeric,'Infinity'::numeric,'-Infinity'::numeric)),currency text not null,version bigint not null default 1,confirmed_by uuid references public.profiles(id),confirmed_at timestamptz);
create table goserve_private.financial_audit(id uuid primary key default gen_random_uuid(),payment_id uuid not null references public.payments(id),action text not null,actor_id uuid not null,created_at timestamptz not null default now(),unique(payment_id,action));
create table goserve_private.catalog_audit(id uuid primary key default gen_random_uuid(),service_id uuid not null,version bigint not null,actor_id uuid not null,snapshot jsonb not null,created_at timestamptz not null default now(),unique(service_id,version));
create table public.notifications(id uuid primary key default gen_random_uuid(),recipient_id uuid not null references public.profiles(id),target_type text not null,target_id uuid not null,event_id uuid not null,created_at timestamptz not null default now(),read_at timestamptz,unique(recipient_id,event_id));
create index notifications_recipient on public.notifications(recipient_id,created_at);
create function goserve_private.request_access(rid uuid) returns boolean language sql stable security definer set search_path='' as $$ select exists(select 1 from public.requests r where r.id=rid and (r.owner_id=auth.uid() or goserve_private.capable(r.office_id,'requests'))) $$;
create function goserve_private.request_row(rid uuid) returns public.requests language plpgsql security definer set search_path='' as $$declare r public.requests;begin perform goserve_private.actor();select * into r from public.requests where id=rid for update;if not found or not goserve_private.request_access(rid) then perform goserve_private.fail('NOT_FOUND');end if;return r;end$$;
create function goserve_private.paid(rid uuid) returns boolean language sql stable security definer set search_path='' as $$select exists(select 1 from public.requests r where r.id=rid and (r.snapshot->>'fee_state'='free' or exists(select 1 from public.payments p where p.request_id=r.id and p.status='paid')))$$;
create function goserve_private.request_event(rid uuid,old_status text,new_status text,why text) returns void language plpgsql security definer set search_path='' as $$
declare ev uuid;r public.requests;
begin
 select * into r from public.requests where id=rid;
 insert into public.request_events(request_id,actor_id,from_status,to_status,reason,sequence) values(rid,auth.uid(),old_status,new_status,why,(select coalesce(max(sequence),0)+1 from public.request_events where request_id=rid)) returning id into ev;
 insert into public.notifications(recipient_id,target_type,target_id,event_id) select p.id,'request',rid,ev from public.profiles p where p.id=r.owner_id or (p.role='staff' and p.office_id=r.office_id and (p.staff_capability is null or exists(select 1 from goserve_private.capabilities c where c.profile_id=p.id and c.office_id=r.office_id and c.capability='requests')));
end$$;
create function public.save_service(p_id uuid,p_expected_version bigint,p_value jsonb,p_idempotency_key uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare a public.profiles;s public.services;r jsonb;
begin
 a:=goserve_private.actor();if not goserve_private.capable(a.office_id,'catalog') then perform goserve_private.fail('FORBIDDEN');end if;
 if p_id is not null then select * into s from public.services where id=p_id for update;if not found or s.office_id<>a.office_id then perform goserve_private.fail('NOT_FOUND');end if;end if;
 r:=goserve_private.begin_operation('service',p_idempotency_key,jsonb_build_array(p_id,p_expected_version,p_value));if r is not null then return r;end if;
 if p_value-ARRAY['name','delivery_modes','requirements','fee_state','amount','currency','active']<>'{}'::jsonb or p_value is null then perform goserve_private.fail('VALIDATION_FAILED');end if;
 if p_id is not null and s.version is distinct from p_expected_version then perform goserve_private.fail('STALE_VERSION');end if;
 if p_id is null then
 insert into public.services(office_id,name,delivery_modes,requirements,fee_state,amount,currency,active) values(a.office_id,p_value->>'name',ARRAY(select jsonb_array_elements_text(p_value->'delivery_modes')),p_value->'requirements',p_value->>'fee_state',(p_value->>'amount')::numeric,p_value->>'currency',(p_value->>'active')::boolean) returning * into s;
 else update public.services set name=p_value->>'name',delivery_modes=ARRAY(select jsonb_array_elements_text(p_value->'delivery_modes')),requirements=p_value->'requirements',fee_state=p_value->>'fee_state',amount=(p_value->>'amount')::numeric,currency=p_value->>'currency',active=(p_value->>'active')::boolean,version=version+1 where id=p_id returning * into s;end if;
 insert into goserve_private.catalog_audit(service_id,version,actor_id,snapshot) values(s.id,s.version,a.id,to_jsonb(s));
 return goserve_private.end_operation('service',p_idempotency_key,to_jsonb(s)||jsonb_build_object('amount',s.amount::text));
end$$;
create function public.update_office(p_expected_version bigint,p_status text,p_note text,p_idempotency_key uuid) returns jsonb language plpgsql security definer set search_path='' as $$declare a public.profiles;o public.offices;r jsonb;begin
 a:=goserve_private.actor();if not goserve_private.capable(a.office_id,'catalog') then perform goserve_private.fail('FORBIDDEN');end if;
 select * into o from public.offices where id=a.office_id for update;
 r:=goserve_private.begin_operation('office',p_idempotency_key,jsonb_build_array(p_expected_version,p_status,p_note));if r is not null then return r;end if;
 if o.version is distinct from p_expected_version then perform goserve_private.fail('STALE_VERSION');end if;
 update public.offices set status=p_status,note=coalesce(p_note,''),status_changed_at=now(),status_changed_by=a.id,version=version+1 where id=o.id returning * into o;return goserve_private.end_operation('office',p_idempotency_key,to_jsonb(o));end$$;
create function public.save_announcement(p_id uuid,p_expected_version bigint,p_body text,p_active boolean,p_idempotency_key uuid) returns jsonb language plpgsql security definer set search_path='' as $$declare a public.profiles;n public.announcements;r jsonb;begin
 a:=goserve_private.actor();if not goserve_private.capable(a.office_id,'catalog') then perform goserve_private.fail('FORBIDDEN');end if;
 if p_id is not null then select * into n from public.announcements where id=p_id for update;if not found or n.office_id<>a.office_id then perform goserve_private.fail('NOT_FOUND');end if;end if;
 r:=goserve_private.begin_operation('announcement',p_idempotency_key,jsonb_build_array(p_id,p_expected_version,p_body,p_active));if r is not null then return r;end if;
 if p_id is null then insert into public.announcements(office_id,body,active,actor_id) values(a.office_id,p_body,p_active,a.id) returning * into n;else
 if n.version is distinct from p_expected_version then perform goserve_private.fail('STALE_VERSION');end if;
 update public.announcements set body=p_body,active=p_active,actor_id=a.id,version=version+1 where id=p_id returning * into n;end if;
 return goserve_private.end_operation('announcement',p_idempotency_key,to_jsonb(n));end$$;
create function public.quote_request(p_service_id uuid,p_delivery_mode text) returns jsonb language plpgsql security definer set search_path='' as $$
declare a public.profiles;s public.services;q public.request_quotes;
begin
 a:=goserve_private.actor();if a.role<>'student' then perform goserve_private.fail('FORBIDDEN');end if;
 select * into s from public.services where id=p_service_id and active for share;
 if not found then perform goserve_private.fail('NOT_FOUND');end if;
 if not p_delivery_mode=any(s.delivery_modes) then perform goserve_private.fail('VALIDATION_FAILED');end if;
 insert into public.request_quotes(owner_id,service_id,service_version,delivery_mode) values(a.id,s.id,s.version,p_delivery_mode) returning * into q;
 return to_jsonb(q)||jsonb_build_object('service',to_jsonb(s)||jsonb_build_object('amount',s.amount::text),'submittable',s.fee_state<>'pending');
end$$;
create function public.submit_request(p_quote_id uuid,p_evidence_file_ids uuid[],p_notes text,p_payment_method text,p_idempotency_key uuid) returns jsonb language plpgsql security definer set search_path='' as $$
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
 if p_payment_method='egovpay' then perform goserve_private.fail('PROVIDER_UNAVAILABLE');end if;
 if p_payment_method is distinct from 'cash' then perform goserve_private.fail('VALIDATION_FAILED');end if;
 if not exists(select 1 from goserve_private.policies where name='cash_offices' and value ? s.office_id) then perform goserve_private.fail('CONFIGURATION_REQUIRED');end if;end if;
 insert into public.requests(owner_id,office_id,service_id,snapshot,delivery_mode,notes,status) values(a.id,s.office_id,s.id,to_jsonb(s)||jsonb_build_object('amount',s.amount::text),q.delivery_mode,coalesce(p_notes,''),'Submitted') returning * into r;
 perform goserve_private.bind_evidence(r.id,p_evidence_file_ids);
 perform goserve_private.request_event(r.id,null,'Submitted',null);
 if s.fee_state='required' then
 insert into public.payments(request_id,method,amount,currency) values(r.id,p_payment_method,s.amount,s.currency);
 update public.requests set status='Awaiting Payment',version=2 where id=r.id returning * into r;
 perform goserve_private.request_event(r.id,'Submitted',r.status,null);end if;
 return goserve_private.end_operation('submit',p_idempotency_key,to_jsonb(r));
end$$;
create function public.transition_request(p_request_id uuid,p_expected_version bigint,p_target_status text,p_reason text,p_idempotency_key uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare a public.profiles;r public.requests;cached jsonb;policy jsonb;old_status text;
begin
 a:=goserve_private.actor();r:=goserve_private.request_row(p_request_id);
 cached:=goserve_private.begin_operation('transition',p_idempotency_key,jsonb_build_array(p_request_id,p_expected_version,p_target_status,p_reason));if cached is not null then return cached;end if;
 if p_target_status not in('Processing','Ready for Pickup/Download','Cancelled','Rejected') then perform goserve_private.fail('FORBIDDEN');end if;
 if r.version is distinct from p_expected_version then perform goserve_private.fail('STALE_VERSION');end if;
 if r.terminal_at is not null then perform goserve_private.fail('INVALID_STATE');end if;
 if p_target_status in('Cancelled','Rejected') then
 select value into policy from goserve_private.policies where name='request_'||lower(p_target_status);
 if policy is null then perform goserve_private.fail('CONFIGURATION_REQUIRED');end if;
 if not coalesce(policy->'roles' ? a.role,false) or not coalesce(policy->'states' ? r.status,false) or policy->>'window_seconds' is null or now()>r.created_at+make_interval(secs=>(policy->>'window_seconds')::double precision) then perform goserve_private.fail('POLICY_BLOCKED');end if;
 if p_target_status='Rejected' and not goserve_private.capable(r.office_id,'requests') then perform goserve_private.fail('FORBIDDEN');end if;
 if nullif(btrim(p_reason),'') is null then perform goserve_private.fail('VALIDATION_FAILED');end if;
 else
 if not goserve_private.capable(r.office_id,'requests') then perform goserve_private.fail('FORBIDDEN');end if;
 if not goserve_private.paid(r.id) then perform goserve_private.fail('PAYMENT_REQUIRED');end if;
 if (p_target_status='Processing' and r.status not in('Submitted','Awaiting Payment')) or (p_target_status='Ready for Pickup/Download' and r.status<>'Processing') then perform goserve_private.fail('INVALID_STATE');end if;
 if p_target_status='Ready for Pickup/Download' and r.delivery_mode='digital' and not goserve_private.has_deliverable(r.id) then perform goserve_private.fail('UPLOAD_INVALID');end if;end if;
 old_status:=r.status;
 update public.requests set status=p_target_status,version=version+1,terminal_at=case when p_target_status in('Cancelled','Rejected') then now() else null end where id=r.id returning * into r;
 perform goserve_private.request_event(r.id,old_status,r.status,p_reason);
 return goserve_private.end_operation('transition',p_idempotency_key,to_jsonb(r));
end$$;
create function public.cash_payment(p_reference text,p_confirm boolean,p_expected_version bigint,p_idempotency_key uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare a public.profiles;r public.requests;p public.payments;cached jsonb;result jsonb;
begin
 a:=goserve_private.actor();if a.role<>'teller' or a.office_id is distinct from 'OFF-CASH' then perform goserve_private.fail('FORBIDDEN');end if;
 select * into r from public.requests where reference=p_reference for update;
 if not found or not exists(select 1 from goserve_private.policies where name='cash_offices' and value ? r.office_id) then perform goserve_private.fail('NOT_FOUND');end if;
 select * into p from public.payments where request_id=r.id and method='cash' for update;if not found then perform goserve_private.fail('NOT_FOUND');end if;
 if p_confirm then
 cached:=goserve_private.begin_operation('cash',p_idempotency_key,jsonb_build_array(p_reference,p_expected_version));if cached is not null then return cached;end if;
 if p.status='paid' then perform goserve_private.fail('ALREADY_PAID');end if;
 if p.status<>'pending' or r.terminal_at is not null then perform goserve_private.fail('INVALID_STATE');end if;
 if p.version is distinct from p_expected_version then perform goserve_private.fail('STALE_VERSION');end if;
 if p.amount::text<>r.snapshot->>'amount' or p.currency<>r.snapshot->>'currency' or r.snapshot->>'fee_state'<>'required' then perform goserve_private.fail('QUOTE_STALE');end if;
 update public.payments set status='paid',version=version+1,confirmed_by=a.id,confirmed_at=now() where id=p.id returning * into p;
 insert into goserve_private.financial_audit(payment_id,action,actor_id) values(p.id,'cash_paid',a.id);
 perform goserve_private.request_event(r.id,r.status,r.status,'Payment confirmed');end if;
 result:=jsonb_build_object('payment_id',p.id,'request_id',r.id,'reference',r.reference,'office_id',r.office_id,'service_name',r.snapshot->>'name','amount',p.amount::text,'currency',p.currency,'status',p.status,'version',p.version,'confirmed_at',p.confirmed_at);
 if p_confirm then return goserve_private.end_operation('cash',p_idempotency_key,result);end if;return result;
end$$;
create function public.ack_notifications(p_ids uuid[]) returns integer language plpgsql security definer set search_path='' as $$declare n integer;begin perform goserve_private.actor();update public.notifications set read_at=now() where recipient_id=auth.uid() and id=any(p_ids) and read_at is null;get diagnostics n=row_count;return n;end$$;
create function goserve_private.ready() returns boolean language plpgsql stable security definer set search_path='' as $$begin perform goserve_private.actor();return true;exception when sqlstate 'P0001' then return false;end$$;
do $$declare t text;begin foreach t in array ARRAY['services','announcements','request_quotes','requests','request_events','payments','notifications'] loop execute format('alter table public.%I enable row level security',t);execute format('revoke all on public.%I from public,anon,authenticated',t);execute format('grant select on public.%I to authenticated',t);execute format('grant all on public.%I to service_role',t);end loop;end$$;
create policy services_read on public.services for select to authenticated using(goserve_private.ready() and (active or goserve_private.capable(office_id,'catalog')));
create policy announcements_read on public.announcements for select to authenticated using(goserve_private.ready() and (active or goserve_private.capable(office_id,'catalog')));
create policy quotes_own on public.request_quotes for select to authenticated using(goserve_private.ready() and owner_id=auth.uid());
create policy requests_read on public.requests for select to authenticated using(goserve_private.ready() and goserve_private.request_access(id));
create policy events_read on public.request_events for select to authenticated using(goserve_private.ready() and goserve_private.request_access(request_id));
create policy payments_read on public.payments for select to authenticated using(goserve_private.ready() and goserve_private.request_access(request_id));
create policy notifications_own on public.notifications for select to authenticated using(goserve_private.ready() and recipient_id=auth.uid());
alter table goserve_private.financial_audit enable row level security;alter table goserve_private.catalog_audit enable row level security;
revoke all on goserve_private.financial_audit,goserve_private.catalog_audit from public,anon,authenticated;grant all on goserve_private.financial_audit,goserve_private.catalog_audit to service_role;
revoke all on all functions in schema goserve_private from public,anon,authenticated;
grant usage on schema goserve_private to authenticated;
grant execute on function goserve_private.ready(),goserve_private.capable(text,text),goserve_private.request_access(uuid) to authenticated;
do $$declare f record;begin for f in select p.oid::regprocedure sig from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname in('save_service','update_office','save_announcement','quote_request','submit_request','transition_request','cash_payment','ack_notifications') loop execute format('revoke all on function %s from public,anon',f.sig);execute format('grant execute on function %s to authenticated',f.sig);end loop;end$$;
commit;
