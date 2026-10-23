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
for(const file of (await readdir(new URL('../migrations/',import.meta.url))).filter(n=>n.endsWith('.sql')).sort())await db.exec(await readFile(new URL('../migrations/'+file,import.meta.url),'utf8'));
const ids={student:randomUUID(),other:randomUUID()};
for(const [name,id] of Object.entries(ids)) {
    await sql('insert into auth.users(id,email,raw_user_meta_data) values($1,$2,$3)',[id,name+'@demo.goserve.test',JSON.stringify({department_id:'DEP-CCS',role:'staff',office_id:'DEP-CCS'})]);
    await sql("insert into public.profiles(id,email,role,is_demo) values($1,$2,'student',true)",[id,name+'@demo.goserve.test']);
}
async function as(name,text,args=[]) {
    await db.exec('begin');
    try {
        await db.exec('set local role authenticated');
        await sql("select set_config('request.jwt.claim.sub',$1,true),set_config('request.jwt.claims',$2,true)",[ids[name],JSON.stringify({is_anonymous:false,amr:[{method:'password'}],user_metadata:{department_id:'DEP-CCS'}})]);
        const result=await sql(text,args); await db.exec('commit'); return result;
    } catch(error) { await db.exec('rollback'); throw error; }
}
async function column() {
    const fields=await sql("select data_type,is_nullable from information_schema.columns where table_schema='public' and table_name='profiles' and column_name='department_id'");
    assert.equal(fields.length,1,'Student department needs its own stored field, separate from staff office');
    return fields[0];
}
const account=async(name) => (await as(name,'select public.account_view() r'))[0].r;
const assign=() => sql("update public.profiles set department_id='DEP-CCS' where id=$1",[ids.student]);

await test('department expansion preserves existing profiles and does not infer CCS from user metadata',async()=>{
    assert.deepEqual(await column(),{data_type:'text',is_nullable:'YES'});
    for(const name of Object.keys(ids)) {
        const p=await account(name);
        assert.equal(p.department_id,null); assert.equal(p.role,'student'); assert.equal(p.office_id,null); assert.equal(p.staff_capability,null);
        assert.equal(p.version,1); assert.equal(p.next_action,'accept_terms'); assert.equal(p.terms.current_version,null);
    }
});
await test('trusted provisioning marks a CCS Student without granting office capabilities',async()=>{
    await column(); await assign(); const p=await account('student');
    assert.equal(p.department_id,'DEP-CCS'); assert.equal(p.role,'student'); assert.equal(p.office_id,null); assert.equal(p.staff_capability,null); assert.equal(p.is_demo,true);
    const allowed=(await as('student',"select goserve_private.capable('DEP-CCS','requests') r"))[0].r;
    assert.equal(allowed,false); assert.equal((await account('other')).department_id,null);
});
await test('authenticated callers cannot assign or overwrite a department, role or office',async()=>{
    await column(); await assign();
    await assert.rejects(()=>as('student',"update public.profiles set department_id=null where id=$1",[ids.student]),/permission denied/);
    await assert.rejects(()=>as('student',"update public.profiles set role='staff',office_id='DEP-CCS' where id=$1",[ids.student]),/permission denied/);
    assert.equal((await account('student')).department_id,'DEP-CCS');
});
await test('department IDs must identify an existing department rather than an office or unknown catalog entry',async()=>{
    await column();
    await assert.rejects(()=>sql("update public.profiles set department_id='DEP-MISSING' where id=$1",[ids.student]),error=>error.code==='23503');
    await assert.rejects(()=>sql("update public.profiles set department_id='OFF-REG' where id=$1",[ids.student]),error=>error.code==='23514');
    await assign(); assert.equal((await account('student')).department_id,'DEP-CCS');
});
await test('existing account updates preserve the trusted department and reject injected assignments',async()=>{
    await column(); await assign();
    await sql("insert into public.terms_documents values('fixture-department','Synthetic department test terms','fixture',now(),true)");
    const fields={first_name:'Demo',middle_name:null,no_middle_name:true,last_name:'Student',student_id:'synthetic',course_program:'Synthetic program',year_level:'Synthetic year',mobile_number:'synthetic',gender:'synthetic'};
    const update=(profile,key)=>as('student','select public.update_account(1,$1,$2::jsonb,$3::uuid) r',['fixture-department',JSON.stringify(profile),key]);
    await assert.rejects(()=>update({...fields,department_id:'DEP-OTHER'},randomUUID()),/VALIDATION_FAILED/);
    const result=(await update(fields,randomUUID()))[0].r;
    assert.equal(result.department_id,'DEP-CCS'); assert.equal(result.office_id,null); assert.equal(result.role,'student');
    assert.equal(result.course_program,'Synthetic program'); assert.equal(result.next_action,'home');
});
await test('department remains within the existing own-profile read boundary',async()=>{
    await column(); await assign();
    assert.deepEqual(await as('other','select department_id from public.profiles where id=$1',[ids.student]),[]);
    const rows=await as('student','select id,department_id from public.profiles');
    assert.deepEqual(rows,[{id:ids.student,department_id:'DEP-CCS'}]);
});
await db.close();
