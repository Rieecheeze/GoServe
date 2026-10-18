import { serve,json,DomainError } from '../_shared/runtime.ts';
import { cleanOne } from '../../lib/v23.ts';
serve(async(request,{admin})=>{
  const secret=Deno.env.get('GOSERVE_CLEANUP_SECRET');
  const seconds=Number(Deno.env.get('GOSERVE_CLEANUP_LEASE_SECONDS'));
  if(!secret||!Number.isSafeInteger(seconds)||seconds<=0)throw new DomainError('CONFIGURATION_REQUIRED');
  if(request.headers.get('Authorization')!==`Bearer ${secret}`)throw new DomainError('FORBIDDEN');
  return json(await cleanOne(admin,seconds));
});
