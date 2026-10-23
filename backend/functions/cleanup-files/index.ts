import { serve,json,DomainError } from '../_shared/runtime.ts';
import { cleanOne,rpc } from '../../lib/v23.ts';
serve(async(request,{admin})=>{
  const token=request.headers.get('Authorization')?.match(/^Bearer ([^\s]{16,256})$/)?.[1];
  if(!token)throw new DomainError('FORBIDDEN');
  const configuration=await rpc(admin,'cleanup_worker_configuration',{p_token:token});
  return json(await cleanOne(admin,configuration.lease_seconds));
});
