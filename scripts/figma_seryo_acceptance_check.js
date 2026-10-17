// Read-only Figma check. Root injects SCREENS, PROOFS and COMPONENTS.
const page=await figma.getNodeByIdAsync('14:2'),issues=[],stats={sources:0,proofs:0,components:0,editablePhones:0,texts:0,vectors:0,disabledSend:0,menuNativeHandoffs:0,types:{}};
const all=n=>[n,...('findAll'in n?n.findAll(()=>true):[])],epsilon=0.5;
function add(id,check,detail){issues.push({id,check,detail});}
function pageOf(n){while(n&&n.type!=='PAGE')n=n.parent;return n;}
function visible(n,root){while(n&&n!==root){if(n.visible===false)return false;n=n.parent;}return !!n;}
function actions(n){return(n.reactions||[]).flatMap(r=>r.actions||[r.action].filter(Boolean));}
function destinations(n){return actions(n).filter(a=>a.type==='NODE').map(a=>a.destinationId);}
function exactRoute(n,id,root,check){const dest=n?destinations(n):[];if(!n||dest.length!==1||dest[0]!==id||actions(n).length!==1)add(root.id,check,{node:n?.id||null,expected:id,actual:dest});}
function relative(n,root){const a=n.absoluteTransform,b=root.absoluteTransform;return{x:a[0][2]-b[0][2],y:a[1][2]-b[1][2],w:n.width,h:n.height};}
function inside(a,b){return a.x>=b.x-epsilon&&a.y>=b.y-epsilon&&a.x+a.w<=b.x+b.w+epsilon&&a.y+a.h<=b.y+b.h+epsilon;}
function noVisibleFill(n){return(n.fills||[]).filter(p=>p.visible!==false&&(p.opacity??1)>0).length===0;}
function find(root,name){return root.findOne(n=>n.name===name);}
const sourceMap=new Map((SCREENS||[]).map(s=>[s.id,s]));
if(sourceMap.size!==8)add('14:2','source-count',{expected:8,actual:sourceMap.size});
if((PROOFS||[]).length!==12)add('14:2','proof-count',{expected:12,actual:(PROOFS||[]).length});
if((COMPONENTS||[]).length!==2)add('14:2','component-count',{expected:2,actual:(COMPONENTS||[]).length});
const targets={Light:{'Attach a file':'24:82','AI privacy':'95:2'},Dark:{'Attach a file':'36:850','AI privacy':'97:162'}};
const phoneRecords=[...(SCREENS||[]).map(s=>({...s,isProof:false})),...(PROOFS||[]).map(p=>({...sourceMap.get(p.source),...p,isProof:true}))];
for(const record of phoneRecords){
 const root=await figma.getNodeByIdAsync(record.id);if(!root||root.type!=='FRAME'||pageOf(root)?.id!==page?.id){add(record.id,'phone-missing','Expected a native frame on page 14:2.');continue;}
 if(record.isProof)stats.proofs++;else stats.sources++;
 const nodes=all(root).filter(n=>visible(n,root)),viewport=find(root,'Seryo / Scrollable chat viewport'),composer=find(root,'Seryo / Pinned composer host'),nav=root.children.find(n=>n.name==='Role navigation host');
 let imageCount=0;for(const node of nodes){stats.types[node.type]=(stats.types[node.type]||0)+1;if(node.type==='TEXT')stats.texts++;if(node.type==='VECTOR')stats.vectors++;for(const property of ['fills','strokes'])if(Array.isArray(node[property]))imageCount+=node[property].filter(p=>p.visible!==false&&p.type==='IMAGE').length;}
 if(imageCount)add(root.id,'native-editability',{imagePaints:imageCount});else if(nodes.some(n=>n.type==='TEXT')&&nodes.some(n=>n.type==='VECTOR'))stats.editablePhones++;else add(root.id,'native-editability','Expected editable text and vector content.');
 if(!viewport||!composer||!nav){add(root.id,'shell-slots',{viewport:!!viewport,composer:!!composer,nav:!!nav});continue;}
 const vb=relative(viewport,root),cb=relative(composer,root),nb=relative(nav,root);
 if(viewport.overflowDirection!=='VERTICAL'||!viewport.clipsContent||vb.h<=0)add(root.id,'thread-scroll',{overflow:viewport.overflowDirection,clips:viewport.clipsContent,height:vb.h});
 if(vb.y+vb.h>cb.y+epsilon||cb.y+cb.h>nb.y+epsilon||nb.y+nb.h>root.height+epsilon)add(root.id,'pinned-composer-geometry',{viewportBottom:vb.y+vb.h,composerY:cb.y,composerBottom:cb.y+cb.h,navY:nb.y,navBottom:nb.y+nb.h,phoneHeight:root.height});
 const sends=nodes.filter(n=>n.name==='Action / Send • disabled');if(sends.length!==1)add(root.id,'disabled-send-count',sends.length);for(const send of sends){if(actions(send).length)add(root.id,'disabled-send-actions',{node:send.id});else stats.disabledSend++;}
 for(const n of nodes){for(const r of n.reactions||[])if(/TIME|DELAY/i.test(r.trigger?.type||''))add(root.id,'timed-action',{node:n.id,trigger:r.trigger.type});for(const action of actions(n))if(action.type==='URL')add(root.id,'provider-or-external-action',{node:n.id,type:action.type});}
 const isConversation=String(record.kind||'').startsWith('conversation'),isMenu=String(record.kind||'').endsWith('-menu');
 if(isConversation){
  const bubbles=nodes.filter(n=>n.name==='Seryo / Student message bubble'),replies=nodes.filter(n=>n.name==='Seryo / Plain guidance reply');if(bubbles.length<2||replies.length<2||bubbles.length!==replies.length)add(root.id,'exchange-count',{student:bubbles.length,seryo:replies.length});
  for(const bubble of bubbles){const row=bubble.parent,b=relative(bubble,root),r=relative(row,root);if(b.w>282+epsilon||Math.abs(b.x+b.w-r.x-r.w)>epsilon)add(root.id,'right-aligned-user-bubble',{node:bubble.id,width:b.w,rightGap:r.x+r.w-b.x-b.w});}
  for(const reply of replies){if(!noVisibleFill(reply))add(root.id,'plain-seryo-reply',{node:reply.id});const caption=reply.findOne(n=>n.type==='TEXT'&&n.name==='Seryo / Source caption');if(!caption||!caption.characters.startsWith('Guidance · '))add(root.id,'guidance-source-caption',{node:reply.id});}
  exactRoute(find(root,'Action / Open Cashiering'),record.theme==='Dark'?'60:246':'60:2',root,'contextual-cashiering-handoff');
 }else{
  const body=find(root,'Seryo / Centered welcome content'),welcome=find(root,'Seryo / Welcome');if(!body||!welcome)add(root.id,'welcome-structure','Centered welcome body/card missing.');else{const wb=relative(welcome,root),bb=relative(body,root);if(wb.x<vb.x-epsilon||wb.x+wb.w>vb.x+vb.w+epsilon||Math.abs(wb.x+wb.w/2-vb.x-vb.w/2)>epsilon)add(root.id,'welcome-horizontal',{welcome:wb,viewport:vb});if(bb.h<vb.h-epsilon)add(root.id,'welcome-fill',{bodyHeight:bb.h,viewportHeight:vb.h});if(bb.h<=vb.h+epsilon&&Math.abs(wb.y+wb.h/2-vb.y-vb.h/2)>1)add(root.id,'welcome-short-content-centering',{welcomeCenter:wb.y+wb.h/2,viewportCenter:vb.y+vb.h/2});}
 }
 if(isMenu){
  const menu=root.children.find(n=>n.type==='INSTANCE'&&/^GoServe tools \/ (Welcome|Conversation) menu$/.test(n.name)),backdrop=find(root,'GoServe tools / Dismiss backdrop');
  if(!menu){add(root.id,'tools-menu-missing','Expected reusable menu instance.');continue;}
  const mb=relative(menu,root);if(!inside(mb,{x:0,y:0,w:root.width,h:root.height})||mb.h>344+epsilon||mb.y+mb.h>cb.y-8+epsilon)add(root.id,'menu-phone-bounds',{menu:mb,composerY:cb.y,phone:{w:root.width,h:root.height}});
  const menuViewport=find(menu,'GoServe tools / Scrollable menu viewport');if(!menuViewport||menuViewport.overflowDirection!=='VERTICAL'||!menuViewport.clipsContent||menuViewport.height<=0)add(root.id,'menu-scroll',{viewport:menuViewport?.id||null});
  const rows=menu.findAll(n=>n.name.startsWith('Tool / ')),names=rows.map(n=>n.name.slice(7)),expected=Object.keys(targets[record.theme]||{});if(rows.length!==2||names.some(n=>!expected.includes(n))||new Set(names).size!==2)add(root.id,'tools-two-options',{actual:names,expected});
  for(const row of rows){const name=row.name.slice(7);exactRoute(row,targets[record.theme]?.[name],root,'tool-native-handoff');if(row.height<48-epsilon)add(root.id,'tool-target-height',{node:row.id,height:row.height});if(!record.isProof)stats.menuNativeHandoffs++;}
  if(!record.isProof){exactRoute(find(menu,'Action / Close GoServe tools'),record.origin,root,'menu-close-origin');exactRoute(backdrop,record.origin,root,'menu-backdrop-origin');}
 }else if(!record.isProof){
  const desiredKind=isConversation?'conversation-menu':'empty-menu',destination=(SCREENS||[]).find(s=>s.theme===record.theme&&s.kind===desiredKind);exactRoute(find(root,'Action / Open GoServe tools'),destination?.id,root,'plus-opens-correct-menu');
 }
}
const localStyles=await figma.getLocalTextStylesAsync(),styleIds=new Set(localStyles.filter(s=>s.name.startsWith('GoServe / ')).map(s=>s.id));
for(const record of COMPONENTS||[]){const component=await figma.getNodeByIdAsync(record.id);if(!component||component.type!=='COMPONENT'||pageOf(component)?.id!==page?.id){add(record.id,'menu-component-missing','Expected an existing reusable component on page 14:2.');continue;}stats.components++;if(component.width!==350||component.height<48||component.height>344)add(component.id,'menu-component-bounds',{width:component.width,height:component.height});for(const node of all(component).filter(n=>visible(n,component))){if(node.type==='TEXT'&&!styleIds.has(node.textStyleId))add(component.id,'component-text-style',{node:node.id,style:typeof node.textStyleId==='string'?node.textStyleId:'mixed'});for(const property of ['fills','strokes'])if(Array.isArray(node[property]))for(const p of node[property])if(p.visible!==false&&(p.opacity??1)>0&&p.type==='SOLID'&&!p.boundVariables?.color)add(component.id,'component-color-binding',{node:node.id,property});}if(!component.description)add(component.id,'component-description','Menu purpose is missing.');}
return{stats,issueCount:issues.length,issues:issues.slice(0,30),truncated:issues.length>30,evidenceBoundary:'Read-only Figma structure, prototype routes and geometry. No Android, provider, file-processing or runtime claims.'};
