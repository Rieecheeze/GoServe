import { isIP } from 'node:net';
import { DomainError,exact,authenticated,readAuthBody,json } from './v23.ts';

const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const opaque=/^[A-Za-z0-9_-]{43}$/;
const safeErrors=new Set(['AUTH_REQUIRED','AUTH_INVALID','RECENT_AUTH_REQUIRED','RECOVERY_SETUP_REQUIRED',
  'RECOVERY_PROOF_INVALID','RECOVERY_GRANT_INVALID',
  'RECOVERY_OPERATION_PENDING','PROFILE_INCOMPLETE','TERMS_REQUIRED','RATE_LIMITED','VALIDATION_FAILED','PASSWORD_TOO_LONG','PASSWORD_WEAK',
  'TEMPORARILY_UNAVAILABLE','CONFIGURATION_REQUIRED','CONFLICT']);

export async function digest(value: string): Promise<string> {
  return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(value))))
    .map(byte=>byte.toString(16).padStart(2,'0')).join('');
}
function randomToken(): string {
  return btoa(String.fromCharCode(...crypto.getRandomValues(new Uint8Array(32)))).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
}
async function challenge(verifier: string): Promise<string> {
  const bytes=new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(verifier)));
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
}
function normalizeCode(value: string): string { return value.replace(/[\s-]/g,'').toUpperCase(); }
async function codeDigest(value: string): Promise<string> { return digest('goserve:recovery-code:v1:'+normalizeCode(value)); }
function base32(bytes: Uint8Array): string {
  const alphabet='ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';let buffer=0,bits=0,value='';
  for(const byte of bytes) {
    buffer=(buffer<<8)|byte;bits+=8;
    while(bits>=5) { bits-=5;value+=alphabet[(buffer>>>bits)&31]; }
  }
  if(bits)value+=alphabet[(buffer<<(5-bits))&31];
  return value;
}
export async function codeSet(): Promise<{codes:string[];digests:string[]}> {
  const codes=Array.from({length:10},()=>base32(crypto.getRandomValues(new Uint8Array(20))).match(/.{4}/g)!.join('-'));
  return {codes,digests:await Promise.all(codes.map(codeDigest))};
}
export function gatewayIp(request: Request,proxyHops?: number): string {
  // Zero activates only the hosted CF header after deployment verifies its provenance.
  if(proxyHops!==0)throw new DomainError('TEMPORARILY_UNAVAILABLE');
  const value=request.headers.get('cf-connecting-ip')?.trim()??'';
  if(!isIP(value))throw new DomainError('TEMPORARILY_UNAVAILABLE');
  return value;
}
async function operation(admin: any,command: string,args: any={}): Promise<any> {
  const {data,error}=await admin.rpc('recovery_server_operation',{p_command:command,p_args:args});
  if(error)throw new DomainError(safeErrors.has(error.message)?error.message:error.code==='23505'?'CONFLICT':'TEMPORARILY_UNAVAILABLE');
  return data;
}
function requireUuid(value: any): void { if(typeof value!=='string'||!uuid.test(value))throw new DomainError('VALIDATION_FAILED'); }
function password(value: any): void {
  if(typeof value!=='string'||[...value].length<6||!/\p{L}/u.test(value)||!/\p{Nd}/u.test(value)||!/[\p{P}\p{S}]/u.test(value))throw new DomainError('PASSWORD_WEAK');
  if(new TextEncoder().encode(value).length>72)throw new DomainError('PASSWORD_TOO_LONG');
}
async function proof(context: any,code: string): Promise<any> {
  const hash=await codeDigest(code);
  if(!context?.owner_id||!/^[A-Z2-7]{32}$/.test(normalizeCode(code)))throw new DomainError('RECOVERY_PROOF_INVALID');
  return {owner_id:context.owner_id,version:context.version,code_digest:hash};
}
async function owner(request: Request,deps: any,recent: boolean): Promise<any> {
  const actor=await authenticated(request,deps.admin,deps.userClient);
  const token=request.headers.get('Authorization')!.split(' ')[1];
  let claims: any;
  try { claims=JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(token.split('.')[1].replace(/-/g,'+').replace(/_/g,'/')),c=>c.charCodeAt(0)))); }
  catch { throw new DomainError('AUTH_INVALID'); }
  if(!uuid.test(claims.session_id??''))throw new DomainError('AUTH_INVALID');
  const timestamps=Array.isArray(claims.amr)?claims.amr.filter((entry:any)=>['password','oauth'].includes(entry?.method)&&typeof entry.timestamp==='number').map((entry:any)=>entry.timestamp):[];
  const authenticatedAt=timestamps.length?Math.max(...timestamps):0;
  if(recent&&(authenticatedAt<Date.now()/1000-300 || authenticatedAt>Date.now()/1000+60))throw new DomainError('RECENT_AUTH_REQUIRED');
  await operation(deps.admin,'session',{owner_id:actor.user.id,session_id:claims.session_id});
  return {id:actor.user.id,email:actor.user.email};
}
async function newCodes(admin: any,command: string,args: any): Promise<any> {
  for(let attempt=0;attempt<3;attempt++) {
    const codes=await codeSet();
    try {
      const result=await operation(admin,command,{...args,digests:codes.digests});
      return {...result,codes:codes.codes};
    }catch(error){if(!(error instanceof DomainError)||error.code!=='CONFLICT'||attempt===2)throw error;}
  }
  throw new DomainError('TEMPORARILY_UNAVAILABLE');
}

export async function recoveryControl(request: Request,deps: any): Promise<Response> {
  const body=await readAuthBody(request);
  const fields: Record<string,string[]>={status:['action'],generate_codes:['action'],acknowledge_codes:['action','generation']};
  if(!fields[body?.action])throw new DomainError('VALIDATION_FAILED');exact(body,fields[body.action]);
  const actor=await owner(request,deps,body.action==='generate_codes');
  if(body.action==='status')return json(await operation(deps.admin,'status',{owner_id:actor.id}));
  if(body.action==='generate_codes')return json(await newCodes(deps.admin,'generate',{owner_id:actor.id}));
  requireUuid(body.generation);
  return json(await operation(deps.admin,'ack',{owner_id:actor.id,generation:body.generation}));
}
async function reconcile(admin: any,job: any): Promise<any> {
  if(job.state==='completed'||job.state==='failed')return {state:job.state,operation_id:job.operation_id};
  try {
    const result=await admin.auth.admin.getUserById(job.owner_id);
    if(!result.error&&result.data?.user?.id===job.owner_id&&result.data.user.app_metadata?.goserve_recovery_op===job.operation_id) {
      await operation(admin,'finish',{operation_id:job.operation_id,state:'completed'});
      return {state:'completed',operation_id:job.operation_id};
    }
  }catch { /* A read failure cannot authorize another mutation. */ }
  return {state:'pending',operation_id:job.operation_id};
}
export async function accountRecovery(request: Request,deps: any): Promise<Response> {
  const body=await readAuthBody(request);
  if(body?.action==='verify') {
    exact(body,['action','email','code','code_challenge','code_challenge_method']);
    if(typeof body.code!=='string'||body.code.length>128)throw new DomainError('VALIDATION_FAILED');
    if(typeof body.email!=='string'||body.email.length>254||!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(body.email)
      ||!opaque.test(body.code_challenge??'')||body.code_challenge_method!=='s256')throw new DomainError('VALIDATION_FAILED');
    const context=await operation(deps.admin,'context',{email:body.email.trim().toLowerCase(),account_digest:await digest(body.email.trim().toLowerCase()),ip_digest:await digest(gatewayIp(request,deps.recoveryProxyHops))});
    if(context.blocked)throw new DomainError('RATE_LIMITED');
    if(context.configuration_required)throw new DomainError('CONFIGURATION_REQUIRED');
    const verified=await proof(context,body.code),grant=randomToken();
    const result=await operation(deps.admin,'redeem',{...verified,grant_digest:await digest('goserve:reset-grant:v1:'+grant),challenge:body.code_challenge});
    return json({reset_grant:grant,expires_at:result.expires_at});
  }
  if(!['reset','result'].includes(body?.action))throw new DomainError('VALIDATION_FAILED');
  exact(body,body.action==='reset'?['action','reset_grant','code_verifier','password','operation_id']:['action','reset_grant','code_verifier','operation_id']);
  requireUuid(body.operation_id);
  if(!opaque.test(body.reset_grant??'')||!opaque.test(body.code_verifier??''))throw new DomainError('RECOVERY_GRANT_INVALID');
  if(body.action==='reset')password(body.password);
  const args={grant_digest:await digest('goserve:reset-grant:v1:'+body.reset_grant),challenge:await challenge(body.code_verifier),operation_id:body.operation_id};
  const job=await operation(deps.admin,body.action==='reset'?'claim':'result',args);
  if(body.action==='result'||!job.should_mutate)return json(await reconcile(deps.admin,job));
  try {
    const result=await deps.admin.auth.admin.updateUserById(job.owner_id,{password:body.password,app_metadata:{goserve_recovery_op:body.operation_id}});
    if(result.error) {
      if(result.error.status>=400&&result.error.status<500) {
        await operation(deps.admin,'finish',{operation_id:body.operation_id,state:'failed'});
        return json({state:'failed',operation_id:body.operation_id});
      }
    } else if(result.data?.user?.id===job.owner_id&&result.data.user.app_metadata?.goserve_recovery_op===body.operation_id) {
      await operation(deps.admin,'finish',{operation_id:body.operation_id,state:'completed'});
      return json({state:'completed',operation_id:body.operation_id});
    }
  }catch { /* The operation marker resolves a lost response without replaying the password. */ }
  return json(await reconcile(deps.admin,job));
}
