import { clients,json } from '../_shared/runtime.ts';
import { callbackEgovPay } from '../../lib/egovpay-workflow.ts';
Deno.serve(async request=>{
  try {return json(await callbackEgovPay(clients().admin,request),202);}
  catch {return json({received:true},202);}
});
