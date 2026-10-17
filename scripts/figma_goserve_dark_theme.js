// Clone a batch of the 53 light Android screens into the dark theme.
// Replace __START__ and __END__ with a half-open index range.
const start=__START__, end=__END__;
const page=await figma.getNodeByIdAsync('14:2');
await figma.setCurrentPageAsync(page);
for(const font of [
  {family:'Archivo',style:'Bold'}, {family:'Archivo',style:'SemiBold'},
  {family:'Roboto',style:'Regular'}, {family:'Roboto',style:'Medium'},
  {family:'Material Symbols Rounded',style:'Regular'},
]) await figma.loadFontAsync(font);
const lights=page.children.filter(n=>n.type==='FRAME'&&n.name.startsWith('GoServe 2.1 • ')&&!n.name.includes(' • dark/'))
  .sort((a,b)=>a.y-b.y||a.x-b.x);
function hx(c){return '#'+[c.r,c.g,c.b].map(x=>Math.round(x*255).toString(16).padStart(2,'0')).join('').toUpperCase();}
function rgb(s){const h=s.slice(1);return {r:parseInt(h.slice(0,2),16)/255,g:parseInt(h.slice(2,4),16)/255,b:parseInt(h.slice(4,6),16)/255};}
function mapPaint(p,col){return p.map(q=>q.type==='SOLID'?{...q,color:rgb(col(hx(q.color)))}:q);}
function rootOf(n){let p=n;while(p.parent&&p.parent.type!=='PAGE')p=p.parent;return p;}
function insidePrimary(n){let p=n.parent;while(p&&p.type!=='PAGE'){
  if(p.type==='FRAME'&&p.name.startsWith('go:')&&p.width>=170&&p.height<=58&&p.fills?.[0]?.type==='SOLID'&&hx(p.fills[0].color)==='#F2F2F2')return true;
  p=p.parent;
}return false;}
function insidePaleChip(n){let p=n.parent;while(p&&p.type!=='PAGE'){
  if(p.type==='FRAME'&&p.height<=36&&p.fills?.[0]?.type==='SOLID'&&['#E5E5E5','#F2F2F2'].includes(hx(p.fills[0].color)))return true;
  p=p.parent;
}return false;}
function frameColor(s,n){
  if(n.name==='gesture handle')return '#F4F4F4';
  if(n.name==='document symbol'||n.name==='ledger bar')return '#ECECEC';
  if(s==='#F4F4F4')return '#080808';
  if(s==='#FFFFFF')return '#242424';
  if(s==='#171717'){
    if(n.name.startsWith('go:')&&n.width>=170&&n.height<=58)return '#F2F2F2';
    if(n.name.startsWith('chip /')&&n.height<=36)return '#E5E5E5';
    return '#242424';
  }
  if(s==='#272727')return '#303030';
  if(s==='#EBEBEB')return '#333333';
  if(s==='#F7F7F7')return '#191919';
  if(s==='#D5D5D5')return '#4A4A4A';
  if(s==='#D1D1D1')return '#3B3B3B';
  if(s==='#A8A8A8')return '#505050';
  return s;
}
function textColor(s,n){
  if(insidePaleChip(n)&&['#FFFFFF','#171717'].includes(s))return '#111111';
  if(insidePrimary(n)&&['#FFFFFF','#171717'].includes(s))return '#111111';
  if(s==='#171717')return '#F4F4F4';
  if(s==='#595959')return '#BDBDBD';
  if(s==='#777777')return '#A9A9A9';
  if(s==='#FFFFFF')return '#F4F4F4';
  return s;
}
const made=[], keys=[];
for(const source of lights.slice(start,end)){
  const key=source.name.split(' • ').slice(1).join(' • ');
  const clone=source.clone();page.appendChild(clone);
  clone.name=`GoServe 2.1 • dark/${key}`;clone.x=source.x+2850;clone.y=source.y;
  const nodes=[clone,...clone.findAll(()=>true)];
  for(const n of nodes){
    if(n.name.startsWith('go:'))n.name=n.name.replace(/^go:([^|]+)\s*\|/,(_,raw)=>{
      const target=raw.trim();return `go:${target.startsWith('dark/')?target.slice(5):'dark/'+target} |`;
    });
    if(n.type==='TEXT'){
      if(n.characters==='Off · light appearance')n.characters='On · dark appearance';
      if(Array.isArray(n.fills))n.fills=mapPaint(n.fills,s=>textColor(s,n));
    }else if(n.type==='FRAME'||n.type==='RECTANGLE'){
      if(Array.isArray(n.fills))n.fills=mapPaint(n.fills,s=>frameColor(s,n));
      if(Array.isArray(n.strokes))n.strokes=mapPaint(n.strokes,s=>s==='#D5D5D5'?'#4A4A4A':s);
      if(n.name==='Appearance switch'){
        n.fills=[{type:'SOLID',color:rgb('#E5E5E5')}];
        const thumb=n.children.find(c=>c.name==='thumb');if(thumb){thumb.x=21;thumb.fills=[{type:'SOLID',color:rgb('#171717')}];}
      }
    }
  }
  made.push(...nodes.map(n=>n.id));keys.push(key);
}
return {created:made,keys,lightCount:lights.length};
