import { json } from '../../lib/v23.ts';
// An authenticated provider wire contract has not been supplied. Never parse a callback as financial proof.
Deno.serve(()=>json({ok:false,error:{code:'PROVIDER_UNAVAILABLE'}},503));
