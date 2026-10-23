const base='https://uuvexkewiuxdbuelwogy.supabase.co/functions/v1/';
const checks=[];
async function verify(label,path,options,status,assertion) {
 const response=await fetch(base+path,{...options,redirect:'error',signal:AbortSignal.timeout(20000)});
 const body=await response.text();
 const ok=response.status===status&&assertion(response,body);
 checks.push({label,status:response.status,ok});
 if(!ok)throw new Error('PAYMENT_BOUNDARY_FAILED');
}
const forged={payment_status:'PAID',amount:'1',currency:'PHP',payment_id:'00000000-0000-4000-8000-000000000001'};
const post={method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(forged)};
const opaque=(_response,body)=>JSON.stringify(JSON.parse(body))===JSON.stringify({received:true});
try {
 await verify('callback without capability','egovpay-callback',post,202,opaque);
 await verify('forged paid callback with unknown capability','egovpay-callback?token=00000000-0000-4000-8000-000000000001',post,202,opaque);
 await verify('return page does not reflect input','egovpay-return?status=PAID&marker=UNTRUSTED_BOUNDARY_MARKER',{method:'GET'},200,
  (_response,body)=>body.includes('Return to GoServe')&&!body.includes('UNTRUSTED_BOUNDARY_MARKER')&&!body.includes('status=PAID'));
 console.log(JSON.stringify({project:'uuvexkewiuxdbuelwogy',checks,passed:checks.length}));
}catch {
 console.log(JSON.stringify({project:'uuvexkewiuxdbuelwogy',checks,error:'PAYMENT_BOUNDARY_FAILED'}));process.exitCode=1;
}
