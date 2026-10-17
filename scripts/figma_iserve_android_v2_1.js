// Figma Plugin API build script for the IServe Android v2.1 concept page.
// Replace __PHASE__ with 1..5, then run through Figma use_figma.
const phase = __PHASE__;
const page = await figma.getNodeByIdAsync('14:2');
if (!page || page.type !== 'PAGE') throw new Error('IServe Android v2.1 page missing');
await figma.setCurrentPageAsync(page);

const fonts = [
  { family: 'Archivo', style: 'Bold' },
  { family: 'Archivo', style: 'SemiBold' },
  { family: 'Roboto', style: 'Regular' },
  { family: 'Roboto', style: 'Medium' },
  { family: 'Roboto', style: 'Bold' },
  { family: 'Material Symbols Rounded', style: 'Regular' },
];
for (const font of fonts) await figma.loadFontAsync(font);

const C = {
  bg: '#F4F4F4', paper: '#FFFFFF', ink: '#171717', muted: '#595959',
  faint: '#777777', line: '#D5D5D5', soft: '#EBEBEB', softer: '#F7F7F7',
  deep: '#272727', white: '#FFFFFF',
};
const created = [];
const screens = [];
function track(node) { created.push(node.id); return node; }
function rgb(hex) { const h = hex.replace('#', ''); return { r: parseInt(h.slice(0, 2), 16) / 255, g: parseInt(h.slice(2, 4), 16) / 255, b: parseInt(h.slice(4, 6), 16) / 255 }; }
function paint(hex) { return [{ type: 'SOLID', color: rgb(hex) }]; }
function box(parent, name, width, height, fill = C.paper, radius = 0, stroke = null) {
  const n = track(figma.createFrame()); n.name = name; n.resize(width, height);
  n.fills = fill ? paint(fill) : []; n.cornerRadius = radius;
  if (stroke) { n.strokes = paint(stroke); n.strokeWeight = 1; }
  parent.appendChild(n); return n;
}
function stack(parent, name, width, gap = 8, pad = 0, fill = null, radius = 0, stroke = null) {
  const n = track(figma.createAutoLayout('VERTICAL')); n.name = name;
  n.resize(width, 1); n.primaryAxisSizingMode = 'AUTO'; n.counterAxisSizingMode = 'FIXED';
  n.itemSpacing = gap; n.paddingTop = pad; n.paddingBottom = pad;
  n.paddingLeft = pad; n.paddingRight = pad;
  n.fills = fill ? paint(fill) : []; n.cornerRadius = radius;
  if (stroke) { n.strokes = paint(stroke); n.strokeWeight = 1; }
  parent.appendChild(n); return n;
}
function text(parent, name, value, width, size = 14, color = C.ink, weight = 'Regular', family = 'Roboto', line = null) {
  const n = track(figma.createText()); n.name = name;
  n.fontName = { family, style: weight }; n.characters = String(value);
  n.fontSize = size; n.lineHeight = { unit: 'PIXELS', value: line || Math.round(size * 1.35) };
  n.fills = paint(color); n.resize(width, 1); n.textAutoResize = 'HEIGHT';
  parent.appendChild(n); return n;
}
function icon(parent, glyph, size = 24, color = C.ink, name = null) {
  const n = text(parent, name || `icon / ${glyph}`, glyph, size + 2, size, color, 'Regular', 'Material Symbols Rounded', size + 2);
  n.textAutoResize = 'NONE'; n.resize(size + 2, size + 2); return n;
}
function hrow(parent, name, width, gap = 10, height = 1) {
  const n = track(figma.createAutoLayout('HORIZONTAL')); n.name = name;
  n.resize(width, height); n.primaryAxisSizingMode = height === 1 ? 'AUTO' : 'FIXED';
  n.counterAxisSizingMode = 'FIXED'; n.itemSpacing = gap; n.counterAxisAlignItems = 'CENTER';
  parent.appendChild(n); return n;
}
function rule(parent, width = 350) { const n = box(parent, 'Divider', width, 1, C.line); return n; }
function chip(parent, label, selected = false, width = null) {
  const w = width || Math.max(66, Math.min(235, Math.round(label.length * 7.2 + 30)));
  const n = box(parent, `chip / ${label}`, w, 32, selected ? C.ink : C.soft, 16);
  const t = text(n, 'label', label, w - 20, 12, selected ? C.white : C.ink, 'Medium');
  t.x = 10; t.y = 8; return n;
}
function button(parent, label, target, width = 350, kind = 'filled', glyph = null) {
  const n = box(parent, `go:${target || 'none'} | ${label}`, width, 52, kind === 'filled' ? C.ink : C.paper, 16, kind === 'outlined' ? C.line : null);
  const fontColor = kind === 'filled' ? C.white : C.ink;
  if (glyph) { const i = icon(n, glyph, 22, fontColor); i.x = 16; i.y = 14; }
  const t = text(n, 'label', label, width - (glyph ? 60 : 28), 14, fontColor, 'Medium');
  t.x = glyph ? 48 : 14; t.y = 16; return n;
}
function linkRow(parent, title, detail, target, glyph = 'chevron_right', width = 350, meta = null) {
  const n = box(parent, `go:${target} | ${title}`, width, meta ? 90 : 78, C.paper, 18, C.line);
  text(n, 'title', title, width - 68, 16, C.ink, 'Medium').set({ x: 16, y: 14 });
  text(n, 'detail', detail, width - 68, 13, C.muted).set({ x: 16, y: 40 });
  if (meta) text(n, 'meta', meta, width - 68, 12, C.faint).set({ x: 16, y: 62 });
  const i = icon(n, glyph, 22, C.ink); i.x = width - 40; i.y = meta ? 33 : 28;
  return n;
}
function notice(parent, title, detail, width = 350, dark = false) {
  const n = stack(parent, `notice / ${title}`, width, 5, 16, dark ? C.deep : C.soft, 18);
  text(n, 'title', title, width - 32, 14, dark ? C.white : C.ink, 'Medium');
  text(n, 'body', detail, width - 32, 13, dark ? C.white : C.muted);
  return n;
}
function field(parent, label, value, width = 350, hint = null, glyph = null) {
  const group = stack(parent, `field / ${label}`, width, 6);
  text(group, 'label', label, width, 13, C.ink, 'Medium');
  const f = box(group, `input / ${label}`, width, 50, C.paper, 14, C.line);
  text(f, 'value', value, width - (glyph ? 68 : 32), 14, value.startsWith('Select') || value.startsWith('Enter') ? C.faint : C.ink).set({ x: 16, y: 15 });
  if (glyph) { const i = icon(f, glyph, 22, C.muted); i.x = width - 42; i.y = 14; }
  if (hint) text(group, 'help', hint, width, 12, C.muted);
  return group;
}
function choice(parent, title, detail, selected = false, target = null, width = 350) {
  const n = box(parent, target ? `go:${target} | ${title}` : `choice / ${title}`, width, 80, selected ? C.soft : C.paper, 18, selected ? C.ink : C.line);
  const radio = box(n, 'radio', 20, 20, selected ? C.ink : C.paper, 10, C.ink); radio.x = 16; radio.y = 18;
  if (selected) { const dot = box(radio, 'selected center', 8, 8, C.white, 4); dot.x = 6; dot.y = 6; }
  text(n, 'title', title, width - 70, 15, C.ink, 'Medium').set({ x: 48, y: 13 });
  text(n, 'detail', detail, width - 70, 12, C.muted).set({ x: 48, y: 39 });
  return n;
}
function section(parent, title, detail = null, width = 350) {
  const g = stack(parent, `section / ${title}`, width, 3);
  text(g, 'heading', title, width, 19, C.ink, 'Bold', 'Archivo', 25);
  if (detail) text(g, 'description', detail, width, 13, C.muted);
  return g;
}
function label(parent, value, width = 350) { return text(parent, 'section label', value, width, 12, C.muted, 'Medium'); }
function statusRow(parent, title, detail, state = 'current', width = 350) {
  const n = box(parent, `status / ${title}`, width, 59, C.paper, 14, C.line);
  const mark = box(n, 'status mark', 10, 10, state === 'current' ? C.ink : C.line, 5); mark.x = 16; mark.y = 24;
  text(n, 'title', title, width - 56, 14, C.ink, state === 'current' ? 'Medium' : 'Regular').set({ x: 38, y: 9 });
  text(n, 'detail', detail, width - 56, 12, C.muted).set({ x: 38, y: 32 });
  return n;
}
function screen(spec, index) {
  const frame = track(figma.createFrame()); frame.name = `IServe 2.1 • ${spec.key}`; frame.resize(390, 844);
  frame.x = 40 + (index % 6) * 450; frame.y = 40 + Math.floor(index / 6) * 925;
  frame.fills = paint(C.bg); frame.clipsContent = true; page.appendChild(frame); screens.push({ key: spec.key, id: frame.id });
  const stat = box(frame, 'Android status inset', 390, 24, C.bg); stat.x = 0; stat.y = 0;
  text(stat, 'time', '9:41', 50, 12, C.ink, 'Medium').set({ x: 20, y: 3 });
  text(stat, 'system', 'LTE  86%', 80, 11, C.ink, 'Medium').set({ x: 304, y: 3 });
  const app = box(frame, 'Material top app bar', 390, 64, C.bg); app.x = 0; app.y = 24;
  if (spec.back) { const back = box(app, `go:${spec.back} | Back`, 48, 48, null, 24); back.x = 8; back.y = 8; const i = icon(back, 'arrow_back', 24); i.x = 12; i.y = 12; }
  const titleX = spec.back ? 64 : 20;
  text(app, 'screen title', spec.title, spec.right ? 260 : 300, 21, C.ink, 'Bold', 'Archivo', 27).set({ x: titleX, y: 19 });
  if (spec.right) { const right = box(app, `go:${spec.right.target} | ${spec.right.label}`, 48, 48, null, 24); right.x = 330; right.y = 8; const i = icon(right, spec.right.icon || 'notifications', 24); i.x = 12; i.y = 12; }
  const contentEnd = spec.nav ? 748 : spec.footer ? 744 : 820;
  const content = stack(frame, 'Scrollable Android content', 390, 14, 0, null);
  content.x = 0; content.y = 88; content.resize(390, contentEnd - 88); content.primaryAxisSizingMode = 'FIXED'; content.counterAxisSizingMode = 'FIXED';
  content.paddingTop = 12; content.paddingBottom = 24; content.paddingLeft = 20; content.paddingRight = 20;
  content.clipsContent = true; content.overflowDirection = 'VERTICAL';
  for (const block of spec.blocks) render(content, block);
  if (spec.nav) nav(frame, spec.nav, spec.key);
  if (spec.footer) { const f = button(frame, spec.footer.label, spec.footer.target, 350, spec.footer.kind || 'filled', spec.footer.icon || null); f.x = 20; f.y = 754; }
  const gesture = box(frame, 'Android gesture inset', 390, 24, C.bg); gesture.x = 0; gesture.y = 820;
  const handle = box(gesture, 'gesture handle', 108, 4, C.ink, 2); handle.x = 141; handle.y = 10;
  return frame;
}
function nav(parent, role, selectedKey) {
  const sets = {
    student: [['Home','student/home','home'],['Requests','student/requests','receipt_long'],['Consult','student/consult','event'],['Inbox','student/inbox','forum'],['More','student/more','menu']],
    staff: [['Home','staff/home','home'],['Queue','staff/queue','assignment'],['Schedule','staff/guidance','event'],['Inbox','staff/inbox','forum'],['More','staff/more','menu']],
    teller: [['Search','teller/cash','search'],['Pending','teller/pending','payments'],['History','teller/history','history'],['More','staff/more','menu']],
  };
  const items = sets[role]; const bar = box(parent, 'Material navigation bar', 390, 72, C.paper);
  bar.x = 0; bar.y = 748; const divider = box(bar, 'top divider', 390, 1, C.line); divider.x = 0; divider.y = 0;
  const w = role === 'teller' ? 88 : 74; const start = role === 'teller' ? 19 : 10;
  for (let j = 0; j < items.length; j++) {
    const [label, target, glyph] = items[j]; const item = box(bar, `go:${target} | ${label}`, w, 64, null); item.x = start + j * w; item.y = 4;
    const selected = target === selectedKey || (selectedKey.startsWith('staff/') && label === 'Schedule' && /guidance|faculty|ccs/.test(selectedKey));
    const pill = box(item, 'selected icon container', 58, 30, selected ? C.soft : null, 16); pill.x = (w - 58) / 2; pill.y = 2;
    const i = icon(pill, glyph, 21, selected ? C.ink : C.muted); i.x = 18; i.y = 4;
    text(item, 'label', label, w, 11, selected ? C.ink : C.muted, selected ? 'Medium' : 'Regular').set({ x: 0, y: 38, textAlignHorizontal: 'CENTER' });
  }
}
function render(parent, b) {
  const w = 350;
  switch (b.type) {
    case 'hero': { const g = stack(parent, 'hero', w, 5); text(g, 'headline', b.title, w, 29, C.ink, 'Bold', 'Archivo', 35); text(g, 'subtitle', b.body, w, 14, C.muted); break; }
    case 'section': section(parent, b.title, b.body); break;
    case 'label': label(parent, b.text); break;
    case 'notice': notice(parent, b.title, b.body, w, b.dark); break;
    case 'button': button(parent, b.label, b.target, w, b.kind, b.icon); break;
    case 'row': linkRow(parent, b.title, b.body, b.target, b.icon, w, b.meta); break;
    case 'field': field(parent, b.label, b.value, w, b.hint, b.icon); break;
    case 'choice': choice(parent, b.title, b.body, b.selected, b.target); break;
    case 'status': statusRow(parent, b.title, b.body, b.state); break;
    case 'rule': rule(parent); break;
    case 'chips': { const row = hrow(parent, 'filter chips', w, 8, 36); for (const item of b.items) chip(row, item, item === b.selected); break; }
    case 'office': {
      const n = stack(parent, `go:${b.target} | ${b.name}`, w, 7, 16, C.paper, 20, C.line);
      const head = hrow(n, 'office heading', 318, 8, 32); chip(head, b.status, b.status === 'Open'); text(head, 'campus', b.campus, 170, 12, C.muted);
      text(n, 'office name', b.name, 318, 17, C.ink, 'Bold', 'Archivo', 23);
      text(n, 'office detail', b.service, 318, 13, C.muted);
      if (b.note) text(n, 'announcement', b.note, 318, 12, C.muted);
      break;
    }
    case 'summary': {
      const n = stack(parent, `go:${b.target} | summary`, w, 7, 18, C.ink, 22);
      text(n, 'eyebrow', b.label, 314, 12, C.white, 'Medium');
      text(n, 'title', b.title, 314, 20, C.white, 'Bold', 'Archivo', 27);
      text(n, 'detail', b.body, 314, 13, C.white);
      break;
    }
    case 'timeline': { const n = stack(parent, 'status history', w, 7); for (const item of b.items) statusRow(n, item[0], item[1], item[2]); break; }
    case 'message': {
      const n = stack(parent, `message / ${b.from}`, w, 5, 14, b.mine ? C.deep : C.paper, 18, b.mine ? null : C.line);
      text(n, 'sender', b.from, 322, 12, b.mine ? C.white : C.muted, 'Medium');
      text(n, 'message', b.body, 322, 14, b.mine ? C.white : C.ink);
      break;
    }
    case 'metric': {
      const n = stack(parent, `metric / ${b.label}`, w, 4, 18, C.paper, 20, C.line);
      text(n, 'value', b.value, 314, 32, C.ink, 'Bold', 'Archivo', 38);
      text(n, 'label', b.label, 314, 13, C.muted);
      break;
    }
    case 'scanner': {
      const n = box(parent, 'CameraX QR viewfinder', w, 244, C.deep, 20);
      const qr = box(n, 'QR target', 174, 174, null, 18, C.white); qr.x = 88; qr.y = 24; qr.strokeWeight = 3;
      text(n, 'camera hint', b.hint, 318, 13, C.white, 'Medium').set({ x: 16, y: 211, textAlignHorizontal: 'CENTER' });
      break;
    }
    case 'state': {
      const n = stack(parent, `state / ${b.title}`, w, 10, 22, C.paper, 20, C.line);
      icon(n, b.icon || 'info', 30, C.ink);
      text(n, 'state title', b.title, 306, 18, C.ink, 'Bold', 'Archivo');
      text(n, 'state body', b.body, 306, 14, C.muted);
      if (b.action) button(n, b.action, b.target, 306, 'outlined');
      break;
    }
    default: throw new Error(`Unknown block ${b.type}`);
  }
}

const all = [
  { key:'shared/sign-in', title:'IServe', blocks:[
    {type:'hero',title:'Campus tasks, made easier.',body:'Find offices, send requests, and keep up with what happens next.'},
    {type:'notice',title:'TIP Manila demo',body:'Casal and Arlegui campuses. Sample services and schedules are not official.'},
    {type:'button',label:'Continue with TIP Google',target:'student/consent',icon:'account_circle'},
    {type:'button',label:'Use pre-created demo account',target:'shared/demo-login',kind:'outlined',icon:'key'},
    {type:'notice',title:'Private by default',body:'No public registration. Your account decides which screens and records you can access.'},
  ]},
  { key:'shared/demo-login', title:'Demo sign in', back:'shared/sign-in', footer:{label:'Sign in to demo',target:'student/home'}, blocks:[
    {type:'section',title:'Choose a pre-created account',body:'Use the assigned demo email and password. This screen does not create accounts.'},
    {type:'field',label:'Email',value:'student.a@iserve.demo'},
    {type:'field',label:'Password',value:'Enter assigned password'},
    {type:'notice',title:'Access follows the signed-in account',body:'Student, staff, faculty, guidance, secretary, and teller records stay separate.'},
    {type:'row',title:'Preview staff entry',body:'Demo sign-in with an authorized staff account.',target:'staff/home'},
    {type:'row',title:'Preview teller entry',body:'Demo sign-in with an authorized teller account.',target:'teller/cash'},
  ]},
  { key:'student/consent', title:'Privacy & profile', back:'shared/sign-in', footer:{label:'Accept and continue',target:'student/home'}, blocks:[
    {type:'section',title:'Before you begin',body:'The demo stores only the information needed for the selected services.'},
    {type:'choice',title:'I agree to the privacy notice',body:'Required for this demo account.',selected:true},
    {type:'notice',title:'AI help is optional',body:'Use only non-sensitive questions and files. AI answers are guidance, not official decisions.'},
    {type:'field',label:'Profile image',value:'Choose a photo (optional)',icon:'photo_library'},
    {type:'notice',title:'Your records stay private',body:'Only you and authorized office staff can open your requests and files.'},
  ]},
  { key:'student/home', title:'Good morning', nav:'student', right:{target:'student/notifications',label:'Notifications',icon:'notifications'}, blocks:[
    {type:'hero',title:'What do you need today?',body:'Start with an office, or return to a request already in progress.'},
    {type:'summary',label:'YOUR ACTIVE REQUEST',title:'Certificate of enrollment',body:'Processing · Registrar · REQ-2026-0148',target:'student/request-detail'},
    {type:'button',label:'New document request',target:'student/new-request',icon:'add'},
    {type:'section',title:'Campus offices',body:'Status and announcements update inside IServe.'},
    {type:'office',name:'Registrar',status:'Open',campus:'Casal · Arlegui',service:'Document requests and verified PDFs',note:'Demo service details',target:'student/office-detail'},
    {type:'office',name:'Student Accounting',status:'On Lunch Break',campus:'Casal · Arlegui',service:'Accounting inquiries and fee-linked requests',target:'student/office-detail'},
    {type:'office',name:'Cashiering',status:'Open',campus:'Casal · Arlegui',service:'Cash payment confirmation',target:'student/office-detail'},
    {type:'office',name:'Guidance and Counseling Center',status:'Cut-Off Reached',campus:'Casal · Arlegui',service:'Guidance consultations',target:'student/office-detail'},
    {type:'office',name:'CCS secretary',status:'Temporarily Closed',campus:'Casal · Arlegui',service:'Faculty coordination',target:'student/office-detail'},
  ]},
  { key:'student/office-detail', title:'Registrar', back:'student/home', footer:{label:'Start a request',target:'student/new-request'}, blocks:[
    {type:'notice',title:'Demo office • Casal and Arlegui',body:'Open. Status set by authorized staff; check the latest note before visiting.'},
    {type:'section',title:'How this office can help',body:'Sample services and requirements need TIP verification before real use.'},
    {type:'row',title:'Document request',body:'Submit notes and private evidence.',target:'student/new-request',icon:'description'},
    {type:'row',title:'Ask the office',body:'Start an asynchronous inquiry thread.',target:'student/thread',icon:'forum'},
    {type:'row',title:'Consultations',body:'Find Guidance or CCS faculty availability.',target:'student/consult',icon:'event'},
    {type:'notice',title:'Office announcement',body:'Demo announcement appears here with its publication time.'},
  ]},
  { key:'student/new-request', title:'New request', back:'student/office-detail', footer:{label:'Continue to payment choice',target:'student/payment'}, blocks:[
    {type:'section',title:'Tell the office what you need',body:'Registrar · non-official demo service'},
    {type:'field',label:'Request type',value:'Select a configured service',icon:'expand_more'},
    {type:'field',label:'Purpose',value:'Select a configured purpose',icon:'expand_more'},
    {type:'field',label:'Notes',value:'Add only details the office needs'},
    {type:'section',title:'Supporting evidence'},
    {type:'choice',title:'Choose file from device',body:'Private upload; accepted type and size come from the service.',target:'student/upload-state'},
    {type:'choice',title:'Use camera',body:'Capture evidence with Android camera permission.',target:'student/upload-state'},
    {type:'notice',title:'Review before sending',body:'A free request skips checkout. A fee-based request uses the configured server amount.'},
  ]},
  { key:'student/upload-state', title:'Add evidence', back:'student/new-request', footer:{label:'Use selected file',target:'student/new-request'}, blocks:[
    {type:'section',title:'Device file or camera',body:'Android file picker and camera return one file for review.'},
    {type:'row',title:'Selected file',body:'sample-evidence.pdf · private',target:'student/new-request',icon:'description'},
    {type:'state',title:'File cannot be uploaded',body:'Check the request type’s accepted format and size, then choose another file.',icon:'error',action:'Choose another file',target:'student/upload-state'},
    {type:'notice',title:'Camera unavailable?',body:'Choose a file from the device instead. Permission can be changed in Android settings.'},
  ]},
  { key:'student/payment', title:'Payment choice', back:'student/new-request', footer:{label:'Continue',target:'student/payment-pending'}, blocks:[
    {type:'section',title:'Choose how to pay',body:'REQ-2026-0148 · configured demo fee'},
    {type:'notice',title:'Amount due',body:'The server loads the exact test amount. The Android app cannot change it.',dark:true},
    {type:'choice',title:'eGovPay sandbox',body:'Open approved checkout; the app return is not proof of payment.',selected:true,target:'student/payment-pending'},
    {type:'choice',title:'Cash at Cashiering',body:'Show the payment reference. An authorized teller confirms cash.',target:'student/cash-instructions'},
    {type:'notice',title:'No fee for this service?',body:'The request continues directly to Processing without checkout.'},
  ]},
  { key:'student/payment-pending', title:'Payment status', back:'student/requests', footer:{label:'Refresh status',target:'student/requests',kind:'outlined'}, blocks:[
    {type:'status',title:'pending',body:'Awaiting authenticated callback and server reconciliation.'},
    {type:'section',title:'Checkout opened',body:'Returning to IServe does not mark this payment paid.'},
    {type:'notice',title:'Reference PAY-2026-0088',body:'Keep this reference until the provider status is confirmed.'},
    {type:'row',title:'View request',body:'Request progress and payment status are tracked separately.',target:'student/request-detail'},
    {type:'row',title:'Open labeled simulator',body:'Use only when official sandbox access is unavailable.',target:'demo/simulator'},
    {type:'state',title:'Checkout did not finish',body:'Payment is still pending. Refresh the verified status or retry when allowed.',icon:'info',action:'Review payment choice',target:'student/payment'},
  ]},
  { key:'student/cash-instructions', title:'Pay with cash', back:'student/payment', footer:{label:'Track this request',target:'student/request-detail'}, blocks:[
    {type:'notice',title:'Payment reference',body:'PAY-2026-0088 · present at Cashiering with the configured demo amount.',dark:true},
    {type:'status',title:'pending',body:'Only an authorized teller can confirm paid cash.'},
    {type:'section',title:'What happens next'},
    {type:'status',title:'Find the teller',body:'Share the reference and confirm the exact amount.'},
    {type:'status',title:'Watch for confirmation',body:'IServe updates in-app when the teller confirms.'},
  ]},
  { key:'student/requests', title:'My requests', nav:'student', right:{target:'student/notifications',label:'Notifications'}, blocks:[
    {type:'hero',title:'See what happens next.',body:'Request progress and payment status stay separate.'},
    {type:'chips',items:['All','Active','Ready','Completed'],selected:'All'},
    {type:'row',title:'Certificate of enrollment',body:'Processing · Registrar',meta:'REQ-2026-0148 · payment paid',target:'student/request-detail'},
    {type:'row',title:'Payment inquiry',body:'Awaiting Payment · Cashiering',meta:'REQ-2026-0131 · payment pending',target:'student/cash-instructions'},
    {type:'row',title:'Document request',body:'Ready for Pickup/Download · Registrar',meta:'REQ-2026-0116 · verified PDF',target:'student/verified-file'},
    {type:'button',label:'New request',target:'student/new-request',icon:'add'},
  ]},
  { key:'student/request-detail', title:'Request detail', back:'student/requests', footer:{label:'Ask the office',target:'student/thread',kind:'outlined'}, blocks:[
    {type:'summary',label:'REQ-2026-0148',title:'Certificate of enrollment',body:'Processing · Registrar · payment paid',target:'student/verified-file'},
    {type:'section',title:'Status history',body:'Each change records actor and time.'},
    {type:'timeline',items:[['Submitted','Student · Oct 10, 9:04 AM','done'],['Awaiting Payment','System · Oct 10, 9:05 AM','done'],['Processing','Registrar · Oct 10, 10:31 AM','current'],['Ready for Pickup/Download','Not reached','future'],['Completed','Not reached','future']]},
    {type:'row',title:'Verified PDF',body:'Available only after staff verification and payment gate.',target:'student/verified-file',icon:'picture_as_pdf'},
    {type:'row',title:'Pickup QR',body:'Appears when a physical result is ready.',target:'student/pickup-token',icon:'qr_code'},
  ]},
  { key:'student/verified-file', title:'Verified file', back:'student/request-detail', footer:{label:'Download verified PDF',target:'student/request-detail',icon:'download'}, blocks:[
    {type:'section',title:'Your document is ready',body:'REQ-2026-0116 · Ready for Pickup/Download'},
    {type:'notice',title:'Verified PDF',body:'private-deliverable.pdf · uploaded by authorized Registrar staff',dark:true},
    {type:'status',title:'paid',body:'Download opens only for the owning student after payment and request checks.'},
    {type:'notice',title:'Private download',body:'The app requests a short-lived authorized file link. Do not share it.'},
  ]},
  { key:'student/pickup-token', title:'Pickup QR', back:'student/request-detail', blocks:[
    {type:'section',title:'Show this at pickup',body:'Ready for Pickup/Download · physical request'},
    {type:'scanner',hint:'Single-use token · valid for this request only'},
    {type:'notice',title:'Staff validates the token',body:'A QR scan or manual entry must pass the same server check before pickup completes.'},
  ]},
  { key:'student/consult', title:'Consultations', nav:'student', right:{target:'student/notifications',label:'Notifications'}, blocks:[
    {type:'hero',title:'Find a time that works.',body:'Choose Guidance or a CCS faculty member.'},
    {type:'choice',title:'Guidance consultation',body:'Available slots managed by a Guidance counselor.',selected:true},
    {type:'choice',title:'CCS faculty consultation',body:'Select a faculty member and view their availability.'},
    {type:'field',label:'Date',value:'Select date',icon:'calendar_month'},
    {type:'section',title:'Available times',body:'Demo availability; capacity checked again when booking.'},
    {type:'chips',items:['9:00 AM','10:30 AM','2:00 PM'],selected:'10:30 AM'},
    {type:'button',label:'Review booking',target:'student/appointment',icon:'event'},
  ]},
  { key:'student/appointment', title:'Review booking', back:'student/consult', footer:{label:'Confirm booking',target:'student/notifications'}, blocks:[
    {type:'notice',title:'Guidance consultation',body:'October 15, 2026 · 10:30 AM · Casal Campus',dark:true},
    {type:'field',label:'Optional note',value:'Add only useful private context'},
    {type:'notice',title:'Capacity is checked on confirm',body:'If the slot fills, choose another time. Cancellation and rescheduling create in-app updates.'},
    {type:'row',title:'Reschedule',body:'Pick another available slot before releasing this one.',target:'student/consult',icon:'event_repeat'},
    {type:'row',title:'Cancel appointment',body:'Record a cancellation and notify authorized staff.',target:'student/consult',icon:'event_busy'},
  ]},
  { key:'student/inbox', title:'Inquiry threads', nav:'student', right:{target:'student/notifications',label:'Notifications'}, blocks:[
    {type:'hero',title:'Keep the conversation together.',body:'Replies arrive in-app; messages are asynchronous.'},
    {type:'row',title:'Registrar inquiry',body:'Office replied · Certificate of enrollment',target:'student/thread',icon:'forum',meta:'Oct 10 · 9:12 AM'},
    {type:'row',title:'Accounting question',body:'Waiting for office reply',target:'student/thread',icon:'forum'},
    {type:'button',label:'Start an inquiry',target:'student/new-thread',icon:'add_comment'},
  ]},
  { key:'student/new-thread', title:'New inquiry', back:'student/inbox', footer:{label:'Send inquiry',target:'student/thread'}, blocks:[
    {type:'field',label:'Office',value:'Select destination office',icon:'expand_more'},
    {type:'field',label:'Subject',value:'Enter a short subject'},
    {type:'field',label:'Message',value:'Describe your question'},
    {type:'notice',title:'Assigned recipient',body:'The thread may be routed to office staff, Guidance, CCS secretary, or CCS faculty.'},
  ]},
  { key:'student/thread', title:'Registrar inquiry', back:'student/inbox', footer:{label:'Send reply',target:'student/inbox'}, blocks:[
    {type:'notice',title:'Assigned to Registrar',body:'REQ-2026-0148 · messages include sender and time.'},
    {type:'message',from:'Registrar · Oct 10, 9:05 AM',body:'Your request is in progress. The verified file will appear when ready.'},
    {type:'message',from:'You · Oct 10, 9:12 AM',body:'A verified PDF will work. Thank you.',mine:true},
    {type:'field',label:'Reply',value:'Write a concise reply'},
  ]},
  { key:'student/notifications', title:'Notifications', back:'student/home', blocks:[
    {type:'section',title:'Updates inside IServe',body:'No email or device push messages are sent.'},
    {type:'row',title:'Request moved to Processing',body:'Registrar · REQ-2026-0148',target:'student/request-detail',icon:'receipt_long',meta:'Unread · now'},
    {type:'row',title:'Payment awaiting confirmation',body:'PAY-2026-0088',target:'student/payment-pending',icon:'payments',meta:'Oct 10'},
    {type:'row',title:'Consultation booked',body:'October 15 · 10:30 AM',target:'student/appointment',icon:'event',meta:'Oct 10'},
    {type:'state',title:'Nothing new',body:'New status changes and replies will appear here while the app is open.',icon:'notifications_none'},
  ]},
  { key:'student/more', title:'More', nav:'student', blocks:[
    {type:'section',title:'Your IServe tools',body:'Student A · verified demo account'},
    {type:'row',title:'AI guidance',body:'Approved knowledge or one temporary file.',target:'shared/assistant',icon:'help'},
    {type:'row',title:'Notifications',body:'Live updates and read states.',target:'student/notifications',icon:'notifications'},
    {type:'row',title:'Privacy and profile',body:'Consent, account details, private profile image.',target:'student/consent',icon:'account_circle'},
    {type:'button',label:'Sign out',target:'shared/sign-in',kind:'outlined',icon:'logout'},
  ]},
  { key:'shared/assistant', title:'AI guidance', back:'student/more', footer:{label:'Send question',target:'shared/assistant'}, blocks:[
    {type:'notice',title:'Guidance only',body:'Answers use approved demo knowledge or your current temporary file. Ask an office for an official answer.',dark:true},
    {type:'message',from:'IServe assistant',body:'Which office or request step can I help explain?'},
    {type:'field',label:'Question',value:'Ask about a campus service'},
    {type:'row',title:'Analyze one temporary file',body:'PDF, JPG, or PNG · up to 10 MB · consent required.',target:'shared/ai-upload',icon:'attach_file'},
    {type:'state',title:'I cannot confirm that rule',body:'No approved knowledge covers this question. Contact the relevant office.',icon:'info'},
  ]},
  { key:'shared/ai-upload', title:'Temporary analysis', back:'shared/assistant', footer:{label:'Analyze this file',target:'shared/ai-error'}, blocks:[
    {type:'section',title:'Use a non-sensitive file',body:'One PDF, JPG, or PNG, up to 10 MB.'},
    {type:'choice',title:'I consent to temporary analysis',body:'The file is deleted immediately after analysis, including handled failures.',selected:true},
    {type:'field',label:'Choose file',value:'Select one device file',icon:'upload_file'},
    {type:'notice',title:'No saved chat history',body:'Only this answer is shown; the app does not retain prompts or uploaded-document history.'},
  ]},
  { key:'shared/ai-error', title:'Assistant unavailable', back:'shared/assistant', footer:{label:'Try again',target:'shared/assistant',kind:'outlined'}, blocks:[
    {type:'state',title:'Could not get an answer',body:'The AI provider did not respond. Your temporary file was deleted. Try again later or ask the office.',icon:'error'},
    {type:'notice',title:'Guidance only',body:'No provider failure should be presented as an official answer.'},
  ]},
  { key:'staff/home', title:'Office overview', nav:'staff', right:{target:'staff/inbox',label:'Inquiries',icon:'notifications'}, blocks:[
    {type:'hero',title:'Registrar today',body:'Your office queue, appointments, and public availability.'},
    {type:'metric',value:'08',label:'Requests needing review'},
    {type:'row',title:'Office availability',body:'Open · updated by staff today',target:'staff/availability',icon:'toggle_on'},
    {type:'row',title:'Announcements',body:'Publish a current office update.',target:'staff/availability',icon:'campaign'},
    {type:'row',title:'Request queue',body:'Filter Submitted, Processing, and Ready.',target:'staff/queue',icon:'assignment'},
    {type:'row',title:'Inquiry inbox',body:'2 messages need an office reply.',target:'staff/inbox',icon:'forum'},
  ]},
  { key:'staff/availability', title:'Availability', back:'staff/home', footer:{label:'Publish office update',target:'staff/home'}, blocks:[
    {type:'section',title:'What students see',body:'Choose one exact public status. Actor and time are recorded.'},
    {type:'choice',title:'Open',body:'Accepting the demonstrated service.',selected:true},
    {type:'choice',title:'On Lunch Break',body:'Temporarily unavailable.'},
    {type:'choice',title:'Cut-Off Reached',body:'Current service cut-off reached.'},
    {type:'choice',title:'Temporarily Closed',body:'Unavailable until staff update.'},
    {type:'field',label:'Status note',value:'Add an optional note'},
    {type:'field',label:'Announcement',value:'Add a current office update'},
  ]},
  { key:'staff/queue', title:'Request queue', nav:'staff', right:{target:'staff/inbox',label:'Inquiries',icon:'notifications'}, blocks:[
    {type:'section',title:'Registrar requests',body:'Office-scoped. Other offices’ private records stay hidden.'},
    {type:'chips',items:['All','Submitted','Processing','Ready'],selected:'All'},
    {type:'row',title:'Document request',body:'Submitted · evidence to review',target:'staff/request-detail',icon:'description',meta:'REQ-2026-0152 · payment pending'},
    {type:'row',title:'Certificate of enrollment',body:'Processing · verified account',target:'staff/request-detail',icon:'description',meta:'REQ-2026-0148 · payment paid'},
    {type:'state',title:'No requests in this filter',body:'Choose another status or wait for a new submission.',icon:'inbox'},
  ]},
  { key:'staff/request-detail', title:'Review request', back:'staff/queue', footer:{label:'Update status',target:'staff/rejection'}, blocks:[
    {type:'summary',label:'REQ-2026-0152',title:'Document request',body:'Submitted · payment pending',target:'staff/rejection'},
    {type:'row',title:'Evidence',body:'2 private files · open only for authorized review.',target:'staff/evidence',icon:'attach_file'},
    {type:'row',title:'Verified PDF upload',body:'Private deliverable, gated by request and payment.',target:'staff/verified-upload',icon:'upload_file'},
    {type:'row',title:'Pickup scanner',body:'For a ready physical request only.',target:'staff/scanner',icon:'qr_code_scanner'},
    {type:'notice',title:'Payment and request are separate',body:'Do not mark paid from this screen. Rejection or cancellation requires a reason.'},
  ]},
  { key:'staff/evidence', title:'Review evidence', back:'staff/request-detail', footer:{label:'Return to request',target:'staff/request-detail',kind:'outlined'}, blocks:[
    {type:'section',title:'Private evidence',body:'REQ-2026-0152 · authorized Registrar review'},
    {type:'row',title:'student-evidence.pdf',body:'PDF · uploaded by owning student',target:'staff/request-detail',icon:'description'},
    {type:'row',title:'supporting-image.jpg',body:'JPG · uploaded by owning student',target:'staff/request-detail',icon:'image'},
    {type:'notice',title:'Review only',body:'Files are not public. Access is limited by user and office policy.'},
  ]},
  { key:'staff/verified-upload', title:'Verified PDF', back:'staff/request-detail', footer:{label:'Upload verified PDF',target:'staff/request-detail'}, blocks:[
    {type:'section',title:'Deliver a verified file',body:'Use a private PDF. The owning student downloads only when gates pass.'},
    {type:'field',label:'PDF file',value:'Choose verified PDF',icon:'upload_file'},
    {type:'notice',title:'Payment gate',body:'An unpaid fee-based request cannot download the deliverable.'},
    {type:'state',title:'Upload needs a PDF',body:'Choose a PDF verified by authorized office staff.',icon:'error'},
  ]},
  { key:'staff/rejection', title:'Update status', back:'staff/request-detail', footer:{label:'Save status',target:'staff/queue'}, blocks:[
    {type:'section',title:'Choose the next state',body:'Only allowed transitions appear for this request.'},
    {type:'choice',title:'Processing',body:'Office work has started.',selected:true},
    {type:'choice',title:'Ready for Pickup/Download',body:'Physical result or verified PDF prepared.'},
    {type:'choice',title:'Rejected',body:'Reason required; paid record stays paid.'},
    {type:'choice',title:'Cancelled',body:'Reason required; manual follow-up when paid.'},
    {type:'field',label:'Reason when required',value:'Enter a clear reason'},
    {type:'notice',title:'Audit trail',body:'Previous state, new state, actor, time, and required reason are stored.'},
  ]},
  { key:'staff/scanner', title:'Pickup scanner', back:'staff/request-detail', footer:{label:'Enter token manually',target:'staff/manual-token',kind:'outlined'}, blocks:[
    {type:'section',title:'Scan the student’s QR',body:'CameraX preview after Android camera permission. Server checks every token.'},
    {type:'scanner',hint:'Align the pickup QR inside the frame'},
    {type:'notice',title:'One pickup only',body:'Unknown, expired, used, or unauthorized tokens are rejected.'},
    {type:'row',title:'Camera permission denied?',body:'Manual token entry remains available.',target:'staff/manual-token',icon:'keyboard'},
  ]},
  { key:'staff/manual-token', title:'Manual pickup token', back:'staff/scanner', footer:{label:'Validate pickup',target:'staff/token-result'}, blocks:[
    {type:'state',title:'Camera not available',body:'Enter the code from the student’s pickup screen. The same server checks apply.',icon:'no_photography'},
    {type:'field',label:'Pickup token',value:'Enter token exactly'},
    {type:'notice',title:'Permission can be changed later',body:'Open Android settings if you want to use the camera on a later pickup.'},
  ]},
  { key:'staff/token-result', title:'Pickup result', back:'staff/manual-token', footer:{label:'Scan another token',target:'staff/scanner'}, blocks:[
    {type:'state',title:'Token already used',body:'This pickup was already completed. Check the request before trying another code.',icon:'error'},
    {type:'notice',title:'Server validation required',body:'A scan or manual entry never completes a request without office authority and an unused token.'},
  ]},
  { key:'staff/guidance', title:'Guidance schedule', nav:'staff', blocks:[
    {type:'section',title:'Guidance availability',body:'Counselor-managed demo slots and capacity.'},
    {type:'field',label:'Date',value:'October 15, 2026',icon:'calendar_month'},
    {type:'row',title:'9:00 AM',body:'2 of 3 booked · Casal Campus',target:'staff/guidance-slot',icon:'event'},
    {type:'row',title:'10:30 AM',body:'3 of 3 booked · full',target:'staff/guidance-slot',icon:'event_busy'},
    {type:'row',title:'2:00 PM',body:'Open · capacity 3',target:'staff/guidance-slot',icon:'event_available'},
    {type:'button',label:'Add available slot',target:'staff/guidance-slot',icon:'add'},
  ]},
  { key:'staff/guidance-slot', title:'Guidance slot', back:'staff/guidance', footer:{label:'Save slot',target:'staff/guidance'}, blocks:[
    {type:'field',label:'Date',value:'Select date',icon:'calendar_month'},
    {type:'field',label:'Start time',value:'Select time',icon:'schedule'},
    {type:'field',label:'Duration',value:'Select configured duration',icon:'expand_more'},
    {type:'field',label:'Capacity',value:'3'},
    {type:'notice',title:'Capacity check',body:'The server rejects a booking when the active count reaches the configured limit.'},
  ]},
  { key:'staff/faculty', title:'Faculty schedule', back:'staff/more', footer:{label:'Add availability',target:'staff/faculty-slot'}, blocks:[
    {type:'section',title:'Your CCS consultations',body:'Faculty-specific slots, bookings, and assigned inquiries.'},
    {type:'field',label:'Date',value:'October 15, 2026',icon:'calendar_month'},
    {type:'row',title:'10:00 AM',body:'Booked · authorized appointment view',target:'staff/faculty-slot',icon:'event'},
    {type:'row',title:'1:30 PM',body:'Available · capacity 1',target:'staff/faculty-slot',icon:'event_available'},
    {type:'row',title:'Assigned inquiries',body:'Questions routed to this faculty account.',target:'staff/inbox',icon:'forum'},
  ]},
  { key:'staff/faculty-slot', title:'Faculty availability', back:'staff/faculty', footer:{label:'Save availability',target:'staff/faculty'}, blocks:[
    {type:'field',label:'Date',value:'Select date',icon:'calendar_month'},
    {type:'field',label:'Start time',value:'Select time',icon:'schedule'},
    {type:'field',label:'Capacity',value:'1'},
    {type:'notice',title:'Your schedule only',body:'A faculty member manages their own slots. Authorized CCS secretary coordination is separate.'},
  ]},
  { key:'staff/ccs', title:'CCS coordination', back:'staff/more', blocks:[
    {type:'section',title:'Secretary workspace',body:'Coordinate faculty schedules and assigned CCS work within authorization.'},
    {type:'row',title:'Faculty availability',body:'Review selected CCS faculty slots.',target:'staff/faculty',icon:'event'},
    {type:'row',title:'Assigned requests',body:'CCS office requests only.',target:'staff/queue',icon:'assignment'},
    {type:'row',title:'Assigned inquiries',body:'Route or reply to CCS threads.',target:'staff/inbox',icon:'forum'},
    {type:'notice',title:'Scope stays with CCS',body:'Secretary access does not expose unrelated offices or student records.'},
  ]},
  { key:'staff/inbox', title:'Office inquiries', nav:'staff', blocks:[
    {type:'section',title:'Assigned conversations',body:'Asynchronous messages with sender, assignment, and time.'},
    {type:'row',title:'Certificate of enrollment',body:'New student reply · Registrar',target:'staff/thread',icon:'forum'},
    {type:'row',title:'Document requirements',body:'Waiting for office reply',target:'staff/thread',icon:'forum'},
    {type:'state',title:'No assigned messages',body:'New authorized inquiries will appear here.',icon:'inbox'},
  ]},
  { key:'staff/thread', title:'Office reply', back:'staff/inbox', footer:{label:'Send office reply',target:'staff/inbox'}, blocks:[
    {type:'notice',title:'Assigned to Registrar',body:'Only authorized staff and the owning student can read this thread.'},
    {type:'message',from:'Student · Oct 10, 9:12 AM',body:'Do I need to bring a printed receipt?'},
    {type:'field',label:'Reply',value:'Write a clear reply'},
  ]},
  { key:'staff/more', title:'Staff tools', nav:'staff', blocks:[
    {type:'section',title:'Role-scoped tools',body:'Only screens allowed by the signed-in account appear in the Android app.'},
    {type:'row',title:'Guidance schedule',body:'Counselor capability only.',target:'staff/guidance',icon:'event'},
    {type:'row',title:'Faculty schedule',body:'CCS faculty capability only.',target:'staff/faculty',icon:'event'},
    {type:'row',title:'CCS coordination',body:'CCS secretary capability only.',target:'staff/ccs',icon:'group'},
    {type:'row',title:'Pickup scanner',body:'Authorized office staff only.',target:'staff/scanner',icon:'qr_code_scanner'},
    {type:'button',label:'Sign out',target:'shared/sign-in',kind:'outlined',icon:'logout'},
  ]},
  { key:'teller/cash', title:'Cash payments', nav:'teller', blocks:[
    {type:'section',title:'Find a payment',body:'Teller-only lookup across configured demo offices.'},
    {type:'field',label:'Payment reference',value:'PAY-2026-0088'},
    {type:'button',label:'Search reference',target:'teller/pending',icon:'search'},
    {type:'notice',title:'Teller access only',body:'This role can confirm pending cash, but cannot edit unrelated request content.'},
  ]},
  { key:'teller/pending', title:'Pending cash', nav:'teller', blocks:[
    {type:'section',title:'Payment found',body:'PAY-2026-0088 · Cashiering'},
    {type:'summary',label:'AUTHORITATIVE AMOUNT',title:'Configured demo fee',body:'pending · cash · request REQ-2026-0131',target:'teller/confirm'},
    {type:'choice',title:'I received the exact amount',body:'Confirm only after checking the reference and amount.',selected:true},
    {type:'button',label:'Review confirmation',target:'teller/confirm',icon:'check'},
    {type:'notice',title:'No duplicate confirmation',body:'The server rejects a second paid event and records teller actor and time.'},
  ]},
  { key:'teller/confirm', title:'Confirm cash', back:'teller/pending', footer:{label:'Confirm as paid',target:'teller/history'}, blocks:[
    {type:'notice',title:'PAY-2026-0088',body:'pending · cash · exact server-configured amount',dark:true},
    {type:'choice',title:'Reference and cash verified',body:'I received the amount shown for this payment.',selected:true},
    {type:'state',title:'Already paid?',body:'A duplicate confirmation is rejected. Do not collect the same payment twice.',icon:'info'},
  ]},
  { key:'teller/history', title:'Cash history', nav:'teller', blocks:[
    {type:'section',title:'Confirmed by tellers',body:'Auditable reference, actor, and time.'},
    {type:'row',title:'PAY-2026-0088',body:'paid · confirmed by authorized teller',target:'teller/confirm',icon:'receipt_long'},
    {type:'state',title:'Nothing to show',body:'Confirmed cash payments appear here after server validation.',icon:'history'},
  ]},
  { key:'demo/simulator', title:'Payment simulator', back:'student/payment-pending', blocks:[
    {type:'notice',title:'SIMULATOR • NON-OFFICIAL',body:'Use only while approved eGovPay sandbox access is unavailable.',dark:true},
    {type:'section',title:'Test a payment outcome',body:'Internal statuses use the same trusted callback and reconciliation contract.'},
    {type:'row',title:'paid',body:'Server confirms a reconciled test outcome.',target:'student/requests',icon:'check_circle'},
    {type:'row',title:'failed',body:'Keep request progress separate.',target:'student/payment-pending',icon:'error'},
    {type:'row',title:'expired',body:'Show retry only when allowed.',target:'student/payment-pending',icon:'schedule'},
    {type:'row',title:'cancelled',body:'Do not infer request completion.',target:'student/payment-pending',icon:'close'},
  ]},
  { key:'shared/states', title:'Loading & recovery', back:'student/home', blocks:[
    {type:'state',title:'Loading offices',body:'Office status and announcements are being refreshed.',icon:'sync'},
    {type:'state',title:'No requests yet',body:'Start a request to track it here.',icon:'receipt_long',action:'New request',target:'student/new-request'},
    {type:'state',title:'Could not load updates',body:'Check the connection and try again.',icon:'wifi_off',action:'Retry',target:'student/notifications'},
  ]},
];

const ranges = { 1:[0,9], 2:[9,20], 3:[20,30], 4:[30,40], 5:[40,all.length] };
if (!ranges[phase]) throw new Error('Invalid build phase');
const [start,end] = ranges[phase];
for (let i = start; i < Math.min(end, all.length); i++) {
  const spec = all[i];
  if (page.children.some(n => n.name === `IServe 2.1 • ${spec.key}`)) continue;
  screen(spec, i);
}
return { phase, createdNodeIds: created, screens, totalPlanned: all.length, pageId: page.id };
