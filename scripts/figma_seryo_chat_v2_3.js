// Native Figma chat composition; execute through use_figma on page 14:2.
await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('14:2'));
const createdNodeIds = [], mutatedNodeIds = [], deletedNodeIds = [];
const roots = await Promise.all(['24:51','36:819'].map(id=>figma.getNodeByIdAsync(id)));
const styles = Object.fromEntries((await figma.getLocalTextStylesAsync()).filter(s=>s.name.startsWith('GoServe / ')).map(s=>[s.name.split(' / ')[1],s.id]));
const collections = await figma.variables.getLocalVariableCollectionsAsync();
const variables = await figma.variables.getLocalVariablesAsync();
const fonts = new Map();
for(const root of roots)for(const t of root.findAllWithCriteria({types:['TEXT']}))for(const s of t.getStyledTextSegments(['fontName']))fonts.set(JSON.stringify(s.fontName),s.fontName);
for(const f of [{family:'Montserrat',style:'ExtraBold'},{family:'Karla',style:'Regular'},{family:'Karla',style:'SemiBold'}])fonts.set(JSON.stringify(f),f);
await Promise.all([...fonts.values()].map(f=>figma.loadFontAsync(f)));
const record=n=>(createdNodeIds.push(n.id),n);
function paint(theme,key){const c=collections.find(c=>c.name==='GoServe / '+theme);const v=variables.find(v=>v.variableCollectionId===c.id&&v.name==='color/'+key);const value=v.valuesByMode[c.defaultModeId];return figma.variables.setBoundVariableForPaint({type:'SOLID',color:{r:value.r,g:value.g,b:value.b}},'color',v);}
function box(parent,name,direction,width,gap=0,padding=0){const n=record(figma.createAutoLayout(direction));n.name=name;n.resize(width,100);n.primaryAxisSizingMode='AUTO';n.counterAxisSizingMode='FIXED';n.itemSpacing=gap;n.paddingLeft=n.paddingRight=n.paddingTop=n.paddingBottom=padding;n.fills=[];parent.appendChild(n);return n;}
function txt(parent,name,value,role,theme,color='text',align='LEFT'){const n=record(figma.createText());n.name=name;n.textStyleId=styles[role];n.fills=[paint(theme,color)];n.resize(Math.max(1,parent.width-parent.paddingLeft-parent.paddingRight),100);parent.appendChild(n);n.layoutSizingHorizontal='FILL';n.layoutSizingVertical='HUG';n.textAutoResize='HEIGHT';n.textAlignHorizontal=align;n.characters=value;return n;}
function route(n,id){n.reactions=[{trigger:{type:'ON_CLICK'},actions:[{type:'NODE',destinationId:id,navigation:'NAVIGATE',transition:null,resetScrollPosition:true}]}];}
function icon(parent,name,path,size,theme,color='secondary'){const c=paint(theme,color).color;const hex='#'+[c.r,c.g,c.b].map(x=>Math.round(x*255).toString(16).padStart(2,'0')).join('');const n=figma.createNodeFromSvg('<svg width="'+size+'" height="'+size+'" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="'+path+'" fill="none" stroke="'+hex+'" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/></svg>');n.name=name;parent.appendChild(n);createdNodeIds.push(n.id,...n.findAll(()=>true).map(c=>c.id));for(const p of n.findAll(()=>true))if('strokes' in p&&p.strokes.length)p.strokes=[paint(theme,color)];return n;}
const paths={privacy:'M12 3L20 6V11C20 16 17 19 12 21C7 19 4 16 4 11V6L12 3Z M12 8V12 M12 16H12.01',ai:'M8 4H18A3 3 0 0 1 21 7V16A3 3 0 0 1 18 19H10L5 22V18A3 3 0 0 1 2 15V10 M5 2L6 5L9 6L6 7L5 10L4 7L1 6L4 5Z M9 10H16 M9 14H13',attach:'M12 5V19 M5 12H19',send:'M12 19V5 M6 11L12 5L18 11'};
const contracts=[];
for(const [index,root] of roots.entries()){
 const theme=index?'Dark':'Light',privacy=index?'97:162':'95:2',upload=index?'36:850':'24:82',offices=index?'36:1766':'33:54';
 const nav=root.children.find(n=>n.type==='INSTANCE'&&n.name.startsWith('GoServe / Capsule'));
 if(!nav)throw new Error('Missing retained navigation on '+root.id);
 for(const child of [...root.children])if(child!==nav){deletedNodeIds.push(child.id,...child.findAll(()=>true).map(n=>n.id));child.remove();}
 root.fills=[paint(theme,'background')];root.clipsContent=true;mutatedNodeIds.push(root.id);
 const header=box(root,'Seryo / Header','HORIZONTAL',350,8);header.x=20;header.y=24;header.resize(350,64);header.primaryAxisSizingMode='FIXED';header.counterAxisSizingMode='AUTO';header.minHeight=64;header.counterAxisAlignItems='CENTER';
 const titles=box(header,'Seryo / Header titles','VERTICAL',294,2);titles.layoutSizingHorizontal='FILL';
 txt(titles,'Seryo / Toolbar title','Seryo','Toolbar',theme);
 txt(titles,'Seryo / Setup status','eGovAI · Setup pending','Caption',theme,'secondary');
 const privacyAction=box(header,'Action / Review AI privacy','HORIZONTAL',48);privacyAction.resize(48,48);privacyAction.primaryAxisSizingMode='FIXED';privacyAction.counterAxisSizingMode='FIXED';privacyAction.primaryAxisAlignItems='CENTER';privacyAction.counterAxisAlignItems='CENTER';privacyAction.cornerRadius=24;route(privacyAction,privacy);icon(privacyAction,'Icon / Privacy',paths.privacy,22,theme);
 const chat=box(root,'Seryo / Chat layout','VERTICAL',350,16);chat.x=20;chat.y=104;chat.resize(350,638);chat.primaryAxisSizingMode='FIXED';chat.counterAxisSizingMode='FIXED';chat.constraints={horizontal:'STRETCH',vertical:'STRETCH'};
 const viewport=box(chat,'Seryo / Scrollable welcome viewport','VERTICAL',350);viewport.resize(350,400);viewport.primaryAxisSizingMode='FIXED';viewport.layoutSizingHorizontal='FILL';viewport.layoutSizingVertical='FILL';viewport.clipsContent=true;viewport.overflowDirection='VERTICAL';
 const center=box(viewport,'Seryo / Centered welcome fill','VERTICAL',350);center.layoutSizingHorizontal='FILL';center.primaryAxisAlignItems='CENTER';center.counterAxisAlignItems='CENTER';
 const welcome=box(center,'Seryo / Welcome','VERTICAL',310,16);welcome.maxWidth=310;welcome.layoutSizingHorizontal='FILL';welcome.counterAxisAlignItems='CENTER';welcome.paddingTop=welcome.paddingBottom=12;
 icon(welcome,'Icon / Muted Seryo welcome',paths.ai,48,theme,'secondary');
 txt(welcome,'Seryo / Welcome heading','How can Seryo help you?','Hero',theme,'text','CENTER');
 txt(welcome,'Seryo / Welcome guidance','Find campus offices, explore services, and understand the next step for a request.','Support',theme,'secondary','CENTER');
 const browse=box(welcome,'Action / Browse offices','HORIZONTAL',310);browse.resize(310,48);browse.primaryAxisSizingMode='FIXED';browse.counterAxisSizingMode='FIXED';browse.layoutSizingHorizontal='FILL';browse.counterAxisAlignItems='CENTER';browse.primaryAxisAlignItems='CENTER';route(browse,offices);txt(browse,'Browse offices action label','Browse offices','Label',theme,'text','CENTER');
 const dock=box(chat,'Seryo / Bottom composer dock','VERTICAL',350,8);dock.layoutSizingHorizontal='FILL';
 const composer=box(dock,'Seryo / Composer • setup pending','VERTICAL',350,8,12);composer.layoutSizingHorizontal='FILL';composer.cornerRadius=24;composer.fills=[paint(theme,'surface')];composer.strokes=[paint(theme,'border')];composer.strokeWeight=1;
 const draft=box(composer,'Seryo / Question field • unavailable','VERTICAL',326);draft.minHeight=48;draft.layoutSizingHorizontal='FILL';txt(draft,'Seryo / Question placeholder','Ask about a campus service…','Body',theme,'secondary');
 const controls=box(composer,'Seryo / Composer controls','HORIZONTAL',326,8);controls.resize(326,48);controls.primaryAxisSizingMode='FIXED';controls.counterAxisSizingMode='FIXED';controls.layoutSizingHorizontal='FILL';controls.counterAxisAlignItems='CENTER';
 const attach=box(controls,'Action / Attachment information','HORIZONTAL',48);attach.resize(48,48);attach.primaryAxisSizingMode='FIXED';attach.counterAxisSizingMode='FIXED';attach.primaryAxisAlignItems=attach.counterAxisAlignItems='CENTER';attach.cornerRadius=24;route(attach,upload);icon(attach,'Icon / Attach',paths.attach,24,theme,'text');
 const spacer=box(controls,'Seryo / Flexible composer space','HORIZONTAL',214);spacer.resize(214,48);spacer.layoutSizingHorizontal='FILL';
 const send=box(controls,'Action / Send • disabled','HORIZONTAL',48);send.resize(48,48);send.primaryAxisSizingMode='FIXED';send.counterAxisSizingMode='FIXED';send.primaryAxisAlignItems=send.counterAxisAlignItems='CENTER';send.cornerRadius=24;send.fills=[paint(theme,'muted')];icon(send,'Icon / Send disabled',paths.send,24,theme,'secondary');
 txt(dock,'Seryo / No-send setup note','Questions are not sent while setup is pending.','Caption',theme,'secondary','CENTER');
 center.minHeight=viewport.height;
 contracts.push({theme,rootId:root.id,root:{w:root.width,h:root.height},headerId:header.id,header:{x:header.x,y:header.y,w:header.width,h:header.height},chatId:chat.id,chat:{x:chat.x,y:chat.y,w:chat.width,h:chat.height},welcomeViewportId:viewport.id,welcomeViewport:{w:viewport.width,h:viewport.height},centerId:center.id,welcomeId:welcome.id,welcome:{w:welcome.width,h:welcome.height},dockId:dock.id,dock:{y:chat.y+dock.y,w:dock.width,h:dock.height},composerId:composer.id,composer:{w:composer.width,h:composer.height,radius:composer.cornerRadius},draftId:draft.id,privacyActionId:privacyAction.id,attachmentActionId:attach.id,sendDisabledId:send.id,browseActionId:browse.id,navId:nav.id,nav:{x:nav.x,y:nav.y,w:nav.width,h:nav.height},routes:{privacy,upload,offices}});
}
return {createdNodeIds,mutatedNodeIds,deletedNodeIds,contracts};
