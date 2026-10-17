// Persistence of the verified Figma navigation/office-mark correction; no content rebuild.
await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('14:2'));
const page=figma.currentPage,createdNodeIds=[],mutatedNodeIds=[],deletedNodeIds=[],reports=[],unresolved=[],changed=new Set();
const mains=['19:93','36:93','127:1124','127:1329'];
const requested=typeof ROOT_IDS!=='undefined'&&Array.isArray(ROOT_IDS)?ROOT_IDS:page.findAll(n=>n.type==='FRAME'&&/^GoServe \/ Responsive proof \//.test(n.name)).map(n=>n.id);
const roots=(await Promise.all([...new Set([...mains,...requested])].map(id=>figma.getNodeByIdAsync(id)))).filter(Boolean);
const components=page.findAll(n=>n.type==='COMPONENT'&&/^GoServe \/ Capsule navigation \/ (Home|Requests|Consult|Inbox|AI Chat|More)( \/ Dark)?$/.test(n.name));
const fonts=new Map();for(const root of [...components,...roots])for(const t of root.findAllWithCriteria({types:['TEXT']}))for(const s of t.getStyledTextSegments(['fontName']))fonts.set(JSON.stringify(s.fontName),s.fontName);
await Promise.all([...fonts.values()].map(f=>figma.loadFontAsync(f)));
const mark=n=>(changed.add(n.id),n);
function clearMin(n){n.minWidth=null;n.minHeight=null;mark(n);}
for(const root of components){
  const cap=root.children.find(n=>n.name==='Dark capsule'||n.name==='Capsule'),row=cap&&cap.children.find(n=>n.name==='Destinations');
  if(!cap||!row){unresolved.push({id:root.id,reason:'Capsule or Destinations missing'});continue;}
  root.layoutMode='HORIZONTAL';root.resize(390,56);root.primaryAxisSizingMode=root.counterAxisSizingMode='FIXED';root.paddingLeft=root.paddingRight=12;root.paddingTop=root.paddingBottom=root.itemSpacing=0;root.strokesIncludedInLayout=false;clearMin(root);
  cap.layoutMode='VERTICAL';cap.resize(366,56);cap.primaryAxisSizingMode=cap.counterAxisSizingMode='FIXED';cap.paddingLeft=cap.paddingRight=cap.paddingTop=cap.paddingBottom=4;cap.itemSpacing=0;cap.layoutPositioning='AUTO';cap.strokesIncludedInLayout=false;clearMin(cap);cap.layoutSizingHorizontal='FILL';cap.layoutSizingVertical='FIXED';
  row.layoutMode='HORIZONTAL';row.resize(358,48);row.primaryAxisSizingMode=row.counterAxisSizingMode='FIXED';row.paddingLeft=row.paddingRight=row.paddingTop=row.paddingBottom=row.itemSpacing=0;row.layoutPositioning='AUTO';clearMin(row);row.layoutSizingHorizontal='FILL';row.layoutSizingVertical='FIXED';
  for(const tab of row.children.filter(n=>n.name.startsWith('Tab / '))){
    tab.layoutMode='VERTICAL';tab.resize(tab.width,48);tab.primaryAxisSizingMode=tab.counterAxisSizingMode='FIXED';tab.paddingLeft=tab.paddingRight=tab.paddingTop=tab.paddingBottom=tab.itemSpacing=0;tab.primaryAxisAlignItems=tab.counterAxisAlignItems='CENTER';tab.layoutPositioning='AUTO';clearMin(tab);tab.minWidth=48;tab.layoutSizingHorizontal='FILL';tab.layoutSizingVertical='FIXED';
    const slot=tab.children.find(n=>n.name==='Centered icon slot');if(!slot){unresolved.push({id:tab.id,reason:'Centered icon slot missing'});continue;}
    slot.resize(48,40);slot.layoutSizingHorizontal=slot.layoutSizingVertical='FIXED';mark(slot);
    for(const child of slot.children){if(child.name==='Selected indicator'){child.resize(36,36);child.x=6;child.y=2;mark(child);}else if(child.name.startsWith('Icon / ')){child.resize(22,22);child.x=13;child.y=9;mark(child);}}
  }
  reports.push({component:root.id,name:root.name,width:root.width,height:root.height,capsule:{id:cap.id,w:cap.width,h:cap.height},destinations:{id:row.id,w:row.width,h:row.height},tabs:row.children.filter(n=>n.name.startsWith('Tab / ')).map(n=>({id:n.id,w:n.width,h:n.height,minWidth:n.minWidth}))});
}
for(const root of roots){
  let count=0;for(const art of root.findAll(n=>n.type==='FRAME'&&n.name==='Android native office illustration'))for(const t of art.children.filter(n=>n.type==='TEXT')){t.resize(Math.max(1,art.width-26),Math.max(1,t.height));t.textAutoResize='HEIGHT';t.constraints={horizontal:'STRETCH',vertical:'MIN'};mark(t);count++;}
  if(count)reports.push({root:root.id,name:root.name,officeMarks:count});
}
for(const id of changed)mutatedNodeIds.push(id);
return{createdNodeIds,mutatedNodeIds,deletedNodeIds,componentCount:components.length,rootCount:roots.length,reports,unresolved};
