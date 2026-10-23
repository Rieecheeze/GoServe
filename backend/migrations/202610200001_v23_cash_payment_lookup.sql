begin;

create function public.cash_payment_by_id(p_payment_id uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
declare actor public.profiles; payment_reference text;
begin
    actor:=goserve_private.actor();
    if actor.role<>'teller' or actor.office_id is distinct from 'OFF-CASH' then
        perform goserve_private.fail('FORBIDDEN');
    end if;
    select r.reference into payment_reference
    from public.payments p join public.requests r on r.id=p.request_id
    where p.id=p_payment_id and p.method='cash';
    if not found then perform goserve_private.fail('NOT_FOUND'); end if;
    return public.cash_payment(payment_reference,false,null,null);
end;
$$;

revoke all on function public.cash_payment_by_id(uuid) from public,anon;
grant execute on function public.cash_payment_by_id(uuid) to authenticated;

commit;
