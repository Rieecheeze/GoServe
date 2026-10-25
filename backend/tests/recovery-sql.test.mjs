import assert from 'node:assert/strict';
import { randomUUID,randomBytes,createHash } from 'node:crypto';
import { readFile,readdir } from 'node:fs/promises';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import test from 'node:test';
import {fixtureSql} from './sql-fixture.mjs';
let runtime,cryptoExtension;
try { runtime=await import('@electric-sql/pglite'); } catch { runtime=await import('../../.tools/sql-validation/node_modules/@electric-sql/pglite/dist/index.js'); }
try { cryptoExtension=await import('@electric-sql/pglite/contrib/pgcrypto'); } catch { cryptoExtension=await import('../../.tools/sql-validation/node_modules/@electric-sql/pglite/dist/contrib/pgcrypto.js'); }
const db=await runtime.PGlite.create({extensions:{pgcrypto:cryptoExtension.pgcrypto}});
const query=async(sql,args=[])=>(await db.query(sql,args)).rows;
await db.exec(fixtureSql);
await db.exec('create table auth.sessions(id uuid primary key,user_id uuid references auth.users(id));');
await db.exec(await readFile(new URL('../schema.sql',import.meta.url),'utf8'));
for(const file of(await readdir(new URL('../migrations/',import.meta.url))).filter(f=>f.endsWith('.sql')).sort())await db.exec(await readFile(new URL('../migrations/'+file,import.meta.url),'utf8'));
const owners=[randomUUID(),randomUUID(),randomUUID()];
for(let i=0;i<3;i++){
  await query('insert into auth.users(id,email,email_confirmed_at)values($1,$2,now())',[owners[i],`owner${i}@demo.goserve.test`]);
  await query("insert into public.profiles(id,email,role,is_demo,profile_completed_at)values($1,$2,'student',true,now())",[owners[i],`owner${i}@demo.goserve.test`]);
}
await query("insert into public.terms_documents values('recovery-v1','Synthetic test terms','fixture',now(),true)");
for(const who of owners)await query("insert into goserve_private.terms_acceptances values($1,'recovery-v1',now(),$1)",[who]);
const call=async(command,args={})=>(await query('select public.recovery_server_operation($1,$2::jsonb)r',[command,JSON.stringify(args)]))[0].r;
const hash=value=>createHash('sha256').update(value).digest('hex');
const hashes=()=>Array.from({length:10},()=>hash(randomBytes(20)));
const version=async owner=>(await query('select version from goserve_private.recovery_accounts where owner_id=$1',[owner]))[0].version;
async function generate(owner,digests=hashes()){
  const result=await call('generate',{owner_id:owner,digests});
  await call('ack',{owner_id:owner,generation:result.generation});
  return {result,digests};
}
await test('recovery schema denies public service operations and keeps private tables under RLS',async()=>{
  assert.ok((await query("select to_regprocedure('public.recovery_server_operation(text,jsonb)')r"))[0].r,'Recovery operations are missing');
  const tables=await query("select relname,relrowsecurity from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='goserve_private' and relname like 'recovery_%' and relkind='r'");
  assert.ok(tables.length>=6);assert.ok(tables.every(t=>t.relrowsecurity));
  await db.exec('set role anon');try{await assert.rejects(()=>call('status',{owner_id:owners[0]}),/permission denied/);}finally{await db.exec('reset role');}
});
let first,second;
await test('generation requires explicit save acknowledgment and never returns old plaintext',async()=>{
  assert.equal((await call('status',{owner_id:owners[0]})).codes_configured,false);
  const digests=hashes();const result=await call('generate',{owner_id:owners[0],digests});
  assert.equal(result.remaining_codes,10);assert.equal(result.codes_acknowledged,false);assert.equal(result.codes,undefined);
  await assert.rejects(()=>call('ack',{owner_id:owners[1],generation:result.generation}),/RECOVERY_GRANT_INVALID/);
  assert.equal((await call('ack',{owner_id:owners[0],generation:result.generation})).requires_setup,false);
  first={result,digests};second=await generate(owners[1]);
});
await test('codes are globally unique, owner-bound and only redeemable once in queued PGlite calls',async()=>{
  const args={owner_id:owners[0],version:await version(owners[0]),code_digest:first.digests[0],grant_digest:hash('grant-a'),challenge:'a'.repeat(43)};
  await assert.rejects(()=>call('redeem',{...args,owner_id:owners[1]}),/RECOVERY_PROOF_INVALID/);
  const results=await Promise.allSettled([call('redeem',args),call('redeem',{...args,grant_digest:hash('grant-b')})]);
  assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
  const duplicate=await call('generate',{owner_id:randomUUID(),digests:hashes()}).catch(e=>e);
  assert.ok(duplicate instanceof Error);
  await assert.rejects(()=>call('generate',{owner_id:owners[1],digests:first.digests}),/duplicate|CONFLICT/);
});
await test('regeneration needs no old code and invalidates all prior unused codes',async()=>{
  const fresh=hashes();await call('generate',{owner_id:owners[0],digests:fresh});
  await assert.rejects(()=>call('redeem',{owner_id:owners[0],version:0,code_digest:first.digests[2],grant_digest:hash('old-set'),challenge:'a'.repeat(43)}),/RECOVERY_PROOF_INVALID/);
  await assert.rejects(async()=>call('redeem',{owner_id:owners[0],version:await version(owners[0]),code_digest:first.digests[2],grant_digest:hash('old-set'),challenge:'a'.repeat(43)}),/RECOVERY_PROOF_INVALID/);
  await assert.rejects(()=>call('ack',{owner_id:owners[0],generation:first.result.generation}),/RECOVERY_GRANT_INVALID/);
  first.digests=fresh;
});
await test('strict session enforcement must precede public proof and first reset claim',async()=>{
  const args={email:'owner0@demo.goserve.test',account_digest:hash('preactivation'),ip_digest:hash('preactivation-ip')};
  assert.equal((await call('context',args)).configuration_required,true);
  assert.equal((await query("select sum(attempts)::int n from goserve_private.recovery_rates where key_digest=$1",[args.account_digest]))[0].n,1);
  await call('redeem',{owner_id:owners[0],version:await version(owners[0]),code_digest:first.digests[0],grant_digest:hash('reset'),challenge:'a'.repeat(43)});
  await assert.rejects(()=>call('claim',{grant_digest:hash('reset'),challenge:'a'.repeat(43),operation_id:randomUUID()}),/CONFIGURATION_REQUIRED/);
  assert.equal((await query('select count(*)::int n from goserve_private.recovery_jobs'))[0].n,0);
  await query('update goserve_private.recovery_settings set enforce_sessions=true');
});
await test('grant binding and atomic claims allow only one mutation in queued PGlite calls',async()=>{
  const operation_id=randomUUID(),args={grant_digest:hash('reset'),challenge:'a'.repeat(43),operation_id};
  await assert.rejects(()=>call('claim',{...args,challenge:'b'.repeat(43)}),/RECOVERY_GRANT_INVALID/);
  const results=await Promise.all([call('claim',args),call('claim',args)]);assert.equal(results.filter(r=>r.should_mutate).length,1);
  await assert.rejects(()=>call('claim',{...args,operation_id:randomUUID()}),/RECOVERY_GRANT_INVALID/);
  assert.equal((await call('result',args)).state,'pending');
  await assert.rejects(()=>call('generate',{owner_id:owners[0],digests:hashes()}),/RECOVERY_OPERATION_PENDING/);
  await assert.rejects(async()=>call('redeem',{owner_id:owners[0],version:await version(owners[0]),code_digest:first.digests[1],grant_digest:hash('blocked-by-pending'),challenge:'a'.repeat(43)}),/RECOVERY_PROOF_INVALID/);
  await call('finish',{operation_id,state:'completed'});assert.equal((await call('result',args)).state,'completed');
});
await test('durable account and IP budgets apply equally to unknown accounts',async()=>{
  const args={email:'unknown@demo.goserve.test',account_digest:hash('unknown'),ip_digest:hash('unknown-ip')};
  for(let i=0;i<5;i++)assert.equal((await call('context',args)).blocked,false);
  assert.equal((await call('context',args)).blocked,true);
  for(let i=0;i<30;i++)await call('context',{email:`unknown${i}@demo.goserve.test`,account_digest:hash(`unknown${i}`),ip_digest:hash('shared-ip')});
  assert.equal((await call('context',{...args,account_digest:hash('new-account'),ip_digest:hash('shared-ip')})).blocked,true);
});
await test('expired grants cannot authorize a password mutation',async()=>{
  await query('delete from goserve_private.recovery_rates');
  await call('redeem',{owner_id:owners[1],version:await version(owners[1]),code_digest:second.digests[1],grant_digest:hash('expired'),challenge:'a'.repeat(43)});
  await query("update goserve_private.recovery_grants set expires_at=now()-interval '1 second'where grant_digest=$1",[hash('expired')]);
  await assert.rejects(()=>call('claim',{grant_digest:hash('expired'),challenge:'a'.repeat(43),operation_id:randomUUID()}),/RECOVERY_GRANT_INVALID/);
});
async function account(owner,sessionId,sql='select public.account_view()r'){
  await db.exec('begin');try{
    await db.exec('set local role authenticated');
    await query("select set_config('request.jwt.claim.sub',$1,true),set_config('request.jwt.claims',$2,true)",[owner,JSON.stringify({is_anonymous:false,amr:[{method:'password'}],session_id:sessionId})]);
    const result=await query(sql);await db.exec('commit');return result[0].r;
  }catch(error){await db.exec('rollback');throw error;}
}
await test('staged gates require saved code setup and deny revoked JWTs without changing Auth tables',async()=>{
  const sessionId=randomUUID();await query('insert into auth.sessions values($1,$2)',[sessionId,owners[2]]);
  await query('update goserve_private.recovery_settings set enforce_readiness=true,enforce_sessions=true');
  assert.equal((await account(owners[2],sessionId)).next_action,'setup_recovery');
  await assert.rejects(()=>account(owners[2],sessionId,'select public.profile_avatar()r'),/RECOVERY_SETUP_REQUIRED/);
  await query('delete from auth.sessions where id=$1',[sessionId]);
  await assert.rejects(()=>account(owners[2],sessionId),/AUTH_INVALID|FORBIDDEN/);
});
await test('an older Terms acceptance cannot bypass the current policy version',async()=>{
  await query("update public.terms_documents set active=false where active");
  await query("insert into public.terms_documents values('recovery-v2','Synthetic revised terms','fixture2',now(),true)");
  const sessionId=randomUUID();await query('insert into auth.sessions values($1,$2)',[sessionId,owners[0]]);
  assert.equal((await account(owners[0],sessionId)).next_action,'accept_terms');
  await assert.rejects(()=>account(owners[0],sessionId,'select public.profile_avatar()r'),/TERMS_REQUIRED/);
  await assert.rejects(()=>call('generate',{owner_id:owners[0],digests:hashes()}),/TERMS_REQUIRED/);
  await assert.rejects(()=>call('ack',{owner_id:owners[1],generation:second.result.generation}),/TERMS_REQUIRED/);
});
await test('generation and acknowledgment require a completed profile without requiring saved codes',async()=>{
  await query("insert into goserve_private.terms_acceptances values($1,'recovery-v2',now(),$1)",[owners[1]]);
  await query('update public.profiles set profile_completed_at=null where id=$1',[owners[1]]);
  await assert.rejects(()=>call('generate',{owner_id:owners[1],digests:hashes()}),/PROFILE_INCOMPLETE/);
  await assert.rejects(()=>call('ack',{owner_id:owners[1],generation:second.result.generation}),/PROFILE_INCOMPLETE/);
  await query('update public.profiles set profile_completed_at=now() where id=$1',[owners[1]]);
});
await test('public lookup uses confirmed Auth email instead of mutable or stale profile email',async()=>{
  await query('delete from goserve_private.recovery_rates');
  await query("update public.profiles set email='stale@demo.goserve.test'where id=$1",[owners[1]]);
  const lookup=async email=>call('context',{email,account_digest:hash(email),ip_digest:hash('canonical-lookup')});
  assert.equal((await lookup('owner1@demo.goserve.test')).owner_id,owners[1]);
  assert.equal((await lookup('stale@demo.goserve.test')).owner_id,undefined);
  await query('update auth.users set email_confirmed_at=null where id=$1',[owners[1]]);
  assert.equal((await lookup('owner1@demo.goserve.test')).owner_id,undefined);
});
await test('using the last code requires a newly generated acknowledged set',async()=>{
  await query('update goserve_private.recovery_settings set enforce_readiness=false,enforce_sessions=false');
  await query('delete from goserve_private.recovery_rates');
  await query("insert into goserve_private.terms_acceptances values($1,'recovery-v2',now(),$1)",[owners[2]]);
  const set=await generate(owners[2]);
  for(let i=0;i<10;i++) {
    const version=(await query('select version from goserve_private.recovery_accounts where owner_id=$1',[owners[2]]))[0].version;
    await call('redeem',{owner_id:owners[2],version,code_digest:set.digests[i],grant_digest:hash('exhaust-'+i),challenge:'a'.repeat(43)});
  }
  const status=await call('status',{owner_id:owners[2]});assert.equal(status.remaining_codes,0);assert.equal(status.requires_setup,true);
});
await test('file Edge branches respect real SQL readiness before using service-role tickets',async()=>{
  const who=owners[2],sessionId=randomUUID(),bytes=new TextEncoder().encode('%PDF-readiness-fixture\n%%EOF');
  await query('insert into auth.sessions values($1,$2)',[sessionId,who]);
  const service=(await query("insert into public.services(office_id,name,delivery_modes,fee_state,amount,currency)values('OFF-REG','Synthetic file readiness',ARRAY['digital'],'free',0,null)returning id"))[0].id;
  const requestId=(await query("insert into public.requests(owner_id,office_id,service_id,snapshot,delivery_mode,status)values($1,'OFF-REG',$2,'{\"fee_state\":\"free\"}','digital','Ready for Pickup/Download')returning id",[who,service]))[0].id;
  const evidence=(await query("insert into public.request_files(owner_id,service_id,kind,mime_type,size_bytes,verification)values($1,$2,'evidence','application/pdf',$3,'verified')returning id",[who,service,bytes.length]))[0].id;
  const deliverable=(await query("insert into public.request_files(owner_id,service_id,request_id,kind,mime_type,size_bytes,verification)values($1,$2,$3,'deliverable','application/pdf',$4,'verified')returning id",[who,service,requestId,bytes.length]))[0].id;
  for(const file of [evidence,deliverable])await query("insert into goserve_private.file_objects(file_id,object_path,sha256,expires_at,verified_at)values($1::uuid,($1::uuid)::text,$2,now()+interval '1 hour',now())",[file,hash(bytes)]);
  const download=(await query("insert into goserve_private.downloads(file_id,owner_id,expires_at)values($1,$2,now()+interval '1 hour')returning id",[deliverable,who]))[0].id;
  const context={Request,Response,URL,Uint8Array,TextDecoder,ReadableStream,crypto,AbortSignal};
  for(const name of ['v23','files'])runInNewContext(stripTypeScriptTypes(await readFile(new URL(`../lib/${name}.ts`,import.meta.url),'utf8'),{mode:'transform'}).replace(/^import[^\n]+\n/gm,'').replace(/^export /gm,''),context);
  const handlers={};for(const name of ['request-file','download-deliverable']){
    context.serve=handler=>{handlers[name]=handler;};
    runInNewContext(stripTypeScriptTypes(await readFile(new URL(`../functions/${name}/index.ts`,import.meta.url),'utf8'),{mode:'transform'}).replace(/^import[^\n]+\n/gm,''),context);
  }
  let privilegedCalls=0;
  const deps={admin:{auth:{getUser:async()=>({data:{user:{id:who,is_anonymous:false}},error:null})},
    rpc:async(name,args)=>{
      privilegedCalls++;await db.exec('begin');
      try{
        await db.exec('set local role service_role');
        const signature=name==='file_server_operation'?'select public.file_server_operation($1,$2,$3,$4)r':'select public.download_server_operation($1,$2,$3)r';
        const values=name==='file_server_operation'?[args.p_actor,args.p_file_id,args.p_command,args.p_sha256??null]:[args.p_actor,args.p_download_id,args.p_complete??false];
        const data=(await query(signature,values))[0].r;await db.exec('commit');return {data,error:null};
      }catch(error){await db.exec('rollback');return {data:null,error:{message:error.message,code:error.code}};}
    },storage:{from:()=>({download:async()=>({data:new Blob([bytes]),error:null})})}},
    userClient:()=>({rpc:async name=>{assert.equal(name,'account_view');return {data:await account(who,sessionId),error:null};}})};
  const branches=[
    ['request-file',{'X-GoServe-Upload':evidence},bytes],
    ['request-file',{},JSON.stringify({kind:'download',file_id:evidence})],
    ['download-deliverable',{},JSON.stringify({kind:'transfer',download_id:download})],
  ];
  const invoke=async branch=>{const response=await handlers[branch[0]](new Request('https://fixture/'+branch[0],{method:'POST',headers:{Authorization:'Bearer fixture',...branch[1]},body:branch[2]}),deps);await response.arrayBuffer();return response;};
  assert.equal((await account(who,sessionId)).next_action,'home');
  for(const branch of branches)assert.equal((await invoke(branch)).status,200);
  await query('update goserve_private.recovery_settings set enforce_readiness=true,enforce_sessions=true');
  assert.equal((await account(who,sessionId)).next_action,'setup_recovery');
  assert.equal(await account(who,sessionId,'select count(*)::int r from public.request_files'),0);
  for(const branch of branches){const before=privilegedCalls;await assert.rejects(()=>invoke(branch),/RECOVERY_SETUP_REQUIRED/);assert.equal(privilegedCalls,before);}
  await generate(who);
  assert.equal(await account(who,sessionId,'select count(*)::int r from public.request_files'),2);
  for(const branch of branches)assert.equal((await invoke(branch)).status,200);
  await query("update public.terms_documents set active=false where active");
  await query("insert into public.terms_documents values('recovery-v3','Synthetic file Terms','fixture3',now(),true)");
  for(const branch of branches){const before=privilegedCalls;await assert.rejects(()=>invoke(branch),/TERMS_REQUIRED/);assert.equal(privilegedCalls,before);}
  await query("insert into goserve_private.terms_acceptances values($1,'recovery-v3',now(),$1)",[who]);
  await query('update public.profiles set profile_completed_at=null where id=$1',[who]);
  for(const branch of branches){const before=privilegedCalls;await assert.rejects(()=>invoke(branch),/PROFILE_INCOMPLETE/);assert.equal(privilegedCalls,before);}
});
await db.close();
