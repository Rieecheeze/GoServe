// Make student entry Google-only, with explicit Log in / Sign up paths.
const page=await figma.getNodeByIdAsync('14:2');
await figma.setCurrentPageAsync(page);
for(const font of [
  {family:'Archivo',style:'Bold'}, {family:'Roboto',style:'Regular'},
  {family:'Roboto',style:'Medium'},
]) await figma.loadFontAsync(font);
const created=[];
function rgb(s){return {r:parseInt(s.slice(1,3),16)/255,g:parseInt(s.slice(3,5),16)/255,b:parseInt(s.slice(5,7),16)/255};}
function paint(s){return [{type:'SOLID',color:rgb(s)}];}
function frame(parent,name,x,y,w,h,color=null,r=0,stroke=null){const n=figma.createFrame();created.push(n.id);n.name=name;n.resize(w,h);n.x=x;n.y=y;n.cornerRadius=r;n.fills=color?paint(color):[];if(stroke){n.strokes=paint(stroke);n.strokeWeight=1;}parent.appendChild(n);return n;}
function text(parent,name,value,x,y,w,size,color,weight='Regular',family='Roboto'){const n=figma.createText();created.push(n.id);n.name=name;n.fontName={family,style:weight};n.characters=value;n.fontSize=size;n.fills=paint(color);n.resize(w,1);n.textAutoResize='HEIGHT';n.x=x;n.y=y;parent.appendChild(n);return n;}
function theme(prefix){return prefix?{bg:'#080808',paper:'#242424',ink:'#F4F4F4',muted:'#BDBDBD',line:'#4A4A4A',active:'#F2F2F2',activeText:'#171717'}:{bg:'#F4F4F4',paper:'#FFFFFF',ink:'#171717',muted:'#595959',line:'#D5D5D5',active:'#171717',activeText:'#FFFFFF'};}
function redrawLogin(prefix){
  const f=page.children.find(n=>n.name==='GoServe 2.1 • '+prefix+'shared/sign-in');
  const c=theme(prefix);
  for(const n of [...f.children])if(!['Android status inset','Android gesture inset'].includes(n.name))n.remove();
  text(f,'entry headline','Welcome back',24,137,342,31,c.ink,'Bold','Archivo');
  text(f,'entry subtitle','Log in to GoServe with your TIP Google account.',24,183,342,14,c.muted);
  const tabs=frame(f,'Authentication tabs',24,225,342,48,c.paper,24,c.line);
  const login=frame(tabs,`go:${prefix}shared/sign-in | Log in`,3,3,168,42,c.active,21);
  text(login,'label','Log in',0,12,168,14,c.activeText,'Medium').textAlignHorizontal='CENTER';
  const signup=frame(tabs,`go:${prefix}shared/sign-up | Sign up`,171,3,168,42,null,21);
  text(signup,'label','Sign up',0,12,168,14,c.ink,'Medium').textAlignHorizontal='CENTER';
  const google=frame(f,`go:${prefix}student/home | Continue with TIP Google`,24,304,342,56,c.paper,28,c.line);
  const icon=frame(google,'Google monogram',18,15,26,26,prefix?'#303030':'#EBEBEB',13);
  text(icon,'letter','G',0,5,26,13,c.ink,'Medium').textAlignHorizontal='CENTER';
  text(google,'label','Continue with TIP Google',50,19,274,14,c.ink,'Medium').textAlignHorizontal='CENTER';
  frame(f,'divider left',24,401,137,1,c.line);
  text(f,'divider label','OR',179,393,32,12,c.muted,'Medium').textAlignHorizontal='CENTER';
  frame(f,'divider right',229,401,137,1,c.line);
  const demo=frame(f,`go:${prefix}shared/demo-login | Explore demo accounts`,24,431,342,56,c.paper,28,c.line);
  text(demo,'label','Explore demo accounts',16,19,310,14,c.ink,'Medium').textAlignHorizontal='CENTER';
  text(f,'demo note','Demo previews show how the app works. They do not open real records.',35,507,320,13,c.muted).textAlignHorizontal='CENTER';
  text(f,'student auth note','Students use Google authentication only.',35,555,320,13,c.muted).textAlignHorizontal='CENTER';
  return f;
}
function signupFrom(login,prefix,index){
  const c=theme(prefix);const f=login.clone();page.appendChild(f);
  created.push(f.id,...f.findAll(()=>true).map(n=>n.id));
  f.name='GoServe 2.1 • '+prefix+'shared/sign-up';
  f.x=40+(index%6)*450+(prefix?2850:0);f.y=40+Math.floor(index/6)*925;
  f.children.find(n=>n.name==='entry headline').characters='Get started';
  f.children.find(n=>n.name==='entry subtitle').characters='Sign up to GoServe with your TIP Google account.';
  const tabs=f.children.find(n=>n.name==='Authentication tabs');
  const loginTab=tabs.children.find(n=>n.name.endsWith(' | Log in'));
  const signTab=tabs.children.find(n=>n.name.endsWith(' | Sign up'));
  loginTab.fills=[];loginTab.children.find(n=>n.type==='TEXT').fills=paint(c.ink);
  signTab.fills=paint(c.active);signTab.children.find(n=>n.type==='TEXT').fills=paint(c.activeText);
  const google=f.children.find(n=>n.name.includes('Continue with TIP Google'));
  google.name=`go:${prefix}student/consent | Continue with TIP Google`;
  for(const n of [...f.children])if(['divider left','divider label','divider right'].includes(n.name)||n.name.includes('Explore demo accounts')||n.name==='demo note'||n.name==='student auth note')n.remove();
  const info=frame(f,'First-time profile note',24,390,342,136,c.paper,20,c.line);
  text(info,'title','Your first visit',18,18,306,16,c.ink,'Medium');
  text(info,'detail','After Google authentication, review the privacy notice and finish your GoServe profile. The demo stays separate from your account.',18,49,306,14,c.muted);
  text(f,'no-password note','No separate student password is needed.',35,549,320,13,c.muted).textAlignHorizontal='CENTER';
  return f;
}
const light=redrawLogin('');
const dark=redrawLogin('dark/');
signupFrom(light,'',56);
signupFrom(dark,'dark/',56);
return {created,lightLogin:light.id,darkLogin:dark.id,signUpScreens:2};
