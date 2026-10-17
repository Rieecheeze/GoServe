// Student Google entry stays separate from assigned Firebase email/password access.
const page=await figma.getNodeByIdAsync('14:2');
await figma.setCurrentPageAsync(page);
for(const font of [
  {family:'Archivo',style:'Bold'}, {family:'Roboto',style:'Regular'},
  {family:'Roboto',style:'Medium'}, {family:'Material Symbols Rounded',style:'Regular'},
]) await figma.loadFontAsync(font);
const made=[];
function rgb(s){return {r:parseInt(s.slice(1,3),16)/255,g:parseInt(s.slice(3,5),16)/255,b:parseInt(s.slice(5,7),16)/255};}
function paint(s){return [{type:'SOLID',color:rgb(s)}];}
function box(parent,name,x,y,w,h,color=null,r=0,stroke=null){const n=figma.createFrame();made.push(n.id);n.name=name;n.resize(w,h);n.x=x;n.y=y;n.cornerRadius=r;n.fills=color?paint(color):[];if(stroke){n.strokes=paint(stroke);n.strokeWeight=1;}parent.appendChild(n);return n;}
function text(parent,name,value,x,y,w,size,color,weight='Regular',family='Roboto'){const n=figma.createText();made.push(n.id);n.name=name;n.fontName={family,style:weight};n.characters=value;n.fontSize=size;n.fills=paint(color);n.resize(w,1);n.textAutoResize='HEIGHT';n.x=x;n.y=y;parent.appendChild(n);return n;}
function colors(dark){return dark?{bg:'#080808',paper:'#242424',ink:'#F4F4F4',muted:'#BDBDBD',line:'#4A4A4A',primary:'#F2F2F2',primaryText:'#171717'}:{bg:'#F4F4F4',paper:'#FFFFFF',ink:'#171717',muted:'#595959',line:'#D5D5D5',primary:'#171717',primaryText:'#FFFFFF'};}
function base(key,index,dark,title){const c=colors(dark),prefix=dark?'dark/':'';
  const f=box(page,`GoServe 2.1 • ${prefix}${key}`,40+(index%6)*450+(dark?2850:0),40+Math.floor(index/6)*925,390,844,c.bg);f.clipsContent=true;
  const stat=box(f,'Android status inset',0,0,390,24,c.bg);
  text(stat,'time','9:41',20,3,50,12,c.ink,'Medium');text(stat,'system','LTE  86%',304,3,80,11,c.ink,'Medium');
  const back=box(f,`go:${prefix}shared/sign-in | Back`,12,36,48,48,null,24);
  text(back,'icon','arrow_back',12,10,28,24,c.ink,'Regular','Material Symbols Rounded');
  text(f,'screen title',title,24,151,342,29,c.ink,'Bold','Archivo');
  const gesture=box(f,'Android gesture inset',0,820,390,24,c.bg);
  box(gesture,'gesture handle',141,10,108,4,c.ink,2);
  return {f,c,prefix};
}
function input(parent,c,label,y){const field=box(parent,`input / ${label}`,24,y,342,56,c.paper,28,c.line);
  text(field,'placeholder',label,20,19,300,14,c.muted);return field;}
function action(parent,c,target,label,y){const n=box(parent,`go:${target} | ${label}`,24,y,342,56,c.primary,28);
  text(n,'label',label,16,19,310,14,c.primaryText,'Medium').textAlignHorizontal='CENTER';return n;}
function link(parent,c,target,label,y){const n=box(parent,`go:${target} | ${label}`,92,y,206,44,null,20);
  const t=text(n,'label',label,0,12,206,13,c.ink,'Medium');t.textAlignHorizontal='CENTER';t.textDecoration='UNDERLINE';return n;}

// Main student entry shows the office credential route without duplicating fields.
for(const dark of [false,true]){
  const prefix=dark?'dark/':'';
  const login=page.children.find(n=>n.name===`GoServe 2.1 • ${prefix}shared/sign-in`);
  const demo=login.children.find(n=>n.name.includes('Explore demo accounts'));
  demo.name=`go:${prefix}shared/demo-login | Office or demo sign-in`;
  demo.children.find(n=>n.type==='TEXT').characters='Office or demo sign-in';
  const note=login.children.find(n=>n.name==='demo note');
  note.characters='Office users and demo accounts use assigned email and password.';
  const student=login.children.find(n=>n.name==='student auth note');
  student.characters='Students use TIP Google authentication.';
}

// The existing role list now selects an assigned demo account; it never bypasses sign-in.
for(const dark of [false,true]){
  const prefix=dark?'dark/':'';
  const f=page.children.find(n=>n.name===`GoServe 2.1 • ${prefix}shared/demo-login`);
  f.name=`GoServe 2.1 • ${prefix}shared/demo-accounts`;
  f.children.find(n=>n.name==='Material top app bar').children.find(n=>n.name==='screen title').characters='Demo accounts';
  const body=f.children.find(n=>n.name==='Scrollable Android content');
  const heading=body.children.find(n=>n.name.startsWith('section /'));
  heading.findAll(n=>n.type==='TEXT'&&n.name==='heading')[0].characters='Pick a demo account';
  heading.findAll(n=>n.type==='TEXT'&&n.name==='description')[0].characters='Each pre-created account has assigned credentials and a specific role.';
  const notice=body.children.find(n=>n.name.startsWith('notice /'));
  notice.findAll(n=>n.type==='TEXT'&&n.name==='title')[0].characters='Sign-in still required';
  notice.findAll(n=>n.type==='TEXT'&&n.name==='body')[0].characters='Select a role, then enter that account’s password. Preview access follows its assigned role.';
  for(const [role,target,title,detail] of [
    ['student','student','Student demo','See the student flow with demo credentials.'],
    ['staff','staff','Office staff demo','See assigned office queues and schedules.'],
    ['teller','teller','Teller demo','See cash payment verification tools.'],
  ]){
    const row=body.children.find(n=>n.name.includes('Preview '+role+' entry'));
    row.name=`go:${prefix}shared/demo-password-${target} | ${title}`;
    row.findAll(n=>n.type==='TEXT'&&n.name==='title')[0].characters=title;
    row.findAll(n=>n.type==='TEXT'&&n.name==='detail')[0].characters=detail;
  }
}

for(const dark of [false,true]){
  const {f,c,prefix}=base('shared/demo-login',57,dark,'Office & demo sign-in');
  text(f,'support','Use your assigned GoServe email to continue.',24,200,342,14,c.muted);
  box(f,'divider left',24,260,137,1,c.line);
  text(f,'divider label','OR',179,252,32,12,c.muted,'Medium').textAlignHorizontal='CENTER';
  box(f,'divider right',229,260,137,1,c.line);
  input(f,c,'Email address',301);
  action(f,c,`${prefix}shared/demo-password-staff`,'Continue',387);
  link(f,c,`${prefix}shared/demo-accounts`,'Try it first',462);
  text(f,'help','Office access and demos use pre-created accounts. Your role decides which screens open.',35,528,320,13,c.muted).textAlignHorizontal='CENTER';
}
for(const [role,index,title,destination] of [
  ['student',58,'Student demo','student/home'],
  ['staff',59,'Office account','staff/home'],
  ['teller',60,'Teller account','teller/cash'],
])for(const dark of [false,true]){
  const {f,c,prefix}=base(`shared/demo-password-${role}`,index,dark,'Enter password');
  text(f,'account role',title,24,201,342,16,c.ink,'Medium');
  text(f,'support','Use the password assigned to this pre-created account.',24,230,342,14,c.muted);
  const summary=box(f,'Account email summary',24,285,342,54,c.paper,18,c.line);
  text(summary,'label','Assigned demo email',16,18,310,14,c.muted);
  input(f,c,'Password',362);
  action(f,c,`${prefix}${destination}`,'Sign in',449);
  link(f,c,`${prefix}shared/demo-login`,'Change email',529);
  text(f,'access note','The signed-in account controls office and payment access.',35,585,320,13,c.muted).textAlignHorizontal='CENTER';
}
return {created:made,officeDemoEntry:true,passwordScreens:6};
