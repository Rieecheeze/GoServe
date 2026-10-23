import { DomainError, exact, rpc } from './v23.ts';
import { createPayment, getTransaction, type FetchTransport } from './egovpay.ts';

function uuid(value: unknown): value is string {
  return typeof value==='string' && /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(value);
}

function validate(body: any, starting=false) {
  exact(body,starting?['payment_id','idempotency_key']:['payment_id']);
  if(!uuid(body.payment_id)||(starting&&!uuid(body.idempotency_key)))throw new DomainError('VALIDATION_FAILED');
}

async function configuration(admin: any) {
  const config=await rpc(admin,'get_egovpay_server_configuration');
  if(config?.integration_enabled!==true||config?.demo_only!==true
    ||config.base_url!=='https://platforms-api.e.gov.ph/egovpay'||!uuid(config.settlement_template_uuid)
    ||typeof config.token!=='string'||!config.token||config.token==='test_'||/[\s\u0000-\u001f\u007f]/.test(config.token)) {
    throw new DomainError('PROVIDER_UNAVAILABLE');
  }
  return {config:{integrationEnabled:true,demoOnly:true,baseUrl:config.base_url,apiToken:config.token},template:config.settlement_template_uuid};
}

async function operation(admin: any, command: string, payment: string|null, actor: string|null,
  key: string|null=null, details: any=null, token: string|null=null) {
  return rpc(admin,'egovpay_server_operation',{p_command:command,p_payment_id:payment,p_actor:actor,
    p_key:key,p_token:token,p_details:details});
}

export async function startEgovPay(admin: any, client: any, actor: string, body: any,
  serverUrl: string, transport: FetchTransport=fetch): Promise<{checkout_url:string}> {
  validate(body,true);
  await rpc(client,'egovpay_authorize',{p_payment_id:body.payment_id});
  const settings=await configuration(admin);
  let base: URL;
  try {base=new URL(serverUrl);} catch {throw new DomainError('CONFIGURATION_REQUIRED');}
  if(base.protocol!=='https:'||base.username||base.password||base.search||base.hash||base.pathname!=='/')throw new DomainError('CONFIGURATION_REQUIRED');
  const claim=await operation(admin,'claim',body.payment_id,actor,body.idempotency_key);
  if(claim.state==='ready'&&typeof claim.checkout_url==='string')return {checkout_url:claim.checkout_url};
  if(claim.may_create!==true)throw new DomainError('CHECKOUT_UNCONFIRMED');
  try {
    const callback=new URL('/functions/v1/egovpay-callback',base);
    callback.searchParams.set('token',claim.callback_token);
    const receipt=await createPayment(settings.config,{amount:claim.amount,txnid:claim.txnid,
      items:[{name:'GoServe supervised test payment',amount:claim.amount}],settlementTemplateUuid:settings.template,
      callbackUrl:callback.href,redirectUrl:new URL('/functions/v1/egovpay-return',base).href},transport);
    const attached=await operation(admin,'attach',body.payment_id,actor,body.idempotency_key,{uuid:receipt.uuid,url:receipt.url});
    if(attached.request_terminal||attached.status==='cancelled')throw new DomainError('INVALID_STATE');
    return {checkout_url:attached.checkout_url};
  } catch {
    // A lost POST or database response must never produce another provider transaction.
    try {await operation(admin,'ambiguous',body.payment_id,actor,body.idempotency_key);}catch{}
    throw new DomainError('CHECKOUT_UNCONFIRMED');
  }
}

async function reconcile(admin: any, bound: any, actor: string|null, transport: FetchTransport) {
  if(bound.status==='paid')return {status:'paid'};
  if(!uuid(bound.provider_uuid))return {status:['failed','cancelled'].includes(bound.status)?bound.status:'unknown'};
  try {
    const settings=await configuration(admin);
    const details=await getTransaction(settings.config,bound.provider_uuid,transport);
    if(details.txnid!==bound.txnid||details.currency!==bound.currency
      ||!/^\d+(?:\.\d+)?$/.test(bound.amount)||decimal(details.amount)!==decimal(bound.amount))return {status:'unknown'};
    const observed=await operation(admin,'observe',bound.payment_id,actor,null,{uuid:details.uuid.toLowerCase(),
      txnid:details.txnid,environment_type:details.environment_type,amount:details.amount,currency:details.currency,
      payment_status:details.payment_status,...(details.paid_at?{paid_at:details.paid_at}:{})},actor===null?bound.callback_token:null);
    return {status:observed.status};
  } catch {return {status:'unknown'};}
}

function decimal(value: string) {
  const [whole,part='']=value.split('.');const tail=part.replace(/0+$/,'');
  return whole.replace(/^0+(?=\d)/,'')+(tail?'.'+tail:'');
}

async function boundedBody(request: Request,limit: number) {
  if(Number(request.headers.get('Content-Length'))>limit)throw new DomainError('VALIDATION_FAILED');
  const reader=request.body?.getReader();if(!reader)return '';
  let timer: ReturnType<typeof setTimeout>|undefined;
  const timeout=new Promise<never>((_resolve,reject)=>{timer=setTimeout(()=>reject(new DomainError('VALIDATION_FAILED')),5000);});
  const work=(async()=>{
    const chunks: Uint8Array[]=[];let size=0;
    for(;;){const {value,done}=await reader.read();if(done)break;size+=value.byteLength;if(size>limit)throw new DomainError('VALIDATION_FAILED');chunks.push(value);}
    const bytes=new Uint8Array(size);let offset=0;for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.byteLength;}
    return new TextDecoder().decode(bytes);
  })();
  try{return await Promise.race([work,timeout]);}
  finally{clearTimeout(timer);void reader.cancel().catch(()=>{});try{reader.releaseLock();}catch{}}
}

export async function readEgovPayBody(request: Request) {
  try{return JSON.parse(await boundedBody(request,2048));}
  catch{throw new DomainError('VALIDATION_FAILED');}
}

export async function refreshEgovPay(admin: any,client: any,actor: string,body: any,
  transport: FetchTransport=fetch): Promise<{status:string}> {
  validate(body);
  await rpc(client,'egovpay_authorize',{p_payment_id:body.payment_id});
  const bound=await operation(admin,'lookup',body.payment_id,actor);
  return reconcile(admin,bound,actor,transport);
}

export async function callbackEgovPay(admin: any,request: Request,transport: FetchTransport=fetch) {
  try {
    if(request.url.length>512)return {received:true};
    const token=new URL(request.url).searchParams.get('token');
    if(!uuid(token)||request.method!=='POST')return {received:true};
    await boundedBody(request,16384);
    const bound=await operation(admin,'callback',null,null,null,null,token);
    await reconcile(admin,bound,null,transport);
  }catch{}
  return {received:true};
}
