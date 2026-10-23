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
for(const n of (await readdir(new URL('../migrations/',import.meta.url))).filter(n=>n.endsWith('.sql')).sort())await db.exec(await readFile(new URL('../migrations/'+n,import.meta.url),'utf8'));
const ids=Object.fromEntries(['student','unknown','secretary','faculty','ordinary','guidance','registrar'].map(n=>[n,randomUUID()]));
await sql("insert into public.terms_documents values('fixture-secretary','Synthetic Secretary contact terms','fixture',now(),true)");
for(const [name,id] of Object.entries(ids)) {
    await sql('insert into auth.users(id,email) values($1,$2)',[id,name+'@demo.test']);
    const role=['student','unknown'].includes(name)?'student':'staff';
    const office=role==='student'?null:name==='guidance'?'OFF-GCC':name==='registrar'?'OFF-REG':'DEP-CCS';
    const capability=['secretary','faculty','guidance'].includes(name)?name:null;
    await sql('insert into public.profiles(id,email,role,office_id,staff_capability,department_id,profile_completed_at) values($1,$2,$3,$4,$5,$6,now())',
        [id,name+'@demo.test',role,office,capability,name==='student'?'DEP-CCS':null]);
    await sql("insert into goserve_private.terms_acceptances(profile_id,terms_version,actor_id) values($1,'fixture-secretary',$1)",[id]);
}
await sql("insert into goserve_private.policies(name,value) values('consultation_offices','[\"DEP-CCS\",\"OFF-GCC\"]'),('consultation_cancel','{\"roles\":[\"student\",\"staff\"],\"minimum_notice_seconds\":0}'),('consultation_reschedule','{\"roles\":[\"student\",\"staff\"],\"minimum_notice_seconds\":0}')");
async function as(name,text,args=[]) {
    await db.exec('begin');
    try {
        await db.exec('set local role authenticated');
        await sql("select set_config('request.jwt.claim.sub',$1,true),set_config('request.jwt.claims',$2,true)",[ids[name],JSON.stringify({is_anonymous:false,amr:[{method:'password'}],user_metadata:{department_id:'DEP-CCS',staff_capability:'secretary'}})]);
        const result=await sql(text,args); await db.exec('commit'); return result;
    } catch(error) { await db.exec('rollback'); throw error; }
}
const call=async(name,fn,args=[]) => (await as(name,`select public.${fn}(${args.map((_,i)=>'$'+(i+1)).join(',')}) r`,args))[0].r;
const deny=(work,code)=>assert.rejects(work,error=>error.message===code);
const start=new Date(Date.now()+3*86400000).toISOString(),end=new Date(Date.now()+3*86400000+3600000).toISOString();
const save=(actor,owner)=>call(actor,'save_slot',[null,0,owner==='guidance'?'OFF-GCC':'DEP-CCS',ids[owner],start,end,5,true,randomUUID()]);
async function seedSlot(owner,reserved=0) {
    return (await sql('insert into public.consultation_slots(office_id,schedule_owner_id,start_at,end_at,capacity,reserved) values($1,$2,$3,$4,5,$5) returning *',
        [owner==='guidance'?'OFF-GCC':'DEP-CCS',ids[owner],start,end,reserved]))[0];
}
const secretarySlot=await seedSlot('secretary'),facultySlot=await seedSlot('faculty'),ordinarySlot=await seedSlot('ordinary'),guidanceSlot=await seedSlot('guidance');

await test('Secretary can publish their own departmental consultation schedule',async()=>{
    let slot; await assert.doesNotReject(async()=>{slot=await save('secretary','secretary');},'Secretary must own the department schedule without a Faculty login');
    assert.equal(slot.schedule_owner_id,ids.secretary); assert.equal(slot.office_id,'DEP-CCS');
});
await test('Faculty cannot create a departmental schedule for themselves or another contact',async()=>{
    await deny(()=>save('faculty','faculty'),'FORBIDDEN'); await deny(()=>save('faculty','secretary'),'FORBIDDEN');
    await deny(()=>save('ordinary','ordinary'),'FORBIDDEN');
});
await test('Student slot discovery hides Faculty and ordinary department Staff',async()=>{
    const rows=await as('student','select id from public.consultation_slots');
    assert(rows.some(r=>r.id===secretarySlot.id)); assert(rows.some(r=>r.id===guidanceSlot.id));
    assert(!rows.some(r=>r.id===facultySlot.id)); assert(!rows.some(r=>r.id===ordinarySlot.id));
});
await test('known Faculty slot IDs cannot bypass departmental booking restrictions',async()=>{
    for(const slot of [facultySlot,ordinarySlot]) {
        await deny(()=>call('student','book_consultation',[slot.id,slot.version,randomUUID()]),'NOT_FOUND');
        assert.equal((await sql('select reserved from public.consultation_slots where id=$1',[slot.id]))[0].reserved,0);
    }
    const booked=await call('student','book_consultation',[secretarySlot.id,secretarySlot.version,randomUUID()]);
    assert.equal(booked.student_id,ids.student);
});
await test('rescheduling cannot switch a Secretary appointment to a Faculty slot',async()=>{
    const current=await seedSlot('secretary',1);
    const a=(await sql('insert into public.appointments(student_id,slot_id) values($1,$2) returning *',[ids.student,current.id]))[0];
    await deny(()=>call('student','change_appointment',[a.id,a.version,facultySlot.id,facultySlot.version,'Synthetic reason',randomUUID()]),'NOT_FOUND');
    assert.equal((await sql('select reserved from public.consultation_slots where id=$1',[current.id]))[0].reserved,1);
    assert.equal((await sql('select slot_id from public.appointments where id=$1',[a.id]))[0].slot_id,current.id);
});
await test('department inquiry notifications go only to Secretary staff',async()=>{
    const sent=await call('student','send_inquiry',[null,'DEP-CCS','Synthetic department question',randomUUID()]);
    const recipients=await sql("select recipient_id from public.notifications where target_type='inquiry' and target_id=$1",[sent.thread.id]);
    assert.deepEqual(recipients.map(r=>r.recipient_id),[ids.secretary]);
    assert.equal((await as('secretary','select id from public.inquiry_threads where id=$1',[sent.thread.id])).length,1);
    assert.equal((await as('ordinary','select id from public.inquiry_threads where id=$1',[sent.thread.id])).length,0);
});
await test('assignment cannot give Faculty access to a departmental inquiry',async()=>{
    const thread=(await sql("insert into public.inquiry_threads(student_id,office_id) values($1,'DEP-CCS') returning *",[ids.student]))[0];
    await deny(()=>call('secretary','assign_inquiry',[thread.id,ids.faculty,thread.version,randomUUID()]),'FORBIDDEN');
    await sql('update public.inquiry_threads set assigned_to=$1 where id=$2',[ids.faculty,thread.id]);
    assert.deepEqual(await as('faculty','select id from public.inquiry_threads where id=$1',[thread.id]),[]);
    await deny(()=>call('faculty','send_inquiry',[thread.id,null,'Synthetic reply',randomUUID()]),'NOT_FOUND');
});
await test('unassigned department in user metadata cannot create a department connection',async()=>{
    assert.deepEqual(await as('unknown',"select id from public.consultation_slots where office_id='DEP-CCS'"),[]);
    await deny(()=>call('unknown','book_consultation',[secretarySlot.id,1,randomUUID()]),'NOT_FOUND');
    await deny(()=>call('unknown','send_inquiry',[null,'DEP-CCS','Synthetic question',randomUUID()]),'NOT_FOUND');
});
await test('Guidance and Registrar contact stay available independently of department contact',async()=>{
    assert.equal((await save('guidance','guidance')).office_id,'OFF-GCC');
    assert.equal((await call('unknown','book_consultation',[guidanceSlot.id,1,randomUUID()])).student_id,ids.unknown);
    const sent=await call('student','send_inquiry',[null,'OFF-REG','Synthetic registrar question',randomUUID()]);
    const recipients=await sql('select recipient_id from public.notifications where target_id=$1',[sent.thread.id]);
    assert.deepEqual(recipients.map(r=>r.recipient_id),[ids.registrar]);
});
await test('legacy Faculty appointment can be cancelled without notifying Faculty',async()=>{
    const slot=await seedSlot('faculty',1);
    const a=(await sql('insert into public.appointments(student_id,slot_id) values($1,$2) returning *',[ids.student,slot.id]))[0];
    assert.equal((await call('student','change_appointment',[a.id,1,null,null,'Synthetic cancellation',randomUUID()])).state,'cancelled');
    const recipients=await sql('select recipient_id from public.notifications where target_id=$1',[a.id]);
    assert(!recipients.some(r=>r.recipient_id===ids.faculty)); assert(recipients.some(r=>r.recipient_id===ids.secretary));
});
const fileService=randomUUID(),fileRequest=randomUUID(),evidence=randomUUID(),deliverable=randomUUID();
await sql("insert into public.services(id,office_id,name,delivery_modes,fee_state,amount) values($1,'DEP-CCS','Synthetic file authority service',ARRAY['digital'],'free',0)",[fileService]);
await sql("insert into public.requests(id,owner_id,office_id,service_id,snapshot,delivery_mode,status) values($1,$2,'DEP-CCS',$3,'{}','digital','Processing')",[fileRequest,ids.student,fileService]);
for(const [file,kind,verification] of [[evidence,'evidence','verified'],[deliverable,'deliverable','pending']]){
    await sql('insert into public.request_files(id,owner_id,service_id,request_id,kind,mime_type,size_bytes,verification) values($1,$2,$3,$4,$5,\'application/pdf\',4,$6)',[file,ids.student,fileService,fileRequest,kind,verification]);
    await sql("insert into goserve_private.file_objects(file_id,object_path,expires_at) values($1,$2,now()+interval '10 minutes')",[file,'synthetic/'+file]);
}
for(const name of ['faculty','ordinary','secretary'])await sql("insert into goserve_private.capabilities(profile_id,office_id,capability) values($1,'DEP-CCS','requests')",[ids[name]]);
await test('legacy Faculty and ordinary departmental grants cannot read student evidence through the trusted file endpoint',async()=>{
    for(const name of ['faculty','ordinary'])await deny(()=>sql("select public.file_server_operation($1,$2,'read',null)",[ids[name],evidence]),'FORBIDDEN');
});
await test('legacy Faculty and ordinary departmental grants cannot authorize or verify a deliverable',async()=>{
    for(const name of ['faculty','ordinary'])for(const command of ['authorize','verified'])await deny(()=>sql('select public.file_server_operation($1,$2,$3,$4)',[ids[name],deliverable,command,'a'.repeat(64)]),'FORBIDDEN');
});
await test('explicitly authorized Secretary can read evidence and authorize the matching department deliverable',async()=>{
    assert.equal((await sql("select public.file_server_operation($1,$2,'read',null) r",[ids.secretary,evidence]))[0].r.id,evidence);
    assert.equal((await sql("select public.file_server_operation($1,$2,'authorize',null) r",[ids.secretary,deliverable]))[0].r.id,deliverable);
});
await sql("insert into goserve_private.policies(name,value) values('demo_file_defaults',$1)",[{evidence:{verifier:'goserve-v1',mime_types:['application/pdf','image/png','image/jpeg'],max_bytes:10485760,upload_seconds:600,max_pixels:20000000},deliverable:{verifier:'goserve-v1',mime_types:['application/pdf'],max_bytes:10485760,upload_seconds:600}}]);
await test('explicit demo service creation receives only the approved private file defaults',async()=>{
    const service=randomUUID();await sql("insert into public.services(id,office_id,name,delivery_modes,fee_state,amount,is_demo) values($1,'OFF-REG','Synthetic demo defaults',ARRAY['digital'],'free',0,true)",[service]);
    const rows=await sql("select name,value from goserve_private.policies where name in($1,$2) order by name",['file_'+service+'_evidence','file_'+service+'_deliverable']);
    assert.equal(rows.length,2);assert.equal(rows.find(r=>r.name.endsWith('_evidence')).value.max_bytes,10485760);assert.deepEqual(rows.find(r=>r.name.endsWith('_deliverable')).value.mime_types,['application/pdf']);
});
await test('demo defaults never overwrite an existing service-specific rule',async()=>{
    const service=randomUUID();await sql("insert into public.services(id,office_id,name,delivery_modes,fee_state,amount,is_demo) values($1,'OFF-REG','Synthetic custom rule',ARRAY['physical'],'free',0,true)",[service]);
    await sql('update goserve_private.policies set value=$1 where name=$2',[{verifier:'goserve-v1',mime_types:['application/pdf'],max_bytes:4096,upload_seconds:60},'file_'+service+'_evidence']);
    await sql("update public.services set name='Synthetic renamed service' where id=$1",[service]);
    assert.equal((await sql('select value from goserve_private.policies where name=$1',['file_'+service+'_evidence']))[0].value.max_bytes,4096);
});
await test('production services do not silently inherit test file policies',async()=>{
    const service=randomUUID();await sql("insert into public.services(id,office_id,name,delivery_modes,fee_state,amount,is_demo) values($1,'OFF-REG','Synthetic non-demo boundary',ARRAY['digital'],'free',0,false)",[service]);
    assert.equal((await sql('select name from goserve_private.policies where name in($1,$2)',['file_'+service+'_evidence','file_'+service+'_deliverable'])).length,0);
});
await db.close();
