begin;

do $preflight$
begin
    if pg_catalog.to_regnamespace('goserve_private') is null then
        raise exception 'Install the GoServe foundation schema first.' using errcode = '3F000';
    end if;
    if pg_catalog.to_regclass('vault.secrets') is null
       or pg_catalog.to_regclass('vault.decrypted_secrets') is null then
        raise exception 'Supabase Vault must be installed before configuring eGovPay.' using errcode = '55000';
    end if;
    if pg_catalog.to_regclass('goserve_private.egovpay_configuration') is not null
       or exists (
           select 1 from pg_catalog.pg_proc as routine
           join pg_catalog.pg_namespace as namespace on namespace.oid = routine.pronamespace
           where namespace.nspname = 'public' and routine.proname = 'get_egovpay_server_configuration'
       ) then
        raise exception 'eGovPay configuration objects already exist. Review them before changing configuration.'
            using errcode = '42710';
    end if;
end;
$preflight$;

create table goserve_private.egovpay_configuration (
    provider text primary key default 'egovpay' check (provider = 'egovpay'),
    base_url text not null check (
        base_url ~ '^https://[A-Za-z0-9][A-Za-z0-9.-]*(:[0-9]{1,5})?(/[^[:space:]?#]*)?$'
    ),
    settlement_template_uuid uuid not null,
    token_secret_id uuid not null references vault.secrets(id),
    demo_only boolean not null default true,
    integration_enabled boolean not null default false,
    created_at timestamptz not null default pg_catalog.now(),
    updated_at timestamptz not null default pg_catalog.now()
);

alter table goserve_private.egovpay_configuration enable row level security;
revoke usage on schema goserve_private from public, anon, authenticated;
grant usage on schema goserve_private to service_role;
revoke all on table goserve_private.egovpay_configuration from public, anon, authenticated, service_role;
grant select on table goserve_private.egovpay_configuration to service_role;

create policy egovpay_configuration_deny_clients on goserve_private.egovpay_configuration
    as restrictive for all to anon, authenticated
    using (false) with check (false);

create policy egovpay_configuration_server_read on goserve_private.egovpay_configuration
    for select to service_role
    using (true);

create function public.get_egovpay_server_configuration()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $function$
declare
    configuration jsonb;
begin
    select pg_catalog.jsonb_build_object(
        'base_url', settings.base_url,
        'settlement_template_uuid', settings.settlement_template_uuid,
        'token', secret.decrypted_secret,
        'demo_only', settings.demo_only,
        'integration_enabled', settings.integration_enabled
    ) into configuration
    from goserve_private.egovpay_configuration as settings
    join vault.decrypted_secrets as secret on secret.id = settings.token_secret_id
    where settings.provider = 'egovpay' and secret.decrypted_secret <> '';

    if configuration is null then
        raise exception 'eGovPay server configuration is unavailable.' using errcode = '55000';
    end if;
    return configuration;
end;
$function$;

revoke all on function public.get_egovpay_server_configuration() from public, anon, authenticated, service_role;
grant execute on function public.get_egovpay_server_configuration() to service_role;

commit;
