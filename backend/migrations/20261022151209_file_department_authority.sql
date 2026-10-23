begin;

create or replace function public.file_server_operation(p_actor uuid,p_file_id uuid,p_command text,p_sha256 text default null) returns jsonb language plpgsql security definer set search_path='' as $$
declare
 f public.request_files;
 o goserve_private.file_objects;
 r public.requests;
 a public.profiles;
begin
 select * into a from public.profiles where id=p_actor;
 if not found or a.profile_completed_at is null then perform goserve_private.fail('FORBIDDEN');end if;
 select * into f from public.request_files where id=p_file_id;
 if not found then perform goserve_private.fail('NOT_FOUND');end if;
 -- Keep request-before-file lock order identical to prepare/release operations.
 if f.request_id is not null then select * into r from public.requests where id=f.request_id for update;end if;
 select * into f from public.request_files where id=p_file_id for update;
 select * into o from goserve_private.file_objects where file_id=f.id;
 if p_command='read' then
   if f.kind<>'evidence' or f.verification<>'verified' or f.deleted_at is not null then perform goserve_private.fail('UPLOAD_INVALID');end if;
   if f.owner_id<>p_actor and (
     r.id is null or a.role<>'staff' or a.office_id is distinct from r.office_id
     or (r.office_id like 'DEP-%' and a.staff_capability is distinct from 'secretary')
     or (a.staff_capability is not null and not exists(
       select 1 from goserve_private.capabilities where profile_id=p_actor and office_id=r.office_id and capability='requests'
     ))
   ) then perform goserve_private.fail('FORBIDDEN');end if;
   return to_jsonb(f)||jsonb_build_object('object_path',o.object_path);
 end if;
 if f.kind='evidence' then
   if f.owner_id<>p_actor or f.request_id is not null then perform goserve_private.fail('FORBIDDEN');end if;
 else
   if r.id is null or a.role<>'staff' or a.office_id is distinct from r.office_id
     or (r.office_id like 'DEP-%' and a.staff_capability is distinct from 'secretary')
     or (a.staff_capability is not null and not exists(
       select 1 from goserve_private.capabilities where profile_id=p_actor and office_id=r.office_id and capability='requests'
     )) or r.status not in('Processing','Ready for Pickup/Download') then perform goserve_private.fail('FORBIDDEN');end if;
 end if;
 if f.deleted_at is not null or o.expires_at<=now() then perform goserve_private.fail('UPLOAD_INVALID');end if;
 if p_command='verified' then
   if f.verification='verified' then
     if o.sha256 is distinct from p_sha256 then perform goserve_private.fail('UPLOAD_INVALID');end if;
     return to_jsonb(f);
   end if;
   if p_sha256 is null or p_sha256!~'^[a-f0-9]{64}$' then perform goserve_private.fail('UPLOAD_INVALID');end if;
   if f.kind='deliverable' then
     update public.request_files set current=false where request_id=f.request_id and kind='deliverable' and verification='verified' and current and id<>f.id;
   end if;
   update public.request_files set verification='verified',current=true where id=f.id returning * into f;
   update goserve_private.file_objects set sha256=p_sha256,verified_at=now() where file_id=f.id;
   return to_jsonb(f);
 elsif p_command<>'authorize' then perform goserve_private.fail('VALIDATION_FAILED');end if;
 return to_jsonb(f)||jsonb_build_object('object_path',o.object_path,'expires_at',o.expires_at,'policy',(select value from goserve_private.policies where name='file_'||f.service_id::text||'_'||f.kind));
end$$;
revoke all on function public.file_server_operation(uuid,uuid,text,text) from public,anon,authenticated;
grant execute on function public.file_server_operation(uuid,uuid,text,text) to service_role;

commit;
