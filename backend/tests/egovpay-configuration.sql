-- Run with a database owner connection after applying egovpay-configuration.sql.
-- The existing configuration is changed only inside this transaction and restored by ROLLBACK.
-- If an assertion fails, issue ROLLBACK in the same session before continuing.
begin;
set local plpgsql.check_asserts = on;
set local row_security = on;

do $fixture$
declare
    fixture_secret uuid;
begin
    fixture_secret := vault.create_secret(
        'unit-test-token',
        'goserve_egovpay_configuration_test_' || pg_catalog.gen_random_uuid()::text,
        'Rollback-only GoServe test fixture'
    );

    insert into goserve_private.egovpay_configuration(
        provider, base_url, settlement_template_uuid, token_secret_id, demo_only, integration_enabled
    ) values (
        'egovpay', 'https://example.invalid', '00000000-0000-4000-8000-000000000201',
        fixture_secret, true, false
    )
    on conflict (provider) do update
    set base_url = excluded.base_url,
        settlement_template_uuid = excluded.settlement_template_uuid,
        token_secret_id = excluded.token_secret_id,
        demo_only = excluded.demo_only,
        integration_enabled = excluded.integration_enabled,
        updated_at = pg_catalog.clock_timestamp();

    assert not pg_catalog.has_schema_privilege('anon', 'goserve_private', 'USAGE'),
        'Anonymous clients must not have private schema access.';
    assert not pg_catalog.has_schema_privilege('authenticated', 'goserve_private', 'USAGE'),
        'Authenticated clients must not have private schema access.';
    assert not pg_catalog.has_function_privilege('anon', 'public.get_egovpay_server_configuration()', 'EXECUTE'),
        'Anonymous clients must not execute the configuration RPC.';
    assert not pg_catalog.has_function_privilege('authenticated', 'public.get_egovpay_server_configuration()', 'EXECUTE'),
        'Authenticated clients must not execute the configuration RPC.';
    assert pg_catalog.has_function_privilege('service_role', 'public.get_egovpay_server_configuration()', 'EXECUTE'),
        'The server role must be able to execute the configuration RPC.';
end;
$fixture$;

set local role anon;
do $test$
begin
    begin
        perform public.get_egovpay_server_configuration();
        raise exception 'Anonymous configuration RPC access was not denied.';
    exception when insufficient_privilege then null;
    end;
    begin
        perform 1 from goserve_private.egovpay_configuration;
        raise exception 'Anonymous configuration table access was not denied.';
    exception when insufficient_privilege then null;
    end;
end;
$test$;

reset role;
set local role authenticated;
do $test$
begin
    begin
        perform public.get_egovpay_server_configuration();
        raise exception 'Authenticated configuration RPC access was not denied.';
    exception when insufficient_privilege then null;
    end;
    begin
        perform 1 from goserve_private.egovpay_configuration;
        raise exception 'Authenticated configuration table access was not denied.';
    exception when insufficient_privilege then null;
    end;
end;
$test$;

reset role;
set local role service_role;
do $test$
declare
    configuration jsonb;
begin
    configuration := public.get_egovpay_server_configuration();
    assert configuration ->> 'base_url' = 'https://example.invalid',
        'The server must retrieve the synthetic base URL.';
    assert configuration ->> 'settlement_template_uuid' = '00000000-0000-4000-8000-000000000201',
        'The server must retrieve the synthetic settlement template.';
    assert configuration ->> 'token' = 'unit-test-token',
        'The server must retrieve only the synthetic test secret.';
    assert (configuration ->> 'demo_only')::boolean,
        'The test configuration must remain demo only.';
    assert not (configuration ->> 'integration_enabled')::boolean,
        'The test configuration must remain disabled.';
    assert (select pg_catalog.count(*) from goserve_private.egovpay_configuration) = 1,
        'The provider configuration must be a singleton.';
    begin
        update goserve_private.egovpay_configuration set integration_enabled = true;
        raise exception 'The read-only server role was able to alter configuration.';
    exception when insufficient_privilege then null;
    end;
end;
$test$;

reset role;
select 'PASS: eGovPay server-only configuration and synthetic Vault retrieval' as result;
rollback;
