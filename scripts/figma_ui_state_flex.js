// Root injects ROOT_IDS for new-state shells only.
await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('14:2'));
const createdNodeIds=[],mutated=new Set(),reports=[],roots=[];
const protectedIds=new Set(['19:93','36:93','127:1124','127:1329','24:51','36:819']);
const all=node=>[node,...node.findAll(()=>true)],mark=node=>mutated.add(node.id);
const fonts=new Map();
for(const id of [...new Set(ROOT_IDS)]){
  const root=await figma.getNodeByIdAsync(id);
  if(!root||root.type!=='FRAME'||root.parent.id!=='14:2'||protectedIds.has(id)){reports.push({id,skipped:'Not an eligible new-state root'});continue;}
  const status=root.children.find(n=>n.name==='Android status inset'),header=root.children.find(n=>n.name==='Native Android app bar'),viewport=root.children.find(n=>n.name==='Scrollable Android content'),gesture=root.children.find(n=>n.name==='Android gesture inset');
  if(!status||!header||!viewport||!gesture){reports.push({id,skipped:'Missing expected new-state shell parts'});continue;}
  const navHost=root.children.find(n=>n.name==='Role navigation host'),composerHost=root.children.find(n=>n.name==='Seryo composer host');
  const nav=navHost?navHost.children.find(n=>n.name.startsWith('GoServe / Role navigation /')):root.children.find(n=>n.name.startsWith('GoServe / Role navigation /'));
  const composer=composerHost?composerHost.children.find(n=>n.name==='Seryo / Composer disabled in planned state'):root.children.find(n=>n.name==='Seryo / Composer disabled in planned state');
  const known=new Set([status,header,viewport,gesture,navHost,composerHost,nav,composer].filter(Boolean).map(n=>n.id));
  if(root.children.some(n=>!known.has(n.id))){reports.push({id,skipped:'Unexpected direct child; shell left unchanged'});continue;}
  roots.push({root,status,header,viewport,gesture,nav,composer,navHost,composerHost});
  for(const t of root.findAllWithCriteria({types:['TEXT']}))for(const s of t.getStyledTextSegments(['fontName']))fonts.set(JSON.stringify(s.fontName),s.fontName);
}
const loaded=await Promise.allSettled([...fonts.values()].map(font=>figma.loadFontAsync(font)));
const failedFonts=[...fonts.values()].filter((font,index)=>loaded[index].status==='rejected');
if(failedFonts.length)return{createdNodeIds,mutatedNodeIds:[],failedFonts,reports};
function fixed(node,width,height){node.resize(width,height);node.layoutSizingHorizontal='FILL';node.layoutSizingVertical='FIXED';node.minWidth=null;mark(node);}
function host(root,name){const node=figma.createAutoLayout('VERTICAL');node.name=name;node.fills=[];root.appendChild(node);createdNodeIds.push(node.id);return node;}
function flowText(text,width){text.resize(Math.max(1,width),Math.max(1,text.height));text.layoutPositioning='AUTO';text.layoutSizingHorizontal='FILL';text.layoutSizingVertical='HUG';text.textAutoResize='HEIGHT';mark(text);}
for(const item of roots){
  const {root,status,header,viewport,gesture,nav,composer}=item;
  const before=new Map(all(root).map(n=>[n.id,[n.parent?.id,n.x,n.y,n.width,n.height]]));
  const report={id:root.id,name:root.name,findings:[]};
  try{
    root.layoutMode='VERTICAL';root.resize(390,844);root.primaryAxisSizingMode=root.counterAxisSizingMode='FIXED';root.paddingTop=root.paddingRight=root.paddingBottom=root.paddingLeft=0;root.itemSpacing=0;root.clipsContent=true;root.strokesIncludedInLayout=false;mark(root);
    const ordered=[status,header,viewport];
    if(composer){
      const h=item.composerHost||host(root,'Seryo composer host');if(composer.parent.id!==h.id){h.appendChild(composer);mark(composer);}
      h.resize(390,154);h.layoutMode='VERTICAL';h.primaryAxisSizingMode='AUTO';h.counterAxisSizingMode='FIXED';h.minHeight=154;h.minWidth=null;h.paddingLeft=h.paddingRight=20;h.paddingTop=h.paddingBottom=12;h.itemSpacing=0;h.fills=[];h.clipsContent=false;
      composer.resize(350,Math.max(130,composer.height));composer.minHeight=130;composer.minWidth=null;composer.layoutSizingHorizontal='FILL';composer.layoutSizingVertical=composer.layoutMode&&composer.layoutMode!=='NONE'?'HUG':'FIXED';mark(composer);mark(h);ordered.push(h);
    }
    if(nav){
      const h=item.navHost||host(root,'Role navigation host');if(nav.parent.id!==h.id){h.appendChild(nav);mark(nav);}
      const role=/\/ (Staff|Teller)$/.test(nav.name);h.resize(390,78);h.layoutMode='VERTICAL';h.primaryAxisSizingMode=h.counterAxisSizingMode='FIXED';h.paddingTop=16;h.paddingBottom=6;h.paddingLeft=h.paddingRight=role?12:0;h.itemSpacing=0;h.fills=[];h.clipsContent=false;h.minWidth=null;
      nav.resize(role?366:390,56);nav.layoutSizingHorizontal='FILL';nav.layoutSizingVertical='FIXED';nav.minWidth=null;mark(nav);mark(h);ordered.push(h);
    }
    ordered.push(gesture);for(let i=0;i<ordered.length;i++){root.insertChild(i,ordered[i]);ordered[i].layoutPositioning='AUTO';ordered[i].layoutSizingHorizontal='FILL';ordered[i].minWidth=null;mark(ordered[i]);}
    fixed(status,390,24);fixed(gesture,390,24);
    header.layoutMode='HORIZONTAL';header.resize(390,64);header.primaryAxisSizingMode='FIXED';header.counterAxisSizingMode='AUTO';header.layoutSizingHorizontal='FILL';header.layoutSizingVertical='HUG';header.minHeight=64;header.minWidth=null;header.paddingTop=header.paddingBottom=8;header.itemSpacing=8;header.counterAxisAlignItems='CENTER';header.clipsContent=false;mark(header);
    const labels=header.children.filter(n=>n.type==='TEXT'),controls=header.children.filter(n=>n.type!=='TEXT');
    for(const n of controls){n.resize(48,48);n.layoutPositioning='AUTO';n.layoutSizingHorizontal=n.layoutSizingVertical='FIXED';mark(n);}
    const available=header.width-header.paddingLeft-header.paddingRight-controls.length*48-header.itemSpacing*Math.max(0,header.children.length-1);
    for(const t of labels)flowText(t,available/Math.max(1,labels.length));
    viewport.layoutMode='VERTICAL';viewport.resize(390,1);viewport.primaryAxisSizingMode=viewport.counterAxisSizingMode='FIXED';viewport.minHeight=null;viewport.minWidth=null;viewport.layoutSizingHorizontal=viewport.layoutSizingVertical='FILL';viewport.clipsContent=true;viewport.overflowDirection='VERTICAL';mark(viewport);
    for(const body of viewport.children){
      if(body.type==='FRAME'&&body.layoutMode!=='NONE'){body.resize(390-viewport.paddingLeft-viewport.paddingRight,Math.max(1,body.height));body.primaryAxisSizingMode='AUTO';body.counterAxisSizingMode='FIXED';body.minWidth=null;body.layoutSizingHorizontal='FILL';body.layoutSizingVertical='HUG';body.clipsContent=false;mark(body);}
    }
    for(const n of status.children){if('constraints'in n){n.constraints={horizontal:/system|battery|signal|LTE/i.test(n.name)?'MAX':'MIN',vertical:'MIN'};mark(n);}}
    for(const n of gesture.children){if('constraints'in n){n.constraints={horizontal:'CENTER',vertical:'CENTER'};n.x=(gesture.width-n.width)/2;n.y=(gesture.height-n.height)/2;mark(n);}}
    const slots=ordered.map(n=>({id:n.id,name:n.name,y:n.y,height:n.height,bottom:n.y+n.height}));
    for(let i=1;i<slots.length;i++)if(slots[i].y<slots[i-1].bottom-0.5)report.findings.push({issue:'Vertical overlap',a:slots[i-1].id,b:slots[i].id});
    if(viewport.height<1)report.findings.push({issue:'No usable viewport height'});
    if(Math.abs(gesture.y+gesture.height-root.height)>0.5)report.findings.push({issue:'Gesture does not end at root bottom'});
    report.slots=slots;report.noOverlap=!report.findings.some(f=>f.issue==='Vertical overlap');
  }catch(error){report.error=String(error);report.partialMutation=true;}
  for(const n of all(root)){const old=before.get(n.id),now=[n.parent?.id,n.x,n.y,n.width,n.height];if(old&&old.some((v,i)=>typeof v==='number'?Math.abs(v-now[i])>0.01:v!==now[i]))mark(n);}
  if(!report.error&&!report.findings.length){report.geometry={headerHeight:header.height,viewportHeight:viewport.height,navHeight:nav?nav.height:0};delete report.slots;}
  reports.push(report);
}
return{createdNodeIds,mutatedNodeIds:[...mutated],reports};
