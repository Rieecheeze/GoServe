// Give each listed office its own detail and connect fee-based Registrar / OSA requests to Cashiering.
const page=await figma.getNodeByIdAsync('14:2');
await figma.setCurrentPageAsync(page);
for(const font of [
  {family:'Archivo',style:'Bold'}, {family:'Roboto',style:'Regular'},
  {family:'Roboto',style:'Medium'}, {family:'Material Symbols Rounded',style:'Regular'},
]) await figma.loadFontAsync(font);
const created=[];
function node(key){const n=page.children.find(x=>x.name===`GoServe 2.1 • ${key}`);if(!n)throw new Error(`Missing ${key}`);return n;}
function child(f,name){const n=f.children.find(x=>x.name===name);if(!n)throw new Error(`${f.name}: missing ${name}`);return n;}
function t(n,name,value){const x=n.findAll(y=>y.type==='TEXT'&&y.name===name)[0];if(!x)throw new Error(`${n.name}: missing text ${name}`);x.characters=value;}
function title(f,value){t(child(f,'Material top app bar'),'screen title',value);}
function role(prefix){return prefix?'dark/':'';}
function editDetail(f,prefix,def){
  const bar=child(f,'Material top app bar');
  title(f,def.title);
  const back=bar.children.find(n=>n.name.includes(' | Back'));back.name=`go:${prefix}student/offices | Back`;
  const body=child(f,'Scrollable Android content');
  const blocks=body.children;
  const first=blocks[0],section=blocks[1],last=blocks[5];
  first.name=`notice / ${def.noticeTitle}`;t(first,'title',def.noticeTitle);t(first,'body',def.noticeBody);
  section.name=`section / ${def.section}`;t(section,'heading',def.section);t(section,'description',def.sectionBody);
  for(let i=0;i<3;i++){
    const b=blocks[i+2],r=def.rows[i];
    b.name=`go:${prefix}${r.target} | ${r.title}`;
    t(b,'title',r.title);t(b,'detail',r.detail);
  }
  last.name=`notice / ${def.endTitle}`;t(last,'title',def.endTitle);t(last,'body',def.endBody);
  const footer=f.children.find(n=>n.name.startsWith('go:')&&n.y===754);
  footer.name=`go:${prefix}${def.footerTarget} | ${def.footer}`;
  t(footer,'label',def.footer);
}
const definitions=[
  ['student/office-cashiering',61,{
    title:'Cashiering',noticeTitle:'Cashiering • Casal and Arlegui',
    noticeBody:'Cash payments are linked to eligible Registrar and OSA requests. Check the reference and configured amount.',
    section:'Linked payable requests',sectionBody:'Only fee-based requests show checkout and a payment reference.',
    rows:[
      {title:'Registrar request payment',detail:'Open the configured amount and payment choice.',target:'student/payment'},
      {title:'OSA request payment',detail:'Open the OSA payment choice for an eligible request.',target:'student/payment-osa'},
      {title:'My requests',detail:'Find the reference and current payment state.',target:'student/requests'},
    ],endTitle:'Cash confirmation',endBody:'Only an authorized teller confirms a cash payment after checking the reference and amount.',
    footer:'View my requests',footerTarget:'student/requests',
  }],
  ['student/office-osa',62,{
    title:'OSA',noticeTitle:'OSA • Casal and Arlegui',
    noticeBody:'Check the latest office status and service details before sending a request.',
    section:'OSA requests',sectionBody:'Some configured OSA requests may have a fee; Cashiering handles cash payment.',
    rows:[
      {title:'Start an OSA request',detail:'Send notes and any required private evidence.',target:'student/new-request-osa'},
      {title:'Ask OSA',detail:'Start an asynchronous inquiry thread.',target:'student/new-thread'},
      {title:'Cashiering',detail:'Pay an eligible OSA request with its reference.',target:'student/office-cashiering'},
    ],endTitle:'Confirm service details',endBody:'The selected service determines requirements, fees, and next steps.',
    footer:'Start an OSA request',footerTarget:'student/new-request-osa',
  }],
  ['student/office-accounting',63,{
    title:'Student Accounting',noticeTitle:'Student Accounting • Casal and Arlegui',
    noticeBody:'Check current availability and announcements before contacting the office.',
    section:'Accounting help',sectionBody:'Use your request reference when asking about a fee-linked transaction.',
    rows:[
      {title:'Ask Accounting',detail:'Start an asynchronous office inquiry.',target:'student/new-thread'},
      {title:'My requests',detail:'Check request and payment states.',target:'student/requests'},
      {title:'Cashiering',detail:'Find cash payment instructions for an eligible request.',target:'student/office-cashiering'},
    ],endTitle:'Configured amounts',endBody:'Amounts are loaded from the selected service; this screen does not set fees.',
    footer:'Ask Accounting',footerTarget:'student/new-thread',
  }],
  ['student/office-guidance',64,{
    title:'Guidance and Counseling',noticeTitle:'Guidance • Casal and Arlegui',
    noticeBody:'Check available consultation slots and the office status before booking.',
    section:'Guidance consultations',sectionBody:'Availability comes from the assigned Guidance schedule.',
    rows:[
      {title:'Book guidance',detail:'Choose an available appointment slot.',target:'student/appointment'},
      {title:'Ask Guidance',detail:'Send an asynchronous office inquiry.',target:'student/new-thread'},
      {title:'Office directory',detail:'Check other campus offices.',target:'student/offices'},
    ],endTitle:'Appointment updates',endBody:'Changes appear in GoServe after the authorized office updates the schedule.',
    footer:'Book consultation',footerTarget:'student/appointment',
  }],
  ['student/office-ccs',65,{
    title:'CCS secretary',noticeTitle:'CCS secretary • Casal and Arlegui',
    noticeBody:'Check current office status before requesting faculty coordination.',
    section:'Faculty coordination',sectionBody:'Faculty consultation slots are managed by authorized CCS roles.',
    rows:[
      {title:'Faculty consultations',detail:'Find an available faculty slot.',target:'student/consult'},
      {title:'Ask CCS secretary',detail:'Start an asynchronous inquiry.',target:'student/new-thread'},
      {title:'Office directory',detail:'Check other campus offices.',target:'student/offices'},
    ],endTitle:'Schedule updates',endBody:'Availability is based on the assigned schedule in GoServe.',
    footer:'View consultations',footerTarget:'student/consult',
  }],
];

for(const prefix of ['', 'dark/']){
  // Keep the Registrar page distinct and add the Cashiering payment route.
  const registrar=node(`${prefix}student/office-detail`);
  const b=child(registrar,'Scrollable Android content');
  const fee=b.children[0];t(fee,'body','Some configured document requests have a fee. Cash payment uses the request reference at Cashiering.');
  const cash=b.children[4];cash.name=`go:${prefix}student/office-cashiering | Cashiering`;
  t(cash,'title','Cashiering');t(cash,'detail','Pay an eligible Registrar request with its reference.');
  const back=child(registrar,'Material top app bar').children.find(n=>n.name.includes(' | Back'));
  back.name=`go:${prefix}student/offices | Back`;
  for(const [key,index,def] of definitions){
    const f=registrar.clone();page.appendChild(f);created.push(f.id,...f.findAll(()=>true).map(n=>n.id));
    f.name=`GoServe 2.1 • ${prefix}${key}`;
    f.x=40+(index%6)*450+(prefix?2850:0);f.y=40+Math.floor(index/6)*925;
    editDetail(f,prefix,def);
  }
  const request=node(`${prefix}student/new-request`).clone();page.appendChild(request);
  created.push(request.id,...request.findAll(()=>true).map(n=>n.id));
  request.name=`GoServe 2.1 • ${prefix}student/new-request-osa`;
  request.x=40+(66%6)*450+(prefix?2850:0);request.y=40+Math.floor(66/6)*925;
  title(request,'New OSA request');
  child(request,'Material top app bar').children.find(n=>n.name.includes(' | Back')).name=`go:${prefix}student/office-osa | Back`;
  const requestBody=child(request,'Scrollable Android content');
  const intro=requestBody.children.find(n=>n.name.startsWith('section /'));
  t(intro,'description','OSA · configured demo service');
  const reqFooter=request.children.find(n=>n.name.includes('Continue to payment choice'));
  reqFooter.name=`go:${prefix}student/payment-osa | Continue to payment choice`;
  const payment=node(`${prefix}student/payment`).clone();page.appendChild(payment);
  created.push(payment.id,...payment.findAll(()=>true).map(n=>n.id));
  payment.name=`GoServe 2.1 • ${prefix}student/payment-osa`;
  payment.x=40+(67%6)*450+(prefix?2850:0);payment.y=40+Math.floor(67/6)*925;
  child(payment,'Material top app bar').children.find(n=>n.name.includes(' | Back')).name=`go:${prefix}student/new-request-osa | Back`;
  const payIntro=child(payment,'Scrollable Android content').children.find(n=>n.name.startsWith('section /'));
  t(payIntro,'description','OSA request · configured demo fee');
}

// Directory cards route to their own details; add OSA alongside Cashiering.
const mapping={Registrar:'student/office-detail','Student Accounting':'student/office-accounting',Cashiering:'student/office-cashiering',OSA:'student/office-osa','Guidance and Counseling':'student/office-guidance','CCS secretary':'student/office-ccs'};
for(const prefix of ['', 'dark/']){
  const base=node(`${prefix}student/offices`);
  const body=child(base,'Scrollable Android content');
  const cashier=body.children.find(n=>n.name.endsWith(' | Cashiering'));
  const osa=cashier.clone();body.appendChild(osa);created.push(osa.id,...osa.findAll(()=>true).map(n=>n.id));
  osa.name=`go:${prefix}student/office-osa | OSA`;
  t(osa,'office name','OSA');t(osa,'service','Requests and office inquiries');
  const osaStatus=osa.children.find(n=>n.name.startsWith('status /'));
  osaStatus.name='status / Check status';t(osaStatus,'label','Check status');
  for(const row of body.children.filter(n=>n.name.includes(' | ')&&n.name.startsWith('go:'))){
    const name=row.name.split(' | ')[1];if(mapping[name])row.name=`go:${prefix}${mapping[name]} | ${name}`;
  }
  const order=['Registrar','Student Accounting','Cashiering','OSA','Guidance and Counseling','CCS secretary'];
  for(let i=0;i<order.length;i++){
    const row=body.children.find(n=>n.name.endsWith(` | ${order[i]}`));row.y=206+i*112;
  }
  const filtered=node(`${prefix}student/offices-payments`);
  const paymentBody=child(filtered,'Scrollable Android content');
  for(const name of ['Registrar','OSA']){
    const source=body.children.find(n=>n.name.endsWith(` | ${name}`));
    const row=source.clone();paymentBody.appendChild(row);
    created.push(row.id,...row.findAll(()=>true).map(n=>n.id));
  }
  for(const row of paymentBody.children.filter(n=>n.name.startsWith('go:')&&n.name.includes(' | '))){
    const name=row.name.split(' | ')[1];if(mapping[name])row.name=`go:${prefix}${mapping[name]} | ${name}`;
  }
  for(const [i,name] of ['Registrar','OSA','Cashiering','Student Accounting'].entries()){
    const row=paymentBody.children.find(n=>n.name.endsWith(` | ${name}`));row.y=206+i*112;
  }
  const docs=node(`${prefix}student/offices-documents`);
  const docRow=child(docs,'Scrollable Android content').children.find(n=>n.name.endsWith(' | Registrar'));
  docRow.name=`go:${prefix}student/office-detail | Registrar`;
  const home=node(`${prefix}student/home`);
  const accounting=home.findAll(n=>n.name.endsWith(' | Student Accounting'))[0];
  accounting.name=`go:${prefix}student/office-accounting | Student Accounting`;
  const teller=node(`${prefix}teller/cash`);
  const tellerBody=child(teller,'Scrollable Android content');
  const sub=tellerBody.findAll(n=>n.type==='TEXT'&&n.characters==='Teller-only lookup across configured demo offices.')[0];
  if(sub)sub.characters='Search assigned payment references, including Registrar and OSA requests.';
}
return {created,officeDetails:10,osaRequestAndPayment:4,osaDirectoryRows:2};
