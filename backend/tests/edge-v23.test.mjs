import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
let source=stripTypeScriptTypes(await readFile(new URL('../lib/v23.ts',import.meta.url),'utf8'),{mode:'transform'}).replaceAll(/^export /gm,'');
source+='\nglobalThis.api={DomainError,validateRegistration,registerStudent,providerBoundary,cleanOne,authenticated,errorResponse};';
const context={Response,Request,URL,AbortSignal,ReadableStream,Uint8Array,TextDecoder,crypto,console};runInNewContext(source,context);const api=context.api;
let count=0;
const body={email:'student@demo.com',password:'Abcd1!',terms_version:'test-v1',idempotency_key:'00000000-0000-4000-8000-000000000001',profile:{first_name:'A',middle_name:null,no_middle_name:true,last_name:'B',student_id:'1',course_program:'Test',year_level:'Test',mobile_number:'Contact',gender:'Self described'}};
for(const value of [{...body,role:'staff'},{...body,password:'abcdef'},{...body,profile:{...body.profile,no_middle_name:false}},{...body,profile:{...body.profile,office_id:'OFF-REG'}},{...body,email:'bad'}]){assert.throws(()=>api.validateRegistration(value),/VALIDATION_FAILED/);count++;}
api.validateRegistration(body);count++;
function registrationHarness(options={}){
  let created=0,deleted=0,state='creating',auth=null,prepared=false;
  const admin={auth:{admin:{async createUser(input){created++;assert.equal(input.email_confirm,true);assert.equal(input.app_metadata.goserve_registration_attempt,'attempt');assert.equal(input.role,undefined);auth='new-user';if(options.authResponseLost)throw new Error('network');return {data:{user:{id:auth}},error:null};},async deleteUser(id){assert.equal(id,auth);deleted++;auth=null;return {error:options.deleteFailure?{}:null};}}},async rpc(name,args){assert.equal(name,'registration_operation');assert.equal(JSON.stringify(args).includes(body.password),false);
    if(args.p_command==='prepare'){const may_create=!prepared;prepared=true;return {data:{attempt_id:'attempt',state,auth_id:auth,may_create}};}
    if(args.p_command==='resolve')return {data:{attempt_id:'attempt',state,auth_id:auth}};
    if(args.p_command==='compensate'){state='compensating';return {data:{state,auth_id:auth}};}
    if(args.p_command==='deleted'){state='deleted';return {data:{state}};}
    if(args.p_command==='commit'){
      if(options.commitFailure)return {error:{message:'TERMS_REQUIRED'}};
      state='committed';if(options.commitResponseLost)return {error:{message:'network'}};
      return {data:{account_created:true,email:body.email,next_action:'sign_in'}};
    }throw new Error('Unexpected command');
  }};
  return {admin,counts:()=>({created,deleted,state})};
}
let h=registrationHarness();let result=await api.registerStudent(h.admin,body);assert.equal(result.next_action,'sign_in');assert.equal(result.access_token,undefined);await api.registerStudent(h.admin,body);assert.equal(h.counts().created,1);count++;
for(const options of [{authResponseLost:true},{commitResponseLost:true}]){h=registrationHarness(options);assert.equal((await api.registerStudent(h.admin,body)).account_created,true);assert.equal(h.counts().deleted,0);count++;}
h=registrationHarness({commitFailure:true});await assert.rejects(()=>api.registerStudent(h.admin,body),/TEMPORARILY_UNAVAILABLE/);assert.deepEqual(h.counts(),{created:1,deleted:1,state:'deleted'});count++;
h=registrationHarness({commitFailure:true,deleteFailure:true});await assert.rejects(()=>api.registerStudent(h.admin,body),/TEMPORARILY_UNAVAILABLE/);assert.equal(h.counts().state,'compensating');count++;
for(const [state,code] of [[{cleanup:'pending',consent:'allow'},'CLEANUP_REQUIRED'],[{cleanup:'clear',consent:'decline'},'CONSENT_REQUIRED'],[{cleanup:'clear',consent:'allow'},'PROVIDER_UNAVAILABLE']]){await assert.rejects(()=>api.providerBoundary({rpc:async()=>({data:state})},'seryo'),new RegExp(code));count++;}
let verified;
function cleanupAdmin(listing){return {rpc:async(name,args)=>{if(name==='cleanup_claim')return {data:{id:'job',lease_id:'lease',object_path:'owner/file'}};verified=args.p_absence_verified;return {data:{state:verified?'verified_deleted':'failed'}};},storage:{from:()=>({remove:async()=>({error:null}),list:async()=>listing})}};}
for(const [listing,expected] of [[{data:[{name:'file'}],error:null},false],[{data:null,error:{}},false],[{data:[],error:null},true]]){await api.cleanOne(cleanupAdmin(listing),60);assert.equal(verified,expected);count++;}
const response=api.errorResponse(new Error('secret payload'));assert.equal(response.status,503);assert.equal((await response.text()).includes('secret'),false);count++;
let files=stripTypeScriptTypes(await readFile(new URL('../lib/files.ts',import.meta.url),'utf8'),{mode:'transform'}).replace(/^import[^\n]+\n/,'').replaceAll(/^export /gm,'');files+='\nglobalThis.files={readBounded,verifyFile,transferDownload};';runInNewContext(files,context);
const data=new Uint8Array([1,2,3]);
await assert.rejects(()=>context.files.readBounded(new Blob([data]).stream(),2),/UPLOAD_INVALID/);count++;
await assert.rejects(()=>context.files.verifyFile(data,'application/pdf',{verifier:'goserve-v1',mime_types:['application/pdf'],max_bytes:10},{}),/UPLOAD_INVALID/);count++;
await assert.rejects(()=>context.files.verifyFile(data,'image/png',null,{}),/CONFIGURATION_REQUIRED/);count++;
const pdf=new TextEncoder().encode('%PDF-test\n%%EOF');const hash=await context.files.verifyFile(pdf,'application/pdf',{verifier:'goserve-v1',mime_types:['application/pdf'],max_bytes:100},{pdf:async()=>({pages:1,encrypted:false})});assert.equal(hash.length,64);count++;
let completions=0;
const downloader={rpc:async(name,args)=>{if(args.p_complete)completions++;return {data:{object_path:'file',sha256:hash,size_bytes:pdf.length}};},storage:{from:()=>({download:async()=>({data:new Blob([pdf])})})}};
let transfer=await context.files.transferDownload(downloader,'owner','download',new AbortController().signal);assert.equal(completions,0);await transfer.arrayBuffer();assert.equal(completions,1);count++;
transfer=await context.files.transferDownload(downloader,'owner','download',new AbortController().signal);await transfer.body.cancel();assert.equal(completions,1);count++;
const abort=new AbortController();abort.abort();await assert.rejects(()=>context.files.transferDownload(downloader,'owner','download',abort.signal),/DOWNLOAD_UNCONFIRMED/);count++;
async function endpoint(name,dependencies,request){
  let handler;context.serve=callback=>handler=callback;
  const text=stripTypeScriptTypes(await readFile(new URL(`../functions/${name}/index.ts`,import.meta.url),'utf8'),{mode:'transform'}).replace(/^import[^\n]+\n/gm,'');
  runInNewContext(text,context);
  return handler(request,dependencies);
}
const edgeDeps={admin:{auth:{getUser:async()=>({data:{user:{id:'owner',is_anonymous:false}}})},rpc:async()=>({data:{id:'file',verification:'verified',object_path:'private/path',policy:{secret:'private'},expires_at:'hidden'}})},userClient:()=>({rpc:async()=>({data:{id:'owner'}})})};
const fileReply=await endpoint('request-file',edgeDeps,new Request('https://test/request-file',{method:'POST',headers:{Authorization:'Bearer test','X-GoServe-Upload':'file'},body:new Uint8Array([1])}));
const safe=await fileReply.json();assert.equal(safe.id,'file');for(const hidden of ['object_path','policy','expires_at'])assert.equal(safe[hidden],undefined);count++;
await assert.rejects(()=>endpoint('download-deliverable',edgeDeps,new Request('https://test/download-deliverable',{method:'POST',headers:{Authorization:'Bearer test'},body:JSON.stringify({kind:'forged',download_id:'id'})})),/VALIDATION_FAILED/);count++;
context.Deno={env:{get:name=>({SUPABASE_URL:'https://fixture.supabase.co',SUPABASE_ANON_KEY:'fixture',GOSERVE_RECOVERY_REDIRECT:'goserve://auth/recovery'})[name]}};
for(const status of [200,422]){
  context.fetch=async()=>new Response('{}',{status});
  const recovery=await endpoint('recovery-request',{},new Request('https://test/recovery-request',{method:'POST',body:JSON.stringify({email:'mailbox@demo.com',code_challenge:'a'.repeat(43),code_challenge_method:'s256',state:'b'.repeat(43)})}));
  assert.deepEqual(await recovery.json(),{request_received:true});count++;
}
console.log(`PASS: ${count} v2.3 Edge/adapter assertions (external Auth/Storage/parser boundaries injected)`);
