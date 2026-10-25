import { serve,DomainError } from '../_shared/runtime.ts';
serve(async()=>{throw new DomainError('GOOGLE_REGISTRATION_REQUIRED');});
