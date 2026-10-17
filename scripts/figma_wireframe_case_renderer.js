// Inject WIREFRAME_PAGE_ID, WIREFRAME_SECTION_MAP, CASE_SPECS and optional FRAME_MAP.
// This helper creates design fixtures exclusively on the supplied new page.
const wfPage = await figma.getNodeByIdAsync(WIREFRAME_PAGE_ID);
if (!wfPage || wfPage.type !== 'PAGE' || wfPage.id === '14:2') throw new Error('A separate wireframe page is required');
await figma.setCurrentPageAsync(wfPage);
const wfCollections = await figma.variables.getLocalVariableCollectionsAsync();
const wfVariables = await figma.variables.getLocalVariablesAsync();
const wfStyles = await figma.getLocalTextStylesAsync();
const wfCreated = [], wfFrames = [], wfMissing = [];
const wfTrack = n => { wfCreated.push(n.id); return n; };
async function wfLoad(root) {
  const texts = root.type === 'TEXT' ? [root] : root.findAllWithCriteria({types:['TEXT']});
  const fonts = new Map();
  for (const t of texts) for (const s of t.getStyledTextSegments(['fontName'])) fonts.set(JSON.stringify(s.fontName), s.fontName);
  await Promise.all([...fonts.values()].map(f => figma.loadFontAsync(f)));
}
await Promise.all([{family:'Karla',style:'Regular'},{family:'Karla',style:'SemiBold'},{family:'Montserrat',style:'ExtraBold'}].map(f=>figma.loadFontAsync(f)));
function wfPaint(theme,key) {
  const col = wfCollections.find(c=>c.name==='GoServe / '+theme);
  const v = wfVariables.find(v=>v.variableCollectionId===col?.id && v.name==='color/'+key);
  if (!v) throw new Error('Missing GoServe color variable: '+theme+'/'+key);
  const p = v.valuesByMode[col.defaultModeId];
  return figma.variables.setBoundVariableForPaint({type:'SOLID',color:{r:p.r,g:p.g,b:p.b},opacity:p.a===undefined?1:p.a},'color',v);
}
function wfBox(parent,name,width,gap=12,pad=0,horizontal=false) {
  const n=wfTrack(figma.createAutoLayout(horizontal?'HORIZONTAL':'VERTICAL')); n.name=name;
  n.resize(width,100); n.primaryAxisSizingMode='AUTO'; n.counterAxisSizingMode='FIXED';
  n.paddingTop=n.paddingBottom=n.paddingLeft=n.paddingRight=pad; n.itemSpacing=gap; n.fills=[];
  parent.appendChild(n); if(parent.layoutMode && parent.layoutMode!=='NONE')n.layoutSizingHorizontal='FILL';
  return n;
}
async function wfText(parent,value,theme,role='Body',color='text') {
  const n=wfTrack(figma.createText()); n.name=String(value).slice(0,90);
  const s=wfStyles.find(s=>s.name==='GoServe / '+role);
  if(!s)throw new Error('Missing GoServe text style '+role);
  await figma.loadFontAsync(s.fontName); await n.setTextStyleIdAsync(s.id);
  n.fills=[wfPaint(theme,color)]; n.resize(Math.max(1,parent.width-parent.paddingLeft-parent.paddingRight),24);
  parent.appendChild(n); n.textAutoResize='HEIGHT'; n.characters=String(value);
  if(parent.layoutMode && parent.layoutMode!=='NONE'){n.layoutSizingHorizontal='FILL';n.layoutSizingVertical='HUG';}
  return n;
}
function wfRaise(n,theme) {
  n.fills=[wfPaint(theme,'surface')]; n.strokes=[wfPaint(theme,'border')];n.strokeWeight=1;
  n.strokesIncludedInLayout=false; n.cornerRadius=16;
  n.effects=[{type:'DROP_SHADOW',color:{r:0,g:0,b:0,a:theme==='Dark'?0.22:0.08},offset:{x:0,y:3},radius:10,spread:0,visible:true,blendMode:'NORMAL'}];
}
async function wfClone(parent,source) {
  await wfLoad(source); const n=source.clone(); parent.appendChild(n); wfCreated.push(n.id,...n.findAll(()=>true).map(c=>c.id));
  for(const c of [n,...n.findAll(()=>true)])if('reactions' in c)c.reactions=[];
  return n;
}
async function wfAction(parent,theme,a) {
  const n=wfBox(parent,'wf-action:'+a.target+'|'+a.label,350,0,12);
  n.minHeight=48; n.cornerRadius=12; n.primaryAxisAlignItems='CENTER';
  const disabled=a.style==='disabled';
  n.fills=[wfPaint(theme,disabled?'muted':a.style==='primary'?'primary':'surface')];
  n.strokes=a.style==='primary'?[]:[wfPaint(theme,'border')]; n.strokeWeight=1;
  if(a.style==='primary')n.effects=[{type:'DROP_SHADOW',color:{r:0,g:0,b:0,a:0.12},offset:{x:0,y:2},radius:6,spread:0,visible:true,blendMode:'NORMAL'}];
  // Button foreground comes from an existing bound canonical action where available.
  const label=await wfText(n,a.label,theme,'Label',disabled?'secondary':'text');
  if(a.style==='primary') {
    const collection=wfCollections.find(c=>c.name==='GoServe / '+theme);
    const foreground=wfVariables.find(v=>v.variableCollectionId===collection?.id && ['color/onPrimary','color/on-primary','color/primaryText'].includes(v.name));
    if(foreground){const p=foreground.valuesByMode[collection.defaultModeId];label.fills=[figma.variables.setBoundVariableForPaint({type:'SOLID',color:{r:p.r,g:p.g,b:p.b}},'color',foreground)];}
    else { const channel=theme==='Dark'?0:1;label.fills=[{type:'SOLID',color:{r:channel,g:channel,b:channel}}]; }
  }
  label.textAlignHorizontal='CENTER'; n.reactions=[];
  if(disabled)n.name='wf-disabled:'+a.label;
  return n;
}
async function wfPhone(parent,spec,theme) {
  const name='WF / '+spec.key+' / '+theme;
  const mapped=typeof FRAME_MAP!=='undefined'?FRAME_MAP[spec.key]?.[theme]:null;
  let root=mapped?await figma.getNodeByIdAsync(mapped):wfPage.findOne(n=>n.type==='FRAME'&&n.name===name);
  if(root && !wfPage.findOne(n=>n.id===root.id))throw new Error('Refusing to edit a frame outside wireframe page');
  if(!root){root=wfTrack(figma.createFrame());parent.appendChild(root);}
  else {await wfLoad(root);for(const child of [...root.children])child.remove();if(root.parent!==parent)parent.appendChild(root);}
  root.name=name;root.layoutMode='NONE';root.resize(390,844);root.fills=[wfPaint(theme,'background')];root.clipsContent=true;
  root.layoutSizingHorizontal='FIXED';root.layoutSizingVertical='FIXED';
  const source=await figma.getNodeByIdAsync(theme==='Dark'?'145:1708':'145:1706');
  if(!source)throw new Error('Missing canonical phone '+theme);
  for(const insetName of ['Android status inset','Android gesture inset']) {
    const inset=source.children.find(n=>n.name===insetName);
    if(inset){const c=await wfClone(root,inset);c.x=0;c.y=insetName.includes('status')?0:820;}
    else wfMissing.push(theme+':'+insetName);
  }
  const header=wfBox(root,'Native Android app bar',390,0,20,true);header.resize(390,64);
  header.primaryAxisSizingMode='FIXED';header.counterAxisSizingMode='FIXED';header.counterAxisAlignItems='CENTER';header.x=0;header.y=24;
  await wfText(header,spec.toolbar||(spec.key.startsWith('digital_')?'Digital result':spec.key.startsWith('pickup_')?'Pickup':'GoServe'),theme,'Toolbar');
  const viewport=wfBox(root,'Scrollable content · all copy and actions',390,0,0);
  viewport.resize(390,654);viewport.primaryAxisSizingMode='FIXED';viewport.counterAxisSizingMode='FIXED';
  viewport.x=0;viewport.y=88;viewport.clipsContent=true;viewport.overflowDirection='VERTICAL';
  const body=wfBox(viewport,'Full content · vertically hugs',390,20,20);
  await wfText(body,spec.heading,theme,'Section');await wfText(body,spec.body,theme,'Body','secondary');
  const fields=spec.fields||[];
  if(fields.length){
    const card=wfBox(body,'Request information',350,16,16);wfRaise(card,theme);
    for(const field of fields){
      const pair=wfBox(card,'Information / '+field.label,318,4);
      if(/QR placeholder/i.test(field.label)){
        const qr=wfBox(pair,'Nonfunctional pickup QR placeholder',318,8,20);qr.minHeight=160;
        qr.strokes=[wfPaint(theme,'border')];qr.strokeWeight=2;qr.cornerRadius=8;
        await wfText(qr,'Pickup QR',theme,'Label');await wfText(qr,'Design placeholder',theme,'Support','secondary');
        await wfText(qr,'Not scannable',theme,'Support','secondary');
      }else {await wfText(pair,field.label,theme,'Support','secondary');await wfText(pair,field.value,theme,'Body');}
    }
  }
  if((spec.actions||[]).length){const actions=wfBox(body,'Request actions',350,12);for(const a of spec.actions)await wfAction(actions,theme,a);}
  const navSource=source.children.find(n=>/Role navigation|Capsule navigation|navigation/i.test(n.name));
  const navArea=wfBox(root,'Pinned bottom navigation area',390,0,0);navArea.resize(390,78);navArea.primaryAxisSizingMode='FIXED';navArea.x=0;navArea.y=742;
  navArea.fills=[wfPaint(theme,'background')];
  let roleNav=null;
  const staffRole=/staff|office|counselor|faculty|secretary/i.test(spec.role||'');
  const tellerRole=/teller/i.test(spec.role||'');
  if(staffRole||tellerRole){
    let sourcePage=source.parent;while(sourcePage&&sourcePage.type!=='PAGE')sourcePage=sourcePage.parent;
    const set=sourcePage?.findOne(n=>n.type==='COMPONENT_SET'&&n.name==='GoServe / '+(tellerRole?'Teller':'Staff')+' navigation');
    roleNav=set?.children.find(n=>n.name==='Theme='+theme+', Active='+(tellerRole?'Pending':'Queue'));
  }
  if(roleNav){await wfLoad(roleNav);const instance=roleNav.createInstance();navArea.appendChild(instance);wfCreated.push(instance.id,...instance.findAll(()=>true).map(n=>n.id));instance.layoutPositioning='ABSOLUTE';instance.x=12;instance.y=16;instance.resize(366,56);for(const n of [instance,...instance.findAll(()=>true)])if('reactions'in n)n.reactions=[];}
  else if(navSource){const nav=await wfClone(navArea,navSource);nav.layoutPositioning='ABSOLUTE';nav.x=navSource.x;nav.y=16;}
  else {await wfText(navArea,tellerRole?'Search     Pending     History     More':staffRole?'Home     Queue     Schedule     Inbox     More':'Home     Requests     Consult     Inbox     More',theme,'Support','secondary');wfMissing.push(theme+':navigation source');}
  wfFrames.push({key:spec.key,theme,id:root.id,body:body.height,viewport:654,role:spec.role});return root;
}
for(const [index,spec] of CASE_SPECS.entries()) {
  const groupKey=spec.group||spec.groupKey;
  let sectionId=WIREFRAME_SECTION_MAP[groupKey];
  if(!sectionId && Object.keys(WIREFRAME_SECTION_MAP).length===1)sectionId=Object.values(WIREFRAME_SECTION_MAP)[0];
  const section=await figma.getNodeByIdAsync(sectionId);
  let ancestor=section;while(ancestor && ancestor.type!=='PAGE')ancestor=ancestor.parent;
  if(!section || ancestor?.id!==wfPage.id)throw new Error('Section must belong to wireframe page: '+groupKey);
  const themes=typeof WIREFRAME_THEMES!=='undefined'?WIREFRAME_THEMES:['Light','Dark'];
  const bundleWidth=themes.length===1?390:812;
  const bundleName='WF pair / '+spec.key;
  let bundle=section.findOne(n=>n.type==='FRAME'&&n.name===bundleName);
  if(!bundle){bundle=wfBox(section,bundleName,bundleWidth,16,0);bundle.x=32;bundle.y=120+(spec.caseIndex??spec.index??index)*1180;}
  else {await wfLoad(bundle);for(const c of [...bundle.children])if(c.name!=='Theme pair')c.remove();}
  bundle.layoutMode='VERTICAL';bundle.primaryAxisSizingMode='AUTO';bundle.counterAxisSizingMode='FIXED';bundle.fills=[];
  const caption=await wfText(bundle,spec.title+' · '+spec.role,'Light','Section');caption.name='Design caption / '+spec.key;
  const row=bundle.children.find(n=>n.name==='Theme pair')||wfBox(bundle,'Theme pair',bundleWidth,32,0,true);row.primaryAxisSizingMode='FIXED';row.counterAxisSizingMode='AUTO';
  bundle.insertChild(0,caption);bundle.insertChild(1,row);
  for(const theme of themes)await wfPhone(row,spec,theme);
  const notes=wfBox(bundle,'External design notes · not product copy',bundleWidth,6);
  for(const note of spec.notes||[])await wfText(notes,note,'Light','Support','secondary');
}
return {frames:wfFrames,createdNodeIds:wfCreated.join(' '),missing:wfMissing};
