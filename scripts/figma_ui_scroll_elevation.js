// Inject PHASE='inspect'|'apply', ROOT_IDS, and optional EXTRA_ELEVATION_TARGETS.
// This pass changes only recognized viewport scrolling/clipping and surface effects.
await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('14:2'));
const page=figma.currentPage;
const phase=typeof PHASE==='string'?PHASE:'inspect';
if(!['inspect','apply'].includes(phase))return{error:'PHASE must be inspect or apply',createdNodeIds:[],mutatedNodeIds:[],deletedNodeIds:[]};
const apply=phase==='apply',mutated=new Set(),proposed=new Set(),issues=[],viewportEvidence=[],elevationEvidence=[],rootEvidence=[],missingRootIds=[],skippedRootIds=[],errors=[];
const requested=typeof ROOT_IDS!=='undefined'&&Array.isArray(ROOT_IDS)?[...new Set(ROOT_IDS.map(v=>typeof v==='string'?v:v&&v.id).filter(Boolean))]:null;
const extras=typeof EXTRA_ELEVATION_TARGETS!=='undefined'&&Array.isArray(EXTRA_ELEVATION_TARGETS)?EXTRA_ELEVATION_TARGETS:[];
const all=n=>[n,...('findAll'in n?n.findAll(()=>true):[])];
function pageOf(n){while(n&&n.type!=='PAGE')n=n.parent;return n;}
function shown(n){for(let p=n;p&&p.type!=='PAGE';p=p.parent)if('visible'in p&&!p.visible)return false;return true;}
function dims(n){return{x:n.x,y:n.y,w:n.width,h:n.height};}
function same(a,b){return Math.abs(a.x-b.x)<.02&&Math.abs(a.y-b.y)<.02&&Math.abs(a.w-b.w)<.02&&Math.abs(a.h-b.h)<.02;}
function absolute(n){const a=n.absoluteBoundingBox;if(a)return{x:a.x,y:a.y,w:a.width,h:a.height};const t=n.absoluteTransform;return{x:t[0][2],y:t[1][2],w:n.width,h:n.height};}
function issue(root,node,reason){issues.push({root:root.id,id:node.id,reason});}
function isPhone(n){return n.type==='FRAME'&&n.width>=280&&n.width<=460&&n.height>=500&&n.height<=1100&&n.children.some(c=>c.name==='Android status inset')&&n.children.some(c=>c.name==='Android gesture inset');}
function themeOf(root){if(/(?:dark\/|\bDark\b|Theme=Dark)/i.test(root.name))return'Dark';const p=Array.isArray(root.fills)&&root.fills.find(p=>p.type==='SOLID'&&p.visible!==false&&p.opacity!==0);if(p&&p.type==='SOLID'&&(.2126*p.color.r+.7152*p.color.g+.0722*p.color.b)<.2)return'Dark';return'Light';}
const candidates=requested?await Promise.all(requested.map(id=>figma.getNodeByIdAsync(id))):page.findAllWithCriteria({types:['FRAME']}).filter(isPhone);
const roots=[];
for(let i=0;i<candidates.length;i++){
 const n=candidates[i],id=requested?requested[i]:n&&n.id;
 if(!n){missingRootIds.push(id);continue;}
 if(n.type!=='FRAME'||pageOf(n)?.id!==page.id||!shown(n)){skippedRootIds.push(id);continue;}
 if(!isPhone(n)&&!/^GoServe 2\.5 • (?:dark\/)?shared\/seryo-tools-overlay$/.test(n.name)){skippedRootIds.push(id);continue;}
 roots.push(n);
}
const rootSet=new Set(roots.map(n=>n.id));
const snapshots=new Map();
function remember(n){if(!snapshots.has(n.id))snapshots.set(n.id,{node:n,before:dims(n)});}
const viewportKinds={
 'Auth scroll viewport':{kind:'auth',body:/^Auth centered content$/},
 'Auth / Scroll viewport':{kind:'auth',body:/^Auth(?: \/)? centered content$/},
 'Scrollable Android content':{kind:'android',body:/^(?:Hug content(?: \/.*)?|GoServe legacy content stack|New account \/ Requests content|Request examples \/ native wrapping cards)$/},
 'Seryo / Scrollable chat viewport':{kind:'chat',body:/^Seryo \/ (?:Inline state content|Scrollable conversation content|Centered welcome content)$/},
 'Seryo / Scrollable welcome viewport':{kind:'chat',body:/^Seryo \/ (?:Centered welcome content|Welcome content)$/},
 'GoServe tools / Scrollable menu viewport':{kind:'menu',body:/^GoServe tools \/ Menu options$/}
};
function surfaceKind(n){
 const name=n.name,extra=extras.find(e=>e&&(e.id===n.id||e.name===name));
 if(extra&&['preview','composer','menu','auth','nav','card','dialog'].includes(extra.kind))return extra.kind;
 if(name==='Auth floating card')return'auth';
 if(/^Seryo \/ (?:Inline (?:image|document) preview|System picker preview)$/.test(name))return'preview';
 if(/^Seryo \/ Composer (?:• setup pending|disabled in planned state)$/.test(name))return'composer';
 if(/^GoServe \/ Seryo tools menu \/ (?:Light|Dark)$/.test(name)||name==='Seryo / Inline tools menu'||/^GoServe tools \/ (?:Conversation|Welcome) menu$/.test(name))return'menu';
 if(/^GoServe \/ (?:Capsule|Role|Student|Staff|Cashiering|Teller) navigation(?: \/|$)/.test(name))return'nav';
 if(/^(?:Dialog|Modal|Sheet) \/ /.test(name))return'dialog';
 if(name==='Demo active request'||/^Card \/ (?:Request|Payment|Receipt|Profile|Office|Service)(?: |$)/.test(name))return'card';
 // The directory builder names each raised, multi-line office card after its office.
 if(/^(?:Registrar|Student Accounting|Cashiering|Guidance|CCS)$/.test(name)&&n.height>=72&&'cornerRadius'in n&&typeof n.cornerRadius==='number'&&n.cornerRadius>=16&&n.parent&&/^Hug content(?: \/.*)?$/.test(n.parent.name))return'card';
 return null;
}
function shadow(kind,theme){
 const dark=theme==='Dark',spec={preview:[2,8,.06,.18],composer:[4,16,.10,.24],menu:[8,24,.14,.35],auth:[8,24,.14,.35],nav:[4,12,.10,.28],card:[2,8,.06,.18],dialog:[8,24,.14,.35]}[kind];
 return{type:'DROP_SHADOW',color:{r:0,g:0,b:0,a:dark?spec[3]:spec[2]},offset:{x:0,y:spec[0]},radius:spec[1],spread:0,visible:true,blendMode:'NORMAL'};
}
function sameEffect(a,b){return a&&a.type===b.type&&a.visible!==false&&a.blendMode===b.blendMode&&Math.abs(a.color.r-b.color.r)<.0001&&Math.abs(a.color.g-b.color.g)<.0001&&Math.abs(a.color.b-b.color.b)<.0001&&Math.abs(a.color.a-b.color.a)<.0001&&Math.abs(a.offset.x-b.offset.x)<.0001&&Math.abs(a.offset.y-b.offset.y)<.0001&&Math.abs(a.radius-b.radius)<.0001&&Math.abs((a.spread||0)-(b.spread||0))<.0001;}
function protectedChatSurface(n){return/^Seryo \/ (?:Plain guidance reply|Student message bubble|Student message alignment|Question draft|Question field|Composer controls)/.test(n.name)||/^Auth (?:input|field|action|agreement) \/ /.test(n.name)||/^(?:Action|Tool|Tab) \/ /.test(n.name);}
function contentExtent(body,viewport){const a=absolute(viewport),b=absolute(body);let bottom=b.y+b.h;for(const child of body.children.filter(shown)){const c=absolute(child);bottom=Math.max(bottom,c.y+c.h);}return{height:Math.max(0,bottom-b.y),bottom:bottom-a.y+(viewport.paddingBottom||0)};}
for(const root of roots){
 const theme=themeOf(root),nodes=all(root).filter(shown),known=nodes.filter(n=>viewportKinds[n.name]),safe=new Set(),raised=[];remember(root);
 for(const viewport of known){
  const def=viewportKinds[viewport.name],children='children'in viewport?viewport.children.filter(shown):[],matching=children.filter(n=>def.body.test(n.name)),body=matching[0];
  const record={root:root.id,id:viewport.id,kind:def.kind,h:Math.round(viewport.height*10)/10,body:body?.id||null,safe:false,scrollRequired:false,enabled:viewport.overflowDirection==='VERTICAL'&&viewport.clipsContent===true};
  viewportEvidence.push(record);
  if(viewport.type!=='FRAME'&&viewport.type!=='INSTANCE'){issue(root,viewport,'Known viewport is not a frame or instance');continue;}
  if(viewport.layoutMode!=='VERTICAL'||viewport.height<=0||viewport.width<=0||viewport.height>root.height+.1||viewport.layoutSizingVertical==='HUG'){issue(root,viewport,'Collapsed, unbounded or non-vertical viewport; inspect shell before changing');continue;}
  if(matching.length!==1||children.length!==1||!body||!('layoutMode'in body)||body.layoutMode!=='VERTICAL'){issue(root,viewport,'Unexpected viewport body; no wrapping or clipping change applied');continue;}
  if(body.layoutSizingVertical!=='HUG'&&body.primaryAxisSizingMode!=='AUTO'){issue(root,body,'Scrollable body is not content-sized; inspect before changing');continue;}
  const a=absolute(viewport),r=absolute(root);
  if(a.y<r.y-.1||a.y+a.h>r.y+r.h+.1||a.x<r.x-.1||a.x+a.w>r.x+r.w+.1){issue(root,viewport,'Viewport extends outside phone bounds');continue;}
  const extent=contentExtent(body,viewport);record.safe=true;record.contentH=Math.round(extent.height*10)/10;record.scrollRequired=extent.bottom>viewport.height+.5;record.range=Math.max(0,Math.round((extent.bottom-viewport.height)*10)/10);safe.add(viewport.id);remember(viewport);
  if(def.kind==='chat'){
   const composer=nodes.find(n=>n.name==='Seryo / Pinned composer host'||n.name==='Seryo / Bottom composer dock');
   if(!composer||composer.parent?.id!==viewport.parent?.id)issue(root,viewport,'Chat composer is missing or is not a pinned viewport sibling');
   const nav=nodes.find(n=>n.name==='Role navigation host');
   if(nav&&nav.parent?.id!==viewport.parent?.id)issue(root,nav,'Navigation is not a viewport sibling');
  }
  if(!record.enabled){proposed.add(viewport.id);if(apply){try{mutated.add(viewport.id);viewport.overflowDirection='VERTICAL';viewport.clipsContent=true;record.enabled=true;}catch(error){errors.push({id:viewport.id,operation:'scroll',error:String(error)});}}}
 }
 for(const node of nodes){
  const kind=surfaceKind(node);if(!kind||!('effects'in node)||protectedChatSurface(node))continue;
  if(node.type!=='FRAME'&&node.type!=='INSTANCE'&&node.type!=='COMPONENT')continue;
  const expected=shadow(kind,theme),existing=Array.isArray(node.effects)?node.effects:[],drops=existing.filter(e=>e.type==='DROP_SHADOW'),changed=drops.length!==1||!sameEffect(drops[0],expected);
  const record={root:root.id,id:node.id,kind,theme,changed};elevationEvidence.push(record);raised.push(node.id);remember(node);
  if(changed){proposed.add(node.id);if(apply){try{mutated.add(node.id);node.effects=[...existing.filter(e=>e.type!=='DROP_SHADOW'),expected];}catch(error){errors.push({id:node.id,operation:'elevation',error:String(error)});}}}
 }
 // Content below a phone is safe only when a recognized scroll viewport owns it.
 const r=absolute(root);let escaped=0;
 for(const node of nodes){
  if(node.type!=='TEXT'||!node.characters.trim())continue;
  let ownsScroll=false;for(let p=node.parent;p&&p.id!==root.id;p=p.parent)if(safe.has(p.id)){ownsScroll=true;break;}
  if(ownsScroll)continue;
  const b=absolute(node);if(b.y+b.h>r.y+r.h+.5||b.y<r.y-.5){escaped++;issue(root,node,'Text leaves phone outside a recognized scrolling body');}
 }
 if(!known.length&&nodes.some(n=>n.type==='TEXT')&&root.children.some(n=>shown(n)&&n.y+n.height>root.height+.5))issue(root,root,'Flat overflowing layout has no recognized viewport; leave intact and design a bounded shell');
 rootEvidence.push({id:root.id,theme,viewports:known.length,safe:safe.size,raised:raised.length,escapedText:escaped});
}
const geometryChanges=[];
for(const {node,before}of snapshots.values())if(!node.removed&&!same(before,dims(node)))geometryChanges.push({id:node.id,before,after:dims(node)});
const counts=arr=>arr.reduce((a,x)=>(a[x.kind]=(a[x.kind]||0)+1,a),{});
return{
 phase,createdNodeIds:[],mutatedNodeIds:[...mutated],deletedNodeIds:[],proposedNodeIds:[...proposed],
 rootCount:roots.length,missingRootIds,skippedRootIds,
 coverage:{viewports:viewportEvidence.length,safeViewports:viewportEvidence.filter(v=>v.safe).length,longBodies:viewportEvidence.filter(v=>v.scrollRequired).length,enabledScrollViewports:viewportEvidence.filter(v=>v.enabled).length,elevatedSurfaces:elevationEvidence.length,viewportKinds:counts(viewportEvidence),elevationKinds:counts(elevationEvidence)},
 allViewportIds:viewportEvidence.map(v=>v.id),allElevationIds:elevationEvidence.map(v=>v.id),
 unsafeNodeIds:[...new Set(issues.map(i=>i.id))],issues:issues.slice(0,60),issueCount:issues.length,errors,geometryChanges,
 samples:{roots:rootEvidence.slice(0,8),longBodies:viewportEvidence.filter(v=>v.scrollRequired).slice(0,16),elevation:elevationEvidence.filter(v=>v.changed).slice(0,16)},
 limits:{bodyGeometryChanged:false,contentChanged:false,reactionsChanged:false,parentingChanged:false,fontsUntouched:true,plainRepliesAndBubblesUntouched:true,inspectionOnly:!apply}
};
