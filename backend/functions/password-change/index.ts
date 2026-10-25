import { serve,DomainError } from '../_shared/runtime.ts';
serve(async()=>{throw new DomainError('RECOVERY_METHOD_REQUIRED');});
