// Read-only. Root injects ROOT_IDS, including temporary responsive QA clones.
await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('14:2'));
const reports=[],brandExceptionIds=[];
const allowedBrandIds=new Set(typeof BRAND_EXCEPTION_IDS==='undefined'?[]:BRAND_EXCEPTION_IDS);
let totalTextCount=0,totalIssueCount=0,totalLegacyCount=0;
function visible(node,root){let n=node;while(n&&n.id!==root.id){if('visible'in n&&!n.visible)return false;n=n.parent;}return root.visible;}
function os(node,root){let n=node;while(n&&n.id!==root.id){if(/^(Android status inset|Android gesture inset|gesture handle)$/.test(n.name))return true;n=n.parent;}return false;}
function material(text){return text.getStyledTextSegments(['fontName']).some(s=>/Material Symbols|Material Icons/.test(s.fontName.family));}
function icon(node){return ['VECTOR','BOOLEAN_OPERATION','LINE','ELLIPSE'].includes(node.type)||/^(Icon\s*\/|Nav icon|Navigation icon|Selected indicator|Selected circle|Empty state marker)/i.test(node.name);}
function svgChild(node){let n=node;while(n&&n.type!=='PAGE'){if(/^(Icon\s*\/|Nav icon|Navigation icon)/i.test(n.name))return true;n=n.parent;}return false;}
function decorativeAbsolute(node){return node.layoutPositioning==='ABSOLUTE'&&node.type!=='TEXT'&&(!('reactions'in node)||node.reactions.length===0);}
function viewport(node){return 'overflowDirection'in node&&['VERTICAL','HORIZONTAL','BOTH'].includes(node.overflowDirection);}
function center(node){const m=node.absoluteTransform;return{x:m[0][0]*node.width/2+m[0][1]*node.height/2+m[0][2],y:m[1][0]*node.width/2+m[1][1]*node.height/2+m[1][2]};}
function delta(a,b){const x=center(a),y=center(b);return Math.max(Math.abs(x.x-y.x),Math.abs(x.y-y.y));}
for(const id of [...new Set(ROOT_IDS)]){
  const root=await figma.getNodeByIdAsync(id);
  if(!root||root.type!=='FRAME'){reports.push({id,skipped:'Not a frame'});continue;}
  const report={id,name:root.name,textCount:0,issueCount:0,counts:{},findings:[],legacy:{count:0,families:{},samples:[]}};
  const issue=(kind,node,detail)=>{report.issueCount++;report.counts[kind]=(report.counts[kind]||0)+1;if(report.findings.length<25)report.findings.push({kind,id:node.id,name:node.name,...detail});};
  const all=root.findAll(()=>true),ordinary=[];
  for(const text of root.findAllWithCriteria({types:['TEXT']})){
    if(!visible(text,root)||os(text,root)||material(text)||svgChild(text))continue;
    ordinary.push(text);report.textCount++;
    if(text.characters.trim()&&(text.width<=1||text.height<=1))issue('collapsedText',text,{width:text.width,height:text.height});
    const p=text.parent;
    if(p&&'clipsContent'in p&&p.clipsContent&&!viewport(p)&&(text.x<-.5||text.y<-.5||text.x+text.width>p.width+.5||text.y+text.height>p.height+.5))issue('textClippedByParent',text,{parent:p.id,textBottom:text.y+text.height,parentHeight:p.height});
    if(allowedBrandIds.has(text.id)&&text.parent?.name==='Google monogram'&&text.characters==='G'){brandExceptionIds.push(text.id);continue;}
    const families=[...new Set(text.getStyledTextSegments(['fontName']).map(s=>s.fontName.family))].filter(f=>!['Montserrat','Karla'].includes(f));
    if(families.length){report.legacy.count++;for(const f of families)report.legacy.families[f]=(report.legacy.families[f]||0)+1;if(report.legacy.samples.length<5)report.legacy.samples.push({id:text.id,name:text.name,families});}
  }
  for(const parent of [root,...all]){
    if(!visible(parent,root)||os(parent,root)||!('layoutMode'in parent)||parent.layoutMode==='NONE'||!('children'in parent)||svgChild(parent))continue;
    if('overflowDirection'in parent&&['HORIZONTAL','BOTH'].includes(parent.overflowDirection))continue;
    for(const child of parent.children){
      if(!visible(child,root)||icon(child)||svgChild(child)||decorativeAbsolute(child))continue;
      if(child.x<-.5||child.x+child.width>parent.width+.5)issue('horizontalOverflow',child,{parent:parent.id,x:child.x,width:child.width,parentWidth:parent.width});
    }
  }
  const navTabs=all.filter(n=>n.name.startsWith('Tab / ')&&visible(n,root));
  for(const tab of navTabs){
    if(tab.width<47.5||tab.height<47.5)issue('smallNavigationTarget',tab,{width:tab.width,height:tab.height});
    if(!('findAll'in tab))continue;
    const nodes=tab.findAll(n=>visible(n,root)),mark=nodes.find(n=>/^(Icon\s*\/|Nav icon|Navigation icon)/i.test(n.name));
    const selected=nodes.find(n=>n.type==='ELLIPSE'||/Selected.*(?:indicator|circle|destination)/i.test(n.name));
    if(mark){
      if(delta(mark,tab)>1)issue('navigationIconNotCentered',mark,{tab:tab.id,offset:delta(mark,tab)});
      if(Math.abs(mark.width-22)>.5||Math.abs(mark.height-22)>.5)issue('navigationIconSize',mark,{width:mark.width,height:mark.height});
    }
    if(selected){
      if(Math.abs(selected.width-36)>.5||Math.abs(selected.height-36)>.5)issue('selectedIndicatorSize',selected,{width:selected.width,height:selected.height});
      if(mark&&delta(selected,mark)>1)issue('selectedIndicatorNotCentered',selected,{icon:mark.id,offset:delta(selected,mark)});
    }
  }
  const slots=root.children.filter(n=>visible(n,root)&&/^(Android status inset|Native Android app bar|Material top app bar|Scrollable Android content|Scrollable viewport|Seryo composer host|Role navigation host|Android gesture inset)$/.test(n.name)).sort((a,b)=>a.y-b.y);
  for(let i=1;i<slots.length;i++)if(slots[i].y<slots[i-1].y+slots[i-1].height-.5)issue('rootSlotOverlap',slots[i],{previous:slots[i-1].id,previousBottom:slots[i-1].y+slots[i-1].height,y:slots[i].y});
  const scroll=slots.find(n=>/^Scrollable/.test(n.name));
  if(scroll&&scroll.height<=0)issue('nonpositiveViewport',scroll,{height:scroll.height});
  report.viewportHeight=scroll?scroll.height:null;report.navigationTargetCount=navTabs.length;
  totalTextCount+=report.textCount;totalIssueCount+=report.issueCount;totalLegacyCount+=report.legacy.count;reports.push(report);
}
return{brandExceptionIds,rootCount:reports.length,totalTextCount,totalIssueCount,totalLegacyCount,passedRootCount:reports.filter(r=>!r.issueCount&&!r.legacy?.count&&!r.skipped).length,reports:reports.filter(r=>r.issueCount||r.legacy?.count||r.skipped)};
