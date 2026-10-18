begin;
create table public.inquiry_threads(id uuid primary key default gen_random_uuid(),student_id uuid not null references public.profiles(id),office_id text not null references public.offices(id),assigned_to uuid references public.profiles(id),version bigint not null default 1,last_sequence bigint not null default 0,created_at timestamptz not null default now());
create index threads_student on public.inquiry_threads(student_id);
create index threads_office on public.inquiry_threads(office_id,assigned_to);
create table public.inquiry_messages(id uuid primary key default gen_random_uuid(),thread_id uuid not null references public.inquiry_threads(id),sequence bigint not null,sender_id uuid not null references public.profiles(id),body text not null check(btrim(body)<>''),created_at timestamptz not null default now(),unique(thread_id,sequence));
create table public.inquiry_reads(thread_id uuid not null references public.inquiry_threads(id),profile_id uuid not null references public.profiles(id),through_sequence bigint not null default 0,read_at timestamptz not null default now(),primary key(thread_id,profile_id));
create function goserve_private.thread_access(tid uuid) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.inquiry_threads t where t.id=tid and (t.student_id=auth.uid() or goserve_private.capable(t.office_id,'inquiries') or (t.assigned_to=auth.uid() and exists(select 1 from public.profiles p where p.id=auth.uid() and p.role='staff' and p.office_id=t.office_id))))
$$;
create function public.send_inquiry(p_thread_id uuid,p_office_id text,p_body text,p_idempotency_key uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare a public.profiles;t public.inquiry_threads;m public.inquiry_messages;cached jsonb;
begin
 a:=goserve_private.actor();
 if p_thread_id is not null and not goserve_private.thread_access(p_thread_id) then perform goserve_private.fail('NOT_FOUND');end if;
 cached:=goserve_private.begin_operation('inquiry',p_idempotency_key,jsonb_build_array(p_thread_id,p_office_id,p_body));if cached is not null then return cached;end if;
 if nullif(btrim(p_body),'') is null then perform goserve_private.fail('VALIDATION_FAILED');end if;
 if p_thread_id is null then
 if a.role<>'student' then perform goserve_private.fail('FORBIDDEN');end if;
 if not exists(select 1 from public.offices where id=p_office_id and active) then perform goserve_private.fail('NOT_FOUND');end if;
 insert into public.inquiry_threads(student_id,office_id) values(a.id,p_office_id) returning * into t;
 else select * into t from public.inquiry_threads where id=p_thread_id for update;
 if p_office_id is not null and p_office_id<>t.office_id then perform goserve_private.fail('VALIDATION_FAILED');end if;end if;
 update public.inquiry_threads set last_sequence=last_sequence+1,version=version+1 where id=t.id returning * into t;
 insert into public.inquiry_messages(thread_id,sequence,sender_id,body) values(t.id,t.last_sequence,a.id,p_body) returning * into m;
 insert into public.notifications(recipient_id,target_type,target_id,event_id) select p.id,'inquiry',t.id,m.id from public.profiles p where p.id<>a.id and (p.id=t.student_id or (p.role='staff' and p.office_id=t.office_id and (p.id=t.assigned_to or p.staff_capability is null or p.staff_capability in('guidance','secretary') or exists(select 1 from goserve_private.capabilities c where c.profile_id=p.id and c.office_id=t.office_id and c.capability='inquiries'))));
 return goserve_private.end_operation('inquiry',p_idempotency_key,jsonb_build_object('thread',to_jsonb(t),'message',to_jsonb(m)));
end$$;
create function public.read_inquiry(p_thread_id uuid,p_through_sequence bigint) returns jsonb language plpgsql security definer set search_path='' as $$declare r public.inquiry_reads;last_seq bigint;begin
 perform goserve_private.actor();if not goserve_private.thread_access(p_thread_id) then perform goserve_private.fail('NOT_FOUND');end if;
 select last_sequence into last_seq from public.inquiry_threads where id=p_thread_id;
 if p_through_sequence<0 or p_through_sequence>last_seq then perform goserve_private.fail('VALIDATION_FAILED');end if;
 insert into public.inquiry_reads(thread_id,profile_id,through_sequence) values(p_thread_id,auth.uid(),p_through_sequence) on conflict(thread_id,profile_id) do update set through_sequence=greatest(inquiry_reads.through_sequence,excluded.through_sequence),read_at=now() returning * into r;return to_jsonb(r);end$$;
create function public.assign_inquiry(p_thread_id uuid,p_recipient_id uuid,p_expected_version bigint,p_idempotency_key uuid) returns jsonb language plpgsql security definer set search_path='' as $$declare t public.inquiry_threads;cached jsonb;begin
 perform goserve_private.actor();select * into t from public.inquiry_threads where id=p_thread_id for update;
 if not found or not goserve_private.capable(t.office_id,'inquiries') then perform goserve_private.fail('NOT_FOUND');end if;
 cached:=goserve_private.begin_operation('assign',p_idempotency_key,jsonb_build_array(p_thread_id,p_recipient_id,p_expected_version));if cached is not null then return cached;end if;
 if t.version is distinct from p_expected_version then perform goserve_private.fail('STALE_VERSION');end if;
 if p_recipient_id is not null and not exists(select 1 from public.profiles where id=p_recipient_id and role='staff' and office_id=t.office_id) then perform goserve_private.fail('FORBIDDEN');end if;
 update public.inquiry_threads set assigned_to=p_recipient_id,version=version+1 where id=t.id returning * into t;return goserve_private.end_operation('assign',p_idempotency_key,to_jsonb(t));end$$;
create table public.consultation_slots(id uuid primary key default gen_random_uuid(),office_id text not null references public.offices(id),schedule_owner_id uuid not null references public.profiles(id),start_at timestamptz not null,end_at timestamptz not null,capacity integer not null check(capacity>0),reserved integer not null default 0 check(reserved>=0 and reserved<=capacity),active boolean not null default true,version bigint not null default 1,is_demo boolean not null default true,check(start_at<end_at));
create index slots_owner on public.consultation_slots(schedule_owner_id,start_at);
create table public.appointments(id uuid primary key default gen_random_uuid(),student_id uuid not null references public.profiles(id),slot_id uuid not null references public.consultation_slots(id),state text not null default 'booked' check(state in('booked','cancelled')),version bigint not null default 1,created_at timestamptz not null default now());
create unique index appointment_one_active on public.appointments(student_id,slot_id) where state='booked';
create table public.appointment_events(id uuid primary key default gen_random_uuid(),appointment_id uuid not null references public.appointments(id),actor_id uuid not null references public.profiles(id),action text not null,old_slot_id uuid,new_slot_id uuid,reason text,created_at timestamptz not null default now());
create function goserve_private.schedule_access(office text,owner uuid) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.profiles p where p.id=auth.uid() and p.role='staff' and p.office_id=office and ((office='OFF-GCC' and p.staff_capability='guidance') or (office='DEP-CCS' and (p.staff_capability='secretary' or (p.staff_capability='faculty' and p.id=owner)))))
$$;
create function goserve_private.appointment_access(aid uuid) returns boolean language sql stable security definer set search_path='' as $$select exists(select 1 from public.appointments a join public.consultation_slots s on s.id=a.slot_id where a.id=aid and (a.student_id=auth.uid() or goserve_private.schedule_access(s.office_id,s.schedule_owner_id)))$$;
create function goserve_private.appointment_event(aid uuid,action text,old_slot uuid,new_slot uuid,reason text) returns void language plpgsql security definer set search_path='' as $$declare ev uuid;a public.appointments;s public.consultation_slots;begin
 insert into public.appointment_events(appointment_id,actor_id,action,old_slot_id,new_slot_id,reason) values(aid,auth.uid(),action,old_slot,new_slot,reason) returning id into ev;
 select * into a from public.appointments where id=aid;select * into s from public.consultation_slots where id=a.slot_id;
 insert into public.notifications(recipient_id,target_type,target_id,event_id) select p.id,'appointment',aid,ev from public.profiles p where p.id=a.student_id or p.id=s.schedule_owner_id or (p.office_id=s.office_id and p.role='staff' and p.staff_capability in('secretary','guidance'));
end$$;
create function public.save_slot(p_id uuid,p_expected_version bigint,p_office_id text,p_owner_id uuid,p_start_at timestamptz,p_end_at timestamptz,p_capacity integer,p_active boolean,p_idempotency_key uuid) returns jsonb language plpgsql security definer set search_path='' as $$declare s public.consultation_slots;cached jsonb;begin
 perform goserve_private.actor();if not goserve_private.schedule_access(p_office_id,p_owner_id) then perform goserve_private.fail('FORBIDDEN');end if;
 if not exists(select 1 from public.profiles where id=p_owner_id and role='staff' and office_id=p_office_id and ((p_office_id='DEP-CCS' and staff_capability='faculty') or (p_office_id='OFF-GCC' and staff_capability='guidance'))) then perform goserve_private.fail('FORBIDDEN');end if;
 if not exists(select 1 from goserve_private.policies where name='consultation_offices' and value ? p_office_id) then perform goserve_private.fail('CONFIGURATION_REQUIRED');end if;
 if p_id is not null then select * into s from public.consultation_slots where id=p_id for update;if not found or not goserve_private.schedule_access(s.office_id,s.schedule_owner_id) then perform goserve_private.fail('NOT_FOUND');end if;end if;
 cached:=goserve_private.begin_operation('slot',p_idempotency_key,jsonb_build_array(p_id,p_expected_version,p_office_id,p_owner_id,p_start_at,p_end_at,p_capacity,p_active));if cached is not null then return cached;end if;
 if p_start_at>=p_end_at or p_capacity<=0 or p_start_at<=now() then perform goserve_private.fail('VALIDATION_FAILED');end if;
 if p_id is null then insert into public.consultation_slots(office_id,schedule_owner_id,start_at,end_at,capacity,active) values(p_office_id,p_owner_id,p_start_at,p_end_at,p_capacity,p_active) returning * into s;
 else
 if s.version is distinct from p_expected_version then perform goserve_private.fail('STALE_VERSION');end if;
 if s.reserved>0 then perform goserve_private.fail('POLICY_BLOCKED');end if;
 update public.consultation_slots set office_id=p_office_id,schedule_owner_id=p_owner_id,start_at=p_start_at,end_at=p_end_at,capacity=p_capacity,active=p_active,version=version+1 where id=p_id returning * into s;end if;
 return goserve_private.end_operation('slot',p_idempotency_key,to_jsonb(s));end$$;
create function public.book_consultation(p_slot_id uuid,p_expected_version bigint,p_idempotency_key uuid) returns jsonb language plpgsql security definer set search_path='' as $$declare a public.profiles;s public.consultation_slots;b public.appointments;cached jsonb;begin
 a:=goserve_private.actor();if a.role<>'student' then perform goserve_private.fail('FORBIDDEN');end if;
 cached:=goserve_private.begin_operation('book',p_idempotency_key,jsonb_build_array(p_slot_id,p_expected_version));if cached is not null then return cached;end if;
 select * into s from public.consultation_slots where id=p_slot_id for update;if not found then perform goserve_private.fail('NOT_FOUND');end if;
 if not s.active or s.start_at<=now() then perform goserve_private.fail('INVALID_STATE');end if;
 if s.version is distinct from p_expected_version then perform goserve_private.fail('STALE_VERSION');end if;
 if s.reserved>=s.capacity then perform goserve_private.fail('CAPACITY_FULL');end if;
 insert into public.appointments(student_id,slot_id) values(a.id,s.id) returning * into b;
 update public.consultation_slots set reserved=reserved+1,version=version+1 where id=s.id;
 perform goserve_private.appointment_event(b.id,'booked',null,s.id,null);
 return goserve_private.end_operation('book',p_idempotency_key,to_jsonb(b));end$$;
create function public.change_appointment(p_appointment_id uuid,p_expected_version bigint,p_target_slot_id uuid,p_target_version bigint,p_reason text,p_idempotency_key uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare a public.profiles;b public.appointments;s public.consultation_slots;target public.consultation_slots;policy jsonb;cached jsonb;old_slot uuid;op text;
begin
 a:=goserve_private.actor();if not goserve_private.appointment_access(p_appointment_id) then perform goserve_private.fail('NOT_FOUND');end if;
 select * into b from public.appointments where id=p_appointment_id for update;
 op:=case when p_target_slot_id is null then 'cancel' else 'reschedule' end;
 cached:=goserve_private.begin_operation('appointment',p_idempotency_key,jsonb_build_array(p_appointment_id,p_expected_version,p_target_slot_id,p_target_version,p_reason));if cached is not null then return cached;end if;
 perform 1 from public.consultation_slots where id in(b.slot_id,p_target_slot_id) order by id for update;
 select * into s from public.consultation_slots where id=b.slot_id;
 select value into policy from goserve_private.policies where name='consultation_'||op;
 if policy is null or policy->>'minimum_notice_seconds' is null then perform goserve_private.fail('CONFIGURATION_REQUIRED');end if;
 if not coalesce(policy->'roles' ? a.role,false) or now()>s.start_at-make_interval(secs=>(policy->>'minimum_notice_seconds')::double precision) then perform goserve_private.fail('POLICY_BLOCKED');end if;
 if b.version is distinct from p_expected_version then perform goserve_private.fail('STALE_VERSION');end if;
 if b.state<>'booked' then perform goserve_private.fail('INVALID_STATE');end if;
 old_slot:=b.slot_id;
 if p_target_slot_id is not null then
 select * into target from public.consultation_slots where id=p_target_slot_id;
 if not found or not target.active or target.start_at<=now() or target.id=s.id then perform goserve_private.fail('INVALID_STATE');end if;
 if a.role<>'student' and not goserve_private.schedule_access(target.office_id,target.schedule_owner_id) then perform goserve_private.fail('FORBIDDEN');end if;
 if target.version is distinct from p_target_version then perform goserve_private.fail('STALE_VERSION');end if;
 if target.reserved>=target.capacity then perform goserve_private.fail('CAPACITY_FULL');end if;
 update public.consultation_slots set reserved=reserved+1,version=version+1 where id=target.id;
 update public.appointments set slot_id=target.id,version=version+1 where id=b.id returning * into b;
 else update public.appointments set state='cancelled',version=version+1 where id=b.id returning * into b;end if;
 update public.consultation_slots set reserved=reserved-1,version=version+1 where id=s.id;
 perform goserve_private.appointment_event(b.id,op,old_slot,p_target_slot_id,p_reason);
 return goserve_private.end_operation('appointment',p_idempotency_key,to_jsonb(b));end$$;
do $$declare t text;begin foreach t in array ARRAY['inquiry_threads','inquiry_messages','inquiry_reads','consultation_slots','appointments','appointment_events'] loop execute format('alter table public.%I enable row level security',t);execute format('revoke all on public.%I from public,anon,authenticated',t);execute format('grant select on public.%I to authenticated',t);execute format('grant all on public.%I to service_role',t);end loop;end$$;
create policy thread_read on public.inquiry_threads for select to authenticated using(goserve_private.ready() and goserve_private.thread_access(id));
create policy message_read on public.inquiry_messages for select to authenticated using(goserve_private.ready() and goserve_private.thread_access(thread_id));
create policy read_own on public.inquiry_reads for select to authenticated using(goserve_private.ready() and goserve_private.thread_access(thread_id));
create policy slots_read on public.consultation_slots for select to authenticated using(goserve_private.ready() and (active or goserve_private.schedule_access(office_id,schedule_owner_id)));
create policy appointment_read on public.appointments for select to authenticated using(goserve_private.ready() and goserve_private.appointment_access(id));
create policy appointment_event_read on public.appointment_events for select to authenticated using(goserve_private.ready() and goserve_private.appointment_access(appointment_id));
revoke all on all functions in schema goserve_private from public,anon,authenticated;
grant execute on function goserve_private.ready(),goserve_private.capable(text,text),goserve_private.request_access(uuid),goserve_private.thread_access(uuid),goserve_private.schedule_access(text,uuid),goserve_private.appointment_access(uuid) to authenticated;
do $$declare f record;begin for f in select p.oid::regprocedure sig from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname in('send_inquiry','read_inquiry','assign_inquiry','save_slot','book_consultation','change_appointment') loop execute format('revoke all on function %s from public,anon',f.sig);execute format('grant execute on function %s to authenticated',f.sig);end loop;end$$;
commit;
