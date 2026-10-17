// Root injects AUTH_SCREEN_MAP and AUTH_TITLES with paired Light/Dark auth cases.
await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('14:2'));
const PANEL_NAME=typeof REVIEW_PANEL_NAME==='undefined'?'Authentication flow review':REVIEW_PANEL_NAME,HEADING=typeof REVIEW_HEADING==='undefined'?'Authentication and registration':REVIEW_HEADING,DESCRIPTION=typeof REVIEW_DESCRIPTION==='undefined'?'New users agree to terms and complete a profile. Returning users sign in.':REVIEW_DESCRIPTION;
const page=figma.currentPage,index=await figma.getNodeByIdAsync('175:7272');
const created=new Set(),deleted=new Set(),mutated=new Set(),links=[],unresolved=[];
const all=n=>[n,...('findAll'in n?n.findAll(()=>true):[])],mark=n=>{if(!created.has(n.id))mutated.add(n.id);},track=n=>{for(const x of all(n))created.add(x.id);return n;};
if(!index||index.type!=='FRAME')return{error:'Review index missing',createdNodeIds:[],deletedNodeIds:[],mutatedNodeIds:[]};
const styles=Object.fromEntries((await figma.getLocalTextStylesAsync()).filter(s=>s.name.startsWith('GoServe / ')).map(s=>[s.name.split(' / ')[1],s]));
const collection=(await figma.variables.getLocalVariableCollectionsAsync()).find(c=>c.name==='GoServe / Light'),variables=await figma.variables.getLocalVariablesAsync();
const set=page.findAllWithCriteria({types:['COMPONENT_SET']}).find(n=>n.id==='132:1278'||n.name==='GoServe / Inline action'),component=set&&set.children.find(n=>n.type==='COMPONENT'&&n.name==='Theme=Light');
if(!collection||!component||['Section','Office','Support','Label'].some(role=>!styles[role]))return{error:'Shared styles, Light colors or inline action missing',createdNodeIds:[],deletedNodeIds:[],mutatedNodeIds:[]};
const keys=[...new Set(AUTH_SCREEN_MAP.map(s=>s.key))],screenMap=new Map(AUTH_SCREEN_MAP.map(s=>[s.key+'|'+s.theme,s.id]));
const paired=keys.length>0&&AUTH_SCREEN_MAP.length===keys.length*2&&keys.every(key=>{const entries=AUTH_SCREEN_MAP.filter(s=>s.key===key);return entries.length===2&&['Light','Dark'].every(theme=>entries.some(s=>s.theme===theme));});
if(!paired)return{error:'Each authentication case requires exactly one Light and one Dark screen',cases:keys.length,screens:AUTH_SCREEN_MAP.length,createdNodeIds:[],deletedNodeIds:[],mutatedNodeIds:[]};
const fonts=new Map();for(const scope of [index,component])for(const t of scope.findAllWithCriteria({types:['TEXT']}))for(const s of t.getStyledTextSegments(['fontName']))fonts.set(JSON.stringify(s.fontName),s.fontName);
for(const role of ['Section','Office','Support','Label'])fonts.set(JSON.stringify(styles[role].fontName),styles[role].fontName);
await Promise.all([...fonts.values()].map(font=>figma.loadFontAsync(font)));
const before=new Map(all(index).map(n=>[n.id,[n.x,n.y,n.width,n.height]]));
function paint(key){const v=variables.find(v=>v.variableCollectionId===collection.id&&v.name==='color/'+key);if(!v)throw new Error('Missing Light color '+key);const c=v.valuesByMode[collection.defaultModeId];return figma.variables.setBoundVariableForPaint({type:'SOLID',color:{r:c.r,g:c.g,b:c.b}},'color',v);}
function box(parent,name,width,gap=12,pad=0,horizontal=false){const n=track(figma.createAutoLayout(horizontal?'HORIZONTAL':'VERTICAL'));n.name=name;n.resize(width,100);n.primaryAxisSizingMode=horizontal?'FIXED':'AUTO';n.counterAxisSizingMode=horizontal?'AUTO':'FIXED';n.paddingTop=n.paddingRight=n.paddingBottom=n.paddingLeft=pad;n.itemSpacing=gap;n.fills=[];n.clipsContent=false;parent.appendChild(n);n.layoutSizingHorizontal='FILL';n.layoutSizingVertical='HUG';return n;}
function text(parent,value,role,color='text'){const n=track(figma.createText());n.name=value.slice(0,70);n.textStyleId=styles[role].id;n.resize(Math.max(1,parent.width-parent.paddingLeft-parent.paddingRight),100);parent.appendChild(n);n.layoutSizingHorizontal='FILL';n.layoutSizingVertical='HUG';n.textAutoResize='HEIGHT';n.characters=value;n.fills=[paint(color)];return n;}
let panel=index.children.find(n=>n.type==='FRAME'&&n.name===PANEL_NAME);
if(!panel)panel=box(index,PANEL_NAME,1720,16);else{for(const child of [...panel.children]){for(const n of all(child))deleted.add(n.id);child.remove();}panel.layoutMode='VERTICAL';panel.resize(1720,100);panel.primaryAxisSizingMode='AUTO';panel.counterAxisSizingMode='FIXED';panel.paddingTop=panel.paddingRight=panel.paddingBottom=panel.paddingLeft=0;panel.itemSpacing=16;panel.fills=[];panel.clipsContent=false;panel.layoutSizingHorizontal='FILL';panel.layoutSizingVertical='HUG';mark(panel);}
const oldPosition=index.children.indexOf(panel),beforeColumn=index.children.findIndex(n=>n.name==='Review columns'),afterPrimary=index.children.findIndex(n=>n.name==='Primary screen review');
let target=beforeColumn>=0?beforeColumn:afterPrimary>=0?afterPrimary+1:oldPosition;if(oldPosition<target)target--;if(target!==oldPosition){index.insertChild(target,panel);mark(index);}
text(panel,HEADING,'Section');text(panel,DESCRIPTION,'Support','secondary');
const columns=box(panel,'Authentication review columns',1720,20,0,true),perColumn=Math.ceil(keys.length/2);
for(let column=0;column<2;column++){
  const stack=box(columns,'Authentication case column '+(column+1),850,12);stack.resize(850,100);stack.layoutSizingHorizontal='FIXED';stack.layoutSizingVertical='HUG';
  for(const key of keys.slice(column*perColumn,(column+1)*perColumn)){
    let title=AUTH_TITLES[key]||key;if(key==='terms'&&!/draft/i.test(title))title+=' · Draft';if(['account-created','reset-sent','reset-complete'].includes(key)&&!/example/i.test(title))title+=' · Example';
    const row=box(stack,'Authentication review / '+key,850,12,12,true);row.minHeight=72;row.cornerRadius=16;row.fills=[paint('surface')];row.strokes=[paint('border')];row.strokeWeight=1;row.strokesIncludedInLayout=false;row.counterAxisAlignItems='CENTER';text(row,title,'Office');
    const actions=box(row,'Authentication theme links / '+key,136,8,0,true);actions.resize(136,48);actions.layoutSizingHorizontal='FIXED';actions.layoutSizingVertical='HUG';
    for(const theme of ['Light','Dark']){
      const action=track(component.createInstance());action.name='Auth review link / '+key+' / '+theme;actions.appendChild(action);action.resize(64,48);action.layoutSizingHorizontal=action.layoutSizingVertical='FIXED';
      const label=action.findAllWithCriteria({types:['TEXT']}).find(t=>t.name==='label')||action.findAllWithCriteria({types:['TEXT']})[0];if(label){label.characters=theme;label.textAutoResize='HEIGHT';label.textAlignHorizontal='CENTER';mark(label);}
      const id=screenMap.get(key+'|'+theme),destination=id?await figma.getNodeByIdAsync(id):null;
      if(destination&&destination.type==='FRAME'&&destination.id!==index.id){action.reactions=[{trigger:{type:'ON_CLICK'},actions:[{type:'NODE',destinationId:destination.id,navigation:'NAVIGATE',transition:null,resetScrollPosition:true}]}];links.push({key,theme,node:action.id,destination:destination.id});}
      else{action.reactions=[];unresolved.push({key,theme,id,reason:'Authentication destination missing'});}
    }
  }
}
for(const t of index.findAllWithCriteria({types:['TEXT']}))if(t.characters==='G02.3 · Use your TIP account'){t.characters='G02.3 · Complete your profile';t.textAutoResize='HEIGHT';mark(t);}
mark(index);for(const n of all(index)){const old=before.get(n.id),now=[n.x,n.y,n.width,n.height];if(old&&old.some((v,i)=>Math.abs(v-now[i])>.01))mark(n);}
return{createdNodeIds:[...created],deletedNodeIds:[...deleted],mutatedNodeIds:[...mutated],panelId:panel.id,indexHeight:index.height,linkCount:links.length,links,unresolved};


