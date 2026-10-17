// Inputs: LAYOUT_GROUPS and SECTION_MAP. Edits only the separate professor-facing page.
await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('305:2'));
const page=figma.currentPage,created=[],mutated=new Set(),removed=[];
const styles=Object.fromEntries((await figma.getLocalTextStylesAsync()).filter(s=>s.name.startsWith('GoServe / ')).map(s=>[s.name.split(' / ')[1],s]));
const fonts=new Map(Object.values(styles).map(s=>[JSON.stringify(s.fontName),s.fontName]));
for(const t of page.findAllWithCriteria({types:['TEXT']}))for(const s of t.getStyledTextSegments(['fontName']))fonts.set(JSON.stringify(s.fontName),s.fontName);
await Promise.all([...fonts.values()].map(f=>figma.loadFontAsync(f)));
function box(parent,name,width,gap=12,pad=0,direction='VERTICAL'){const n=figma.createAutoLayout(direction);created.push(n.id);n.name=name;parent.appendChild(n);n.resize(width,100);n.primaryAxisSizingMode='AUTO';n.counterAxisSizingMode='FIXED';n.itemSpacing=gap;n.paddingTop=n.paddingBottom=n.paddingLeft=n.paddingRight=pad;n.fills=[];return n;}
function text(parent,value,role='Body',width=390){const n=figma.createText();created.push(n.id);parent.appendChild(n);n.textStyleId=styles[role].id;n.characters=value;n.resize(width,24);n.textAutoResize='HEIGHT';n.fills=[{type:'SOLID',color:{r:.1,g:.1,b:.1}}];return n;}
const retainedIds=new Set(LAYOUT_GROUPS.flatMap(g=>g.panels.map(p=>p.id)));
for(const p of LAYOUT_GROUPS.flatMap(g=>g.panels)){const root=await figma.getNodeByIdAsync(p.id);for(const n of [root,...root.findAll(()=>true)])if('reactions'in n&&n.reactions.length){n.reactions=[];mutated.add(n.id);}}
let y=850;const reports=[];
for(const group of LAYOUT_GROUPS){
 const section=await figma.getNodeByIdAsync(SECTION_MAP[group.key]);section.x=48;section.y=y;section.name=group.title;mutated.add(section.id);
 const heading=box(section,'Overview / Section heading',1704,8);heading.x=48;heading.y=40;text(heading,group.title,'Section',1704);text(heading,group.summary,'Body',1704);
 const cols=[];
 for(const [index,p]of group.panels.entries()){
  const root=await figma.getNodeByIdAsync(p.id);const col=box(section,'Overview / '+p.key,390,12);col.x=48+index*438;col.y=40+heading.height+32;
  const caption=box(col,'Overview / Panel caption',390,0);caption.resize(390,56);caption.primaryAxisSizingMode='FIXED';text(caption,p.title,'Section',390);
  col.appendChild(root);root.layoutSizingHorizontal='FIXED';root.layoutSizingVertical='FIXED';mutated.add(root.id);
  const note=text(col,p.notes,'Support',390);note.name='Overview / Workflow note';cols.push(col);
 }
 for(const old of [...section.children])if(!old.name.startsWith('Overview / ')){removed.push(old.id);old.remove();}
 const height=Math.max(...cols.map(c=>c.y+c.height))+48;section.resizeWithoutConstraints(1800,height);reports.push({key:group.key,id:section.id,bounds:[section.x,section.y,section.width,section.height],panels:group.panels.map(p=>({key:p.key,id:p.id}))});y+=height+120;
}
let index=page.children.find(n=>n.type==='FRAME'&&n.name==='GoServe / Professor overview');
if(index){removed.push(index.id);index.remove();}
index=box(page,'GoServe / Professor overview',1800,24,48);index.x=48;index.y=48;index.fills=[{type:'SOLID',color:{r:1,g:1,b:1}}];index.cornerRadius=20;index.strokes=[{type:'SOLID',color:{r:.82,g:.84,b:.83}}];index.strokeWeight=1;
const hero=text(index,'GoServe · Program overview','Hero',1704);hero.fontSize=36;hero.lineHeight={unit:'PIXELS',value:44};
text(index,'16 key screens · Professor walkthrough · Revised plan v2.3','Body',1704);
text(index,'Account setup → Student services → Authorized office processing → Payment or document release → Confirmation on both sides','Section',1704);
const system=box(index,'Shared database relationship',1704,12,20);system.cornerRadius=12;system.fills=[{type:'SOLID',color:{r:.09,g:.09,b:.09}}];
for(const value of ['Student ↔ Supabase database and Realtime ↔ Assigned office / Cashiering','Requests, replies, payments, appointments and completion update after the database commits. Reconnect reloads missed changes.']){const t=text(system,value,value.startsWith('Student')?'Section':'Body',1664);t.fills=[{type:'SOLID',color:{r:1,g:1,b:1}}];}
text(index,'Physical: Office marks Ready → student claims QR → office scans/reviews → confirms handover → Completed.\nDigital: Office releases PDF → student downloads → confirms receipt → Completed.','Body',1704);
text(index,'These editable wireframes summarize the program. Short notes cover omitted states. The existing Light/Dark UI page contains the detailed screen inventory; UI approval and working native verification remain separate gates.','Support',1704);
const contrast=box(index,'Navigation contrast references',1704,32,0,'HORIZONTAL');
for(const [theme,id]of [['Light','19:93'],['Dark','36:93']]){const source=await figma.getNodeByIdAsync(id);const nav=source.children.find(n=>/Capsule navigation|Role navigation|navigation/i.test(n.name));if(nav){const c=box(contrast,'Navigation / '+theme,400,8);text(c,theme+' navigation','Label',400);const clone=nav.clone();c.appendChild(clone);created.push(clone.id,...clone.findAll(()=>true).map(n=>n.id));for(const n of [clone,...clone.findAll(()=>true)])if('reactions'in n&&n.reactions.length)n.reactions=[];}}
if(index.height+48>800){const delta=index.height+96-850;for(const r of reports){const s=await figma.getNodeByIdAsync(r.id);s.y+=Math.max(0,delta);r.bounds[1]=s.y;}}
const unexpected=page.children.filter(n=>n.id!==index.id&&!Object.values(SECTION_MAP).includes(n.id));
for(const n of unexpected){if(retainedIds.has(n.id))throw new Error('A retained phone was not grouped');removed.push(n.id);n.remove();}
page.flowStartingPoints=[];mutated.add(page.id);
return{pageId:page.id,indexId:index.id,createdNodeIds:created.join(' '),mutatedNodeIds:[...mutated],deletedNodeIds:removed,sections:reports,panelCount:retainedIds.size,pageChildCount:page.children.length,indexBounds:[index.x,index.y,index.width,index.height]};
