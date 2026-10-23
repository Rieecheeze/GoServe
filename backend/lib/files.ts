import { DomainError,rpc } from './v23.ts';

export async function readBounded(stream: ReadableStream<Uint8Array> | null, expected: number): Promise<Uint8Array> {
  if(!stream||!Number.isSafeInteger(expected)||expected<=0)throw new DomainError('UPLOAD_INVALID');
  const reader=stream.getReader();const chunks: Uint8Array[]=[];let length=0;
  try{
    for(;;){const {value,done}=await reader.read();if(done)break;length+=value.byteLength;
      if(length>expected){await reader.cancel();throw new DomainError('UPLOAD_INVALID');}chunks.push(value);}
  }finally{reader.releaseLock();}
  if(length!==expected)throw new DomainError('UPLOAD_INVALID');
  const bytes=new Uint8Array(length);let offset=0;for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.length;}return bytes;
}

export async function verifyFile(bytes: Uint8Array,mime: string,policy: any,parsers: any): Promise<string> {
  if(!policy||policy.verifier!=='goserve-v1'||!policy.mime_types?.includes(mime)
    ||!Number.isSafeInteger(policy.max_bytes)||policy.max_bytes<=0)throw new DomainError('CONFIGURATION_REQUIRED');
  if(!bytes.length||bytes.length>policy.max_bytes)throw new DomainError('UPLOAD_INVALID');
  if(mime.startsWith('image/')&&(!Number.isSafeInteger(policy.max_pixels)||policy.max_pixels<=0))throw new DomainError('CONFIGURATION_REQUIRED');
  try{
    if(mime==='application/pdf'){
      if(new TextDecoder().decode(bytes.slice(0,5))!=='%PDF-'||!new TextDecoder().decode(bytes.slice(-1024)).includes('%%EOF'))throw new Error();
      const pdf=await parsers.pdf(bytes);if(pdf.encrypted||pdf.pages<1)throw new Error();
    }else if(mime==='image/png'){
      if(![137,80,78,71,13,10,26,10].every((v,i)=>bytes[i]===v))throw new Error();
      const dimensions=new DataView(bytes.buffer,bytes.byteOffset,bytes.byteLength);
      if(dimensions.getUint32(16)*dimensions.getUint32(20)>policy.max_pixels)throw new Error();
      await parsers.png(bytes,policy);
    }else if(mime==='image/jpeg'){
      if(bytes[0]!==255||bytes[1]!==216||bytes.at(-2)!==255||bytes.at(-1)!==217)throw new Error();
      await parsers.jpeg(bytes,policy);
    }else throw new Error();
  }catch{throw new DomainError('UPLOAD_INVALID');}
  const digestBytes = new Uint8Array(bytes.byteLength);
  digestBytes.set(bytes);
  return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',digestBytes.buffer))).map(b=>b.toString(16).padStart(2,'0')).join('');
}

export async function transferDownload(admin: any,actor: string,downloadId: string,signal: AbortSignal): Promise<Response> {
  const args={p_actor:actor,p_download_id:downloadId};
  const record=await rpc(admin,'download_server_operation',args);
  const {data,error}=await admin.storage.from('goserve-private').download(record.object_path);
  if(error||!data)throw new DomainError('TEMPORARILY_UNAVAILABLE');
  const bytes=new Uint8Array(await data.arrayBuffer());
  const hash=Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes))).map(b=>b.toString(16).padStart(2,'0')).join('');
  if(bytes.length!==Number(record.size_bytes)||hash!==record.sha256||signal.aborted)throw new DomainError('DOWNLOAD_UNCONFIRMED');
  let offset=0;let cancelled=false;
  const stream=new ReadableStream<Uint8Array>({
    async pull(controller){
      if(cancelled||signal.aborted){controller.error(new DomainError('DOWNLOAD_UNCONFIRMED'));return;}
      if(offset<bytes.length){const end=Math.min(offset+65536,bytes.length);controller.enqueue(bytes.slice(offset,end));offset=end;return;}
      try{
        await rpc(admin,'download_server_operation',{...args,p_complete:true});
        if(cancelled||signal.aborted){controller.error(new DomainError('DOWNLOAD_UNCONFIRMED'));return;}
        controller.close();
      }catch(error){controller.error(error);}
    },cancel(){cancelled=true;}
  },{highWaterMark:0});
  return new Response(stream,{headers:{'Content-Type':'application/pdf','Cache-Control':'no-store',
    'Content-Length':String(bytes.length),'Content-Disposition':'attachment; filename="GoServe-document.pdf"'}});
}
