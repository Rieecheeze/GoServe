await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('14:2'));
const mutated=new Set(),page=figma.currentPage;
const fonts=new Map();for(const t of page.findAllWithCriteria({types:['TEXT']}))for(const s of t.getStyledTextSegments(['fontName']))fonts.set(JSON.stringify(s.fontName),s.fontName);
await Promise.all([...fonts.values()].map(f=>figma.loadFontAsync(f)));
function mark(n){mutated.add(n.id);}
function target(n,min=48){if(!('layoutMode'in n)||n.layoutMode==='NONE')return;n.primaryAxisSizingMode='AUTO';n.minHeight=min;n.clipsContent=false;if(n.parent&&'layoutMode'in n.parent&&n.parent.layoutMode!=='NONE')n.layoutSizingVertical='HUG';mark(n);}
function filter(n){target(n);const pill=n.children.find(n=>n.name==='Visual filter pill');if(pill&&n.type!=='INSTANCE'){target(pill,32);pill.paddingTop=pill.paddingBottom=6;mark(pill);}const row=n.parent;if(row&&row.layoutMode==='HORIZONTAL'){row.counterAxisSizingMode='AUTO';if(row.parent?.layoutMode&&row.parent.layoutMode!=='NONE')row.layoutSizingVertical='HUG';mark(row);const viewport=row.parent;if(viewport&&viewport.overflowDirection==='HORIZONTAL'){viewport.primaryAxisSizingMode='AUTO';viewport.minHeight=48;mark(viewport);}}}
for(const set of page.children.filter(n=>n.type==='COMPONENT_SET'&&['GoServe / Search control','GoServe / Filter control','GoServe / Inline action control'].includes(n.name)))for(const c of set.children){if(set.name.includes('Filter'))filter(c);else target(c);}
for(const root of page.children.filter(n=>n.type==='FRAME'&&n.visible&&n.name.startsWith('GoServe 2.1'))){
  for(const n of root.findAll(n=>(n.type==='FRAME'||n.type==='INSTANCE')&&(n.name==='Search offices or services'||n.name.startsWith('Filter / ')||n.name==='See all'||n.name.startsWith('GoServe / Context action /')||n.name.startsWith('GoServe / Route action /')))){if(n.name.startsWith('Filter / '))filter(n);else target(n);}
  if(root.children.some(n=>n.name==='Material top app bar'))for(const n of root.children){
    if(n.name==='Material top app bar'||n.name==='Android status inset'){n.constraints={horizontal:'STRETCH',vertical:'MIN'};mark(n);}
    else if(n.name==='Scrollable Android content'){n.constraints={horizontal:'STRETCH',vertical:'STRETCH'};mark(n);}
    else if(n.name==='Android gesture inset'||n.name==='GoServe / Role navigation host'||(n.type==='INSTANCE'&&/navigation/i.test(n.name))){n.constraints={horizontal:'STRETCH',vertical:'MAX'};mark(n);}
    else if(n.type==='FRAME'&&n.y>root.height/2&&/^(go:|GoServe \/ Route action \/)/.test(n.name)){n.constraints={horizontal:'STRETCH',vertical:'MAX'};mark(n);}
  }
}
return{mutatedNodeIds:[...mutated]};
