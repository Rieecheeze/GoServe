// Root injects ROOT_IDS. This script only migrates explicitly selected legacy shells.
await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('14:2'));
const createdNodeIds = [];
const mutated = new Set();
const results = [];
const excludedRoots = new Set([
  '23:285','36:630','23:388','36:733','24:51','36:819',
  '19:93','36:93','127:1124','127:1329','23:32','36:377',
  '127:1254','127:1459','33:54','36:1766','45:2','46:2','45:54','46:30',
  '23:95','36:440','23:144','36:489','106:662','106:663','107:1063','107:1064','19:2','36:2'
]);
const styleRoles = ['Hero','Section','Office','Toolbar','Body','Support','Label','Caption','Navigation'];
const allStyles = await figma.getLocalTextStylesAsync();
const styles = Object.fromEntries(allStyles.filter(s => s.name.startsWith('GoServe / ')).map(s => [s.name.split(' / ')[1],s]));
const missingRoles = styleRoles.filter(role => !styles[role]);
if (missingRoles.length) return {createdNodeIds,mutatedNodeIds:[],findings:[{issue:'Required GoServe text styles missing',roles:missingRoles}]};
const roots = [];
for (const id of [...new Set(ROOT_IDS)]) {
  const root = await figma.getNodeByIdAsync(id);
  if (!root || root.type !== 'FRAME' || root.parent.id !== '14:2') {
    results.push({id,skipped:'Not a top-level current-page frame'}); continue;
  }
  if (excludedRoots.has(id) || /(?:new states\/|ui states\/|G\d{2}\.\d)/i.test(root.name)) {
    results.push({id,name:root.name,skipped:'Protected current composition'}); continue;
  }
  const header = root.children.find(n => n.type === 'FRAME' && n.name === 'Material top app bar');
  const viewport = root.children.find(n => n.type === 'FRAME' && n.name === 'Scrollable Android content');
  if (!header || !viewport || root.children.some(n => /Native Android app bar/i.test(n.name))) {
    results.push({id,name:root.name,skipped:'No recognized legacy Material shell'}); continue;
  }
  roots.push({root,header,viewport});
}
const fonts = new Map();
for (const {root} of roots) for (const text of root.findAllWithCriteria({types:['TEXT']})) {
  for (const segment of text.getStyledTextSegments(['fontName'])) fonts.set(JSON.stringify(segment.fontName),segment.fontName);
}
for (const role of styleRoles) fonts.set(JSON.stringify(styles[role].fontName),styles[role].fontName);
const loaded = await Promise.allSettled([...fonts.values()].map(font => figma.loadFontAsync(font)));
const failedFonts = new Set();
[...fonts.keys()].forEach((key,index) => {if (loaded[index].status === 'rejected') failedFonts.add(key);});
function mark(node) { mutated.add(node.id); }
function descendants(root) { return [root,...root.findAll(() => true)]; }
function frames(root) { return root.findAllWithCriteria({types:['FRAME']}); }
function texts(node) { return node.children.filter(n => n.type === 'TEXT'); }
function isIcon(text) {
  return text.type === 'TEXT' && text.getStyledTextSegments(['fontName']).some(s => /Material Symbols|Material Icons/.test(s.fontName.family));
}
function excludedText(text,root) {
  let node = text;
  while (node && node.id !== root.id) {
    if (node.type === 'INSTANCE' || node.type === 'COMPONENT' || /^(Android status inset|Android gesture inset|gesture handle)$/.test(node.name)) return true;
    node = node.parent;
  }
  return isIcon(text);
}
function protectedContainer(node,root) {
  let current=node;
  while (current && current.id!==root.id) {
    if (current.type==='INSTANCE' || current.type==='COMPONENT' || current.type==='COMPONENT_SET') return true;
    current=current.parent;
  }
  return false;
}
function legacyText(text) {
  return text.getStyledTextSegments(['fontName']).some(s => /^(Roboto|Archivo|Arvo|Lato)$/.test(s.fontName.family));
}
function rect(node) { return [node.parent ? node.parent.id : null,node.x,node.y,node.width,node.height]; }
function sameRect(a,b) { return a && b && a.length === b.length && a.every((value,index) => typeof value === 'number' ? Math.abs(value-b[index]) < 0.01 : value === b[index]); }
function contentWidth(parent) { return Math.max(1,parent.width-(parent.paddingLeft || 0)-(parent.paddingRight || 0)); }
function isAuto(parent) { return parent && 'layoutMode' in parent && parent.layoutMode !== 'NONE'; }
function prepareColumn(node,width,paddingTop,paddingRight,paddingBottom,paddingLeft,gap,minHeight=0) {
  node.layoutMode = 'VERTICAL';
  node.resize(width,Math.max(1,node.height));
  node.primaryAxisSizingMode = 'AUTO'; node.counterAxisSizingMode = 'FIXED';
  node.paddingTop = paddingTop; node.paddingRight = paddingRight;
  node.paddingBottom = paddingBottom; node.paddingLeft = paddingLeft;
  node.itemSpacing = gap; node.primaryAxisAlignItems = 'MIN'; node.counterAxisAlignItems = 'MIN';
  node.minHeight = minHeight; node.clipsContent = false;
  if ('strokesIncludedInLayout' in node) node.strokesIncludedInLayout = false;
  if (isAuto(node.parent)) {node.layoutSizingVertical = 'HUG';}
  mark(node);
}
function makeTextFlow(text,width) {
  text.layoutPositioning = 'AUTO';
  text.resize(Math.max(1,width),Math.max(1,text.height));
  if (isAuto(text.parent)) {
    text.layoutSizingHorizontal = 'FILL'; text.layoutSizingVertical = 'HUG';
  }
  text.textAutoResize = 'HEIGHT';
  if ('textTruncation' in text) text.textTruncation = 'DISABLED';
  mark(text);
}
function ancestorNamed(text,expression) {
  let node = text.parent;
  while (node && node.type !== 'PAGE') {if (expression.test(node.name)) return node; node = node.parent;}
  return null;
}
function roleFor(text,kinds) {
  const parent = text.parent, kind = kinds.get(parent.id);
  if (ancestorNamed(text,/^Material navigation bar$/)) return 'Navigation';
  if (text.name === 'screen title') return 'Toolbar';
  if (text.name === 'headline') return 'Hero';
  if (text.name === 'heading' || text.name === 'state title') return 'Section';
  if (text.name === 'office name') return 'Office';
  if (kind === 'button' || kind === 'choice' || kind === 'status' || kind === 'link') return text.name === 'title' || text.name === 'label' ? 'Label' : 'Support';
  if (kind === 'input') return 'Body';
  if (text.name === 'title') {
    if (/\| summary$/.test(parent.name)) return 'Section';
    return /^notice \//.test(parent.name) ? 'Label' : 'Office';
  }
  if (text.name === 'value' && /^metric \//.test(parent.name)) return 'Hero';
  if (text.name === 'label' && /^field \//.test(parent.name)) return 'Label';
  if (text.name === 'label' && /^chip \//.test(parent.name)) return 'Caption';
  if (/^(eyebrow|meta|campus|sender|section label)$/.test(text.name)) return 'Caption';
  if (/^(detail|description|subtitle|help|announcement|office detail|camera hint)$/.test(text.name)) return 'Support';
  if (text.name === 'label') return 'Label';
  return 'Body';
}
for (const {root,header,viewport} of roots) {
  const report = {id:root.id,name:root.name,converted:{},roles:{},findings:[],findingCount:0};
  const before = new Map(descendants(root).map(node => [node.id,rect(node)]));
  const originalReactions = new Map(descendants(root).filter(node => 'reactions' in node && node.reactions.length).map(node => [node.id,JSON.stringify(node.reactions)]));
  function finding(issue,node) {report.findingCount++; if (report.findings.length < 16) report.findings.push({id:node.id,name:node.name,issue});}
  const hasMissingFont = styleRoles.some(role=>failedFonts.has(JSON.stringify(styles[role].fontName))) || root.findAllWithCriteria({types:['TEXT']}).some(text => text.getStyledTextSegments(['fontName']).some(segment => failedFonts.has(JSON.stringify(segment.fontName))));
  if (hasMissingFont) {report.skipped='An existing font could not be loaded';results.push(report);continue;}
  const kinds = new Map();
  const decorations = [];
  function converted(node,kind) {kinds.set(node.id,kind);report.converted[kind]=(report.converted[kind] || 0)+1;mark(node);}
  function flowKnownWrapper(node,kind,paddingLeft,paddingRight,gap,minHeight,padY=12) {
    const fixed = node.children.filter(child => child.type !== 'TEXT' || isIcon(child));
    const old = fixed.map(child => ({child,x:child.x,y:child.y,w:child.width,h:child.height}));
    prepareColumn(node,node.width,padY,paddingRight,padY,paddingLeft,gap,minHeight);
    for (const {child,x,y,w,h} of old) {
      child.layoutPositioning='ABSOLUTE';
      child.resize(w,h);child.x=x;child.y=y;mark(child);
      decorations.push({parent:node,child,right:x>node.width/2,inset:x>node.width/2 ? Math.max(8,node.width-x-w) : x});
    }
    for (const text of texts(node).filter(t => !isIcon(t))) makeTextFlow(text,contentWidth(node));
    converted(node,kind);
  }
  try {
    // A new inner body lets the existing viewport remain fixed and scrollable.
    let body = viewport.children.find(node => node.type === 'FRAME' && node.name === 'GoServe legacy content stack');
    if (!body) {
      const children = [...viewport.children];
      const padding = {top:viewport.paddingTop,right:viewport.paddingRight,bottom:viewport.paddingBottom,left:viewport.paddingLeft};
      const gap = viewport.itemSpacing;
      body = figma.createAutoLayout('VERTICAL');body.name='GoServe legacy content stack';body.fills=[];createdNodeIds.push(body.id);
      body.resize(viewport.width,1);body.primaryAxisSizingMode='AUTO';body.counterAxisSizingMode='FIXED';
      body.paddingTop=padding.top;body.paddingRight=padding.right;body.paddingBottom=Math.max(24,padding.bottom);body.paddingLeft=padding.left;body.itemSpacing=gap;body.clipsContent=false;
      viewport.appendChild(body);
      for (const child of children) {body.appendChild(child);mark(child);}
      mark(viewport);
    }
    const viewportHeight=viewport.height;
    viewport.layoutMode='VERTICAL';viewport.resize(root.width,viewportHeight);
    viewport.primaryAxisSizingMode='FIXED';viewport.counterAxisSizingMode='FIXED';
    viewport.paddingTop=viewport.paddingRight=viewport.paddingBottom=viewport.paddingLeft=0;viewport.itemSpacing=0;
    viewport.clipsContent=true;viewport.overflowDirection='VERTICAL';
    body.resize(viewport.width,Math.max(1,body.height));body.layoutSizingHorizontal='FILL';body.layoutSizingVertical='HUG';mark(body);mark(viewport);
    for (const child of body.children) {
      if (Math.abs(child.width-contentWidth(body))<3) {child.layoutSizingHorizontal='FILL';mark(child);}
    }
    // Convert only the old generator's exact names and direct-text signatures.
    for (const node of frames(body)) {
      if (protectedContainer(node,root)) continue;
      const direct=texts(node).filter(text => !isIcon(text));
      const names=new Set(direct.map(text => text.name));
      if (!direct.length || !direct.some(legacyText)) continue;
      if (/^status \//.test(node.name) && names.has('title') && names.has('detail')) {
        flowKnownWrapper(node,'status',38,16,4,64);continue;
      }
      if (/^input \//.test(node.name) && names.has('value')) {
        const right=node.children.some(child => isIcon(child));flowKnownWrapper(node,'input',16,right?52:16,0,48,12);continue;
      }
      const choice=node.children.some(child => child.name==='radio') && names.has('title') && names.has('detail');
      if (choice) {flowKnownWrapper(node,'choice',48,16,4,64);continue;}
      if (/^go:/.test(node.name) && names.has('title') && names.has('detail') && node.layoutMode==='NONE' && !names.has('eyebrow')) {
        flowKnownWrapper(node,'link',16,52,4,64);continue;
      }
      if (/^go:/.test(node.name) && names.size===1 && names.has('label') && node.layoutMode==='NONE') {
        const left=node.children.some(child => isIcon(child));flowKnownWrapper(node,'button',left?48:14,14,0,48,14);
        for (const text of direct) {text.textAlignHorizontal=left?'LEFT':'CENTER';mark(text);}continue;
      }
      if (/^(notice \/|section \/|field \/|message \/|metric \/|state \/|hero$|status history$)/.test(node.name) || /\| summary$/.test(node.name)) {
        if (node.layoutMode==='VERTICAL') {
          node.primaryAxisSizingMode='AUTO';node.counterAxisSizingMode='FIXED';node.clipsContent=false;
          if (isAuto(node.parent)) node.layoutSizingVertical='HUG';
          for (const text of direct) makeTextFlow(text,contentWidth(node));
          converted(node,'stack');
        }
      }
    }
    // Root-level legacy footer uses the same helper but is outside the body.
    const footer=root.children.find(node => node.type==='FRAME' && /^(go:|GoServe \/ Route action \/)/.test(node.name) && texts(node).some(text => text.name==='label' && !isIcon(text)) && node.y>root.height/2);
    if (footer && footer.layoutMode==='NONE') {
      const left=footer.children.some(child => isIcon(child));flowKnownWrapper(footer,'button',left?48:14,14,0,48,14);
      for (const text of texts(footer).filter(text=>!isIcon(text))) {text.textAlignHorizontal=left?'LEFT':'CENTER';mark(text);}
    }
    // Keep back/right wrappers and reactions while allowing the toolbar title to wrap.
    const headerChildren=[...header.children];
    header.layoutMode='HORIZONTAL';header.resize(root.width,header.height);
    header.primaryAxisSizingMode='FIXED';header.counterAxisSizingMode='AUTO';header.minHeight=64;
    header.paddingTop=header.paddingBottom=8;header.paddingLeft=headerChildren.some(node=>/^go:.*\| Back$/.test(node.name))?8:20;header.paddingRight=12;header.itemSpacing=8;header.counterAxisAlignItems='CENTER';header.clipsContent=false;
    for (const node of headerChildren) {
      node.layoutPositioning='AUTO';
      if (node.type==='TEXT' && node.name==='screen title') {
        const otherWidth=headerChildren.filter(child=>child.id!==node.id).reduce((sum,child)=>sum+child.width,0);
        makeTextFlow(node,Math.max(1,root.width-header.paddingLeft-header.paddingRight-otherWidth-header.itemSpacing*(headerChildren.length-1)));
      } else {node.layoutSizingHorizontal='FIXED';node.layoutSizingVertical='FIXED';mark(node);}
    }
    converted(header,'toolbar');
    // Style only ordinary legacy text whose parent can accommodate its natural height.
    for (const text of root.findAllWithCriteria({types:['TEXT']})) {
      if (excludedText(text,root) || !legacyText(text)) continue;
      const role=roleFor(text,kinds);
      const navigation=ancestorNamed(text,/^Material navigation bar$/);
      if (!isAuto(text.parent) && !navigation) {finding('Manual container not safely recognized; typography left unchanged',text);continue;}
      text.textStyleId=styles[role].id;
      if (navigation) {
        const width=text.width;text.resize(width,Math.max(1,text.height));text.textAutoResize='HEIGHT';mark(text);
      } else makeTextFlow(text,contentWidth(text.parent));
      report.roles[role]=(report.roles[role] || 0)+1;
    }
    // Style application changes row heights; recenter fixed decorations afterward.
    for (const {parent,child,right,inset} of decorations) {
      child.x=right?parent.width-inset-child.width:inset;
      child.y=Math.max(0,(parent.height-child.height)/2);mark(child);
    }
    const gesture=root.children.find(node=>node.name==='Android gesture inset');
    const bottom=gesture?gesture.y:root.height-24;
    if (footer) {footer.x=(root.width-footer.width)/2;footer.y=bottom-footer.height-14;mark(footer);}
    const navigation=root.children.find(node=>node.name==='Material navigation bar' || (node.type==='INSTANCE' && /Capsule navigation/.test(node.name)));
    const contentBottom=footer?footer.y-10:navigation?navigation.y-12:bottom;
    viewport.y=header.y+header.height;
    viewport.resize(root.width,Math.max(48,contentBottom-viewport.y));
    viewport.primaryAxisSizingMode='FIXED';viewport.counterAxisSizingMode='FIXED';
    body.layoutSizingHorizontal='FILL';body.layoutSizingVertical='HUG';mark(viewport);mark(body);mark(header);mark(root);
    for (const node of descendants(root)) {
      if (before.has(node.id) && !sameRect(before.get(node.id),rect(node))) mark(node);
      if (node.type==='TEXT' && !excludedText(node,root) && node.parent && 'clipsContent' in node.parent && node.parent.clipsContent && node.parent.id!==viewport.id && node.y+node.height>node.parent.height+0.5) finding('Text extends below a clipped parent',node);
      if (node.parent && node.parent.id!==root.id && node.x+node.width>node.parent.width+0.5 && node.type!=='VECTOR') finding('Child extends beyond parent right edge',node);
    }
    for (const [id,reactions] of originalReactions) {
      const node=await figma.getNodeByIdAsync(id);
      if (!node || JSON.stringify(node.reactions)!==reactions) finding('Existing reaction changed unexpectedly',node || root);
    }
    report.bounds={headerHeight:header.height,viewportY:viewport.y,viewportHeight:viewport.height,bodyHeight:body.height,scrolls:body.height>viewport.height};
  } catch (error) {
    report.error=String(error);report.partialMutation=true;
    for (const node of descendants(root)) if (before.has(node.id) && !sameRect(before.get(node.id),rect(node))) mark(node);
  }
  results.push(report);
}
return {createdNodeIds,mutatedNodeIds:[...mutated],failedFonts:[...failedFonts].map(key=>JSON.parse(key)),roots:results};
