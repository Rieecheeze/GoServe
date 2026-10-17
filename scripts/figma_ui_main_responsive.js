// Figma-only responsive shell; root executes after the current main-screen builds.
await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('14:2'));
const createdNodeIds=[],mutatedNodeIds=[],deletedNodeIds=[],reports=[],unresolved=[],changed=new Set(),created=new Set();
const roots=await Promise.all(['19:93','36:93','127:1124','127:1329','24:51','36:819'].map(id=>figma.getNodeByIdAsync(id)));
const fonts=new Map();for(const root of roots.filter(Boolean))for(const t of root.findAllWithCriteria({types:['TEXT']}))for(const s of t.getStyledTextSegments(['fontName']))fonts.set(JSON.stringify(s.fontName),s.fontName);
await Promise.all([...fonts.values()].map(f=>figma.loadFontAsync(f)));
const mark=n=>(!created.has(n.id)&&changed.add(n.id),n);
function clearMinimums(n){if('minWidth'in n)n.minWidth=null;if('minHeight'in n)n.minHeight=null;return mark(n);}
function host(parent,name){let n=parent.children.find(n=>n.type==='FRAME'&&n.name===name);if(!n){n=figma.createAutoLayout('VERTICAL');n.name=name;parent.appendChild(n);created.add(n.id);n.fills=[];}n.layoutMode='VERTICAL';n.primaryAxisSizingMode='AUTO';n.counterAxisSizingMode='FIXED';n.itemSpacing=0;n.paddingTop=n.paddingBottom=n.paddingLeft=n.paddingRight=0;n.strokesIncludedInLayout=false;clearMinimums(n);return n;}
function autoChild(n,parent){if(n.parent!==parent)parent.appendChild(n);n.layoutPositioning='AUTO';mark(n);}
function fillWidth(n){n.layoutSizingHorizontal='FILL';mark(n);}
function fixedHeight(n,h){n.resize(n.width,h);n.layoutSizingVertical='FIXED';mark(n);}
function hugHeight(n){n.layoutSizingVertical='HUG';mark(n);}
function named(root,name){return root.findAll(n=>n.name===name)[0]||null;}
function navigation(root){return root.findAll(n=>n.type==='INSTANCE'&&/^GoServe \/ Capsule navigation/.test(n.name))[0]||null;}
function setupRoot(root){root.layoutMode='VERTICAL';root.resize(390,844);root.primaryAxisSizingMode=root.counterAxisSizingMode='FIXED';root.paddingTop=root.paddingBottom=root.paddingLeft=root.paddingRight=root.itemSpacing=0;root.strokesIncludedInLayout=false;root.clipsContent=true;clearMinimums(root);}
function inset(root,name,existing){const n=existing||host(root,name);autoChild(n,root);fillWidth(n);fixedHeight(n,24);if('primaryAxisSizingMode'in n)n.primaryAxisSizingMode='FIXED';if('counterAxisSizingMode'in n)n.counterAxisSizingMode='FIXED';return n;}
function navSlot(root,nav){const n=host(root,'Responsive / Navigation host');autoChild(n,root);fillWidth(n);n.resize(390,78);n.layoutSizingVertical='FIXED';n.primaryAxisSizingMode='FIXED';n.paddingTop=16;n.paddingBottom=6;autoChild(nav,n);clearMinimums(nav);nav.resize(390,56);fillWidth(nav);nav.layoutSizingVertical='FIXED';return n;}
function order(root,slots){for(const slot of slots)root.appendChild(slot);for(const n of root.children)if(!slots.includes(n)){n.layoutPositioning='ABSOLUTE';mark(n);unresolved.push({root:root.id,node:n.id,reason:'Unmanaged root child preserved absolutely'});}}
function report(root,slots,extra={}){const bounds=slots.map(n=>({id:n.id,name:n.name,y:n.y,h:n.height,w:n.width}));const overlap=bounds.slice(1).filter((n,i)=>n.y<bounds[i].y+bounds[i].h-0.5);reports.push({root:root.id,w:root.width,h:root.height,slots:bounds,noOverlap:overlap.length===0&&bounds[0].y>=-0.5&&bounds[bounds.length-1].y+bounds[bounds.length-1].h<=root.height+0.5,...extra});}

for(const root of roots.slice(0,4).filter(Boolean)){
  const header=named(root,'Native Android app bar'),viewport=named(root,'Scrollable Android content'),nav=navigation(root);
  if(!header||!viewport||!nav){unresolved.push({root:root.id,reason:'Home header, viewport or navigation missing'});continue;}
  const status=named(root,'Android status inset'),gesture=named(root,'Android gesture inset');setupRoot(root);
  const top=inset(root,'Responsive / Blank top inset',status);autoChild(header,root);fillWidth(header);header.layoutMode='HORIZONTAL';header.primaryAxisSizingMode='FIXED';header.counterAxisSizingMode='AUTO';hugHeight(header);header.minHeight=64;header.counterAxisAlignItems='CENTER';
  const logo=header.children.find(n=>n.name==='GoServe supplied brand logo'),spacer=header.children.find(n=>n.name==='Flexible masthead spacing'),bell=header.children.find(n=>n.name==='48dp notification target');
  if(logo){clearMinimums(logo);logo.resize(40,40);logo.layoutSizingHorizontal=logo.layoutSizingVertical='FIXED';mark(logo);}
  if(spacer){clearMinimums(spacer);spacer.resize(1,1);spacer.layoutSizingHorizontal='FILL';spacer.layoutSizingVertical='FIXED';mark(spacer);}
  if(bell){clearMinimums(bell);bell.resize(48,48);bell.layoutSizingHorizontal=bell.layoutSizingVertical='FIXED';mark(bell);}
  if(status)for(const n of status.children)if('constraints'in n){n.constraints={horizontal:n.x>200?'MAX':'MIN',vertical:'MIN'};mark(n);}
  if(gesture)for(const n of gesture.children)if('constraints'in n){n.constraints={horizontal:'CENTER',vertical:'CENTER'};mark(n);}
  autoChild(viewport,root);clearMinimums(viewport);fillWidth(viewport);viewport.layoutSizingVertical='FILL';viewport.primaryAxisSizingMode='FIXED';viewport.counterAxisSizingMode='FIXED';viewport.clipsContent=true;viewport.overflowDirection='VERTICAL';
  const body=viewport.children.find(n=>n.name==='Hug content / wraps and scrolls');
  if(body){clearMinimums(body);fillWidth(body);hugHeight(body);for(const n of body.children)if(n.layoutPositioning!=='ABSOLUTE')fillWidth(n);}
  const rows=root.findAll(n=>n.type==='FRAME'&&/^Office grid row /.test(n.name));
  for(const row of rows){row.layoutMode='HORIZONTAL';row.layoutWrap='WRAP';row.primaryAxisSizingMode='FIXED';row.counterAxisSizingMode='AUTO';row.itemSpacing=14;row.counterAxisSpacing=14;row.counterAxisAlignItems='MIN';clearMinimums(row);fillWidth(row);hugHeight(row);
    for(const tile of row.children.filter(n=>n.name.startsWith('Office / '))){clearMinimums(tile);tile.minWidth=140;fillWidth(tile);hugHeight(tile);
      const art=tile.children.find(n=>n.name==='Android native office illustration');if(art){clearMinimums(art);art.resize(art.width,122);fillWidth(art);art.layoutSizingVertical='FIXED';mark(art);for(const child of art.children){if('constraints'in child)child.constraints={horizontal:child.type==='TEXT'?'STRETCH':'SCALE',vertical:child.type==='TEXT'?'MIN':'SCALE'};mark(child);}}
      const details=tile.children.find(n=>n.name==='Office card wraps');if(details){fillWidth(details);hugHeight(details);for(const t of details.findAllWithCriteria({types:['TEXT']})){t.layoutSizingHorizontal='FILL';t.layoutSizingVertical='HUG';t.textAutoResize='HEIGHT';mark(t);}}
    }
  }
  const navHost=navSlot(root,nav),bottom=inset(root,'Responsive / Blank bottom inset',gesture);const slots=[top,header,viewport,navHost,bottom];order(root,slots);report(root,slots,{officeRows:rows.map(r=>({id:r.id,width:r.width,height:r.height,wrap:r.layoutWrap,tiles:r.children.filter(n=>n.name.startsWith('Office / ')).map(n=>({id:n.id,width:n.width,minWidth:n.minWidth}))}))});
}

for(const root of roots.slice(4).filter(Boolean)){
  const header=named(root,'Seryo / Header'),chat=named(root,'Seryo / Chat layout'),nav=navigation(root);
  if(!header||!chat||!nav){unresolved.push({root:root.id,reason:'Seryo header, chat or navigation missing'});continue;}
  setupRoot(root);const top=inset(root,'Responsive / Blank top inset');
  const headerHost=host(root,'Responsive / Seryo header host');autoChild(headerHost,root);fillWidth(headerHost);hugHeight(headerHost);headerHost.paddingLeft=headerHost.paddingRight=20;headerHost.paddingBottom=16;
  autoChild(header,headerHost);clearMinimums(header);fillWidth(header);header.layoutMode='HORIZONTAL';header.primaryAxisSizingMode='FIXED';header.counterAxisSizingMode='AUTO';hugHeight(header);header.minHeight=64;
  const titles=named(header,'Seryo / Header titles');if(titles){clearMinimums(titles);fillWidth(titles);hugHeight(titles);for(const t of titles.findAllWithCriteria({types:['TEXT']})){t.layoutSizingHorizontal='FILL';t.layoutSizingVertical='HUG';t.textAutoResize='HEIGHT';mark(t);}}
  const chatHost=host(root,'Responsive / Seryo chat host');autoChild(chatHost,root);fillWidth(chatHost);chatHost.layoutSizingVertical='FILL';chatHost.primaryAxisSizingMode='FIXED';chatHost.paddingLeft=chatHost.paddingRight=20;
  autoChild(chat,chatHost);clearMinimums(chat);fillWidth(chat);chat.layoutSizingVertical='FILL';chat.primaryAxisSizingMode=chat.counterAxisSizingMode='FIXED';chat.itemSpacing=16;
  const viewport=named(chat,'Seryo / Scrollable welcome viewport'),center=named(chat,'Seryo / Centered welcome fill'),welcome=named(chat,'Seryo / Welcome'),dock=named(chat,'Seryo / Bottom composer dock');
  if(viewport){clearMinimums(viewport);fillWidth(viewport);viewport.layoutSizingVertical='FILL';viewport.primaryAxisSizingMode=viewport.counterAxisSizingMode='FIXED';viewport.clipsContent=true;viewport.overflowDirection='VERTICAL';}
  if(dock){clearMinimums(dock);fillWidth(dock);hugHeight(dock);for(const n of dock.children){fillWidth(n);if(n.type==='TEXT'){n.layoutSizingVertical='HUG';n.textAutoResize='HEIGHT';mark(n);}else if('layoutMode'in n&&n.layoutMode!=='NONE')hugHeight(n);}}
  if(welcome){clearMinimums(welcome);fillWidth(welcome);welcome.maxWidth=310;hugHeight(welcome);for(const t of welcome.findAllWithCriteria({types:['TEXT']})){if(t.parent.layoutMode!=='NONE'){t.layoutSizingHorizontal='FILL';t.layoutSizingVertical='HUG';t.textAutoResize='HEIGHT';mark(t);}}}
  const flexible=named(chat,'Seryo / Flexible composer space');if(flexible){clearMinimums(flexible);fillWidth(flexible);}
  const navHost=navSlot(root,nav),bottom=inset(root,'Responsive / Blank bottom inset');const slots=[top,headerHost,chatHost,navHost,bottom];order(root,slots);
  if(center&&viewport){clearMinimums(center);fillWidth(center);hugHeight(center);center.primaryAxisAlignItems=center.counterAxisAlignItems='CENTER';center.minHeight=viewport.height;mark(center);}
  report(root,slots,{headerId:header.id,chatId:chat.id,welcomeViewport:viewport?{id:viewport.id,w:viewport.width,h:viewport.height}:null,center:center?{id:center.id,h:center.height,minHeight:center.minHeight}:null,welcome:welcome?{id:welcome.id,w:welcome.width,h:welcome.height}:null,dock:dock?{id:dock.id,w:dock.width,h:dock.height}:null});
}
for(const id of created)createdNodeIds.push(id);for(const id of changed)mutatedNodeIds.push(id);
return{createdNodeIds,mutatedNodeIds,deletedNodeIds,reports,unresolved};
