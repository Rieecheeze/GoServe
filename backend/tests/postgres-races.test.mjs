import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { readFile, readdir } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { setTimeout as delay } from 'node:timers/promises';
import { fixtureSql } from './sql-fixture.mjs';

// Explicit opt-in, loopback only, creates and drops only its own fresh synthetic database.
const binary = process.env.GOSERVE_FIXTURE_PSQL;
const port = process.env.GOSERVE_FIXTURE_PORT;
const user = process.env.GOSERVE_FIXTURE_USER;
assert.ok(binary && /^\d+$/.test(port ?? '') && user, 'Set GOSERVE_FIXTURE_PSQL, GOSERVE_FIXTURE_PORT, GOSERVE_FIXTURE_USER for an isolated local cluster.');
const database = 'goserve_race_' + randomUUID().replaceAll('-', '');
const children = new Set();
function connection(db, application = 'goserve_race_fixture') {
  const child = spawn(binary, ['-X', '-q', '-A', '-t', '-v', 'ON_ERROR_STOP=1', '-h', '127.0.0.1', '-p', port, '-U', user, '-d', db], {
    env: { ...process.env, PGAPPNAME: application, PGOPTIONS: '-c statement_timeout=10000 -c lock_timeout=8000' },
    windowsHide: true,
  });
  children.add(child);
  let stdout = '', stderr = '';
  child.stdout.on('data', data => { stdout += data; });
  child.stderr.on('data', data => { stderr += data; });
  const done = new Promise((resolve, reject) => {
    child.once('error', reject);
    child.once('close', code => { children.delete(child); resolve({ code, stdout, stderr }); });
  });
  return { child, done, output: () => stdout };
}
async function execute(sql, db = database) {
  const session = connection(db);
  session.child.stdin.end(sql + '\n');
  const result = await session.done;
  assert.equal(result.code, 0, result.stderr);
  return result.stdout.trim();
}
async function eventually(predicate, message) {
  for (let attempt = 0; attempt < 100; attempt++) {
    if (await predicate()) return;
    await delay(50);
  }
  assert.fail(message);
}
const id = n => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
const student = id(1), faculty = id(2), replacement = id(3), thread = id(4);
const oldSlot = id(5), newSlot = id(6), appointment = id(7);
const auth = actor => `set role authenticated; select set_config('request.jwt.claim.sub','${actor}',false),set_config('request.jwt.claims','{"is_anonymous":false,"amr":[{"method":"password"}]}',false);`;

async function denyAfterConcurrentChange(label, mutation, attemptedMutation) {
  const holder = connection(database, `${label}_holder`);
  holder.child.stdin.write(`begin; ${mutation}\n\\echo LOCK_HELD\n`);
  await eventually(() => holder.output().includes('LOCK_HELD'), 'Holder did not acquire its row lock');
  const contender = connection(database, `${label}_contender`);
  contender.child.stdin.end(auth(faculty) + attemptedMutation + '\n');
  try {
    // Observe a real PostgreSQL lock wait; a timer alone would not prove the interleaving.
    await eventually(async () => (await execute(`select count(*) from pg_stat_activity where datname='${database}' and application_name='${label}_contender' and wait_event_type='Lock'`)) === '1', 'Contender did not wait on the row lock');
    holder.child.stdin.end('commit;\n');
    assert.equal((await holder.done).code, 0);
    const result = await contender.done;
    assert.notEqual(result.code, 0, 'Revoked actor unexpectedly mutated the record');
    assert.match(result.stderr, /NOT_FOUND/);
  } finally {
    if (!holder.child.stdin.destroyed) holder.child.stdin.end('rollback;\n');
  }
}

let created = false;
try {
  await execute(`create database ${database};`, 'postgres');
  created = true;
  console.log(await execute('select version();'));
  await execute(fixtureSql);
  await execute(await readFile(new URL('../schema.sql', import.meta.url), 'utf8'));
  for (const file of (await readdir(new URL('../migrations/', import.meta.url))).filter(name => name.endsWith('.sql')).sort()) {
    await execute(await readFile(new URL('../migrations/' + file, import.meta.url), 'utf8'));
  }
  await execute(`
    insert into auth.users(id,email) values ('${student}','student@fixture.test'),('${faculty}','faculty@fixture.test'),('${replacement}','replacement@fixture.test');
    insert into public.profiles(id,email,role,office_id,staff_capability,profile_completed_at) values
      ('${student}','student@fixture.test','student',null,null,now()),
      ('${faculty}','faculty@fixture.test','staff','DEP-CCS','faculty',now()),
      ('${replacement}','replacement@fixture.test','staff','DEP-CCS','faculty',now());
    insert into public.terms_documents values('fixture','Synthetic fixture','fixture',now(),true);
    insert into goserve_private.terms_acceptances(profile_id,terms_version,actor_id) select id,'fixture',id from public.profiles;
    insert into public.inquiry_threads(id,student_id,office_id,assigned_to) values('${thread}','${student}','DEP-CCS','${faculty}');
    insert into public.consultation_slots(id,office_id,schedule_owner_id,start_at,end_at,capacity,reserved) values
      ('${oldSlot}','DEP-CCS','${faculty}',now()+interval '2 days',now()+interval '2 days 1 hour',1,1),
      ('${newSlot}','DEP-CCS','${replacement}',now()+interval '3 days',now()+interval '3 days 1 hour',1,0);
    insert into public.appointments(id,student_id,slot_id) values('${appointment}','${student}','${oldSlot}');
    insert into goserve_private.policies values('consultation_cancel','{"roles":["staff","student"],"minimum_notice_seconds":0}');
  `);

  await denyAfterConcurrentChange('inquiry',
    `update public.inquiry_threads set assigned_to='${replacement}',version=version+1 where id='${thread}';`,
    `select public.send_inquiry('${thread}',null,'Must not be sent','${randomUUID()}');`);
  assert.equal(await execute(`select count(*) from public.inquiry_messages where thread_id='${thread}'`), '0');
  console.log('PASS: separate-session reassignment/reply race denied after observed lock wait; no message inserted');

  await denyAfterConcurrentChange('appointment',
    `update public.appointments set slot_id='${newSlot}',version=version+1 where id='${appointment}'; update public.consultation_slots set reserved=0 where id='${oldSlot}'; update public.consultation_slots set reserved=1 where id='${newSlot}';`,
    `select public.change_appointment('${appointment}',2,null,null,'Must not cancel','${randomUUID()}');`);
  assert.equal(await execute(`select state||':'||slot_id::text from public.appointments where id='${appointment}'`), `booked:${newSlot}`);
  assert.equal(await execute(`select string_agg(reserved::text,',' order by id) from public.consultation_slots where id in('${oldSlot}','${newSlot}')`), '0,1');
  console.log('PASS: separate-session reschedule/cancel race denied after observed lock wait; booking and capacity preserved');
} finally {
  for (const child of children) child.kill();
  if (created) await execute(`drop database ${database} with (force);`, 'postgres');
}
