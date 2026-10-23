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
const ids=Object.fromEntries(['student','other','staff','teller','wrongOffice','incomplete','terms','missing'].map(name=>[name,randomUUID()]));
const claims={is_anonymous:false,amr:[{method:'password'}]};
async function as(who,text,args=[],jwt=claims,role='authenticated') {
    await db.exec('begin');
    try {
        await db.exec(`set local role ${role}`);
        await sql("select set_config('request.jwt.claim.sub',$1,true),set_config('request.jwt.claims',$2,true)",[ids[who]??'',JSON.stringify(jwt)]);
        const rows=await sql(text,args); await db.exec('commit'); return rows;
    } catch(error) { await db.exec('rollback'); throw error; }
}
const call=async(who,name,args=[]) => (await as(who,`select public.${name}(${args.map((_,i)=>'$'+(i+1)).join(',')}) r`,args))[0].r;
const deny=(work,code) => assert.rejects(work,error=>error.message===code);
const policy=value=>sql("insert into goserve_private.policies(name,value) values('cash_offices',$1::jsonb) on conflict(name) do update set value=excluded.value",[JSON.stringify(value)]);
async function lookup(id) {
    let result;
    await assert.doesNotReject(async()=>{ result=await call('teller','cash_payment_by_id',[id]); },'A ready scoped Teller must have a canonical cash ID reader');
    return result;
}
await sql("insert into public.terms_documents values('fixture-cash-lookup','Synthetic cash lookup terms','fixture',now(),true)");
for(const [name,id] of Object.entries(ids)) {
    await sql('insert into auth.users(id,email) values($1,$2)',[id,name+'@demo.com']);
    if(name==='missing')continue;
    const role=name==='staff'?'staff':['teller','wrongOffice','incomplete','terms'].includes(name)?'teller':'student';
    const office=role==='student'?null:name==='staff' || name==='wrongOffice'?'OFF-REG':'OFF-CASH';
    await sql('insert into public.profiles(id,email,role,office_id,profile_completed_at) values($1,$2,$3,$4,case when $5 then null else now() end)',[id,name+'@demo.com',role,office,name==='incomplete']);
    if(name!=='terms')await sql("insert into goserve_private.terms_acceptances(profile_id,terms_version,actor_id) values($1,'fixture-cash-lookup',$1)",[id]);
}
const AMOUNT='12345678901234567890.123456789';
async function payment(office='OFF-REG',method='cash',status='pending') {
    const service=(await sql("insert into public.services(office_id,name,delivery_modes,fee_state,amount,currency) values($1,'Synthetic cash lookup service',ARRAY['physical'],'required',$2::numeric,'TEST') returning *",[office,AMOUNT]))[0];
    const request=(await sql("insert into public.requests(owner_id,office_id,service_id,snapshot,delivery_mode,notes,status) select $1,office_id,id,to_jsonb(services)||jsonb_build_object('amount',amount::text),'physical','private synthetic request notes','Awaiting Payment' from public.services where id=$2 returning *",[ids.student,service.id]))[0];
    const row=(await sql("insert into public.payments(request_id,method,status,amount,currency) values($1,$2,$3,$4::numeric,'TEST') returning *",[request.id,method,status,AMOUNT]))[0];
    return {service,request,row};
}
const cash=await payment(),outside=await payment('OFF-ACC'),egov=await payment('OFF-REG','egovpay');
await policy(['OFF-REG']);

await test('cash ID lookup has least-privilege public grants',async()=>{
    const grants=(await sql("select has_function_privilege('anon','public.cash_payment_by_id(uuid)','execute') anon,has_function_privilege('authenticated','public.cash_payment_by_id(uuid)','execute') authenticated"))[0];
    assert.deepEqual(grants,{anon:false,authenticated:true});
    await assert.rejects(()=>as('teller','select public.cash_payment_by_id($1)',[cash.row.id],claims,'anon'),/permission denied/);
});
await test('cash ID lookup uses current account readiness and trusted claims',async()=>{
    await deny(()=>call(null,'cash_payment_by_id',[cash.row.id]),'AUTH_REQUIRED');
    await deny(()=>as('teller','select public.cash_payment_by_id($1)',[cash.row.id],{...claims,is_anonymous:true}),'AUTH_REQUIRED');
    await deny(()=>call('missing','cash_payment_by_id',[cash.row.id]),'FORBIDDEN');
    await deny(()=>call('incomplete','cash_payment_by_id',[cash.row.id]),'PROFILE_INCOMPLETE');
    await deny(()=>call('terms','cash_payment_by_id',[cash.row.id]),'TERMS_REQUIRED');
});
await test('only a ready Teller at OFF-CASH can review by ID',async()=>{
    for(const actor of ['student','other','staff','wrongOffice'])await deny(()=>call(actor,'cash_payment_by_id',[cash.row.id]),'FORBIDDEN');
});
await test('unknown IDs and non-cash payments return the same NOT_FOUND boundary',async()=>{
    for(const id of [randomUUID(),null,egov.row.id])await deny(()=>call('teller','cash_payment_by_id',[id]),'NOT_FOUND');
});
await test('cash office policy is still authoritative and never exposed',async()=>{
    await deny(()=>call('teller','cash_payment_by_id',[outside.row.id]),'NOT_FOUND');
    await sql("delete from goserve_private.policies where name='cash_offices'");
    try { await deny(()=>call('teller','cash_payment_by_id',[cash.row.id]),'NOT_FOUND'); }
    finally { await policy(['OFF-REG']); }
    await assert.rejects(()=>as('teller','select * from goserve_private.policies'),/permission denied/);
});
await test('cash ID projection is exact, lossless and excludes request/profile content',async()=>{
    const value=await lookup(cash.row.id);
    assert.deepEqual(Object.keys(value).sort(),['payment_id','request_id','reference','office_id','service_name','amount','currency','status','version','confirmed_at'].sort());
    assert.equal(value.payment_id,cash.row.id); assert.equal(value.request_id,cash.request.id); assert.equal(value.reference,cash.request.reference);
    assert.equal(value.office_id,'OFF-REG'); assert.equal(value.service_name,'Synthetic cash lookup service'); assert.equal(value.amount,AMOUNT);
    assert.equal(value.currency,'TEST'); assert.equal(value.status,'pending'); assert.equal(value.version,1); assert.equal(value.confirmed_at,null);
});
await test('canonical ID lookup agrees with reference lookup before and after confirmation',async()=>{
    const item=await payment();
    assert.deepEqual(await lookup(item.row.id),await call('teller','cash_payment',[item.request.reference,false,null,null]));
    await call('teller','cash_payment',[item.request.reference,true,1,randomUUID()]);
    const current=await lookup(item.row.id); assert.equal(current.status,'paid'); assert.equal(current.version,2); assert.ok(current.confirmed_at);
    assert.deepEqual(current,await call('teller','cash_payment',[item.request.reference,false,null,null]));
});
await test('cash lookup cannot create payment, request progress, events or audit writes',async()=>{
    const snapshot=async()=>({payment:(await sql('select * from public.payments where id=$1',[cash.row.id]))[0],request:(await sql('select * from public.requests where id=$1',[cash.request.id]))[0],counts:(await sql('select (select count(*)::int from goserve_private.financial_audit) audit,(select count(*)::int from public.request_events) events,(select count(*)::int from public.notifications) notifications'))[0]});
    const before=await snapshot(); await lookup(cash.row.id); await lookup(cash.row.id); assert.deepEqual(await snapshot(),before);
});
await test('Teller ID lookup does not grant hidden request or profile reads',async()=>{
    await lookup(cash.row.id); assert.equal((await as('teller','select * from public.requests where id=$1',[cash.request.id])).length,0);
    assert.equal((await as('teller','select * from public.profiles where id=$1',[ids.student])).length,0);
});
await test('failed expired and cancelled cash remain reviewable without confirmation',async()=>{
    for(const status of ['failed','expired','cancelled']) {
        const item=await payment('OFF-REG','cash',status); const current=await lookup(item.row.id);
        assert.equal(current.status,status); assert.equal(current.version,1); assert.equal(current.confirmed_at,null);
    }
});

await db.close();
