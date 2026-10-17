// Read-only. Root injects AUTH_CASES, AUTH_DEFS, PROOFS and SETTINGS.
await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('14:2'));
const issues=[],counts={},stats={caseCount:0,authRootsChecked:0,authProofsChecked:0,generalProofsSkipped:0,settingsChecked:0,ordinaryTextCount:0,passwordTargetsChecked:0,forgotLinksChecked:0,termsReadersChecked:0};
let issueCount=0;
function issue(kind,meta,detail={}){issueCount++;counts[kind]=(counts[kind]||0)+1;if(issues.length<40)issues.push({kind,id:meta.id,key:meta.key,theme:meta.theme,...detail});}
const caseMap=new Map(AUTH_CASES.map(c=>[c.key+'|'+c.theme,c])),byId=new Map(AUTH_CASES.map(c=>[c.id,c])),defs=new Map(AUTH_DEFS.map(d=>[d.key+'|'+d.theme,d])),keys=[...new Set(AUTH_CASES.map(c=>c.key))];
stats.caseCount=keys.length;
if(keys.length!==39||AUTH_CASES.length!==78||caseMap.size!==78||byId.size!==78||keys.some(key=>!['Light','Dark'].every(theme=>caseMap.has(key+'|'+theme))))issue('casePairInventory',{id:'14:2'},{expectedPairs:39,actualPairs:keys.length,actualRoots:AUTH_CASES.length});
const nodeCache=new Map();async function node(id){if(!id)return null;if(!nodeCache.has(id))nodeCache.set(id,await figma.getNodeByIdAsync(id));return nodeCache.get(id);}
function visible(n){while(n&&n.type!=='PAGE'){if('visible'in n&&!n.visible)return false;n=n.parent;}return true;}
function textExcluded(t){let n=t;while(n&&n.type!=='PAGE'){if(/^(Android status inset|Android gesture inset|Google monogram)$/.test(n.name)||/^(Icon\s*\/|Show password icon)/.test(n.name))return true;n=n.parent;}return t.getStyledTextSegments(['fontName']).some(s=>/Material Symbols|Material Icons/.test(s.fontName.family));}
function texts(root){return root.findAllWithCriteria({types:['TEXT']}).filter(t=>visible(t)&&!textExcluded(t));}
function frames(root,name){return root.findAllWithCriteria({types:['FRAME']}).filter(n=>n.name===name&&visible(n));}
function actions(n){return!n||!('reactions'in n)?[]:n.reactions.flatMap(r=>r.actions|| (r.action?[r.action]:[])).filter(Boolean);}
function action(root,label){for(const t of texts(root).filter(t=>t.characters===label)){let n=t;while(n&&n.id!==root.id){if('reactions'in n&&n.reactions.length)return n;n=n.parent;}const candidate=t.parent;if(candidate&&candidate.id!==root.id)return candidate;}return null;}
function target(n,id){const routes=actions(n).filter(a=>a.type==='NODE');return routes.length>0&&routes.every(a=>a.destinationId===id);}
function checkbox(root,label){return frames(root,'Auth agreement / '+label)[0];}
function checked(n){return!!n&&n.findAll(x=>x.name==='Checked agreement'&&visible(x)).length>0;}
function field(root,label){return frames(root,'Auth field / '+label)[0];}
async function inspect(meta,isProof=false){
  const root=await node(meta.id);if(!root||root.type!=='FRAME'){issue('missingAuthRoot',meta);return;}if(!visible(root)){issue('hiddenAuthRoot',meta);return;}
  if(isProof)stats.authProofsChecked++;else stats.authRootsChecked++;
  const viewport=frames(root,'Auth scroll viewport')[0],body=frames(root,'Auth centered content')[0],card=frames(root,'Auth floating card')[0];
  if(!viewport||!body||!card){issue('authShellMissing',meta,{viewport:!!viewport,body:!!body,card:!!card});return;}
  if(viewport.height<=0||body.height<=0||viewport.overflowDirection!=='VERTICAL')issue('authScrollViewport',meta,{viewportHeight:viewport.height,bodyHeight:body.height,overflow:viewport.overflowDirection});
  if(Math.abs(card.x+card.width/2-body.width/2)>1)issue('cardNotCenteredHorizontally',meta,{cardX:card.x,cardWidth:card.width,bodyWidth:body.width});
  const pt=body.paddingTop,pb=body.paddingBottom,fits=card.height+pt+pb<=viewport.height+1;
  if(fits){const expected=(viewport.height-card.height+pt-pb)/2;if(Math.abs(body.y+card.y-expected)>1)issue('shortCardNotCentered',meta,{cardY:body.y+card.y,expected});}
  else if(Math.abs(card.y-pt)>1)issue('longCardNotAtScrollTop',meta,{cardY:card.y,expected:pt});
  const ordinary=texts(root);stats.ordinaryTextCount+=ordinary.length;
  for(const t of ordinary){if(/\(required\)/i.test(t.characters))issue('requiredSuffixVisible',meta,{node:t.id});if(/Welcome back/i.test(t.characters))issue('obsoleteWelcomeCopy',meta,{node:t.id});const families=[...new Set(t.getStyledTextSegments(['fontName']).map(s=>s.fontName.family))].filter(f=>!['Montserrat','Karla'].includes(f));if(families.length)issue('unapprovedProductFont',meta,{node:t.id,families});}
  for(const n of root.findAll(n=>n.name.startsWith('Auth visibility / ')&&visible(n))){stats.passwordTargetsChecked++;if(n.width<47.5||n.height<47.5)issue('passwordVisibilityTargetSmall',meta,{node:n.id,width:n.width,height:n.height});}
  const key=meta.key,def=defs.get(key+'|'+meta.theme);
  if(key==='terms'||key==='terms-view'){
    if(!ordinary.some(t=>/Draft terms\s*[·—-]\s*UI preview/i.test(t.characters)))issue('visibleDraftTermsMissing',meta);
    if(key==='terms-view'){stats.termsReadersChecked++;const back=action(root,'Back');if(!back||!actions(back).some(a=>a.type==='BACK'))issue('termsReaderBackContext',meta);const accepts=root.findAll(n=>visible(n)&&'reactions'in n&&n.reactions.length&&/^Auth action \/ (?:I agree|Agree|Accept|Continue|Create account|Sign up)/i.test(n.name));if(accepts.length||root.findAll(n=>n.name.startsWith('Auth agreement / ')&&visible(n)).length)issue('termsReaderRequestsConsent',meta);}
  }
  if(key&&key.startsWith('sign-in')&&field(root,'Password')){
    stats.forgotLinksChecked++;const password=field(root,'Password'),forgot=card.children.find(n=>n.name==='Auth action / Forgot password?'&&visible(n)),expected=caseMap.get('reset-email|'+meta.theme)?.id;
    if(!forgot||card.children.indexOf(forgot)!==card.children.indexOf(password)+1)issue('forgotPasswordPlacement',meta);
    if(!forgot||!expected||!target(forgot,expected))issue('forgotPasswordDestination',meta);
  }
  if(key==='sign-up'){
    if(checked(checkbox(root,'I agree to the Terms and Conditions and Privacy Notice.')))issue('initialAgreementChecked',meta);
    for(const label of ['Continue with Google','Sign up with email']){const n=action(root,label);if(!n||actions(n).length)issue('unagreedMethodActionEnabled',meta,{label});}
    if(root.findAll(n=>n.name.startsWith('Auth field / ')&&visible(n)).length)issue('registrationCollectsBeforeAgreement',meta);
  }
  for(const item of def?.items||[])if(item.type==='button'&&(item.disabled||item.style==='disabled')){const n=action(root,item.label);if(n&&actions(n).length)issue('disabledActionHasReaction',meta,{label:item.label});}
  if(key&&(/busy/.test(key)||['launch','reset-sending','reset-saving','account-created','reset-complete'].includes(key))){
    for(const n of [root,...root.findAll(n=>visible(n)&&'reactions'in n&&n.reactions.length)]){
      if(!('reactions'in n))continue;if(n.reactions.some(r=>/^AFTER_(?:TIME|TIMEOUT|DELAY)$/.test(r.trigger?.type||'')))issue('timedAuthTransition',meta,{node:n.id});
      for(const a of actions(n).filter(a=>a.type==='NODE')){const destination=await node(a.destinationId);if(destination&&/(?:student|staff)\/home$|teller\/cash$/.test(destination.name))issue('unguardedDashboardTransition',meta,{node:n.id,destination:a.destinationId});}
    }
  }
  if(key&&/^(?:email|google)-profile(?:-errors|-no-middle)?$/.test(key)){
    const expected=['First name','Middle name','Last name','Student ID','Course / program','Year level','Mobile number','Gender','Email address'],missing=expected.filter(label=>!field(root,label));if(missing.length)issue('profileFieldsMissing',meta,{missing});
    if(key.startsWith('google')&&field(root,'Password'))issue('googleProfileRequestsPassword',meta);
    const middle=def?.items.find(i=>i.type==='field'&&i.label==='Middle name'),noMiddle=key.endsWith('-no-middle');if(noMiddle&&(!checked(checkbox(root,'No middle name'))||middle?.required!==false||middle?.disabled!==true))issue('noMiddleNameException',meta);
    const email=def?.items.find(i=>i.type==='field'&&i.label==='Email address');if(email&&email.disabled!==true)issue('profileEmailNotReadOnlyInDefinition',meta);
  }
}
for(const meta of AUTH_CASES)await inspect(meta);
for(const proof of PROOFS){const p=typeof proof==='string'?{id:proof}:proof,source=byId.get(p.source),key=p.key||source?.key,theme=p.theme||source?.theme;if(!key||!keys.includes(key)){stats.generalProofsSkipped++;continue;}await inspect({id:p.id||p.rootId,key,theme},true);}
if(stats.authProofsChecked!==12)issue('authProofInventory',{id:'14:2'},{expected:12,actual:stats.authProofsChecked});
if(SETTINGS.length!==6)issue('settingsInventory',{id:'14:2'},{expected:6,actual:SETTINGS.length});
for(const meta of SETTINGS){const root=await node(meta.id);if(!root||root.type!=='FRAME'){issue('settingsRootMissing',meta);continue;}stats.settingsChecked++;const n=action(root,'Terms and Conditions'),expected=caseMap.get('terms-view|'+meta.theme)?.id;if(!n||!expected||!target(n,expected))issue('settingsTermsDestination',meta);}
const result={stats,issueCount,counts,issues,findingsTruncated:issueCount>issues.length};
function byteLength(value){let size=0;for(const ch of JSON.stringify(value)){const c=ch.codePointAt(0);size+=c<128?1:c<2048?2:c<65536?3:4;}return size;}
while(issues.length&&byteLength(result)>14500){issues.pop();result.findingsTruncated=true;}
return result;
