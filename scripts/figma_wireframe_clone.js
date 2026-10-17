// Inputs: WIREFRAME_PAGE_ID, SECTION_MAP, SOURCE_BATCH. Only the separate wireframe page is edited.
const page=await figma.getNodeByIdAsync(WIREFRAME_PAGE_ID);
if(!page||page.type!=='PAGE'||page.id==='14:2')throw new Error('A separate wireframe page is required');
await figma.setCurrentPageAsync(page);
const created=[],mutated=[],frames=[],sourceSignatures=[],styles=await figma.getLocalTextStylesAsync();
const labelStyle=styles.find(s=>s.name==='GoServe / Office'),bodyStyle=styles.find(s=>s.name==='GoServe / Support');
const sources=[];const fonts=new Map();
function fingerprint(root){const value=JSON.stringify([root.parent.id,...[root,...root.findAll(()=>true)].map(n=>[n.id,n.name,n.x,n.y,n.width,n.height,n.type==='TEXT'?n.characters:null,'reactions'in n?n.reactions:null])]);let h=2166136261;for(let i=0;i<value.length;i++)h=Math.imul(h^value.charCodeAt(i),16777619);return(h>>>0).toString(16);}
for(const spec of SOURCE_BATCH){for(const screen of spec.screens){const source=await figma.getNodeByIdAsync(screen.id);if(!source||source.type!=='FRAME')throw new Error('Missing source '+screen.id);sources.push({spec,screen,source});sourceSignatures.push({id:source.id,hash:fingerprint(source)});for(const t of source.findAllWithCriteria({types:['TEXT']}))for(const s of t.getStyledTextSegments(['fontName']))fonts.set(JSON.stringify(s.fontName),s.fontName);}}
for(const s of [labelStyle,bodyStyle])fonts.set(JSON.stringify(s.fontName),s.fontName);
await Promise.all([...fonts.values()].map(f=>figma.loadFontAsync(f)));
function text(parent,name,value,style,width){const n=figma.createText();created.push(n.id);n.name=name;parent.appendChild(n);n.textStyleId=style.id;n.characters=value;n.resize(width,24);n.textAutoResize='HEIGHT';n.fills=[{type:'SOLID',color:{r:.1,g:.1,b:.1}}];return n;}
for(const spec of SOURCE_BATCH){
 const section=await figma.getNodeByIdAsync(SECTION_MAP[spec.group]);if(!section||section.type!=='SECTION'||section.parent.id!==page.id)throw new Error('Wrong section '+spec.group);
 const name='WF pair / '+spec.key;let pair=section.children.find(n=>n.type==='FRAME'&&n.name===name);
 if(!pair){pair=figma.createAutoLayout('HORIZONTAL');section.appendChild(pair);pair.name=name;pair.fills=[];pair.itemSpacing=32;pair.counterAxisAlignItems='MIN';created.push(pair.id);}
 pair.x=48;pair.y=120+spec.index*1280;
 for(const screen of spec.screens){
  const source=sources.find(x=>x.spec.key===spec.key&&x.screen.id===screen.id).source;
  const colName='WF column / '+screen.theme;let col=pair.children.find(n=>n.type==='FRAME'&&n.name===colName);
  if(!col){col=figma.createAutoLayout('VERTICAL');pair.appendChild(col);col.name=colName;col.fills=[];col.itemSpacing=12;col.resize(390,100);col.primaryAxisSizingMode='AUTO';col.counterAxisSizingMode='FIXED';created.push(col.id);}
  let frame=col.children.find(n=>n.type==='FRAME'&&n.name==='WF / '+spec.key+' / '+screen.theme);
  if(!frame){
   const heading=text(col,'Wireframe caption',spec.title+' · '+screen.theme,labelStyle,390);
   frame=source.clone();col.appendChild(frame);frame.name='WF / '+spec.key+' / '+screen.theme;created.push(frame.id,...frame.findAll(()=>true).map(n=>n.id));
   text(col,'Wireframe implementation note',spec.note||'Editable layout reference. Interactive routes show design states; database and provider operations require implementation and verification.',bodyStyle,390);
   if(screen.noteIds?.length){const notes=[];for(const id of screen.noteIds){const n=await figma.getNodeByIdAsync(id);if(n&&n.type==='TEXT')notes.push(n.characters);}if(notes.length)text(col,'Existing workflow notes',notes.join('\n'),bodyStyle,390);}
  }
  frames.push({key:spec.key,group:spec.group,theme:screen.theme,id:frame.id,sourceId:screen.id,pairId:pair.id});
 }
 mutated.push(pair.id,section.id);
}
return {createdNodeIds:created.join(' '),mutatedNodeIds:[...new Set(mutated)],frames,sourceSignatures};
