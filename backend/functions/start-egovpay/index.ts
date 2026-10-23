import { serve,authenticated,json } from '../_shared/runtime.ts';
import { startEgovPay,readEgovPayBody } from '../../lib/egovpay-workflow.ts';
serve(async(request,{admin,userClient})=>{
  const {client,user}=await authenticated(request,admin,userClient);
  return json(await startEgovPay(admin,client,user.id,await readEgovPayBody(request),Deno.env.get('SUPABASE_URL')??''));
});
