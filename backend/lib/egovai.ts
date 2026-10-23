export class EgovAiError extends Error {constructor(){super('PROVIDER_UNAVAILABLE');}}
type Configuration={base_url:string;access_code:string;integration_enabled:boolean;timeout_ms?:number};
async function boundedJson(response:Response):Promise<any>{
 if(response.status!==200||!response.body)throw new EgovAiError();
 const reader=response.body.getReader(),chunks:Uint8Array[]=[];let size=0;
 try{
  while(true){const chunk=await reader.read();if(chunk.done)break;size+=chunk.value.length;if(size>49152)throw new EgovAiError();chunks.push(chunk.value);}
 }finally{await reader.cancel().catch(()=>{});}
 const bytes=new Uint8Array(size);let offset=0;for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.length;}
 return JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(bytes));
}
export async function generateSeryo(configuration:Configuration,prompt:string,transport:typeof fetch=fetch):Promise<string>{
 if(configuration?.integration_enabled!==true||configuration.base_url!=='https://platforms-api.e.gov.ph/egov-ai'
 ||typeof configuration.access_code!=='string'||!configuration.access_code||configuration.access_code.length>1024||/[\s\x00-\x1f\x7f]/.test(configuration.access_code)
 ||typeof prompt!=='string'||!prompt||prompt.length>24000)throw new EgovAiError();
 const timeout=configuration.timeout_ms??20000;
 if(!Number.isInteger(timeout)||timeout<1||timeout>45000)throw new EgovAiError();
 const controller=new AbortController();let timer:ReturnType<typeof setTimeout>|undefined;
 const expired=new Promise<never>((_,reject)=>{timer=setTimeout(()=>{controller.abort();reject(new EgovAiError());},timeout);});
 const post=async(path:string,body:any,token?:string)=>boundedJson(await transport(configuration.base_url+path,{
  method:'POST',headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},
  body:JSON.stringify(body),redirect:'error',credentials:'omit',cache:'no-store',signal:controller.signal
 }));
 const work=async()=>{
  const token=await post('/api/v1/egov/integration/token',{access_code:configuration.access_code});
  if(typeof token?.access_token!=='string'||!token.access_token||token.access_token.length>4096||/[\s\x00-\x1f\x7f]/.test(token.access_token))throw new EgovAiError();
  const result=await post('/api/v1/egov/integration/ai_assistant/generate',{prompt,category:'PH'},token.access_token);
  if(typeof result?.data!=='string'||!result.data.trim()||result.data.length>8000||result.pending_confirmation)throw new EgovAiError();
  // Provider session identifiers never become chat history or inputs to a later request.
  return result.data;
 };
 try{return await Promise.race([work(),expired]);}catch{throw new EgovAiError();}finally{clearTimeout(timer);controller.abort();}
}
