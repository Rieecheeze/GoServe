import {readFile} from 'node:fs/promises';
const base='https://uuvexkewiuxdbuelwogy.supabase.co';
const configuration=await readFile(new URL('../android/app/build/generated/source/buildConfig/debug/ph/edu/tip/goserve/BuildConfig.java',import.meta.url),'utf8');
const value=name=>JSON.parse(configuration.match(new RegExp(`String ${name} = ("[^"\\r\\n]*");`))[1]);
if(value('SUPABASE_URL')!==base)throw new Error('PROJECT_MISMATCH');
const key=value('SUPABASE_PUBLISHABLE_KEY'),results=[];
async function request(path,body,token){
 const r=await fetch(base+path,{method:'POST',headers:{apikey:key,'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:JSON.stringify(body),redirect:'error',signal:AbortSignal.timeout(20000)});
 const data=await r.json().catch(()=>null);return {status:r.status,data};
}
function verify(label,result,status,code){
 const actual=result.data?.error?.code;
 const ok=result.status===status&&actual===code;results.push({label,status:result.status,code:actual??'UNEXPECTED_RESPONSE',ok});
 if(!ok)throw new Error('ACCESS_BOUNDARY_FAILED');
}
let token;
try{
 for(const name of ['request-file','download-deliverable','seryo-control','seryo-send','start-egovpay','refresh-egovpay']){
  verify(name+' anonymous',await request('/functions/v1/'+name,{}),401,'AUTH_REQUIRED');
  verify(name+' invalid token',await request('/functions/v1/'+name,{},'synthetic-invalid-token'),401,'AUTH_INVALID');
 }
 verify('cleanup anonymous',await request('/functions/v1/cleanup-files',{}),403,'FORBIDDEN');
 verify('cleanup wrong worker',await request('/functions/v1/cleanup-files',{},'synthetic-invalid-worker-token'),403,'FORBIDDEN');
 if(process.argv[2]){
  const privateRecord=JSON.parse(await readFile(process.argv[2],'utf8'));
  const student=privateRecord.accounts?.find(a=>a.email==='student.ccs@demo.goserve.test');
  if(privateRecord.project_ref!=='uuvexkewiuxdbuelwogy'||!student)throw new Error('DEMO_SCOPE_MISMATCH');
  const login=await request('/auth/v1/token?grant_type=password',{email:student.email,password:student.password});
  if(login.status!==200||!login.data?.access_token)throw new Error('DEMO_LOGIN_FAILED');token=login.data.access_token;
  verify('student Seryo onboarding guard',await request('/functions/v1/seryo-control',{kind:'readiness'},token),409,'PROFILE_INCOMPLETE');
  verify('student download onboarding guard',await request('/functions/v1/download-deliverable',{kind:'begin',file_id:'00000000-0000-4000-8000-000000000000'},token),409,'PROFILE_INCOMPLETE');
 }
 console.log(JSON.stringify({project:base,checks:results,passed:results.length}));
}catch{
 console.log(JSON.stringify({project:base,checks:results,error:'ACCESS_VERIFICATION_FAILED'}));process.exitCode=1;
}finally{
 if(token){try{const closed=await request('/auth/v1/logout?scope=local',{},token);if(![200,204,401].includes(closed.status))process.exitCode=1;}catch{process.exitCode=1;}}
}
