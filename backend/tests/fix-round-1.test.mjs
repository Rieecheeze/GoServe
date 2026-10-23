import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFile } from 'node:fs/promises';
import { stripTypeScriptTypes } from 'node:module';
import { runInNewContext } from 'node:vm';

const source = stripTypeScriptTypes(await readFile(new URL('../lib/v23.ts', import.meta.url), 'utf8'), { mode: 'transform' }).replace(/^export /gm, '');
const context = { Request, Response, URL, URLSearchParams, AbortSignal, TextDecoder, Uint8Array, crypto };
runInNewContext(source + '\nglobalThis.api={validateRegistration,rpc,errorResponse,authenticated};', context);
const { api } = context;
const profile = { first_name:'A',middle_name:null,no_middle_name:true,last_name:'B',student_id:'1',course_program:'Test',year_level:'Test',mobile_number:'Contact',gender:'Self described' };
const registration = password => ({ email:'synthetic@demo.com',password,profile,terms_version:'test',idempotency_key:'00000000-0000-4000-8000-000000000001' });

test('password rule accepts Unicode letters/digits and preserves whitespace', () => {
  for (const password of ['éééé١!', '𐐀abc1!', ' A1!  ']) assert.doesNotThrow(() => api.validateRegistration(registration(password)));
});
test('password rule counts code points and requires a real punctuation/symbol', () => {
  for (const password of ['𐐀a1!x', 'Abcd1é', 'Abcd1 ', 'éééé١é']) assert.throws(() => api.validateRegistration(registration(password)), /VALIDATION_FAILED/);
});
test('known SQLSTATE errors have sanitized actionable mappings', async () => {
  for (const [code, expected, status] of [['22P02','VALIDATION_FAILED',422],['23502','VALIDATION_FAILED',422],['23514','VALIDATION_FAILED',422],['23505','CONFLICT',409],['40001','TEMPORARILY_UNAVAILABLE',503],['40P01','TEMPORARILY_UNAVAILABLE',503]]) {
    let failure;
    try { await api.rpc({ rpc:async()=>({error:{code,message:'private SQL details'}}) }, 'test'); } catch (error) { failure=error; }
    assert.equal(failure?.code, expected);
    const response=api.errorResponse(failure);
    assert.equal(response.status,status);
    assert.ok(!(await response.text()).includes('private SQL'));
  }
});
test('new authenticated Edge boundary respects authoritative SQL session rejection', async () => {
  let called=0;
  await assert.rejects(() => api.authenticated(new Request('https://fixture/test',{headers:{Authorization:'Bearer verified-session'}}),
    {auth:{getUser:async()=>({data:{user:{id:'existing-complete-user',is_anonymous:false,user_metadata:{email_verified:true}}}})}},
    ()=>({rpc:async name=>{called++;assert.equal(name,'account_view');return {error:{code:'P0001',message:'FORBIDDEN'}};}})), /FORBIDDEN/);
  assert.equal(called,1);
});

const body = {email:'synthetic@demo.com',code_challenge:'a'.repeat(43),code_challenge_method:'s256',state:'b'.repeat(43)};
async function recovery(value, environment={}, transport=async()=>new Response('{}')) {
  let handler;
  context.serve=callback=>{handler=callback;};
  context.Deno={env:{get:name=>({SUPABASE_URL:'https://fixture.supabase.co',SUPABASE_ANON_KEY:'public-fixture',GOSERVE_RECOVERY_REDIRECT:'goserve://auth/recovery',...environment})[name]}};
  context.fetch=transport;
  const entry=stripTypeScriptTypes(await readFile(new URL('../functions/recovery-request/index.ts',import.meta.url),'utf8'),{mode:'transform'}).replace(/^import[^\n]+\n/gm,'');
  runInNewContext(entry,context);
  return handler(new Request('https://fixture/recovery-request',{method:'POST',body:JSON.stringify(value)}),{admin:{auth:{resetPasswordForEmail:async()=>({})}}});
}
test('recovery forwards native S256 challenge and binds opaque state to trusted redirect',async()=>{
  let sent;
  const response=await recovery(body,{},async(url,init)=>{sent={url:new URL(url),init};return new Response('{}');});
  assert.deepEqual(await response.json(),{request_received:true});
  assert.equal(sent.url.origin,'https://fixture.supabase.co');
  assert.equal(sent.url.pathname,'/auth/v1/recover');
  const redirect=new URL(sent.url.searchParams.get('redirect_to'));
  assert.equal(redirect.protocol,'goserve:');assert.equal(redirect.host,'auth');assert.equal(redirect.pathname,'/recovery');
  assert.equal(redirect.searchParams.get('state'),body.state);
  assert.equal(redirect.searchParams.get('flow'),'recovery');
  assert.deepEqual(JSON.parse(sent.init.body),{email:body.email,code_challenge:body.code_challenge,code_challenge_method:'s256'});
  assert.equal(sent.init.headers.apikey,'public-fixture');
});
test('recovery rejects arbitrary redirects, verifier disclosure and non-S256 attempts',async()=>{
  let calls=0;
  for(const invalid of [{...body,redirect_to:'https://evil.test'},{...body,code_verifier:'secret'},{...body,code_challenge_method:'plain'},{...body,state:'bad&redirect_to=evil'}]) {
    await assert.rejects(()=>recovery(invalid,{},async()=>{calls++;throw new Error('must not call Auth');}),/VALIDATION_FAILED/);
  }
  assert.equal(calls,0);
});
test('missing or invalid recovery configuration fails before Auth is called',async()=>{
  let calls=0;
  for(const configuration of [{GOSERVE_RECOVERY_REDIRECT:undefined},{SUPABASE_URL:undefined},{SUPABASE_ANON_KEY:undefined},{GOSERVE_RECOVERY_REDIRECT:'invalid'},{GOSERVE_RECOVERY_REDIRECT:'goserve://auth/recovery#fragment'},{SUPABASE_URL:'http://fixture.supabase.co'}]) {
    await assert.rejects(()=>recovery(body,configuration,async()=>{calls++;throw new Error('must not call Auth');}),/CONFIGURATION_REQUIRED/);
  }
  assert.equal(calls,0);
});
async function assertReceived(response) {
  assert.equal(response.status,200);
  assert.equal(response.headers.get('Cache-Control'),'no-store');
  assert.equal(await response.text(),'{"request_received":true}');
}
for(const status of [200,400,401,403,404,422,429,500,503]) {
  test(`recovery Auth ${status} conceals known and unknown accounts`,async()=>{
    for(const email of ['existing@demo.com','unknown@demo.com']) {
      await assertReceived(await recovery({...body,email},{},async()=>new Response('private account error',{status})));
    }
  });
}
test('recovery transport failures return the same request-received result',async()=>{
  await assertReceived(await recovery(body,{},async()=>{throw new Error('private network detail');}));
});
test('recovery timeout keeps the ten-second transport bound and neutral result',async()=>{
  const original=context.AbortSignal;
  let deadline, sentSignal;
  context.AbortSignal={timeout:milliseconds=>{
    deadline=milliseconds;
    return AbortSignal.abort(new DOMException('private timeout detail','TimeoutError'));
  }};
  try {
    await assertReceived(await recovery(body,{},async(_url,{signal})=>{sentSignal=signal;signal.throwIfAborted();}));
    assert.equal(deadline,10000);
    assert.equal(sentSignal?.aborted,true);
    assert.equal(sentSignal?.reason.name,'TimeoutError');
  } finally { context.AbortSignal=original; }
});
test('recovery response-body cleanup failures cannot change the public result',async()=>{
  for(const status of [200,404,429,500]) {
    let cancelled=false;
    const response=new Response(new ReadableStream({cancel(){cancelled=true;throw new Error('private cleanup detail');}}),{status});
    await assertReceived(await recovery(body,{},async()=>response));
    assert.equal(cancelled,true);
  }
});
