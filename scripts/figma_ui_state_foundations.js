await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('14:2'));
const createdNodeIds = [], mutatedNodeIds = [], components = [];
const page = figma.currentPage;
const styles = Object.fromEntries((await figma.getLocalTextStylesAsync()).map(s => [s.name.split(' / ')[1], s.id]));
const collections = await figma.variables.getLocalVariableCollectionsAsync();
const variables = await figma.variables.getLocalVariablesAsync();
await Promise.all([{family:'Karla',style:'SemiBold'},{family:'Karla',style:'Regular'}].map(f => figma.loadFontAsync(f)));
const record = n => (createdNodeIds.push(n.id), n);
function paint(theme, name) {
  const c = collections.find(c => c.name === 'GoServe / ' + theme);
  const v = variables.find(v => v.variableCollectionId === c.id && v.name === 'color/' + name);
  const value = v.valuesByMode[c.defaultModeId];
  return figma.variables.setBoundVariableForPaint({type:'SOLID',color:{r:value.r,g:value.g,b:value.b}}, 'color', v);
}
function label(parent, value, theme, color) {
  const t = record(figma.createText()); t.name = 'Label'; t.textStyleId = styles.Label;
  t.fills = [paint(theme,color)]; t.resize(parent.width - 32, 100);
  parent.appendChild(t); t.layoutSizingHorizontal = 'FILL'; t.layoutSizingVertical = 'HUG';
  t.textAutoResize = 'HEIGHT'; t.textAlignHorizontal = 'CENTER'; t.characters = value;
  return t;
}
const paths = {
  Home:'M3 10L12 3L21 10V21H15V14H9V21H3Z',
  Queue:'M6 3H18V21L15 19L12 21L9 19L6 21Z M9 7H15 M9 11H15 M9 15H13',
  Schedule:'M4 5H20V21H4Z M4 10H20 M8 3V7 M16 3V7 M9 14H11V16H9Z',
  Inbox:'M3 4H17V15H9L5 19V15H3Z M17 9H21V21L17 17H12',
  More:'M4 6H20 M4 12H20 M4 18H20',
  Search:'M10 3A7 7 0 1 0 10 17A7 7 0 1 0 10 3 M15 15L21 21',
  Pending:'M4 6H20V20H4Z M8 3V9 M16 3V9 M8 13H16 M8 16H13',
  History:'M3 10A9 9 0 1 1 5 19 M3 3V10H10 M12 7V12L16 14'
};
function icon(parent, theme, name, selected, x) {
  const p = paint(theme, selected ? 'navigation' : 'navWhite');
  const hex = '#' + ['r','g','b'].map(k=>Math.round(p.color[k]*255).toString(16).padStart(2,'0')).join('');
  const n = figma.createNodeFromSvg('<svg width="22" height="22" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="'+paths[name]+'" fill="none" stroke="'+hex+'" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>');
  parent.appendChild(n); n.name = 'Icon / ' + name; n.layoutPositioning = 'ABSOLUTE'; n.x=x; n.y=13;
  createdNodeIds.push(n.id,...n.findAll(()=>true).map(n=>n.id));
  for(const child of n.findAll(()=>true)) if('strokes' in child && child.strokes.length) child.strokes=[p];
}
let cursor = 0;
for(const theme of ['Light','Dark']) for(const style of ['Primary','Secondary','Disabled']) {
  const name = 'GoServe / State action / Theme='+theme+', Style='+style;
  let n = page.children.find(n=>n.type==='COMPONENT' && n.name===name);
  if(!n) {
    n=record(figma.createComponent()); n.name=name; n.layoutMode='VERTICAL'; n.resize(350,48);
    n.primaryAxisSizingMode='AUTO'; n.counterAxisSizingMode='FIXED'; n.primaryAxisAlignItems='CENTER';
    n.paddingLeft=n.paddingRight=16; n.paddingTop=n.paddingBottom=14; n.minHeight=48; n.cornerRadius=24;
    n.strokesIncludedInLayout=false; n.fills=[paint(theme,style==='Primary'?'primary':style==='Disabled'?'muted':'surface')];
    if(style==='Secondary'){n.strokes=[paint(theme,'border')];n.strokeWeight=1;}
    label(n,'Action label',theme,style==='Primary'?'onPrimary':style==='Disabled'?'secondary':'text');
    n.x=12800+(cursor%3)*400; n.y=100+Math.floor(cursor/3)*100;
    n.description='GoServe compact action. Minimum 48dp touch height; wraps with text. Disabled variants have no prototype action.';
  }
  components.push({id:n.id,family:'State action',theme,style}); cursor++;
}
for(const role of ['Staff','Teller']) {
  const tabs=role==='Staff'?['Home','Queue','Schedule','Inbox','More']:['Search','Pending','History','More'];
  for(const theme of ['Light','Dark']) for(const active of tabs) {
    const name='GoServe / '+role+' navigation / Theme='+theme+', Active='+active;
    let n=page.children.find(n=>n.type==='COMPONENT'&&n.name===name);
    if(!n) {
      n=record(figma.createComponent());n.name=name;n.layoutMode='HORIZONTAL';n.resize(366,56);
      n.primaryAxisSizingMode=n.counterAxisSizingMode='FIXED';n.counterAxisAlignItems='CENTER';
      n.paddingLeft=n.paddingRight=8;n.itemSpacing=0;n.cornerRadius=28;n.fills=[paint(theme,'navigation')];
      for(const name of tabs){
        const tab=record(figma.createAutoLayout('HORIZONTAL'));tab.name='Tab / '+name;tab.resize(350/tabs.length,48);
        tab.primaryAxisSizingMode=tab.counterAxisSizingMode='FIXED';tab.fills=[];n.appendChild(tab);
        tab.layoutSizingHorizontal='FILL';
        if(name===active){const circle=record(figma.createEllipse());circle.name='Selected destination';circle.resize(36,36);circle.fills=[paint(theme,'navWhite')];tab.appendChild(circle);circle.layoutPositioning='ABSOLUTE';circle.x=(tab.width-36)/2;circle.y=6;}
        icon(tab,theme,name,name===active,(tab.width-22)/2);
      }
      for(const tab of n.children){for(const item of tab.children){item.x=(tab.width-item.width)/2;item.constraints={horizontal:'CENTER',vertical:'CENTER'};}}
      n.x=12800+(cursor%3)*400;n.y=400+Math.floor((cursor-6)/3)*100;cursor++;
      n.description='Role-scoped GoServe navigation. 56dp capsule, 48dp targets, 22dp icons, 36dp selection. Accessible names come from Tab names.';
    }
    components.push({id:n.id,family:role+' navigation',theme,active});
  }
}
return {createdNodeIds,mutatedNodeIds,components};
