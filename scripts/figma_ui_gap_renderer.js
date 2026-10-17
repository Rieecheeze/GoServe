// PHASE and STATE_SPECS are supplied by the caller; this script edits Figma only.
await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('14:2'));
const page = figma.currentPage;
const createdNodeIds=[], mutatedNodeIds=[], deletedNodeIds=[], screens=[], unresolved=[];
const styles=Object.fromEntries((await figma.getLocalTextStylesAsync()).filter(s=>s.name.startsWith('GoServe / ')).map(s=>[s.name.split(' / ')[1],s.id]));
const collections=await figma.variables.getLocalVariableCollectionsAsync(),variables=await figma.variables.getLocalVariablesAsync();
await Promise.all([{family:'Montserrat',style:'ExtraBold'},{family:'Karla',style:'Regular'},{family:'Karla',style:'SemiBold'}].map(f=>figma.loadFontAsync(f)));
const record=n=>(createdNodeIds.push(n.id),n);
const prefix=theme=>theme==='Dark'?'GoServe 2.1 • dark/':'GoServe 2.1 • ';
const existing={Light:{'G01.1':'23:285','G01.2':'23:388'},Dark:{'G01.1':'36:630','G01.2':'36:733'}};
function paint(theme,key){const c=collections.find(c=>c.name==='GoServe / '+theme),v=variables.find(v=>v.variableCollectionId===c.id&&v.name==='color/'+key),p=v.valuesByMode[c.defaultModeId];return figma.variables.setBoundVariableForPaint({type:'SOLID',color:{r:p.r,g:p.g,b:p.b}},'color',v);}
function findState(theme,key){const id=existing[theme][key];return id?page.findAll(n=>n.id===id)[0]:page.findOne(n=>n.name===prefix(theme)+'states/'+key);}
function resolve(theme,target){if(!target)return null;if(target.startsWith('@'))return page.findOne(n=>n.name===prefix(theme)+target.slice(1))?.id||null;return findState(theme,target)?.id||null;}
function route(n,theme,target){const id=resolve(theme,target);if(!id){if(target)unresolved.push({node:n.id,target,theme});return;}let owner=n;while(owner.parent&&owner.parent.type!=='PAGE')owner=owner.parent;if(owner.id===id){n.reactions=[];return;}n.reactions=[{trigger:{type:'ON_CLICK'},actions:[{type:'NODE',destinationId:id,navigation:'NAVIGATE',transition:null,resetScrollPosition:true}]}];}
function box(parent,name,width,gap=12,pad=0,horizontal=false){const n=record(figma.createAutoLayout(horizontal?'HORIZONTAL':'VERTICAL'));n.name=name;n.resize(width,100);n.primaryAxisSizingMode=horizontal?'FIXED':'AUTO';n.counterAxisSizingMode=horizontal?'AUTO':'FIXED';n.paddingLeft=n.paddingRight=n.paddingTop=n.paddingBottom=pad;n.itemSpacing=gap;n.fills=[];parent.appendChild(n);if(parent.layoutMode&&parent.layoutMode!=='NONE')n.layoutSizingHorizontal='FILL';return n;}
function text(parent,value,theme,role='Body',color='text'){const n=record(figma.createText());n.name=value.slice(0,70);n.textStyleId=styles[role];n.fills=[paint(theme,color)];n.resize(Math.max(1,parent.width-parent.paddingLeft-parent.paddingRight),100);parent.appendChild(n);if(parent.layoutMode&&parent.layoutMode!=='NONE'){n.layoutSizingHorizontal='FILL';n.layoutSizingVertical='HUG';}n.textAutoResize='HEIGHT';n.characters=value;return n;}
function divider(parent,theme){const n=record(figma.createRectangle());n.name='Section divider';n.resize(parent.width-parent.paddingLeft-parent.paddingRight,1);n.fills=[paint(theme,'border')];parent.appendChild(n);n.layoutSizingHorizontal='FILL';return n;}
const paths={back:'M19 12H5 M11 6L5 12L11 18',check:'M5 12L10 17L20 7',info:'M12 3A9 9 0 1 0 12 21A9 9 0 1 0 12 3 M12 11V16 M12 7H12.01',empty:'M4 5H20V19H4Z M4 13H9L11 16H14L16 13H20',error:'M12 3L22 21H2Z M12 9V14 M12 17H12.01',time:'M12 3A9 9 0 1 0 12 21A9 9 0 1 0 12 3 M12 7V12L16 14'};
function icon(parent,theme,path,size=24,color='text'){const p=paint(theme,color),hex='#'+['r','g','b'].map(k=>Math.round(p.color[k]*255).toString(16).padStart(2,'0')).join('');const n=figma.createNodeFromSvg('<svg width="'+size+'" height="'+size+'" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="'+path+'" fill="none" stroke="'+hex+'" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>');parent.appendChild(n);createdNodeIds.push(n.id,...n.findAll(()=>true).map(n=>n.id));for(const c of n.findAll(()=>true))if('strokes' in c&&c.strokes.length)c.strokes=[p];return n;}
function instance(parent,component){const n=component.createInstance();parent.appendChild(n);createdNodeIds.push(n.id,...n.findAll(()=>true).map(c=>c.id));return n;}
function action(parent,theme,a){const set=page.findOne(n=>n.type==='COMPONENT_SET'&&n.name==='GoServe / State action'),style=a.disabled?'Disabled':a.style||'Primary',c=set.children.find(c=>c.name==='Theme='+theme+', Style='+style);const n=instance(parent,c);n.name='Action / '+a.label+(a.disabled?' / Disabled':'');n.resize(parent.width-parent.paddingLeft-parent.paddingRight,48);n.layoutSizingHorizontal='FILL';n.layoutSizingVertical='HUG';n.findAllWithCriteria({types:['TEXT']})[0].characters=a.label;if(!a.disabled)route(n,theme,a.target);else n.reactions=[];return n;}
function section(parent,theme,s){
  if(s.type==='text'){text(parent,s.text,theme,s.role||'Body',s.role==='Support'?'secondary':'text');return;}
  if(s.type==='field'){
    if(s.label==='Search'){
      const set=page.findOne(n=>n.type==='COMPONENT_SET'&&n.name==='GoServe / Search control');
      const control=instance(parent,set.children.find(c=>c.name==='Theme='+theme));control.name='Search offices or services';control.resize(parent.width-parent.paddingLeft-parent.paddingRight,48);control.layoutSizingHorizontal='FILL';control.findAllWithCriteria({types:['TEXT']})[0].characters=s.value||s.hint||'Search offices or services';return;
    }
    const field=box(parent,'Field / '+s.label,parent.width-parent.paddingLeft-parent.paddingRight,6);text(field,s.label,theme,'Label');
    const input=box(field,'Input / '+s.label,field.width,0,14);input.minHeight=s.multiline?112:48;input.cornerRadius=12;input.fills=[paint(theme,'surface')];input.strokes=[paint(theme,s.error?'text':'border')];input.strokeWeight=s.error?2:1;input.strokesIncludedInLayout=false;
    text(input,s.value||s.hint||'Enter a value',theme,'Body',s.value?'text':'secondary');
    if(s.value&&s.hint)text(field,s.hint,theme,'Support','secondary');if(s.error)text(field,s.error,theme,'Label');return;
  }
  if(s.type==='choice'){
    if(s.label==='Services'){
      const viewport=box(parent,'Horizontal scrolling service filters',parent.width-parent.paddingLeft-parent.paddingRight,0);viewport.resize(parent.width-parent.paddingLeft-parent.paddingRight,48);viewport.primaryAxisSizingMode='FIXED';viewport.clipsContent=true;viewport.overflowDirection='HORIZONTAL';
      const row=box(viewport,'Service filters',346,8,0,true);row.layoutSizingHorizontal='FIXED';row.primaryAxisSizingMode='AUTO';
      const set=page.findOne(n=>n.type==='COMPONENT_SET'&&n.name==='GoServe / Filter control');
      for(const o of s.options){const c=set.children.find(c=>c.name==='Theme='+theme+', State='+(o.selected?'Selected':'Unselected'));const filter=instance(row,c);filter.name='Filter / '+o.label;filter.resize(110,48);filter.findAllWithCriteria({types:['TEXT']})[0].characters=o.label;route(filter,theme,o.target);}return;
    }
    const group=box(parent,'Choices / '+s.label,parent.width-parent.paddingLeft-parent.paddingRight,4);text(group,s.label,theme,'Label');
    for(const o of s.options){const row=box(group,'Choice / '+o.label,group.width,12,0,true);row.minHeight=48;row.paddingTop=row.paddingBottom=8;row.counterAxisAlignItems='CENTER';const mark=record(figma.createEllipse());mark.name=o.selected?'Selected':'Unselected';mark.resize(18,18);mark.fills=o.selected?[paint(theme,'primary')]:[];mark.strokes=[paint(theme,o.selected?'primary':'secondary')];mark.strokeWeight=1.5;row.appendChild(mark);const copy=box(row,'Choice text',group.width-30,2);copy.layoutSizingHorizontal='FILL';text(copy,o.label,theme,'Label');if(o.detail)text(copy,o.detail,theme,'Support','secondary');if(o.target)route(row,theme,o.target);}return;
  }
  if(s.type==='status'){
    const row=box(parent,'Status / '+s.title,parent.width-parent.paddingLeft-parent.paddingRight,12,0,true);row.counterAxisAlignItems='MIN';
    icon(row,theme,s.tone==='success'?paths.check:s.tone==='error'?paths.error:paths.info,24,s.tone==='success'?'success':'text');
    const copy=box(row,'Growing status copy',row.width-36,4);copy.layoutSizingHorizontal='FILL';text(copy,s.title,theme,'Label');if(s.body)text(copy,s.body,theme,'Support','secondary');return;
  }
  if(s.type==='summary'){
    const summary=box(parent,'Summary / '+s.title,parent.width-parent.paddingLeft-parent.paddingRight,12,16);summary.fills=[paint(theme,'surface')];summary.strokes=[paint(theme,'border')];summary.strokeWeight=1;summary.strokesIncludedInLayout=false;summary.cornerRadius=16;
    if(s.title)text(summary,s.title,theme,'Office');if(s.body)text(summary,s.body,theme,'Support','secondary');for(const row of s.rows||[]){const pair=box(summary,'Information / '+row.label,summary.width-32,2);text(pair,row.label,theme,'Support','secondary');text(pair,row.value||'—',theme,'Body');}return;
  }
  throw new Error('Unknown section '+s.type);
}
async function loadCurrent(root){const fonts=new Map();for(const t of root.findAllWithCriteria({types:['TEXT']}))for(const s of t.getStyledTextSegments(['fontName']))fonts.set(JSON.stringify(s.fontName),s.fontName);await Promise.all([...fonts.values()].map(f=>figma.loadFontAsync(f)));}
async function navigation(root,theme,spec){
  if(spec.nav==='none')return;
  let c;
  if(['Staff','Teller'].includes(spec.nav)){
    const set=page.findOne(n=>n.type==='COMPONENT_SET'&&n.name==='GoServe / '+spec.nav+' navigation');
    const active=spec.nav==='Staff'?(spec.group==='G05'||spec.group==='G13'?'Home':spec.group==='G14'?'Inbox':'Queue'):(spec.group==='G01'&&spec.key==='G01.9'?'History':'Pending');
    c=set.children.find(c=>c.name==='Theme='+theme+', Active='+active);
  }else{c=page.findOne(n=>n.type==='COMPONENT'&&n.name==='GoServe / Capsule navigation / '+spec.nav+(theme==='Dark'?' / Dark':''));}
  if(!c)throw new Error('Missing navigation for '+spec.nav+' '+theme);
  const n=instance(root,c);n.name='GoServe / Role navigation / '+spec.nav;const roleNav=['Staff','Teller'].includes(spec.nav);n.x=roleNav?12:0;n.y=758;n.resize(roleNav?366:390,56);n.constraints={horizontal:'STRETCH',vertical:'MAX'};
  const routes=spec.nav==='Staff'?{Home:'@staff/home',Queue:'@staff/queue',Schedule:'@staff/guidance',Inbox:'@staff/inbox',More:'@staff/more'}:spec.nav==='Teller'?{Search:'@teller/cash',Pending:'@teller/pending',History:'@teller/history',More:'@teller/more'}:{Home:'@student/home',Requests:'@student/requests',Consult:'@student/consult',Inbox:'@student/inbox','AI Chat':'@shared/assistant',More:'@student/more'};
  for(const tab of n.findAll(n=>n.name.startsWith('Tab / ')))route(tab,theme,routes[tab.name.slice(6)]);
}
async function render(root,theme,spec){
  await loadCurrent(root);for(const n of [...root.children]){deletedNodeIds.push(n.id,...('findAll' in n?n.findAll(()=>true).map(n=>n.id):[]));n.remove();}
  root.layoutMode='NONE';root.fills=[paint(theme,'background')];root.clipsContent=true;mutatedNodeIds.push(root.id);
  const home=await figma.getNodeByIdAsync(theme==='Dark'?'36:93':'19:93');
  for(const name of ['Android status inset','Android gesture inset']){const source=home.children.find(n=>n.name===name);if(source){await loadCurrent(source);const clone=source.clone();root.appendChild(clone);createdNodeIds.push(clone.id,...clone.findAll(()=>true).map(n=>n.id));}}
  const header=box(root,'Native Android app bar',390,8,12,true);header.resize(390,64);header.primaryAxisSizingMode=header.counterAxisSizingMode='FIXED';header.x=0;header.y=24;header.counterAxisAlignItems='CENTER';header.constraints={horizontal:'STRETCH',vertical:'MIN'};
  const back=box(header,'Action / Back',48,0,0,true);back.resize(48,48);back.layoutSizingHorizontal='FIXED';back.primaryAxisSizingMode=back.counterAxisSizingMode='FIXED';back.primaryAxisAlignItems=back.counterAxisAlignItems='CENTER';icon(back,theme,paths.back);route(back,theme,spec.back);
  const toolbar=spec.group==='G12'?'Seryo':spec.kind==='empty'?spec.title:spec.group==='G04'?'New request':spec.group==='G05'?'Service fee':spec.group==='G06'?'Request details':spec.group==='G07'||spec.group==='G08'?'Payment':spec.group==='G09'?'Consultation':spec.group==='G10'?'Files':spec.group==='G11'?'Pickup':spec.group==='G14'?'Inquiries':spec.title;
  text(header,toolbar,theme,'Toolbar');
  const viewport=box(root,'Scrollable Android content',390,0,0);viewport.resize(390,spec.kind==='chat'?500:spec.nav==='none'?724:654);viewport.primaryAxisSizingMode=viewport.counterAxisSizingMode='FIXED';viewport.x=0;viewport.y=88;viewport.clipsContent=true;viewport.overflowDirection='VERTICAL';viewport.constraints={horizontal:'STRETCH',vertical:'STRETCH'};
  const body=box(viewport,'Hug content / wraps and scrolls',390,20,20);body.layoutSizingHorizontal='FILL';
  if(spec.planned){const note=box(body,'Planned preview',350,0,10);note.fills=[paint(theme,'muted')];note.cornerRadius=12;text(note,'Planned preview · Service not enabled',theme,'Caption','secondary');}
  if(spec.kind==='empty'){const art=box(body,'Empty state marker',350,0);art.paddingTop=32;icon(art,theme,paths.empty,40,'secondary');}
  const firstStop=spec.body.indexOf('. '),emptyHeading=spec.kind==='empty'&&firstStop>0?spec.body.slice(0,firstStop):null;
  text(body,emptyHeading||spec.title,theme,spec.kind==='empty'?'Hero':'Section');const bodyCopy=emptyHeading?spec.body.slice(firstStop+2):spec.body;if(bodyCopy)text(body,bodyCopy,theme,'Body','secondary');
  if(spec.kind==='loading')icon(body,theme,paths.time,28,'secondary');
  for(const s of spec.sections||[])section(body,theme,s);
  if((spec.actions||[]).length){const actions=box(body,'Context actions',350,10);for(const a of spec.actions)action(actions,theme,a);}
  if(spec.kind==='chat'){
    const source=await figma.getNodeByIdAsync(theme==='Dark'?'123:1183':'123:1149');const composer=source.clone();root.appendChild(composer);composer.name='Seryo / Composer disabled in planned state';composer.x=20;composer.y=612;composer.resize(350,130);createdNodeIds.push(composer.id,...composer.findAll(()=>true).map(n=>n.id));for(const n of composer.findAll(n=>n.reactions?.length))n.reactions=[];
  }
  await navigation(root,theme,spec);
  const label=page.findOne(n=>n.name==='Design annotation / '+theme+' / '+spec.key);if(label){await figma.loadFontAsync(label.fontName);label.characters=spec.key+' · '+spec.title+(spec.planned?' · Planned':'')+'\n'+spec.annotation;mutatedNodeIds.push(label.id);}
  screens.push({key:spec.key,theme,id:root.id,title:spec.title,contentHeight:body.height,viewportHeight:viewport.height,planned:spec.planned});
}
if(PHASE==='create'){
  for(const [index,spec]of STATE_SPECS.entries())for(const [themeIndex,theme]of ['Light','Dark'].entries()){
    let root=findState(theme,spec.key);
    if(root&&existing[theme][spec.key]){
      const suffix=spec.key==='G01.1'?'student/inbox-existing-data':'student/notifications-existing-data';
      if(!page.findOne(n=>n.name===prefix(theme)+suffix)){await loadCurrent(root);const clone=root.clone();clone.name=prefix(theme)+suffix;clone.x=9500+themeIndex*450;clone.y=14500+(spec.key==='G01.1'?0:980);createdNodeIds.push(clone.id,...clone.findAll(()=>true).map(n=>n.id));}
    }
    const position=index+(typeof STATE_START_INDEX==='number'?STATE_START_INDEX:0);
    const x=40+(position%4)*960+themeIndex*450,y=14600+Math.floor(position/4)*1040;
    if(!root){root=record(figma.createFrame());root.name=prefix(theme)+'states/'+spec.key;root.resize(390,844);root.x=x;root.y=y;root.fills=[paint(theme,'background')];root.clipsContent=true;}
    let annotation=page.findOne(n=>n.name==='Design annotation / '+theme+' / '+spec.key);
    if(!annotation){annotation=record(figma.createText());annotation.name='Design annotation / '+theme+' / '+spec.key;annotation.textStyleId=styles.Caption;annotation.resize(390,100);annotation.textAutoResize='HEIGHT';annotation.characters=spec.key+' · '+spec.title+(spec.planned?' · Planned':'');annotation.fills=[{type:'SOLID',color:{r:0.25,g:0.25,b:0.25}}];annotation.x=root.x;annotation.y=root.y-110;}
    screens.push({key:spec.key,theme,id:root.id,title:spec.title});
  }
}else if(PHASE==='render'){
  for(const spec of STATE_SPECS)for(const theme of ['Light','Dark']){const root=findState(theme,spec.key);if(!root)throw new Error('Create state first '+spec.key);await render(root,theme,spec);}
}
return {createdNodeIds,mutatedNodeIds,deletedNodeIds,screens,unresolved};


