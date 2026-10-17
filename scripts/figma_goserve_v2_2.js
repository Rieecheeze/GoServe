// Executed through use_figma with OP set to foundations, screens-light, screens-dark, or navigation.
await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('14:2'));
const createdNodeIds = [], mutatedNodeIds = [], deletedNodeIds = [];
const fonts = [{family:'Montserrat',style:'ExtraBold'},{family:'Karla',style:'Regular'},{family:'Karla',style:'SemiBold'}];
await Promise.all(fonts.map(f=>figma.loadFontAsync(f)));
const styles = Object.fromEntries((await figma.getLocalTextStylesAsync()).filter(s=>s.name.startsWith('GoServe / ')).map(s=>[s.name.split(' / ')[1],s.id]));
const variables = await figma.variables.getLocalVariablesAsync();
const collections = await figma.variables.getLocalVariableCollectionsAsync();
function v(theme,key){const c=collections.find(c=>c.name==='GoServe / '+theme);return variables.find(v=>v.variableCollectionId===c.id&&v.name==='color/'+key)}
function paint(theme,key){const variable=v(theme,key),value=Object.values(variable.valuesByMode)[0];return figma.variables.setBoundVariableForPaint({type:'SOLID',color:{r:value.r,g:value.g,b:value.b}},'color',variable)}
function record(n){createdNodeIds.push(n.id);return n}
function box(parent,name,width,gap=12,padding=0,color=null,theme='Light'){
 const n=record(figma.createAutoLayout('VERTICAL'));n.name=name;n.resize(width,1);n.primaryAxisSizingMode='AUTO';n.counterAxisSizingMode='FIXED';n.itemSpacing=gap;n.paddingLeft=n.paddingRight=n.paddingTop=n.paddingBottom=padding;n.fills=color?[paint(theme,color)]:[];if(parent)parent.appendChild(n);return n;
}
function text(parent,characters,role='Body',theme='Light',color='text'){
 const n=record(figma.createText());n.name=characters.slice(0,45);n.textStyleId=styles[role];n.fills=[paint(theme,color)];n.resize(Math.max(1,parent.width-parent.paddingLeft-parent.paddingRight),100);parent.appendChild(n);n.layoutSizingHorizontal='FILL';n.layoutSizingVertical='HUG';n.textAutoResize='HEIGHT';n.characters=characters;return n;
}
function card(parent,title,body,theme='Light',color='surface'){
 const n=box(parent,title,parent.width-parent.paddingLeft-parent.paddingRight,8,16,color,theme);n.cornerRadius=18;n.strokes=[paint(theme,'border')];n.strokeWeight=1;text(n,title,'Label',theme);text(n,body,'Support',theme,'secondary');return n;
}
function button(parent,label,theme='Light',destination=null,outlined=false){
 const n=box(parent,label,parent.width-parent.paddingLeft-parent.paddingRight,0,14,outlined?'surface':'primary',theme);n.cornerRadius=24;n.counterAxisAlignItems='CENTER';n.minHeight=48;n.strokesIncludedInLayout=false;const labelNode=text(n,label,'Label',theme,outlined?'text':'onPrimary');labelNode.textAlignHorizontal='CENTER';if(outlined){n.strokes=[paint(theme,'border')];n.strokeWeight=1}if(destination)route(n,destination);return n;
}
function route(node,destination){node.reactions=[{trigger:{type:'ON_CLICK'},actions:[{type:'NODE',destinationId:destination,navigation:'NAVIGATE',transition:null,resetScrollPosition:true}]}]}
const paths={Home:['M4 10L12 3L20 10V20H15V14H9V20H4Z'],Requests:['M6 3L8 4L10 3L12 4L14 3L16 4L18 3V17H6V3 M6 17H3V19a2 2 0 0 0 2 2H17a2 2 0 0 0 2 -2V17H6 M9 7H15 M9 10H15 M9 13H13'],Consult:['M5 5H19a1 1 0 0 1 1 1V20H4V6a1 1 0 0 1 1 -1 M8 3V7 M16 3V7 M4 10H20 M10 14H14V18H10Z'],Inbox:['M3 3H15V13H7L3 17V3 M18 7H21V21L17 17H10V16'],'AI Chat':['M5 4H19a2 2 0 0 1 2 2V15a2 2 0 0 1 -2 2H10L5 21V17a2 2 0 0 1 -2 -2V6a2 2 0 0 1 2 -2 M7 9H17 M7 13H14'],More:['M4 6H20 M4 12H20 M4 18H20']};
function icon(parent,label,color){const svg='<svg width="24" height="24" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="'+paths[label][0]+'" fill="none" stroke="'+color+'" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>';const n=figma.createNodeFromSvg(svg);n.name='Icon / '+label;parent.appendChild(n);createdNodeIds.push(n.id,...n.findAll(()=>true).map(c=>c.id));return n}
const labels=['Home','Requests','Consult','Inbox','AI Chat','More'];
function navComponent(selected,theme='Light'){
 const name='GoServe / Capsule navigation / '+selected+(theme==='Dark'?' / Dark':'');let n=figma.currentPage.children.find(n=>n.type==='COMPONENT'&&n.name===name);if(n)return n;
 n=record(figma.createComponent());n.name=name;n.description='Six student destinations. 48dp minimum targets, centered icons only, with accessible names and tooltips in Android.';n.layoutMode='VERTICAL';n.resize(390,56);n.primaryAxisSizingMode='AUTO';n.counterAxisSizingMode='FIXED';n.paddingLeft=n.paddingRight=12;n.fills=[];n.x=theme==='Dark'?6350:5900;n.y=labels.indexOf(selected)*120;
 const cap=box(n,'Capsule',366,0,4,theme==='Dark'?'navWhite':'navigation',theme);cap.cornerRadius=36;const row=record(figma.createAutoLayout('HORIZONTAL'));row.name='Destinations';row.resize(358,48);row.primaryAxisSizingMode='FIXED';row.counterAxisSizingMode='AUTO';row.fills=[];cap.appendChild(row);
 for(const label of labels){const tab=box(row,'Tab / '+label,358/6,0,0);tab.layoutSizingHorizontal='FILL';tab.counterAxisAlignItems='CENTER';const slot=record(figma.createFrame());slot.name='Centered icon slot';slot.resize(48,40);slot.fills=[];tab.appendChild(slot);if(label===selected){const circle=record(figma.createEllipse());circle.name='Selected indicator';circle.resize(36,36);circle.x=6;circle.y=2;circle.fills=[paint(theme,theme==='Dark'?'onPrimary':'navWhite')];slot.appendChild(circle)}const mark=icon(slot,label,label===selected?(theme==='Dark'?'#F4F4F4':'#171717'):(theme==='Dark'?'#171717':'#F4F4F4'));mark.resize(22,22);mark.x=13;mark.y=9;const tx=text(tab,label,'Navigation',theme,theme==='Dark'?'onPrimary':'navWhite');tx.textAlignHorizontal='CENTER';tx.visible=false;tab.resize(tab.width,48);tab.primaryAxisSizingMode='FIXED';tab.primaryAxisAlignItems='CENTER'}
 return n;
}
function installNav(screen,selected,theme){
 const component=navComponent(selected,theme), instance=record(component.createInstance());screen.appendChild(instance);instance.x=0;instance.y=screen.height-86;instance.constraints={horizontal:'STRETCH',vertical:'MAX'};
 const prefix=theme==='Dark'?'GoServe 2.1 • dark/':'GoServe 2.1 • ';const routes=['student/home','student/requests','student/consult','student/inbox','shared/assistant','student/more'];
 for(const tab of instance.findAllWithCriteria({types:['FRAME']}).filter(n=>n.name.startsWith('Tab / '))){const index=labels.indexOf(tab.name.slice(6));const target=figma.currentPage.children.find(n=>n.name===prefix+routes[index]);if(target && target.id!==screen.id)route(tab,target.id)}return instance;
}
function logo(parent,theme,size=40){const fill=theme==='Dark'?'#FFFFFF':'#0A0A0A',stroke=theme==='Dark'?'#0A0A0A':'#FFFFFF';const svg='<svg width="'+size+'" height="'+size+'" viewBox="0 0 300 300" xmlns="http://www.w3.org/2000/svg"><rect width="300" height="300" rx="78" fill="'+fill+'"/><path d="M70 200 Q150 40 230 200" stroke="'+stroke+'" stroke-width="16" fill="none" stroke-linecap="round"/><path d="M110 140 V200 M150 120 V200 M190 140 V200" stroke="'+stroke+'" stroke-width="8" fill="none" stroke-linecap="round"/><path d="M46 200 H254" stroke="'+stroke+'" stroke-width="20" stroke-linecap="round"/></svg>';const n=figma.createNodeFromSvg(svg);n.name='GoServe supplied brand logo';parent.appendChild(n);createdNodeIds.push(n.id,...n.findAll(()=>true).map(c=>c.id));return n}
async function clear(screen){const old=screen.findAllWithCriteria({types:['TEXT']});const fm=new Map();for(const n of old)for(const seg of n.getStyledTextSegments(['fontName']))fm.set(JSON.stringify(seg.fontName),seg.fontName);await Promise.all([...fm.values()].map(f=>figma.loadFontAsync(f)));for(const n of [...screen.children]){deletedNodeIds.push(n.id);n.remove()}mutatedNodeIds.push(screen.id)}
async function shell(screen,title,theme,selected='AI Chat'){
 await clear(screen);screen.fills=[paint(theme,'background')];screen.clipsContent=true;screen.resize(390,844);
 const header=box(screen,'Application header',390,12,20,null,theme);header.layoutMode='HORIZONTAL';header.counterAxisAlignItems='CENTER';header.y=24;const mark=icon(header,'AI Chat',theme==='Dark'?'#F4F4F4':'#171717');mark.resize(40,40);text(header,title,'Toolbar',theme);
 const viewport=box(screen,'Scrollable viewport',390,0,0,null,theme);viewport.resize(390,634);viewport.primaryAxisSizingMode='FIXED';viewport.y=108;viewport.clipsContent=true;viewport.overflowDirection='VERTICAL';const body=box(viewport,'Content grows with text',390,16,20,null,theme);
 installNav(screen,selected,theme);return body;
}
function setup(body,theme){card(body,'Seryo setup in progress','Chat will open after Seryo’s tasks and procedures are configured. You can browse offices while setup is underway.',theme,'muted')}
async function seryoScreen(screen,kind,theme){
 const title=kind==='consent'?'Seryo privacy':kind==='upload'?'Temporary file analysis':kind==='response'?'Seryo response design':kind==='error'?'Seryo unavailable':'Seryo';const b=await shell(screen,title,theme);text(b,'GoServe AI chatbot · Powered by eGovAI','Caption',theme,'secondary');
 const prefix=theme==='Dark'?'GoServe 2.1 • dark/':'GoServe 2.1 • ';const target=suffix=>figma.currentPage.children.find(n=>n.name===prefix+suffix)?.id;
 if(kind==='chat'){setup(b,theme);text(b,'Ask Seryo','Hero',theme);text(b,'Ask about campus offices, services, and the next step for a request.','Body',theme);text(b,'Your question','Label',theme);card(b,'What would you like to ask?','Draft only. Chat is unavailable during setup.',theme);button(b,'Send to Seryo · Setup pending',theme);button(b,'Attach a file',theme,target('shared/ai-upload'),true);button(b,'Review AI privacy',theme,target('shared/ai-consent'),true);button(b,'Browse offices',theme,target('student/offices'),true)}
 if(kind==='consent'){setup(b,theme);text(b,'You choose what to share','Hero',theme);text(b,'AI help is optional. Avoid sharing passwords, payment credentials, or sensitive student records.','Body',theme);text(b,'Seryo provides guidance. The responsible office confirms official requirements and decisions.','Body',theme);text(b,'Questions are not sent while setup is pending. Drafts stay on this screen and are not saved as chat history.','Support',theme,'secondary');text(b,'Consent will be requested before enabled AI processing. This preview does not record consent.','Label',theme);button(b,'Back to Seryo',theme,target('shared/assistant'))}
 if(kind==='upload'){setup(b,theme);text(b,'Explain a document','Hero',theme);text(b,'Planned support: one PDF, JPG, or PNG, up to 10 MB. Select a non-sensitive file.','Body',theme);button(b,'Choose file · Setup pending',theme,null,true);text(b,'File selection and upload will open when Seryo is configured. No file has been selected or uploaded.','Support',theme,'secondary');text(b,'Planned handling: temporary analysis only, with immediate file cleanup after success or failure. Server support must be completed before uploads are enabled.','Support',theme,'secondary')}
 if(kind==='response'){text(b,'Planned panel','Caption',theme,'secondary');text(b,'An answer from Seryo','Hero',theme);card(b,'Your question','A student question will appear here after chat is enabled.',theme);card(b,'Seryo','The response and approved source references will appear here. No AI response is being simulated in this design.',theme);text(b,'Guidance only. Check official requirements with the office.','Support',theme,'secondary');button(b,'Back to Seryo',theme,target('shared/assistant'))}
 if(kind==='error'){text(b,'Planned panel','Caption',theme,'secondary');text(b,'Seryo is unavailable','Hero',theme);text(b,'We could not get an answer. Try again later or contact the office.','Body',theme);card(b,'Temporary files','Show a confirmed cleanup result only after the server has verified deletion.',theme);button(b,'Back to Seryo',theme,target('shared/assistant'));button(b,'Browse offices',theme,target('student/offices'),true)}
}
if(OP.startsWith('screens')){
 const theme=OP.endsWith('dark')?'Dark':'Light';const prefix=theme==='Dark'?'GoServe 2.1 • dark/':'GoServe 2.1 • ';let consent=figma.currentPage.children.find(n=>n.name===prefix+'shared/ai-consent');if(!consent){consent=record(figma.createFrame());consent.name=prefix+'shared/ai-consent';consent.x=theme==='Dark'?5140:2290;consent.y=8090;consent.resize(390,844)}
 for(const [suffix,kind]of [['shared/assistant','chat'],['shared/ai-consent','consent'],['shared/ai-upload','upload'],['shared/ai-response','response'],['shared/ai-error','error']]){const screen=figma.currentPage.children.find(n=>n.name===prefix+suffix);await seryoScreen(screen,kind,theme)}
}
if(OP==='navigation'){
 for(const screen of figma.currentPage.children.filter(n=>n.type==='FRAME'&&n.width===390&&n.height===844&&n.name.includes('student/'))){
  const nav=screen.findAllWithCriteria({types:['FRAME']}).find(n=>n.name==='Material navigation bar');if(!nav)continue;
  const selected=/requests|request-detail|verified-file|pickup-token/.test(screen.name)?'Requests':/consult|appointment/.test(screen.name)?'Consult':/inbox|thread/.test(screen.name)?'Inbox':/more|settings/.test(screen.name)?'More':'Home';deletedNodeIds.push(nav.id);nav.remove();mutatedNodeIds.push(screen.id);installNav(screen,selected,screen.name.includes('dark/')?'Dark':'Light');const scroll=screen.children.find(n=>n.name==='Scrollable Android content');if(scroll){scroll.resize(scroll.width,Math.min(scroll.height,screen.height-102-scroll.y));mutatedNodeIds.push(scroll.id)}
 }
}
if(OP==='repair-seryo'){
 const ids=['24:51','95:2','24:82','40:2','24:111','36:819','97:162','36:850','40:33','36:879'];
 for(const id of ids){const screen=await figma.getNodeByIdAsync(id);const header=screen.children.find(n=>n.name==='Application header');header.resize(390,80);header.primaryAxisSizingMode='FIXED';header.counterAxisSizingMode='AUTO';mutatedNodeIds.push(header.id);
 const viewport=screen.children.find(n=>n.name==='Scrollable viewport');const body=viewport.children[0];body.layoutSizingHorizontal='FILL';mutatedNodeIds.push(body.id);
 for(const child of body.children){if(child.type==='FRAME'){child.layoutSizingHorizontal='FILL';child.primaryAxisSizingMode='AUTO';child.counterAxisSizingMode='FIXED';mutatedNodeIds.push(child.id)}}
 for(const node of screen.findAllWithCriteria({types:['TEXT']}).filter(n=>!n.id.startsWith('I'))){node.resize(Math.max(1,node.parent.width-node.parent.paddingLeft-node.parent.paddingRight),100);node.layoutSizingHorizontal='FILL';node.layoutSizingVertical='HUG';node.textAutoResize='HEIGHT';mutatedNodeIds.push(node.id)}
 }
 for(const comp of figma.currentPage.children.filter(n=>n.type==='COMPONENT'&&n.name.startsWith('GoServe / Capsule'))){for(const node of comp.findAllWithCriteria({types:['TEXT']})){node.resize(node.parent.width,100);node.layoutSizingHorizontal='FILL';node.layoutSizingVertical='HUG';node.textAutoResize='HEIGHT';mutatedNodeIds.push(node.id)}}
}
if(OP==='subscreen-navigation'){
 const targets=figma.currentPage.children.filter(n=>n.type==='FRAME'&&n.width===390&&n.height===844&&n.name.includes('student/')&&/offices|request-detail|verified-file/.test(n.name));
 for(const screen of targets){if(!screen.children.some(n=>n.type==='INSTANCE'&&n.name.startsWith('GoServe / Capsule'))){installNav(screen,/request-detail|verified-file/.test(screen.name)?'Requests':'Home',screen.name.includes('dark/')?'Dark':'Light');mutatedNodeIds.push(screen.id)}}
}
if(OP==='auth-loading'){
 const startX=Math.max(...figma.currentPage.children.map(n=>n.x+n.width))+180;
 for(const [index,theme] of ['Light','Dark'].entries()){
  const name='GoServe 2.2 • '+theme.toLowerCase()+'/authentication-loading';if(figma.currentPage.children.some(n=>n.name===name))continue;
  const screen=record(figma.createFrame());screen.name=name;screen.resize(390,844);screen.x=startX+index*450;screen.y=90;screen.fills=[paint(theme,'background')];
  const group=box(screen,'Branded authentication loading',342,20,0);group.x=24;group.y=286;group.counterAxisAlignItems='CENTER';logo(group,theme,112);
  const title=text(group,'GoServe','Toolbar',theme);title.textAlignHorizontal='CENTER';const progress=figma.createNodeFromSvg('<svg width="32" height="32" viewBox="0 0 32 32" xmlns="http://www.w3.org/2000/svg"><path d="M16 2a14 14 0 1 1 -14 14" fill="none" stroke="'+(theme==='Dark'?'#F2F2F2':'#171717')+'" stroke-width="3" stroke-linecap="round"/></svg>');progress.name='Indeterminate progress';group.appendChild(progress);createdNodeIds.push(progress.id,...progress.findAll(()=>true).map(n=>n.id));
  const status=text(group,'Connecting to GoServe…','Support',theme,'secondary');status.textAlignHorizontal='CENTER';
 }
}
return {createdNodeIds,mutatedNodeIds,deletedNodeIds,components:figma.currentPage.children.filter(n=>n.type==='COMPONENT'&&n.name.startsWith('GoServe /')).map(n=>({id:n.id,name:n.name})),screens:figma.currentPage.children.filter(n=>n.type==='FRAME'&&/shared\/(ai|assistant)/.test(n.name)).map(n=>({id:n.id,name:n.name,w:n.width,h:n.height}))};
