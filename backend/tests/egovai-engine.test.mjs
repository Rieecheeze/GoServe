import assert from 'node:assert/strict';
import test from 'node:test';
import {readFile} from 'node:fs/promises';
import {stripTypeScriptTypes} from 'node:module';
const source=await readFile(new URL('../lib/egovai-engine.ts',import.meta.url),'utf8');
const {generateSeryoEngine,EgovAiEngineError}=await import('data:text/javascript;base64,'+Buffer.from(stripTypeScriptTypes(source,{mode:'transform'})).toString('base64'));
const config={integration_enabled:true,api_key:'synthetic-key',api_secret:'synthetic-secret',engine_code:'seryo-test'};
const event=(name,data)=>`event: ${name}\ndata: ${JSON.stringify(data)}\n\n`;
const stream=(text)=>new Response(text,{headers:{'Content-Type':'text/event-stream'}});
const valid=event('progress',{label:'Finding relevant services'})+event('chunk',{content:'Open Requests.'})+event('complete',{session_id:'discard-me',pending_confirmation:null,source_activities:[]});
await test('custom engine uses fixed official route and exactly scoped server credentials',async()=>{
 let request;const reply=await generateSeryoEngine(config,'Scoped Seryo prompt',async(url,init)=>{request={url,init};return stream(valid);});
 assert.equal(reply,'Open Requests.');assert.equal(request.url,'https://egov-ai-core-ws.e.gov.ph/api/v1/engines/generate_stream');
 assert.deepEqual(request.init.headers,{'Content-Type':'application/json','X-EGOVAI-API-KEY':'synthetic-key','X-EGOVAI-API-SECRET':'synthetic-secret','X-EGOVAI-AGENT-ENGINE-CODE':'seryo-test'});
 assert.deepEqual(JSON.parse(request.init.body),{question:'Scoped Seryo prompt',session_id:null});assert.equal(request.init.redirect,'error');
});
await test('chunk boundaries and CRLF do not expose partial text',async()=>{
 const bytes=new TextEncoder().encode(valid.replace(/\n/g,'\r\n'));let i=0;
 const body=new ReadableStream({pull(c){if(i===bytes.length)c.close();else c.enqueue(bytes.slice(i,++i));}});
 assert.equal(await generateSeryoEngine(config,'prompt',async()=>new Response(body,{headers:{'Content-Type':'text/event-stream'}})),'Open Requests.');
});
await test('tool confirmations, missing completion, malformed events and provider errors fail closed',async()=>{
 for(const data of [event('chunk',{content:'Partial'})+event('complete',{pending_confirmation:{token:'do-not-use'}}),event('chunk',{content:'Partial'}),event('error',{message:'private error'}),'event: chunk\ndata: not-json\n\n',event('chunk',{content:{raw:'object'}})+event('complete',{}),event('tool_call',{name:'sql'})+valid]){
  await assert.rejects(()=>generateSeryoEngine(config,'prompt',async()=>stream(data)),e=>e instanceof EgovAiEngineError&&!e.message.includes('private'));
 }
});
await test('disabled credentials, multiple engines and injected header values never dispatch',async()=>{
 for(const value of [{...config,integration_enabled:false},{...config,api_secret:''},{...config,engine_code:'seryo,other'},{...config,api_key:'a\r\nX-Evil: true'}]){
  let calls=0;await assert.rejects(()=>generateSeryoEngine(value,'prompt',async()=>{calls++;return stream(valid);}),EgovAiEngineError);assert.equal(calls,0);
 }
});
await test('oversized text and a stalled network are bounded',async()=>{
 await assert.rejects(()=>generateSeryoEngine(config,'prompt',async()=>stream(event('chunk',{content:'x'.repeat(8001)})+event('complete',{}))),EgovAiEngineError);
 await assert.rejects(()=>generateSeryoEngine({...config,timeout_ms:10},'prompt',async()=>new Promise(()=>{})),EgovAiEngineError);
});
