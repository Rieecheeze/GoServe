import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import test from 'node:test';

const context={Request,Response,URL,Uint8Array,TextDecoder,ReadableStream,crypto,AbortSignal};
for(const file of ['v23','files']) {
  const source=stripTypeScriptTypes(await readFile(new URL(`../lib/${file}.ts`,import.meta.url),'utf8'),{mode:'transform'}).replace(/^import[^\n]+\n/gm,'').replace(/^export /gm,'');
  runInNewContext(source,context);
}
const handlers={};
for(const name of ['request-file','download-deliverable']) {
  context.serve=handler=>{handlers[name]=handler;};
  const source=stripTypeScriptTypes(await readFile(new URL(`../functions/${name}/index.ts`,import.meta.url),'utf8'),{mode:'transform'}).replace(/^import[^\n]+\n/gm,'');
  runInNewContext(source,context);
}
const bytes=new TextEncoder().encode('%PDF-fixture\n%%EOF');
const sha256=createHash('sha256').update(bytes).digest('hex');
const branches=[
  {name:'precreated upload ticket',endpoint:'request-file',headers:{'X-GoServe-Upload':'file'},body:bytes},
  {name:'existing evidence read',endpoint:'request-file',body:JSON.stringify({kind:'download',file_id:'file'})},
  {name:'issued deliverable transfer',endpoint:'download-deliverable',body:JSON.stringify({kind:'transfer',download_id:'download'})},
];
function harness(nextAction,recoverySetup=true) {
  const effects=[];
  const account={id:'owner',profile_complete:nextAction!=='complete_profile',terms:{current_version:'v2',accepted_version:nextAction==='accept_terms'?'v1':'v2'},
    recovery:{codes_configured:!recoverySetup,codes_acknowledged:!recoverySetup,remaining_codes:recoverySetup?0:10,generation:null,requires_setup:recoverySetup},next_action:nextAction};
  return {effects,admin:{auth:{getUser:async()=>({data:{user:{id:'owner',is_anonymous:false}},error:null})},
    rpc:async(name,args)=>{effects.push({name,args});return {data:{id:'file',verification:'verified',object_path:'private/file',mime_type:'application/pdf',size_bytes:bytes.length,sha256}};},
    storage:{from:()=>({download:async()=>{effects.push({name:'storage'});return {data:new Blob([bytes]),error:null};}})}},
    userClient:()=>({rpc:async name=>{assert.equal(name,'account_view');return {data:account,error:null};}})};
}
async function invoke(branch,deps) {
  const response=await handlers[branch.endpoint](new Request('https://fixture/'+branch.endpoint,{method:'POST',headers:{Authorization:'Bearer fixture',...branch.headers},body:branch.body}),deps);
  await response.arrayBuffer();return response;
}
for(const [action,error] of [['setup_recovery','RECOVERY_SETUP_REQUIRED'],['accept_terms','TERMS_REQUIRED'],['complete_profile','PROFILE_INCOMPLETE']]) {
  for(const branch of branches)test(`${branch.name} denies ${action} before privileged file access`,async()=>{
    const deps=harness(action);
    await assert.rejects(()=>invoke(branch,deps),new RegExp(error));
    assert.equal(deps.effects.length,0);
  });
}
for(const branch of branches)test(`${branch.name} preserves preactivation home routing`,async()=>{
  const deps=harness('home',true);
  assert.equal((await invoke(branch,deps)).status,200);
  assert.ok(deps.effects.some(effect=>effect.name==='file_server_operation'||effect.name==='download_server_operation'));
});
