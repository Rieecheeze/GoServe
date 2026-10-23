begin;
create table goserve_private.seryo_gateway_operations(
 id uuid primary key default gen_random_uuid(),owner_id uuid not null references public.profiles(id),
 key uuid not null,state text not null check(state in('running','resolved')),
 created_at timestamptz not null default now(),unique(owner_id,key)
);
alter table goserve_private.seryo_gateway_operations enable row level security;
revoke all on goserve_private.seryo_gateway_operations from public,anon,authenticated,service_role;
create index seryo_gateway_owner_time on goserve_private.seryo_gateway_operations(owner_id,created_at desc);

create or replace function public.seryo_state() returns jsonb
language plpgsql security definer set search_path='' as $$
declare c public.ai_consent; dirty boolean; ready boolean; running boolean;
begin
 perform goserve_private.actor();
 select * into c from public.ai_consent where profile_id=auth.uid();
 select exists(select 1 from goserve_private.ai_operations where owner_id=auth.uid() and (state in('running','unknown') or cleanup in('pending','unknown','failed')))
 or exists(select 1 from goserve_private.ai_attachments where owner_id=auth.uid() and cleanup<>'verified_deleted') into dirty;
 select exists(select 1 from goserve_private.policies where name='seryo_gateway' and value->>'enabled'='true' and value->>'transport'='platforms-ai-assistant-v1') into ready;
 select exists(select 1 from goserve_private.seryo_gateway_operations where owner_id=auth.uid() and state='running' and created_at>now()-interval '90 seconds') into running;
 return jsonb_build_object('consent',coalesce(c.decision,'unset'),'consent_version',coalesce(c.version,0),
 'provider_ready',ready,'can_send',ready and c.decision is not distinct from 'allow' and not dirty and not running,
 'can_attach',false,'cleanup',case when dirty then 'unresolved' else 'clear' end,
 'blockers',case when ready then '[]'::jsonb else '["PROVIDER_UNAVAILABLE"]'::jsonb end
 ||case when dirty then '["CLEANUP_REQUIRED"]'::jsonb else '[]'::jsonb end
 ||case when c.decision is distinct from 'allow' then '["CONSENT_REQUIRED"]'::jsonb else '[]'::jsonb end);
end $$;

create function public.seryo_context() returns jsonb language plpgsql security definer set search_path='' as $$
declare a public.profiles; state jsonb; result jsonb;
begin
 a:=goserve_private.actor();state:=public.seryo_state();
 if state->>'consent'<>'allow' then perform goserve_private.fail('CONSENT_REQUIRED');end if;
 if state->>'cleanup'<>'clear' then perform goserve_private.fail('CLEANUP_REQUIRED');end if;
 select coalesce(jsonb_agg(jsonb_build_object('office',name,'service',service_name,'delivery',delivery_modes,
 'feeLabel',case when fee_state='free' then 'No fee' when fee_state='required' then amount::text||' '||currency||' (test only)' else 'Check with the office' end)),'[]'::jsonb)
 into result from (select o.name,s.name service_name,s.delivery_modes,s.fee_state,s.amount,s.currency
 from public.services s join public.offices o on o.id=s.office_id
 where s.active and s.is_demo and o.active and (o.id not like 'DEP-%' or o.id=a.department_id or (a.role='staff' and a.staff_capability='secretary' and o.id=a.office_id))
 order by o.name,s.name,s.id limit 30) catalog;
 return result;
end $$;

create function public.seryo_dispatch_begin(p_key uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare a public.profiles; state jsonb; operation uuid;
begin
 a:=goserve_private.actor();perform 1 from public.profiles where id=a.id for update;
 if p_key is null then perform goserve_private.fail('VALIDATION_FAILED');end if;
 state:=public.seryo_state();
 if state->>'consent'<>'allow' then perform goserve_private.fail('CONSENT_REQUIRED');end if;
 if state->>'cleanup'<>'clear' then perform goserve_private.fail('CLEANUP_REQUIRED');end if;
 if state->>'provider_ready'<>'true' then perform goserve_private.fail('PROVIDER_UNAVAILABLE');end if;
 if exists(select 1 from goserve_private.seryo_gateway_operations where owner_id=a.id and key=p_key) then perform goserve_private.fail('INVALID_STATE');end if;
 if state->>'can_send'<>'true'
 or (select count(*) from goserve_private.seryo_gateway_operations where owner_id=a.id and created_at>now()-interval '1 minute')>=5
 or (select count(*) from goserve_private.seryo_gateway_operations where owner_id=a.id and created_at>now()-interval '1 day')>=40 then perform goserve_private.fail('POLICY_BLOCKED');end if;
 insert into goserve_private.seryo_gateway_operations(owner_id,key,state) values(a.id,p_key,'running') returning id into operation;
 return jsonb_build_object('operation_id',operation,'consent_version',(state->>'consent_version')::bigint);
end $$;

create function public.seryo_dispatch_finish(p_actor uuid,p_operation uuid) returns void language plpgsql security definer set search_path='' as $$
begin
 update goserve_private.seryo_gateway_operations set state='resolved' where id=p_operation and owner_id=p_actor;
 if not found then perform goserve_private.fail('FORBIDDEN');end if;
end $$;
revoke all on function public.seryo_state(),public.seryo_context(),public.seryo_dispatch_begin(uuid) from public,anon;
grant execute on function public.seryo_state(),public.seryo_context(),public.seryo_dispatch_begin(uuid) to authenticated;
revoke all on function public.seryo_dispatch_finish(uuid,uuid) from public,anon,authenticated;
grant execute on function public.seryo_dispatch_finish(uuid,uuid) to service_role;
commit;
