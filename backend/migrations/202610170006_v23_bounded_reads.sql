begin;
create function public.unread_counts(p_thread_ids uuid[]) returns jsonb language plpgsql security invoker set search_path='' as $$
declare counts jsonb;total_messages bigint;total_notifications bigint;
begin
 if cardinality(p_thread_ids)>100 or p_thread_ids is null then raise exception 'VALIDATION_FAILED' using errcode='P0001';end if;
 select count(*) into total_notifications from public.notifications where read_at is null;
 select count(*) into total_messages from public.inquiry_messages m left join public.inquiry_reads r on r.thread_id=m.thread_id and r.profile_id=auth.uid() where m.sender_id<>auth.uid() and m.sequence>coalesce(r.through_sequence,0);
 select coalesce(jsonb_agg(to_jsonb(c)),'[]') into counts from (
 select t.id as thread_id,count(m.id) as unread_count from public.inquiry_threads t
 left join public.inquiry_reads r on r.thread_id=t.id and r.profile_id=auth.uid()
 left join public.inquiry_messages m on m.thread_id=t.id and m.sequence>coalesce(r.through_sequence,0) and m.sender_id<>auth.uid()
 where t.id=any(p_thread_ids) group by t.id) c;
 return jsonb_build_object('notifications',total_notifications,'messages',total_messages,'threads',counts);
end$$;
revoke all on function public.unread_counts(uuid[]) from public,anon;grant execute on function public.unread_counts(uuid[]) to authenticated;
create index message_sender_thread on public.inquiry_messages(thread_id,sequence,sender_id);
create index notifications_unread on public.notifications(recipient_id) where read_at is null;
commit;
