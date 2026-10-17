// Root injects ROOT_IDS. Only the enumerated legacy exceptions are changed.
await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('14:2'));
const page=figma.currentPage,createdNodeIds=[],deletedNodeIds=[],mutated=new Set(),reports=[],roots=[];
const styles=Object.fromEntries((await figma.getLocalTextStylesAsync()).filter(s=>s.name.startsWith('GoServe / ')).map(s=>[s.name.split(' / ')[1],s]));
if(['Office','Support','Label'].some(role=>!styles[role]))return{createdNodeIds,deletedNodeIds,mutatedNodeIds:[],error:'Required GoServe styles missing'};
const all=node=>[node,...('findAll'in node?node.findAll(()=>true):[])],mark=node=>mutated.add(node.id),fonts=new Map();
const isMaterial=t=>t.type==='TEXT'&&t.getStyledTextSegments(['fontName']).some(s=>/Material Symbols|Material Icons/.test(s.fontName.family));
function protectedNode(node,root){let n=node;while(n&&n.id!==root.id){if(n.type==='INSTANCE'||n.type==='COMPONENT'||/^(Android status inset|Android gesture inset)$/.test(n.name))return true;n=n.parent;}return false;}
function roleOf(root){return root.name.includes('teller/')?'Teller':root.name.includes('staff/')?'Staff':null;}
function activeOf(root,role){const n=root.name;return role==='Teller'?/teller\/history/.test(n)?'History':/teller\/more/.test(n)?'More':/teller\/cash/.test(n)?'Search':'Pending':/staff\/(inbox|thread)/.test(n)?'Inbox':/staff\/(more|settings)/.test(n)?'More':/staff\/(guidance|faculty|ccs|schedule)/.test(n)?'Schedule':/staff\/(queue|request|scanner|manual|token|evidence|verified|rejection)/.test(n)?'Queue':'Home';}
for(const id of [...new Set(ROOT_IDS)]){
  const root=await figma.getNodeByIdAsync(id);if(!root||root.type!=='FRAME'||root.parent.id!==page.id){reports.push({id,skipped:'Not a current-page root'});continue;}
  const role=roleOf(root),theme=root.name.includes('dark/')?'Dark':'Light',oldNav=root.children.find(n=>n.type==='FRAME'&&n.name==='Material navigation bar');
  const set=role?page.children.find(n=>n.type==='COMPONENT_SET'&&n.name==='GoServe / '+role+' navigation'):null;
  const active=role?activeOf(root,role):null,component=set?set.children.find(n=>n.type==='COMPONENT'&&n.name.split(',').map(x=>x.trim()).includes('Theme='+theme)&&n.name.split(',').map(x=>x.trim()).includes('Active='+active)):null;
  roots.push({root,role,theme,oldNav,component,active});
  for(const scope of [root,component].filter(Boolean))for(const t of scope.findAllWithCriteria({types:['TEXT']}))for(const s of t.getStyledTextSegments(['fontName']))fonts.set(JSON.stringify(s.fontName),s.fontName);
}
for(const role of ['Office','Support','Label'])fonts.set(JSON.stringify(styles[role].fontName),styles[role].fontName);
const loaded=await Promise.allSettled([...fonts.values()].map(font=>figma.loadFontAsync(font)));
const failedFonts=[...fonts.values()].filter((font,index)=>loaded[index].status==='rejected');
if(failedFonts.length)return{createdNodeIds,deletedNodeIds,mutatedNodeIds:[],failedFonts,reports};
function textFlow(t,parent,role,width){t.textStyleId=styles[role].id;t.resize(Math.max(1,width),Math.max(1,t.height));t.layoutPositioning='AUTO';t.layoutSizingHorizontal='FILL';t.layoutSizingVertical='HUG';t.textAutoResize='HEIGHT';mark(t);}
function column(node,width,padding,gap,minHeight=0){node.layoutMode='VERTICAL';node.resize(width,Math.max(1,node.height));node.primaryAxisSizingMode='AUTO';node.counterAxisSizingMode='FIXED';node.paddingTop=node.paddingRight=node.paddingBottom=node.paddingLeft=padding;node.itemSpacing=gap;node.minHeight=minHeight>0?minHeight:null;node.clipsContent=false;if(node.parent&&'layoutMode'in node.parent&&node.parent.layoutMode!=='NONE')node.layoutSizingVertical='HUG';mark(node);}
for(const item of roots){
  const {root,role,theme,oldNav,component,active}=item,before=new Map(all(root).map(n=>[n.id,[n.parent?.id,n.x,n.y,n.width,n.height]]));
  const report={id:root.id,name:root.name,settings:0,timeChips:0,scanner:0,copy:0,navigation:0,findings:[]};
  try{
    for(const node of root.findAllWithCriteria({types:['FRAME']})){
      if(protectedNode(node,root))continue;
      const direct=node.children.filter(n=>n.type==='TEXT'&&!isMaterial(n));
      if(node.layoutMode==='NONE'&&node.width>=280&&node.children.length===2&&direct.length===2&&direct.some(t=>t.name==='title')&&direct.some(t=>t.name==='detail')){
        column(node,node.width,16,4,64);for(const t of direct)textFlow(t,node,t.name==='title'?'Office':'Support',node.width-32);report.settings++;continue;
      }
      if(/^chip \/ \d{1,2}:\d{2}\s*(AM|PM)$/i.test(node.name)&&direct.length===1&&node.children.length===1){
        const t=direct[0],fill=node.fills,stroke=node.strokes,radius=node.cornerRadius,weight=node.strokeWeight;
        column(node,node.width,0,0,48);node.primaryAxisAlignItems='CENTER';node.counterAxisAlignItems='CENTER';node.fills=[];node.strokes=[];
        const pill=figma.createAutoLayout('VERTICAL');pill.name='Time chip visual pill';pill.fills=fill;pill.strokes=stroke;pill.strokeWeight=weight;pill.cornerRadius=radius;createdNodeIds.push(pill.id);node.appendChild(pill);
        pill.resize(node.width,32);pill.primaryAxisSizingMode='AUTO';pill.counterAxisSizingMode='FIXED';pill.paddingTop=pill.paddingBottom=6;pill.paddingLeft=pill.paddingRight=8;pill.minHeight=32;pill.layoutSizingHorizontal='FILL';pill.layoutSizingVertical='HUG';pill.counterAxisAlignItems='CENTER';pill.clipsContent=false;pill.appendChild(t);textFlow(t,pill,'Label',pill.width-16);t.textAlignHorizontal='CENTER';mark(t);
        const row=node.parent;if(row&&'layoutMode'in row&&row.layoutMode==='HORIZONTAL'){row.counterAxisSizingMode='AUTO';row.minHeight=48;if(row.parent&&'layoutMode'in row.parent&&row.parent.layoutMode!=='NONE')row.layoutSizingVertical='HUG';mark(row);}report.timeChips++;continue;
      }
      if(node.name==='CameraX QR viewfinder'){
        const qr=node.children.find(n=>n.name==='QR target'),hint=direct.find(n=>n.name==='camera hint');
        if(qr&&hint&&node.children.length===2){column(node,node.width,16,12);node.counterAxisAlignItems='CENTER';qr.layoutPositioning='AUTO';qr.resize(174,174);qr.layoutSizingHorizontal=qr.layoutSizingVertical='FIXED';mark(qr);textFlow(hint,node,'Support',node.width-32);hint.textAlignHorizontal='CENTER';mark(hint);report.scanner++;}
      }
    }
    if(['24:2','36:766'].includes(root.id))for(const t of root.findAllWithCriteria({types:['TEXT']})){
      const replacement=t.characters==='Student A · verified demo account'?'Your signed-in account':t.characters==='AI guidance'?'Seryo':t.characters==='Approved knowledge or one temporary file.'?'Campus AI help · Setup pending':null;
      if(replacement){t.characters=replacement;t.name=t.name==='AI guidance'?'Seryo':t.name;if(t.textAutoResize!=='HEIGHT')t.textAutoResize='HEIGHT';mark(t);report.copy++;}
    }
    if(oldNav&&role){
      if(!component)report.findings.push({issue:'Role navigation component missing',role,theme,active});
      else if(!oldNav.children.some(n=>n.type==='INSTANCE'&&n.name==='GoServe / Legacy role navigation / '+role)){
        for(const child of [...oldNav.children]){deletedNodeIds.push(...all(child).map(n=>n.id));child.remove();}
        oldNav.layoutMode='HORIZONTAL';oldNav.resize(390,56);oldNav.primaryAxisSizingMode=oldNav.counterAxisSizingMode='FIXED';oldNav.paddingLeft=oldNav.paddingRight=12;oldNav.paddingTop=oldNav.paddingBottom=0;oldNav.itemSpacing=0;oldNav.fills=[];oldNav.strokes=[];oldNav.clipsContent=false;oldNav.minHeight=56;oldNav.x=0;oldNav.y=758;oldNav.constraints={horizontal:'STRETCH',vertical:'MAX'};mark(oldNav);
        const nav=component.createInstance();nav.name='GoServe / Legacy role navigation / '+role;oldNav.appendChild(nav);nav.resize(366,56);nav.layoutSizingHorizontal='FILL';nav.layoutSizingVertical='FIXED';createdNodeIds.push(...all(nav).map(n=>n.id));
        const targets=role==='Staff'?{Home:'staff/home',Queue:'staff/queue',Schedule:'staff/guidance',Inbox:'staff/inbox',More:'staff/more'}:{Search:'teller/cash',Pending:'teller/pending',History:'teller/history',More:'teller/more'};
        const prefix=theme==='Dark'?'GoServe 2.1 • dark/':'GoServe 2.1 • ';
        for(const tab of nav.findAll(n=>n.name.startsWith('Tab / '))){const suffix=targets[tab.name.slice(6)],destination=suffix?page.children.find(n=>n.type==='FRAME'&&n.name===prefix+suffix):null;tab.reactions=destination&&destination.id!==root.id?[{trigger:{type:'ON_CLICK'},actions:[{type:'NODE',destinationId:destination.id,navigation:'NAVIGATE',transition:null,resetScrollPosition:true}]}]:[];mark(tab);if(!destination)report.findings.push({issue:'Navigation destination missing',label:tab.name,suffix});}
        const viewport=root.children.find(n=>n.name==='Scrollable Android content');if(viewport){viewport.resize(viewport.width,Math.max(48,oldNav.y-16-viewport.y));viewport.primaryAxisSizingMode=viewport.counterAxisSizingMode='FIXED';viewport.overflowDirection='VERTICAL';viewport.clipsContent=true;mark(viewport);}report.navigation++;
      }
    }
  }catch(error){report.error=String(error);report.partialMutation=true;}
  for(const n of all(root)){const old=before.get(n.id),now=[n.parent?.id,n.x,n.y,n.width,n.height];if(old&&old.some((v,i)=>typeof v==='number'?Math.abs(v-now[i])>.01:v!==now[i]))mark(n);}
  reports.push(report);
}
return{createdNodeIds,deletedNodeIds,mutatedNodeIds:[...mutated],reports};
