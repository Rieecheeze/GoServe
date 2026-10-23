-- Run after cleanup_worker_authority and cleanup-files are deployed. No credentials are returned.
begin;
create extension if not exists pg_cron;
create extension if not exists pg_net with schema extensions;
revoke all on table net._http_response,net.http_request_queue from public,anon,authenticated;
do $$
declare token text; existing bigint;
begin
 select decrypted_secret into token from vault.decrypted_secrets where name='goserve_cleanup_worker_token';
 if token is null then
  token:=gen_random_uuid()::text||gen_random_uuid()::text;
  perform vault.create_secret(token,'goserve_cleanup_worker_token','GoServe cleanup worker only');
 end if;
 insert into goserve_private.policies(name,value)
 values('cleanup_worker',jsonb_build_object('token_digest',encode(sha256(convert_to(token,'UTF8')),'hex'),'lease_seconds',60))
 on conflict(name) do update set value=excluded.value;
 select jobid into existing from cron.job where jobname='goserve-private-file-cleanup';
 if existing is not null then perform cron.unschedule(existing); end if;
 perform cron.schedule('goserve-private-file-cleanup','*/5 * * * *',$job$
  select net.http_post(
   url:='https://uuvexkewiuxdbuelwogy.supabase.co/functions/v1/cleanup-files',
   headers:=jsonb_build_object('Content-Type','application/json','Authorization','Bearer '||(select decrypted_secret from vault.decrypted_secrets where name='goserve_cleanup_worker_token')),
   body:='{}'::jsonb,timeout_milliseconds:=10000);
 $job$);
end $$;
commit;
select jsonb_build_object('scheduled',exists(select 1 from cron.job where jobname='goserve-private-file-cleanup' and active),'credential_stored',exists(select 1 from vault.secrets where name='goserve_cleanup_worker_token')) as cleanup_setup;
