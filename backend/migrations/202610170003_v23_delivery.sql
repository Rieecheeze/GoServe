begin;
create table public.request_files(id uuid primary key default gen_random_uuid(),owner_id uuid not null references public.profiles(id),service_id uuid not null references public.services(id),request_id uuid references public.requests(id),kind text not null check(kind in('evidence','deliverable')),requirement text,mime_type text not null,size_bytes bigint not null check(size_bytes>0),verification text not null default 'pending' check(verification in('pending','verified','rejected')),version bigint not null default 1,current boolean not null default true,created_at timestamptz not null default now(),deleted_at timestamptz);
create unique index one_current_deliverable on public.request_files(request_id) where kind='deliverable' and current and verification='verified' and deleted_at is null;
create index files_request on public.request_files(request_id);
create table goserve_private.file_objects(file_id uuid primary key references public.request_files(id),object_path text not null unique,sha256 text,expires_at timestamptz not null,verified_at timestamptz);
create table goserve_private.pickup_claims(id uuid primary key default gen_random_uuid(),request_id uuid not null references public.requests(id),code_hash text not null unique,display_code text not null,expires_at timestamptz not null,active boolean not null default true,consumed_at timestamptz,consumed_by uuid references public.profiles(id));
create unique index pickup_one_active on goserve_private.pickup_claims(request_id) where active;
create table goserve_private.pickup_reviews(id uuid primary key default gen_random_uuid(),claim_id uuid not null references goserve_private.pickup_claims(id),actor_id uuid not null references public.profiles(id),request_version bigint not null,created_at timestamptz not null default now());
create table goserve_private.downloads(id uuid primary key default gen_random_uuid(),file_id uuid not null references public.request_files(id),owner_id uuid not null references public.profiles(id),expires_at timestamptz not null,transfer_completed_at timestamptz,saved_at timestamptz,created_at timestamptz not null default now());
create table goserve_private.cleanup_jobs(id uuid primary key default gen_random_uuid(),file_id uuid not null unique references public.request_files(id),eligible_at timestamptz not null,state text not null default 'pending' check(state in('pending','leased','failed','verified_deleted')),lease_id uuid,lease_until timestamptz,attempts integer not null default 0,verified_deleted_at timestamptz);
create function goserve_private.bind_evidence(rid uuid,files uuid[]) returns void language plpgsql security definer set search_path='' as $$
declare r public.requests;f public.request_files;requirement jsonb;
begin
 select * into r from public.requests where id=rid;
 if files is null or cardinality(files)<>(select count(distinct x) from unnest(files) x) then perform goserve_private.fail('UPLOAD_INVALID');end if;
 foreach requirement in array ARRAY(select jsonb_array_elements(r.snapshot->'requirements')) loop
 if not exists(select 1 from public.request_files where id=any(files) and request_files.requirement=requirement->>'id') then perform goserve_private.fail('UPLOAD_INVALID');end if;end loop;
 for f in select * from public.request_files where id=any(files) order by id for update loop
 if f.owner_id<>r.owner_id or f.service_id<>r.service_id or f.kind<>'evidence' or f.verification<>'verified' or f.request_id is not null or f.deleted_at is not null then perform goserve_private.fail('UPLOAD_INVALID');end if;end loop;
 if (select count(*) from public.request_files where id=any(files))<>cardinality(files) then perform goserve_private.fail('UPLOAD_INVALID');end if;
 update public.request_files set request_id=rid where id=any(files);
end$$;
create function goserve_private.has_deliverable(rid uuid) returns boolean language sql stable security definer set search_path='' as $$select exists(select 1 from public.request_files where request_id=rid and kind='deliverable' and verification='verified' and current and mime_type='application/pdf' and deleted_at is null)$$;
create function public.prepare_file(p_service_id uuid,p_request_id uuid,p_kind text,p_requirement text,p_mime_type text,p_size_bytes bigint,p_idempotency_key uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare a public.profiles;r public.requests;f public.request_files;s public.services;policy jsonb;cached jsonb;owner uuid;
begin
 a:=goserve_private.actor();
 select * into s from public.services where id=p_service_id and active;if not found then perform goserve_private.fail('NOT_FOUND');end if;
 if p_kind='evidence' then if a.role<>'student' or p_request_id is not null then perform goserve_private.fail('FORBIDDEN');end if;
 if p_requirement is not null and not exists(select 1 from jsonb_array_elements(s.requirements) item where item->>'id'=p_requirement) then perform goserve_private.fail('UPLOAD_INVALID');end if;owner:=a.id;
 elsif p_kind='deliverable' then r:=goserve_private.request_row(p_request_id);if not goserve_private.capable(r.office_id,'requests') or r.delivery_mode<>'digital' or r.service_id<>s.id or r.status not in('Processing','Ready for Pickup/Download') or p_mime_type<>'application/pdf' then perform goserve_private.fail('FORBIDDEN');end if;owner:=r.owner_id;
 else perform goserve_private.fail('VALIDATION_FAILED');end if;
 select value into policy from goserve_private.policies where name='file_'||s.id::text||'_'||p_kind;
 if policy is null or policy->>'max_bytes' is null or policy->>'upload_seconds' is null or policy->>'verifier' is null then perform goserve_private.fail('CONFIGURATION_REQUIRED');end if;
 if p_size_bytes<=0 or p_size_bytes>(policy->>'max_bytes')::bigint or not coalesce(policy->'mime_types' ? p_mime_type,false) then perform goserve_private.fail('UPLOAD_INVALID');end if;
 cached:=goserve_private.begin_operation('file',p_idempotency_key,jsonb_build_array(p_service_id,p_request_id,p_kind,p_requirement,p_mime_type,p_size_bytes));if cached is not null then return cached;end if;
 insert into public.request_files(owner_id,service_id,request_id,kind,requirement,mime_type,size_bytes) values(owner,s.id,p_request_id,p_kind,p_requirement,p_mime_type,p_size_bytes) returning * into f;
 insert into goserve_private.file_objects(file_id,object_path,expires_at) values(f.id,owner::text||'/'||f.id::text,now()+make_interval(secs=>(policy->>'upload_seconds')::double precision));
 return goserve_private.end_operation('file',p_idempotency_key,to_jsonb(f));
end$$;
create function public.pickup_claim(p_request_id uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare r public.requests;c goserve_private.pickup_claims;policy jsonb;code text;
begin
 r:=goserve_private.request_row(p_request_id);
 if r.owner_id<>auth.uid() then perform goserve_private.fail('FORBIDDEN');end if;
 if r.delivery_mode<>'physical' or r.status<>'Ready for Pickup/Download' then perform goserve_private.fail('INVALID_STATE');end if;
 if not goserve_private.paid(r.id) then perform goserve_private.fail('PAYMENT_REQUIRED');end if;
 select value into policy from goserve_private.policies where name='pickup';
 if policy is null or (policy->>'validity_seconds')::integer is null or (policy->>'validity_seconds')::integer<=0 then perform goserve_private.fail('CONFIGURATION_REQUIRED');end if;
 select * into c from goserve_private.pickup_claims where request_id=r.id and active for update;
 if found and c.expires_at<=now() then
 if not coalesce((policy->>'replace_expired')::boolean,false) then perform goserve_private.fail('POLICY_BLOCKED');end if;
 update goserve_private.pickup_claims set active=false where id=c.id;c:=null;end if;
 if c.id is null then
 code:=replace(gen_random_uuid()::text,'-','')||replace(gen_random_uuid()::text,'-','');
 insert into goserve_private.pickup_claims(request_id,code_hash,display_code,expires_at) values(r.id,encode(sha256(convert_to(code,'UTF8')),'hex'),code,now()+make_interval(secs=>(policy->>'validity_seconds')::double precision)) returning * into c;end if;
 return jsonb_build_object('id',c.id,'request_id',r.id,'display_code',c.display_code,'qr_payload',c.display_code,'expires_at',c.expires_at);
end$$;
create function public.pickup_validate(p_code text) returns jsonb language plpgsql security definer set search_path='' as $$
declare c goserve_private.pickup_claims;r public.requests;v uuid;
begin
 perform goserve_private.actor();
 select * into c from goserve_private.pickup_claims where code_hash=encode(sha256(convert_to(p_code,'UTF8')),'hex');
 if not found then perform goserve_private.fail('CODE_INVALID');end if;
 select * into r from public.requests where id=c.request_id for update;
 if not goserve_private.capable(r.office_id,'requests') then perform goserve_private.fail('CODE_INVALID');end if;
 if c.consumed_at is not null then perform goserve_private.fail('CODE_CONSUMED');end if;
 if not c.active or c.expires_at<=now() then perform goserve_private.fail('CODE_EXPIRED');end if;
 if r.status<>'Ready for Pickup/Download' or r.delivery_mode<>'physical' or not goserve_private.paid(r.id) then perform goserve_private.fail('INVALID_STATE');end if;
 insert into goserve_private.pickup_reviews(claim_id,actor_id,request_version) values(c.id,auth.uid(),r.version) returning id into v;
 return jsonb_build_object('validation_id',v,'request_id',r.id,'reference',r.reference,'office_id',r.office_id,'request_version',r.version,'claim_expires_at',c.expires_at);
end$$;
create function public.pickup_handover(p_validation_id uuid,p_expected_version bigint,p_idempotency_key uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare v goserve_private.pickup_reviews;c goserve_private.pickup_claims;r public.requests;cached jsonb;
begin
 perform goserve_private.actor();select * into v from goserve_private.pickup_reviews where id=p_validation_id and actor_id=auth.uid();if not found then perform goserve_private.fail('NOT_FOUND');end if;
 select * into c from goserve_private.pickup_claims where id=v.claim_id;
 r:=goserve_private.request_row(c.request_id);if not goserve_private.capable(r.office_id,'requests') then perform goserve_private.fail('FORBIDDEN');end if;
 cached:=goserve_private.begin_operation('handover',p_idempotency_key,jsonb_build_array(p_validation_id,p_expected_version));if cached is not null then return cached;end if;
 select * into c from goserve_private.pickup_claims where id=v.claim_id for update;
 if c.consumed_at is not null then perform goserve_private.fail('CODE_CONSUMED');end if;
 if not c.active or c.expires_at<=now() then perform goserve_private.fail('CODE_EXPIRED');end if;
 if r.version<>v.request_version or r.version is distinct from p_expected_version then perform goserve_private.fail('STALE_VERSION');end if;
 if r.status<>'Ready for Pickup/Download' or not goserve_private.paid(r.id) then perform goserve_private.fail('INVALID_STATE');end if;
 update goserve_private.pickup_claims set active=false,consumed_at=now(),consumed_by=auth.uid() where id=c.id;
 update public.requests set status='Completed',terminal_at=now(),version=version+1 where id=r.id returning * into r;
 perform goserve_private.request_event(r.id,'Ready for Pickup/Download','Completed',null);
 return goserve_private.end_operation('handover',p_idempotency_key,to_jsonb(r));
end$$;
create function public.begin_download(p_file_id uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare f public.request_files;r public.requests;d goserve_private.downloads;seconds integer;
begin
 perform goserve_private.actor();select * into f from public.request_files where id=p_file_id;
 if not found then perform goserve_private.fail('NOT_FOUND');end if;r:=goserve_private.request_row(f.request_id);
 select * into f from public.request_files where id=p_file_id;
 if r.owner_id<>auth.uid() then perform goserve_private.fail('FORBIDDEN');end if;
 if r.status<>'Ready for Pickup/Download' or r.delivery_mode<>'digital' or not goserve_private.paid(r.id) or not f.current or f.kind<>'deliverable' or f.verification<>'verified' or f.deleted_at is not null then perform goserve_private.fail('INVALID_STATE');end if;
 select (value->>'validity_seconds')::integer into seconds from goserve_private.policies where name='download';if seconds is null or seconds<=0 then perform goserve_private.fail('CONFIGURATION_REQUIRED');end if;
 insert into goserve_private.downloads(file_id,owner_id,expires_at) values(f.id,auth.uid(),now()+make_interval(secs=>seconds)) returning * into d;
 return jsonb_build_object('download_id',d.id,'file_id',f.id,'expires_at',d.expires_at,'sha256',(select sha256 from goserve_private.file_objects where file_id=f.id));
end$$;
create function public.acknowledge_download(p_download_id uuid,p_sha256 text) returns jsonb language plpgsql security definer set search_path='' as $$declare d goserve_private.downloads;f public.request_files;r public.requests;begin
 perform goserve_private.actor();select * into d from goserve_private.downloads where id=p_download_id and owner_id=auth.uid() for update;if not found then perform goserve_private.fail('NOT_FOUND');end if;
 select * into f from public.request_files where id=d.file_id;r:=goserve_private.request_row(f.request_id);
 select * into f from public.request_files where id=d.file_id;
 if d.transfer_completed_at is null then perform goserve_private.fail('DOWNLOAD_UNCONFIRMED');end if;
 if not f.current or f.deleted_at is not null or r.status<>'Ready for Pickup/Download' or not goserve_private.paid(r.id) then perform goserve_private.fail('INVALID_STATE');end if;
 if p_sha256 is distinct from (select sha256 from goserve_private.file_objects where file_id=f.id) then perform goserve_private.fail('DOWNLOAD_UNCONFIRMED');end if;
 update goserve_private.downloads set saved_at=coalesce(saved_at,now()) where id=d.id returning * into d;return jsonb_build_object('download_id',d.id,'confirmed_at',d.saved_at);end$$;
create function public.confirm_receipt(p_request_id uuid,p_expected_version bigint,p_idempotency_key uuid) returns jsonb language plpgsql security definer set search_path='' as $$declare r public.requests;cached jsonb;begin
 r:=goserve_private.request_row(p_request_id);if r.owner_id<>auth.uid() then perform goserve_private.fail('FORBIDDEN');end if;
 cached:=goserve_private.begin_operation('receipt',p_idempotency_key,jsonb_build_array(p_request_id,p_expected_version));if cached is not null then return cached;end if;
 if r.version is distinct from p_expected_version then perform goserve_private.fail('STALE_VERSION');end if;
 if r.delivery_mode<>'digital' or r.status<>'Ready for Pickup/Download' or not goserve_private.paid(r.id) then perform goserve_private.fail('INVALID_STATE');end if;
 if not exists(select 1 from goserve_private.downloads d join public.request_files f on f.id=d.file_id where f.request_id=r.id and f.current and f.verification='verified' and f.deleted_at is null and d.owner_id=auth.uid() and d.saved_at is not null and d.transfer_completed_at is not null) then perform goserve_private.fail('DOWNLOAD_UNCONFIRMED');end if;
 update public.requests set status='Completed',terminal_at=now(),version=version+1 where id=r.id returning * into r;perform goserve_private.request_event(r.id,'Ready for Pickup/Download','Completed',null);return goserve_private.end_operation('receipt',p_idempotency_key,to_jsonb(r));end$$;
alter table public.request_files enable row level security;
revoke all on public.request_files from public,anon,authenticated;grant select on public.request_files to authenticated;grant all on public.request_files to service_role;
create policy files_read on public.request_files for select to authenticated using(goserve_private.ready() and (owner_id=auth.uid() or goserve_private.request_access(request_id)));
insert into storage.buckets(id,name,public) values('goserve-private','goserve-private',false) on conflict(id) do update set public=false;
create policy goserve_no_direct_objects on storage.objects as restrictive for all to anon,authenticated using(bucket_id<>'goserve-private') with check(bucket_id<>'goserve-private');
do $$declare t text;begin foreach t in array ARRAY['file_objects','pickup_claims','pickup_reviews','downloads','cleanup_jobs'] loop execute format('alter table goserve_private.%I enable row level security',t);execute format('revoke all on goserve_private.%I from public,anon,authenticated',t);execute format('grant all on goserve_private.%I to service_role',t);end loop;end$$;
revoke all on all functions in schema goserve_private from public,anon,authenticated;
grant execute on function goserve_private.ready(),goserve_private.capable(text,text),goserve_private.request_access(uuid) to authenticated;
do $$declare f record;begin for f in select p.oid::regprocedure sig from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname in('prepare_file','pickup_claim','pickup_validate','pickup_handover','begin_download','acknowledge_download','confirm_receipt') loop execute format('revoke all on function %s from public,anon',f.sig);execute format('grant execute on function %s to authenticated',f.sig);end loop;end$$;
commit;
