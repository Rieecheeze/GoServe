begin;
create or replace function goserve_private.seryo_engine_ready() returns boolean language plpgsql security definer set search_path='' as $$
declare transport text; configured boolean;
begin
 select value->>'transport' into transport from goserve_private.policies where name='seryo_gateway'
 and value->>'enabled'='true' and value->>'tools_enabled'='false' and value->>'public_search_enabled'='false';
 if transport is null or to_regclass('vault.decrypted_secrets') is null then return false;end if;
 if transport='egovai-engine-v1' then
  execute $q$select count(distinct name)=3 from vault.decrypted_secrets where name in('goserve_seryo_api_key','goserve_seryo_api_secret','goserve_seryo_engine_code') and length(decrypted_secret)>0$q$ into configured;
 elsif transport='platforms-ai-assistant-v1' then
  if to_regclass('goserve_private.egovai_configuration') is null then return false;end if;
  execute $q$select exists(select 1 from goserve_private.egovai_configuration c join vault.decrypted_secrets s on s.id=c.access_code_secret_id
  where c.provider='egovai' and c.integration_enabled and c.base_url='https://platforms-api.e.gov.ph/egov-ai' and s.name='goserve_egovai_access_code' and length(s.decrypted_secret)>0)$q$ into configured;
 else return false;end if;
 return coalesce(configured,false);
end $$;
revoke all on function goserve_private.seryo_engine_ready() from public,anon,authenticated,service_role;

create function public.get_seryo_provider_configuration() returns jsonb language plpgsql security definer set search_path='' as $$
declare transport text; result jsonb;
begin
 if not goserve_private.seryo_engine_ready() then perform goserve_private.fail('PROVIDER_UNAVAILABLE');end if;
 select value->>'transport' into transport from goserve_private.policies where name='seryo_gateway';
 if transport='egovai-engine-v1' then
  result:=public.get_seryo_engine_configuration();
 elsif transport='platforms-ai-assistant-v1' then
  execute $q$select jsonb_build_object('base_url',c.base_url,'access_code',s.decrypted_secret,'integration_enabled',true)
  from goserve_private.egovai_configuration c join vault.decrypted_secrets s on s.id=c.access_code_secret_id
  where c.provider='egovai' and c.integration_enabled and c.base_url='https://platforms-api.e.gov.ph/egov-ai' and s.name='goserve_egovai_access_code' and length(s.decrypted_secret)>0$q$ into result;
 else perform goserve_private.fail('PROVIDER_UNAVAILABLE');end if;
 if result is null then perform goserve_private.fail('PROVIDER_UNAVAILABLE');end if;
 return result||jsonb_build_object('transport',transport);
end $$;
revoke all on function public.get_seryo_provider_configuration() from public,anon,authenticated;
grant execute on function public.get_seryo_provider_configuration() to service_role;
commit;
