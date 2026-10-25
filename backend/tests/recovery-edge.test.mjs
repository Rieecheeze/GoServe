import assert from 'node:assert/strict';
import { createHash,randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { stripTypeScriptTypes } from 'node:module';
import { runInNewContext } from 'node:vm';
import { isIP } from 'node:net';
import test from 'node:test';

const context={crypto,Date,URL,TextEncoder,TextDecoder,Uint8Array,Response,Request,AbortSignal,fetch,btoa,atob,isIP};
for(const file of ['v23','recovery']) {
  const source=stripTypeScriptTypes(await readFile(new URL(`../lib/${file}.ts`,import.meta.url),'utf8'),{mode:'transform'}).replace(/^import[^\n]+\n/gm,'').replace(/^export /gm,'');
  runInNewContext(source,context);
}
runInNewContext('globalThis.api={accountRecovery,recoveryControl,errorResponse};',context);
const {accountRecovery,recoveryControl}=context.api;
const owner=randomUUID(),sessionId=randomUUID(),generation=randomUUID(),operationId=randomUUID();
const verifier='a'.repeat(43),grant='b'.repeat(43),validCode='AAAA-AAAA-AAAA-AAAA-AAAA-AAAA-AAAA-AAAA';
const codeChallenge=createHash('sha256').update(verifier).digest('base64url');
function token(age=0) {
  return ['e30',Buffer.from(JSON.stringify({session_id:sessionId,amr:[{method:'password',timestamp:Math.floor(Date.now()/1000)-age}]})).toString('base64url'),'fixture-signature'].join('.');
}
const status={codes_configured:true,codes_acknowledged:true,remaining_codes:10,generation,requires_setup:false};
function request(body,age=0) {
  return new Request('https://fixture/account-recovery',{method:'POST',headers:{'Content-Type':'application/json','CF-Connecting-IP':'203.0.113.17','X-Forwarded-For':'198.51.100.2, 203.0.113.9',Authorization:`Bearer ${token(age)}`},body:JSON.stringify(body)});
}
function harness(options={}) {
  const calls=[];let updates=0,claimed=false,marker=options.marker??null,finished='pending';
  const admin={
    auth:{getUser:async()=>({data:{user:{id:owner,email:'owner@tip.edu.ph',is_anonymous:false}},error:null}),admin:{
      updateUserById:async(id,body)=>{
        updates++;assert.equal(id,owner);assert.deepEqual(Object.keys(body).sort(),['app_metadata','password']);
        assert.deepEqual(Object.keys(body.app_metadata),['goserve_recovery_op']);
        if(options.lostAfterCommit){marker=body.app_metadata.goserve_recovery_op;throw new Error('private transport payload');}
        if(options.updateError)return {data:{user:null},error:options.updateError};
        marker=body.app_metadata.goserve_recovery_op;return {data:{user:{id:owner,app_metadata:{goserve_recovery_op:marker}}},error:null};
      },
      getUserById:async(id)=>{assert.equal(id,owner);return {data:{user:{id:owner,app_metadata:{goserve_recovery_op:marker}}},error:null};},
    }},
    rpc:async(name,{p_command,p_args})=>{
      assert.equal(name,'recovery_server_operation');calls.push([p_command,JSON.parse(JSON.stringify(p_args))]);
      if(p_command==='session')return options.revoked?{error:{message:'AUTH_INVALID'}}:{data:{}};
      if(p_command==='status'||p_command==='ack')return {data:status};
      if(p_command==='generate')return {data:{generation,remaining_codes:10,codes_configured:true,codes_acknowledged:false}};
      if(p_command==='context')return {data:options.configurationRequired?{blocked:false,configuration_required:true}:options.unknown?{blocked:false}:options.blocked?{blocked:true}:{blocked:false,owner_id:owner,version:7}};
      if(p_command==='redeem')return {data:{expires_at:new Date(Date.now()+300000).toISOString()}};
      if(p_command==='claim'||p_command==='result'){
        const should_mutate=p_command==='claim'&&!claimed;claimed=true;
        return {data:{owner_id:owner,state:finished,operation_id:p_args.operation_id,should_mutate}};
      }
      if(p_command==='finish'){finished=p_args.state;return {data:{state:finished,operation_id:p_args.operation_id}};}
      throw new Error('Unexpected fixed operation');
    },
  };
  return {admin,userClient:()=>({rpc:async()=>({data:{id:owner},error:null})}),recoveryProxyHops:0,calls,updates:()=>updates};
}
test('code generation derives its owner from Auth and returns plaintext only for a new set',async()=>{
  const h=harness();const response=await recoveryControl(request({action:'generate_codes'}),h),result=await response.json();
  assert.deepEqual(Object.keys(result).sort(),['codes','codes_acknowledged','codes_configured','generation','remaining_codes']);assert.equal(result.codes.length,10);
  const persisted=h.calls.find(([command])=>command==='generate')[1];assert.equal(persisted.owner_id,owner);assert.equal(persisted.digests.length,10);assert.equal(persisted.codes,undefined);
  assert.equal(JSON.stringify(persisted).includes(result.codes[0]),false);
  const read=await(await recoveryControl(request({action:'status'}),h)).json();assert.equal(read.codes,undefined);
  assert.equal(response.headers.get('Cache-Control'),'no-store');
});
test('only generation requires recent first-factor authentication; saving can take longer',async()=>{
  const h=harness();await assert.rejects(()=>recoveryControl(request({action:'generate_codes'},301),h),/RECENT_AUTH_REQUIRED/);
  assert.equal(h.calls.length,0);
  assert.equal((await(await recoveryControl(request({action:'acknowledge_codes',generation},900),h)).json()).codes_acknowledged,true);
});
test('revoked sessions and caller-selected owners cannot generate or read codes',async()=>{
  const h=harness({revoked:true});await assert.rejects(()=>recoveryControl(request({action:'generate_codes'}),h),/AUTH_INVALID/);
  assert.equal(h.calls.some(([command])=>command==='generate'),false);
  await assert.rejects(()=>recoveryControl(request({action:'generate_codes',owner_id:randomUUID()}),h),/VALIDATION_FAILED/);
});
test('unknown accounts and invalid saved codes have the same public proof error',async()=>{
  const body={action:'verify',email:'owner@tip.edu.ph',code:validCode,code_challenge:codeChallenge,code_challenge_method:'s256'};
  await assert.rejects(()=>accountRecovery(request(body),harness({unknown:true})),/RECOVERY_PROOF_INVALID/);
  await assert.rejects(()=>accountRecovery(request({...body,code:'not-a-code'}),harness()),/RECOVERY_PROOF_INVALID/);
  await assert.rejects(()=>accountRecovery(request(body),harness({blocked:true})),/RATE_LIMITED/);
  await assert.rejects(()=>accountRecovery(request({...body,method:'totp'}),harness()),/VALIDATION_FAILED/);
});
test('public verification returns only an installation-bound opaque grant',async()=>{
  const h=harness(),body={action:'verify',email:'Owner@TIP.EDU.PH',code:validCode,code_challenge:codeChallenge,code_challenge_method:'s256'};
  const result=await(await accountRecovery(request(body),h)).json();assert.deepEqual(Object.keys(result).sort(),['expires_at','reset_grant']);assert.match(result.reset_grant,/^[A-Za-z0-9_-]{43}$/);
  const persisted=h.calls.find(([command])=>command==='redeem')[1];assert.equal(persisted.challenge,codeChallenge);assert.equal(persisted.owner_id,owner);assert.equal(persisted.code,undefined);
  assert.equal(h.updates(),0);
});
test('public proof budgets use the CF address regardless of forged forwarded headers',async()=>{
  const h=harness({unknown:true}),body={action:'verify',email:'unknown@tip.edu.ph',code:validCode,code_challenge:codeChallenge,code_challenge_method:'s256'};
  for(const value of ['198.51.100.2','192.0.2.8, 192.0.2.9']) {
    const message=request(body);message.headers.set('X-Forwarded-For',value);message.headers.set('X-Real-IP',value);
    await assert.rejects(()=>accountRecovery(message,h),/RECOVERY_PROOF_INVALID/);
  }
  const budgets=h.calls.filter(([command])=>command==='context').map(([,args])=>args.ip_digest);
  assert.equal(budgets.length,2);assert.equal(new Set(budgets).size,1);
  assert.equal(budgets[0],createHash('sha256').update('203.0.113.17').digest('hex'));
});
test('public proof stays closed before SQL budgets when CF ingress is absent or disabled',async()=>{
  const body={action:'verify',email:'unknown@tip.edu.ph',code:validCode,code_challenge:codeChallenge,code_challenge_method:'s256'};
  for(const scenario of ['missing','multiple','disabled','unsupported']) {
    const h=harness(),message=request(body);
    if(scenario==='missing')message.headers.delete('CF-Connecting-IP');
    if(scenario==='multiple')message.headers.set('CF-Connecting-IP','203.0.113.17, 192.0.2.8');
    if(scenario==='disabled')h.recoveryProxyHops=undefined;
    if(scenario==='unsupported')h.recoveryProxyHops=1;
    await assert.rejects(()=>accountRecovery(message,h),/TEMPORARILY_UNAVAILABLE/);
    assert.equal(h.calls.length,0);
  }
});
test('public recovery cannot issue a grant before strict session revocation is enabled',async()=>{
  const h=harness({configurationRequired:true});
  await assert.rejects(()=>accountRecovery(request({action:'verify',email:'owner@tip.edu.ph',code:validCode,code_challenge:codeChallenge,code_challenge_method:'s256'}),h),/CONFIGURATION_REQUIRED/);
  assert.equal(h.calls.some(([command])=>command==='redeem'),false);
});
const reset={action:'reset',reset_grant:grant,code_verifier:verifier,password:'New password 1!',operation_id:operationId};
test('a completed reset returns no session and a duplicate operation never mutates again',async()=>{
  const h=harness();const first=await(await accountRecovery(request(reset),h)).json();assert.deepEqual(first,{state:'completed',operation_id:operationId});
  const second=await(await accountRecovery(request({...reset,password:'Another password 2!'}),h)).json();assert.equal(second.state,'completed');assert.equal(h.updates(),1);
  assert.equal(first.access_token,undefined);assert.equal(first.refresh_token,undefined);
});
test('lost provider responses reconcile the server marker without replaying a password',async()=>{
  const h=harness({lostAfterCommit:true});const result=await(await accountRecovery(request(reset),h)).json();assert.equal(result.state,'completed');
  assert.equal(h.updates(),1);
  assert.equal((await(await accountRecovery(request({...reset,action:'result',password:undefined}),h)).json()).state,'completed');assert.equal(h.updates(),1);
});
test('an uncertain provider failure stays pending across retries and result queries',async()=>{
  const h=harness({updateError:{status:503}});
  assert.equal((await(await accountRecovery(request(reset),h)).json()).state,'pending');
  assert.equal((await(await accountRecovery(request(reset),h)).json()).state,'pending');
  assert.equal((await(await accountRecovery(request({...reset,action:'result',password:undefined}),h)).json()).state,'pending');assert.equal(h.updates(),1);
});
test('password and body validation happen before a grant is claimed',async()=>{
  const h=harness();await assert.rejects(()=>accountRecovery(request({...reset,password:'weak'}),h),/PASSWORD_WEAK/);
  await assert.rejects(()=>accountRecovery(request({...reset,password:'A1!'.repeat(25)}),h),/PASSWORD_TOO_LONG/);assert.equal(h.calls.length,0);
  const tooLarge=new Request('https://fixture',{method:'POST',headers:{'Content-Type':'application/json'},body:'x'.repeat(8193)});
  await assert.rejects(()=>accountRecovery(tooLarge,h),/VALIDATION_FAILED/);assert.equal(h.updates(),0);
});
test('retired public signup and email endpoints fail closed before sending mail or creating users',async()=>{
  for(const name of ['register-student','recovery-request','password-change']) {
    let handler;context.serve=callback=>{handler=callback;};
    runInNewContext(stripTypeScriptTypes(await readFile(new URL(`../functions/${name}/index.ts`,import.meta.url),'utf8'),{mode:'transform'}).replace(/^import[^\n]+\n/gm,''),context);
    await assert.rejects(()=>handler(request({}),harness()),new RegExp(name==='register-student'?'GOOGLE_REGISTRATION_REQUIRED':'RECOVERY_METHOD_REQUIRED'));
  }
});
