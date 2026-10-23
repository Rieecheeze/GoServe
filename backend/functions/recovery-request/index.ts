import { serve,json } from '../_shared/runtime.ts';
import { requestRecovery } from '../../lib/v23.ts';
serve(async(request)=>{
  await requestRecovery(await request.json(), {
    url: Deno.env.get('SUPABASE_URL'),
    publicKey: Deno.env.get('SUPABASE_ANON_KEY'),
    redirect: Deno.env.get('GOSERVE_RECOVERY_REDIRECT'),
  });
  return json({request_received:true});
});
