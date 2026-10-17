await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('14:2'));
const createdNodeIds=[],mutatedNodeIds=[];
const styles=Object.fromEntries((await figma.getLocalTextStylesAsync()).filter(s=>s.name.startsWith('GoServe / ')).map(s=>[s.name.split(' / ')[1],s.id]));
const variables=await figma.variables.getLocalVariablesAsync(),collections=await figma.variables.getLocalVariableCollectionsAsync();
function paint(theme,key){const collection=collections.find(c=>c.name==='GoServe / '+theme),variable=variables.find(v=>v.variableCollectionId===collection.id&&v.name==='color/'+key),value=Object.values(variable.valuesByMode)[0];return figma.variables.setBoundVariableForPaint({type:'SOLID',color:{r:value.r,g:value.g,b:value.b}},'color',variable)}
async function loadCurrent(node){const fonts=new Map();for(const t of node.findAllWithCriteria({types:['TEXT']}))for(const s of t.getStyledTextSegments(['fontName']))fonts.set(JSON.stringify(s.fontName),s.fontName);await Promise.all([...fonts.values()].map(f=>figma.loadFontAsync(f)))}
await Promise.all([{family:'Karla',style:'Regular'},{family:'Karla',style:'SemiBold'}].map(f=>figma.loadFontAsync(f)));
for(const [id,theme]of [['19:321','Light'],['36:319','Dark']]){
 const card=await figma.getNodeByIdAsync(id);await loadCurrent(card);card.layoutMode='VERTICAL';card.primaryAxisSizingMode='AUTO';card.counterAxisSizingMode='FIXED';card.paddingLeft=34;card.paddingRight=14;card.paddingTop=card.paddingBottom=12;card.itemSpacing=4;card.fills=[paint(theme,'surface')];card.strokes=[paint(theme,'border')];card.strokeWeight=1;
 for(const dot of card.children.filter(c=>c.type!=='TEXT')){dot.layoutPositioning='ABSOLUTE';dot.x=14;dot.y=18;mutatedNodeIds.push(dot.id)}
 const texts=card.children.filter(c=>c.type==='TEXT');for(const [index,t]of texts.entries()){t.textStyleId=styles[index===0?'Label':'Support'];if(index===0)t.characters='Pending';t.fills=[paint(theme,index===0?'text':'secondary')];t.resize(card.width-48,100);t.layoutSizingHorizontal='FILL';t.layoutSizingVertical='HUG';t.textAutoResize='HEIGHT';mutatedNodeIds.push(t.id)}mutatedNodeIds.push(card.id);
}
function compactPill(target,width){
 const labels=target.findAllWithCriteria({types:['TEXT']});let pill=target.children.find(n=>n.name==='Visual filter pill');
 if(!pill){pill=figma.createAutoLayout('VERTICAL');pill.name='Visual filter pill';pill.fills=target.fills;pill.strokes=target.strokes;pill.strokeWeight=target.strokeWeight;pill.cornerRadius=16;target.appendChild(pill);createdNodeIds.push(pill.id);for(const text of labels)pill.appendChild(text)}
 target.fills=[];target.strokes=[];target.paddingLeft=target.paddingRight=target.paddingTop=target.paddingBottom=0;target.layoutMode='VERTICAL';target.resize(width,48);target.primaryAxisSizingMode='FIXED';target.counterAxisSizingMode='FIXED';target.primaryAxisAlignItems='CENTER';target.counterAxisAlignItems='CENTER';
 pill.resize(width,32);pill.primaryAxisSizingMode='FIXED';pill.counterAxisSizingMode='FIXED';pill.primaryAxisAlignItems='CENTER';pill.counterAxisAlignItems='CENTER';pill.paddingLeft=pill.paddingRight=8;pill.paddingTop=pill.paddingBottom=0;
 for(const text of labels){text.textStyleId=styles.Label;text.resize(width-16,100);text.layoutSizingHorizontal='FILL';text.layoutSizingVertical='HUG';text.textAutoResize='HEIGHT';text.textAlignHorizontal='CENTER';mutatedNodeIds.push(text.id)}mutatedNodeIds.push(target.id,pill.id);
}
for(const id of ['33:54','45:2','45:54','36:1766','46:2','46:30']){
 const screen=await figma.getNodeByIdAsync(id);await loadCurrent(screen);
 const search=screen.findAllWithCriteria({types:['FRAME']}).find(n=>n.name==='Search offices or services');if(search){search.minHeight=48;search.paddingTop=search.paddingBottom=14;search.primaryAxisSizingMode='AUTO';search.cornerRadius=24;search.strokesIncludedInLayout=false;mutatedNodeIds.push(search.id)}
 for(const target of screen.findAllWithCriteria({types:['FRAME']}).filter(n=>n.name.startsWith('Filter / ')))compactPill(target,target.width);
}
for(const id of ['24:239','36:1007']){
 const row=await figma.getNodeByIdAsync(id);await loadCurrent(row);const targets=[...row.children];const natural=[];
 for(const target of targets){const t=target.findAllWithCriteria({types:['TEXT']})[0];t.textStyleId=styles.Label;t.textAutoResize='WIDTH_AND_HEIGHT';natural.push(t.width+24)}
 const available=row.width-8*(targets.length-1),extra=(available-natural.reduce((a,b)=>a+b,0))/targets.length;
 row.itemSpacing=8;row.primaryAxisSizingMode='FIXED';row.counterAxisSizingMode='AUTO';row.paddingLeft=row.paddingRight=0;
 targets.forEach((target,index)=>compactPill(target,natural[index]+extra));mutatedNodeIds.push(row.id);
}
return{createdNodeIds,mutatedNodeIds,statusCards:(await Promise.all(['19:321','36:319'].map(id=>figma.getNodeByIdAsync(id)))).map(n=>({id:n.id,w:n.width,h:n.height,text:n.children.filter(c=>c.type==='TEXT').map(t=>({id:t.id,h:t.height,bottom:t.y+t.height}))})),queueRows:(await Promise.all(['24:239','36:1007'].map(id=>figma.getNodeByIdAsync(id)))).map(n=>({id:n.id,w:n.width,h:n.height,lastRight:n.children[n.children.length-1].x+n.children[n.children.length-1].width}))};
