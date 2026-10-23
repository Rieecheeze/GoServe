package ph.edu.tip.goserve.data;

import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import ph.edu.tip.goserve.domain.OfficeStatus;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.FeeDeclaration;

public final class OfficeManagementRepository {
    interface Transport { String accountId();void requireCurrent();JSONArray get(String path)throws Exception;JSONObject rpc(String name,JSONObject args)throws Exception; }
    public enum Kind { OFFICE,ANNOUNCEMENT,SERVICE }
    public static final class WorkflowException extends Exception {public final String code;public WorkflowException(String code){super(code);this.code=code;}}
    public static final class Info {
        public final String id,name,note,changedAt,changedBy;public final long version;public final OfficeStatus status;
        public Info(String id,String name,String note,OfficeStatus status,long version,String at,String by){this.id=id;this.name=name;this.note=note;this.status=status;this.version=version;changedAt=at;changedBy=by;}
    }
    public static final class Announcement {
        public final String id,officeId,body,actorId,createdAt;public final boolean active;public final long version;
        public Announcement(String id,String office,String body,String actor,String time,boolean active,long version){this.id=id;officeId=office;this.body=body;actorId=actor;createdAt=time;this.active=active;this.version=version;}
    }
    public static final class Catalog {
        public final RequestData.Service service;public final boolean demo;private final String requirements;
        public Catalog(RequestData.Service service,boolean demo,String requirements){this.service=service;this.demo=demo;this.requirements=requirements;}
        public JSONArray requirements()throws Exception{return new JSONArray(requirements);}
    }
    public static final class Page<T> {public final List<T> items;public final String nextTime,nextId;public Page(List<T> items,String time,String id){this.items=List.copyOf(items);nextTime=time;nextId=id;}}
    public static final class Command {
        public final Kind kind;public final String accountId,officeId,id,key;public final long version;private final String body;
        public Command(Kind kind,String account,String office,String id,long version,String key,String body){this.kind=kind;accountId=account;officeId=office;this.id=id;this.version=version;this.key=key;this.body=body;}
        public JSONObject arguments()throws Exception{return new JSONObject(body);}
    }
    public static final class Result {
        public final Info office;public final Announcement announcement;public final Catalog service;
        public Result(Info office,Announcement announcement,Catalog service){this.office=office;this.announcement=announcement;this.service=service;}
    }
    private static final String OFFICE_COLUMNS="id,name,status,note,version,status_changed_at,status_changed_by";
    private static final String ANNOUNCEMENT_COLUMNS="id,office_id,body,active,version,actor_id,created_at";
    private static final String SERVICE_COLUMNS="id,office_id,name,delivery_modes,requirements,fee_state,amount,currency,active,version,is_demo";
    private final Transport transport;private final String assignedOffice,actor;
    public OfficeManagementRepository(AccountGateway gateway,String office){this(new Transport(){public String accountId(){return gateway.lease().accountId;}public void requireCurrent(){gateway.requireCurrent();}public JSONArray get(String path)throws Exception{return gateway.get(path);}public JSONObject rpc(String name,JSONObject args)throws Exception{return gateway.rpc(name,args);}},office);}
    OfficeManagementRepository(Transport transport,String office){this.transport=transport;assignedOffice=office;actor=transport.accountId();}
    public void requireCurrent(){transport.requireCurrent();if(!actor.equals(transport.accountId()))throw new IllegalStateException("Account changed.");}
    public Info office()throws Exception{
        JSONArray rows=read(table("offices",OFFICE_COLUMNS,1).appendQueryParameter("id","eq."+assignedOffice));
        if(rows.length()!=1)throw fail("NOT_FOUND");return info(rows.getJSONObject(0));
    }
    public Page<Announcement> announcements(String time,String id,boolean activeOnly)throws Exception{
        if((time==null)!=(id==null))throw fail("VALIDATION_FAILED");
        Uri.Builder query=table("announcements",ANNOUNCEMENT_COLUMNS,50).appendQueryParameter("office_id","eq."+assignedOffice).appendQueryParameter("order","created_at.desc,id.desc");
        if(activeOnly)query.appendQueryParameter("active","eq.true");
        if(id!=null){uuid(id);timestamp(time);query.appendQueryParameter("or","(created_at.lt."+time+",and(created_at.eq."+time+",id.lt."+id+"))");}
        JSONArray rows=read(query);List<Announcement> values=new ArrayList<>();String priorTime=time,priorId=id;
        for(int n=0;n<rows.length();n++){
            Announcement item=announcement(rows.getJSONObject(n));
            if(activeOnly && !item.active)throw fail("FORBIDDEN");
            if(priorTime!=null){int order=Instant.parse(item.createdAt).compareTo(Instant.parse(priorTime));if(order>0 || order==0 && item.id.compareTo(priorId)>=0)throw fail("STALE_RESPONSE");}
            values.add(item);priorTime=item.createdAt;priorId=item.id;
        }
        return new Page<>(values,rows.length()==50?priorTime:null,rows.length()==50?priorId:null);
    }
    public Page<Catalog> services(String id)throws Exception{
        Uri.Builder query=table("services",SERVICE_COLUMNS,50).appendQueryParameter("office_id","eq."+assignedOffice).appendQueryParameter("order","id.asc");
        if(id!=null){uuid(id);query.appendQueryParameter("id","gt."+id);}
        JSONArray rows=read(query);List<Catalog> values=new ArrayList<>();String prior=id;
        for(int n=0;n<rows.length();n++){Catalog value=catalog(rows.getJSONObject(n));if(prior!=null && value.service.id.compareTo(prior)<=0)throw fail("STALE_RESPONSE");values.add(value);prior=value.service.id;}
        return new Page<>(values,null,rows.length()==50?prior:null);
    }
    public Announcement announcement(String id)throws Exception{uuid(id);return announcement(one("announcements",ANNOUNCEMENT_COLUMNS,id));}
    public Catalog service(String id)throws Exception{uuid(id);return catalog(one("services",SERVICE_COLUMNS,id));}
    public Command officeDraft(Info original,OfficeStatus status,String note,Command previous)throws Exception{
        requireCurrent();if(original==null || !assignedOffice.equals(original.id) || original.version<1 || status==null || note==null)throw fail("VALIDATION_FAILED");
        return command(Kind.OFFICE,assignedOffice,original.version,new JSONObject().put("p_expected_version",original.version).put("p_status",status.wireValue).put("p_note",note),previous);
    }
    public Command announcementDraft(Announcement original,String body,boolean active,Command previous)throws Exception{
        requireCurrent();nonblank(body);if(original!=null && !assignedOffice.equals(original.officeId))throw fail("FORBIDDEN");
        return command(Kind.ANNOUNCEMENT,original==null?null:original.id,original==null?0:original.version,new JSONObject().put("p_id",original==null?JSONObject.NULL:original.id).put("p_expected_version",original==null?0:original.version).put("p_body",body).put("p_active",active),previous);
    }
    public Command serviceDraft(Catalog original,String name,List<String> modes,List<String> requirementIds,FeeDeclaration fee,String amount,String currency,boolean active,Command previous)throws Exception{
        requireCurrent();nonblank(name);if(original!=null && !assignedOffice.equals(original.service.officeId))throw fail("FORBIDDEN");
        if(fee==null || modes==null || modes.isEmpty() || requirementIds==null)throw fail("VALIDATION_FAILED");
        LinkedHashSet<String> deliveries=new LinkedHashSet<>(modes);if(!Set.of("physical","digital").containsAll(deliveries))throw fail("VALIDATION_FAILED");
        JSONArray requirements=new JSONArray(),existing=original==null?new JSONArray():original.requirements();Set<String> seen=new HashSet<>();
        for(String requirement:requirementIds){nonblank(requirement);if(!seen.add(requirement))continue;JSONObject retained=null;for(int i=0;i<existing.length();i++)if(requirement.equals(existing.getJSONObject(i).getString("id")))retained=existing.getJSONObject(i);requirements.put(retained==null?new JSONObject().put("id",requirement):new JSONObject(retained.toString()));}
        Object declared=JSONObject.NULL,unit=JSONObject.NULL;
        if(fee==FeeDeclaration.FREE)declared="0";
        else if(fee==FeeDeclaration.REQUIRED){nonblank(currency);try{
            if(amount==null || amount.length()>20_000)throw fail("VALIDATION_FAILED");
            BigDecimal number=new BigDecimal(amount);long precision=number.precision(),scale=number.scale();
            long plainLength=scale<=0?precision-scale:scale>=precision?scale+2:precision+1;
            if(number.signum()<=0 || plainLength>20_000)throw fail("VALIDATION_FAILED");
            declared=number.toPlainString();unit=currency;
        }catch(NumberFormatException invalid){throw fail("VALIDATION_FAILED");}}
        JSONObject value=new JSONObject().put("name",name).put("delivery_modes",new JSONArray(deliveries)).put("requirements",requirements).put("fee_state",fee==FeeDeclaration.PENDING_REVIEW?"pending":fee.wireValue).put("amount",declared).put("currency",unit).put("active",active);
        return command(Kind.SERVICE,original==null?null:original.service.id,original==null?0:original.service.version,new JSONObject().put("p_id",original==null?JSONObject.NULL:original.service.id).put("p_expected_version",original==null?0:original.service.version).put("p_value",value),previous);
    }
    private Command command(Kind kind,String id,long version,JSONObject args,Command previous)throws Exception{
        requireCurrent();if(kind!=Kind.OFFICE && id!=null)uuid(id);if(version<0 || id!=null && version<1 || id==null && version!=0)throw fail("VALIDATION_FAILED");
        if(previous!=null && previous.kind==kind && actor.equals(previous.accountId) && assignedOffice.equals(previous.officeId) && Objects.equals(id,previous.id) && version==previous.version){JSONObject old=previous.arguments();old.remove("p_idempotency_key");if(equal(old,args))return previous;}
        String key=UUID.randomUUID().toString();args.put("p_idempotency_key",key);return new Command(kind,actor,assignedOffice,id,version,key,args.toString());
    }
    public Result save(Command command)throws Exception{
        requireCurrent();validate(command);JSONObject args=command.arguments();String operation=command.kind==Kind.OFFICE?"update_office":command.kind==Kind.ANNOUNCEMENT?"save_announcement":"save_service";
        JSONObject receipt=transport.rpc(operation,args);requireCurrent();long version=command.version+1;
        if(command.kind==Kind.OFFICE){
            Info saved=info(receipt);if(saved.version!=version || !actor.equals(saved.changedBy) || saved.changedAt==null || !saved.status.wireValue.equals(args.getString("p_status")) || !saved.note.equals(args.getString("p_note")))throw fail("VALIDATION_FAILED");
            Info current=office();if(current.version<saved.version)throw fail("STALE_RESPONSE");return new Result(current,null,null);
        }
        if(command.kind==Kind.ANNOUNCEMENT){
            Announcement saved=announcement(receipt);if(command.id!=null && !command.id.equals(saved.id) || saved.version!=version || !actor.equals(saved.actorId) || !saved.body.equals(args.getString("p_body")) || saved.active!=args.getBoolean("p_active"))throw fail("VALIDATION_FAILED");
            Announcement current=announcement(one("announcements",ANNOUNCEMENT_COLUMNS,saved.id));if(current.version<saved.version)throw fail("STALE_RESPONSE");return new Result(null,current,null);
        }
        Catalog saved=catalog(receipt);JSONObject value=args.getJSONObject("p_value");
        if(command.id!=null && !command.id.equals(saved.service.id) || saved.service.version!=version || command.id==null && !saved.demo)throw fail("VALIDATION_FAILED");
        for(String key:keys(value)){
            if("amount".equals(key) && !value.isNull(key)){if(saved.service.amount==null || saved.service.amount.compareTo(new BigDecimal(value.getString(key)))!=0)throw fail("VALIDATION_FAILED");}
            else if(!equal(receipt.get(key),value.get(key)))throw fail("VALIDATION_FAILED");
        }
        Catalog current=catalog(one("services",SERVICE_COLUMNS,saved.service.id));if(current.service.version<saved.service.version)throw fail("STALE_RESPONSE");return new Result(null,null,current);
    }
    public Command restore(Kind kind,String id,long version,String key,String body)throws Exception{
        Command candidate=new Command(kind,actor,assignedOffice,id,version,key,body);validate(candidate);return candidate;
    }
    private void validate(Command c)throws Exception{
        if(c==null || c.kind==null || !actor.equals(c.accountId) || !assignedOffice.equals(c.officeId))throw fail("FORBIDDEN");uuid(c.key);
        JSONObject args=c.arguments();if(!c.key.equals(text(args,"p_idempotency_key")) || integer(args,"p_expected_version",0)!=c.version)throw fail("VALIDATION_FAILED");
        if(c.kind==Kind.OFFICE){
            if(!assignedOffice.equals(c.id) || c.version<1 || !keys(args).equals(Set.of("p_expected_version","p_status","p_note","p_idempotency_key")))throw fail("VALIDATION_FAILED");
            try{OfficeStatus.fromWire(text(args,"p_status"));}catch(IllegalArgumentException invalid){throw fail("VALIDATION_FAILED");}text(args,"p_note");
        }else{
            if(c.id==null?c.version!=0:c.version<1)throw fail("VALIDATION_FAILED");if(c.id!=null)uuid(c.id);
            if(!Objects.equals(c.id,args.isNull("p_id")?null:text(args,"p_id")))throw fail("VALIDATION_FAILED");
            if(c.kind==Kind.ANNOUNCEMENT){if(!keys(args).equals(Set.of("p_id","p_expected_version","p_body","p_active","p_idempotency_key")))throw fail("VALIDATION_FAILED");nonblank(text(args,"p_body"));bool(args,"p_active");}
            else{
                if(!keys(args).equals(Set.of("p_id","p_expected_version","p_value","p_idempotency_key")))throw fail("VALIDATION_FAILED");JSONObject v=args.getJSONObject("p_value");
                if(!keys(v).equals(Set.of("name","delivery_modes","requirements","fee_state","amount","currency","active")))throw fail("VALIDATION_FAILED");
                JSONObject record=new JSONObject(v.toString()).put("id",c.id==null?"00000000-0000-4000-8000-000000000001":c.id).put("office_id",assignedOffice).put("version",1).put("is_demo",true);catalog(record);
            }
        }
    }
    private JSONObject one(String table,String columns,String id)throws Exception{
        JSONArray rows=read(table(table,columns,1).appendQueryParameter("id","eq."+id).appendQueryParameter("office_id","eq."+assignedOffice));
        if(rows.length()!=1 || !id.equals(text(rows.getJSONObject(0),"id")))throw fail("NOT_FOUND");return rows.getJSONObject(0);
    }
    private JSONArray read(Uri.Builder query)throws Exception{
        requireCurrent();if(assignedOffice==null || !assignedOffice.matches("(?:OFF|DEP)-[A-Z0-9-]+"))throw fail("FORBIDDEN");
        JSONArray rows=transport.get(query.build().toString());requireCurrent();int limit=Integer.parseInt(Uri.parse(query.toString()).getQueryParameter("limit"));if(rows==null || rows.length()>limit)throw fail("VALIDATION_FAILED");return rows;
    }
    private Info info(JSONObject row)throws Exception{
        if(!assignedOffice.equals(text(row,"id")))throw fail("FORBIDDEN");String name=text(row,"name");nonblank(name);String note=row.isNull("note")?"":text(row,"note");
        OfficeStatus status;try{status=OfficeStatus.fromWire(text(row,"status"));}catch(IllegalArgumentException invalid){throw fail("VALIDATION_FAILED");}
        String at=row.isNull("status_changed_at")?null:text(row,"status_changed_at"),by=row.isNull("status_changed_by")?null:text(row,"status_changed_by");if(at!=null)timestamp(at);if(by!=null)uuid(by);
        return new Info(assignedOffice,name,note,status,integer(row,"version",1),at,by);
    }
    private Announcement announcement(JSONObject row)throws Exception{
        String id=text(row,"id"),office=text(row,"office_id"),body=text(row,"body"),by=text(row,"actor_id"),time=text(row,"created_at");uuid(id);uuid(by);timestamp(time);nonblank(body);if(!assignedOffice.equals(office))throw fail("FORBIDDEN");
        return new Announcement(id,office,body,by,time,bool(row,"active"),integer(row,"version",1));
    }
    private Catalog catalog(JSONObject row)throws Exception{
        RequestData.Service service;try{service=RequestRepository.parseService(row);}catch(Exception invalid){throw fail("VALIDATION_FAILED");}
        if(!assignedOffice.equals(service.officeId))throw fail("FORBIDDEN");return new Catalog(service,bool(row,"is_demo"),row.getJSONArray("requirements").toString());
    }
    private static Uri.Builder table(String table,String columns,int limit){return Uri.parse("/rest/v1/"+table).buildUpon().appendQueryParameter("select",columns).appendQueryParameter("limit",Integer.toString(limit));}
    private static String text(JSONObject row,String key)throws Exception{Object value=row.get(key);if(!(value instanceof String))throw fail("VALIDATION_FAILED");return (String)value;}
    private static boolean bool(JSONObject row,String key)throws Exception{Object value=row.get(key);if(!(value instanceof Boolean))throw fail("VALIDATION_FAILED");return (Boolean)value;}
    private static long integer(JSONObject row,String key,long minimum)throws Exception{String value=row.get(key).toString();if(!value.matches("[0-9]+"))throw fail("VALIDATION_FAILED");try{long n=Long.parseLong(value);if(n<minimum)throw fail("VALIDATION_FAILED");return n;}catch(NumberFormatException invalid){throw fail("VALIDATION_FAILED");}}
    private static void nonblank(String value)throws WorkflowException{if(value==null || value.isBlank())throw fail("VALIDATION_FAILED");}
    private static void uuid(String value)throws WorkflowException{if(value==null || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))throw fail("VALIDATION_FAILED");}
    private static void timestamp(String value)throws WorkflowException{try{Instant.parse(value);}catch(RuntimeException invalid){throw fail("VALIDATION_FAILED");}}
    private static Set<String> keys(JSONObject object){Set<String> keys=new HashSet<>();object.keys().forEachRemaining(keys::add);return keys;}
    private static boolean equal(Object left,Object right)throws Exception{
        if(left instanceof JSONObject && right instanceof JSONObject){JSONObject a=(JSONObject)left,b=(JSONObject)right;if(!keys(a).equals(keys(b)))return false;for(String key:keys(a))if(!equal(a.get(key),b.get(key)))return false;return true;}
        if(left instanceof JSONArray && right instanceof JSONArray){JSONArray a=(JSONArray)left,b=(JSONArray)right;if(a.length()!=b.length())return false;for(int i=0;i<a.length();i++)if(!equal(a.get(i),b.get(i)))return false;return true;}
        if(left instanceof Number && right instanceof Number)return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString()))==0;
        return Objects.equals(left,right);
    }
    private static WorkflowException fail(String code){return new WorkflowException(code);}
}
