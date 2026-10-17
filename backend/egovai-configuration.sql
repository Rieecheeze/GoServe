begin;

create table goserve_private.egovai_configuration (
    provider text primary key default 'egovai' check (provider = 'egovai'),
    base_url text not null check (base_url ~ '^https://[A-Za-z0-9][A-Za-z0-9.-]*(:[0-9]{1,5})?(/[^[:space:]?#]*)?$'),
    access_code_secret_id uuid not null references vault.secrets(id),
    agent_name text not null default 'Seryo',
    procedures text not null default '',
    integration_enabled boolean not null default false,
    updated_at timestamptz not null default now()
);

alter table goserve_private.egovai_configuration enable row level security;
revoke all on table goserve_private.egovai_configuration from public, anon, authenticated, service_role;
grant select on table goserve_private.egovai_configuration to service_role;
create policy egovai_configuration_deny_clients on goserve_private.egovai_configuration
    as restrictive for all to anon, authenticated using (false) with check (false);
create policy egovai_configuration_server_read on goserve_private.egovai_configuration
    for select to service_role using (true);

create function public.get_egovai_server_configuration()
returns jsonb language plpgsql security definer set search_path = '' as $function$
declare configuration jsonb;
begin
    select pg_catalog.jsonb_build_object(
        'base_url', settings.base_url, 'access_code', secret.decrypted_secret,
        'agent_name', settings.agent_name, 'procedures', settings.procedures,
        'integration_enabled', settings.integration_enabled
    ) into configuration
    from goserve_private.egovai_configuration settings
    join vault.decrypted_secrets secret on secret.id = settings.access_code_secret_id
    where settings.provider = 'egovai' and secret.decrypted_secret <> '';
    if configuration is null then
        raise exception 'Seryo server configuration is unavailable.' using errcode = '55000';
    end if;
    return configuration;
end;
$function$;
revoke all on function public.get_egovai_server_configuration() from public, anon, authenticated, service_role;
grant execute on function public.get_egovai_server_configuration() to service_role;

commit;
