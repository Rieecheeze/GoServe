import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {readFile,readdir} from 'node:fs/promises';
import test from 'node:test';
import {fixtureSql} from './sql-fixture.mjs';
let runtime,crypto;
try {runtime=await import('@electric-sql/pglite');}catch{runtime=await import('../../.tools/sql-validation/node_modules/@electric-sql/pglite/dist/index.js');}
try {crypto=await import('@electric-sql/pglite/contrib/pgcrypto');}catch{crypto=await import('../../.tools/sql-validation/node_modules/@electric-sql/pglite/dist/contrib/pgcrypto.js');}
const db=await runtime.PGlite.create({extensions:{pgcrypto:crypto.pgcrypto}});
const sql=async(text,args=[]) => (await db.query(text,args)).rows;
await db.exec(fixtureSql);
await db.exec(await readFile(new URL('../schema.sql',import.meta.url),'utf8'));
for(const f of (await readdir(new URL('../migrations/',import.meta.url))).filter(f=>f.endsWith('.sql')).sort())await db.exec(await readFile(new URL('../migrations/'+f,import.meta.url),'utf8'));
const owner=randomUUID(),other=randomUUID(),incomplete=randomUUID(),noTerms=randomUUID();
await sql("insert into public.terms_documents values('egovpay-fixture','Synthetic payment fixture','fixture',now(),true)");
for(const id of [owner,other,incomplete,noTerms]){
  await sql('insert into auth.users(id,email) values($1,$2)',[id,id+'@demo.com']);
  await sql("insert into public.profiles(id,email,role,is_demo,profile_completed_at) values($1,$2,'student',true,case when $3 then null else now() end)",[id,id+'@demo.com',id===incomplete]);
  if(id!==noTerms)await sql("insert into goserve_private.terms_acceptances(profile_id,terms_version,actor_id) values($1,'egovpay-fixture',$1)",[id]);
}
async function as(id,query,args=[],role='authenticated',claims={is_anonymous:false,amr:[{method:'password'}]}){
  await db.exec('begin');try{await db.exec('set local role '+role);await sql("select set_config('request.jwt.claim.sub',$1,true),set_config('request.jwt.claims',$2,true)",[id??'',JSON.stringify(claims)]);const r=await sql(query,args);await db.exec('commit');return r;}catch(e){await db.exec('rollback');throw e;}
}
const call=async(id,name,args=[]) => (await as(id,'select public.'+name+'('+args.map((_,i)=>'$'+(i+1)).join(',')+') r',args))[0].r;
const deny=(fn,code) => assert.rejects(fn,e=>e.message===code);
const srv=async(command,payment,key=null,details=null,actor=owner,token=null) => (await as(null,'select public.egovpay_server_operation($1,$2,$3,$4,$5,$6) r',[command,payment,actor,key,token,details],'service_role'))[0].r;
const service=(await sql("insert into public.services(office_id,name,delivery_modes,fee_state,amount,currency) values('OFF-REG','Synthetic eGovPay',ARRAY['physical'],'required',5,'PHP') returning *"))[0];
const quote=await call(owner,'quote_request',[service.id,'physical']);
await test('eGovPay options stay false without configured test gateway; legacy projection is unchanged',async()=>{
  const old=await call(owner,'workflow_options',[quote.id,null]);const newer=await call(owner,'workflow_options_v2',[quote.id,null]);
  assert.equal(newer.egovpay,false);delete newer.egovpay;assert.deepEqual(newer,old);
  await deny(()=>call(owner,'submit_request',[quote.id,[],'','egovpay',randomUUID()]),'PROVIDER_UNAVAILABLE');
});
await db.exec(`create table goserve_private.egovpay_configuration(provider text primary key,base_url text,demo_only boolean,integration_enabled boolean,token text default 'synthetic-token');
  insert into goserve_private.egovpay_configuration(provider,base_url,demo_only,integration_enabled) values('egovpay','https://platforms-api.e.gov.ph/egovpay',true,true);
  create function public.get_egovpay_server_configuration() returns jsonb language sql security definer set search_path='' as $$
    select to_jsonb(c)||jsonb_build_object('settlement_template_uuid','20000000-0000-4000-8000-000000000002') from goserve_private.egovpay_configuration c $$;
  revoke all on function public.get_egovpay_server_configuration() from public,anon,authenticated;
  grant execute on function public.get_egovpay_server_configuration() to service_role;`);
await test('configuration and demo eligibility gates must all be true before checkout is offered',async()=>{
  for(const change of ["integration_enabled=false","demo_only=false","base_url='https://attacker.test'","token=''","token=null"]){
    await sql('update goserve_private.egovpay_configuration set '+change);
    assert.equal((await call(owner,'workflow_options_v2',[quote.id,null])).egovpay,false);
    await deny(()=>call(owner,'submit_request',[quote.id,[],'','egovpay',randomUUID()]),'PROVIDER_UNAVAILABLE');
    await sql("update goserve_private.egovpay_configuration set integration_enabled=true,demo_only=true,base_url='https://platforms-api.e.gov.ph/egovpay',token='synthetic-token'");
  }
  for(const change of ["is_demo=false","currency='USD'"]){
    await sql('update public.services set '+change+' where id=$1',[service.id]);
    assert.equal((await call(owner,'workflow_options_v2',[quote.id,null])).egovpay,false);
    await deny(()=>call(owner,'submit_request',[quote.id,[],'','egovpay',randomUUID()]),'PROVIDER_UNAVAILABLE');
    await sql("update public.services set is_demo=true,currency='PHP' where id=$1",[service.id]);
  }
  await sql('update public.profiles set is_demo=false where id=$1',[owner]);
  assert.equal((await call(owner,'workflow_options_v2',[quote.id,null])).egovpay,false);
  await deny(()=>call(owner,'submit_request',[quote.id,[],'','egovpay',randomUUID()]),'PROVIDER_UNAVAILABLE');
  await sql('update public.profiles set is_demo=true where id=$1',[owner]);
});
let request,payment,key=randomUUID(),claim;
await test('configured demo PHP quote may submit eGovPay while actor and ownership gates remain enforced',async()=>{
  assert.equal((await call(owner,'workflow_options_v2',[quote.id,null])).egovpay,true);
  await deny(()=>call(other,'workflow_options_v2',[quote.id,null]),'NOT_FOUND');
  await deny(()=>call(incomplete,'workflow_options_v2',[quote.id,null]),'PROFILE_INCOMPLETE');
  await deny(()=>call(noTerms,'workflow_options_v2',[quote.id,null]),'TERMS_REQUIRED');
  await deny(()=>as(owner,'select public.workflow_options_v2($1,null)',[quote.id],'authenticated',{is_anonymous:true}),'AUTH_REQUIRED');
  request=await call(owner,'submit_request',[quote.id,[],'','egovpay',randomUUID()]);
  payment=(await sql('select * from public.payments where request_id=$1',[request.id]))[0];
  assert.equal(payment.status,'pending');assert.equal(request.status,'Awaiting Payment');
  await deny(()=>call(other,'egovpay_authorize',[payment.id]),'NOT_FOUND');
  await deny(()=>call(incomplete,'egovpay_authorize',[payment.id]),'PROFILE_INCOMPLETE');
  await assert.rejects(()=>as(owner,"select public.egovpay_server_operation('claim',$1,$2,$3,null,null)",[payment.id,owner,key]),/permission denied/);
});
await test('new payment submission and checkout claims require current active Terms',async()=>{
  await sql("update public.terms_documents set active=false where version='egovpay-fixture'");
  await sql("insert into public.terms_documents values('egovpay-next','Synthetic changed Terms','fixture',now(),true)");
  assert.equal((await call(owner,'workflow_options_v2',[quote.id,null])).egovpay,false);
  await deny(()=>call(owner,'submit_request',[quote.id,[],'','egovpay',randomUUID()]),'TERMS_REQUIRED');
  await deny(()=>srv('claim',payment.id,key),'TERMS_REQUIRED');
  await sql("update public.terms_documents set active=false where version='egovpay-next'");
  await sql("update public.terms_documents set active=true where version='egovpay-fixture'");
});
await test('one server claim per payment preserves same key and rejects a different key',async()=>{
  claim=await srv('claim',payment.id,key);assert.equal(claim.may_create,true);assert.equal(claim.amount,'5');assert.equal(claim.currency,'PHP');
  const replay=await srv('claim',payment.id,key);assert.equal(replay.may_create,false);assert.equal(replay.txnid,claim.txnid);
  await deny(()=>srv('claim',payment.id,randomUUID()),'IDEMPOTENCY_CONFLICT');
  await deny(()=>srv('claim',payment.id,key,null,other),'NOT_FOUND');
  await srv('ambiguous',payment.id,key);assert.equal((await srv('claim',payment.id,key)).may_create,false);
});
const uuid=randomUUID(),url='https://egovpay-pgi-dev.oueg.info/'+uuid;
const details={uuid,txnid:null,environment_type:'TEST',amount:'5.0000',currency:'PHP',payment_status:'INITIAL'};
await test('stored checkout and authoritative INITIAL reconcile idempotently without changing request status',async()=>{
  await srv('attach',payment.id,key,{uuid,url});details.txnid=claim.txnid;
  assert.equal((await srv('observe',payment.id,null,details)).status,'pending');
  await srv('observe',payment.id,null,details);
  assert.equal((await sql('select count(*)::int n from goserve_private.provider_payment_events where payment_id=$1',[payment.id]))[0].n,1);
  assert.equal((await sql('select status from public.requests where id=$1',[request.id]))[0].status,'Awaiting Payment');
  assert.equal((await sql('select version from public.payments where id=$1',[payment.id]))[0].version,1);
});
let resumedKey;
await test('an owner can resume the same ready checkout with a fresh key that stays bound to that payment',async()=>{
  resumedKey=randomUUID();const resumed=await srv('claim',payment.id,resumedKey);
  assert.equal(resumed.may_create,false);assert.equal(resumed.checkout_url,url);assert.equal(resumed.provider_uuid,uuid);
  const q4=await call(owner,'quote_request',[service.id,'physical']);const r4=await call(owner,'submit_request',[q4.id,[],'','egovpay',randomUUID()]);
  const p4=(await sql('select * from public.payments where request_id=$1',[r4.id]))[0];
  await deny(()=>srv('claim',p4.id,resumedKey),'IDEMPOTENCY_CONFLICT');
});
await test('all identity amount currency and environment mismatches fail closed',async()=>{
  for(const patch of [{uuid:randomUUID()},{txnid:'OTHER'},{amount:'5.01'},{amount:5},{currency:'USD'},{environment_type:'LIVE'}])await deny(()=>srv('observe',payment.id,null,{...details,...patch}),'VALIDATION_FAILED');
  for(const payment_status of ['PAID','SUCCESS','FAILED','CANCELLED','FUTURE'])assert.equal((await srv('observe',payment.id,null,{...details,payment_status})).status,'unknown');
  assert.equal((await sql('select status from public.payments where id=$1',[payment.id]))[0].status,'pending');
});
await test('callback capability is private and late PAID preserves a cancelled request while recording financial truth once',async()=>{
  assert.equal((await srv('callback',null,null,null,null,claim.callback_token)).payment_id,payment.id);
  await deny(()=>srv('callback',null,null,null,null,randomUUID()),'NOT_FOUND');
  await assert.rejects(()=>as(owner,'select * from goserve_private.egovpay_checkouts'),/permission denied/);
  await sql("update public.requests set status='Cancelled',terminal_at=now() where id=$1",[request.id]);
  const paid={...details,payment_status:'PAID',paid_at:'October 23, 2026 06:38:24 AM'};
  assert.equal((await srv('observe',payment.id,null,paid)).status,'paid');
  assert.equal((await srv('observe',payment.id,null,paid,null,claim.callback_token)).status,'paid');
  const p=(await sql('select status,version from public.payments where id=$1',[payment.id]))[0];assert.equal(p.status,'paid');assert.equal(p.version,2);
  assert.equal((await sql("select count(*)::int n from goserve_private.provider_payment_events where payment_id=$1 and verified_status='paid'",[payment.id]))[0].n,1);
  assert.equal((await sql('select status from public.requests where id=$1',[request.id]))[0].status,'Cancelled');
  await deny(()=>srv('claim',payment.id,key),'INVALID_STATE');
});
await test('verified TEST PAID adds one immutable provider ledger event and one payment version without advancing request',async()=>{
  const quote2=await call(owner,'quote_request',[service.id,'physical']);
  const r2=await call(owner,'submit_request',[quote2.id,[],'','egovpay',randomUUID()]);
  const p2=(await sql('select * from public.payments where request_id=$1',[r2.id]))[0],key2=randomUUID(),uuid2=randomUUID();
  const c2=await srv('claim',p2.id,key2);await srv('attach',p2.id,key2,{uuid:uuid2,url:'https://egovpay-pgi-dev.oueg.info/'+uuid2});
  const paid={...details,uuid:uuid2,txnid:c2.txnid,payment_status:'PAID',paid_at:'October 23, 2026 06:38:24 AM'};
  assert.equal((await srv('observe',p2.id,null,paid,null,c2.callback_token)).status,'paid');
  assert.equal((await srv('observe',p2.id,null,paid)).status,'paid');
  assert.equal((await srv('observe',p2.id,null,{...paid,payment_status:'INITIAL',paid_at:null})).status,'paid');
  const row=(await sql('select * from public.payments where id=$1',[p2.id]))[0];assert.equal(row.status,'paid');assert.equal(row.version,2);assert.ok(row.confirmed_at);assert.equal(row.confirmed_by,null);
  assert.equal((await sql("select count(*)::int n from goserve_private.provider_payment_events where payment_id=$1 and verified_status='paid'",[p2.id]))[0].n,1);
  assert.equal((await sql('select status,version from public.requests where id=$1',[r2.id]))[0].status,'Awaiting Payment');
});
await test('a conflicting preexisting ledger event cannot credit a different payment',async()=>{
  const q3=await call(owner,'quote_request',[service.id,'physical']);const r3=await call(owner,'submit_request',[q3.id,[],'','egovpay',randomUUID()]);
  const p3=(await sql('select * from public.payments where request_id=$1',[r3.id]))[0],k3=randomUUID(),u3=randomUUID();
  const c3=await srv('claim',p3.id,k3);await srv('attach',p3.id,k3,{uuid:u3,url:'https://egovpay-pgi-dev.oueg.info/'+u3});
  await sql("insert into goserve_private.provider_payment_events(provider_transaction_id,event_id,payment_id,verified_status,amount,currency) values($1,'status:PAID',$2,'paid',5,'PHP')",[u3,payment.id]);
  await deny(()=>srv('observe',p3.id,null,{...details,uuid:u3,txnid:c3.txnid,payment_status:'PAID',paid_at:'October 23, 2026 06:38:24 AM'}),'VALIDATION_FAILED');
  assert.equal((await sql('select status from public.payments where id=$1',[p3.id]))[0].status,'pending');
});
await db.close();
