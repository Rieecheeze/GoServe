import assert from 'node:assert/strict';
import test from 'node:test';
import {readFile} from 'node:fs/promises';
import {stripTypeScriptTypes} from 'node:module';
const source=await readFile(new URL('../lib/egovai.ts',import.meta.url),'utf8');
const {generateSeryo,EgovAiError}=await import('data:text/javascript;base64,'+Buffer.from(stripTypeScriptTypes(source,{mode:'transform'})).toString('base64'));
const configuration={base_url:'https://platforms-api.e.gov.ph/egov-ai',access_code:'synthetic-code',integration_enabled:true};
const response=(data,status=200)=>new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json'}});
await test('exchange and assistant use the documented gateway routes and discard session identity',async()=>{
 const calls=[];
 const result=await generateSeryo(configuration,'Safe test prompt',async(url,init)=>{
  calls.push({url,init});return calls.length===1?response({access_token:'synthetic-token',expires_in_seconds:28800}):response({data:'Open the Requests tab.',session_id:'private-session'});
 });
 assert.equal(result,'Open the Requests tab.');assert.equal(calls.length,2);
 assert.equal(calls[0].url,configuration.base_url+'/api/v1/egov/integration/token');
 assert.deepEqual(JSON.parse(calls[0].init.body),{access_code:'synthetic-code'});
 assert.equal(calls[1].url,configuration.base_url+'/api/v1/egov/integration/ai_assistant/generate');
 assert.deepEqual(JSON.parse(calls[1].init.body),{prompt:'Safe test prompt',category:'PH'});
 assert.equal(calls[1].init.headers.Authorization,'Bearer synthetic-token');assert.equal(calls[1].init.redirect,'error');
});
await test('disabled, foreign hosts and missing secrets cannot dispatch',async()=>{
 for(const value of [{...configuration,integration_enabled:false},{...configuration,base_url:'https://evil.test'},{...configuration,access_code:''}]){
  let calls=0;await assert.rejects(()=>generateSeryo(value,'prompt',async()=>{calls++;return response({});}),EgovAiError);assert.equal(calls,0);
 }
});
await test('invalid credentials, malformed payload and provider errors stay controlled',async()=>{
 for(const value of [response({error:'private provider diagnostic'},401),response({access_token:''}),response({access_token:'contains spaces'})])await assert.rejects(()=>generateSeryo(configuration,'prompt',async()=>value),e=>e instanceof EgovAiError&&!e.message.includes('private'));
 let n=0;await assert.rejects(()=>generateSeryo(configuration,'prompt',async()=>++n===1?response({access_token:'token'}):response({data:{private:'raw'}})),EgovAiError);
});
await test('unbounded output and slow decoding are terminated',async()=>{
 let n=0;await assert.rejects(()=>generateSeryo(configuration,'prompt',async()=>++n===1?response({access_token:'token'}):response({data:'x'.repeat(8001)})),EgovAiError);
 await assert.rejects(()=>generateSeryo({...configuration,timeout_ms:10},'prompt',async()=>new Promise(()=>{})),EgovAiError);
});
