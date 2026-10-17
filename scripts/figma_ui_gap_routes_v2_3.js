// Figma-only routing correction. Execute after rendering G01–G14 and extra states.
await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('14:2'));
const page = figma.currentPage;
const createdNodeIds = [], mutatedNodeIds = [], deletedNodeIds = [], unresolved = [];
const archiveRecommendation = [], routeChanges = [], hiddenLegacyRows = [];
const changed = new Set(), created = new Set();
const prefix = theme => theme === 'Dark' ? 'GoServe 2.1 • dark/' : 'GoServe 2.1 • ';
const styles = Object.fromEntries((await figma.getLocalTextStylesAsync()).filter(s => s.name.startsWith('GoServe / ')).map(s => [s.name.split(' / ')[1], s]));
const collections = await figma.variables.getLocalVariableCollectionsAsync();
const variables = await figma.variables.getLocalVariablesAsync();

// Existing text may still contain legacy faces or icon fonts; load those faces first.
const fonts = new Map();
for (const t of page.findAllWithCriteria({types:['TEXT']})) {
  for (const segment of t.getStyledTextSegments(['fontName'])) fonts.set(JSON.stringify(segment.fontName), segment.fontName);
}
for (const style of Object.values(styles)) if (style.fontName && style.fontName !== figma.mixed) fonts.set(JSON.stringify(style.fontName), style.fontName);
await Promise.all([...fonts.values()].map(font => figma.loadFontAsync(font)));

function mutate(node) { if (node && !created.has(node.id)) changed.add(node.id); return node; }
function track(node) { created.add(node.id); return node; }
function pageOf(node) { let n = node; while (n && n.type !== 'PAGE') n = n.parent; return n; }
function screenOf(node) { let n = node; while (n && n.type !== 'PAGE') { if (n.type === 'FRAME' && n.name.startsWith('GoServe 2.1 • ')) return n; n = n.parent; } return null; }
function namedNode(type, name) { return page.findAllWithCriteria({types:[type]}).find(n => n.name === name); }
function isOnPage(node) { return node && pageOf(node)?.id === page.id; }
async function lookup(theme, suffix, knownId) {
  const exact = namedNode('FRAME', prefix(theme) + suffix);
  if (exact) return exact;
  const known = knownId ? await figma.getNodeByIdAsync(knownId) : null;
  if (known && isOnPage(known)) return known;
  unresolved.push({theme, source:suffix, reason:'Existing frame not found'});
  return null;
}
function state(theme, key) {
  return namedNode('FRAME', prefix(theme) + 'states/' + key) || null;
}
function destination(theme, target) {
  return target.startsWith('@') ? namedNode('FRAME', prefix(theme) + target.slice(1)) : state(theme, target);
}
async function reactions(node, value) {
  if (!node || !('reactions' in node)) return;
  if (typeof node.setReactionsAsync === 'function') await node.setReactionsAsync(value);
  else node.reactions = value;
  mutate(node);
}
async function route(node, theme, target) {
  const dest = destination(theme, target);
  if (!node || !dest) { unresolved.push({theme, node:node ? node.id : null, target, reason:!node ? 'Source action not found' : 'Destination not found'}); return; }
  if (screenOf(node)?.id === dest.id) { await reactions(node, []); return; }
  await reactions(node, [{trigger:{type:'ON_CLICK'}, actions:[{type:'NODE',destinationId:dest.id,navigation:'NAVIGATE',transition:null,resetScrollPosition:true}]}]);
  routeChanges.push({node:node.id,target:dest.id,key:target});
}
function texts(root) { return !root ? [] : root.type === 'TEXT' ? [root] : 'findAllWithCriteria' in root ? root.findAllWithCriteria({types:['TEXT']}) : []; }
function action(root, label) {
  if (!root) return null;
  const exact = root.findAll(n => 'reactions' in n && (n.name === 'Action / ' + label || n.name.endsWith(' | ' + label) || n.name === 'GoServe / Route action / ' + label));
  if (exact.length) return exact[0];
  const t = texts(root).find(n => n.characters === label);
  if (!t) return null;
  let n = t.parent;
  while (n && n !== root) {
    if ('reactions' in n && (/^(go:|Action \/|choice \/|Choice \/|GoServe \/ Route action)/.test(n.name))) return n;
    n = n.parent;
  }
  return t.parent && t.parent !== root && 'reactions' in t.parent ? t.parent : null;
}
function textValue(t, value) {
  if (!t || t.type !== 'TEXT') return;
  t.characters = value;
  if (!/icon|symbol/i.test(t.name)) t.textAutoResize = 'HEIGHT';
  mutate(t);
}
function relabel(node, label) {
  if (!node) return;
  const candidates = texts(node).filter(t => !/icon|symbol/i.test(t.name) && !(t.fontName !== figma.mixed && /Material Symbols/.test(t.fontName.family)));
  const labelNode = candidates.find(t => t.name === 'label') || candidates[0];
  if (!labelNode) { unresolved.push({node:node.id,reason:'Action label text not found'}); return; }
  textValue(labelNode, label);
  node.name = 'GoServe / Route action / ' + label;
  mutate(node);
}
function bodyOf(root) {
  if (!root) return null;
  const viewport = root.findAll(n => n.name === 'Scrollable Android content')[0];
  if (!viewport) return null;
  return viewport.children.find(n => n.name === 'Hug content / wraps and scrolls' || n.name === 'Content grows with text' || n.name === 'GoServe legacy content stack') || viewport;
}
function paint(theme, key) {
  const collection = collections.find(c => c.name === 'GoServe / ' + theme);
  const variable = collection && variables.find(v => v.variableCollectionId === collection.id && v.name === 'color/' + key);
  if (!variable) throw new Error('Missing color variable ' + theme + '/' + key);
  const value = variable.valuesByMode[collection.defaultModeId];
  return figma.variables.setBoundVariableForPaint({type:'SOLID',color:{r:value.r,g:value.g,b:value.b}}, 'color', variable);
}
function newText(parent, name, value, theme, role='Support', color='secondary') {
  const t = track(figma.createText()); t.name = name;
  const style = styles[role]; if (!style) throw new Error('Missing GoServe style ' + role);
  t.textStyleId = style.id; t.resize(Math.max(1,parent.width-(parent.paddingLeft||0)-(parent.paddingRight||0)),100);
  parent.appendChild(t); t.layoutSizingHorizontal='FILL'; t.layoutSizingVertical='HUG'; t.textAutoResize='HEIGHT';
  t.characters=value; t.fills=[paint(theme,color)]; return t;
}
function vertical(parent, name, width, gap=8, padding=0) {
  const n = track(figma.createAutoLayout('VERTICAL')); n.name=name; n.resize(width,100);
  n.primaryAxisSizingMode='AUTO'; n.counterAxisSizingMode='FIXED'; n.itemSpacing=gap;
  n.paddingLeft=n.paddingRight=n.paddingTop=n.paddingBottom=padding; n.fills=[];
  parent.appendChild(n); if (parent.layoutMode && parent.layoutMode !== 'NONE') n.layoutSizingHorizontal='FILL'; return n;
}
async function compactAction(parent, theme, label, target, width=350) {
  let n = parent.children.find(n => n.name === 'GoServe / Route action / ' + label);
  if (!n) {
    const set = namedNode('COMPONENT_SET', 'GoServe / Inline action');
    const component = set && set.children.find(n => n.type === 'COMPONENT' && n.name === 'Theme=' + theme);
    if (component) { n=component.createInstance(); parent.appendChild(n); track(n); for (const child of n.findAll(()=>true)) track(child); }
    else { n=vertical(parent,'GoServe / Route action / '+label,width,0,0); n.minHeight=48; n.primaryAxisAlignItems=n.counterAxisAlignItems='CENTER'; newText(n,'label',label,theme,'Label','text').textAlignHorizontal='CENTER'; }
  }
  n.name='GoServe / Route action / '+label; n.resize(width,48);
  if (parent.layoutMode && parent.layoutMode !== 'NONE') n.layoutSizingHorizontal='FILL';
  relabel(n,label); await route(n,theme,target); return n;
}
function annotation(theme, anchor, key, value) {
  if (!anchor) return;
  const name='Design route annotation / '+theme+' / '+key;
  let t=namedNode('TEXT',name);
  if (!t) { t=track(figma.createText()); t.name=name; t.textStyleId=styles.Support.id; t.resize(390,100); t.textAutoResize='HEIGHT'; t.fills=[{type:'SOLID',color:{r:0.25,g:0.25,b:0.25}}]; }
  t.characters=value; t.x=anchor.x; t.y=anchor.y-155; mutate(t);
}
async function hide(node, reason) {
  if (!node) return;
  node.visible=false; mutate(node); await reactions(node,[]);
  for (const descendant of ('findAll' in node ? node.findAll(n=>'reactions' in n && n.reactions.length>0) : [])) await reactions(descendant,[]);
  hiddenLegacyRows.push({id:node.id,reason});
}
function directContentRow(text, root) {
  const body=bodyOf(root); if (!body) return null;
  let n=text; while (n.parent && n.parent!==body && n!==root) n=n.parent;
  return n.parent===body ? n : null;
}

for (const theme of ['Light','Dark']) {
  const dark=theme==='Dark';
  const request=await lookup(theme,'student/new-request',dark?'36:207':'19:209');
  if (request) {
    const literal=dark?null:await figma.getNodeByIdAsync('19:248');
    const footer=literal&&screenOf(literal)?.id===request.id?literal:action(request,'Continue to payment choice')||action(request,'Review request');
    relabel(footer,'Review request'); await route(footer,theme,'G04.2');
    const input=request.findAll(n=>n.name==='input / Request type' || n.name==='Input / Request type')[0] || request.findAll(n=>n.name==='field / Request type' || n.name==='Field / Request type')[0];
    await route(input,theme,'G04.1');
    const body=bodyOf(request);
    if (body) {
      let fee=body.children.find(n=>n.name==='GoServe / Read-only fee declaration');
      if (!fee) {
        fee=vertical(body,'GoServe / Read-only fee declaration',350,4,16); fee.cornerRadius=16; fee.fills=[paint(theme,'muted')];
        newText(fee,'Fee declaration label','Office-declared fee',theme,'Label','text');
        newText(fee,'Fee declaration value','No payment required',theme,'Body','text');
        newText(fee,'Fee declaration support','This service skips payment.',theme,'Support','secondary');
        body.insertChild(Math.min(1,body.children.length-1),fee); mutate(body);
      }
    } else unresolved.push({theme,node:request.id,reason:'New-request scrolling body not found'});
    annotation(theme,request,'new-request','Free-service design example. Review request opens G04.2 and skips payment. The request type opens configured selection. Required/pending fee variants are in the design-state board. This prototype does not capture or submit real input.');
  }

  const booking=await lookup(theme,'student/appointment',dark?'36:599':'23:254');
  if (booking) {
    await hide(action(booking,'Reschedule'),'Pre-booking review cannot manage an existing booking');
    await hide(action(booking,'Cancel appointment'),'Pre-booking review cannot cancel an existing booking');
    await route(action(booking,'Confirm booking'),theme,'G09.1');
    annotation(theme,booking,'booking-review','New-booking review only. Confirm booking navigates to the separate G09.1 confirmed-booking design example; it creates no real booking. Cancellation and rescheduling belong to existing authorized booking detail.');
  }
  const consult=await lookup(theme,'student/consult',dark?'36:536':'23:191');
  if (consult) {
    const body=bodyOf(consult);
    if (!body) unresolved.push({theme,node:consult.id,reason:'Consult scrolling body not found'});
    else {
      let row=body.children.find(n=>n.name==='GoServe / My appointments action');
      if (!row) { row=vertical(body,'GoServe / My appointments action',350,0,0); body.insertChild(0,row); mutate(body); }
      await compactAction(row,theme,'My appointments','G01.3',350);
    }
  }

  const status=await lookup(theme,'staff/rejection',dark?'36:1141':'26:2');
  if (status) {
    await route(action(status,'Rejected'),theme,'G06.staff-reject');
    await route(action(status,'Cancelled'),theme,'G06.staff-cancel');
    const save=action(status,'Save status');
    if (save) { await reactions(save,[]); save.opacity=0.55; mutate(save); }
    else unresolved.push({theme,node:status.id,reason:'Save status action not found'});
    annotation(theme,status,'staff-status','Rejected and Cancelled open separate staff reason flows. Empty reason routes to validation. Save status is inactive in this static editor, so it cannot bypass required reasons; a later implementation enables only a validated authorized transition. No live status change occurs.');
  }

  const availability=await lookup(theme,'staff/availability',dark?'36:954':'24:186');
  if (availability) {
    await route(action(availability,'Publish office update'),theme,'G13.1');
    annotation(theme,availability,'office-publication','Publish routes to G13.1 saving-state design example. Static fields do not capture edits, publish data or create actor/time records. A live update requires authorized server confirmation.');
  }
  const teller=await lookup(theme,'teller/confirm',dark?'36:1643':'26:500');
  if (teller) {
    await route(action(teller,'Confirm as paid'),theme,'G08.3');
    annotation(theme,teller,'cash-confirmation','Confirm as paid routes to G08.3 confirmation-in-progress design example. No payment is collected or confirmed here. Actual teller confirmation requires the reviewed server record and an authoritative result; duplicate confirmation stays blocked.');
  }
  const inquiry=await lookup(theme,'student/new-thread',dark?'36:678':'23:333');
  if (inquiry) {
    await route(action(inquiry,'Send inquiry'),theme,'G14.2');
    annotation(theme,inquiry,'inquiry-send','Send inquiry routes to G14.2 pending-send design example. This static prototype does not capture or transmit typed input. Actual send requires a valid authorized office recipient and validated subject/message.');
  }
  const thread=await lookup(theme,'student/thread',dark?'36:707':'23:362');
  if (thread) await route(action(thread,'Send reply'),theme,'G14.2');

  const verified=await lookup(theme,'student/verified-file',dark?'36:489':'23:144');
  if (verified) {
    const about=action(verified,'About downloads');
    if (about) await route(about,theme,'G10.5');
    annotation(theme,verified,'file-availability','This is an authorized, free-file verification preview with no real download. About downloads leads only to the authorized unavailable-file example G10.5. The unauthorized G10.4 variant is isolated; it exposes no private file and never opens this preview.');
  }

  const board=await lookup(theme,'shared/states',dark?'36:1739':'26:596');
  if (board) {
    const body=bodyOf(board);
    if (body) {
      let group=body.children.find(n=>n.name==='GoServe / Fee review design examples');
      if (!group) { group=vertical(body,'GoServe / Fee review design examples',350,8,0); newText(group,'Fee example heading','Fee review examples',theme,'Section','text'); newText(group,'Fee example explanation','Design examples only. No fee is collected.',theme); }
      for (const [label,key] of [['Free service','G04.2'],['Required fee','G04.3'],['Pending office declaration','G04.4']]) await compactAction(group,theme,label,key,350);
    } else unresolved.push({theme,node:board.id,reason:'Design-state board body not found'});
  }

  const cashier=await lookup(theme,'student/office-cashiering',dark?'60:246':'60:2');
  if (cashier) {
    for (const row of cashier.findAll(n=>'reactions' in n && /student\/payment-osa|\| OSA request payment$/.test(n.name))) await hide(row,'OSA is outside the confirmed five-office scope');
    for (const t of texts(cashier).filter(t=>/\bOSA\b/.test(t.characters))) {
      const row=directContentRow(t,cashier);
      if (row && row.visible===false) continue;
      if (/OSA request payment|Open the OSA payment choice/.test(t.characters)) { if (row) await hide(row,'Legacy OSA payment row'); }
      else textValue(t,'Cashiering handles fees declared by the issuing office. Check the request reference and confirmed amount.');
    }
  }
  const search=await lookup(theme,'teller/cash',dark?'36:1558':'26:415');
  if (search) for (const t of texts(search).filter(t=>/\bOSA\b/.test(t.characters))) textValue(t,'Search payment references for services with an office-declared fee.');
}

// Current-page scope only: hide exactly the three standalone OSA flows per theme.
const osaRoots=[];
for (const theme of ['Light','Dark']) for (const [suffix,lightId] of [['student/office-osa','60:36'],['student/new-request-osa','60:172'],['student/payment-osa','60:215']]) {
  const node=await lookup(theme,suffix,theme==='Light'?lightId:null);
  if (!node) continue;
  node.visible=false; mutate(node); osaRoots.push(node);
  archiveRecommendation.push({id:node.id,name:node.name,destinationPage:'0:1',action:'Move exact standalone frame in a separate archive-page operation if desired'});
}
const osaIds=new Set(osaRoots.map(n=>n.id));
function cleanActions(actions) {
  const kept=[];
  for (const action of actions||[]) {
    if (action.type==='NODE' && osaIds.has(action.destinationId)) continue;
    if (action.type==='CONDITIONAL') {
      const blocks=action.conditionalBlocks.map(block=>({...block,actions:cleanActions(block.actions)})).filter(block=>block.actions.length>0);
      if (blocks.length) kept.push({...action,conditionalBlocks:blocks});
    } else kept.push(action);
  }
  return kept;
}
let clearedOsaIncoming=0;
for (const node of page.findAll(n=>'reactions' in n && n.reactions.length>0)) {
  const before=node.reactions;
  const after=before.map(reaction=>({trigger:reaction.trigger,actions:cleanActions(reaction.actions)})).filter(reaction=>reaction.actions.length>0);
  if (JSON.stringify(before.map(r=>({trigger:r.trigger,actions:r.actions})))!==JSON.stringify(after)) { await reactions(node,after); clearedOsaIncoming++; }
}

for (const id of created) createdNodeIds.push(id);
for (const id of changed) mutatedNodeIds.push(id);
return {createdNodeIds,mutatedNodeIds,deletedNodeIds,routeChanges,hiddenLegacyRows,clearedOsaIncoming,archiveRecommendation,unresolved};
