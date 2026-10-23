import assert from 'node:assert/strict';
import test from 'node:test';
import {verifyAccount} from '../../scripts/verify-account-backend.mjs';
const expected={id:'fixture',email:'student.ccs@demo.goserve.test',role:'student',office_id:null,staff_capability:null,department_id:'DEP-CCS'};
const profile=()=>({...expected,is_demo:true});
const account=()=>({...profile(),next_action:'accept_terms',profile_complete:false,terms:{current_version:null},avatar_path:null,avatar_version:0,version:1});
await test('rollout verifier accepts only matching own scope with Terms still unaccepted',()=>{
 assert.deepEqual(verifyAccount(expected,[profile()],account(),account()),{scopeVerified:true,nextAction:'accept_terms',termsActivated:false,profileCompleted:false});
});
await test('rollout verifier rejects extra profiles, forged scope and legacy bootstrap',()=>{
 for(const [rows,rpc,bootstrap]of [[[profile(),profile()],account(),account()],[[{...profile(),role:'staff'}],account(),account()],[[profile()],{...account(),department_id:null},account()],[[profile()],account(),profile()]])
  assert.throws(()=>verifyAccount(expected,rows,rpc,bootstrap),/ACCOUNT_SCOPE_MISMATCH|ACCOUNT_READINESS_MISMATCH/);
});
await test('rollout verifier never treats accepted Terms or invented completion as untouched data',()=>{
 for(const change of[{next_action:'home'},{profile_complete:true},{terms:{current_version:'unapproved'}},{terms:{current_version:null,accepted_version:'legacy'}},{avatar_path:'unknown'},{avatar_version:1},{version:1.5}])
  assert.throws(()=>verifyAccount(expected,[profile()],account(),{...account(),...change}),/ACCOUNT_READINESS_MISMATCH/);
});
