begin;
create function goserve_private.seryo_engine_ready() returns boolean language plpgsql security definer set search_path='' as $$
declare configured boolean;
begin
 if not exists(select 1 from goserve_private.policies where name='seryo_gateway'
 and value->>'enabled'='true' and value->>'transport'='egovai-engine-v1'
 and value->>'tools_enabled'='false' and value->>'public_search_enabled'='false') then return false;end if;
 if to_regclass('vault.decrypted_secrets') is null then return false;end if;
 execute $q$select count(distinct name)=3 from vault.decrypted_secrets where name in('goserve_seryo_api_key','goserve_seryo_api_secret','goserve_seryo_engine_code') and length(decrypted_secret)>0$q$ into configured;
 return coalesce(configured,false);
end $$;
revoke all on function goserve_private.seryo_engine_ready() from public,anon,authenticated,service_role;

create function public.get_seryo_engine_configuration() returns jsonb language plpgsql security definer set search_path='' as $$
declare result jsonb;
begin
 if not goserve_private.seryo_engine_ready() then perform goserve_private.fail('PROVIDER_UNAVAILABLE');end if;
 execute $q$select jsonb_build_object('integration_enabled',true,'api_key',max(decrypted_secret) filter(where name='goserve_seryo_api_key'),'api_secret',max(decrypted_secret) filter(where name='goserve_seryo_api_secret'),'engine_code',max(decrypted_secret) filter(where name='goserve_seryo_engine_code')) from vault.decrypted_secrets where name in('goserve_seryo_api_key','goserve_seryo_api_secret','goserve_seryo_engine_code')$q$ into result;
 return result;
end $$;
revoke all on function public.get_seryo_engine_configuration() from public,anon,authenticated;
grant execute on function public.get_seryo_engine_configuration() to service_role;
create or replace function public.seryo_state() returns jsonb
language plpgsql security definer set search_path='' as $$
declare c public.ai_consent; dirty boolean; ready boolean; running boolean;
begin
 perform goserve_private.actor();
 select * into c from public.ai_consent where profile_id=auth.uid();
 select exists(select 1 from goserve_private.ai_operations where owner_id=auth.uid() and (state in('running','unknown') or cleanup in('pending','unknown','failed')))
 or exists(select 1 from goserve_private.ai_attachments where owner_id=auth.uid() and cleanup<>'verified_deleted') into dirty;
 ready:=goserve_private.seryo_engine_ready();
 select exists(select 1 from goserve_private.seryo_gateway_operations where owner_id=auth.uid() and state='running' and created_at>now()-interval '90 seconds') into running;
 return jsonb_build_object('consent',coalesce(c.decision,'unset'),'consent_version',coalesce(c.version,0),
 'provider_ready',ready,'can_send',ready and c.decision is not distinct from 'allow' and not dirty and not running,
 'can_attach',false,'cleanup',case when dirty then 'unresolved' else 'clear' end,
 'blockers',case when ready then '[]'::jsonb else '["PROVIDER_UNAVAILABLE"]'::jsonb end
 ||case when dirty then '["CLEANUP_REQUIRED"]'::jsonb else '[]'::jsonb end
 ||case when c.decision is distinct from 'allow' then '["CONSENT_REQUIRED"]'::jsonb else '[]'::jsonb end);
end $$;
commit;

