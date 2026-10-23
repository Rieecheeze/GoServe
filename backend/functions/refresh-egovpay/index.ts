import { serve,authenticated,json } from '../_shared/runtime.ts';
import { refreshEgovPay,readEgovPayBody } from '../../lib/egovpay-workflow.ts';
serve(async(request,{admin,userClient})=>{
  const {client,user}=await authenticated(request,admin,userClient);
  return json(await refreshEgovPay(admin,client,user.id,await readEgovPayBody(request)));
});
