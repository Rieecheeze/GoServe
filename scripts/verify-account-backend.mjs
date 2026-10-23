import {readFile,writeFile} from 'node:fs/promises';
import {fileURLToPath,pathToFileURL} from 'node:url';

const project='https://uuvexkewiuxdbuelwogy.supabase.co';
const roster=new Set(['registrar@demo.goserve.test','accounting@demo.goserve.test','teller@demo.goserve.test',
 'guidance@demo.goserve.test','secretary.ccs@demo.goserve.test','student.ccs@demo.goserve.test']);
const scope=['id','email','role','office_id','staff_capability','department_id'];

export function verifyAccount(expected,rows,rpc,bootstrap){
 if(!Array.isArray(rows) || rows.length!==1 || !rpc || !bootstrap)throw new Error('ACCOUNT_SCOPE_MISMATCH');
 for(const value of[rows[0],rpc,bootstrap]){
  if(scope.some(k=>value[k]!==expected[k]) || value.is_demo!==true)throw new Error('ACCOUNT_SCOPE_MISMATCH');
 }
 for(const value of[rpc,bootstrap]){
  if(value.next_action!=='accept_terms' || value.profile_complete!==false || !value.terms ||
   value.terms.current_version!==null || value.terms.accepted_version!=null || value.terms.accepted_at!=null ||
   value.avatar_path!==null || value.avatar_version!==0 || !Number.isSafeInteger(value.version) || value.version<1)
   throw new Error('ACCOUNT_READINESS_MISMATCH');
 }
 return {scopeVerified:true,nextAction:'accept_terms',termsActivated:false,profileCompleted:false};
}

export async function main(privatePath){
 if(!privatePath)throw new Error('PRIVATE_CREDENTIAL_PATH_REQUIRED');
 const record=JSON.parse(await readFile(privatePath,'utf8'));
 if(record.project_ref!=='uuvexkewiuxdbuelwogy' || record.accounts?.length!==6 ||
  new Set(record.accounts.map(a=>a.email)).size!==6 || record.accounts.some(a=>!roster.has(a.email) || !a.id || typeof a.password!=='string' || !a.password))
  throw new Error('DEMO_SCOPE_MISMATCH');
 const config=await readFile(fileURLToPath(new URL('../android/app/build/generated/source/buildConfig/debug/ph/edu/tip/goserve/BuildConfig.java',import.meta.url)),'utf8');
 const configured=name=>{const m=config.match(new RegExp(`String ${name} = ("[^"\\r\\n]*");`));if(!m)throw new Error('APP_CONFIG_MISSING');return JSON.parse(m[1]);};
 if(configured('SUPABASE_URL')!==project)throw new Error('APP_PROJECT_MISMATCH');
 const key=configured('SUPABASE_PUBLISHABLE_KEY');
 async function request(path,method,body,token){
  const response=await fetch(project+path,{method,headers:{apikey:key,Accept:'application/json',...(body?{'Content-Type':'application/json'}:{}),...(token?{Authorization:'Bearer '+token}:{})},
   ...(body?{body:JSON.stringify(body)}:{}),redirect:'error',signal:AbortSignal.timeout(20000)});
  return {ok:response.ok,status:response.status,data:await response.json().catch(()=>null)};
 }
 const results=[];
 for(const account of record.accounts){
  let token;const result={email:account.email,authVerified:false,scopeVerified:false,verificationSessionClosed:false};
  try{
   const login=await request('/auth/v1/token?grant_type=password','POST',{email:account.email,password:account.password});
   if(login.ok && typeof login.data?.access_token==='string')token=login.data.access_token;
   if(!token || login.data?.user?.id!==account.id || login.data?.user?.email!==account.email)throw new Error('AUTH_VERIFICATION_FAILED');
   result.authVerified=true;
   const rows=await request('/rest/v1/profiles?select=id,email,role,office_id,staff_capability,department_id,is_demo','GET',null,token);
   const canonical=await request('/rest/v1/rpc/account_view','POST',{},token);
   const bootstrap=await request('/functions/v1/bootstrap-user','POST',{},token);
   if(!rows.ok || !canonical.ok || !bootstrap.ok)throw new Error('ACCOUNT_ENDPOINT_UNAVAILABLE');
   Object.assign(result,verifyAccount(account,rows.data,canonical.data,bootstrap.data));
  }catch(error){result.error=['AUTH_VERIFICATION_FAILED','ACCOUNT_ENDPOINT_UNAVAILABLE','ACCOUNT_SCOPE_MISMATCH','ACCOUNT_READINESS_MISMATCH'].includes(error.message)?error.message:'VERIFICATION_UNAVAILABLE';}
  finally{if(token)try{const closed=await request('/auth/v1/logout?scope=local','POST',{},token);result.verificationSessionClosed=closed.ok || closed.status===401;}catch{}}
  results.push(result);console.log(JSON.stringify(result));
 }
 const denied=await request('/functions/v1/bootstrap-user','POST',{});
 const allVerified=results.every(r=>r.authVerified && r.scopeVerified && r.verificationSessionClosed && !r.error) && denied.status===401;
 const evidence={project,accounts:results,anonymousBootstrapDenied:denied.status===401,allVerified,termsActivated:false,acceptancesCreated:false,
  privateCredentialsModified:false,passwordsChanged:false,mailSent:false,fullNativeAppLoginVerified:false};
 await writeFile(fileURLToPath(new URL('../output/validation/account-rollout/http-readiness.json',import.meta.url)),JSON.stringify(evidence,null,2)+'\n');
 if(!allVerified)process.exitCode=1;
}
if(process.argv[1] && import.meta.url===pathToFileURL(process.argv[1]).href)main(process.argv[2]).catch(()=>{console.error('ACCOUNT_BACKEND_VERIFICATION_UNAVAILABLE: credentials and raw server responses withheld.');process.exitCode=1;});
