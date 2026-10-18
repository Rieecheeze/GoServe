import { createClient } from 'npm:@supabase/supabase-js@2.117.3';
import { DomainError, errorResponse, json, authenticated } from '../../lib/v23.ts';

export function clients() {
  const url=Deno.env.get('SUPABASE_URL');
  const secret=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
  const anon=Deno.env.get('SUPABASE_ANON_KEY');
  if (!url?.startsWith('https://') || !secret || !anon) throw new DomainError('CONFIGURATION_REQUIRED');
  const options={auth:{persistSession:false,autoRefreshToken:false,detectSessionInUrl:false}};
  return {admin:createClient(url,secret,options),userClient:(token: string)=>createClient(url,anon,{...options,global:{headers:{Authorization:`Bearer ${token}`}}})};
}

export function serve(handler: (request: Request, dependencies: ReturnType<typeof clients>)=>Promise<Response>) {
  Deno.serve(async request=>{
    try {
      if(request.method!=='POST')return json({ok:false,error:{code:'METHOD_NOT_ALLOWED'}},405);
      return await handler(request,clients());
    }catch(error){return errorResponse(error instanceof SyntaxError?new DomainError('VALIDATION_FAILED'):error);}
  });
}
export {authenticated,json,DomainError};
