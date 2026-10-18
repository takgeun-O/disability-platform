package devtools;

import io.github.takgeun.iyum.member.domain.MemberInputPolicy;
import io.github.takgeun.iyum.community.post.domain.PostInputPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.security.SecureRandom;

/** Explicit local fixture tool. Not scanned by Spring, packaged, or run by Flyway. */
public final class CommunitySeed {
    static final String KEY = "feed-seed-v1";
    static final String MARK = "[개발 테스트]";
    static final Path DIR = System.getenv("IYUM_SEED_DIR") == null ? Path.of(System.getProperty("user.home"), ".local", "share", "iyum", KEY) : Path.of(System.getenv("IYUM_SEED_DIR"));
    static final Path MANIFEST = DIR.resolve("manifest.properties");
    static final Path ACCOUNTS = DIR.resolve("accounts.properties");
    static final String[] EMAILS = {"feed-seed-1@iyum.test", "feed-seed-2@iyum.test", "feed-seed-3@iyum.test"};
    static final String[] NAMES = {"개발테스트소리숲", "개발테스트천천히함께걷는사람", "개발테스트이어봄"};
    static final String[] TOPICS = {"COCHLEAR_IMPLANT", "DAILY", "TREATMENT_REHABILITATION", "COMMUNICATION", "EMPLOYMENT", "WELFARE_LIFE"};
    static final String[] TITLES = {"처음 보청기를 쓰며 기록한 일주일", "조용한 카페에서 대화를 나눈 경험", "기기를 관리할 때 챙기는 작은 습관", "상담 전에 질문을 메모해 보았어요", "오늘의 작은 변화와 일상을 나눕니다", "함께 대화할 때 도움이 된 방법"};
    static Properties load(Path file) throws Exception { var p=new Properties(); if(Files.exists(file))try(var in=Files.newBufferedReader(file)){p.load(in);}return p; }
    static void privateFile(Path file, Properties p) throws Exception {
        Files.createDirectories(DIR); Files.setPosixFilePermissions(DIR,PosixFilePermissions.fromString("rwx------"));
        Path tmp=Files.createTempFile(DIR,"ownership-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try {
            try(var out=Files.newBufferedWriter(tmp)){p.store(out,"Local fixture; never share credentials");}
            try(var channel=java.nio.channels.FileChannel.open(tmp,StandardOpenOption.WRITE)){channel.force(true);}
            Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(tmp); }
    }
    static void readableAccounts() throws Exception {
        if(!Files.exists(ACCOUNTS))return;
        Properties p=load(ACCOUNTS);Path file=DIR.resolve("accounts.json");
        if(!Files.exists(file))Files.createFile(file,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"));
        var json=new StringBuilder("[\n");
        for(int i=1;i<=3;i++){
            if(i>1)json.append(",\n");
            // Generated email/password characters are JSON-safe ASCII and URL-safe Base64.
            json.append("  {\"email\": \"").append(p.getProperty("account."+i+".email")).append("\", \"password\": \"").append(p.getProperty("account."+i+".password")).append("\"}");
        }
        Files.writeString(file,json.append("\n]\n").toString());
    }
    static String setting(Properties p,String key){var v=System.getenv(key);if(v==null)v=p.getProperty(key);if(v==null||v.isBlank())throw new IllegalArgumentException("Missing setting: "+key);return v.strip();}
    static String password(){byte[] b=new byte[24];new SecureRandom().nextBytes(b);return "Iy!"+Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    static PreparedStatement statement(Connection c,String sql,Object... values)throws SQLException{var s=c.prepareStatement(sql);for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);return s;}
    static long insert(Connection c,String sql,Object... values)throws SQLException{try(var s=statement(c,sql,values);var rs=s.executeQuery()){rs.next();return rs.getLong(1);}}
    static List<Long> ids(Properties p,String key){return Arrays.stream(p.getProperty(key,"").split(",")).filter(s->!s.isBlank()).map(Long::valueOf).toList();}
    static String joined(List<Long> ids){return String.join(",",ids.stream().map(String::valueOf).toList());}
    static List<Long> queryIds(Connection c,String sql)throws SQLException{var out=new ArrayList<Long>();try(var s=c.createStatement();var rs=s.executeQuery(sql)){while(rs.next())out.add(rs.getLong(1));}return out;}
    static void ownership(Connection c,Properties m)throws Exception{
        var members=ids(m,"members");if(members.size()!=3)throw new IllegalStateException("Invalid ownership manifest");
        for(int i=0;i<3;i++)try(var s=statement(c,"select email from members where id=?",members.get(i));var rs=s.executeQuery()){
            if(!rs.next()||!EMAILS[i].equals(rs.getString(1)))throw new IllegalStateException("Member ownership mismatch");
        }
        var posts=ids(m,"posts");var comments=ids(m,"comments");
        for(long id:posts)try(var s=statement(c,"select author_id,title from community_posts where id=?",id);var rs=s.executeQuery()){
            if(!rs.next()||!members.contains(rs.getLong(1))||!rs.getString(2).startsWith(MARK))throw new IllegalStateException("Post ownership mismatch: "+id);
        }
        for(long id:comments)try(var s=statement(c,"select author_id,post_id,content from community_comments where id=?",id);var rs=s.executeQuery()){
            if(!rs.next()||!members.contains(rs.getLong(1))||!posts.contains(rs.getLong(2))||!rs.getString(3).startsWith(MARK))throw new IllegalStateException("Comment ownership mismatch: "+id);
        }
    }
    static void report(Connection c,Properties m)throws Exception{
        ownership(c,m);
        readableAccounts();
        System.out.println("Seed "+KEY+"; owned members="+ids(m,"members").size()+", posts="+ids(m,"posts").size()+", comments="+ids(m,"comments").size());
        for(int i=0;i<5;i++){
            long id=ids(m,"posts").get(i);
            try(var s=statement(c,"select count(*) from community_comments where post_id=? and deleted_at is null",id);var rs=s.executeQuery()){
                rs.next();System.out.println("Case "+i+": http://127.0.0.1:8443/community/posts/"+id+" ; visible comments="+rs.getInt(1));
            }
        }
        System.out.println("Credentials (not printed; open JSON locally): "+DIR.resolve("accounts.json")+" ; manifest: "+MANIFEST);
    }
    static void noAdditionalData(Connection c,Properties m)throws Exception {
        String members=joined(ids(m,"members")),posts=joined(ids(m,"posts"));
        if(!new HashSet<>(queryIds(c,"select id from community_posts where author_id in ("+members+")")).equals(new HashSet<>(ids(m,"posts"))) ||
           !new HashSet<>(queryIds(c,"select id from community_comments where author_id in ("+members+") or post_id in ("+posts+")")).equals(new HashSet<>(ids(m,"comments"))))
            throw new IllegalStateException("Additional data references seed accounts/posts; cleanup refused");
    }
    static void recover(Connection c,Properties m)throws Exception {
        Path journals=DIR.resolve("comment-journal");if(!Files.exists(journals))return;
        var owned=new ArrayList<>(ids(m,"comments"));
        try(var files=Files.list(journals)){
            for(Path file:files.filter(f->f.toString().endsWith(".properties")).sorted().toList()){
                var j=load(file);long post=Long.parseLong(j.getProperty("postId")),member=Long.parseLong(j.getProperty("memberId"));
                if(!ids(m,"posts").contains(post)||!ids(m,"members").contains(member))throw new IllegalStateException("Journal target ownership mismatch");
                String content=new String(Base64.getDecoder().decode(j.getProperty("contentBase64")),java.nio.charset.StandardCharsets.UTF_8);
                if(!content.startsWith(MARK)||!content.contains("[run:"+j.getProperty("runId")+"]"))throw new IllegalStateException("Journal mark mismatch");
                long recorded=Long.parseLong(j.getProperty("commentId","0"));
                if(owned.contains(recorded))continue; // normal ownership() still validates mark, author and post
                var matches=new ArrayList<Long>();
                try(var q=statement(c,"select id from community_comments where post_id=? and author_id=? and content=?",post,member,content);var rs=q.executeQuery()){while(rs.next())matches.add(rs.getLong(1));}
                if(matches.size()>1 || (recorded!=0 && !matches.equals(List.of(recorded))))throw new IllegalStateException("Ambiguous or mismatched journal receipt");
                if(matches.size()==1 && !owned.contains(matches.getFirst()))owned.add(matches.getFirst());
            }
        }
        m.setProperty("comments",joined(owned));ownership(c,m);privateFile(MANIFEST,m);
        System.out.println("Journal recovery verified; owned comments="+owned.size()+". No database row changed.");
    }
    // Written before DELETE/commit so an interrupted file archive can be resumed safely.
    static final Path CLEANUP = DIR.resolve("cleanup-pending.properties");
    static void archiveCleanup(Properties receipt)throws Exception {
        String id=receipt.getProperty("archiveId", "");
        if(!id.matches("[a-f0-9-]{36}"))throw new IllegalStateException("Invalid cleanup receipt");
        Path archive=DIR.resolve("history").resolve(id);
        Files.createDirectories(archive);
        privateFile(archive.resolve("manifest.properties"),receipt);
        Path journal=DIR.resolve("comment-journal"), saved=archive.resolve("comment-journal");
        if(Files.exists(journal)) {
            if(Files.exists(saved))throw new IllegalStateException("Both active and archived journals exist; inspect cleanup receipt");
            Files.move(journal,saved,StandardCopyOption.ATOMIC_MOVE);
        }
        Files.deleteIfExists(ACCOUNTS);Files.deleteIfExists(DIR.resolve("accounts.json"));
        Files.deleteIfExists(MANIFEST);
        Files.delete(CLEANUP); // last: failures above leave a durable recovery receipt
        System.out.println("Owned rows removed; manifest and journals archived together.");
    }
    static boolean resumeCleanup(Connection c,String action,String db,String port)throws Exception {
        if(!Files.exists(CLEANUP))return false;
        Properties receipt=load(CLEANUP);
        if(!db.equals(receipt.getProperty("database"))||!port.equals(receipt.getProperty("port")))throw new IllegalStateException("Cleanup receipt database mismatch");
        if(!action.equals("cleanup"))throw new IllegalStateException("Pending cleanup: run cleanup to verify database and resume archive before create/recover");
        long remaining=0;
        for(String[] pair:new String[][]{{"members","members"},{"posts","community_posts"},{"comments","community_comments"}}) {
            String owned=joined(ids(receipt,pair[0]));
            if(!owned.isEmpty())remaining+=queryIds(c,"select id from "+pair[1]+" where id in ("+owned+")").size();
        }
        if(remaining==0) { c.rollback();archiveCleanup(receipt);return true; }
        // Commit did not happen: preserve active files unless the complete original ownership verifies.
        if(!load(MANIFEST).equals(withoutArchive(receipt)))throw new IllegalStateException("Pending cleanup manifest mismatch; manual inspection required");
        ownership(c,receipt);noAdditionalData(c,receipt);
        Files.delete(CLEANUP);return false;
    }
    static Properties withoutArchive(Properties p) { Properties copy=new Properties();copy.putAll(p);copy.remove("archiveId");return copy; }
    public static void main(String[] args)throws Exception{
        Files.createDirectories(DIR);
        Path lock=DIR.resolve("ownership.lock");Files.createDirectory(lock);
        try { run(args); } finally { Files.delete(lock); }
    }
    static void run(String[] args)throws Exception{

        if(args.length!=2||!args[1].equals("--confirm-local-development"))throw new IllegalArgumentException("Use create/status/dry-run/recover/cleanup --confirm-local-development");
        String action=args[0];if(!Set.of("create","status","dry-run","recover","cleanup").contains(action))throw new IllegalArgumentException("Unknown action");
        var config=Files.readString(Path.of("src/main/resources/application.yaml"));
        if(!config.contains("jdbc:postgresql://localhost:${IYUM_DB_PORT}/${IYUM_DB_NAME}"))throw new IllegalStateException("Local datasource template changed; review before seeding");
        Properties env=load(Path.of(".env"));String port=setting(env,"IYUM_DB_PORT"),db=setting(env,"IYUM_DB_NAME");
        if(!port.matches("[0-9]{1,5}")||!db.matches("[A-Za-z0-9_-]+")||!db.toLowerCase().contains("iyum"))throw new IllegalArgumentException("Expected local IYUM database");
        var props=new Properties();props.setProperty("user",setting(env,"IYUM_DB_USER"));props.setProperty("password",setting(env,"IYUM_DB_PASSWORD"));props.setProperty("connectTimeout","5");
        // Host cannot be supplied from env/CLI. Neither datasource secrets nor JDBC URL are logged.
        try(var c=DriverManager.getConnection("jdbc:postgresql://127.0.0.1:"+port+"/"+db,props)){
            c.setAutoCommit(false);try{
                try(var s=c.createStatement()){s.execute("select pg_advisory_xact_lock(1800261018)");}
                if(resumeCleanup(c,action,db,port))return;
                Properties m=load(MANIFEST);
                if(!m.isEmpty()){
                    if(!db.equals(m.getProperty("database"))||!port.equals(m.getProperty("port")))throw new IllegalStateException("Manifest belongs to another database");
                    if(action.equals("recover")){recover(c,m);c.commit();return;}
                    ownership(c,m);
                    if(action.equals("dry-run")){noAdditionalData(c,m);c.rollback();System.out.println("Cleanup dry-run passed: owned rows including soft-deleted comments; database unchanged.");return;}
                    if(action.equals("cleanup")){
                        String members=joined(ids(m,"members")),posts=joined(ids(m,"posts")),comments=joined(ids(m,"comments"));
                        // Refuse deletion if later manual data references this fixture. Never cascade or delete unrecorded rows.
                        noAdditionalData(c,m);
                        Properties receipt=new Properties();receipt.putAll(m);receipt.setProperty("archiveId",UUID.randomUUID().toString());privateFile(CLEANUP,receipt);
                        try(var s=c.createStatement()){
                            s.executeUpdate("delete from community_comments where id in ("+comments+")");s.executeUpdate("delete from community_posts where id in ("+posts+")");
                            s.executeUpdate("delete from members where id in ("+members+")");
                        }
                        c.commit();archiveCleanup(receipt);return;
                    }
                    report(c,m);c.commit();System.out.println("Existing fixture preserved; inserted 0 rows.");return;
                }
                if(Files.exists(DIR.resolve("comment-journal")))throw new IllegalStateException("Orphan legacy journals without active manifest; preserve and reconcile with archived manifest before create");
                if(!action.equals("create"))throw new IllegalStateException("No manifest; no data changed");
                for(String email:EMAILS)try(var s=statement(c,"select id from members where email=?",email);var rs=s.executeQuery()){if(rs.next())throw new IllegalStateException("Seed email exists without manifest; no changes");}
                var accounts=new Properties();var memberIds=new ArrayList<Long>();var postIds=new ArrayList<Long>();var commentIds=new ArrayList<Long>();
                var encoder=PasswordEncoderFactories.createDelegatingPasswordEncoder();
                Instant base=Instant.now().minusSeconds(7200).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
                for(int i=0;i<3;i++){
                    String pw=password();accounts.setProperty("account."+(i+1)+".email",EMAILS[i]);accounts.setProperty("account."+(i+1)+".password",pw);
                    long id=insert(c,"insert into members(email,password_hash,nickname,status,role,created_at,updated_at) values (?,?,?,'ACTIVE','USER',?,?) returning id",MemberInputPolicy.normalizeEmail(EMAILS[i]),encoder.encode(pw),MemberInputPolicy.normalizedNickname(NAMES[i]),Timestamp.from(base),Timestamp.from(base));memberIds.add(id);
                }
                // Dedicated synthetic ACTIVE members; no existing account is activated and no security setting changes.
                for(int i=0;i<85;i++){
                    String topic=i<65?"HEARING_AID":TOPICS[(i-65)%TOPICS.length];String type=i<45?"QUESTION":i<55?"GENERAL":i<65?"REVIEW":new String[]{"GENERAL","QUESTION","REVIEW"}[i%3];
                    String title=MARK+" "+String.format("%02d",i+1)+" "+TITLES[i%TITLES.length];
                    if(i==0)title+= " — 여러 사람이 함께 있는 공간에서 내가 편안하게 대화를 이어가는 방법을 조금씩 찾아가는 기록";
                    String content=MARK+" 커서 조회와 화면 배치를 확인하기 위한 가상 경험입니다. 실제 의료·정책 안내가 아닙니다.\n\n"+TITLES[i%TITLES.length]+". 오늘은 조용한 곳과 소리가 많은 곳에서 느낀 점을 메모했어요.\n서로의 경험이 다를 수 있어 궁금한 점을 나누고 싶습니다. 🌿 안녕하세요 — café, 한글과 Unicode.\n\n"+"짧은 휴식과 대화 시간을 천천히 조절해 보았어요. ".repeat(i%4+1);
                    var stamp=Timestamp.from(base.plusSeconds(i/5)); // tie groups exercise id DESC
                    long id=insert(c,"insert into community_posts(author_id,post_type,topic,title,content,created_at,updated_at) values (?,?,?,?,?,?,?) returning id",memberIds.get(i%3),type,topic,PostInputPolicy.normalizeTitle(title),PostInputPolicy.validateContent(content),stamp,stamp);postIds.add(id);
                }
                int[] counts={65,0,1,20,21};
                for(int p=0;p<counts.length;p++)for(int i=0;i<counts[p];i++){
                    var stamp=Timestamp.from(base.plusSeconds(500+i/5));
                    String content=MARK+" 댓글 "+String.format("%02d",i+1)+" · "+new String[]{"경험을 나눠 주셔서 고마워요.","저도 비슷한 상황을 기록해 보고 있어요.","서로의 속도를 존중하며 이야기를 이어가요."}[i%3]+"\n화면과 커서 순서를 확인하는 예시입니다. 🌱";
                    commentIds.add(insert(c,"insert into community_comments(post_id,author_id,content,created_at,updated_at) values (?,?,?,?,?) returning id",postIds.get(p),memberIds.get(i%3),content,stamp,stamp));
                }
                m.setProperty("database",db);m.setProperty("port",port);m.setProperty("key",KEY);m.setProperty("members",joined(memberIds));m.setProperty("posts",joined(postIds));m.setProperty("comments",joined(commentIds));m.setProperty("createdAt",Instant.now().toString());for(int i=0;i<5;i++)m.setProperty("case."+i,String.valueOf(postIds.get(i)));
                privateFile(ACCOUNTS,accounts);Path pending=DIR.resolve("manifest-pending.properties");privateFile(pending,m);
                c.commit();Files.move(pending,MANIFEST,StandardCopyOption.ATOMIC_MOVE);report(c,m);
            }catch(Exception e){c.rollback();throw e;}
        }catch(SQLException e){throw new IllegalStateException("Local seed database operation failed (SQLSTATE "+e.getSQLState()+"); transaction rolled back. Check local configuration without sharing secrets.");}
    }
}
