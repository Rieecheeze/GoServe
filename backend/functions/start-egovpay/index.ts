import { serve,authenticated,DomainError } from '../_shared/runtime.ts';
import { exact,providerBoundary } from '../../lib/v23.ts';
serve(async(request,{admin,userClient})=>{
  const {client}=await authenticated(request,admin,userClient);
  const body=await request.json();exact(body,['payment_id','idempotency_key']);
  const {data,error}=await client.from('payments').select('id').eq('id',body.payment_id).maybeSingle();
  if(error||!data)throw new DomainError('NOT_FOUND');
  await providerBoundary(client,'egovpay');
  throw new Error('Provider unavailable');
});
