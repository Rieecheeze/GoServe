export class EgovAiEngineError extends Error{constructor(){super('PROVIDER_UNAVAILABLE');}}
type EngineConfiguration={integration_enabled:boolean;api_key:string;api_secret:string;engine_code:string;timeout_ms?:number};
export async function generateSeryoEngine(configuration:EngineConfiguration,prompt:string,transport:typeof fetch=fetch):Promise<string>{
 const header=(value:unknown)=>typeof value==='string'&&value.length>0&&value.length<=4096&&!/[\s\x00-\x1f\x7f]/.test(value);
 if(configuration?.integration_enabled!==true||!header(configuration.api_key)||!header(configuration.api_secret)
 ||!header(configuration.engine_code)||!/^[A-Za-z0-9_-]{1,200}$/.test(configuration.engine_code)
 ||typeof prompt!=='string'||!prompt||prompt.length>24000)throw new EgovAiEngineError();
 const timeout=configuration.timeout_ms??20000;
 if(!Number.isInteger(timeout)||timeout<1||timeout>45000)throw new EgovAiEngineError();
 const controller=new AbortController();let timer:ReturnType<typeof setTimeout>|undefined;
 let reader:ReadableStreamDefaultReader<Uint8Array>|undefined;
 const expired=new Promise<never>((_,reject)=>{timer=setTimeout(()=>{controller.abort();void reader?.cancel().catch(()=>{});reject(new EgovAiEngineError());},timeout);});
 const work=async()=>{
  const response=await transport('https://egov-ai-core-ws.e.gov.ph/api/v1/engines/generate_stream',{
   method:'POST',headers:{'Content-Type':'application/json','X-EGOVAI-API-KEY':configuration.api_key,
   'X-EGOVAI-API-SECRET':configuration.api_secret,'X-EGOVAI-AGENT-ENGINE-CODE':configuration.engine_code},
   body:JSON.stringify({question:prompt,session_id:null}),redirect:'error',credentials:'omit',cache:'no-store',signal:controller.signal
  });
  if(response.status!==200||!response.headers.get('Content-Type')?.toLowerCase().startsWith('text/event-stream')||!response.body)throw new EgovAiEngineError();
  reader=response.body.getReader();const decoder=new TextDecoder('utf-8',{fatal:true});
  let buffer='',eventName='',data:string[]=[],reply='',size=0,complete=false;
  const dispatch=()=>{
   if(!eventName&&!data.length)return;
   if(complete||!['progress','chunk','complete','error'].includes(eventName))throw new EgovAiEngineError();
   const value=JSON.parse(data.join('\n'));
   if(!value||typeof value!=='object'||Array.isArray(value)||value.tool_calls||value.tool_call)throw new EgovAiEngineError();
   if(eventName==='error')throw new EgovAiEngineError();
   if(eventName==='chunk'){
    if(typeof value.content!=='string')throw new EgovAiEngineError();reply+=value.content;if(reply.length>8000)throw new EgovAiEngineError();
   }
   if(eventName==='complete'){
    if(value.pending_confirmation!==undefined&&value.pending_confirmation!==null&&value.pending_confirmation!==false)throw new EgovAiEngineError();
    complete=true;
   }
   eventName='';data=[];
  };
  while(!complete){
   const chunk=await reader.read();if(chunk.done)break;size+=chunk.value.length;if(size>65536)throw new EgovAiEngineError();
   buffer+=decoder.decode(chunk.value,{stream:true});
   let newline:number;
   while((newline=buffer.indexOf('\n'))>=0){
    let line=buffer.slice(0,newline);buffer=buffer.slice(newline+1);if(line.endsWith('\r'))line=line.slice(0,-1);
    if(!line){dispatch();if(complete)break;}
    else if(line.startsWith('event:'))eventName=line.slice(6).trim();
    else if(line.startsWith('data:'))data.push(line.slice(5).replace(/^ /,''));
    else if(!line.startsWith(':')&&!line.startsWith('id:')&&!line.startsWith('retry:'))throw new EgovAiEngineError();
   }
  }
  if(!complete||!reply.trim())throw new EgovAiEngineError();
  // Only the complete answer is returned; provider sessions, source labels and tool metadata are discarded.
  return reply;
 };
 try{return await Promise.race([work(),expired]);}catch{throw new EgovAiEngineError();}
 finally{clearTimeout(timer);controller.abort();void reader?.cancel().catch(()=>{});}
}
