import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {readFile,readdir} from 'node:fs/promises';
import test from 'node:test';
import {fixtureSql} from './sql-fixture.mjs';

let runtime,crypto;
try { runtime=await import('@electric-sql/pglite'); } catch { runtime=await import('../../.tools/sql-validation/node_modules/@electric-sql/pglite/dist/index.js'); }
try { crypto=await import('@electric-sql/pglite/contrib/pgcrypto'); } catch { crypto=await import('../../.tools/sql-validation/node_modules/@electric-sql/pglite/dist/contrib/pgcrypto.js'); }
const db=await runtime.PGlite.create({extensions:{pgcrypto:crypto.pgcrypto}});
const sql=async(text,args=[]) => (await db.query(text,args)).rows;
await db.exec(fixtureSql);
await db.exec(await readFile(new URL('../schema.sql',import.meta.url),'utf8'));
for(const file of (await readdir(new URL('../migrations/',import.meta.url))).filter(x=>x.endsWith('.sql')).sort()) {
  await db.exec(await readFile(new URL('../migrations/'+file,import.meta.url),'utf8'));
}
const ids=Object.fromEntries(['student','other','staff','wrong','limited','teller','incomplete','terms','missing'].map(name=>[name,randomUUID()]));
const normalClaims={is_anonymous:false,amr:[{method:'password'}]};
async function as(who,text,args=[],claims=normalClaims,role='authenticated') {
  await db.exec('begin');
  try {
    await db.exec(`set local role ${role}`);
    await sql("select set_config('request.jwt.claim.sub',$1,true),set_config('request.jwt.claims',$2,true)",[ids[who]??'',JSON.stringify(claims)]);
    const rows=await sql(text,args);
    await db.exec('commit');
    return rows;
  } catch(error) { await db.exec('rollback');throw error; }
}
const call=async(who,name,args=[]) => (await as(who,`select public.${name}(${args.map((_,i)=>'$'+(i+1)).join(',')}) r`,args))[0].r;
const options=(who,quote=null,request=null) => call(who,'workflow_options',[quote,request]);
const deny=(task,code) => assert.rejects(task,error=>error.message===code);
const policy=(name,value) => sql('insert into goserve_private.policies(name,value) values($1,$2::jsonb) on conflict(name) do update set value=excluded.value',[name,JSON.stringify(value)]);
const remove=name => sql('delete from goserve_private.policies where name=$1',[name]);
const blocked=(blocker,files=false) => ({available:false,blocker,...(files?{mime_types:null,max_bytes:null}:{})});
const available={available:true,blocker:null};
const filePolicy={verifier:'goserve-v1',mime_types:['application/pdf','image/png','image/jpeg'],max_bytes:16384,max_pixels:1000000,upload_seconds:300};
await sql("insert into public.terms_documents values('fixture-options','Synthetic options fixture','fixture',now(),true)");
for(const [name,id] of Object.entries(ids)) {
  await sql('insert into auth.users(id,email) values($1,$2)',[id,name+'@demo.com']);
  if(name==='missing') continue;
  const role=['staff','wrong','limited'].includes(name)?'staff':name==='teller'?'teller':'student';
  const office={staff:'OFF-REG',wrong:'OFF-ACC',limited:'OFF-REG',teller:'OFF-CASH'}[name]??null;
  await sql("insert into public.profiles(id,email,role,office_id,staff_capability,profile_completed_at) values($1,$2,$3,$4,$5,case when $6 then null else now() end)",[id,name+'@demo.com',role,office,name==='limited'?'faculty':null,name==='incomplete']);
  if(name!=='terms') await sql("insert into goserve_private.terms_acceptances(profile_id,terms_version,actor_id) values($1,'fixture-options',$1)",[id]);
}
const service=async(fee='free') => (await sql("insert into public.services(office_id,name,delivery_modes,fee_state,amount,currency) values('OFF-REG','Synthetic workflow options',ARRAY['physical','digital'],$1,$2,$3) returning *",[fee,fee==='free'?'0':fee==='required'?'5':null,fee==='required'?'TEST':null]))[0];
const quote=(s,mode='physical') => call('student','quote_request',[s.id,mode]);
const request=async(s,mode='physical',status='Submitted') => (await sql("insert into public.requests(owner_id,office_id,service_id,snapshot,delivery_mode,status) select $1,office_id,id,to_jsonb(services),$3,$4 from public.services where id=$2 returning *",[ids.student,s.id,mode,status]))[0];
const free=await service();
const required=await service('required');
const pending=await service('pending');
const q=await quote(required);
const freeQuote=await quote(free);
const pendingQuote=await quote(pending);
const r=await request(free);
const digital=await request(free,'digital','Processing');
const ready=await request(free,'physical','Ready for Pickup/Download');

await test('workflow options uses the current actor guard and least-privilege function grants',async()=>{
  await assert.rejects(()=>as('student','select public.workflow_options($1,null)',[q.id],normalClaims,'anon'),/permission denied/);
  await deny(()=>as(null,'select public.workflow_options($1,null)',[q.id]),'AUTH_REQUIRED');
  await deny(()=>as('student','select public.workflow_options($1,null)',[q.id],{is_anonymous:true,amr:[{method:'password'}]}),'AUTH_REQUIRED');
  await deny(()=>as('student','select public.workflow_options($1,null)',[q.id],{is_anonymous:false,user_metadata:{provider:'email'}}),'FORBIDDEN');
  await deny(()=>as('student','select public.workflow_options($1,null)',[q.id],{is_anonymous:false,amr:[{method:'oauth'}],user_metadata:{email:'fake@tip.edu.ph',email_verified:true}}),'FORBIDDEN');
  await deny(()=>options('missing',q.id),'FORBIDDEN');
  await deny(()=>options('incomplete',q.id),'PROFILE_INCOMPLETE');
  await deny(()=>options('terms',q.id),'TERMS_REQUIRED');
  await assert.rejects(()=>as('student','select * from goserve_private.policies'),/permission denied/);
  await assert.rejects(()=>as('student',"update goserve_private.policies set value='{}'"),/permission denied/);
});

await test('exactly one ID is required before lookup',async()=>{
  await deny(()=>options('student'),'VALIDATION_FAILED');
  await deny(()=>options('student',q.id,r.id),'VALIDATION_FAILED');
});

await test('quote discovery enforces Student ownership and current active service/mode',async()=>{
  await deny(()=>options('other',q.id),'NOT_FOUND');
  await deny(()=>options('staff',q.id),'FORBIDDEN');
  await deny(()=>options('student',randomUUID()),'NOT_FOUND');
  const staleService=await service();const staleQuote=await quote(staleService);
  for(const change of ["version=version+1","active=false","delivery_modes=ARRAY['digital']"]) {
    await sql('update public.services set '+change+' where id=$1',[staleService.id]);
    await deny(()=>options('student',staleQuote.id),'QUOTE_STALE');
    await sql("update public.services set version=1,active=true,delivery_modes=ARRAY['physical','digital'] where id=$1",[staleService.id]);
  }
});

await test('quote projection is exact and uses configured cash membership without inferring payment readiness',async()=>{
  await remove('cash_offices');
  assert.deepEqual(await options('student',q.id),{quote_id:q.id,service_version:q.service_version,cash:blocked('CONFIGURATION_REQUIRED'),evidence:blocked('CONFIGURATION_REQUIRED',true)});
  for(const membership of [['OFF-REG'],{'OFF-REG':false}]) {
    await policy('cash_offices',membership);
    assert.deepEqual((await options('student',q.id)).cash,available);
  }
  for(const membership of [[],null,7,true,['OFF-ACC']]) {
    await policy('cash_offices',membership);
    assert.deepEqual((await options('student',q.id)).cash,blocked('CONFIGURATION_REQUIRED'));
  }
  assert.deepEqual((await options('student',freeQuote.id)).cash,blocked('POLICY_BLOCKED'));
  assert.deepEqual((await options('student',pendingQuote.id)).cash,blocked('CONFIGURATION_REQUIRED'));
});

await test('file choices project only supported usable configured MIME and declared-byte maximum',async()=>{
  const name='file_'+required.id+'_evidence';
  await policy(name,{...filePolicy,mime_types:['application/pdf','image/png','image/jpeg','text/plain','application/pdf'],private_marker:'never-project'});
  assert.deepEqual((await options('student',q.id)).evidence,{...available,mime_types:['application/pdf','image/png','image/jpeg'],max_bytes:filePolicy.max_bytes});
  await policy(name,{...filePolicy,mime_types:['application/pdf'],max_pixels:undefined});
  assert.deepEqual((await options('student',q.id)).evidence,{...available,mime_types:['application/pdf'],max_bytes:filePolicy.max_bytes});
});

await test('malformed file configuration fails closed without leaking casts or raw policy',async()=>{
  const name='file_'+required.id+'_evidence';
  for(const value of [null,[],true,{},
    {...filePolicy,verifier:'unknown'}, {...filePolicy,mime_types:'application/pdf'},
    {...filePolicy,mime_types:[]}, {...filePolicy,mime_types:['text/plain']},
    {...filePolicy,max_bytes:'16384'}, {...filePolicy,max_bytes:0}, {...filePolicy,max_bytes:1.5},
    {...filePolicy,max_bytes:9007199254740992}, {...filePolicy,max_bytes:null},
    {...filePolicy,max_pixels:'1000000'}, {...filePolicy,max_pixels:0},
    {...filePolicy,upload_seconds:'private-invalid-cast'}, {...filePolicy,upload_seconds:0},
    {...filePolicy,upload_seconds:1e100}]) {
    await policy(name,value);
    assert.deepEqual((await options('student',q.id)).evidence,blocked('CONFIGURATION_REQUIRED',true));
  }
});

await test('request access matches owner or assigned office requests capability and action keys remain scoped',async()=>{
  await deny(()=>options('other',null,r.id),'NOT_FOUND');
  await deny(()=>options('wrong',null,r.id),'NOT_FOUND');
  await deny(()=>options('limited',null,r.id),'NOT_FOUND');
  await deny(()=>options('teller',null,r.id),'NOT_FOUND');
  await deny(()=>options('student',null,randomUUID()),'NOT_FOUND');
  const owner=await options('student',null,r.id);
  assert.deepEqual(Object.keys(owner).sort(),['actions','request_id','request_version']);
  assert.deepEqual(Object.keys(owner.actions).sort(),['cancel','pickup_claim']);
  const staff=await options('staff',null,r.id);
  assert.deepEqual(Object.keys(staff.actions).sort(),['cancel','deliverable_upload','reject']);
  await sql("insert into goserve_private.capabilities values($1,'OFF-REG','requests')",[ids.limited]);
  assert.deepEqual(Object.keys((await options('limited',null,r.id)).actions).sort(),['cancel','deliverable_upload','reject']);
  await sql('delete from goserve_private.capabilities where profile_id=$1',[ids.limited]);
});

await test('cancellation and rejection project configured roles, states, windows and terminal state',async()=>{
  await remove('request_cancelled');await remove('request_rejected');
  assert.deepEqual((await options('student',null,r.id)).actions.cancel,blocked('CONFIGURATION_REQUIRED'));
  await policy('request_cancelled',{roles:['student'],states:['Submitted'],window_seconds:3600});
  await policy('request_rejected',{roles:['staff'],states:['Submitted'],window_seconds:3600});
  assert.deepEqual((await options('student',null,r.id)).actions.cancel,available);
  assert.deepEqual((await options('staff',null,r.id)).actions.cancel,blocked('POLICY_BLOCKED'));
  assert.deepEqual((await options('staff',null,r.id)).actions.reject,available);
  await sql("update public.requests set created_at=now()-interval '2 hours' where id=$1",[r.id]);
  assert.deepEqual((await options('student',null,r.id)).actions.cancel,blocked('POLICY_BLOCKED'));
  await sql('update public.requests set created_at=now() where id=$1',[r.id]);
  for(const value of [{},null,{roles:['student'],states:['Submitted'],window_seconds:'bad'}, {roles:['student'],states:['Submitted'],window_seconds:1e100}]) {
    await policy('request_cancelled',value);
    assert.deepEqual((await options('student',null,r.id)).actions.cancel,blocked('CONFIGURATION_REQUIRED'));
  }
  const terminal=await request(free,'physical','Cancelled');
  await sql('update public.requests set terminal_at=now() where id=$1',[terminal.id]);
  assert.deepEqual((await options('student',null,terminal.id)).actions.cancel,blocked('INVALID_STATE'));
});

await test('pickup projection checks physical Ready, paid/free, validity and expired-claim replacement without issuing a code',async()=>{
  assert.deepEqual((await options('student',null,r.id)).actions.pickup_claim,blocked('INVALID_STATE'));
  await remove('pickup');
  assert.deepEqual((await options('student',null,ready.id)).actions.pickup_claim,blocked('CONFIGURATION_REQUIRED'));
  for(const value of [{validity_seconds:'bad'},{validity_seconds:0},{validity_seconds:2147483648},{validity_seconds:300,replace_expired:'bad'}]) {
    await policy('pickup',value);
    assert.deepEqual((await options('student',null,ready.id)).actions.pickup_claim,blocked('CONFIGURATION_REQUIRED'));
  }
  await policy('pickup',{validity_seconds:300,replace_expired:false});
  assert.deepEqual((await options('student',null,ready.id)).actions.pickup_claim,available);
  const unpaid=await request(required,'physical','Ready for Pickup/Download');
  assert.deepEqual((await options('student',null,unpaid.id)).actions.pickup_claim,blocked('PAYMENT_REQUIRED'));
  await sql("insert into goserve_private.pickup_claims(request_id,code_hash,display_code,expires_at) values($1,'fixture-hash','private-fixture-code',now()+interval '1 hour')",[ready.id]);
  assert.deepEqual((await options('student',null,ready.id)).actions.pickup_claim,available);
  await sql("update goserve_private.pickup_claims set expires_at=now()-interval '1 second' where request_id=$1",[ready.id]);
  assert.deepEqual((await options('student',null,ready.id)).actions.pickup_claim,blocked('POLICY_BLOCKED'));
  await policy('pickup',{validity_seconds:300,replace_expired:true});
  assert.deepEqual((await options('student',null,ready.id)).actions.pickup_claim,available);
});

await test('deliverable projection uses active digital service, current upload statuses and PDF only',async()=>{
  await policy('file_'+free.id+'_deliverable',filePolicy);
  assert.deepEqual((await options('staff',null,digital.id)).actions.deliverable_upload,{...available,mime_types:['application/pdf'],max_bytes:filePolicy.max_bytes});
  assert.deepEqual((await options('staff',null,r.id)).actions.deliverable_upload,blocked('INVALID_STATE',true));
  await sql('update public.services set active=false where id=$1',[free.id]);
  assert.deepEqual((await options('staff',null,digital.id)).actions.deliverable_upload,blocked('INVALID_STATE',true));
  await sql('update public.services set active=true where id=$1',[free.id]);
});

await test('all discovery paths preserve public and private row counts and do not expose secrets',async()=>{
  const counts=async()=>{
    const result={};
    for(const table of ['public.request_quotes','public.requests','public.request_files','public.payments','public.request_events','public.notifications','goserve_private.file_objects','goserve_private.pickup_claims','goserve_private.pickup_reviews','goserve_private.operation_keys','goserve_private.financial_audit','goserve_private.downloads']) {
      result[table]=(await sql('select count(*)::integer n from '+table))[0].n;
    }
    return result;
  };
  const before=await counts();
  for(let i=0;i<2;i++) for(const value of [await options('student',q.id),await options('student',null,ready.id),await options('staff',null,digital.id)]) {
    assert.doesNotMatch(JSON.stringify(value),/private-fixture-code|code_hash|object_path|display_code|private_marker|verifier|max_pixels|upload_seconds|@demo.com/);
  }
  assert.deepEqual(await counts(),before);
  const definition=(await sql("select pg_get_functiondef('public.workflow_options(uuid,uuid)'::regprocedure) definition"))[0].definition;
  assert.doesNotMatch(definition,/\bfor\s+(update|share)\b|request_row\s*\(|begin_operation\s*\(|\b(insert|update|delete)\s+(into|from|public\.|goserve_private\.)/i);
});

await test('configuration/status/version changes after advisory remain denied by existing mutations',async()=>{
  await policy('cash_offices',['OFF-REG']);
  assert.equal((await options('student',q.id)).cash.available,true);
  await remove('cash_offices');
  await deny(()=>call('student','submit_request',[q.id,[],'','cash',randomUUID()]),'CONFIGURATION_REQUIRED');
  await policy('request_cancelled',{roles:['student'],states:['Submitted'],window_seconds:3600});
  assert.equal((await options('student',null,r.id)).actions.cancel.available,true);
  await remove('request_cancelled');
  await deny(()=>call('student','transition_request',[r.id,r.version,'Cancelled','Synthetic reason',randomUUID()]),'CONFIGURATION_REQUIRED');
  assert.equal((await options('student',null,ready.id)).actions.pickup_claim.available,true);
  await sql("update public.requests set status='Processing',version=version+1 where id=$1",[ready.id]);
  await deny(()=>call('student','pickup_claim',[ready.id]),'INVALID_STATE');
  const paymentReady=await request(required,'physical','Ready for Pickup/Download');
  await sql("insert into public.payments(request_id,method,status,amount,currency) values($1,'cash','paid',5,'TEST')",[paymentReady.id]);
  assert.equal((await options('student',null,paymentReady.id)).actions.pickup_claim.available,true);
  await sql("update public.payments set status='pending',version=version+1 where request_id=$1",[paymentReady.id]);
  await deny(()=>call('student','pickup_claim',[paymentReady.id]),'PAYMENT_REQUIRED');
  assert.equal((await options('staff',null,digital.id)).actions.deliverable_upload.available,true);
  await sql("update public.requests set status='Submitted',version=version+1 where id=$1",[digital.id]);
  await deny(()=>call('staff','prepare_file',[free.id,digital.id,'deliverable',null,'application/pdf',100,randomUUID()]),'FORBIDDEN');
  await policy('file_'+free.id+'_evidence',filePolicy);
  assert.equal((await options('student',freeQuote.id)).evidence.available,true);
  await remove('file_'+free.id+'_evidence');
  await deny(()=>call('student','prepare_file',[free.id,null,'evidence',null,'application/pdf',100,randomUUID()]),'CONFIGURATION_REQUIRED');
  await sql('update public.services set version=version+1 where id=$1',[free.id]);
  await deny(()=>options('student',freeQuote.id),'QUOTE_STALE');
  await deny(()=>call('student','submit_request',[freeQuote.id,[],'',null,randomUUID()]),'QUOTE_STALE');
});

const rolePolicy={roles:['student','staff'],states:['Submitted','Awaiting Payment','Processing'],window_seconds:604800,
 role_rules:{student:{roles:['student'],states:['Submitted','Awaiting Payment'],window_seconds:86400},staff:{roles:['staff'],states:['Submitted','Awaiting Payment','Processing'],window_seconds:604800}}};
await test('role-specific cancellation state is identical in advisory options and transactional enforcement',async()=>{
 await policy('request_cancelled',rolePolicy);const record=await request(free,'physical','Processing');
 assert.deepEqual((await options('student',null,record.id)).actions.cancel,blocked('POLICY_BLOCKED'));
 assert.deepEqual((await options('staff',null,record.id)).actions.cancel,available);
 await deny(()=>call('student','transition_request',[record.id,record.version,'Cancelled','Synthetic role check',randomUUID()]),'POLICY_BLOCKED');
 assert.equal((await call('staff','transition_request',[record.id,record.version,'Cancelled','Synthetic staff cancellation',randomUUID()])).status,'Cancelled');
});
await test('Student 24-hour cancellation cannot inherit the Staff seven-day window',async()=>{
 await policy('request_cancelled',rolePolicy);const record=await request(free);
 await sql("update public.requests set created_at=now()-interval '2 days' where id=$1",[record.id]);
 assert.deepEqual((await options('student',null,record.id)).actions.cancel,blocked('POLICY_BLOCKED'));
 assert.deepEqual((await options('staff',null,record.id)).actions.cancel,available);
 await deny(()=>call('student','transition_request',[record.id,record.version,'Cancelled','Synthetic expired window',randomUUID()]),'POLICY_BLOCKED');
});
await test('missing role override cannot fall back to a broader global cancellation policy',async()=>{
 const p=structuredClone(rolePolicy);delete p.role_rules.student;await policy('request_cancelled',p);const record=await request(free);
 assert.equal((await options('student',null,record.id)).actions.cancel.available,false);
 await assert.rejects(()=>call('student','transition_request',[record.id,record.version,'Cancelled','Synthetic absent override',randomUUID()]));
});
await db.close();
