import { serve,authenticated } from '../_shared/runtime.ts';
import { providerBoundary } from '../../lib/v23.ts';
serve(async(request,{admin,userClient})=>{
  const {client}=await authenticated(request,admin,userClient);
  await providerBoundary(client,'seryo');
  throw new Error('Provider unavailable');
});
