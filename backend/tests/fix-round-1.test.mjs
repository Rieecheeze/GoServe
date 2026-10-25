import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFile } from 'node:fs/promises';
import { stripTypeScriptTypes } from 'node:module';
import { runInNewContext } from 'node:vm';

const source = stripTypeScriptTypes(await readFile(new URL('../lib/v23.ts', import.meta.url), 'utf8'), { mode: 'transform' }).replace(/^export /gm, '');
const context = { Request, Response, URL, URLSearchParams, AbortSignal, TextDecoder, Uint8Array, crypto };
runInNewContext(source + '\nglobalThis.api={validateRegistration,rpc,errorResponse,authenticated};', context);
const { api } = context;
const profile = { first_name:'A',middle_name:null,no_middle_name:true,last_name:'B',student_id:'1',course_program:'Test',year_level:'Test',mobile_number:'Contact',gender:'Self described' };
const registration = password => ({ email:'synthetic@demo.com',password,profile,terms_version:'test',idempotency_key:'00000000-0000-4000-8000-000000000001' });

test('password rule accepts Unicode letters/digits and preserves whitespace', () => {
  for (const password of ['éééé١!', '𐐀abc1!', ' A1!  ']) assert.doesNotThrow(() => api.validateRegistration(registration(password)));
});
test('password rule counts code points and requires a real punctuation/symbol', () => {
  for (const password of ['𐐀a1!x', 'Abcd1é', 'Abcd1 ', 'éééé١é']) assert.throws(() => api.validateRegistration(registration(password)), /VALIDATION_FAILED/);
});
test('known SQLSTATE errors have sanitized actionable mappings', async () => {
  for (const [code, expected, status] of [['22P02','VALIDATION_FAILED',422],['23502','VALIDATION_FAILED',422],['23514','VALIDATION_FAILED',422],['23505','CONFLICT',409],['40001','TEMPORARILY_UNAVAILABLE',503],['40P01','TEMPORARILY_UNAVAILABLE',503]]) {
    let failure;
    try { await api.rpc({ rpc:async()=>({error:{code,message:'private SQL details'}}) }, 'test'); } catch (error) { failure=error; }
    assert.equal(failure?.code, expected);
    const response=api.errorResponse(failure);
    assert.equal(response.status,status);
    assert.ok(!(await response.text()).includes('private SQL'));
  }
});
test('new authenticated Edge boundary respects authoritative SQL session rejection', async () => {
  let called=0;
  await assert.rejects(() => api.authenticated(new Request('https://fixture/test',{headers:{Authorization:'Bearer verified-session'}}),
    {auth:{getUser:async()=>({data:{user:{id:'existing-complete-user',is_anonymous:false,user_metadata:{email_verified:true}}}})}},
    ()=>({rpc:async name=>{called++;assert.equal(name,'account_view');return {error:{code:'P0001',message:'FORBIDDEN'}};}})), /FORBIDDEN/);
  assert.equal(called,1);
});
test('protected Edge operations retain the recovery setup blocker from SQL',async()=>{
  await assert.rejects(()=>api.rpc({rpc:async()=>({error:{code:'P0001',message:'RECOVERY_SETUP_REQUIRED'}})},'protected_operation'),/RECOVERY_SETUP_REQUIRED/);
});
