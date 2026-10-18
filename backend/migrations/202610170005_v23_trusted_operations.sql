begin;
create table goserve_private.registration_attempts(id uuid primary key default gen_random_uuid(),email text not null,key uuid not null,fingerprint jsonb not null,state text not null default 'creating' check(state in('creating','committed','compensating','repair_required','deleted')),auth_id uuid,created_at timestamptz not null default now(),unique(email,key));
create table public.ai_consent(profile_id uuid primary key references public.profiles(id),decision text not null check(decision in('allow','decline','revoke')),version bigint not null default 1,updated_at timestamptz not null default now());
create table goserve_private.ai_consent_audit(id uuid primary key default gen_random_uuid(),profile_id uuid not null references public.profiles(id),decision text not null,version bigint not null,notice_version text,created_at timestamptz not null default now(),unique(profile_id,version));
create table goserve_private.ai_operations(id uuid primary key default gen_random_uuid(),owner_id uuid not null references public.profiles(id),key uuid not null,state text not null check(state in('running','resolved','unknown')),cleanup text not null check(cleanup in('not_needed','pending','unknown','failed','verified_deleted')),created_at timestamptz not null default now(),unique(owner_id,key));
create table goserve_private.ai_attachments(id uuid primary key default gen_random_uuid(),owner_id uuid not null references public.profiles(id),operation_id uuid references goserve_private.ai_operations(id),object_path text not null unique,mime_type text not null check(mime_type in('application/pdf','image/jpeg','image/png')),size_bytes bigint not null check(size_bytes>0 and size_bytes<=10485760),active boolean not null default true,cleanup text not null default 'pending' check(cleanup in('pending','unknown','failed','verified_deleted')),created_at timestamptz not null default now(),verified_deleted_at timestamptz,check(cleanup<>'verified_deleted' or verified_deleted_at is not null));
create unique index ai_one_active_attachment on goserve_private.ai_attachments(owner_id) where active;
create table goserve_private.provider_payment_events(provider_transaction_id text not null,event_id text not null,payment_id uuid not null references public.payments(id),verified_status text not null check(verified_status in('pending','paid','failed','expired','cancelled')),amount numeric not null check(amount>0 and amount not in('NaN'::numeric,'Infinity'::numeric,'-Infinity'::numeric)),currency text not null,verified_at timestamptz not null default now(),primary key(provider_transaction_id,event_id));
create function public.seryo_state() returns jsonb language plpgsql security definer set search_path='' as $$declare c public.ai_consent;dirty boolean;begin
 perform goserve_private.actor();select * into c from public.ai_consent where profile_id=auth.uid();
 select exists(select 1 from goserve_private.ai_operations where owner_id=auth.uid() and (state in('running','unknown') or cleanup in('pending','unknown','failed'))) or exists(select 1 from goserve_private.ai_attachments where owner_id=auth.uid() and cleanup<>'verified_deleted') into dirty;
 return jsonb_build_object('consent',coalesce(c.decision,'unset'),'consent_version',coalesce(c.version,0),'provider_ready',false,'can_send',false,'can_attach',false,'cleanup',case when dirty then 'unresolved' else 'clear' end,'blockers',jsonb_build_array('PROVIDER_UNAVAILABLE')||case when dirty then '["CLEANUP_REQUIRED"]'::jsonb else '[]'::jsonb end);
end$$;
create function public.set_ai_consent(p_decision text,p_expected_version bigint,p_idempotency_key uuid) returns jsonb language plpgsql security definer set search_path='' as $$declare a public.profiles;c public.ai_consent;cached jsonb;begin
 a:=goserve_private.actor();perform 1 from public.profiles where id=a.id for update;
 cached:=goserve_private.begin_operation('ai_consent',p_idempotency_key,jsonb_build_array(p_decision,p_expected_version));if cached is not null then return public.seryo_state();end if;
 select * into c from public.ai_consent where profile_id=a.id;
 if coalesce(c.version,0) is distinct from p_expected_version then perform goserve_private.fail('STALE_VERSION');end if;
 if p_decision not in('allow','decline','revoke') or p_decision is null then perform goserve_private.fail('VALIDATION_FAILED');end if;
 insert into public.ai_consent(profile_id,decision) values(a.id,p_decision) on conflict(profile_id) do update set decision=excluded.decision,version=ai_consent.version+1,updated_at=now() returning * into c;
 insert into goserve_private.ai_consent_audit(profile_id,decision,version,notice_version) values(a.id,c.decision,c.version,(select value->>'version' from goserve_private.policies where name='ai_notice'));
 return goserve_private.end_operation('ai_consent',p_idempotency_key,public.seryo_state());end$$;
create function public.registration_operation(p_command text,p_email text,p_key uuid,p_profile jsonb,p_terms_version text,p_attempt_id uuid default null) returns jsonb language plpgsql security definer set search_path='' as $$
declare a goserve_private.registration_attempts;u uuid;t text;fresh boolean:=false;
begin
 if p_command='prepare' then
 perform goserve_private.validate_profile(p_profile);
 select version into t from public.terms_documents where active for share;if t is null then perform goserve_private.fail('CONFIGURATION_REQUIRED');end if;
 if t is distinct from p_terms_version then perform goserve_private.fail('TERMS_REQUIRED');end if;
 insert into goserve_private.registration_attempts(email,key,fingerprint) values(lower(p_email),p_key,jsonb_build_array(p_profile,p_terms_version)) on conflict do nothing returning * into a;fresh:=found;
 select * into a from goserve_private.registration_attempts where email=lower(p_email) and key=p_key for update;
 if a.fingerprint<>jsonb_build_array(p_profile,p_terms_version) then perform goserve_private.fail('IDEMPOTENCY_CONFLICT');end if;
 else select * into a from goserve_private.registration_attempts where id=p_attempt_id for update;if not found then perform goserve_private.fail('NOT_FOUND');end if;end if;
 select id into u from auth.users where raw_app_meta_data->>'goserve_registration_attempt'=a.id::text and lower(email)=a.email;
 if p_command='commit' then
 if a.state='committed' then return jsonb_build_object('account_created',true,'email',a.email,'next_action','sign_in');end if;
 if a.state<>'creating' or u is null then perform goserve_private.fail('TEMPORARILY_UNAVAILABLE');end if;
 select version into t from public.terms_documents where active for share;
 if t is distinct from a.fingerprint->>1 then perform goserve_private.fail('TERMS_REQUIRED');end if;
 insert into public.profiles(id,email,display_name,role,office_id,staff_capability,is_demo) values(u,a.email,'','student',null,null,true);
 perform goserve_private.save_profile(u,a.fingerprint->0);
 insert into goserve_private.terms_acceptances(profile_id,terms_version,actor_id) values(u,t,u);
 update goserve_private.registration_attempts set state='committed',auth_id=u where id=a.id;
 return jsonb_build_object('account_created',true,'email',a.email,'next_action','sign_in');
 elsif p_command='compensate' then
 if a.state='committed' or exists(select 1 from public.profiles where id=u) then perform goserve_private.fail('INVALID_STATE');end if;
 if u is null then update goserve_private.registration_attempts set state='repair_required' where id=a.id;else update goserve_private.registration_attempts set state='compensating',auth_id=u where id=a.id;end if;
 elsif p_command='deleted' then
 if a.state<>'compensating' or exists(select 1 from auth.users where id=a.auth_id) then perform goserve_private.fail('CLEANUP_REQUIRED');end if;
 update goserve_private.registration_attempts set state='deleted' where id=a.id;
 elsif p_command not in('prepare','resolve') then perform goserve_private.fail('VALIDATION_FAILED');end if;
 return jsonb_build_object('attempt_id',a.id,'state',(select state from goserve_private.registration_attempts where id=a.id),'auth_id',u,'may_create',fresh);
end$$;
create function public.file_server_operation(p_actor uuid,p_file_id uuid,p_command text,p_sha256 text default null) returns jsonb language plpgsql security definer set search_path='' as $$
declare f public.request_files;o goserve_private.file_objects;r public.requests;a public.profiles;
begin
 select * into a from public.profiles where id=p_actor;if not found or a.profile_completed_at is null then perform goserve_private.fail('FORBIDDEN');end if;
 select * into f from public.request_files where id=p_file_id for update;if not found then perform goserve_private.fail('NOT_FOUND');end if;
 select * into o from goserve_private.file_objects where file_id=f.id;
 if p_command='read' then
 if f.kind<>'evidence' or f.verification<>'verified' or f.deleted_at is not null then perform goserve_private.fail('UPLOAD_INVALID');end if;
 select * into r from public.requests where id=f.request_id;
 if f.owner_id<>p_actor and (r.id is null or a.role<>'staff' or a.office_id is distinct from r.office_id or (a.staff_capability is not null and not exists(select 1 from goserve_private.capabilities where profile_id=p_actor and office_id=r.office_id and capability='requests'))) then perform goserve_private.fail('FORBIDDEN');end if;
 return to_jsonb(f)||jsonb_build_object('object_path',o.object_path);end if;
 if f.kind='evidence' then if f.owner_id<>p_actor or f.request_id is not null then perform goserve_private.fail('FORBIDDEN');end if;
 else select * into r from public.requests where id=f.request_id for update;
 if a.role<>'staff' or a.office_id is distinct from r.office_id or (a.staff_capability is not null and not exists(select 1 from goserve_private.capabilities where profile_id=p_actor and office_id=r.office_id and capability='requests')) or r.status not in('Processing','Ready for Pickup/Download') then perform goserve_private.fail('FORBIDDEN');end if;end if;
 if f.deleted_at is not null or o.expires_at<=now() then perform goserve_private.fail('UPLOAD_INVALID');end if;
 if p_command='verified' then
 if f.verification='verified' then if o.sha256 is distinct from p_sha256 then perform goserve_private.fail('UPLOAD_INVALID');end if;return to_jsonb(f);end if;
 if p_sha256 is null or p_sha256!~'^[a-f0-9]{64}$' then perform goserve_private.fail('UPLOAD_INVALID');end if;
 if f.kind='deliverable' then update public.request_files set current=false where request_id=f.request_id and kind='deliverable' and id<>f.id;end if;
 update public.request_files set verification='verified' where id=f.id returning * into f;
 update goserve_private.file_objects set sha256=p_sha256,verified_at=now() where file_id=f.id;
 return to_jsonb(f);
 elsif p_command<>'authorize' then perform goserve_private.fail('VALIDATION_FAILED');end if;
 return to_jsonb(f)||jsonb_build_object('object_path',o.object_path,'expires_at',o.expires_at,'policy',(select value from goserve_private.policies where name='file_'||f.service_id::text||'_'||f.kind));
end$$;
create function public.download_server_operation(p_actor uuid,p_download_id uuid,p_complete boolean default false) returns jsonb language plpgsql security definer set search_path='' as $$
declare d goserve_private.downloads;f public.request_files;r public.requests;o goserve_private.file_objects;
begin
 select * into d from goserve_private.downloads where id=p_download_id and owner_id=p_actor for update;if not found then perform goserve_private.fail('NOT_FOUND');end if;
 select * into f from public.request_files where id=d.file_id;select * into r from public.requests where id=f.request_id for update;select * into o from goserve_private.file_objects where file_id=f.id;
 if r.owner_id<>p_actor or r.status<>'Ready for Pickup/Download' or r.delivery_mode<>'digital' or not f.current or f.verification<>'verified' or f.deleted_at is not null or d.expires_at<=now() or not goserve_private.paid(r.id) or not exists(select 1 from public.profiles where id=p_actor and profile_completed_at is not null) then perform goserve_private.fail('INVALID_STATE');end if;
 if p_complete then update goserve_private.downloads set transfer_completed_at=coalesce(transfer_completed_at,now()) where id=d.id;end if;
 return jsonb_build_object('download_id',d.id,'object_path',o.object_path,'sha256',o.sha256,'size_bytes',f.size_bytes);
end$$;
create function public.cleanup_claim(p_lease_seconds integer) returns jsonb language plpgsql security definer set search_path='' as $$declare j goserve_private.cleanup_jobs;begin
 if p_lease_seconds is null or p_lease_seconds<=0 then perform goserve_private.fail('CONFIGURATION_REQUIRED');end if;
 insert into goserve_private.cleanup_jobs(file_id,eligible_at) select f.id,r.terminal_at+interval '30 days' from public.request_files f join public.requests r on r.id=f.request_id where r.terminal_at is not null and f.deleted_at is null on conflict(file_id) do update set eligible_at=excluded.eligible_at where cleanup_jobs.state<>'verified_deleted';
 select * into j from goserve_private.cleanup_jobs where eligible_at<=now() and state<>'verified_deleted' and (lease_until is null or lease_until<=now()) order by eligible_at for update skip locked limit 1;
 if not found then return null;end if;
 if not exists(select 1 from public.request_files f join public.requests r on r.id=f.request_id where f.id=j.file_id and r.terminal_at+interval '30 days'<=now()) then perform goserve_private.fail('POLICY_BLOCKED');end if;
 update goserve_private.cleanup_jobs set state='leased',lease_id=gen_random_uuid(),lease_until=now()+make_interval(secs=>p_lease_seconds),attempts=attempts+1 where id=j.id returning * into j;
 return to_jsonb(j)||jsonb_build_object('object_path',(select object_path from goserve_private.file_objects where file_id=j.file_id));end$$;
create function public.cleanup_finish(p_job_id uuid,p_lease_id uuid,p_absence_verified boolean) returns jsonb language plpgsql security definer set search_path='' as $$declare j goserve_private.cleanup_jobs;begin
 select * into j from goserve_private.cleanup_jobs where id=p_job_id for update;
 if not found or j.lease_id is distinct from p_lease_id or j.lease_until<=now() or j.state<>'leased' then perform goserve_private.fail('STALE_VERSION');end if;
 update goserve_private.cleanup_jobs set state=case when p_absence_verified then 'verified_deleted' else 'failed' end,verified_deleted_at=case when p_absence_verified then now() else null end,lease_until=null where id=j.id returning * into j;
 if p_absence_verified then update public.request_files set deleted_at=now() where id=j.file_id;end if;return to_jsonb(j);end$$;
create function goserve_private.append_only() returns trigger language plpgsql set search_path='' as $$begin perform goserve_private.fail('IMMUTABLE_RECORD');return null;end$$;
create trigger request_events_append before update or delete on public.request_events for each row execute function goserve_private.append_only();
create trigger financial_audit_append before update or delete on goserve_private.financial_audit for each row execute function goserve_private.append_only();
create trigger catalog_audit_append before update or delete on goserve_private.catalog_audit for each row execute function goserve_private.append_only();
create trigger terms_acceptance_append before update or delete on goserve_private.terms_acceptances for each row execute function goserve_private.append_only();
create trigger message_append before update or delete on public.inquiry_messages for each row execute function goserve_private.append_only();
create trigger appointment_event_append before update or delete on public.appointment_events for each row execute function goserve_private.append_only();
create trigger ai_consent_append before update or delete on goserve_private.ai_consent_audit for each row execute function goserve_private.append_only();
create trigger provider_event_append before update or delete on goserve_private.provider_payment_events for each row execute function goserve_private.append_only();
create function goserve_private.freeze_snapshot() returns trigger language plpgsql set search_path='' as $$begin
 if TG_TABLE_NAME='requests' then if (new.snapshot,new.owner_id,new.office_id,new.service_id,new.delivery_mode,new.reference) is distinct from (old.snapshot,old.owner_id,old.office_id,old.service_id,old.delivery_mode,old.reference) then perform goserve_private.fail('IMMUTABLE_RECORD');end if;
 elsif (new.request_id,new.amount,new.currency,new.method) is distinct from (old.request_id,old.amount,old.currency,old.method) then perform goserve_private.fail('IMMUTABLE_RECORD');end if;return new;end$$;
create trigger request_snapshot_immutable before update on public.requests for each row execute function goserve_private.freeze_snapshot();
create trigger payment_snapshot_immutable before update on public.payments for each row execute function goserve_private.freeze_snapshot();
alter table public.ai_consent enable row level security;revoke all on public.ai_consent from public,anon,authenticated;grant select on public.ai_consent to authenticated;grant all on public.ai_consent to service_role;
create policy ai_consent_own on public.ai_consent for select to authenticated using(profile_id=auth.uid() and goserve_private.ready());
drop policy offices_select_members on public.offices;
create policy offices_select_members on public.offices for select to authenticated using(active and goserve_private.ready());
create function goserve_private.cash_access(rid uuid) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.profiles p join public.requests r on r.id=rid where p.id=auth.uid() and p.role='teller' and p.office_id='OFF-CASH' and exists(select 1 from goserve_private.policies where name='cash_offices' and value ? r.office_id))
$$;
create policy payments_teller_read on public.payments for select to authenticated using(goserve_private.ready() and method='cash' and goserve_private.cash_access(request_id));
do $$declare t text;begin foreach t in array ARRAY['registration_attempts','ai_consent_audit','ai_operations','ai_attachments','provider_payment_events'] loop execute format('alter table goserve_private.%I enable row level security',t);execute format('revoke all on goserve_private.%I from public,anon,authenticated',t);execute format('grant all on goserve_private.%I to service_role',t);end loop;end$$;
revoke all on all functions in schema goserve_private from public,anon,authenticated;
grant execute on function goserve_private.ready(),goserve_private.capable(text,text),goserve_private.request_access(uuid),goserve_private.thread_access(uuid),goserve_private.schedule_access(text,uuid),goserve_private.appointment_access(uuid),goserve_private.cash_access(uuid) to authenticated;
revoke all on function public.seryo_state(),public.set_ai_consent(text,bigint,uuid) from public,anon;grant execute on function public.seryo_state(),public.set_ai_consent(text,bigint,uuid) to authenticated;
do $$declare f record;begin for f in select p.oid::regprocedure sig from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname in('registration_operation','file_server_operation','download_server_operation','cleanup_claim','cleanup_finish') loop execute format('revoke all on function %s from public,anon,authenticated',f.sig);execute format('grant execute on function %s to service_role',f.sig);end loop;end$$;
do $$declare t text;begin
 foreach t in array ARRAY['offices','profiles','services','announcements','requests','request_events','payments','request_files','notifications','inquiry_threads','inquiry_messages','inquiry_reads','consultation_slots','appointments','appointment_events','ai_consent'] loop
 if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename=t) then execute format('alter publication supabase_realtime add table public.%I',t);end if;end loop;end$$;
commit;
