// Follow-up to the Android v2.1 Figma build. Run once in the GoServe file.
const page = await figma.getNodeByIdAsync('14:2');
await figma.setCurrentPageAsync(page);
for (const font of [
  {family:'Archivo',style:'Bold'},
  {family:'Archivo',style:'SemiBold'},
  {family:'Roboto',style:'Regular'},
  {family:'Roboto',style:'Medium'},
  {family:'Material Symbols Rounded',style:'Regular'},
]) await figma.loadFontAsync(font);

const made=[];
const P={bg:'#F4F4F4',white:'#FFFFFF',ink:'#171717',muted:'#595959',line:'#D5D5D5',soft:'#EBEBEB',mid:'#D1D1D1',gray:'#A8A8A8'};
function rgb(s){const h=s.slice(1);return {r:parseInt(h.slice(0,2),16)/255,g:parseInt(h.slice(2,4),16)/255,b:parseInt(h.slice(4,6),16)/255};}
function fill(s){return [{type:'SOLID',color:rgb(s)}];}
function fr(p,name,x,y,w,h,bg=null,r=0,stroke=null){let n=figma.createFrame();made.push(n.id);n.name=name;n.resize(w,h);n.x=x;n.y=y;n.cornerRadius=r;n.fills=bg?fill(bg):[];if(stroke){n.strokes=fill(stroke);n.strokeWeight=1;}p.appendChild(n);return n;}
function tx(p,name,str,x,y,w,size=14,color=P.ink,weight='Regular',family='Roboto',line=null){let n=figma.createText();made.push(n.id);n.name=name;n.fontName={family,style:weight};n.characters=str;n.fontSize=size;n.lineHeight={unit:'PIXELS',value:line||Math.round(size*1.3)};n.fills=fill(color);n.resize(w,1);n.textAutoResize='HEIGHT';n.x=x;n.y=y;p.appendChild(n);return n;}
function ic(p,str,x,y,size=22,color=P.ink){return tx(p,'icon / '+str,str,x,y,size+4,size,color,'Regular','Material Symbols Rounded',size+3);}
function btn(p,target,label,x,y,w,h=48,dark=false){const n=fr(p,`go:${target} | ${label}`,x,y,w,h,dark?P.ink:P.white,24,dark?null:P.line);tx(n,'label',label,14,15,w-28,14,dark?P.white:P.ink,'Medium');return n;}
function pill(p,label,x,y,w,selected=false,target=null){const n=fr(p,target?`go:${target} | ${label}`:`chip / ${label}`,x,y,w,36,selected?P.ink:P.white,18,selected?null:P.line);tx(n,'label',label,10,10,w-20,12,selected?P.white:P.ink,'Medium');return n;}

// Replace the flat home list with a compact service discovery dashboard.
const home=await figma.getNodeByIdAsync('19:93');
for(const c of [...home.children]) if(c.name==='Material top app bar'||c.name==='Scrollable Android content') c.remove();
const app=fr(home,'Material top app bar',0,24,390,64,P.bg);
tx(app,'brand','GoServe',20,17,230,23,P.ink,'Bold','Archivo',29);
const bell=fr(app,'go:student/notifications | Notifications',330,8,48,48,null,24);ic(bell,'notifications',12,12,24);
const ct=fr(home,'Scrollable Android content',0,88,390,660,null);
ct.clipsContent=true;ct.overflowDirection='VERTICAL';
tx(ct,'hero title','Campus services, in one place',20,8,350,30,P.ink,'Bold','Archivo',36);
tx(ct,'hero support','Find the right office and check your request before heading across campus.',20,55,350,13,P.muted);
const search=fr(ct,'go:student/offices | Search offices or services',20,108,350,52,P.white,17,P.line);
ic(search,'search',16,14,23,P.muted);tx(search,'placeholder','Search offices or services',50,18,280,13,P.muted);
const filter=fr(ct,'Service filters',20,172,350,38,null);filter.clipsContent=true;
pill(filter,'All services',0,0,96,true,'student/offices');
pill(filter,'Documents',104,0,98,false,'student/offices');
pill(filter,'Payments',210,0,94,false,'student/offices');
pill(filter,'Consultations',312,0,108,false,'student/offices');
const active=fr(ct,'go:student/request-detail | Active request',20,226,350,154,P.ink,20);
tx(active,'eyebrow','ACTIVE REQUEST',16,15,180,11,P.white,'Medium');
const status=fr(active,'status / Processing',244,15,90,28,P.white,14);
tx(status,'label','Processing',12,7,68,11,P.ink,'Medium');
tx(active,'request title','Certificate of enrollment',16,50,312,21,P.white,'Bold','Archivo',27);
tx(active,'request metadata','Registrar · REQ-2026-0148',16,80,310,12,P.white);
const detail=fr(active,'go:student/request-detail | View request details',16,107,171,34,P.white,17);
tx(detail,'label','View request details',12,9,150,12,P.ink,'Medium');
tx(ct,'section / Campus offices','Campus offices',20,403,220,20,P.ink,'Bold','Archivo',25);
const see=fr(ct,'go:student/offices | See all offices',298,398,72,34,null,17);tx(see,'label','See all',10,10,62,12,P.ink,'Medium');
function officeTile(x,top,abbr,name,state,cover,detail,art){
  const n=fr(ct,`go:student/office-detail | ${name}`,x,445,168,208,P.white,18,P.line);
  const cap=fr(n,'office illustration',0,0,168,122,cover,18);
  tx(cap,'office mark',abbr,13,12,90,24,art,'Bold','Archivo',29);
  if(abbr==='REG'){
    const sheet=fr(cap,'document symbol',106,40,46,58,P.white,4);
    for(let j=0;j<3;j++)fr(sheet,'document line',9,12+j*12,28,2,P.ink);
  }else{
    for(let j=0;j<4;j++)fr(cap,'ledger bar',75+j*19,83-j*14,11,22+j*14,P.white,3);
  }
  const tag=fr(n,`status / ${state}`,11,131,state==='Open'?55:116,27,P.soft,13);
  tx(tag,'label',state,9,7,tag.width-17,11,P.ink,'Medium');
  tx(n,'office name',name,11,165,146,14,P.ink,'Bold','Archivo',19);
  tx(n,'office detail',detail,11,184,146,11,P.muted);
}
officeTile(20,0,'REG','Registrar','Open',P.mid,'Documents',P.muted);
officeTile(202,0,'ACC','Student Accounting','On Lunch Break',P.gray,'Accounting',P.ink);

// Add a dedicated directory so Home search and See all have a destination.
function basicScreen(name,index,title,back,role=null){
  const f=fr(page,`GoServe 2.1 • ${name}`,40+(index%6)*450,40+Math.floor(index/6)*925,390,844,P.bg);f.clipsContent=true;
  const status=fr(f,'Android status inset',0,0,390,24,P.bg);tx(status,'time','9:41',20,3,50,12,P.ink,'Medium');tx(status,'system','LTE  86%',304,3,80,11,P.ink,'Medium');
  const bar=fr(f,'Material top app bar',0,24,390,64,P.bg);
  if(back){let b=fr(bar,`go:${back} | Back`,8,8,48,48,null,24);ic(b,'arrow_back',12,12,24);}
  tx(bar,'screen title',title,back?64:20,19,300,21,P.ink,'Bold','Archivo',27);
  const body=fr(f,'Scrollable Android content',0,88,390,656,null);body.clipsContent=true;body.overflowDirection='VERTICAL';
  const gesture=fr(f,'Android gesture inset',0,820,390,24,P.bg);fr(gesture,'gesture handle',141,10,108,4,P.ink,2);
  return {f,body};
}
const directory=basicScreen('student/offices',48,'Campus offices','student/home');
tx(directory.body,'intro','Find an office',20,13,350,26,P.ink,'Bold','Archivo',32);
tx(directory.body,'support','Check availability and announcements before starting.',20,50,350,13,P.muted);
const ds=fr(directory.body,'Search offices or services',20,88,350,52,P.white,16,P.line);ic(ds,'search',14,14,22,P.muted);tx(ds,'placeholder','Search offices or services',48,17,290,14,P.muted);
pill(directory.body,'All services',20,152,96,true);pill(directory.body,'Documents',124,152,98);pill(directory.body,'Payments',230,152,94);
const offices=[
 ['Registrar','Open','Casal · Arlegui','Document requests and verified PDFs'],
 ['Student Accounting','On Lunch Break','Casal · Arlegui','Accounting inquiries and fee-linked requests'],
 ['Cashiering','Open','Casal · Arlegui','Cash payment confirmation'],
 ['Guidance and Counseling','Cut-Off Reached','Casal · Arlegui','Guidance consultations'],
 ['CCS secretary','Temporarily Closed','Casal · Arlegui','Faculty coordination'],
];
for(let j=0;j<offices.length;j++){
  const [name,state,campus,service]=offices[j],y=206+j*112;
  const row=fr(directory.body,`go:student/office-detail | ${name}`,20,y,350,100,P.white,18,P.line);
  tx(row,'office name',name,15,12,240,16,P.ink,'Bold','Archivo');
  tx(row,'campus',campus,15,36,250,12,P.muted);
  tx(row,'service',service,15,60,320,12,P.muted);
  const tag=fr(row,`status / ${state}`,265,12,70,25,P.soft,13);tx(tag,'label',state,8,6,62,10,P.ink,'Medium');
}

// Role-safe More and Settings routes. The theme control lives only in Settings.
function settings(key,index,back){
  const {f,body}=basicScreen(key,index,'Settings',back);
  tx(body,'heading','Appearance',20,18,350,22,P.ink,'Bold','Archivo',28);
  tx(body,'description','Choose how GoServe looks on this device.',20,49,350,13,P.muted);
  const theme=fr(body,`go:dark/${key} | Turn dark mode on`,20,92,350,94,P.white,18,P.line);
  ic(theme,'dark_mode',16,20,25);tx(theme,'title','Dark mode',58,17,202,16,P.ink,'Medium');
  tx(theme,'detail','Off · light appearance',58,44,210,13,P.muted);
  const sw=fr(theme,'Appearance switch',288,31,46,28,P.mid,14);fr(sw,'thumb',3,3,22,22,P.white,11);
  tx(body,'section / Account','Account',20,220,350,20,P.ink,'Bold','Archivo',26);
  const privacy=fr(body,'Privacy summary',20,253,350,90,P.white,18,P.line);
  tx(privacy,'title','Your account and privacy',16,14,300,15,P.ink,'Medium');
  tx(privacy,'detail','Access follows your assigned role. No public registration.',16,41,310,13,P.muted);
  const about=fr(body,'About GoServe',20,359,350,78,P.white,18,P.line);
  tx(about,'title','About GoServe',16,13,300,15,P.ink,'Medium');
  tx(about,'detail','TIP Manila demo · version 2.1',16,40,310,13,P.muted);
  return f;
}
settings('student/settings',49,'student/more');
settings('staff/settings',50,'staff/more');
settings('teller/settings',51,'teller/more');
const tm=basicScreen('teller/more',52,'More',null);
tx(tm.body,'heading','Teller account',20,18,350,25,P.ink,'Bold','Archivo',30);
tx(tm.body,'detail','Cash payments and assigned account settings.',20,54,350,13,P.muted);
const sr=fr(tm.body,'go:teller/settings | Settings',20,104,350,78,P.white,18,P.line);ic(sr,'settings',16,26,24);tx(sr,'title','Settings',58,15,260,16,P.ink,'Medium');tx(sr,'detail','Appearance and account information',58,43,260,13,P.muted);
const out=fr(tm.body,'go:shared/sign-in | Sign out',20,196,350,58,P.white,18,P.line);ic(out,'logout',16,17,24);tx(out,'label','Sign out',58,20,250,14,P.ink,'Medium');

// Existing More panels gain a Settings entry above Sign out.
for(const [screenId,target] of [['24:2','student/settings'],['26:362','staff/settings']]){
  const f=await figma.getNodeByIdAsync(screenId);
  if(!f)continue;
  const body=f.children.find(c=>c.name==='Scrollable Android content');
  if(!body)continue;
  const row=fr(body,`go:${target} | Settings`,0,0,350,78,P.white,18,P.line);
  ic(row,'settings',16,26,23);tx(row,'title','Settings',54,13,270,16,P.ink,'Medium');tx(row,'detail','Light and dark appearance',54,40,270,13,P.muted);
  const signout=body.children.find(c=>c.name.includes('Sign out'));
  if(signout)body.insertChild(body.children.indexOf(signout),row);
}

// Teller navigation never routes into staff tools.
for(const f of page.children.filter(n=>n.type==='FRAME'&&n.name.includes('teller/'))){
  for(const n of f.findAll(n=>n.name?.startsWith('go:staff/more | More')))n.name='go:teller/more | More';
}

// User-visible v2.1 brand is GoServe; archived v2.0 concepts remain intact.
page.name='GoServe • Android v2.1';
for(const n of page.findAll(()=>true)){
  if(n.type==='TEXT'&&/IServe/i.test(n.characters))n.characters=n.characters.replace(/IServe/gi,'GoServe');
  if(/IServe/i.test(n.name))n.name=n.name.replace(/IServe/gi,'GoServe');
}
return {created:made,screens:['student/home rebuilt','student/offices','student/settings','staff/settings','teller/settings','teller/more']};
