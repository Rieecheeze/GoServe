// Inject SOURCE_IDS and FRAME_SIZES from the saved original canvas inventory.

await figma.setCurrentPageAsync(await figma.getNodeByIdAsync('14:2'));
const page=figma.currentPage,createdNodeIds=[],mutatedNodeIds=[];
const flowNames={'19:2':'Student sign in · Light','56:38':'Student sign up · Light','36:2':'Student sign in · Dark','56:66':'Student sign up · Dark','24:131':'Office staff · Light','36:899':'Office staff · Dark','26:415':'Cashiering · Light','36:1558':'Cashiering · Dark'};
page.flowStartingPoints=page.flowStartingPoints.map(point=>({...point,name:flowNames[point.nodeId]||point.name}));mutatedNodeIds.push(page.id);
const sections=page.findAllWithCriteria({types:['SECTION']}),sourceIds=SOURCE_IDS,frames=FRAME_SIZES,findings=[];
let preserved=0,links=0;const targets=new Set();
const visible=n=>{let c=n;while(c&&c.type!=='PAGE'){if(!c.visible)return false;c=c.parent;}return true;};
for(const id of sourceIds){const n=await figma.getNodeByIdAsync(id);if(!n)findings.push({kind:'MissingSource',id});else if(n.parent.type!=='SECTION')findings.push({kind:'UngroupedSource',id,parent:n.parent.id});}
for(const [id,w,h]of frames){const n=await figma.getNodeByIdAsync(id);if(!n)continue;if(Math.abs(n.width-w)>.01||Math.abs(n.height-h)>.01)findings.push({kind:'ScreenSizeChanged',id});else preserved++;
 for(const node of [n,...n.findAll(()=>true)])if(visible(node)&&'reactions'in node)for(const reaction of node.reactions)for(const action of reaction.actions||[])if(action.destinationId){links++;targets.add(action.destinationId);}
}
for(const s of sections){if(!s.visible)continue;for(const child of s.children){if(!child.visible)continue;if(child.x<-.5||child.y<-.5||child.x+child.width>s.width+.5||child.y+child.height>s.height+.5)findings.push({kind:'SectionChildOverflow',id:child.id,parent:s.id});}}
const top=page.children.filter(n=>n.type==='SECTION'&&n.visible);
for(let i=0;i<top.length;i++)for(let j=i+1;j<top.length;j++){const a=top[i],b=top[j];if(a.x<b.x+b.width&&a.x+a.width>b.x&&a.y<b.y+b.height&&a.y+a.height>b.y)findings.push({kind:'TopSectionOverlap',a:a.id,b:b.id});}
for(const id of targets){const n=await figma.getNodeByIdAsync(id);if(!n||!visible(n))findings.push({kind:'BadPrototypeTarget',id,missing:!n});}
return{createdNodeIds,mutatedNodeIds,sourceCount:sourceIds.length,sectionCount:sections.length,topLevelSectionCount:page.children.length,preservedFrameSizes:preserved,prototypeLinks:links,uniqueDestinations:targets.size,findings,flowStarts:page.flowStartingPoints};

