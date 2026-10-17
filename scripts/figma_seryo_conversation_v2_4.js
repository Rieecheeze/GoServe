// Figma-only Seryo revision. Existing native handoffs remain static prototype links.
await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('14:2'));
const page=figma.currentPage,created=new Set(),mutated=new Set(),deleted=new Set(),reports=[],routes=[],annotations=[];
const section=await figma.getNodeByIdAsync('189:7634'),componentsSection=await figma.getNodeByIdAsync('189:7485');
const all=n=>[n,...('findAll'in n?n.findAll(()=>true):[])];
const track=n=>{for(const c of all(n))created.add(c.id);return n;};
const mark=n=>{if(n&&!created.has(n.id))mutated.add(n.id);return n;};
const remove=n=>{for(const c of all(n))deleted.add(c.id);n.remove();};
const pageOf=n=>{while(n&&n.type!=='PAGE')n=n.parent;return n;};
const exact=(name,type)=>page.findAll(n=>n.type===type&&n.name===name)[0];
const styles=Object.fromEntries((await figma.getLocalTextStylesAsync()).filter(s=>s.name.startsWith('GoServe / ')).map(s=>[s.name.split(' / ')[1],s]));
const collections=await figma.variables.getLocalVariableCollectionsAsync(),variables=await figma.variables.getLocalVariablesAsync();
const defs=[
 {theme:'Light',empty:'24:51',conversation:'40:2',nav:'95:45',navMain:'95:12',auth:'19:2',logo:'107:643',upload:'24:82',offices:'33:54',inquiry:'23:333',privacy:'95:2',cashiering:'60:2',registrar:'19:175'},
 {theme:'Dark',empty:'36:819',conversation:'40:33',nav:'97:169',navMain:'112:1275',auth:'36:2',logo:'107:1066',upload:'36:850',offices:'36:1766',inquiry:'36:678',privacy:'97:162',cashiering:'60:246',registrar:'36:173'}
];
if(!section||section.type!=='SECTION'||!componentsSection||componentsSection.type!=='SECTION')throw new Error('Verified Seryo/components sections are missing.');
for(const role of ['Hero','Toolbar','Office','Body','Support','Label','Caption'])if(!styles[role])throw new Error('Missing existing GoServe text style: '+role);
function paint(theme,key){const c=collections.find(c=>c.name==='GoServe / '+theme),v=c&&variables.find(v=>v.variableCollectionId===c.id&&v.name==='color/'+key);if(!v)throw new Error('Missing existing color '+theme+'/'+key);const x=v.valuesByMode[c.defaultModeId];return figma.variables.setBoundVariableForPaint({type:'SOLID',color:{r:x.r,g:x.g,b:x.b}},'color',v);}
const menuName=theme=>'GoServe / Seryo tools menu / '+theme;
const screenName=(theme,conversation)=>'GoServe 2.4 • '+(theme==='Dark'?'dark/':'')+'shared/'+(conversation?'ai-response-menu':'assistant-menu');
const plans=[];
for(const def of defs){
 const nodes={};for(const key of ['empty','conversation','nav','navMain','auth','logo','upload','offices','inquiry','privacy','cashiering'])nodes[key]=await figma.getNodeByIdAsync(def[key]);
 for(const key of ['empty','conversation','auth','logo','upload','offices','inquiry','privacy','cashiering'])if(!nodes[key]||pageOf(nodes[key])?.id!==page.id)throw new Error('Verified source/destination missing: '+key+' '+def[key]);
 if(nodes.empty.type!=='FRAME'||nodes.conversation.type!=='FRAME'||(!nodes.nav&&nodes.navMain?.type!=='COMPONENT'))throw new Error('Invalid Seryo root or navigation source for '+def.theme);
 const status=nodes.auth.children.find(n=>n.name==='Android status inset'),gesture=nodes.auth.children.find(n=>n.name==='Android gesture inset');
 if(!status||!gesture)throw new Error('Native Android insets missing for '+def.theme);
 const menu=exact(menuName(def.theme),'COMPONENT'),menuScreens=[false,true].map(conversation=>exact(screenName(def.theme,conversation),'FRAME'));
 for(const key of ['background','surface','text','secondary','border','muted','primary','onPrimary'])paint(def.theme,key);
 plans.push({def,nodes,status,gesture,menu,menuScreens});
}
// Load existing fonts before any text edits, clones, removals or reparenting.
const fonts=new Map();for(const plan of plans)for(const n of [...Object.values(plan.nodes),plan.menu,...plan.menuScreens].filter(Boolean))for(const t of all(n).filter(n=>n.type==='TEXT'))for(const s of t.getStyledTextSegments(['fontName']))fonts.set(JSON.stringify(s.fontName),s.fontName);
for(const style of Object.values(styles))if(style.fontName&&style.fontName!==figma.mixed)fonts.set(JSON.stringify(style.fontName),style.fontName);
await Promise.all([...fonts.values()].map(font=>figma.loadFontAsync(font)));
function box(parent,name,width,gap=0,padding=0,horizontal=false){const n=track(figma.createAutoLayout(horizontal?'HORIZONTAL':'VERTICAL'));n.name=name;n.resize(Math.max(1,width),1);n.primaryAxisSizingMode=horizontal?'FIXED':'AUTO';n.counterAxisSizingMode=horizontal?'AUTO':'FIXED';n.paddingTop=n.paddingRight=n.paddingBottom=n.paddingLeft=padding;n.itemSpacing=gap;n.minWidth=n.minHeight=null;n.fills=[];n.clipsContent=false;parent.appendChild(n);n.layoutSizingHorizontal='FILL';n.layoutSizingVertical='HUG';return n;}
function text(parent,name,value,role,theme,color='text',align='LEFT'){const n=track(figma.createText());n.name=name;n.textStyleId=styles[role].id;n.resize(Math.max(1,parent.width-(parent.paddingLeft||0)-(parent.paddingRight||0)),100);parent.appendChild(n);n.layoutSizingHorizontal='FILL';n.layoutSizingVertical='HUG';n.textAutoResize='HEIGHT';n.characters=value;n.fills=[paint(theme,color)];n.textAlignHorizontal=align;return n;}
const paths={plus:'M12 5V19 M5 12H19',send:'M12 19V5 M6 11L12 5L18 11',close:'M6 6L18 18 M18 6L6 18',chevron:'M9 5L16 12L9 19',file:'M14 3H6A2 2 0 0 0 4 5V19A2 2 0 0 0 6 21H18A2 2 0 0 0 20 19V9L14 3Z M14 3V9H20 M8 14H16 M8 17H13',offices:'M3 21H21 M5 21V8L12 3L19 8V21 M9 21V15H15V21 M8 10H8.01 M12 10H12.01 M16 10H16.01',inquiry:'M21 11.5A8.5 8.5 0 0 1 12.5 20H5L2 23V11.5A8.5 8.5 0 0 1 19 11.5 M8 9H15 M8 13H13',privacy:'M12 3L20 6V11C20 16 17 19 12 21C7 19 4 16 4 11V6L12 3Z M12 8V12 M12 16H12.01'};
function icon(parent,name,path,theme,color='text',size=22){const p=paint(theme,color).color,hex='#'+[p.r,p.g,p.b].map(x=>Math.round(x*255).toString(16).padStart(2,'0')).join('');const n=track(figma.createNodeFromSvg('<svg width="'+size+'" height="'+size+'" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="'+path+'" fill="none" stroke="'+hex+'" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>'));n.name=name;parent.appendChild(n);n.layoutSizingHorizontal=n.layoutSizingVertical='FIXED';for(const v of n.findAll(n=>'strokes'in n&&n.strokes.length))v.strokes=[paint(theme,color)];return n;}
function target(parent,name,theme,path,color='text'){const n=box(parent,name,48);n.resize(48,48);n.primaryAxisSizingMode=n.counterAxisSizingMode='FIXED';n.layoutSizingHorizontal=n.layoutSizingVertical='FIXED';n.primaryAxisAlignItems=n.counterAxisAlignItems='CENTER';n.cornerRadius=24;if(path)icon(n,'Icon / '+name,path,theme,color);return n;}
function route(node,destinationId,owner){node.reactions=destinationId===owner?.id?[]:[{trigger:{type:'ON_CLICK'},actions:[{type:'NODE',destinationId,navigation:'NAVIGATE',transition:null,resetScrollPosition:true}]}];mark(node);routes.push({node:node.id,destination:destinationId});}
function fixedChild(n,width,height){n.resize(width,height);n.layoutPositioning='AUTO';n.layoutSizingHorizontal='FILL';n.layoutSizingVertical='FIXED';n.minWidth=n.minHeight=null;mark(n);}
function navWithin(root){return root.findAll(n=>n.type==='INSTANCE'&&/GoServe \/ Capsule navigation/.test(n.name))[0];}
function insetCopy(source,root){const n=track(source.clone());root.appendChild(n);fixedChild(n,390,24);return n;}
function shell(root,plan,keepNav){
 let nav=keepNav&&navWithin(root);if(nav){page.appendChild(nav);nav.visible=false;mark(nav);}
 for(const child of [...root.children])remove(child);
 root.layoutMode='VERTICAL';root.resize(390,844);root.primaryAxisSizingMode=root.counterAxisSizingMode='FIXED';root.paddingTop=root.paddingRight=root.paddingBottom=root.paddingLeft=root.itemSpacing=0;root.minWidth=root.minHeight=null;root.fills=[paint(plan.def.theme,'background')];root.strokes=[];root.clipsContent=true;root.reactions=[];mark(root);
 const status=insetCopy(plan.status,root);
 for(const child of status.children)if('constraints'in child){child.constraints={horizontal:/system|battery|signal/i.test(child.name)?'MAX':'MIN',vertical:'MIN'};mark(child);}
 if(!nav){nav=plan.nodes.nav?track(plan.nodes.nav.clone()):track(plan.nodes.navMain.createInstance());}
 return{root,status,nav};
}
function buildBase(root,plan,conversation){
 const theme=plan.def.theme,s=shell(root,plan,true),header=box(root,'Seryo / Header',390,8,0,true);header.paddingLeft=header.paddingRight=20;header.paddingTop=header.paddingBottom=8;header.minHeight=64;header.counterAxisAlignItems='CENTER';
 const titles=box(header,'Seryo / Header titles',294,2);text(titles,'Seryo / Toolbar title','Seryo','Toolbar',theme);text(titles,'Seryo / Setup status','eGovAI · Setup pending','Caption',theme,'secondary');
 const privacy=target(header,'Action / Review AI privacy',theme,paths.privacy,'secondary');route(privacy,plan.def.privacy,root);
 const viewport=box(root,'Seryo / Scrollable chat viewport',390);viewport.resize(390,480);viewport.primaryAxisSizingMode=viewport.counterAxisSizingMode='FIXED';viewport.layoutSizingHorizontal=viewport.layoutSizingVertical='FILL';viewport.overflowDirection='VERTICAL';viewport.clipsContent=true;
 const body=box(viewport,'Seryo / Scrollable conversation content',390,24,20);body.paddingTop=body.paddingBottom=24;
 if(!conversation){
  body.name='Seryo / Centered welcome content';body.primaryAxisAlignItems='CENTER';body.counterAxisAlignItems='CENTER';const welcome=box(body,'Seryo / Welcome',350,16);welcome.maxWidth=350;welcome.counterAxisAlignItems='CENTER';
  const brand=track(plan.nodes.logo.clone());welcome.appendChild(brand);brand.name='GoServe logo only';brand.rescale(40/brand.width);brand.layoutSizingHorizontal=brand.layoutSizingVertical='FIXED';
  text(welcome,'Seryo / Welcome heading','How can Seryo help you?','Hero',theme,'text','CENTER');text(welcome,'Seryo / Welcome guidance','Find campus offices, explore services, and understand the next step for a request.','Support',theme,'secondary','CENTER');
 }else{
  function user(value){const row=box(body,'Seryo / Student message alignment',350);row.counterAxisAlignItems='MAX';const bubble=box(row,'Seryo / Student message bubble',282,0,14);bubble.maxWidth=282;bubble.cornerRadius=24;bubble.fills=[paint(theme,'muted')];text(bubble,'Student / '+value,value,'Body',theme);}
  function answer(value,openCashiering){const reply=box(body,'Seryo / Plain guidance reply',350,8);text(reply,'Seryo / Reply speaker','Seryo','Label',theme);text(reply,'Seryo / Guidance answer',value,'Body',theme);text(reply,'Seryo / Source caption','Guidance · Confirm with the office','Caption',theme,'secondary');if(openCashiering){const action=box(reply,'Action / Open Cashiering',350,0,12);action.minHeight=48;action.cornerRadius=24;action.strokes=[paint(theme,'border')];action.strokeWeight=1;action.fills=[];text(action,'Open Cashiering label','Open Cashiering','Label',theme,'text','CENTER');route(action,plan.def.cashiering,root);}}
  user('Which office handles payments?');answer('Cashiering handles payments. The service office declares whether a payment is required before you pay.',true);user('What if my request is free?');answer('A request declared free skips payment. Follow the office’s processing and release steps.',false);
  user('Take me to the Registrar.');const start=body.children.length;answer('Open the Registrar to view its services and current availability.',true);const registrarReply=body.children[start],registrarAction=registrarReply.findOne(n=>n.name==='Action / Open Cashiering');registrarAction.name='Action / Open Registrar';const registrarLabel=registrarAction.findAllWithCriteria({types:['TEXT']})[0];registrarLabel.name='Open Registrar label';registrarLabel.characters='Open Registrar';route(registrarAction,plan.def.registrar,root);
 }
 const composerHost=box(root,'Seryo / Pinned composer host',390,8);composerHost.paddingLeft=composerHost.paddingRight=20;composerHost.paddingTop=composerHost.paddingBottom=8;
 const composer=box(composerHost,'Seryo / Composer • setup pending',350,8,12);composer.cornerRadius=24;composer.fills=[paint(theme,'surface')];composer.strokes=[paint(theme,'border')];composer.strokeWeight=1;
 const draft=box(composer,'Seryo / Question draft • unavailable',326);draft.minHeight=48;draft.reactions=[];text(draft,'Seryo / Question placeholder',conversation?'Ask a follow-up…':'Ask about a campus service…','Body',theme,'secondary');
 const controls=box(composer,'Seryo / Composer controls',326,8,0,true);controls.minHeight=48;controls.counterAxisAlignItems='CENTER';
 const plus=target(controls,'Action / Open GoServe tools',theme,paths.plus),spacer=box(controls,'Seryo / Flexible composer space',214);spacer.resize(214,48);spacer.layoutSizingHorizontal='FILL';spacer.layoutSizingVertical='FIXED';
 const send=target(controls,'Action / Send • disabled',theme,paths.send,'secondary');send.fills=[paint(theme,'muted')];send.reactions=[];
 text(composerHost,'Seryo / No-send setup note','Questions are not sent while setup is pending.','Caption',theme,'secondary','CENTER');
 const navHost=box(root,'Role navigation host',390);navHost.resize(390,78);navHost.primaryAxisSizingMode=navHost.counterAxisSizingMode='FIXED';navHost.layoutSizingHorizontal='FILL';navHost.layoutSizingVertical='FIXED';navHost.paddingTop=16;navHost.paddingBottom=6;navHost.appendChild(s.nav);s.nav.visible=true;fixedChild(s.nav,390,56);
 const gesture=insetCopy(plan.gesture,root);for(const child of gesture.children)if('constraints'in child){child.constraints={horizontal:'CENTER',vertical:'CENTER'};child.x=(gesture.width-child.width)/2;child.y=(gesture.height-child.height)/2;mark(child);}
 if(!conversation)body.minHeight=viewport.height;
 return{root,theme,conversation,header,viewport,body,composerHost,composer,draft,plus,send,navHost,nav:s.nav,gesture};
}
function buildMenu(plan){const theme=plan.def.theme;let component=plan.menu;if(!component){component=track(figma.createComponent());component.name=menuName(theme);componentsSection.appendChild(component);component.x=Math.max(24,...componentsSection.children.filter(n=>n.id!==component.id).map(n=>n.x+n.width))+48;component.y=24;}else for(const c of [...component.children])remove(c);
 component.layoutMode='VERTICAL';component.resize(350,200);component.primaryAxisSizingMode=component.counterAxisSizingMode='FIXED';component.paddingTop=component.paddingRight=component.paddingBottom=component.paddingLeft=12;component.itemSpacing=8;component.minWidth=component.minHeight=null;component.cornerRadius=24;component.fills=[paint(theme,'surface')];component.strokes=[paint(theme,'border')];component.strokeWeight=1;component.clipsContent=true;component.effects=[{type:'DROP_SHADOW',color:{r:0,g:0,b:0,a:theme==='Dark'?0.35:0.14},offset:{x:0,y:8},radius:24,visible:true,blendMode:'NORMAL'}];component.description='Seryo plus menu for deliberate native handoffs. Shared Light/Dark menu contents; each screen instance returns Close to its exact empty/conversation origin. No provider requests or record changes.';mark(component);
 const header=box(component,'GoServe tools / Menu header',326,8,0,true);header.minHeight=48;header.counterAxisAlignItems='CENTER';text(header,'GoServe tools / Menu title','GoServe tools','Office',theme);const close=target(header,'Action / Close GoServe tools',theme,paths.close);close.reactions=[];
 const viewport=box(component,'GoServe tools / Scrollable menu viewport',326);viewport.resize(326,120);viewport.primaryAxisSizingMode=viewport.counterAxisSizingMode='FIXED';viewport.layoutSizingHorizontal=viewport.layoutSizingVertical='FILL';viewport.overflowDirection='VERTICAL';viewport.clipsContent=true;
 const body=box(viewport,'GoServe tools / Menu options',326,4);
 const items=[{key:'Attach a file',sub:'One PDF, JPG or PNG · 10 MB max',icon:'file',to:plan.def.privacy},{key:'AI privacy',icon:'privacy',to:plan.def.privacy}];
 for(const item of items){const row=box(body,'Tool / '+item.key,326,12,6,true);row.minHeight=56;row.paddingLeft=row.paddingRight=8;row.cornerRadius=16;row.counterAxisAlignItems='CENTER';icon(row,'Icon / '+item.key,paths[item.icon],theme,'secondary');const labels=box(row,'Tool labels / '+item.key,252,2);text(labels,item.key+' label',item.key,'Label',theme);if(item.sub)text(labels,'Attachment limits',item.sub,'Caption',theme,'secondary');route(row,item.to);}
 return component;
}
function clonedScreen(source,existing,name){let root=existing;if(root){for(const child of [...root.children])remove(child);root.layoutMode='VERTICAL';root.resize(390,844);root.primaryAxisSizingMode=root.counterAxisSizingMode='FIXED';root.paddingTop=root.paddingRight=root.paddingBottom=root.paddingLeft=root.itemSpacing=0;root.minWidth=root.minHeight=null;root.fills=source.fills;root.strokes=[];root.clipsContent=true;root.reactions=[];for(const child of source.children){const copy=track(child.clone());root.appendChild(copy);if(copy.layoutPositioning!=='ABSOLUTE'){copy.layoutSizingHorizontal=child.layoutSizingHorizontal;copy.layoutSizingVertical=child.layoutSizingVertical;}}mark(root);}else{root=track(source.clone());root.name=name;section.appendChild(root);root.x=Math.max(24,...section.children.filter(n=>n.id!==root.id).map(n=>n.x+n.width))+48;root.y=24;}return root;}
const mainLayouts=[],menus=[];
for(const plan of plans){plan.component=buildMenu(plan);mainLayouts.push(buildBase(plan.nodes.empty,plan,false),buildBase(plan.nodes.conversation,plan,true));}
for(const layout of mainLayouts){const plan=plans.find(p=>p.def.theme===layout.theme),index=layout.conversation?1:0,root=clonedScreen(layout.root,plan.menuScreens[index],screenName(layout.theme,layout.conversation));
 const clonedPlus=root.findOne(n=>n.name==='Action / Open GoServe tools');if(clonedPlus)route(clonedPlus,layout.root.id,root);
 const backdrop=track(figma.createFrame());backdrop.name='GoServe tools / Dismiss backdrop';backdrop.resize(390,844);backdrop.fills=[{type:'SOLID',color:{r:0,g:0,b:0},opacity:layout.theme==='Dark'?0.55:0.32}];backdrop.strokes=[];root.appendChild(backdrop);backdrop.layoutPositioning='ABSOLUTE';backdrop.x=backdrop.y=0;backdrop.constraints={horizontal:'STRETCH',vertical:'STRETCH'};route(backdrop,layout.root.id,root);
 const menu=track(plan.component.createInstance());root.appendChild(menu);menu.name='GoServe tools / '+(layout.conversation?'Conversation':'Welcome')+' menu';menu.layoutPositioning='ABSOLUTE';const available=Math.max(48,layout.composerHost.y-8-32),height=Math.min(200,available);menu.resize(350,height);menu.x=20;menu.y=layout.composerHost.y-8-height;menu.constraints={horizontal:'STRETCH',vertical:'MAX'};
 const close=menu.findOne(n=>n.name==='Action / Close GoServe tools');if(!close)throw new Error('Tools menu Close target missing');route(close,layout.root.id,root);
 route(layout.plus,root.id,layout.root);menus.push({root,origin:layout.root.id,menu,backdrop,close,theme:layout.theme,conversation:layout.conversation,component:plan.component.id});
}
// The fixture warning is outside the phone; it never appears as a provider message.
for(const layout of mainLayouts.filter(x=>x.conversation)){const name='Seryo 2.4 / Fixture annotation / '+layout.theme;let note=exact(name,'TEXT');if(!note){note=track(figma.createText());section.appendChild(note);}note.name=name;note.textStyleId=styles.Caption.id;note.resize(390,80);note.textAutoResize='HEIGHT';note.characters='Project-plan-based fixture · Not a live provider answer. Static guidance example only; eGovAI processing remains unavailable.';note.fills=[paint(layout.theme,'secondary')];note.x=layout.root.x;note.y=layout.root.y-note.height-12;mark(note);annotations.push({node:note.id,root:layout.root.id,text:note.characters});}
function bounds(n){return{x:n.x,y:n.y,w:n.width,h:n.height};}
for(const layout of mainLayouts){const navRoutes=layout.nav.findAll(n=>'reactions'in n&&n.reactions.length).reduce((sum,n)=>sum+n.reactions.length,0);reports.push({id:layout.root.id,name:layout.root.name,theme:layout.theme,kind:layout.conversation?'conversation':'empty',root:bounds(layout.root),header:bounds(layout.header),viewport:bounds(layout.viewport),bodyHeight:layout.body.height,composer:bounds(layout.composerHost),nav:bounds(layout.navHost),gesture:bounds(layout.gesture),plus:layout.plus.id,sendDisabled:layout.send.id,navRoutes});}
for(const m of menus)reports.push({id:m.root.id,name:m.root.name,theme:m.theme,kind:m.conversation?'conversation-menu':'empty-menu',root:bounds(m.root),menu:bounds(m.menu),origin:m.origin,component:m.component,close:m.close.id,backdrop:m.backdrop.id});
mark(section);mark(componentsSection);
return{createdNodeIds:[...created],deletedNodeIds:[...deleted],mutatedNodeIds:[...mutated].filter(id=>!deleted.has(id)),screens:reports,components:plans.map(p=>({id:p.component.id,name:p.component.name,theme:p.def.theme,w:p.component.width,h:p.component.height})),routes,annotations,limits:{screenCount:8,menuComponentCount:2,providerCalls:false,sendEnabled:false,fileSelectionEnabled:false}};


