import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import test from 'node:test';

const context={Response,Request,URL,AbortController,AbortSignal,setTimeout,clearTimeout,TextEncoder,TextDecoder,Uint8Array,crypto};
for(const file of ['v23','egovpay','egovpay-workflow']) {
  let source=stripTypeScriptTypes(await readFile(new URL('../lib/'+file+'.ts',import.meta.url),'utf8'),{mode:'transform'})
    .replace(/^import[^\n]+\n/gm,'').replaceAll(/^export /gm,'');
  source+='\nglobalThis.api={startEgovPay,refreshEgovPay,callbackEgovPay,readEgovPayBody};';
  if(file!=='egovpay-workflow') source=source.replace('\nglobalThis.api={startEgovPay,refreshEgovPay,callbackEgovPay,readEgovPayBody};','');
  runInNewContext(source,context);
}
const payment='10000000-0000-4000-8000-000000000001',key='20000000-0000-4000-8000-000000000002';
const uuid='30000000-0000-4000-8000-000000000003',token='40000000-0000-4000-8000-000000000004';
const checkout='https://egovpay-pgi-dev.oueg.info/'+uuid;
const config={base_url:'https://platforms-api.e.gov.ph/egovpay',token:'fake-key',demo_only:true,integration_enabled:true,settlement_template_uuid:key};
const bound={payment_id:payment,owner_id:'owner',request_id:key,amount:'5.00',currency:'PHP',txnid:'GS-'+payment,callback_token:token,provider_uuid:uuid,checkout_url:checkout,state:'ready',status:'pending'};
function harness(overrides={}) {
  let claim,posts=0,gets=0,observations=0;
  const client={rpc:async(name)=>({data:name==='egovpay_authorize'?{payment_id:payment}:null})};
  const admin={rpc:async(name,args)=>{
    if(name==='get_egovpay_server_configuration')return {data:config};
    if(name!=='egovpay_server_operation')throw new Error('Unexpected RPC');
    if(args.p_command==='claim') {if(!claim) {claim={...bound,state:'creating',checkout_url:null,provider_uuid:null,may_create:true,...overrides};return {data:claim};}return {data:{...claim,may_create:false}};}
    if(args.p_command==='attach'){claim={...bound,...overrides};return {data:claim};}
    if(args.p_command==='ambiguous'){claim={...bound,checkout_url:null,provider_uuid:null,state:'unknown',...overrides};return {data:claim};}
    if(args.p_command==='lookup'||args.p_command==='callback')return {data:{...bound,...overrides}};
    if(args.p_command==='observe'){observations++;return {data:{status:args.p_details.payment_status==='INITIAL'?'pending':args.p_details.payment_status==='PAID'&&args.p_details.paid_at?'paid':'unknown'}};}
    throw new Error('Unexpected command');
  }};
  const fetch=async(url,init)=>{
    if(init.method==='POST') {posts++;if(overrides.lost)throw new Error('secret network diagnostic');return Response.json({data:{uuid,url:checkout}},{status:201});}
    gets++;return Response.json({data:{uuid,txnid:bound.txnid,environment_type:'TEST',amount:'5.0000',currency:'PHP',payment_status:'INITIAL',...overrides.details}});
  };
  return {client,admin,fetch,counts:()=>({posts,gets,observations})};
}
test('start creates once from server-owned binding and returns only checkout_url',async()=>{
  const h=harness();const body={payment_id:payment,idempotency_key:key};
  assert.equal(JSON.stringify(await context.api.startEgovPay(h.admin,h.client,'owner',body,'https://fixture.supabase.co',h.fetch)),JSON.stringify({checkout_url:checkout}));
  await context.api.startEgovPay(h.admin,h.client,'owner',body,'https://fixture.supabase.co',h.fetch);
  assert.equal(h.counts().posts,1);
});
test('ambiguous creation cannot send a second POST',async()=>{
  const h=harness({lost:true});const task=()=>context.api.startEgovPay(h.admin,h.client,'owner',{payment_id:payment,idempotency_key:key},'https://fixture.supabase.co',h.fetch);
  await assert.rejects(task,/CHECKOUT_UNCONFIRMED/);await assert.rejects(task,/CHECKOUT_UNCONFIRMED/);
  assert.equal(h.counts().posts,1);
});
test('start rejects client-owned prices, redirects, malformed IDs before provider work',async()=>{
  for(const extra of [{amount:'1'},{redirect_url:'https://attacker.test'}, {payment_id:'invalid'}]){
    const h=harness();await assert.rejects(()=>context.api.startEgovPay(h.admin,h.client,'owner',{payment_id:payment,idempotency_key:key,...extra},'https://fixture.supabase.co',h.fetch),/VALIDATION_FAILED/);
    assert.equal(h.counts().posts,0);
  }
});
test('refresh authenticates provider details and never treats unsupported statuses as paid',async()=>{
  for(const payment_status of ['INITIAL','SUCCESS','FAILED','CANCELLED']){
    const h=harness({details:{payment_status,paid_at:'2026-10-22'}});
    const result=await context.api.refreshEgovPay(h.admin,h.client,'owner',{payment_id:payment},h.fetch);
    assert.equal(result.status,payment_status==='INITIAL'?'pending':'unknown');assert.equal(h.counts().gets,1);
  }
});
test('only verified PAID with a paid_at value may reconcile paid',async()=>{
  const h=harness({details:{payment_status:'PAID',paid_at:'October 23, 2026 06:38:24 AM'}});
  assert.equal((await context.api.refreshEgovPay(h.admin,h.client,'owner',{payment_id:payment},h.fetch)).status,'paid');
  const missing=harness({details:{payment_status:'PAID'}});
  assert.equal((await context.api.refreshEgovPay(missing.admin,missing.client,'owner',{payment_id:payment},missing.fetch)).status,'unknown');
});
test('a cancelled bound payment still fetches authoritative financial truth',async()=>{
  const h=harness({status:'cancelled',details:{payment_status:'PAID',paid_at:'October 23, 2026 06:38:24 AM'}});
  assert.equal((await context.api.refreshEgovPay(h.admin,h.client,'owner',{payment_id:payment},h.fetch)).status,'paid');
  assert.equal(h.counts().gets,1);
});
test('provider identity amount currency and environment mismatches cannot reach reconciliation',async()=>{
  for(const details of [{txnid:'other'},{amount:'5.01'},{amount:5},{currency:'USD'},{environment_type:'LIVE'},{uuid:key}]){
    const h=harness({details});const result=await context.api.refreshEgovPay(h.admin,h.client,'owner',{payment_id:payment},h.fetch);
    assert.equal(result.status,'unknown');assert.equal(h.counts().observations,0);
  }
});
test('callback ignores forged paid data and exposes no record identity',async()=>{
  const h=harness();const req=new Request('https://fixture.supabase.co/functions/v1/egovpay-callback?token='+token,{method:'POST',body:JSON.stringify({paid:true,amount:'1',url:'https://attacker.test'})});
  const result=await context.api.callbackEgovPay(h.admin,req,h.fetch);
  assert.equal(JSON.stringify(result),JSON.stringify({received:true}));assert.equal(h.counts().gets,1);
  assert.equal(h.counts().observations,1);
});
test('callback rejects oversized bodies and missing capabilities before provider access',async()=>{
  for(const [suffix,body]of [['?token='+token,'x'.repeat(16385)],['','{"paid":true}'],['?token=invalid','{}']]){
    const h=harness();const req=new Request('https://fixture.supabase.co/functions/v1/egovpay-callback'+suffix,{method:'POST',body});
    assert.equal(JSON.stringify(await context.api.callbackEgovPay(h.admin,req,h.fetch)),JSON.stringify({received:true}));
    assert.equal(h.counts().gets,0);
  }
});
test('payment request bodies are bounded before parsing and reject malformed JSON',async()=>{
  const read=body=>context.api.readEgovPayBody(new Request('https://fixture.test',{method:'POST',body}));
  await assert.rejects(()=>read('x'.repeat(2049)),/VALIDATION_FAILED/);
  await assert.rejects(()=>read('not json'),/VALIDATION_FAILED/);
  assert.equal((await read('{"payment_id":"'+payment+'"}')).payment_id,payment);
});
test('cancellation during provider creation prevents returning checkout to the caller',async()=>{
  const h=harness({status:'cancelled'});
  await assert.rejects(()=>context.api.startEgovPay(h.admin,h.client,'owner',{payment_id:payment,idempotency_key:key},'https://fixture.supabase.co',h.fetch),/CHECKOUT_UNCONFIRMED/);
  assert.equal(h.counts().posts,1);
});
