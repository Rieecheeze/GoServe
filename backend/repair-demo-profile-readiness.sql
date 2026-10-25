begin;

do $$
declare
  approved_ids uuid[];
begin
  select array_agg(id) into approved_ids
  from public.profiles
  where is_demo and (
    (email='registrar@demo.goserve.test' and role='staff' and office_id='OFF-REG' and staff_capability is null)
    or (email='accounting@demo.goserve.test' and role='staff' and office_id='OFF-ACC' and staff_capability is null)
    or (email='teller@demo.goserve.test' and role='teller' and office_id='OFF-CASH' and staff_capability is null)
    or (email='guidance@demo.goserve.test' and role='staff' and office_id='OFF-GCC' and staff_capability='guidance')
    or (email='secretary.ccs@demo.goserve.test' and role='staff' and office_id='DEP-CCS'
      and staff_capability='secretary' and department_id='DEP-CCS')
  );
  if coalesce(cardinality(approved_ids),0) <> 5 then
    raise exception 'Approved demo staff roster does not match; no readiness changes applied';
  end if;

  update public.profiles
  set profile_completed_at=now(), updated_at=now(), version=version+1
  where id=any(approved_ids) and is_demo and profile_completed_at is null;
end $$;

-- Staff readiness does not stand in for anyone's Terms or AI consent.
-- The CCS student completes their own profile after accepting the displayed Terms.
commit;
