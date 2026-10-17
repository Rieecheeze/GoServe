// Figma-only contextual entry points. Root executes after contextual states exist.
await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('14:2'));
const page=figma.currentPage;
const createdNodeIds=[],mutatedNodeIds=[],deletedNodeIds=[],unresolved=[],routes=[];
const created=new Set(),changed=new Set();
const prefix=theme=>theme==='Dark'?'GoServe 2.1 • dark/':'GoServe 2.1 • ';
const styles=Object.fromEntries((await figma.getLocalTextStylesAsync()).filter(s=>s.name.startsWith('GoServe / ')).map(s=>[s.name.split(' / ')[1],s]));
const collections=await figma.variables.getLocalVariableCollectionsAsync(),variables=await figma.variables.getLocalVariablesAsync();
function track(n){created.add(n.id);return n;}
function mark(n){if(n&&!created.has(n.id))changed.add(n.id);return n;}
function pageOf(node){let n=node;while(n&&n.type!=='PAGE')n=n.parent;return n;}
function screenOf(node){let n=node;while(n&&n.type!=='PAGE'){if(n.type==='FRAME'&&n.name.startsWith('GoServe 2.1 • '))return n;n=n.parent;}return null;}
function namedNode(type,name){return page.findAllWithCriteria({types:[type]}).find(n=>n.name===name);}
function textNodes(n){return !n?[]:n.type==='TEXT'?[n]:'findAllWithCriteria'in n?n.findAllWithCriteria({types:['TEXT']}):[];}
async function loadFonts(root){const fonts=new Map();for(const t of textNodes(root))for(const s of t.getStyledTextSegments(['fontName']))fonts.set(JSON.stringify(s.fontName),s.fontName);await Promise.all([...fonts.values()].map(f=>figma.loadFontAsync(f)));}
async function lookup(theme,suffix,id){const byName=namedNode('FRAME',prefix(theme)+suffix);if(byName)return byName;const n=id?await figma.getNodeByIdAsync(id):null;if(n&&pageOf(n)?.id===page.id)return n;unresolved.push({theme,source:suffix,reason:'Source frame not found'});return null;}
function resolve(theme,target){return namedNode('FRAME',prefix(theme)+(target.startsWith('@')?target.slice(1):'states/'+target));}
async function setReactions(n,value){if(!n||!('reactions'in n))return;if(typeof n.setReactionsAsync==='function')await n.setReactionsAsync(value);else n.reactions=value;mark(n);}
async function route(n,theme,target){const dest=resolve(theme,target);if(!n||!dest){unresolved.push({theme,node:n?n.id:null,target,reason:!n?'Source action not found':'Destination not found'});return;}if(screenOf(n)?.id===dest.id){await setReactions(n,[]);return;}await setReactions(n,[{trigger:{type:'ON_CLICK'},actions:[{type:'NODE',destinationId:dest.id,navigation:'NAVIGATE',transition:null,resetScrollPosition:true}]}]);routes.push({node:n.id,target:dest.id,key:target});}
function bodyOf(root){const viewport=root&&root.findAll(n=>n.name==='Scrollable Android content')[0];if(!viewport)return null;return viewport.children.find(n=>['GoServe legacy content stack','Hug content / wraps and scrolls','Content grows with text'].includes(n.name))||viewport;}
function paint(theme,key){const c=collections.find(c=>c.name==='GoServe / '+theme),v=c&&variables.find(v=>v.variableCollectionId===c.id&&v.name==='color/'+key);if(!v)throw new Error('Missing color '+theme+'/'+key);const value=v.valuesByMode[c.defaultModeId];return figma.variables.setBoundVariableForPaint({type:'SOLID',color:{r:value.r,g:value.g,b:value.b}},'color',v);}
function vertical(parent,name,width=350,gap=8,pad=0){const n=track(figma.createAutoLayout('VERTICAL'));n.name=name;n.resize(width,100);n.primaryAxisSizingMode='AUTO';n.counterAxisSizingMode='FIXED';n.paddingLeft=n.paddingRight=n.paddingTop=n.paddingBottom=pad;n.itemSpacing=gap;n.fills=[];parent.appendChild(n);if(parent.layoutMode&&parent.layoutMode!=='NONE')n.layoutSizingHorizontal='FILL';return n;}
async function newText(parent,name,value,theme,role='Support'){const style=styles[role];if(!style)throw new Error('Missing style '+role);if(style.fontName!==figma.mixed)await figma.loadFontAsync(style.fontName);const t=track(figma.createText());t.name=name;t.textStyleId=style.id;t.resize(Math.max(1,parent.width-(parent.paddingLeft||0)-(parent.paddingRight||0)),100);parent.appendChild(t);t.layoutSizingHorizontal='FILL';t.layoutSizingVertical='HUG';t.textAutoResize='HEIGHT';t.characters=value;t.fills=[paint(theme,role==='Support'?'secondary':'text')];return t;}
function action(root,label){const named=root.findAll(n=>'reactions'in n&&(n.name.endsWith(' | '+label)||n.name==='Action / '+label||n.name==='GoServe / Context action / '+label))[0];if(named)return named;const t=textNodes(root).find(n=>n.characters===label);return t&&t.parent&&t.parent!==root&&'reactions'in t.parent?t.parent:null;}
async function compactAction(parent,theme,label,target){let n=parent.children.find(n=>n.name==='GoServe / Context action / '+label);if(!n){const set=namedNode('COMPONENT_SET','GoServe / Inline action');const c=set&&set.children.find(n=>n.type==='COMPONENT'&&n.name==='Theme='+theme);if(c){await loadFonts(c);n=c.createInstance();parent.appendChild(n);track(n);for(const child of n.findAll(()=>true))track(child);}else{n=vertical(parent,'GoServe / Context action / '+label,350,0,0);n.minHeight=48;n.primaryAxisAlignItems=n.counterAxisAlignItems='CENTER';const t=await newText(n,'label',label,theme,'Label');t.textAlignHorizontal='CENTER';}}await loadFonts(n);n.name='GoServe / Context action / '+label;n.resize(Math.max(1,parent.width-(parent.paddingLeft||0)-(parent.paddingRight||0)),48);if(parent.layoutMode&&parent.layoutMode!=='NONE')n.layoutSizingHorizontal='FILL';const t=textNodes(n).find(t=>t.name==='label')||textNodes(n)[0];if(t){t.characters=label;t.textAutoResize='HEIGHT';mark(t);}await route(n,theme,target);return n;}
async function hideTree(n){if(!n)return;n.visible=false;mark(n);if('reactions'in n)await setReactions(n,[]);for(const child of 'findAll'in n?n.findAll(c=>'reactions'in c&&c.reactions.length>0):[])await setReactions(child,[]);}

for(const theme of ['Light','Dark']){
  const dark=theme==='Dark';
  const staffHome=await lookup(theme,'staff/home',dark?'36:899':'24:131');
  if(staffHome){await loadFonts(staffHome);const body=bodyOf(staffHome);if(body)await compactAction(body,theme,'Service fees','G05.1');else unresolved.push({theme,node:staffHome.id,reason:'Staff Home content stack not found'});}

  const thread=await lookup(theme,'staff/thread',dark?'36:1478':'26:339');
  if(thread){await loadFonts(thread);const body=bodyOf(thread);if(body){const assign=await compactAction(body,theme,'Assign inquiry','G14.1');if(body.layoutMode&&body.layoutMode!=='NONE'){body.insertChild(Math.min(1,body.children.length-1),assign);mark(body);}}else unresolved.push({theme,node:thread.id,reason:'Staff thread content stack not found'});await route(action(thread,'Send office reply')||action(thread,'Send reply'),theme,'G14.staff-sending');}

  const pickup=await lookup(theme,'student/pickup-token',dark?'36:516':'23:171');
  if(pickup){
    await loadFonts(pickup);
    const body=bodyOf(pickup);
    if(!body){unresolved.push({theme,node:pickup.id,reason:'Pickup content stack not found'});continue;}
    const placeholders=pickup.findAll(n=>n.type==='FRAME'&&(/CameraX QR|scanner|QR target|pickup QR/i.test(n.name)));
    for(const n of placeholders)await hideTree(n);
    for(const t of textNodes(pickup)){
      if(t.name==='screen title'){t.characters='Pickup code';t.textAutoResize='HEIGHT';mark(t);}
      else if(t.characters==='Single-use token · valid for this request only'){t.characters='No valid pickup token is loaded.';t.textAutoResize='HEIGHT';mark(t);}
    }
    let demo=body.children.find(n=>n.name==='GoServe / Nonfunctional pickup code');
    if(!demo){demo=vertical(body,'GoServe / Nonfunctional pickup code',350,8,16);demo.cornerRadius=16;demo.fills=[paint(theme,'surface')];demo.strokes=[paint(theme,'border')];demo.strokeWeight=1;demo.strokesIncludedInLayout=false;await newText(demo,'Pickup demo heading','Demo — not valid for pickup',theme,'Label');await newText(demo,'Readable manual code','NOT-A-PICKUP-CODE',theme,'Body');await newText(demo,'No pickup token','No valid token is loaded. The issuing office provides the real code when a request is ready.',theme,'Support');if(body.layoutMode&&body.layoutMode!=='NONE'){body.insertChild(Math.min(1,body.children.length-1),demo);mark(body);}}
    await compactAction(body,theme,'View pickup code example','G11.1');
  }
}

for(const id of created)createdNodeIds.push(id);
for(const id of changed)mutatedNodeIds.push(id);
return{createdNodeIds,mutatedNodeIds,deletedNodeIds,routes,unresolved};
