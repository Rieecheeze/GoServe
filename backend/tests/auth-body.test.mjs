import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import test from 'node:test';

const source=stripTypeScriptTypes(await readFile(new URL('../lib/v23.ts',import.meta.url),'utf8'),{mode:'transform'}).replace(/^export /gm,'');
const context={Request,Response,TextDecoder};
runInNewContext(source+'\nglobalThis.api={readAuthBody,DomainError,errorResponse};',context);
const {readAuthBody,DomainError,errorResponse}=context.api;

test('auth JSON requires a standard content type and rejects an oversized streamed body',async()=>{
  for(const [contentType,body] of [['text/plain','{}'],['application/json','x'.repeat(8193)]]) {
    await assert.rejects(()=>readAuthBody(new Request('https://fixture',{method:'POST',headers:{'Content-Type':contentType},body})),/VALIDATION_FAILED/);
  }
  const parsed=await readAuthBody(new Request('https://fixture',{method:'POST',headers:{'Content-Type':'application/json; charset=utf-8'},body:'{"action":"status"}'}));
  assert.equal(parsed.action,'status');
});
test('the JSON bound counts UTF-8 bytes across chunks and cancels the unread stream',async()=>{
  let cancelled=false;
  const chunk=new TextEncoder().encode('é'.repeat(2100));
  const stream=new ReadableStream({start(controller){controller.enqueue(chunk);controller.enqueue(chunk);},cancel(){cancelled=true;}});
  await assert.rejects(()=>readAuthBody(new Request('https://fixture',{method:'POST',headers:{'Content-Type':'application/json'},body:stream,duplex:'half'})),/VALIDATION_FAILED/);
  assert.equal(cancelled,true);
});
test('public recovery failures expose stable codes and no provider details',async()=>{
  for(const [code,status] of [['RECOVERY_PROOF_INVALID',422],['RECOVERY_GRANT_INVALID',422],['PASSWORD_TOO_LONG',422],['RATE_LIMITED',429],['CONFIGURATION_REQUIRED',503]]) {
    const response=errorResponse(new DomainError(code));assert.equal(response.status,status);
    assert.deepEqual(await response.json(),{ok:false,error:{code}});
    assert.equal(response.headers.get('Cache-Control'),'no-store');
  }
  assert.equal(await errorResponse(new Error('private provider detail')).text(),'{"ok":false,"error":{"code":"TEMPORARILY_UNAVAILABLE"}}');
});
