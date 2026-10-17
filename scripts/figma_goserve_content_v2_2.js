await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('14:2'));
const createdNodeIds = [], mutatedNodeIds = [], deletedNodeIds = [];
const theme = THEME;
const prefix = theme === 'Dark' ? 'GoServe 2.1 • dark/' : 'GoServe 2.1 • ';
const ids = theme === 'Dark'
  ? { home:'36:93', directory:'36:1766', list:'36:377', request:'36:440', verified:'36:489', signin:'36:2' }
  : { home:'19:93', directory:'33:54', list:'23:32', request:'23:95', verified:'23:144', signin:'19:2' };
const styles = Object.fromEntries((await figma.getLocalTextStylesAsync()).filter(s => s.name.startsWith('GoServe / ')).map(s => [s.name.split(' / ')[1],s.id]));
const variables = await figma.variables.getLocalVariablesAsync();
const collection = (await figma.variables.getLocalVariableCollectionsAsync()).find(c => c.name === 'GoServe / '+theme);
const screens = {};
for (const [name,id] of Object.entries(ids)) screens[name] = await figma.getNodeByIdAsync(id);
const fontMap = new Map();
for (const screen of Object.values(screens)) for (const n of screen.findAllWithCriteria({types:['TEXT']})) {
  for (const segment of n.getStyledTextSegments(['fontName'])) fontMap.set(JSON.stringify(segment.fontName),segment.fontName);
}
for (const font of [{family:'Montserrat',style:'ExtraBold'},{family:'Karla',style:'Regular'},{family:'Karla',style:'SemiBold'}]) fontMap.set(JSON.stringify(font),font);
await Promise.all([...fontMap.values()].map(f => figma.loadFontAsync(f)));
const paint = key => {const variable=variables.find(v=>v.variableCollectionId===collection.id&&v.name==='color/'+key);const value=variable.valuesByMode[collection.defaultModeId];return figma.variables.setBoundVariableForPaint({type:'SOLID',color:{r:value.r,g:value.g,b:value.b}},'color',variable);};
const record = node => {createdNodeIds.push(node.id);return node;};
function box(parent,name,width,gap=12,padding=0,color=null,horizontal=false,fill=true) {
  const n=record(figma.createAutoLayout(horizontal?'HORIZONTAL':'VERTICAL'));n.name=name;n.resize(width,1);
  n.primaryAxisSizingMode=horizontal?'FIXED':'AUTO';n.counterAxisSizingMode=horizontal?'AUTO':'FIXED';n.itemSpacing=gap;
  n.paddingLeft=n.paddingRight=n.paddingTop=n.paddingBottom=padding;n.fills=color?[paint(color)]:[];
  if(parent){parent.appendChild(n);if(fill&&'layoutMode' in parent && parent.layoutMode!=='NONE')n.layoutSizingHorizontal='FILL';}return n;
}
function text(parent,characters,role='Body',color='text') {
  const n=record(figma.createText());n.name=characters.slice(0,60);n.textStyleId=styles[role];
  n.fills=[paint(color)];n.resize(Math.max(1,parent.width-parent.paddingLeft-parent.paddingRight),100);n.textAutoResize='HEIGHT';n.characters=characters;
  parent.appendChild(n);n.layoutSizingHorizontal='FILL';return n;
}
function route(n,id) {let owner=n;while(owner.parent&&owner.parent.type!=='PAGE')owner=owner.parent;if(id&&owner.id!==id)n.reactions=[{trigger:{type:'ON_CLICK'},actions:[{type:'NODE',destinationId:id,navigation:'NAVIGATE',transition:null,resetScrollPosition:true}]}];}
const destination = suffix => figma.currentPage.children.find(n=>n.name===prefix+suffix)?.id;
function controlInstance(parent,kind,label,width,state=null,fill=true) {
  const set=figma.currentPage.children.find(n=>n.type==='COMPONENT_SET'&&n.name==='GoServe / '+kind+' control');
  const component=set?.children.find(n=>n.name==='Theme='+theme+(state?', State='+state:''));if(!component)return null;
  const n=component.createInstance();parent.appendChild(n);n.resize(width,48);if(fill)n.layoutSizingHorizontal='FILL';n.layoutSizingVertical='HUG';n.findAllWithCriteria({types:['TEXT']})[0].characters=label;createdNodeIds.push(n.id,...n.findAll(()=>true).map(c=>c.id));return n;
}
function button(parent,label,target=null,outlined=false) {
  const n=box(parent,label,parent.width,0,16,outlined?'surface':'primary');n.paddingTop=n.paddingBottom=14;n.cornerRadius=1000;n.minHeight=48;n.strokesIncludedInLayout=false;
  const tx=text(n,label,'Label',outlined?'text':'onPrimary');tx.textAlignHorizontal='CENTER';
  if(outlined){n.strokes=[paint('border')];n.strokeWeight=1;}route(n,target);return n;
}
function searchControl(parent,target) {const n=controlInstance(parent,'Search','Search offices or services',parent.width-parent.paddingLeft-parent.paddingRight)||button(parent,'Search offices or services',null,true);n.name='Search offices or services';route(n,target);return n;}
function inlineAction(parent,label,target) {const n=controlInstance(parent,'Inline action',label,64,null,false)||box(parent,label,64,0,0,null,false,false);n.name=label;n.minWidth=64;n.minHeight=48;n.primaryAxisAlignItems='CENTER';if(n.type!=='INSTANCE')text(n,label,'Label').textAlignHorizontal='CENTER';route(n,target);return n;}
function notice(parent,message) {const n=box(parent,'Explicit preview notice',parent.width,0,12,'muted');n.cornerRadius=16;text(n,message,'Caption','secondary');return n;}
function card(parent,title,body,role='Label') {const n=box(parent,title,parent.width,8,16,'surface');n.cornerRadius=18;n.strokes=[paint('border')];n.strokeWeight=1;text(n,title,role);if(body)text(n,body,'Support','secondary');return n;}
function svg(parent,name,source,width,height) {const n=figma.createNodeFromSvg(source);n.name=name;n.resize(width,height);parent.appendChild(n);createdNodeIds.push(n.id,...n.findAll(()=>true).map(c=>c.id));return n;}
function logo(parent,size) {
  const fill=theme==='Dark'?'#FFFFFF':'#0A0A0A',stroke=theme==='Dark'?'#0A0A0A':'#FFFFFF';
  return svg(parent,'GoServe supplied brand logo','<svg width="'+size+'" height="'+size+'" viewBox="0 0 300 300" xmlns="http://www.w3.org/2000/svg"><rect width="300" height="300" rx="78" fill="'+fill+'"/><path d="M70 200 Q150 40 230 200" stroke="'+stroke+'" stroke-width="16" fill="none" stroke-linecap="round" stroke-linejoin="round"/><path d="M110 140 V200 M150 120 V200 M190 140 V200" stroke="'+stroke+'" stroke-width="8" fill="none" stroke-linecap="round" stroke-linejoin="round"/><path d="M46 200 H254" stroke="'+stroke+'" stroke-width="20" stroke-linecap="round"/></svg>',size,size);
}
function lineIcon(parent,kind) {
  const paths={back:'M19 12H5 M11 6L5 12L11 18',notification:'M6 17H18L16 14V9a4 4 0 0 0 -8 0V14Z M10 20H14',search:'M10 4a6 6 0 1 0 0 12a6 6 0 1 0 0 -12 M14 14L20 20'};
  return svg(parent,'Native outline / '+kind,'<svg width="24" height="24" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="'+paths[kind]+'" fill="none" stroke="'+(theme==='Dark'?'#F4F4F4':'#171717')+'" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>',24,24);
}
function preserve(node) {return node.name==='Android status inset'||node.name==='Android gesture inset'||/navigation/i.test(node.name);}
async function clearContent(screen) {
  for(const child of [...screen.children]) if(!preserve(child)) {
    deletedNodeIds.push(child.id,...('findAll' in child?child.findAll(()=>true).map(n=>n.id):[]));child.remove();
  }
  screen.fills=[paint('background')];screen.clipsContent=true;mutatedNodeIds.push(screen.id);
}
async function shell(screen,title,home=false) {
  await clearContent(screen);
  const header=box(screen,'Native Android app bar',390,12,20,null,true);header.paddingTop=header.paddingBottom=8;header.x=0;header.y=24;header.minHeight=64;header.counterAxisAlignItems='CENTER';
  if(home)logo(header,40);else{const back=box(header,'48dp back target',48,0,12);back.resize(48,48);back.layoutSizingHorizontal='FIXED';back.primaryAxisSizingMode='FIXED';lineIcon(back,'back');route(back,ids.home);}
  if(home)box(header,'Flexible masthead spacing',1,0,0);else text(header,title,'Toolbar');
  if(home){const bell=box(header,'48dp notification target',48,0,12);bell.resize(48,48);bell.layoutSizingHorizontal='FIXED';bell.primaryAxisSizingMode='FIXED';lineIcon(bell,'notification');}
  const viewport=box(screen,'Scrollable Android content',390,0,0);viewport.resize(390,654);viewport.primaryAxisSizingMode='FIXED';viewport.y=88;viewport.clipsContent=true;viewport.overflowDirection='VERTICAL';
  return box(viewport,'Hug content / wraps and scrolls',390,16,20);
}
const offices=[
  {mark:'REG',name:'Registrar',category:'Documents',status:'Open'},
  {mark:'ACC',name:'Student Accounting',category:'Account services',status:'On Lunch Break'},
  {mark:'CASH',name:'Cashiering',category:'Payments',status:'Open'},
  {mark:'GCC',name:'Guidance and Counseling Center',category:'Consultations',status:'Open'},
  {mark:'CCS',name:'College of Computer Studies',category:'Consultations',status:'Open'}
];
function officeArt(parent,accounting) {
  const bg=accounting?(theme==='Dark'?'#5A5A5A':'#A8A8A8'):(theme==='Dark'?'#3B3B3B':'#D1D1D1'),paper=theme==='Dark'?'#F4F4F4':'#FFFFFF';
  const content=accounting?'<path d="M78 83H83Q86 83 86 86V102Q86 105 83 105H78Q75 105 75 102V86Q75 83 78 83 M97 69H102Q105 69 105 72V102Q105 105 102 105H97Q94 105 94 102V72Q94 69 97 69 M116 55H121Q124 55 124 58V102Q124 105 121 105H116Q113 105 113 102V58Q113 55 116 55 M135 41H140Q143 41 143 44V102Q143 105 140 105H135Q132 105 132 102V44Q132 41 135 41" fill="'+paper+'"/>':'<path d="M110 40H148Q152 40 152 44V94Q152 98 148 98H110Q106 98 106 94V44Q106 40 110 40" fill="'+paper+'"/><path d="M115 52H143V54H115Z M115 64H143V66H115Z M115 76H143V78H115Z" fill="#171717"/>';
  const art=svg(parent,'Android native office illustration','<svg width="168" height="122" viewBox="0 0 168 122" xmlns="http://www.w3.org/2000/svg"><path d="M18 0H150Q168 0 168 18V104Q168 122 150 122H18Q0 122 0 104V18Q0 0 18 0" fill="'+bg+'"/>'+content+'</svg>',168,122);return art;
}
function officeTile(parent,office,width) {
  const tile=box(parent,'Office / '+office.name,width,0,0,'surface',false,false);tile.cornerRadius=18;tile.clipsContent=true;
  const art=officeArt(tile,office.mark==='ACC');art.resize(width,width*122/168);
  const mark=record(figma.createText());mark.textStyleId=styles.Section;mark.resize(width-26,100);mark.textAutoResize='HEIGHT';mark.characters=office.mark;mark.fills=[paint(office.mark==='ACC'?'text':'secondary')];art.appendChild(mark);mark.x=13;mark.y=12;
  const details=box(tile,'Office card wraps',width,6,10);text(details,office.status,'Caption');text(details,office.name,'Office');text(details,office.category,'Caption','secondary');route(tile,destination('student/office-detail'));return tile;
}
function filters(parent,selected,targets,home=false) {
  const viewport=box(parent,'Horizontal scrolling service filters',parent.width,0,0);viewport.resize(parent.width-parent.paddingLeft-parent.paddingRight,48);viewport.primaryAxisSizingMode='AUTO';viewport.minHeight=48;viewport.clipsContent=true;viewport.overflowDirection='HORIZONTAL';
  const row=box(viewport,'Filter labels / stateful',home?500:350,8,0,null,true,false);
  for(const label of home?['All services','Documents','Payments','Consultations']:['All services','Documents','Payments']) {
    const width=label==='Consultations'?130:110;let filter=controlInstance(row,'Filter',label,width,label===selected?'Selected':'Unselected',false);
    if(!filter){filter=box(row,'Filter / '+label,width,0,0,null,false,false);filter.minHeight=48;filter.primaryAxisAlignItems=filter.counterAxisAlignItems='CENTER';const pill=box(filter,'Visual filter pill',width,0,8,label===selected?'primary':'surface');pill.paddingTop=pill.paddingBottom=6;pill.cornerRadius=16;pill.minHeight=32;pill.strokesIncludedInLayout=false;if(label!==selected){pill.strokes=[paint('border')];pill.strokeWeight=1;}text(pill,label,'Label',label===selected?'onPrimary':'text').textAlignHorizontal='CENTER';}
    filter.name='Filter / '+label;route(filter,targets[label]);
  }
}
let nextX=Math.max(...figma.currentPage.children.map(n=>n.x+n.width))+120;
function variant(name,base) {
  let screen=figma.currentPage.children.find(n=>n.name===prefix+name);
  if(!screen){screen=record(figma.createFrame());screen.name=prefix+name;screen.resize(390,844);screen.x=nextX;screen.y=base.y;nextX+=510;}
  return screen;
}
const docs=variant('student/offices-documents',screens.directory),payments=variant('student/offices-payments',screens.directory);
const free=variant('student/request-detail-free',screens.request),pending=variant('student/request-detail-pending',screens.request);
for(const screen of [docs,payments,free,pending]) for(const n of screen.findAllWithCriteria({types:['TEXT']})) for(const segment of n.getStyledTextSegments(['fontName'])) await figma.loadFontAsync(segment.fontName);
const filterTargets={'All services':ids.directory,Documents:docs.id,Payments:payments.id,Consultations:destination('student/consult')};
async function home(hasRequest=false) {
  const body=await shell(screens.home,'GoServe',true);
  text(body,'Campus services, in one place','Hero');text(body,hasRequest?'Find the right office and check your request before heading across campus.':'Find the right office and learn how to start your first request.','Support','secondary');
  searchControl(body,ids.directory);filters(body,'All services',filterTargets,true);
  if(hasRequest){const active=box(body,'Demo active request',350,8,16,'primary');active.cornerRadius=18;text(active,'ACTIVE REQUEST','Caption','onPrimary');text(active,'Enrollment certificate','Section','onPrimary');text(active,'Registrar · DEMO-001 · Processing','Caption','onPrimary');button(active,'View request details',ids.request,true);}
  const heading=box(body,'Office directory heading',350,12,0,null,true);heading.counterAxisAlignItems='CENTER';text(heading,'Campus offices','Section');inlineAction(heading,'See all',ids.directory);
  for(let index=0;index<offices.length;index+=2){const row=box(body,'Office grid row '+(index/2+1),350,14,0,null,true);row.counterAxisAlignItems='MIN';for(const office of offices.slice(index,index+2))officeTile(row,office,168);}
}
async function directory(screen,selected) {
  const body=await shell(screen,'Campus offices');text(body,'Find an office','Hero');text(body,'Check availability and announcements before starting.','Support','secondary');searchControl(body,null);filters(body,selected,filterTargets);
  const data=selected==='Documents'?offices.filter(o=>o.mark==='REG'):selected==='Payments'?offices.filter(o=>o.mark==='CASH'):offices;
  for(const office of data){const c=card(body,office.name,'Casal / Arlegui','Office');text(c,office.status,'Caption');text(c,office.category,'Caption','secondary');text(c,'Non-official demo data','Caption','secondary');route(c,destination('student/office-detail'));}
}
async function requestList(hasRequests=false) {
  const screen=screens.list;await clearContent(screen);
  const viewport=box(screen,'Scrollable Android content',390,0,0);viewport.resize(390,718);viewport.primaryAxisSizingMode='FIXED';viewport.y=24;viewport.clipsContent=true;viewport.overflowDirection='VERTICAL';
  const body=box(viewport,hasRequests?'Request examples / native wrapping cards':'New account / Requests content',390,12,24);text(body,'Requests','Hero');
  if(!hasRequests){
    text(body,'Track requests you submit to campus offices.','Support','secondary');
    const empty=box(body,'New account / Empty request state',342,0);empty.minHeight=568;empty.primaryAxisAlignItems='CENTER';empty.counterAxisAlignItems='CENTER';
    const content=box(empty,'Empty requests / Centered content',310,16,0,null,false,false);content.maxWidth=310;content.layoutSizingHorizontal='FILL';
    const color=theme==='Dark'?'#BDBDBD':'#595959';const mark=svg(content,'Icon / Empty requests','<svg width="48" height="48" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="M6 3L8 4L10 3L12 4L14 3L16 4L18 3V17H6V3 M6 17H3V19a2 2 0 0 0 2 2H17a2 2 0 0 0 2 -2V17H6 M9 7H15 M9 10H15 M9 13H13" fill="none" stroke="'+color+'" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>',48,48);content.counterAxisAlignItems='CENTER';
    text(content,'No requests yet','Section').textAlignHorizontal='CENTER';text(content,'Your requests will appear here after you submit one to a campus office.','Support','secondary').textAlignHorizontal='CENTER';button(content,'Browse offices',ids.directory);return;
  }
  text(body,'Request examples','Section');
  const cases=[
    {title:'Enrollment certificate',office:'Registrar',status:'Processing',fee:'Required · Declared by the office in this example',id:ids.request},
    {title:'Verified document copy',office:'Registrar',status:'Ready for Pickup/Download',fee:'Free · Declared by the office in this example',id:free.id},
    {title:'Student service request',office:'Guidance and Counseling Center',status:'Submitted',fee:'Awaiting office fee declaration',id:pending.id}
  ];
  for(const c of cases){const n=box(body,'Demo request / '+c.title,342,8,18,'surface');n.cornerRadius=18;n.strokes=[paint('border')];n.strokeWeight=1;text(n,c.title,'Office');text(n,c.office,'Support','secondary');text(n,c.status,'Label');text(n,c.fee,'Support','secondary');text(n,'Demo request','Caption','secondary');route(n,c.id);}
}
function phase(parent,label,state) {
  const row=box(parent,label+' / '+state,318,12,0,null,true);row.counterAxisAlignItems='CENTER';
  const color=state==='Complete'?(theme==='Dark'?'#85D6A6':'#146C43'):(theme==='Dark'?'#F4F4F4':'#171717');
  const check=theme==='Dark'?'#062B18':'#FFFFFF';
  const path=state==='Complete'?'<circle cx="12" cy="12" r="10" fill="'+color+'"/><path d="M7 12L10 15L17 8" stroke="'+check+'" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"/>':'<circle cx="12" cy="12" r="9" stroke="'+color+'" stroke-width="2" fill="none"/>'+(state==='Current'?'<circle cx="12" cy="12" r="4" fill="'+color+'"/>':'');
  svg(row,'Progress icon / '+state,'<svg width="24" height="24" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">'+path+'</svg>',24,24);
  const copy=box(row,'Wrapped phase labels',282,2,0);text(copy,label,'Label',state==='Complete'?'success':state==='Current'?'text':'secondary');text(copy,state,'Caption',state==='Complete'?'success':'secondary');
}
async function request(screen,kind) {
  const body=await shell(screen,'Request details');notice(body,previewNotice);
  const required=kind==='required',isFree=kind==='free';text(body,required?'Enrollment certificate':isFree?'Verified document copy':'Student service request','Hero');
  text(body,required||isFree?'Registrar':'Guidance and Counseling Center','Support','secondary');text(body,'Reference: '+(required?'DEMO-001':isFree?'DEMO-002':'DEMO-003'),'Caption','secondary');
  text(body,required?'Processing':isFree?'Ready for Pickup/Download':'Submitted','Label');text(body,'Progress','Section');
  const progress=box(body,'Request phases grow with text',350,16,16,'surface');progress.cornerRadius=18;
  const phases=required?['Submitted','Awaiting Payment','Processing','Ready for Pickup/Download','Completed']:['Submitted','Processing','Ready for Pickup/Download','Completed'];
  const current=required?2:isFree?2:0;for(let i=0;i<phases.length;i++)phase(progress,phases[i],i<current?'Complete':i===current?'Current':'Upcoming');
  text(body,'Office fee declaration','Section');text(body,required?'Required · Declared by the office in this example':isFree?'Free · Declared by the office in this example':'Awaiting office fee declaration','Label');
  text(body,required?'A required fee must be paid before processing. Cashiering handles payments when an office requires a fee.':isFree?'This example has no fee. It moves from Submitted to Processing without Awaiting Payment.':'The office must confirm whether this service is free or requires payment before processing can begin.','Support','secondary');
  if(required){text(body,'Paid · Demo payment record','Label');text(body,'This example shows a paid required fee. It does not record or accept a real payment.','Support','secondary');}
  if(isFree)button(body,'View verified file',ids.verified,true);
}
async function verified() {
  const body=await shell(screens.verified,'Verified file');notice(body,'Demo file · Verification example only. No document has been issued or downloaded.');
  text(body,'Preview verification complete','Hero');const complete=card(body,'File verification: Complete','Enrollment certificate.pdf');
  const green=theme==='Dark'?'#85D6A6':'#146C43';svg(complete,'Native completion check','<svg width="40" height="40" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><circle cx="12" cy="12" r="9" stroke="'+green+'" stroke-width="1.8" fill="none"/><path d="M7 12L10 15L17 8" stroke="'+green+'" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>',40,40);
  text(body,'This example shows how an office-issued file will appear after verification. The request is free in this preview.','Body');button(body,'About downloads',null,true);
  text(body,'This preview contains no private file. Download access will be available when an office issues a real document.','Support','secondary');
}
async function signin() {
  await clearContent(screens.signin);const viewport=box(screens.signin,'Scrollable Android content',390,0,0);viewport.resize(390,796);viewport.primaryAxisSizingMode='FIXED';viewport.y=24;viewport.clipsContent=true;viewport.overflowDirection='VERTICAL';
  const body=box(viewport,'Authentication content / wrapping',390,20,24);body.paddingTop=36;logo(body,56);text(body,'Welcome back','Hero');text(body,'Log in to GoServe with your TIP Google account.','Support','secondary');
  const tabs=box(body,'Authentication tabs',342,4,4,'muted',true);tabs.cornerRadius=20;button(tabs,'Log in',null);button(tabs,'Sign up',destination('shared/sign-up'),true);
  button(body,'Continue with TIP Google',destination('shared/auth-loading'),true);const divider=text(body,'OR','Caption','secondary');divider.textAlignHorizontal='CENTER';
  button(body,'Office or demo sign-in',destination('shared/demo-login'),true);const hint=text(body,'Student: TIP Google. Office and demo: assigned email and password.','Support','secondary');hint.textAlignHorizontal='CENTER';
}
if(typeof ONLY!=='undefined'&&ONLY==='requests'){await requestList();return{theme,createdNodeIds,mutatedNodeIds,deletedNodeIds,list:{id:screens.list.id,targets:[ids.request,free.id,pending.id]},variants:[docs,payments,free,pending].map(n=>({id:n.id,name:n.name}))};}
await home();await directory(screens.directory,'All services');await directory(docs,'Documents');await directory(payments,'Payments');await requestList();
await request(screens.request,'required');await request(free,'free');await request(pending,'pending');await verified();await signin();
const updated=[...Object.values(screens),docs,payments,free,pending];
const fontIssues=[],widthIssues=[];
for(const screen of updated) for(const n of screen.findAllWithCriteria({types:['TEXT']})) {
  if(!['Montserrat','Karla'].includes(n.fontName.family) && !/navigation|inset/.test(n.parent?.name||''))fontIssues.push(n.id);
  if(n.width<1||n.height<1)widthIssues.push(n.id);
}
return {theme,createdNodeIds,mutatedNodeIds,deletedNodeIds,variants:[docs,payments,free,pending].map(n=>({id:n.id,name:n.name})),screens:updated.map(n=>({id:n.id,name:n.name,texts:n.findAllWithCriteria({types:['TEXT']}).length,viewport:n.children.filter(c=>c.name==='Scrollable Android content').map(c=>({id:c.id,height:c.height,contentHeight:c.children[0]?.height}))})),fontIssues,widthIssues};
