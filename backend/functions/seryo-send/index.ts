import { serve,authenticated,json,DomainError } from '../_shared/runtime.ts';
import { exact,rpc } from '../../lib/v23.ts';
import { readBounded } from '../../lib/files.ts';
import { prepareSeryoPrompt,seryoIdentityReply,validateSeryoReply,SeryoGuardError,SERYO_REFUSAL } from '../../lib/seryo-guardrails.ts';
import { generateSeryoEngine } from '../../lib/egovai-engine.ts';
import { generateSeryo } from '../../lib/egovai.ts';
serve(async(request,{admin,userClient})=>{
  const {client,user}=await authenticated(request,admin,userClient);
  const size=Number(request.headers.get('Content-Length'));
  if(!Number.isSafeInteger(size)||size<1||size>10000)throw new DomainError('VALIDATION_FAILED');
  const raw=new TextDecoder('utf-8',{fatal:true}).decode(await readBounded(request.body,size));
  const body=JSON.parse(raw);exact(body,['question','idempotency_key']);
  if(typeof body.idempotency_key!=='string'||!/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(body.idempotency_key))throw new DomainError('VALIDATION_FAILED');
  const context=await rpc(client,'seryo_context');
  const introduction=seryoIdentityReply(body.question);
  if(introduction!==null)return json({reply:introduction,refused:false});
  let prompt:string;
  try{prompt=prepareSeryoPrompt(body.question,context);}catch(error){
    if(error instanceof SeryoGuardError)return json({reply:SERYO_REFUSAL,refused:true});throw error;
  }
  const configuration=await rpc(admin,'get_seryo_provider_configuration');
  if(configuration.integration_enabled!==true)throw new DomainError('PROVIDER_UNAVAILABLE');
  if(!['platforms-ai-assistant-v1','egovai-engine-v1'].includes(configuration.transport))throw new DomainError('PROVIDER_UNAVAILABLE');
  if(configuration.transport==='platforms-ai-assistant-v1')prompt=prepareSeryoPrompt(body.question,context,true);
  const dispatch=await rpc(client,'seryo_dispatch_begin',{p_key:body.idempotency_key});
  let reply:string;
  try{
    try{reply=validateSeryoReply(await(configuration.transport==='platforms-ai-assistant-v1'?generateSeryo(configuration,prompt):generateSeryoEngine(configuration,prompt)));}
    catch(error){if(error instanceof SeryoGuardError)reply=SERYO_REFUSAL;else throw new DomainError('PROVIDER_UNAVAILABLE');}
  }finally{await rpc(admin,'seryo_dispatch_finish',{p_actor:user.id,p_operation:dispatch.operation_id});}
  const current=await rpc(client,'seryo_state');
  if(current.consent!=='allow'||current.consent_version!==dispatch.consent_version)throw new DomainError('CONSENT_REQUIRED');
  return json({reply,refused:reply===SERYO_REFUSAL});
});
