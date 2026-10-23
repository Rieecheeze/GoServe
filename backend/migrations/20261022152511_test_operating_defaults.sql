begin;
create function goserve_private.apply_demo_file_defaults() returns trigger language plpgsql security definer set search_path='' as $$
declare defaults jsonb;kind text;policy jsonb;
begin
 if new.is_demo is distinct from true then return new;end if;
 select value into defaults from goserve_private.policies where name='demo_file_defaults';
 if defaults is null or jsonb_typeof(defaults)<>'object' then return new;end if;
 foreach kind in array ARRAY['evidence','deliverable'] loop
   policy:=defaults->kind;
   if policy is not null and jsonb_typeof(policy)='object' then
     insert into goserve_private.policies(name,value) values('file_'||new.id::text||'_'||kind,policy) on conflict(name) do nothing;
   end if;
 end loop;
 return new;
end$$;
revoke all on function goserve_private.apply_demo_file_defaults() from public,anon,authenticated;
create trigger demo_file_defaults after insert or update on public.services for each row execute function goserve_private.apply_demo_file_defaults();
commit;
