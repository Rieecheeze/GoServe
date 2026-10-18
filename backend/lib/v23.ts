export class DomainError extends Error {
  constructor(public code: string) { super(code); }
}

const codes = new Set(['AUTH_REQUIRED','AUTH_INVALID','FORBIDDEN','NOT_FOUND','VALIDATION_FAILED',
  'PROFILE_INCOMPLETE','TERMS_REQUIRED','CONFIGURATION_REQUIRED','STALE_VERSION','QUOTE_STALE',
  'IDEMPOTENCY_CONFLICT','INVALID_STATE','PAYMENT_REQUIRED','ALREADY_PAID','CODE_INVALID','CODE_EXPIRED',
  'CODE_CONSUMED','CAPACITY_FULL','POLICY_BLOCKED','UPLOAD_INVALID','DOWNLOAD_UNCONFIRMED',
  'CONSENT_REQUIRED','PROVIDER_UNAVAILABLE','CLEANUP_REQUIRED','TEMPORARILY_UNAVAILABLE','IMMUTABLE_RECORD']);

export function exact(value: any, allowed: string[], required: string[] = allowed): void {
  if (!value || typeof value !== 'object' || Array.isArray(value)
    || Object.keys(value).some(k => !allowed.includes(k)) || required.some(k => !(k in value))) {
    throw new DomainError('VALIDATION_FAILED');
  }
}

export function validateProfile(profile: any): void {
  exact(profile, ['first_name','middle_name','no_middle_name','last_name','student_id',
    'course_program','year_level','mobile_number','gender']);
  for (const key of ['first_name','last_name','student_id','course_program','year_level','mobile_number','gender']) {
    if (typeof profile[key] !== 'string' || !profile[key].trim()) throw new DomainError('VALIDATION_FAILED');
  }
  if (typeof profile.no_middle_name !== 'boolean'
    || (profile.no_middle_name && profile.middle_name !== null && profile.middle_name !== '')
    || (!profile.no_middle_name && (typeof profile.middle_name !== 'string' || !profile.middle_name.trim()))) {
    throw new DomainError('VALIDATION_FAILED');
  }
}

export function validateRegistration(body: any): void {
  exact(body,['email','password','profile','terms_version','idempotency_key']);
  if (typeof body.email !== 'string' || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(body.email)
    || typeof body.password !== 'string' || body.password.length < 6
    || !/[A-Za-z]/.test(body.password) || !/[0-9]/.test(body.password)
    || !/[^A-Za-z0-9\s]/.test(body.password)
    || typeof body.terms_version !== 'string' || !body.terms_version.trim()
    || typeof body.idempotency_key !== 'string'
    || !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(body.idempotency_key)) {
    throw new DomainError('VALIDATION_FAILED');
  }
  validateProfile(body.profile);
}

export async function rpc(client: any, name: string, args: any = {}): Promise<any> {
  const {data,error} = await client.rpc(name,args);
  if (error) throw new DomainError(codes.has(error.message) ? error.message : 'TEMPORARILY_UNAVAILABLE');
  return data;
}

export async function registerStudent(admin: any, body: any): Promise<any> {
  validateRegistration(body);
  const args = {p_email:body.email.toLowerCase(),p_key:body.idempotency_key,p_profile:body.profile,p_terms_version:body.terms_version};
  const attempt = await rpc(admin,'registration_operation',{p_command:'prepare',...args});
  if (attempt.state === 'committed') return {account_created:true,email:args.p_email,next_action:'sign_in'};
  if (attempt.state !== 'creating') throw new DomainError('TEMPORARILY_UNAVAILABLE');
  if (attempt.may_create) {
    // The correlation is server-owned. A lost Auth response is reconciled without creating another identity.
    try {
      const result = await admin.auth.admin.createUser({email:args.p_email,password:body.password,email_confirm:true,
        app_metadata:{goserve_registration_attempt:attempt.attempt_id}});
      if (result.error) throw new DomainError('TEMPORARILY_UNAVAILABLE');
    } catch {
      const resolved = await rpc(admin,'registration_operation',{...args,p_command:'resolve',p_attempt_id:attempt.attempt_id});
      if (!resolved.auth_id) throw new DomainError('TEMPORARILY_UNAVAILABLE');
    }
  } else if (!attempt.auth_id) {
    throw new DomainError('TEMPORARILY_UNAVAILABLE');
  }
  try {
    return await rpc(admin,'registration_operation',{...args,p_command:'commit',p_attempt_id:attempt.attempt_id});
  } catch {
    // Reconcile first: a committed DB response may have been lost. Compensation locks out later commits.
    const resolved = await rpc(admin,'registration_operation',{...args,p_command:'resolve',p_attempt_id:attempt.attempt_id});
    if (resolved.state === 'committed') return {account_created:true,email:args.p_email,next_action:'sign_in'};
    if (resolved.auth_id && resolved.state === 'creating') {
      const cleanup = await rpc(admin,'registration_operation',{...args,p_command:'compensate',p_attempt_id:attempt.attempt_id});
      if (cleanup.state === 'compensating' && cleanup.auth_id) {
        const deleted = await admin.auth.admin.deleteUser(cleanup.auth_id);
        if (!deleted.error) await rpc(admin,'registration_operation',{...args,p_command:'deleted',p_attempt_id:attempt.attempt_id});
      }
    }
    throw new DomainError('TEMPORARILY_UNAVAILABLE');
  }
}

export async function authenticated(request: Request, admin: any, userClient: (token: string) => any): Promise<any> {
  const token = request.headers.get('Authorization')?.match(/^Bearer ([^\s]+)$/i)?.[1];
  if (!token) throw new DomainError('AUTH_REQUIRED');
  const {data,error} = await admin.auth.getUser(token);
  if (error || !data.user || data.user.is_anonymous) throw new DomainError('AUTH_INVALID');
  const client = userClient(token);
  const account = await rpc(client,'account_view');
  if (account.id !== data.user.id) throw new DomainError('AUTH_INVALID');
  return {client,user:data.user,account};
}

export function errorResponse(error: unknown): Response {
  const code = error instanceof DomainError ? error.code : 'TEMPORARILY_UNAVAILABLE';
  const status = code.startsWith('AUTH_') ? 401 : code==='FORBIDDEN' ? 403 : code==='NOT_FOUND' ? 404
    : ['PROVIDER_UNAVAILABLE','CONFIGURATION_REQUIRED','TEMPORARILY_UNAVAILABLE','CLEANUP_REQUIRED'].includes(code) ? 503
    : ['VALIDATION_FAILED','UPLOAD_INVALID'].includes(code) ? 422 : 409;
  return json({ok:false,error:{code}},status);
}

export function json(body: any,status=200): Response {
  return new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json','Cache-Control':'no-store'}});
}

export async function providerBoundary(client: any, kind: string): Promise<any> {
  if (kind==='seryo') {
    const state = await rpc(client,'seryo_state');
    if (state.cleanup!=='clear') throw new DomainError('CLEANUP_REQUIRED');
    if (state.consent!=='allow') throw new DomainError('CONSENT_REQUIRED');
  }
  throw new DomainError('PROVIDER_UNAVAILABLE');
}

export async function cleanOne(admin: any, leaseSeconds: number): Promise<any> {
  const job = await rpc(admin,'cleanup_claim',{p_lease_seconds:leaseSeconds});
  if (!job) return {processed:false};
  let absent = false;
  try {
    const bucket = admin.storage.from('goserve-private');
    const deleted = await bucket.remove([job.object_path]);
    if (!deleted.error) {
      const parts = job.object_path.split('/');
      const name = parts.pop();
      const listing = await bucket.list(parts.join('/'),{search:name,limit:100});
      absent = !listing.error && Array.isArray(listing.data) && !listing.data.some((file: any)=>file.name===name);
    }
  } catch { absent = false; }
  const outcome = await rpc(admin,'cleanup_finish',{p_job_id:job.id,p_lease_id:job.lease_id,p_absence_verified:absent});
  return {processed:true,state:outcome.state};
}
