begin;

alter table public.profiles
    add column avatar_path text,
    add column avatar_version bigint not null default 0 check(avatar_version>=0),
    add constraint profiles_avatar_owner check(avatar_path is null or avatar_path ~ ('^'||id::text||'/[a-f0-9-]{36}\.jpg$'));

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('profile-avatars','profile-avatars',false,1048576,array['image/jpeg']);

create policy avatar_bucket_no_update on storage.buckets as restrictive for update to authenticated
    using(id<>'profile-avatars') with check(id<>'profile-avatars');
create policy avatar_bucket_no_delete on storage.buckets as restrictive for delete to authenticated
    using(id<>'profile-avatars');
create policy avatar_bucket_no_create on storage.buckets as restrictive for insert to authenticated
    with check(id<>'profile-avatars');

create table goserve_private.avatar_uploads(
    path text primary key,
    owner_id uuid not null references public.profiles(id),
    expected_version bigint not null,
    state text not null default 'pending' check(state in('pending','current','retired')),
    created_at timestamptz not null default now()
);
create unique index avatar_one_current on goserve_private.avatar_uploads(owner_id) where state='current';
create index avatar_upload_owner on goserve_private.avatar_uploads(owner_id,state);
alter table goserve_private.avatar_uploads enable row level security;
revoke all on goserve_private.avatar_uploads from public,anon,authenticated;
grant all on goserve_private.avatar_uploads to service_role;

create function public.profile_avatar() returns jsonb language plpgsql security definer set search_path='' as $$
declare p public.profiles; obsolete jsonb;
begin
    p:=goserve_private.actor();
    select coalesce(jsonb_agg(x.path),'[]'::jsonb) into obsolete from (
        select a.path from goserve_private.avatar_uploads a
        where a.owner_id=p.id and a.state='retired'
          and exists(select 1 from storage.objects o where o.bucket_id='profile-avatars' and o.name=a.path)
        order by a.created_at,a.path limit 20
    ) x;
    return jsonb_build_object('avatar_path',p.avatar_path,'avatar_version',p.avatar_version,'obsolete_paths',obsolete);
end$$;

create function public.prepare_profile_avatar(p_expected_version bigint,p_idempotency_key uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare p public.profiles; cached jsonb; path text;
begin
    p:=goserve_private.actor();
    select * into p from public.profiles where id=p.id for update;
    cached:=goserve_private.begin_operation('avatar_prepare',p_idempotency_key,to_jsonb(p_expected_version));
    if cached is not null then return cached;end if;
    if p.avatar_version is distinct from p_expected_version then perform goserve_private.fail('STALE_VERSION');end if;
    path:=p.id::text||'/'||gen_random_uuid()::text||'.jpg';
    insert into goserve_private.avatar_uploads(path,owner_id,expected_version) values(path,p.id,p.avatar_version);
    return goserve_private.end_operation('avatar_prepare',p_idempotency_key,jsonb_build_object('path',path,'expected_version',p.avatar_version));
end$$;

create function public.set_profile_avatar(p_path text,p_expected_version bigint,p_idempotency_key uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare p public.profiles; cached jsonb; metadata jsonb;
begin
    p:=goserve_private.actor();
    select * into p from public.profiles where id=p.id for update;
    cached:=goserve_private.begin_operation('avatar_set',p_idempotency_key,jsonb_build_array(p_path,p_expected_version));
    if cached is not null then return public.profile_avatar();end if;
    if p.avatar_version is distinct from p_expected_version then perform goserve_private.fail('STALE_VERSION');end if;
    if p_path is not null then
        if not exists(select 1 from goserve_private.avatar_uploads a where a.path=p_path and a.owner_id=p.id and a.state='pending' and a.expected_version=p_expected_version)
        then perform goserve_private.fail('UPLOAD_INVALID');end if;
        select o.metadata into metadata from storage.objects o where o.bucket_id='profile-avatars' and o.name=p_path;
        if metadata is null or metadata->>'mimetype' is distinct from 'image/jpeg'
            or jsonb_typeof(metadata->'size') is distinct from 'number'
        then perform goserve_private.fail('UPLOAD_INVALID');end if;
        if (metadata->>'size')::numeric<=0 or (metadata->>'size')::numeric>1048576
        then perform goserve_private.fail('UPLOAD_INVALID');end if;
    end if;
    -- Retired paths never become current again, so delayed Storage cleanup cannot delete a later photo.
    update goserve_private.avatar_uploads set state='retired' where owner_id=p.id and state='current';
    if p_path is not null then update goserve_private.avatar_uploads set state='current' where path=p_path;end if;
    update public.profiles set avatar_path=p_path,avatar_version=avatar_version+1,updated_at=now() where id=p.id;
    return goserve_private.end_operation('avatar_set',p_idempotency_key,public.profile_avatar());
end$$;

create function public.discard_profile_avatar(p_path text) returns jsonb language plpgsql security definer set search_path='' as $$
declare p public.profiles; state text;
begin
    p:=goserve_private.actor();
    perform 1 from public.profiles where id=p.id for update;
    select a.state into state from goserve_private.avatar_uploads a where a.path=p_path and a.owner_id=p.id;
    if not found then perform goserve_private.fail('NOT_FOUND');end if;
    if state='current' then perform goserve_private.fail('CONFLICT');end if;
    update goserve_private.avatar_uploads set state='retired' where path=p_path;
    return jsonb_build_object('retired',true);
end$$;

create function goserve_private.avatar_storage_access(p_path text,p_action text) returns boolean language sql stable security definer set search_path='' as $$
    select goserve_private.ready() and exists(
        select 1 from goserve_private.avatar_uploads a where a.path=p_path and a.owner_id=auth.uid()
        and case p_action when 'read' then true when 'insert' then a.state='pending' when 'delete' then a.state='retired' else false end
    )
$$;
revoke all on function goserve_private.avatar_storage_access(text,text) from public,anon;
grant execute on function goserve_private.avatar_storage_access(text,text) to authenticated;

create policy avatar_scope on storage.objects as restrictive for all to authenticated
    using(bucket_id<>'profile-avatars' or goserve_private.avatar_storage_access(name,'read'))
    with check(bucket_id<>'profile-avatars' or goserve_private.avatar_storage_access(name,'insert'));
create policy avatar_no_overwrite on storage.objects as restrictive for update to authenticated
    using(bucket_id<>'profile-avatars') with check(bucket_id<>'profile-avatars');
create policy avatar_retired_delete on storage.objects as restrictive for delete to authenticated
    using(bucket_id<>'profile-avatars' or goserve_private.avatar_storage_access(name,'delete'));
create policy avatar_read on storage.objects for select to authenticated
    using(bucket_id='profile-avatars' and goserve_private.avatar_storage_access(name,'read'));
create policy avatar_insert on storage.objects for insert to authenticated
    with check(bucket_id='profile-avatars' and goserve_private.avatar_storage_access(name,'insert'));
create policy avatar_delete on storage.objects for delete to authenticated
    using(bucket_id='profile-avatars' and goserve_private.avatar_storage_access(name,'delete'));

revoke all on function public.profile_avatar(),public.prepare_profile_avatar(bigint,uuid),public.set_profile_avatar(text,bigint,uuid),public.discard_profile_avatar(text) from public,anon;
grant execute on function public.profile_avatar(),public.prepare_profile_avatar(bigint,uuid),public.set_profile_avatar(text,bigint,uuid),public.discard_profile_avatar(text) to authenticated;
commit;
