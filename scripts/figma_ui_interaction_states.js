// Static Figma reference families. No form capture, native focus, click or provider simulation.
await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('14:2'));
const page=figma.currentPage,section=await figma.getNodeByIdAsync('189:7485');
const created=new Set(),mutated=new Set(),deleted=new Set(),createdStyleIds=[],mutatedStyleIds=[],reports=[],referenceNotes=[],errors=[];
const all=n=>[n,...('findAll'in n?n.findAll(()=>true):[])],track=n=>{created.add(n.id);return n;},mark=n=>{if(!created.has(n.id))mutated.add(n.id);};
if(!section||section.type!=='SECTION')return{error:'Existing GoServe components section missing',createdNodeIds:[],mutatedNodeIds:[],deletedNodeIds:[]};
const definitions=[
 {key:'field',name:'GoServe / Email field states',states:['Rest','Focused','Invalid','Disabled'],height:104,title:'Email field · Interaction states',note:'Static visual references. Focus uses a 2 px border. Invalid input keeps an adjacent message. The input remains at least 48 dp high.'},
 {key:'action',name:'GoServe / Primary action states',states:['Rest','Pressed','Focused','Disabled'],height:58,title:'Primary action · Interaction states',note:'Static visual references. Focus: 2 px ring, 3 px surface gap. Pressed: reduced elevation. All variants retain a 48 dp control and the same outer bounds.'}
];
const existingSets=page.findAllWithCriteria({types:['COMPONENT_SET']});
const existingNotes=page.findAllWithCriteria({types:['TEXT']}).filter(n=>n.name.startsWith('GoServe / Interaction reference / '));
const textStyles=await figma.getLocalTextStylesAsync(),styles=Object.fromEntries(['Section','Label','Body','Support','Caption'].map(role=>[role,textStyles.find(s=>s.name==='GoServe / '+role)]));
const collections=await figma.variables.getLocalVariableCollectionsAsync(),variables=await figma.variables.getLocalVariablesAsync(),effectStyles=await figma.getLocalEffectStylesAsync();
const neededColors=['surface','text','secondary','border','muted','primary','onPrimary'];
const colors=new Map();
for(const theme of ['Light','Dark']){
 const collection=collections.find(c=>c.name==='GoServe / '+theme);
 for(const key of neededColors){const variable=collection&&variables.find(v=>v.variableCollectionId===collection.id&&v.name==='color/'+key);if(!variable)errors.push({reason:'Missing existing color',theme,key});else colors.set(theme+'|'+key,{variable,collection});}
}
for(const role of Object.keys(styles))if(!styles[role])errors.push({reason:'Missing existing GoServe text style',role});
if(errors.length)return{createdNodeIds:[],mutatedNodeIds:[],deletedNodeIds:[],errors};
function paint(theme,key){const {variable,collection}=colors.get(theme+'|'+key),value=variable.valuesByMode[collection.defaultModeId];if(!value||typeof value!=='object'||!('r'in value))throw new Error('Expected resolved existing color '+theme+'/'+key);return figma.variables.setBoundVariableForPaint({type:'SOLID',color:{r:value.r,g:value.g,b:value.b}},'color',variable);}
const fontMap=new Map();
for(const style of Object.values(styles))if(style.fontName&&style.fontName!==figma.mixed)fontMap.set(JSON.stringify(style.fontName),style.fontName);
for(const node of [...existingSets.filter(n=>definitions.some(d=>d.name===n.name)),...existingNotes])for(const text of node.type==='TEXT'?[node]:node.findAllWithCriteria({types:['TEXT']}))for(const segment of text.getStyledTextSegments(['fontName']))fontMap.set(JSON.stringify(segment.fontName),segment.fontName);
const loaded=await Promise.allSettled([...fontMap.values()].map(font=>figma.loadFontAsync(font)));
const failedFonts=[...fontMap.values()].filter((font,index)=>loaded[index].status==='rejected');
if(failedFonts.length)return{createdNodeIds:[],mutatedNodeIds:[],deletedNodeIds:[],failedFonts};
function clear(node){for(const child of [...node.children]){for(const n of all(child))deleted.add(n.id);child.remove();}mark(node);}
function box(parent,name,width,height,vertical=true){const n=track(figma.createAutoLayout(vertical?'VERTICAL':'HORIZONTAL'));n.name=name;n.resize(width,height);n.primaryAxisSizingMode=n.counterAxisSizingMode='FIXED';n.paddingTop=n.paddingRight=n.paddingBottom=n.paddingLeft=n.itemSpacing=0;n.fills=[];n.strokes=[];n.clipsContent=false;n.minWidth=n.minHeight=null;parent.appendChild(n);n.layoutSizingHorizontal='FILL';n.layoutSizingVertical='FIXED';return n;}
function text(parent,name,value,theme,role='Label',color='text',align='LEFT'){const n=track(figma.createText());n.name=name;n.textStyleId=styles[role].id;n.resize(Math.max(1,parent.width-(parent.paddingLeft||0)-(parent.paddingRight||0)),20);n.textAutoResize='HEIGHT';n.characters=value;n.fills=[paint(theme,color)];n.textAlignHorizontal=align;parent.appendChild(n);n.layoutSizingHorizontal='FILL';n.layoutSizingVertical='HUG';return n;}
function shadow(theme,state){return[{type:state==='Pressed'?'INNER_SHADOW':'DROP_SHADOW',color:{r:0,g:0,b:0,a:theme==='Dark'?(state==='Pressed'?.22:.24):(state==='Pressed'?.16:.10)},offset:{x:0,y:state==='Pressed'?1:2},radius:state==='Pressed'?3:8,visible:true,blendMode:'NORMAL'}];}
const effects=new Map();
for(const theme of ['Light','Dark'])for(const state of ['Rest','Pressed']){
 const name='GoServe / Action elevation / '+theme+' / '+state;let style=effectStyles.find(s=>s.name===name);
 if(!style){style=figma.createEffectStyle();style.name=name;createdStyleIds.push(style.id);}else mutatedStyleIds.push(style.id);
 style.effects=shadow(theme,state);style.description=state==='Pressed'?'Static depressed action reference. Native touch press temporarily reduces elevation; no layout movement.':'Soft offset elevation for enabled primary actions. Disabled controls have no elevation.';
 effects.set(theme+'|'+state,style);
}
function resetComponent(component,name,width,height,theme){clear(component);component.name=name;component.layoutMode='VERTICAL';component.resize(width,height);component.primaryAxisSizingMode=component.counterAxisSizingMode='FIXED';component.paddingTop=component.paddingRight=component.paddingBottom=component.paddingLeft=component.itemSpacing=0;component.fills=[paint(theme,'surface')];component.strokes=[];component.effects=[];component.clipsContent=false;component.minWidth=component.minHeight=null;component.reactions=[];mark(component);}
function buildField(component,theme,state){
 resetComponent(component,'Theme='+theme+', State='+state,350,104,theme);component.itemSpacing=8;
 text(component,'Email address label','Email address',theme,'Label',state==='Disabled'?'secondary':'text');
 const row=box(component,'Email address / '+state+' input',350,48,false);row.minHeight=48;row.paddingLeft=row.paddingRight=16;row.primaryAxisAlignItems='MIN';row.counterAxisAlignItems='CENTER';row.cornerRadius=16;row.fills=[paint(theme,state==='Disabled'?'muted':'surface')];row.strokes=[paint(theme,state==='Focused'||state==='Invalid'?'text':'border')];row.strokeWeight=state==='Focused'||state==='Invalid'?2:1;row.strokeAlign='INSIDE';row.strokesIncludedInLayout=false;
 text(row,'Email address placeholder','name@example.com',theme,'Body','secondary');
 const helper=box(component,'Email address / Reserved validation line',350,20);if(state==='Invalid')text(helper,'Email address validation message','Enter a valid email address.',theme,'Support');
 component.description='Email field, '+theme+' / '+state+'. Static native visual contract: 48 dp minimum input height, 2 px focused/invalid border, stable spacing, visible adjacent validation. Focus the actual native input and preserve entered text on errors. Disabled input does not accept interaction. These variants do not capture or validate values.';
 return{input:row.id,validation:helper.id};
}
async function buildAction(component,theme,state){
 resetComponent(component,'Theme='+theme+', State='+state,350,58,theme);component.paddingTop=component.paddingRight=component.paddingBottom=component.paddingLeft=5;component.cornerRadius=29;
 if(state==='Focused'){component.strokes=[paint(theme,'text')];component.strokeWeight=2;component.strokeAlign='INSIDE';component.strokesIncludedInLayout=false;}
 const control=box(component,'Primary action / '+state+' control',340,48);control.minHeight=48;control.primaryAxisAlignItems=control.counterAxisAlignItems='CENTER';control.cornerRadius=24;control.fills=[paint(theme,state==='Disabled'?'muted':'primary')];
 if(state!=='Disabled')await control.setEffectStyleIdAsync(effects.get(theme+'|'+(state==='Pressed'?'Pressed':'Rest')).id);
 text(control,'Primary action example label','Sign in',theme,'Label',state==='Disabled'?'secondary':'onPrimary','CENTER');
 component.description='Primary action, '+theme+' / '+state+'. Static native visual contract: minimum 48 dp control height; all variants keep 350 × 58 outer bounds. Focused uses a 2 px high-contrast ring separated from the control by 3 px of surface. Pressed temporarily replaces soft elevation with an inset treatment, then returns on release/cancel. Disabled has no elevation and cannot activate. Sign in is an example label; use the actual action label. No prototype action or provider behavior is simulated.';
 return{control:control.id,focusRing:state==='Focused'?component.id:null};
}
function note(name,value,x,y,width,role='Caption'){
 let node=existingNotes.find(n=>n.name===name);if(!node){node=track(figma.createText());node.name=name;section.appendChild(node);mark(section);}else mark(node);
 node.textStyleId=styles[role].id;node.resize(width,20);node.textAutoResize='HEIGHT';node.characters=value;node.fills=[paint('Light','secondary')];node.x=x;node.y=y;node.reactions=[];
 return node;
}
const existingOwnedIds=new Set([...existingSets.filter(n=>definitions.some(d=>d.name===n.name)),...existingNotes].map(n=>n.id));
let nextX=Math.max(32,...section.children.filter(n=>!existingOwnedIds.has(n.id)).map(n=>n.x+n.width))+64;
for(const def of definitions){
 let set=existingSets.find(s=>s.name===def.name);const setExisted=!!set,variants=[],coverage=[];
 for(const [col,theme]of ['Light','Dark'].entries())for(const [row,state]of def.states.entries()){
  const name='Theme='+theme+', State='+state;let component=set?.children.find(n=>n.type==='COMPONENT'&&n.name===name);
  if(!component){component=track(figma.createComponent());section.appendChild(component);mark(section);if(set){set.appendChild(component);mark(set);}}
  const parts=def.key==='field'?buildField(component,theme,state):await buildAction(component,theme,state);variants.push(component);coverage.push({id:component.id,theme,state,parts,bounds:{w:component.width,h:component.height},minimumHitHeight:48,colorsBound:true,static:true});
 }
 if(!set){set=track(figma.combineAsVariants(variants,section));set.name=def.name;mark(section);}else mark(set);
 set.layoutMode='NONE';set.fills=[];set.strokes=[];set.clipsContent=false;set.description=def.note+' Reuses GoServe text styles and semantic Light/Dark colors. No form capture, automatic validation, authentication or provider request is performed by this reference family.';
 const width=804,height=48+4*def.height+3*56;set.resize(width,height);if(!setExisted||set.parent?.id!==section.id){section.appendChild(set);set.x=nextX;set.y=112;mark(section);}nextX=Math.max(nextX,set.x+width+64);
 for(const [index,component]of variants.entries()){
  const col=Math.floor(index/4),row=index%4;component.x=24+col*406;component.y=24+row*(def.height+56);mark(component);
  const state=coverage[index].state,theme=coverage[index].theme,node=note('GoServe / Interaction reference / '+def.key+' / '+theme+' / '+state,theme+' · '+state,set.x+component.x,set.y+component.y-20,350);
  referenceNotes.push({id:node.id,owner:set.id,kind:'state-caption',theme,state});
 }
 const title=note('GoServe / Interaction reference / '+def.key+' / Title',def.title,set.x,set.y-84,width,'Section');
 const description=note('GoServe / Interaction reference / '+def.key+' / Description',def.note,set.x,set.y-48,width,'Support');
 referenceNotes.push({id:title.id,owner:set.id,kind:'title'},{id:description.id,owner:set.id,kind:'description'});
 reports.push({id:set.id,name:set.name,bounds:{x:set.x,y:set.y,w:set.width,h:set.height},variantCount:variants.length,coverage,referenceNoteIds:referenceNotes.filter(n=>n.owner===set.id).map(n=>n.id)});
}
const maxRight=Math.max(section.width,...section.children.map(n=>n.x+n.width+32)),maxBottom=Math.max(section.height,...section.children.map(n=>n.y+n.height+32));
if(maxRight>section.width||maxBottom>section.height){section.resizeWithoutConstraints(maxRight,maxBottom);mark(section);}
return{createdNodeIds:[...created],mutatedNodeIds:[...mutated].filter(id=>!deleted.has(id)),deletedNodeIds:[...deleted],createdStyleIds,mutatedStyleIds,componentSetIds:reports.map(r=>r.id),components:reports,referenceNotes,effectStyles:[...effects.entries()].map(([key,s])=>({key,id:s.id,name:s.name})),stateCoverage:{themes:['Light','Dark'],field:definitions[0].states,action:definitions[1].states,totalVariants:16},errors,limits:{staticReferencesOnly:true,prototypeReactions:0,nativeFocusImplemented:false,providerCalls:false,noPhoneRoots:true,existingScreensChanged:false}};
