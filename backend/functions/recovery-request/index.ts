import { serve,json } from '../_shared/runtime.ts';
import { exact } from '../../lib/v23.ts';
serve(async(request,{admin})=>{
  const body=await request.json();exact(body,['email']);
  if(typeof body.email==='string' && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(body.email)){
    const redirect=Deno.env.get('GOSERVE_RECOVERY_REDIRECT');
    if(redirect)try{await admin.auth.resetPasswordForEmail(body.email,{redirectTo:redirect});}catch{/* Same public result for unknown mailboxes and delivery errors. */}
  }
  return json({request_received:true});
});
