// Inject ORGANIZATION_PLAN from figma_ui_organization_plan.json.
await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('14:2'));
const page=figma.currentPage,plan=ORGANIZATION_PLAN,createdNodeIds=[],mutated=new Set(),sectionMap={},reports=[];
const allFonts=new Map(plan.fonts.map(f=>[JSON.stringify(f),f]));
const styles=Object.fromEntries((await figma.getLocalTextStylesAsync()).filter(s=>s.name.startsWith('GoServe / ')).map(s=>[s.name.split(' / ')[1],s]));
for(const role of ['Office','Support'])allFonts.set(JSON.stringify(styles[role].fontName),styles[role].fontName);
await Promise.all([...allFonts.values()].map(f=>figma.loadFontAsync(f)));
plan.sourceIds=plan.sourceIds||[...new Set([plan.reviewId,...plan.components.flatMap(x=>[x.id,...(x.noteIds||[])]),...plan.retained.map(x=>x.id),...Object.values(plan.groups).flatMap(ps=>ps.flatMap(p=>p.screens.flatMap(s=>[s.id,...s.noteIds])))])];
const existingIds=new Set(page.findAll(()=>true).map(n=>n.id));
const missing=plan.sourceIds.filter(id=>!existingIds.has(id));
if(missing.length)return{createdNodeIds,mutatedNodeIds:[],error:'Saved source nodes missing',missing};
const refs=new Map();for(const id of plan.sourceIds)refs.set(id,await figma.getNodeByIdAsync(id));
const mark=n=>mutated.add(n.id);
const componentNotes=new Map(plan.components.map(c=>{const owner=refs.get(c.id);return[c.id,(c.noteIds||[]).map(id=>{const n=refs.get(id);return{node:n,dx:n.x-owner.x,dy:n.y-owner.y};})];}));
const contentSignatures=new Map([...refs.values()].filter(n=>n.type==='FRAME'||n.type==='COMPONENT'||n.type==='COMPONENT_SET').map(n=>[n.id,JSON.stringify([n.width,n.height,n.findAll(()=>true).map(c=>[c.id,c.x,c.y,c.width,c.height,'reactions'in c?c.reactions:null])])]));
const sectionName=name=>'GoServe / Canvas / '+name;
function section(key,name,parent=page){
 let node=page.findOne(n=>n.type==='SECTION'&&n.name===sectionName(name));
 if(!node){node=figma.createSection();node.name=sectionName(name);createdNodeIds.push(node.id);}
 if(node.parent.id!==parent.id)parent.appendChild(node);
 node.fills=[{type:'SOLID',color:{r:.91,g:.925,b:.92}}];node.visible=true;mark(node);sectionMap[key]=node;return node;
}
function caption(parent,name,value,width){
 let t=parent.children.find(n=>n.type==='TEXT'&&n.name===name);
 if(!t){t=figma.createText();t.name=name;parent.appendChild(t);createdNodeIds.push(t.id);}
 t.textStyleId=styles.Office.id;t.resize(width,100);t.textAutoResize='HEIGHT';t.characters=value;t.fills=[{type:'SOLID',color:{r:.08,g:.09,b:.09}}];mark(t);return t;
}
function place(parent,node,x,y){if(node.parent.id!==parent.id)parent.appendChild(node);node.x=x;node.y=y;mark(node);}
function grid(key,parent){
 const group=section(key,plan.categories[key].name,parent),pairs=plan.groups[key];let y=88,total=0;
 for(let i=0;i<pairs.length;i+=2){
  const row=pairs.slice(i,i+2),headers=[];let headerHeight=24;
  for(let j=0;j<row.length;j++){
   const pair=row[j],x=48+j*932,ordered=[...pair.screens].sort((a,b)=>a.theme==='Light'?-1:1);
   for(const screen of ordered){const sx=x+(screen.theme==='Dark'?422:0),t=caption(group,'Canvas caption / '+screen.id,screen.caption||(screen.theme+' · '+pair.title),390);t.x=sx;t.y=y;headerHeight=Math.max(headerHeight,t.height);headers.push(t);}
  }
  let rowBottom=y+headerHeight;
  for(let j=0;j<row.length;j++){
   const pair=row[j],x=48+j*932;
   for(const screen of pair.screens){
    const node=refs.get(screen.id),sx=x+(screen.theme==='Dark'?422:0)+(390-node.width)/2,sy=y+headerHeight+16;
    const w=node.width,h=node.height;place(group,node,sx,sy);
    if(Math.abs(node.width-w)>.01||Math.abs(node.height-h)>.01)throw new Error('Grouping resized screen '+node.id);
    let noteY=sy+node.height+16;
    for(const id of screen.noteIds||[]){const note=refs.get(id);place(group,note,sx,noteY);noteY+=note.height+12;}
    rowBottom=Math.max(rowBottom,noteY);total++;
   }
  }
  y=rowBottom+80;
 }
 group.resizeWithoutConstraints(1840,Math.max(240,y-32));reports.push({key,id:group.id,pairs:pairs.length,screens:total,width:group.width,height:group.height});return group;
}
const start=section('start','00 · Start here');start.x=0;start.y=0;
const index=refs.get(plan.reviewId);place(start,index,48,64);start.resizeWithoutConstraints(index.width+96,index.height+112);
const components=section('components','01 · Components');components.x=2080;components.y=0;
let cy=88;
const sets=plan.components.filter(n=>n.type==='COMPONENT_SET');
for(let i=0;i<sets.length;i+=2){const row=sets.slice(i,i+2);let height=0;for(let j=0;j<row.length;j++){const c=row[j],node=refs.get(c.id),x=48+j*2220,label=caption(components,'Canvas caption / '+c.id,c.name.replace('GoServe / ',''),2080);label.x=x;label.y=cy;const notes=componentNotes.get(c.id)||[],topInset=notes.length?84:0;place(components,node,x,cy+label.height+16+topInset);for(const note of notes)place(components,note.node,node.x+note.dx,node.y+note.dy);height=Math.max(height,label.height+16+topInset+node.height);}cy+=height+64;}
const nav=plan.components.filter(n=>n.type==='COMPONENT');
for(let i=0;i<nav.length;i+=4){const row=nav.slice(i,i+4);let height=0;for(let j=0;j<row.length;j++){const c=row[j],node=refs.get(c.id),x=48+j*550,label=caption(components,'Canvas caption / '+c.id,c.name.replace('GoServe / Capsule navigation / ',''),500);label.x=x;label.y=cy;const notes=componentNotes.get(c.id)||[],topInset=notes.length?84:0;place(components,node,x,cy+label.height+16+topInset);for(const note of notes)place(components,note.node,node.x+note.dx,node.y+note.dy);height=Math.max(height,label.height+16+topInset+node.height);}cy+=height+48;}
components.resizeWithoutConstraints(4400,cy);
const mainY=Math.max(3000,Math.ceil((Math.max(start.height,components.height)+240)/100)*100);
const auth=grid('auth',page);auth.x=0;auth.y=mainY;
const student=section('student','03 · Student journeys');student.x=0;student.y=auth.y+auth.height+180;
let studentY=72;for(const key of ['home','requests','consult','account']){const group=grid(key,student);group.x=48;group.y=studentY;studentY+=group.height+120;}student.resizeWithoutConstraints(1936,studentY-72);
let officeY=mainY;for(const key of ['staff','teller','seryo','demo']){const group=grid(key,page);group.x=2200;group.y=officeY;officeY+=group.height+180;}
const stateLibrary=section('states','07 · State library');stateLibrary.x=4280;stateLibrary.y=mainY;
const laneY=[72,72];for(let i=1;i<=14;i++){const key='G'+String(i).padStart(2,'0'),lane=i<=7?0:1,group=grid(key,stateLibrary);group.x=48+lane*2000;group.y=laneY[lane];laneY[lane]+=group.height+120;}stateLibrary.resizeWithoutConstraints(3936,Math.max(...laneY)-72);
const proofs=grid('proofs',page);proofs.x=8520;proofs.y=mainY;
const retired=section('retained','99 · Retained earlier design references');retired.x=8520;retired.y=proofs.y+proofs.height+180;
let ry=64;for(const item of plan.retained){const n=refs.get(item.id);place(retired,n,48,ry);ry+=n.height+32;}retired.resizeWithoutConstraints(2100,ry+16);retired.visible=false;mark(retired);
const flowNames={'19:2':'Student sign in · Light','56:38':'Student sign up · Light','36:2':'Student sign in · Dark','56:66':'Student sign up · Dark','24:131':'Office staff · Light','36:899':'Office staff · Dark','26:415':'Cashiering · Light','36:1558':'Cashiering · Dark'};
page.flowStartingPoints=page.flowStartingPoints.map(point=>({...point,name:flowNames[point.nodeId]||point.name}));mark(page);
const order=['start','components','auth','student','staff','teller','seryo','states','proofs','demo','retained'];
for(let i=0;i<order.length;i++){page.insertChild(i,sectionMap[order[i]]);mark(sectionMap[order[i]]);}
const assignment=plan.sourceIds.map(id=>{const n=refs.get(id);return{id,parentId:n.parent.id};});
const changedContents=[];for(const [id,before]of contentSignatures){const n=refs.get(id),after=JSON.stringify([n.width,n.height,n.findAll(()=>true).map(c=>[c.id,c.x,c.y,c.width,c.height,'reactions'in c?c.reactions:null])]);if(after!==before)changedContents.push(id);}
const created=new Set(createdNodeIds);
return{createdNodeIds,mutatedNodeIds:[...mutated].filter(id=>!created.has(id)),sections:Object.entries(sectionMap).map(([key,n])=>({key,id:n.id,parentId:n.parent.id,bounds:[n.x,n.y,n.width,n.height],visible:n.visible})),reports:reports.map(r=>({key:r.key,pairs:r.pairs,screens:r.screens,height:r.height})),sourceCount:assignment.length,pageChildren:page.children.map(n=>({id:n.id,type:n.type})),allSourcesGrouped:assignment.every(a=>a.parentId!=='14:2'),unchangedContentCount:contentSignatures.size-changedContents.length,changedContents};

