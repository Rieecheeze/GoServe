import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {readFile,readdir} from 'node:fs/promises';
import test from 'node:test';
import {fixtureSql} from './sql-fixture.mjs';
let runtime,crypto;
try{runtime=await import('@electric-sql/pglite');}catch{runtime=await import('../../.tools/sql-validation/node_modules/@electric-sql/pglite/dist/index.js');}
try{crypto=await import('@electric-sql/pglite/contrib/pgcrypto');}catch{crypto=await import('../../.tools/sql-validation/node_modules/@electric-sql/pglite/dist/contrib/pgcrypto.js');}
await test('existing live department expansion survives the missing core and photo rollout without inventing account completion',async()=>{
 const db=await runtime.PGlite.create({extensions:{pgcrypto:crypto.pgcrypto}});
 const sql=async(t,a=[])=>(await db.query(t,a)).rows;
 try{
  await db.exec(fixtureSql);await db.exec(await readFile(new URL('../schema.sql',import.meta.url),'utf8'));
  await db.exec(await readFile(new URL('../migrations/20261020162841_profile_department.sql',import.meta.url),'utf8'));
  const ids=[randomUUID(),randomUUID(),randomUUID()];
  for(const [i,id] of ids.entries()){
   await sql('insert into auth.users(id,email) values($1,$2)',[id,'rollout-'+i+'@demo.goserve.test']);
   await sql("insert into public.profiles(id,email,role,office_id,staff_capability,is_demo,department_id,consent_accepted_at) values($1,$2,$3,$4,$5,true,$6,'2026-10-20T00:00:00Z')",[id,'rollout-'+i+'@demo.goserve.test',i===0?'student':i===1?'staff':'teller',i===1?'DEP-CCS':i===2?'OFF-CASH':null,i===1?'secretary':null,i<2?'DEP-CCS':null]);
  }
  const projection='id,email,role,office_id,staff_capability,is_demo,department_id,consent_accepted_at';
  const before=await sql('select '+projection+' from public.profiles order by id');
  for(const f of(await readdir(new URL('../migrations/',import.meta.url))).filter(x=>x.endsWith('.sql') && !x.endsWith('_profile_department.sql')).sort())await db.exec(await readFile(new URL('../migrations/'+f,import.meta.url),'utf8'));
  assert.deepEqual(await sql('select '+projection+' from public.profiles order by id'),before);
  assert.deepEqual(await sql('select profile_completed_at,avatar_path,avatar_version from public.profiles order by id'),ids.map(()=>({profile_completed_at:null,avatar_path:null,avatar_version:0})));
  assert.equal((await sql('select count(*)::int n from public.terms_documents'))[0].n,0);
  assert.equal((await sql('select count(*)::int n from goserve_private.terms_acceptances'))[0].n,0);
  await db.exec('set role authenticated');
  await sql("select set_config('request.jwt.claim.sub',$1,false),set_config('request.jwt.claims',$2,false)",[ids[0],JSON.stringify({is_anonymous:false,amr:[{method:'password'}]})]);
  const account=(await sql('select public.account_view() r'))[0].r;
  assert.equal(account.department_id,'DEP-CCS');assert.equal(account.role,'student');assert.equal(account.office_id,null);assert.equal(account.next_action,'accept_terms');assert.equal(account.profile_complete,false);
  await assert.rejects(()=>sql('select public.profile_avatar()'),/PROFILE_INCOMPLETE/);
 }finally{await db.close();}
});
