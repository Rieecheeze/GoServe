import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {readFile,readdir} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import {setTimeout as delay} from 'node:timers/promises';
import {fixtureSql} from './sql-fixture.mjs';

const binary=process.env.GOSERVE_FIXTURE_PSQL,port=process.env.GOSERVE_FIXTURE_PORT,user=process.env.GOSERVE_FIXTURE_USER;
assert.ok(binary&&/^\d+$/.test(port??'')&&user,'Set the explicit isolated loopback PostgreSQL fixture environment.');
const database='goserve_egovpay_'+randomUUID().replaceAll('-',''),children=new Set();
function connection(db=database,application='egovpay_fixture'){
  const child=spawn(binary,['-X','-q','-A','-t','-v','ON_ERROR_STOP=1','-h','127.0.0.1','-p',port,'-U',user,'-d',db],{
    env:{...process.env,PGAPPNAME:application,PGOPTIONS:'-c statement_timeout=10000 -c lock_timeout=8000'},windowsHide:true,
  });children.add(child);let stdout='',stderr='';child.stdout.on('data',d=>stdout+=d);child.stderr.on('data',d=>stderr+=d);
  const done=new Promise((resolve,reject)=>{child.once('error',reject);child.once('close',code=>{children.delete(child);resolve({code,stdout,stderr});});});
  return {child,done,output:()=>stdout};
}
async function execute(sql,db=database){const s=connection(db);s.child.stdin.end(sql+'\n');const r=await s.done;assert.equal(r.code,0,r.stderr);return r.stdout.trim();}
async function eventually(fn,message){for(let n=0;n<100;n++){if(await fn())return;await delay(50);}assert.fail(message);}
const owner=randomUUID(),service=randomUUID();
const server="set role service_role;";
const op=(command,payment,key='null',details='null',token='null')=>`select public.egovpay_server_operation('${command}','${payment}','${owner}',${key},${token},${details});`;
async function fixture(){
  const request=randomUUID(),payment=randomUUID(),key=randomUUID(),uuid=randomUUID();
  await execute(`insert into public.requests(id,owner_id,office_id,service_id,snapshot,delivery_mode,status)
    values('${request}','${owner}','OFF-REG','${service}','{"amount":"5","currency":"PHP","fee_state":"required","is_demo":true}','physical','Awaiting Payment');
    insert into public.payments(id,request_id,method,amount,currency) values('${payment}','${request}','egovpay',5,'PHP');`);
  return {request,payment,key,uuid};
}
async function interleave(label,holderSql,contenderSql,verify){
  const holder=connection(database,label+'_holder');holder.child.stdin.write('begin;'+holderSql+'\n\\echo LOCK_HELD\n');
  await eventually(()=>holder.output().includes('LOCK_HELD'),'Holder failed to acquire lock');
  const contender=connection(database,label+'_contender');contender.child.stdin.end(contenderSql+'\n');
  try{
    await eventually(async()=>await execute(`select count(*) from pg_stat_activity where datname='${database}' and application_name='${label}_contender' and wait_event_type='Lock'`)==='1','Contender never entered a real lock wait');
    holder.child.stdin.end('commit;\n');assert.equal((await holder.done).code,0);await verify(await contender.done);
  }finally{if(!holder.child.stdin.destroyed)holder.child.stdin.end('rollback;\n');}
}
let created=false;
try{
  await execute('create database '+database+';','postgres');created=true;
  await execute(fixtureSql);await execute(await readFile(new URL('../schema.sql',import.meta.url),'utf8'));
  for(const file of (await readdir(new URL('../migrations/',import.meta.url))).filter(f=>f.endsWith('.sql')).sort())await execute(await readFile(new URL('../migrations/'+file,import.meta.url),'utf8'));
  await execute(`insert into auth.users(id,email) values('${owner}','egovpay@fixture.test');
    insert into public.profiles(id,email,role,is_demo,profile_completed_at) values('${owner}','egovpay@fixture.test','student',true,now());
    insert into public.terms_documents values('fixture','Synthetic fixture','fixture',now(),true);
    insert into goserve_private.terms_acceptances(profile_id,terms_version,actor_id) values('${owner}','fixture','${owner}');
    insert into public.services(id,office_id,name,delivery_modes,fee_state,amount,currency) values('${service}','OFF-REG','Synthetic payment',ARRAY['physical'],'required',5,'PHP');
    create table goserve_private.egovpay_configuration(provider text primary key,base_url text,demo_only boolean,integration_enabled boolean);
    insert into goserve_private.egovpay_configuration values('egovpay','https://platforms-api.e.gov.ph/egovpay',true,true);
    create function public.get_egovpay_server_configuration() returns jsonb language sql security definer set search_path='' as $$
      select to_jsonb(c)||'{"token":"synthetic-token","settlement_template_uuid":"20000000-0000-4000-8000-000000000002"}'::jsonb from goserve_private.egovpay_configuration c $$;
    revoke all on function public.get_egovpay_server_configuration() from public,anon,authenticated;
    grant execute on function public.get_egovpay_server_configuration() to service_role;`);
  const first=await fixture();
  await interleave('same_key',server+op('claim',first.payment,`'${first.key}'`),server+op('claim',first.payment,`'${first.key}'`),async r=>{
    assert.equal(r.code,0,r.stderr);assert.equal(JSON.parse(r.stdout).may_create,false);
  });
  assert.equal(await execute('select count(*) from goserve_private.egovpay_checkouts'),'1');
  console.log('PASS same-key concurrent claims serialize to one checkout and only one may_create');
  const second=await fixture();
  await interleave('different_key',server+op('claim',second.payment,`'${second.key}'`),server+op('claim',second.payment,`'${randomUUID()}'`),async r=>{
    assert.notEqual(r.code,0);assert.match(r.stderr,/IDEMPOTENCY_CONFLICT/);
  });
  console.log('PASS different-key concurrent claims cannot create a second checkout');
  const attach=async f=>{
    const c=JSON.parse(await execute(server+op('lookup',f.payment)));
    await execute(server+op('attach',f.payment,`'${f.key}'`,`'${JSON.stringify({uuid:f.uuid,url:'https://egovpay-pgi-dev.oueg.info/'+f.uuid})}'::jsonb`));
    return JSON.stringify({uuid:f.uuid,txnid:c.txnid,environment_type:'TEST',amount:'5.0000',currency:'PHP',payment_status:'PAID',paid_at:'October 23, 2026 06:38:24 AM'});
  };
  const paid=await attach(first);
  await interleave('paid_replay',server+op('observe',first.payment,'null',`'${paid}'::jsonb`),server+op('observe',first.payment,'null',`'${paid}'::jsonb`),async r=>{assert.equal(r.code,0,r.stderr);assert.equal(JSON.parse(r.stdout).status,'paid');});
  assert.equal(await execute(`select status||':'||version::text from public.payments where id='${first.payment}'`),'paid:2');
  assert.equal(await execute(`select count(*) from goserve_private.provider_payment_events where payment_id='${first.payment}' and verified_status='paid'`),'1');
  console.log('PASS concurrent verified PAID replays produce one ledger entry and one payment version increment');
  const cancelled=await attach(second);
  await interleave('cancel_before_paid',`update public.requests set status='Cancelled',terminal_at=now() where id='${second.request}';`,server+op('observe',second.payment,'null',`'${cancelled}'::jsonb`),async r=>{assert.equal(r.code,0,r.stderr);assert.equal(JSON.parse(r.stdout).status,'paid');});
  assert.equal(await execute(`select status||':'||version::text from public.payments where id='${second.payment}'`),'paid:2');
  assert.equal(await execute(`select count(*) from goserve_private.provider_payment_events where payment_id='${second.payment}' and verified_status='paid'`),'1');
  assert.equal(await execute(`select status from public.requests where id='${second.request}'`),'Cancelled');
  console.log('PASS cancellation wins request lock race; authoritative PAID is recorded without reopening the request');
}finally{
  for(const child of children)child.kill();
  if(created)await execute('drop database '+database+' with(force);','postgres');
}
