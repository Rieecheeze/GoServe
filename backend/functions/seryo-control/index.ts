import { serve,json,authenticated,DomainError } from '../_shared/runtime.ts';
import { exact,rpc } from '../../lib/v23.ts';
serve(async(request,{admin,userClient})=>{
  const {client}=await authenticated(request,admin,userClient);
  const body=await request.json();
  if(body.kind==='readiness'){exact(body,['kind']);return json(await rpc(client,'seryo_state'));}
  if(body.kind==='consent'){
    exact(body,['kind','decision','expected_version','idempotency_key']);
    return json(await rpc(client,'set_ai_consent',{p_decision:body.decision,p_expected_version:body.expected_version,p_idempotency_key:body.idempotency_key}));
  }
  // No attachment can exist while the provider/file protocol remains unavailable.
  throw new DomainError(body.kind==='discard_attachment'?'NOT_FOUND':'VALIDATION_FAILED');
});
