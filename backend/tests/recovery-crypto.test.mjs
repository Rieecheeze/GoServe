import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { stripTypeScriptTypes } from 'node:module';
import { runInNewContext } from 'node:vm';
import { isIP } from 'node:net';
import test from 'node:test';

let source='';
try { source=await readFile(new URL('../lib/recovery.ts',import.meta.url),'utf8'); } catch(error) { if(error.code!=='ENOENT')throw error; }
source=stripTypeScriptTypes(source,{mode:'transform'}).replace(/^import[^\n]+\n/gm,'').replace(/^export /gm,'');
const context={isIP,crypto,Date,URL,TextEncoder,TextDecoder,Uint8Array,Response,Request,btoa,atob,
  DomainError:class extends Error { constructor(code) { super(code);this.code=code; } }};
runInNewContext(source+'\nglobalThis.api={codeSet:typeof codeSet==="function"?codeSet:null,digest:typeof digest==="function"?digest:null,gatewayIp:typeof gatewayIp==="function"?gatewayIp:null};',context);
const api=context.api;
test('code generation encodes all twenty CSPRNG bytes without reducing entropy',async()=>{
  const original=context.crypto;
  context.crypto={subtle:crypto.subtle,getRandomValues:array=>{assert.equal(array.length,20);array.set(Array.from({length:20},(_,i)=>i));return array;}};
  try { assert.equal((await api.codeSet()).codes[0],'AAAQ-EAYE-AUDA-OCAJ-BIFQ-YDIO-B4IB-CEQT'); }
  finally { context.crypto=original; }
});
test('each generated code has 160 random bits and only domain-separated digests are persisted',async()=>{
  assert.equal(typeof api.codeSet,'function');
  const one=await api.codeSet(),two=await api.codeSet();
  assert.equal(one.codes.length,10);assert.equal(new Set([...one.codes,...two.codes]).size,20);
  for(let i=0;i<10;i++){
    assert.match(one.codes[i],/^(?:[A-Z2-7]{4}-){7}[A-Z2-7]{4}$/);
    assert.match(one.digests[i],/^[0-9a-f]{64}$/);assert.notEqual(one.digests[i],await api.digest(one.codes[i]));
  }
});
test('forwarded headers cannot replace the activated single CF address',()=>{
  assert.equal(typeof api.gatewayIp,'function');
  const request=new Request('https://fixture',{headers:{'CF-Connecting-IP':'203.0.113.7','X-Forwarded-For':'198.51.100.99','X-Real-IP':'198.51.100.8'}});
  assert.throws(()=>api.gatewayIp(request),/TEMPORARILY_UNAVAILABLE/);
  assert.equal(api.gatewayIp(request,0),'203.0.113.7');
  request.headers.set('X-Forwarded-For','192.0.2.7, 192.0.2.8');
  assert.equal(api.gatewayIp(request,0),'203.0.113.7');
});
test('missing or multi-address CF headers never fall back to forwarded addresses',()=>{
  for(const value of ['', 'not-an-ip', '203.0.113.7, 203.0.113.8','[2001:db8::7]','203.0.113.7:443']) {
    const request=new Request('https://fixture',{headers:{'CF-Connecting-IP':value,'X-Forwarded-For':'198.51.100.1','X-Real-IP':'198.51.100.1'}});
    assert.throws(()=>api.gatewayIp(request,0),/TEMPORARILY_UNAVAILABLE/);
  }
  assert.equal(api.gatewayIp(new Request('https://fixture',{headers:{'CF-Connecting-IP':'2001:db8::7'}}),0),'2001:db8::7');
});
test('only explicit numeric zero activates CF ingress selection',()=>{
  const request=new Request('https://fixture',{headers:{'CF-Connecting-IP':'203.0.113.7','X-Forwarded-For':'198.51.100.99, 198.51.100.8'}});
  for(const value of [undefined,-1,1,2,8,9,NaN,Infinity,'0'])assert.throws(()=>api.gatewayIp(request,value),/TEMPORARILY_UNAVAILABLE/);
});
test('runtime activates ingress only for the exact approved zero setting',async()=>{
  let activation;
  const runtime={DomainError:context.DomainError,createClient:()=>({}),authenticated(){},json(){},errorResponse(){},
    Deno:{env:{get:name=>name==='GOSERVE_RECOVERY_PROXY_HOPS'?activation:({SUPABASE_URL:'https://fixture.supabase.co',SUPABASE_SERVICE_ROLE_KEY:'synthetic-server-key',SUPABASE_ANON_KEY:'synthetic-public-key'})[name]}}};
  const text=stripTypeScriptTypes(await readFile(new URL('../functions/_shared/runtime.ts',import.meta.url),'utf8'),{mode:'transform'}).replace(/^import[^\n]+\n/gm,'').replace(/^export \{[^\n]+\};\r?$/gm,'').replace(/^export /gm,'');
  runInNewContext(text+'\nglobalThis.settings=clients;',runtime);
  activation='0';assert.equal(runtime.settings().recoveryProxyHops,0);
  for(activation of [undefined,'disabled','1','8','00',' 0'])assert.equal(runtime.settings().recoveryProxyHops,undefined);
});
