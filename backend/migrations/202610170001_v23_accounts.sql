begin;
alter table public.profiles
 add column first_name text, add column middle_name text, add column no_middle_name boolean not null default false,
 add column last_name text, add column student_id text, add column course_program text, add column year_level text,
 add column mobile_number text, add column gender text, add column profile_completed_at timestamptz,
 add column version bigint not null default 1;
alter table public.offices add column version bigint not null default 1;
create table public.terms_documents(version text primary key, content text not null check(btrim(content)<>''), content_digest text not null, published_at timestamptz not null default now(),active boolean not null default false);
create unique index terms_one_active on public.terms_documents(active) where active;
create table goserve_private.terms_acceptances(profile_id uuid references public.profiles(id),terms_version text references public.terms_documents(version),accepted_at timestamptz not null default now(),actor_id uuid not null,primary key(profile_id,terms_version));
create table goserve_private.operation_keys(actor uuid not null,operation text not null,key uuid not null,input jsonb not null,result jsonb,primary key(actor,operation,key));
create table goserve_private.policies(name text primary key,value jsonb not null);
create table goserve_private.capabilities(profile_id uuid references public.profiles(id),office_id text references public.offices(id),capability text not null,primary key(profile_id,office_id,capability));
create function goserve_private.fail(code text) returns void language plpgsql set search_path='' as $$begin raise exception '%',code using errcode='P0001'; end$$;
create function goserve_private.actor(ready boolean default true) returns public.profiles language plpgsql security definer set search_path='' as $$
declare p public.profiles;
begin
 if auth.uid() is null or coalesce((auth.jwt()->>'is_anonymous')::boolean,true) then perform goserve_private.fail('AUTH_REQUIRED'); end if;
 select * into p from public.profiles where id=auth.uid();
 if not found then perform goserve_private.fail('FORBIDDEN'); end if;
 if ready and p.profile_completed_at is null then perform goserve_private.fail('PROFILE_INCOMPLETE'); end if;
 if ready and not exists(select 1 from goserve_private.terms_acceptances where profile_id=p.id) then perform goserve_private.fail('TERMS_REQUIRED'); end if;
 return p;
end$$;
create function goserve_private.capable(office text,cap text) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.profiles p where p.id=auth.uid() and p.role='staff' and p.office_id=office and (office not like 'DEP-%' or p.staff_capability='secretary') and
 (exists(select 1 from goserve_private.capabilities c where c.profile_id=p.id and c.office_id=office and c.capability=cap)
 or (p.staff_capability is null and cap in('catalog','requests','inquiries'))
 or (p.staff_capability in('guidance','secretary') and cap='inquiries')))
$$;
create function goserve_private.begin_operation(op text,k uuid,payload jsonb) returns jsonb language plpgsql security definer set search_path='' as $$
declare old goserve_private.operation_keys;
begin
 if k is null then perform goserve_private.fail('VALIDATION_FAILED'); end if;
 insert into goserve_private.operation_keys(actor,operation,key,input) values(auth.uid(),op,k,payload) on conflict do nothing;
 select * into old from goserve_private.operation_keys where actor=auth.uid() and operation=op and key=k for update;
 if old.input is distinct from payload then perform goserve_private.fail('IDEMPOTENCY_CONFLICT'); end if;
 return old.result;
end$$;
create function goserve_private.end_operation(op text,k uuid,r jsonb) returns jsonb language plpgsql security definer set search_path='' as $$begin update goserve_private.operation_keys set result=r where actor=auth.uid() and operation=op and key=k;return r;end$$;
create function goserve_private.validate_profile(p jsonb) returns void language plpgsql set search_path='' as $$
declare f text;
begin
 if p is null or jsonb_typeof(p)<>'object' or p-ARRAY['first_name','middle_name','no_middle_name','last_name','student_id','course_program','year_level','mobile_number','gender']<>'{}'::jsonb then perform goserve_private.fail('VALIDATION_FAILED'); end if;
 foreach f in array ARRAY['first_name','last_name','student_id','course_program','year_level','mobile_number','gender'] loop
 if jsonb_typeof(p->f) is distinct from 'string' or btrim(p->>f)='' then perform goserve_private.fail('VALIDATION_FAILED'); end if;end loop;
 if jsonb_typeof(p->'no_middle_name') is distinct from 'boolean' or
 ((p->>'no_middle_name')::boolean and nullif(btrim(p->>'middle_name'),'') is not null) or
 (not (p->>'no_middle_name')::boolean and (jsonb_typeof(p->'middle_name') is distinct from 'string' or btrim(p->>'middle_name')='')) then perform goserve_private.fail('VALIDATION_FAILED'); end if;
end$$;
create function goserve_private.save_profile(who uuid,p jsonb) returns void language plpgsql security definer set search_path='' as $$begin
 perform goserve_private.validate_profile(p);
 update public.profiles set first_name=btrim(p->>'first_name'),middle_name=nullif(btrim(p->>'middle_name'),''),no_middle_name=(p->>'no_middle_name')::boolean,last_name=btrim(p->>'last_name'),student_id=btrim(p->>'student_id'),course_program=btrim(p->>'course_program'),year_level=btrim(p->>'year_level'),mobile_number=btrim(p->>'mobile_number'),gender=btrim(p->>'gender'),display_name=btrim(p->>'first_name')||' '||btrim(p->>'last_name'),profile_completed_at=now(),updated_at=now(),version=version+1 where id=who and role='student';
 if not found then perform goserve_private.fail('FORBIDDEN'); end if;
end$$;
create function public.account_view() returns jsonb language plpgsql security definer set search_path='' as $$
declare p public.profiles; t jsonb; current_terms text;
begin
 p:=goserve_private.actor(false);
 select version into current_terms from public.terms_documents where active;
 select jsonb_build_object('accepted_version',terms_version,'accepted_at',accepted_at) into t from goserve_private.terms_acceptances where profile_id=p.id order by accepted_at desc limit 1;
 return to_jsonb(p)||jsonb_build_object('profile_complete',p.profile_completed_at is not null,'terms',coalesce(t,'{}')||jsonb_build_object('current_version',current_terms),'next_action',case when t is null then 'accept_terms' when p.profile_completed_at is null then 'complete_profile' else 'home' end);
end$$;
create function public.update_account(p_expected_version bigint,p_terms_version text,p_student_profile jsonb,p_idempotency_key uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare p public.profiles;r jsonb;t text;
begin
 p:=goserve_private.actor(false);
 select * into p from public.profiles where id=p.id for update;
 r:=goserve_private.begin_operation('account',p_idempotency_key,jsonb_build_array(p_expected_version,p_terms_version,p_student_profile));if r is not null then return public.account_view();end if;
 select version into t from public.terms_documents where active for share;
 if t is null then perform goserve_private.fail('CONFIGURATION_REQUIRED');end if;
 if p.version is distinct from p_expected_version then perform goserve_private.fail('STALE_VERSION');end if;
 if p_terms_version is null and p_student_profile is null then perform goserve_private.fail('VALIDATION_FAILED');end if;
 if p_terms_version is not null then
 if t<>p_terms_version then perform goserve_private.fail('TERMS_REQUIRED');end if;
 insert into goserve_private.terms_acceptances(profile_id,terms_version,actor_id) values(p.id,t,p.id) on conflict do nothing;
 end if;
 if p_student_profile is not null then perform goserve_private.save_profile(p.id,p_student_profile);else update public.profiles set version=version+1,updated_at=now() where id=p.id;end if;
 return goserve_private.end_operation('account',p_idempotency_key,public.account_view());
end$$;
create function goserve_private.immutable_terms() returns trigger language plpgsql set search_path='' as $$begin
 if TG_OP='DELETE' or (to_jsonb(new)-'active') is distinct from (to_jsonb(old)-'active') then perform goserve_private.fail('IMMUTABLE_RECORD');end if;return new;end$$;
create trigger terms_immutable before update or delete on public.terms_documents for each row execute function goserve_private.immutable_terms();
alter table public.terms_documents enable row level security;
create policy terms_read on public.terms_documents for select to anon,authenticated using(true);
grant select on public.terms_documents to anon,authenticated;
grant all on public.terms_documents to service_role;
do $$declare t text;begin foreach t in array ARRAY['terms_acceptances','operation_keys','policies','capabilities'] loop execute format('alter table goserve_private.%I enable row level security',t);execute format('revoke all on goserve_private.%I from public,anon,authenticated',t);execute format('grant all on goserve_private.%I to service_role',t);end loop;end$$;
revoke all on all functions in schema goserve_private from public,anon,authenticated;
revoke all on function public.account_view(),public.update_account(bigint,text,jsonb,uuid) from public,anon;
grant execute on function public.account_view(),public.update_account(bigint,text,jsonb,uuid) to authenticated;
commit;
