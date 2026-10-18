import { serve,json,authenticated,DomainError } from '../_shared/runtime.ts';
import { exact,rpc } from '../../lib/v23.ts';
import { readBounded,verifyFile } from '../../lib/files.ts';
import { parsers } from '../_shared/file-parsers.ts';
serve(async(request,{admin,userClient})=>{
  const {client,user}=await authenticated(request,admin,userClient);
  const uploadId=request.headers.get('X-GoServe-Upload');
  if(uploadId){
    const args={p_actor:user.id,p_file_id:uploadId};
    const file=await rpc(admin,'file_server_operation',{...args,p_command:'authorize'});
    if(file.verification==='verified'){
      const {object_path,expires_at,policy,...metadata}=file;
      return json(metadata);
    }
    const bytes=await readBounded(request.body,Number(file.size_bytes));
    const sha=await verifyFile(bytes,file.mime_type,file.policy,parsers);
    const bucket=admin.storage.from('goserve-private');
    const {error}=await bucket.upload(file.object_path,bytes,{contentType:file.mime_type,upsert:false});
    if(error){
      // A lost upload response is safe to reconcile only if the immutable object has the same verified bytes.
      const existing=await bucket.download(file.object_path);
      if(existing.error||!existing.data)throw new DomainError('TEMPORARILY_UNAVAILABLE');
      const old=await verifyFile(new Uint8Array(await existing.data.arrayBuffer()),file.mime_type,file.policy,parsers);
      if(old!==sha)throw new DomainError('UPLOAD_INVALID');
    }
    return json(await rpc(admin,'file_server_operation',{...args,p_command:'verified',p_sha256:sha}));
  }
  const body=await request.json();
  if(body.kind==='download'){
    exact(body,['kind','file_id']);
    const file=await rpc(admin,'file_server_operation',{p_actor:user.id,p_file_id:body.file_id,p_command:'read'});
    const {data,error}=await admin.storage.from('goserve-private').download(file.object_path);
    if(error||!data)throw new DomainError('TEMPORARILY_UNAVAILABLE');
    return new Response(data,{headers:{'Content-Type':file.mime_type,'Cache-Control':'no-store','Content-Disposition':'attachment'}});
  }
  exact(body,['service_id','request_id','kind','requirement','mime_type','size_bytes','idempotency_key']);
  return json(await rpc(client,'prepare_file',{p_service_id:body.service_id,p_request_id:body.request_id,p_kind:body.kind,
    p_requirement:body.requirement,p_mime_type:body.mime_type,p_size_bytes:body.size_bytes,p_idempotency_key:body.idempotency_key}));
});
