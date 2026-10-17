// Wire one batch of Android v2.1 screen actions. Replace range placeholders.
const start=__START__,end=__END__;
const page=await figma.getNodeByIdAsync('14:2');
await figma.setCurrentPageAsync(page);
const frames=page.children.filter(n=>n.type==='FRAME'&&n.name.startsWith('GoServe 2.1 • '))
  .sort((a,b)=>a.y-b.y||a.x-b.x);
const byKey=new Map(frames.map(n=>[n.name.split(' • ').slice(1).join(' • '),n]));
const linked=[],skipped=[];
for(const frame of frames.slice(start,end)){
  const sourceKey=frame.name.split(' • ').slice(1).join(' • ');
  for(const node of frame.findAll(n=>n.name.startsWith('go:'))){
    const match=node.name.match(/^go:([^|]+)\s*\|/);
    if(!match)continue;
    const targetKey=match[1].trim();
    if(targetKey==='none'||targetKey===sourceKey){
      if(targetKey===sourceKey&&node.reactions?.length)await node.setReactionsAsync([]);
      skipped.push(node.id);continue;
    }
    const dest=byKey.get(targetKey);
    if(!dest)throw new Error(`Missing target ${targetKey} from ${sourceKey}`);
    await node.setReactionsAsync([{trigger:{type:'ON_CLICK'},actions:[{
      type:'NODE',destinationId:dest.id,navigation:'NAVIGATE',transition:null,preserveScrollPosition:false,
    }]}]);
    linked.push(node.id);
  }
}
return {start,end,linked,skipped,frameCount:frames.length};
