import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {readFile,readdir} from 'node:fs/promises';
import test from 'node:test';
import {fixtureSql} from './sql-fixture.mjs';
let runtime,crypto;
try{runtime=await import('@electric-sql/pglite');}catch{runtime=await import('../../.tools/sql-validation/node_modules/@electric-sql/pglite/dist/index.js');}
try{crypto=await import('@electric-sql/pglite/contrib/pgcrypto');}catch{crypto=await import('../../.tools/sql-validation/node_modules/@electric-sql/pglite/dist/contrib/pgcrypto.js');}
const db=await runtime.PGlite.create({extensions:{pgcrypto:crypto.pgcrypto}});
const sql=async(t,a=[])=>(await db.query(t,a)).rows;
await db.exec(fixtureSql);
await db.exec(await readFile(new URL('../schema.sql',import.meta.url),'utf8'));
for(const f of(await readdir(new URL('../migrations/',import.meta.url))).filter(f=>f.endsWith('.sql')).sort())await db.exec(await readFile(new URL('../migrations/'+f,import.meta.url),'utf8'));
const ids={student:randomUUID(),other:randomUUID(),staff:randomUUID(),incomplete:randomUUID()};
await sql("insert into public.terms_documents values('avatar-fixture','Synthetic terms only','fixture',now(),true)");
for(const [name,id]of Object.entries(ids)){
 await sql('insert into auth.users(id,email) values($1,$2)',[id,name+'@demo.goserve.test']);
 await sql("insert into public.profiles(id,email,role,office_id,is_demo,profile_completed_at) values($1,$2,$3,$4,true,$5)",[id,name+'@demo.goserve.test',name==='staff'?'staff':'student',name==='staff'?'OFF-REG':null,name==='incomplete'?null:new Date().toISOString()]);
 if(name!=='incomplete')await sql("insert into goserve_private.terms_acceptances values($1,'avatar-fixture',now(),$1)",[id]);
}
async function as(name,text,args=[]){await db.exec('begin');try{await db.exec('set local role authenticated');await sql("select set_config('request.jwt.claim.sub',$1,true),set_config('request.jwt.claims',$2,true)",[ids[name],JSON.stringify({is_anonymous:false,amr:[{method:'password'}]})]);const r=await sql(text,args);await db.exec('commit');return r;}catch(e){await db.exec('rollback');throw e;}}
const read=async(n='student')=>(await as(n,'select public.profile_avatar() r'))[0].r;
const prepare=async(n='student',v=0,key=randomUUID())=>(await as(n,'select public.prepare_profile_avatar($1,$2) r',[v,key]))[0].r;
const save=async(n,path,v,key=randomUUID())=>(await as(n,'select public.set_profile_avatar($1,$2,$3) r',[path,v,key]))[0].r;
const upload=(n,path,metadata={mimetype:'image/jpeg',size:1234})=>as(n,"insert into storage.objects(bucket_id,name,metadata) values('profile-avatars',$1,$2::jsonb)",[path,JSON.stringify(metadata)]);
async function reset(){await sql('delete from storage.objects');if((await sql("select to_regclass('goserve_private.avatar_uploads') r"))[0].r)await sql('delete from goserve_private.avatar_uploads');await sql('delete from goserve_private.operation_keys');if((await sql("select 1 from information_schema.columns where table_schema='public' and table_name='profiles' and column_name='avatar_path'")).length)await sql('update public.profiles set avatar_path=null,avatar_version=0');}
await test('avatar schema preserves accounts and uses a private bounded JPEG bucket',async()=>{
 const columns=await sql("select column_name from information_schema.columns where table_schema='public' and table_name='profiles' and column_name in('avatar_path','avatar_version')");assert.equal(columns.length,2,'versioned private profile pictures are missing');
 assert.deepEqual(await sql("select public,file_size_limit,allowed_mime_types from storage.buckets where id='profile-avatars'"),[{public:false,file_size_limit:1048576,allowed_mime_types:['image/jpeg']}]);
 const r=await read();assert.equal(r.avatar_path,null);assert.equal(r.avatar_version,0);assert.deepEqual(r.obsolete_paths,[]);
});
await test('owner prepares and binds only a Storage-backed private photo',async()=>{
 await reset();const ticket=await prepare();assert.match(ticket.path,new RegExp('^'+ids.student+'/[a-f0-9-]{36}\\.jpg$'));await upload('student',ticket.path);const r=await save('student',ticket.path,0);assert.equal(r.avatar_path,ticket.path);assert.equal(r.avatar_version,1);assert.equal((await read('other')).avatar_path,null);
});
await test('another account cannot read, upload, bind or retire the owner photo',async()=>{
 await reset();const t=await prepare();await upload('student',t.path);assert.deepEqual(await as('other',"select name from storage.objects where bucket_id='profile-avatars'"),[]);await assert.rejects(()=>upload('other',t.path),/row-level security/);await assert.rejects(()=>save('other',t.path,0),/UPLOAD_INVALID/);await assert.rejects(()=>as('other','select public.discard_profile_avatar($1)',[t.path]),/NOT_FOUND/);
});
await test('ordinary staff pictures use the same private owner boundary',async()=>{
 await reset();const t=await prepare('staff');await upload('staff',t.path);const r=await save('staff',t.path,0);assert.equal(r.avatar_path,t.path);assert.deepEqual(await as('staff',"select name from storage.objects where name like $1",[ids.student+'/%']),[]);
});
await test('unprepared paths and malformed object names cannot be uploaded',async()=>{
 await reset();for(const p of[ids.student+'/'+randomUUID()+'.jpg',ids.student+'/../bad.jpg',ids.student+'/bad.png'])await assert.rejects(()=>upload('student',p),/row-level security/);
});
await test('incomplete accounts cannot prepare, read or upload a profile image',async()=>{
 await reset();await assert.rejects(()=>prepare('incomplete'),/PROFILE_INCOMPLETE/);await assert.rejects(()=>read('incomplete'),/PROFILE_INCOMPLETE/);await assert.rejects(()=>upload('incomplete',ids.incomplete+'/'+randomUUID()+'.jpg'),/row-level security/);
});
await test('missing, oversized or non-JPEG objects cannot become profile pictures',async()=>{
 await reset();const t=await prepare();await assert.rejects(()=>save('student',t.path,0),/UPLOAD_INVALID/);
 for(const m of[{mimetype:'image/png',size:100},{mimetype:'image/jpeg',size:0},{mimetype:'image/jpeg',size:1048577},{mimetype:'image/jpeg',size:'invalid'},{}]){await sql('delete from storage.objects');await sql("insert into storage.objects(bucket_id,name,metadata) values('profile-avatars',$1,$2)",[t.path,JSON.stringify(m)]);await assert.rejects(()=>save('student',t.path,0),/UPLOAD_INVALID/);}assert.equal((await read()).avatar_version,0);
});
await test('retries retain operation identity and cannot change its input',async()=>{
 await reset();const key=randomUUID(),t=await prepare('student',0,key);assert.deepEqual(await prepare('student',0,key),t);await assert.rejects(()=>prepare('student',1,key),/IDEMPOTENCY_CONFLICT/);await upload('student',t.path);const k=randomUUID(),r=await save('student',t.path,0,k);assert.deepEqual(await save('student',t.path,0,k),r);await assert.rejects(()=>save('student',null,0,k),/IDEMPOTENCY_CONFLICT/);
});
await test('stale photo saves preserve the newer picture and unrelated account fields',async()=>{
 await reset();const a=await prepare(),b=await prepare();await upload('student',a.path);await upload('student',b.path);await save('student',a.path,0);await assert.rejects(()=>save('student',b.path,0),/STALE_VERSION/);assert.equal((await read()).avatar_path,a.path);const p=(await sql('select role,office_id,department_id,version from public.profiles where id=$1',[ids.student]))[0];assert.deepEqual(p,{role:'student',office_id:null,department_id:null,version:1});
});
await test('direct profile edits, Storage overwrite and current-photo deletion are blocked',async()=>{
 await reset();const t=await prepare();await upload('student',t.path);await save('student',t.path,0);await assert.rejects(()=>as('student','update public.profiles set avatar_path=null where id=$1',[ids.student]),/permission denied/);assert.deepEqual(await as('student',"update storage.objects set metadata='{}' where name=$1 returning name",[t.path]),[]);assert.deepEqual(await as('student','delete from storage.objects where name=$1 returning name',[t.path]),[]);assert.equal((await read()).avatar_path,t.path);
});
await test('replacement retires the old photo, permits cleanup and prevents rebinding',async()=>{
 await reset();const a=await prepare();await upload('student',a.path);await save('student',a.path,0);const b=await prepare('student',1);await upload('student',b.path);const r=await save('student',b.path,1);assert.deepEqual(r.obsolete_paths,[a.path]);await assert.rejects(()=>save('student',a.path,2),/UPLOAD_INVALID/);assert.deepEqual(await as('student','delete from storage.objects where name=$1 returning name',[a.path]),[{name:a.path}]);assert.equal((await read()).avatar_path,b.path);assert.deepEqual((await read()).obsolete_paths,[]);
});
await test('discard allows abandoned-upload cleanup but cannot discard a current photo',async()=>{
 await reset();const t=await prepare();await upload('student',t.path);await as('student','select public.discard_profile_avatar($1)',[t.path]);await assert.rejects(()=>save('student',t.path,0),/UPLOAD_INVALID/);assert.deepEqual(await as('student','delete from storage.objects where name=$1 returning name',[t.path]),[{name:t.path}]);const a=await prepare();await upload('student',a.path);await save('student',a.path,0);await assert.rejects(()=>as('student','select public.discard_profile_avatar($1)',[a.path]),/CONFLICT/);
});
await test('removal clears only the picture and retires its object for cleanup',async()=>{
 await reset();const t=await prepare();await upload('student',t.path);await save('student',t.path,0);const r=await save('student',null,1);assert.equal(r.avatar_path,null);assert.equal(r.avatar_version,2);assert.deepEqual(r.obsolete_paths,[t.path]);
});
await test('anonymous callers have no avatar RPC or private registry access',async()=>{
 assert.equal((await sql("select has_function_privilege('anon','public.profile_avatar()','execute') r"))[0].r,false);assert.equal((await sql("select has_table_privilege('authenticated','goserve_private.avatar_uploads','select') r"))[0].r,false);
});
await test('a permissive existing bucket policy cannot make the private photo bucket public or remove it',async()=>{
 await db.exec('alter table storage.buckets enable row level security;grant select,insert,update,delete on storage.buckets to authenticated;create policy avatar_fixture_bucket_access on storage.buckets for all to authenticated using(true) with check(true)');
 assert.deepEqual(await as('student',"update storage.buckets set public=true where id='profile-avatars' returning id"),[]);
 assert.deepEqual(await as('student',"delete from storage.buckets where id='profile-avatars' returning id"),[]);
 assert.equal((await sql("select public from storage.buckets where id='profile-avatars'"))[0].public,false);
});
await db.close();
