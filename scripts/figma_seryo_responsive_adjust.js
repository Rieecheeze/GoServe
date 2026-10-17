// Inject PROOFS after cloning Seryo phone examples.
await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('14:2'));
const mutated=new Set(),reports=[];
for(const p of PROOFS){const root=await figma.getNodeByIdAsync(p.id),nodes=[root,...root.findAll(()=>true)],before=new Map(nodes.map(n=>[n.id,[n.x,n.y,n.width,n.height]])),viewport=nodes.find(n=>n.type==='FRAME'&&n.name==='Seryo / Scrollable chat viewport'),welcome=nodes.find(n=>n.type==='FRAME'&&n.name==='Seryo / Centered welcome content');
if(welcome&&viewport){welcome.minHeight=viewport.height;mutated.add(welcome.id);}
const menu=root.children.find(n=>n.type==='INSTANCE'&&/^GoServe tools \/ .+ menu$/.test(n.name)),composer=root.children.find(n=>n.type==='FRAME'&&n.name==='Seryo / Pinned composer host'),header=root.children.find(n=>n.type==='FRAME'&&n.name==='Seryo / Header');
if(menu){const mh=menu.findOne(n=>n.name==='GoServe tools / Menu header'),options=menu.findOne(n=>n.name==='GoServe tools / Menu options'),available=composer.y-8-(header.y+header.height+8),needed=menu.paddingTop+menu.paddingBottom+menu.itemSpacing+mh.height+options.height,height=Math.min(344,available,Math.max(200,needed));menu.resize(root.width-40,height);menu.x=20;menu.y=composer.y-8-height;mutated.add(menu.id);reports.push({id:root.id,menuHeight:height,needed,available});}
for(const n of nodes){const old=before.get(n.id),now=[n.x,n.y,n.width,n.height];if(old&&old.some((v,i)=>Math.abs(v-now[i])>.01))mutated.add(n.id);}}
return{createdNodeIds:[],mutatedNodeIds:[...mutated],reports};
