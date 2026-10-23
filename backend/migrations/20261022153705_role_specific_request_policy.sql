begin;
-- A role override is complete and never falls back to a broader global rule.
create or replace function public.transition_request(p_request_id uuid,p_expected_version bigint,p_target_status text,p_reason text,p_idempotency_key uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare a public.profiles;r public.requests;cached jsonb;policy jsonb;old_status text;
begin
 a:=goserve_private.actor();r:=goserve_private.request_row(p_request_id);
 cached:=goserve_private.begin_operation('transition',p_idempotency_key,jsonb_build_array(p_request_id,p_expected_version,p_target_status,p_reason));if cached is not null then return cached;end if;
 if p_target_status not in('Processing','Ready for Pickup/Download','Cancelled','Rejected') then perform goserve_private.fail('FORBIDDEN');end if;
 if r.version is distinct from p_expected_version then perform goserve_private.fail('STALE_VERSION');end if;
 if r.terminal_at is not null then perform goserve_private.fail('INVALID_STATE');end if;
 if p_target_status in('Cancelled','Rejected') then
 select value into policy from goserve_private.policies where name='request_'||lower(p_target_status);
 if policy ? 'role_rules' then policy:=case when jsonb_typeof(policy->'role_rules')='object' then policy->'role_rules'->a.role else null end;end if;
 if policy is null then perform goserve_private.fail('CONFIGURATION_REQUIRED');end if;
 if not coalesce(policy->'roles' ? a.role,false) or not coalesce(policy->'states' ? r.status,false) or policy->>'window_seconds' is null or now()>r.created_at+make_interval(secs=>(policy->>'window_seconds')::double precision) then perform goserve_private.fail('POLICY_BLOCKED');end if;
 if p_target_status='Rejected' and not goserve_private.capable(r.office_id,'requests') then perform goserve_private.fail('FORBIDDEN');end if;
 if nullif(btrim(p_reason),'') is null then perform goserve_private.fail('VALIDATION_FAILED');end if;
 else
 if not goserve_private.capable(r.office_id,'requests') then perform goserve_private.fail('FORBIDDEN');end if;
 if not goserve_private.paid(r.id) then perform goserve_private.fail('PAYMENT_REQUIRED');end if;
 if (p_target_status='Processing' and r.status not in('Submitted','Awaiting Payment')) or (p_target_status='Ready for Pickup/Download' and r.status<>'Processing') then perform goserve_private.fail('INVALID_STATE');end if;
 if p_target_status='Ready for Pickup/Download' and r.delivery_mode='digital' and not goserve_private.has_deliverable(r.id) then perform goserve_private.fail('UPLOAD_INVALID');end if;end if;
 old_status:=r.status;
 update public.requests set status=p_target_status,version=version+1,terminal_at=case when p_target_status in('Cancelled','Rejected') then now() else null end where id=r.id returning * into r;
 perform goserve_private.request_event(r.id,old_status,r.status,p_reason);
 return goserve_private.end_operation('transition',p_idempotency_key,to_jsonb(r));
end$$;

create or replace function public.workflow_options(p_quote_id uuid default null,p_request_id uuid default null)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare
  a public.profiles;
  q public.request_quotes;
  s public.services;
  r public.requests;
  result jsonb;
  actions jsonb := '{}'::jsonb;
  policy jsonb;
  file_option jsonb;
  mime_types jsonb;
  file_kind text;
  action_name text;
  blocker text;
  staff_access boolean;
  seconds double precision;
  deadline timestamptz;
  validity integer;
  replace_expired boolean;
  max_bytes numeric;
  max_pixels numeric;
begin
  a := goserve_private.actor();
  if (p_quote_id is null) = (p_request_id is null) then
    perform goserve_private.fail('VALIDATION_FAILED');
  end if;

  if p_quote_id is not null then
    if a.role <> 'student' then perform goserve_private.fail('FORBIDDEN'); end if;
    select * into q from public.request_quotes where id = p_quote_id and owner_id = a.id;
    if not found then perform goserve_private.fail('NOT_FOUND'); end if;
    select * into s from public.services where id = q.service_id;
    if not found or not s.active or s.version <> q.service_version
      or not q.delivery_mode = any(s.delivery_modes) then
      perform goserve_private.fail('QUOTE_STALE');
    end if;

    blocker := 'CONFIGURATION_REQUIRED';
    if s.fee_state = 'free' then
      blocker := 'POLICY_BLOCKED';
    elsif s.fee_state = 'required' and exists (
      select 1 from goserve_private.policies where name = 'cash_offices' and value ? s.office_id
    ) then
      blocker := null;
    end if;
    result := jsonb_build_object('quote_id',q.id,'service_version',s.version,
      'cash',jsonb_build_object('available',blocker is null,'blocker',blocker));
    file_kind := 'evidence';
  else
    select * into r from public.requests
      where id = p_request_id and (owner_id = a.id or goserve_private.capable(office_id,'requests'));
    if not found then perform goserve_private.fail('NOT_FOUND'); end if;
    staff_access := goserve_private.capable(r.office_id,'requests');

    foreach action_name in array ARRAY['cancel','reject'] loop
      if (action_name = 'cancel' and (r.owner_id = a.id or staff_access))
        or (action_name = 'reject' and staff_access) then
        blocker := 'CONFIGURATION_REQUIRED';
        if r.terminal_at is not null or r.status in ('Completed','Cancelled','Rejected') then
          blocker := 'INVALID_STATE';
        else
          select value into policy from goserve_private.policies
            where name = case action_name when 'cancel' then 'request_cancelled' else 'request_rejected' end;
            if policy ? 'role_rules' then policy:=case when jsonb_typeof(policy->'role_rules')='object' then policy->'role_rules'->a.role else null end;end if;
          begin
            if jsonb_typeof(policy) = 'object'
              and jsonb_typeof(policy->'roles') in ('array','object','string')
              and jsonb_typeof(policy->'states') in ('array','object','string')
              and policy->>'window_seconds' is not null then
              seconds := (policy->>'window_seconds')::double precision;
              deadline := r.created_at + make_interval(secs => seconds);
              if seconds >= 0 and isfinite(deadline) then
                blocker := case when policy->'roles' ? a.role and policy->'states' ? r.status
                  and now() <= deadline then null else 'POLICY_BLOCKED' end;
              end if;
            end if;
          exception when data_exception then
            blocker := 'CONFIGURATION_REQUIRED';
          end;
        end if;
        actions := actions || jsonb_build_object(action_name,
          jsonb_build_object('available',blocker is null,'blocker',blocker));
      end if;
    end loop;

    if r.owner_id = a.id then
      blocker := 'CONFIGURATION_REQUIRED';
      if r.delivery_mode <> 'physical' or r.status <> 'Ready for Pickup/Download' then
        blocker := 'INVALID_STATE';
      elsif not goserve_private.paid(r.id) then
        blocker := 'PAYMENT_REQUIRED';
      else
        select value into policy from goserve_private.policies where name = 'pickup';
        begin
          if jsonb_typeof(policy) = 'object' then
            validity := (policy->>'validity_seconds')::integer;
            replace_expired := coalesce((policy->>'replace_expired')::boolean,false);
            if validity > 0 then
              blocker := case when not replace_expired and exists (
                select 1 from goserve_private.pickup_claims
                where request_id = r.id and active and expires_at <= now()
              ) then 'POLICY_BLOCKED' else null end;
            end if;
          end if;
        exception when data_exception then
          blocker := 'CONFIGURATION_REQUIRED';
        end;
      end if;
      actions := actions || jsonb_build_object('pickup_claim',
        jsonb_build_object('available',blocker is null,'blocker',blocker));
    end if;

    if staff_access then
      select * into s from public.services where id = r.service_id and active;
      if found and r.delivery_mode = 'digital' and r.status in ('Processing','Ready for Pickup/Download') then
        file_kind := 'deliverable';
      else
        actions := actions || jsonb_build_object('deliverable_upload',jsonb_build_object(
          'available',false,'blocker','INVALID_STATE','mime_types',null,'max_bytes',null));
      end if;
    end if;
    result := jsonb_build_object('request_id',r.id,'request_version',r.version,'actions',actions);
  end if;

  if file_kind is not null then
    file_option := jsonb_build_object('available',false,'blocker','CONFIGURATION_REQUIRED',
      'mime_types',null,'max_bytes',null);
    select value into policy from goserve_private.policies where name = 'file_' || s.id::text || '_' || file_kind;
    begin
      if jsonb_typeof(policy) = 'object' and policy->>'verifier' = 'goserve-v1'
        and jsonb_typeof(policy->'mime_types') = 'array'
        and jsonb_typeof(policy->'max_bytes') = 'number' and policy->>'upload_seconds' is not null then
        max_bytes := (policy->>'max_bytes')::numeric;
        seconds := (policy->>'upload_seconds')::double precision;
        deadline := now() + make_interval(secs => seconds);
        select jsonb_agg(choice.mime order by choice.ordinal) into mime_types from (
          select mime,min(ordinal) ordinal
          from jsonb_array_elements_text(policy->'mime_types') with ordinality as configured(mime,ordinal)
          where mime = 'application/pdf' or (file_kind = 'evidence' and mime in ('image/png','image/jpeg'))
          group by mime
        ) choice;
        if max_bytes between 1 and 9007199254740991 and trunc(max_bytes) = max_bytes
          and seconds > 0 and isfinite(deadline) and mime_types is not null then
          if mime_types ? 'image/png' or mime_types ? 'image/jpeg' then
            if jsonb_typeof(policy->'max_pixels') = 'number' then
              max_pixels := (policy->>'max_pixels')::numeric;
            else
              max_pixels := null;
            end if;
          else
            max_pixels := 1;
          end if;
          if max_pixels between 1 and 9007199254740991 and trunc(max_pixels) = max_pixels then
            file_option := jsonb_build_object('available',true,'blocker',null,
              'mime_types',mime_types,'max_bytes',max_bytes::bigint);
          end if;
        end if;
      end if;
    exception when data_exception then
      file_option := jsonb_build_object('available',false,'blocker','CONFIGURATION_REQUIRED',
        'mime_types',null,'max_bytes',null);
    end;
    if file_kind = 'evidence' then
      result := result || jsonb_build_object('evidence',file_option);
    else
      result := result || jsonb_build_object('actions',actions || jsonb_build_object('deliverable_upload',file_option));
    end if;
  end if;
  return result;
end;
$$;
revoke all on function public.transition_request(uuid,bigint,text,text,uuid) from public,anon;
grant execute on function public.transition_request(uuid,bigint,text,text,uuid) to authenticated;
revoke all on function public.workflow_options(uuid,uuid) from public,anon;
grant execute on function public.workflow_options(uuid,uuid) to authenticated;
commit;
