// CASES is supplied by the caller; proofs remain editable for UI review.
await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('14:2'));
const createdNodeIds=[],mutatedNodeIds=[],proofs=[],page=figma.currentPage;
function visible(n,root){let p=n;while(p&&p!==root){if(!p.visible)return false;p=p.parent;}return true;}
function chrome(n,root){let p=n;while(p&&p!==root){if(/^Android (status|gesture) inset$/.test(p.name))return true;p=p.parent;}return false;}
for(const c of CASES){
  const source=await figma.getNodeByIdAsync(c.id),fonts=new Map();for(const t of source.findAllWithCriteria({types:['TEXT']}))for(const s of t.getStyledTextSegments(['fontName']))fonts.set(JSON.stringify(s.fontName),s.fontName);await Promise.all([...fonts.values()].map(f=>figma.loadFontAsync(f)));
  const name='GoServe / Responsive proof / '+c.id+' / '+c.width+'x'+c.height+' / '+c.scale+'x';let n=page.findOne(n=>n.type==='FRAME'&&n.name===name);
  if(!n){n=source.clone();n.name=name;page.appendChild(n);createdNodeIds.push(n.id,...n.findAll(()=>true).map(x=>x.id));n.x=40+c.index*360;n.y=42000;
    n.resize(c.width,c.height);n.primaryAxisSizingMode=n.counterAxisSizingMode='FIXED';
    if(c.scale!==1)for(const t of n.findAllWithCriteria({types:['TEXT']})){if(!visible(t,n)||chrome(t,n)||t.getStyledTextSegments(['fontName']).some(s=>/Material (Symbols|Icons)/.test(s.fontName.family)))continue;if(typeof t.fontSize==='number')t.fontSize=t.fontSize*c.scale;if(t.lineHeight!==figma.mixed&&t.lineHeight.unit==='PIXELS')t.lineHeight={unit:'PIXELS',value:t.lineHeight.value*c.scale};if(t.textAutoResize!=='NONE')t.textAutoResize='HEIGHT';mutatedNodeIds.push(t.id);}
  }
  const authViewport=n.findOne(x=>x.type==='FRAME'&&x.name==='Auth scroll viewport'),authBody=n.findOne(x=>x.type==='FRAME'&&x.name==='Auth centered content');if(authViewport&&authBody){authBody.minHeight=authViewport.height;mutatedNodeIds.push(authBody.id);}
  proofs.push({id:n.id,source:c.id,width:n.width,height:n.height,scale:c.scale,name:n.name});
}
return{createdNodeIds,mutatedNodeIds,proofs};
