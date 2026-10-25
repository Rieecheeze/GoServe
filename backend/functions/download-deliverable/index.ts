import { serve,json,authenticated,DomainError } from '../_shared/runtime.ts';
import { exact,rpc } from '../../lib/v23.ts';
import { transferDownload } from '../../lib/files.ts';
serve(async(request,{admin,userClient})=>{
  const {client,user}=await authenticated(request,admin,userClient,true);const body=await request.json();
  if(body.kind==='begin'){exact(body,['kind','file_id']);return json(await rpc(client,'begin_download',{p_file_id:body.file_id}));}
  exact(body,['kind','download_id']);
  if(body.kind!=='transfer')throw new DomainError('VALIDATION_FAILED');
  return await transferDownload(admin,user.id,body.download_id,request.signal);
});
