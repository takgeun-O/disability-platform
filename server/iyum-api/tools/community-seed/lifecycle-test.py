# Explicit disposable local DB lifecycle test. Never targets the configured development DB.
import os,subprocess,json,time,uuid,tempfile
from pathlib import Path
root=Path(__file__).resolve().parents[2];out=Path(tempfile.mkdtemp(prefix='iyum-seed-lifecycle-'));private=out/'isolated-private';private.mkdir(mode=0o700,exist_ok=True)
db='iyum_seed_test_'+uuid.uuid4().hex[:12]
assert db.startswith('iyum_seed_test_')
env=os.environ.copy()
for line in (root/'.env').read_text().splitlines():
 if '=' in line and not line.startswith('#'):
  k,v=line.split('=',1)
  if k.startswith('IYUM_DB_'):env[k]=v.strip().strip('"').strip("'")
env['IYUM_DB_NAME']=db;env['IYUM_SEED_DIR']=str(private);env['JAVA_HOME']=os.environ.get('JAVA_HOME') or subprocess.check_output(['/usr/libexec/java_home','-v','21'],text=True).strip()
results=[]
def sql(text):
 r=subprocess.run(['docker','exec','-i','iyum-local-postgres-1','sh','-c','psql -v ON_ERROR_STOP=1 -X -At -U "$POSTGRES_USER" -d "$1"','sh',db],input=text,text=True,capture_output=True);assert r.returncode==0,'isolated SQL failed';return r.stdout.strip()
def action(a,success=True):
 r=subprocess.run(['./gradlew','-I','tools/community-seed.init.gradle','seedCommunity','-PseedAction='+a,'-PseedConfirm=--confirm-local-development'],cwd=root,env=env,text=True,capture_output=True)
 (out/('isolated-'+a+'-'+str(len(results))+'.log')).write_text(r.stdout+r.stderr)
 assert (r.returncode==0)==success,'Unexpected '+a+' outcome'
 return r
def properties():return dict(l.split('=',1) for l in (private/'manifest.properties').read_text().splitlines() if l and not l.startswith('#'))
subprocess.run(['docker','exec','iyum-local-postgres-1','sh','-c','createdb -U "$POSTGRES_USER" "$1"','sh',db],check=True,capture_output=True)
try:
 for f in sorted((root/'src/main/resources/db/migration').glob('*.sql')):sql(f.read_text())
 action('create');m=properties();member=int(m['members'].split(',')[0]);post=int(m['posts'].split(',')[0])
 import base64
 def intent(member,post,label):
  run=str(uuid.uuid4());content='[개발 테스트] '+label+' [run:'+run+']'
  journal=private/'comment-journal';journal.mkdir(mode=0o700,exist_ok=True)
  f=journal/(run+'.properties');f.write_text(f'runId={run}\npostId={post}\nmemberId={member}\ncontentBase64='+base64.b64encode(content.encode()).decode()+'\n')
  cid=int(sql(f"INSERT INTO community_comments(post_id,author_id,content,created_at,updated_at,deleted_at) VALUES({post},{member},'{content}',now(),now(),now()) RETURNING id;").splitlines()[0]);return f,cid
 f,cid=intent(member,post,'round one');action('recover');action('dry-run')
 # No round id in legacy manifest/journal: first generation format remains accepted.
 results.append({'name':'legacy intent recovered and soft deleted ownership retained','pass':True})
 unknown=int(sql(f"INSERT INTO community_comments(post_id,author_id,content,created_at,updated_at) VALUES({post},{member},'unowned',now(),now()) RETURNING id;").splitlines()[0])
 snapshot=(private/'manifest.properties').read_bytes();journal=f.read_bytes()
 action('cleanup',False);assert snapshot==(private/'manifest.properties').read_bytes() and journal==f.read_bytes();assert sql('SELECT count(*) FROM community_posts')=='85'
 sql(f'DELETE FROM community_comments WHERE id={unknown}')
 results.append({'name':'unowned row refuses cleanup, active manifest and journal unchanged','pass':True})
 # Force failure AFTER DB commit, before archive directory creation.
 (private/'history').write_text('injected archive failure')
 action('cleanup',False);assert sql('SELECT count(*) FROM community_posts')=='0';assert (private/'cleanup-pending.properties').exists();assert f.exists();assert (private/'manifest.properties').exists()
 action('create',False)
 (private/'history').unlink();action('cleanup');assert not (private/'cleanup-pending.properties').exists();assert not f.exists()
 archive=list((private/'history').iterdir());assert len(archive)==1;assert (archive[0]/'comment-journal'/f.name).exists();assert (archive[0]/'manifest.properties').exists()
 results.append({'name':'post-commit archive failure blocks create and cleanup safely resumes archive','pass':True})
 action('create');new=properties();newmember=int(new['members'].split(',')[0]);newpost=int(new['posts'].split(',')[0]);assert newmember!=member and newpost!=post
 action('recover');action('dry-run');assert str(cid) not in properties()['comments'].split(',')
 results.append({'name':'same directory create-cleanup-create-recover-dry-run, previous journal archived','pass':True})
 f2,cid2=intent(newmember,newpost,'round two response lost');action('recover');assert str(cid2) in properties()['comments'].split(',');action('dry-run')
 results.append({'name':'second round missing response ID recovered exactly','pass':True})
 bad=private/'comment-journal'/'wrong.properties';bad.write_text(f2.read_text().replace('postId='+str(newpost),'postId='+str(post)))
 action('recover',False);bad.unlink();results.append({'name':'current round mismatched journal rejected, not skipped','pass':True})
 action('cleanup');assert sql('SELECT count(*) FROM community_posts')=='0';assert sql('SELECT count(*) FROM members')=='0';assert sql('SELECT count(*) FROM community_comments')=='0';assert len(list((private/'history').iterdir()))==2
 results.append({'name':'second cleanup preserves two archives and deletes only isolated owned data','pass':True})
finally:
 subprocess.run(['docker','exec','iyum-local-postgres-1','sh','-c','dropdb -U "$POSTGRES_USER" "$1"','sh',db],check=True,capture_output=True)
 (out/'isolated-results.json').write_text(json.dumps({'database':db,'databaseDropped':True,'results':results},indent=2))
 print(json.dumps(results))
