import {readFile,writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';

const project='https://uuvexkewiuxdbuelwogy.supabase.co';
const privatePath=process.argv[2];
const expectedEmails=new Set(['registrar@demo.goserve.test','accounting@demo.goserve.test','teller@demo.goserve.test',
  'guidance@demo.goserve.test','secretary.ccs@demo.goserve.test','student.ccs@demo.goserve.test']);
const configPath=fileURLToPath(new URL('../android/app/build/generated/source/buildConfig/debug/ph/edu/tip/goserve/BuildConfig.java',import.meta.url));

async function main() {
  if(!privatePath)throw new Error('PRIVATE_CREDENTIAL_PATH_REQUIRED');
  const record=JSON.parse(await readFile(privatePath,'utf8'));
  if(record.project_ref!=='uuvexkewiuxdbuelwogy' || record.accounts?.length!==6 ||
      new Set(record.accounts.map(a=>a.email)).size!==6 || record.accounts.some(a=>!expectedEmails.has(a.email)) ||
      typeof record.requested_password!=='string' || !record.requested_password)throw new Error('DEMO_SCOPE_MISMATCH');
  const config=await readFile(configPath,'utf8');
  const configured=name=>{
    const value=config.match(new RegExp(`String ${name} = ("[^"\\r\\n]*");`));
    if(!value)throw new Error('APP_CONFIG_MISSING');
    return JSON.parse(value[1]);
  };
  if(configured('SUPABASE_URL')!==project)throw new Error('APP_PROJECT_MISMATCH');
  const key=configured('SUPABASE_PUBLISHABLE_KEY');
  const save=()=>writeFile(privatePath,JSON.stringify(record,null,2)+'\n','utf8');
  async function request(path,method,body,token) {
    const response=await fetch(project+path,{
      method,headers:{apikey:key,Accept:'application/json',...(body?{'Content-Type':'application/json'}:{}),...(token?{Authorization:'Bearer '+token}:{})},
      ...(body?{body:JSON.stringify(body)}:{}),redirect:'error',signal:AbortSignal.timeout(20000),
    });
    const data=await response.json().catch(()=>null);
    return {ok:response.ok,status:response.status,data};
  }
  async function close(token) {
    if(!token)return true;
    try { const result=await request('/auth/v1/logout?scope=local','POST',{},token); return result.ok || result.status===401; }
    catch { return false; }
  }
  const results=[];
  for(const account of record.accounts) {
    let token;
    const result={name:account.name,email:account.email,passwordVerified:false,profileVerified:false};
    try {
      if(account.password!==record.requested_password) {
        const current=await request('/auth/v1/token?grant_type=password','POST',{email:account.email,password:account.password});
        if(!current.ok || !current.data?.access_token || current.data.user?.email!==account.email)throw new Error('CURRENT_LOGIN_FAILED');
        token=current.data.access_token;
        const changed=await request('/auth/v1/user','PUT',{current_password:account.password,password:record.requested_password},token);
        if(!changed.ok || changed.data?.id!==current.data.user.id)throw new Error('PASSWORD_UPDATE_NOT_CONFIRMED');
        account.password=record.requested_password; await save();
        result.previousSessionClosed=await close(token); token=null;
      }
      const login=await request('/auth/v1/token?grant_type=password','POST',{email:account.email,password:record.requested_password});
      if(!login.ok || !login.data?.access_token || login.data.user?.email!==account.email || !login.data.user?.id)throw new Error('REQUESTED_PASSWORD_LOGIN_FAILED');
      token=login.data.access_token; result.passwordVerified=true;
      account.password=record.requested_password; account.id=login.data.user.id; account.password_verified=true; await save();
      const profile=await request('/rest/v1/profiles?select=id,email,role,office_id,staff_capability,is_demo,department_id','GET',null,token);
      if(!profile.ok || !Array.isArray(profile.data) || profile.data.length!==1)throw new Error('OWN_PROFILE_READ_FAILED');
      const row=profile.data[0];
      if(row.id!==account.id || row.email!==account.email || row.role!==account.role || row.office_id!==account.office_id ||
          row.staff_capability!==account.staff_capability || row.department_id!==account.department_id || row.is_demo!==true)throw new Error('TRUSTED_PROFILE_MISMATCH');
      result.profileVerified=true; result.role=row.role; result.officeId=row.office_id; result.capability=row.staff_capability; result.departmentId=row.department_id;
      account.provisioned=true; account.profile_verified=true; await save();
    } catch(error) {
      const allowed=new Set(['CURRENT_LOGIN_FAILED','PASSWORD_UPDATE_NOT_CONFIRMED','REQUESTED_PASSWORD_LOGIN_FAILED','OWN_PROFILE_READ_FAILED','TRUSTED_PROFILE_MISMATCH']);
      result.error=allowed.has(error.message)?error.message:'VERIFICATION_UNAVAILABLE';
    } finally { result.verificationSessionClosed=await close(token); }
    results.push(result);
    console.log(JSON.stringify(result));
  }
  const verified=results.every(r=>r.passwordVerified && r.profileVerified && r.verificationSessionClosed && !r.error);
  record.status=verified?'Six demo Auth logins and trusted profiles verified; revised app backend rollout pending':'Provisioning verification incomplete; inspect non-secret validation results';
  record.department_contact='CCS Secretary only'; await save();
  const evidence={project,accounts:results,allVerified:verified,facultyAccountCreated:false,departmentContact:'CCS Secretary only',
    coreAccountBackendDeployed:false,fullNativeAppLoginVerified:false};
  await writeFile(fileURLToPath(new URL('../output/validation/demo-accounts/provisioning.json',import.meta.url)),JSON.stringify(evidence,null,2)+'\n');
  if(!verified)process.exitCode=1;
}
main().catch(()=>{ console.error('DEMO_VERIFICATION_UNAVAILABLE: no credentials or raw server response printed.'); process.exitCode=1; });
