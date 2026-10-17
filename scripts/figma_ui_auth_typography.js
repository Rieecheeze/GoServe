// Root injects ROOT_IDS. Preserve the existing ten authentication prototype flows.
await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('14:2'));
const allowed=new Set(['56:38','56:66','58:2','58:22','58:42','58:62','58:82','58:102','58:122','58:142']);
const createdNodeIds=[],mutated=new Set(),brandExceptionIds=[],reports=[],roots=[],fonts=new Map();
const styles=Object.fromEntries((await figma.getLocalTextStylesAsync()).filter(s=>s.name.startsWith('GoServe / ')).map(s=>[s.name.split(' / ')[1],s]));
const needed=['Hero','Toolbar','Office','Body','Support','Label','Caption'];
if(needed.some(role=>!styles[role]))return{createdNodeIds,mutatedNodeIds:[],brandExceptionIds,error:'GoServe authentication styles missing'};
const all=n=>[n,...('findAll'in n?n.findAll(()=>true):[])],mark=n=>mutated.add(n.id);
function pageOf(node){let n=node;while(n&&n.type!=='PAGE')n=n.parent;return n;}
for(const id of [...new Set(ROOT_IDS)]){
  const root=await figma.getNodeByIdAsync(id);
  if(!allowed.has(id)||!root||root.type!=='FRAME'||pageOf(root)?.id!=='14:2'){reports.push({id,skipped:'Not an allowlisted authentication root'});continue;}
  roots.push(root);for(const t of root.findAllWithCriteria({types:['TEXT']}))for(const s of t.getStyledTextSegments(['fontName']))fonts.set(JSON.stringify(s.fontName),s.fontName);
}
for(const role of needed)fonts.set(JSON.stringify(styles[role].fontName),styles[role].fontName);
const loaded=await Promise.allSettled([...fonts.values()].map(font=>figma.loadFontAsync(font))),failedFonts=[...fonts.values()].filter((font,i)=>loaded[i].status==='rejected');
if(failedFonts.length)return{createdNodeIds,mutatedNodeIds:[],brandExceptionIds,failedFonts,reports};
function make(parent,name,mode='VERTICAL'){const n=figma.createAutoLayout(mode);n.name=name;n.fills=[];parent.appendChild(n);createdNodeIds.push(n.id);return n;}
function col(n,padY,padX,gap,minHeight=null){n.layoutMode='VERTICAL';n.resize(n.width,Math.max(1,n.height));n.primaryAxisSizingMode='AUTO';n.counterAxisSizingMode='FIXED';n.paddingTop=n.paddingBottom=padY;n.paddingLeft=n.paddingRight=padX;n.itemSpacing=gap;n.minHeight=minHeight;n.minWidth=null;n.clipsContent=false;if(n.parent&&'layoutMode'in n.parent&&n.parent.layoutMode!=='NONE')n.layoutSizingVertical='HUG';mark(n);}
function flow(t,width,role){if(role)t.textStyleId=styles[role].id;t.resize(Math.max(1,width),Math.max(1,t.height));t.layoutPositioning='AUTO';t.layoutSizingHorizontal='FILL';t.layoutSizingVertical='HUG';t.textAutoResize='HEIGHT';mark(t);}
function skip(t,root){let n=t;while(n&&n.id!==root.id){if(/^(Android status inset|Android gesture inset)$/.test(n.name))return true;if(n.name==='Google monogram'){brandExceptionIds.push(t.id);return true;}n=n.parent;}return t.getStyledTextSegments(['fontName']).some(s=>/Material Symbols|Material Icons/.test(s.fontName.family));}
function roleFor(t){return t.name==='entry headline'?'Hero':t.name==='screen title'?'Toolbar':t.name==='placeholder'?'Body':t.name==='title'?'Office':t.name==='divider label'?'Caption':/^(label|account role)$/.test(t.name)?'Label':'Support';}
for(const root of roots){
  const before=new Map(all(root).map(n=>[n.id,[n.parent?.id,n.x,n.y,n.width,n.height]])),report={id:root.id,name:root.name,roles:{},findings:[]};
  try{
    const status=root.children.find(n=>n.name==='Android status inset'),gesture=root.children.find(n=>n.name==='Android gesture inset');
    if(!status||!gesture){reports.push({...report,skipped:'System inset missing'});continue;}
    let viewport=root.children.find(n=>n.name==='GoServe auth scroll viewport'),body=viewport?viewport.children.find(n=>n.name==='GoServe auth content'):null,header=root.children.find(n=>n.name==='GoServe auth back bar');
    if(!viewport){
      const back=root.children.find(n=>n.type==='FRAME'&&/^go:.*\| Back$/.test(n.name));
      const content=root.children.filter(n=>n.id!==status.id&&n.id!==gesture.id&&(!back||n.id!==back.id)).sort((a,b)=>a.y-b.y);
      if(!content.length){reports.push({...report,skipped:'Authentication content missing'});continue;}
      const firstY=content[0].y;
      if(back){header=make(root,'GoServe auth back bar','HORIZONTAL');header.appendChild(back);mark(back);}
      viewport=make(root,'GoServe auth scroll viewport');viewport.resize(390,1);body=make(viewport,'GoServe auth content');body.resize(390,1);col(body,0,24,16);body.paddingTop=Math.max(16,firstY-(header?88:24));body.paddingBottom=24;
      for(const n of content){body.appendChild(n);n.layoutPositioning='AUTO';mark(n);}
    }
    if(!body)throw new Error('Authentication body missing');
    const dividers=body.children.filter(n=>['divider left','divider label','divider right'].includes(n.name));
    if(dividers.length===3){const index=body.children.indexOf(dividers[0]),row=make(body,'GoServe auth divider','HORIZONTAL');body.insertChild(index,row);row.resize(342,20);row.primaryAxisSizingMode='FIXED';row.counterAxisSizingMode='AUTO';row.itemSpacing=12;row.counterAxisAlignItems='CENTER';for(const name of ['divider left','divider label','divider right']){const n=dividers.find(x=>x.name===name);row.appendChild(n);n.layoutPositioning='AUTO';if(n.type!=='TEXT'){n.resize(120,1);n.layoutSizingHorizontal='FILL';n.layoutSizingVertical='FIXED';}mark(n);}}
    for(const n of body.children){
      if(n.type==='TEXT')continue;
      if(n.name==='Authentication tabs'){
        n.layoutMode='HORIZONTAL';n.resize(342,54);n.primaryAxisSizingMode='FIXED';n.counterAxisSizingMode='AUTO';n.paddingTop=n.paddingBottom=n.paddingLeft=n.paddingRight=3;n.itemSpacing=0;n.minHeight=54;n.clipsContent=false;mark(n);
        for(const tab of n.children){if(tab.type==='FRAME'){col(tab,14,8,0,48);tab.layoutSizingHorizontal='FILL';for(const t of tab.children.filter(x=>x.type==='TEXT')){flow(t,152,'Label');t.textAlignHorizontal='CENTER';mark(t);}}}
      }else if(n.name==='First-time profile note'){col(n,18,18,8);}
      else if(n.name==='Account email summary'){col(n,12,16,0,48);}
      else if(/^input \//.test(n.name)){col(n,12,20,0,48);}
      else if(/^go:/.test(n.name)){
        const google=n.children.find(x=>x.name==='Google monogram');
        if(google){n.layoutMode='HORIZONTAL';n.resize(n.width,48);n.primaryAxisSizingMode='FIXED';n.counterAxisSizingMode='AUTO';n.paddingLeft=n.paddingRight=16;n.paddingTop=n.paddingBottom=11;n.itemSpacing=12;n.minHeight=48;n.counterAxisAlignItems='CENTER';n.clipsContent=false;google.layoutPositioning='AUTO';google.resize(26,26);google.layoutSizingHorizontal=google.layoutSizingVertical='FIXED';mark(google);mark(n);}
        else col(n,14,14,0,48);
      }
      if('layoutMode'in n&&n.layoutMode!=='NONE'){n.layoutSizingHorizontal='FILL';n.layoutSizingVertical='HUG';mark(n);}
    }
    root.layoutMode='VERTICAL';root.resize(390,844);root.primaryAxisSizingMode=root.counterAxisSizingMode='FIXED';root.paddingTop=root.paddingRight=root.paddingBottom=root.paddingLeft=0;root.itemSpacing=0;root.clipsContent=true;mark(root);
    const order=[status,...(header?[header]:[]),viewport,gesture];for(let i=0;i<order.length;i++){root.insertChild(i,order[i]);order[i].layoutPositioning='AUTO';order[i].layoutSizingHorizontal='FILL';mark(order[i]);}
    for(const n of [status,gesture]){n.resize(390,24);n.layoutSizingHorizontal='FILL';n.layoutSizingVertical='FIXED';mark(n);}
    if(header){header.resize(390,64);header.primaryAxisSizingMode='FIXED';header.counterAxisSizingMode='AUTO';header.paddingLeft=header.paddingRight=12;header.paddingTop=header.paddingBottom=8;header.minHeight=64;header.layoutSizingHorizontal='FILL';header.layoutSizingVertical='HUG';header.counterAxisAlignItems='CENTER';for(const back of header.children){back.resize(48,48);back.layoutPositioning='AUTO';back.layoutSizingHorizontal=back.layoutSizingVertical='FIXED';mark(back);}mark(header);}
    viewport.resize(390,1);viewport.primaryAxisSizingMode=viewport.counterAxisSizingMode='FIXED';viewport.paddingTop=viewport.paddingRight=viewport.paddingBottom=viewport.paddingLeft=0;viewport.minHeight=null;viewport.minWidth=null;viewport.layoutSizingHorizontal=viewport.layoutSizingVertical='FILL';viewport.clipsContent=true;viewport.overflowDirection='VERTICAL';mark(viewport);
    body.resize(390,Math.max(1,body.height));body.primaryAxisSizingMode='AUTO';body.counterAxisSizingMode='FIXED';body.layoutSizingHorizontal='FILL';body.layoutSizingVertical='HUG';body.clipsContent=false;mark(body);
    for(const t of root.findAllWithCriteria({types:['TEXT']})){
      if(skip(t,root))continue;const role=roleFor(t),parent=t.parent;
      if(!('layoutMode'in parent)||parent.layoutMode==='NONE'){report.findings.push({id:t.id,name:t.name,issue:'Unrecognized manual text parent'});continue;}
      if(t.name==='divider label'){t.textStyleId=styles.Caption.id;t.textAutoResize='WIDTH_AND_HEIGHT';t.layoutSizingHorizontal=t.layoutSizingVertical='HUG';mark(t);}
      else{const fixed=parent.layoutMode==='HORIZONTAL'?parent.children.filter(n=>n.id!==t.id&&n.name==='Google monogram').reduce((sum,n)=>sum+n.width+parent.itemSpacing,0):0;flow(t,parent.width-parent.paddingLeft-parent.paddingRight-fixed,role);}
      report.roles[role]=(report.roles[role]||0)+1;
    }
    for(const n of status.children)if('constraints'in n){n.constraints={horizontal:/system|battery|signal/i.test(n.name)?'MAX':'MIN',vertical:'MIN'};mark(n);}
    for(const n of gesture.children)if('constraints'in n){n.constraints={horizontal:'CENTER',vertical:'CENTER'};n.x=(gesture.width-n.width)/2;n.y=(gesture.height-n.height)/2;mark(n);}
    report.bounds={headerHeight:header?.height||0,viewportHeight:viewport.height,bodyHeight:body.height,scrolls:body.height>viewport.height};
  }catch(error){report.error=String(error);report.partialMutation=true;}
  for(const n of all(root)){const old=before.get(n.id),now=[n.parent?.id,n.x,n.y,n.width,n.height];if(old&&old.some((v,i)=>typeof v==='number'?Math.abs(v-now[i])>.01:v!==now[i]))mark(n);}
  reports.push(report);
}
return{createdNodeIds,mutatedNodeIds:[...mutated],brandExceptionIds:[...new Set(brandExceptionIds)],reports};
