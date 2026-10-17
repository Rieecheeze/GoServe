// Arrange equivalent light and dark screens by complete user flow.
const page=await figma.getNodeByIdAsync('14:2');
await figma.setCurrentPageAsync(page);
await figma.loadFontAsync({family:'Archivo',style:'Bold'});
const groups=[
  ['01  ENTRY & ACCOUNTS',[
    'shared/sign-in','shared/sign-up','shared/demo-login','shared/demo-accounts',
    'shared/demo-password-student','shared/demo-password-staff','shared/demo-password-teller',
    'student/consent','student/settings','staff/settings','teller/settings',
  ]],
  ['02  STUDENT HOME & OFFICES',[
    'student/home','student/offices','student/offices-documents','student/offices-payments',
    'student/office-registrar','student/office-accounting','student/office-cashiering',
    'student/office-osa','student/office-guidance','student/office-ccs',
  ]],
  ['03  REQUESTS & PAYMENTS',[
    'student/new-request','student/new-request-osa','student/upload-state','student/payment',
    'student/payment-osa','student/payment-pending','student/cash-instructions',
    'student/requests','student/request-detail','student/verified-file','student/pickup-token',
  ]],
  ['04  CONSULTATIONS & MESSAGES',[
    'student/consult','student/appointment','student/inbox','student/new-thread',
    'student/thread','student/notifications','student/more',
  ]],
  ['05  AI & SHARED STATES',[
    'shared/assistant','shared/ai-upload','shared/ai-response','shared/ai-error',
    'shared/states','demo/simulator',
  ]],
  ['06  OFFICE OPERATIONS',[
    'staff/home','staff/availability','staff/queue','staff/request-detail','staff/evidence',
    'staff/verified-upload','staff/rejection','staff/scanner','staff/manual-token',
    'staff/token-result','staff/inbox','staff/thread','staff/more',
  ]],
  ['07  SCHEDULE MANAGEMENT',[
    'staff/guidance','staff/guidance-slot','staff/faculty','staff/faculty-slot','staff/ccs',
  ]],
  ['08  TELLER OPERATIONS',[
    'teller/cash','teller/pending','teller/confirm','teller/history','teller/more',
  ]],
];
const all=page.children.filter(n=>n.type==='FRAME'&&n.name.startsWith('GoServe 2.1 • '));
const byKey=new Map(all.map(n=>[n.name.split(' • ').slice(1).join(' • '),n]));
const expected=new Set(groups.flatMap(g=>g[1]));
const light=all.filter(n=>!n.name.includes(' • dark/')).map(n=>n.name.split(' • ')[1]);
const missing=light.filter(k=>!expected.has(k));
if(missing.length||expected.size!==light.length)throw new Error(`Grouping mismatch: missing=${missing}, expected=${expected.size}, actual=${light.length}`);
for(const n of [...page.children].filter(n=>n.type==='TEXT'&&n.name.startsWith('group / ')))n.remove();
const ids=[];
function rgb(s){return {r:parseInt(s.slice(1,3),16)/255,g:parseInt(s.slice(3,5),16)/255,b:parseInt(s.slice(5,7),16)/255};}
function label(name,value,x,y){const n=figma.createText();ids.push(n.id);page.appendChild(n);n.name=name;
  n.fontName={family:'Archivo',style:'Bold'};n.characters=value;n.fontSize=24;
  n.fills=[{type:'SOLID',color:rgb('#171717')}];n.resize(1900,1);n.textAutoResize='HEIGHT';n.x=x;n.y=y;return n;}
let y=0;
for(let g=0;g<groups.length;g++){
  const [title,keys]=groups[g];
  label(`group / ${g+1} / light`,`${title}  ·  ${keys.length} screens`,40,y);
  label(`group / ${g+1} / dark`,`${title}  ·  ${keys.length} screens`,2890,y);
  for(const [i,key] of keys.entries())for(const prefix of ['', 'dark/']){
    const f=byKey.get(prefix+key);if(!f)throw new Error(`Missing screen ${prefix+key}`);
    f.x=40+(i%6)*450+(prefix?2850:0);
    f.y=y+90+Math.floor(i/6)*925;
  }
  y+=90+Math.ceil(keys.length/6)*925+60;
}
for(const n of page.children.filter(n=>n.type==='TEXT'&&n.name.startsWith('section / '))){
  n.y=-85;n.fontSize=32;
}
return {created:ids,groups:groups.map(([name,keys])=>({name,count:keys.length})),screens:all.length,canvasHeight:y};
