import test, { after, before } from 'node:test';
import assert from 'node:assert/strict';
import { initializeApp, getApps, deleteApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const migrateScript = path.join(scriptDir, 'migrate.mjs');
const projectId = 'demo-familyhub-migrate';

function runMigrate(args, env = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [migrateScript, ...args], {
      cwd: scriptDir,
      env: {
        ...process.env,
        FIRESTORE_EMULATOR_HOST: '127.0.0.1:8080',
        ...env,
      },
      stdio: ['ignore', 'pipe', 'pipe'],
    });

    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (chunk) => { stdout += chunk; });
    child.stderr.on('data', (chunk) => { stderr += chunk; });
    child.on('close', (code) => resolve({ code, stdout, stderr }));
    child.on('error', reject);
  });
}

before(async () => {
  for (const app of getApps()) {
    await deleteApp(app);
  }
  process.env.FIRESTORE_EMULATOR_HOST = '127.0.0.1:8080';
  initializeApp({ projectId });
  const db = getFirestore();
  await db.recursiveDelete(db.collection('families'));

  const familyRef = db.collection('families').doc('legacy-family');
  await familyRef.set({
    members: [{ id: 'old-root', role: 'ADMIN' }],
    legacyFlag: true,
  });
  await familyRef.collection('members').doc('local-admin-id').set({
    id: 'local-admin-id',
    role: 'ADMIN',
    displayName: 'Owner',
    roleLabel: 'Admin',
    avatarColor: '#111111',
    extraField: 'remove-me',
  });
  await familyRef.collection('members').doc('child-1').set({
    id: 'child-1',
    role: 'CHILD',
    displayName: 'Kid',
    roleLabel: 'Child',
    avatarColor: '#222222',
    syncedAt: 123,
  });
});

after(async () => {
  for (const app of getApps()) {
    await deleteApp(app);
  }
});

test('audit reports legacy family blockers', async () => {
  const result = await runMigrate([
    'audit',
    '--emulator',
    '--project',
    projectId,
    '--family',
    'legacy-family',
  ]);

  assert.equal(result.code, 1);
  assert.match(result.stdout, /legacy-root-members/);
  assert.match(result.stdout, /admin-not-auth-uid/);
  assert.match(result.stdout, /member-extra-fields/);
});

test('apply normalizes family when admin uid is provided', async () => {
  const adminUid = 'authAdminUid000000000001';

  const dryRun = await runMigrate([
    'apply',
    '--emulator',
    '--project',
    projectId,
    '--family',
    'legacy-family',
    '--admin-uid',
    adminUid,
    '--dry-run',
  ]);
  assert.equal(dryRun.code, 1);

  const seededDb = getFirestore();
  await seededDb
    .collection('families')
    .doc('legacy-family')
    .collection('members')
    .doc(adminUid)
    .set({
      id: adminUid,
      role: 'ADMIN',
      displayName: 'Owner',
      roleLabel: 'Admin',
      avatarColor: '#111111',
    });

  const apply = await runMigrate([
    'apply',
    '--emulator',
    '--project',
    projectId,
    '--family',
    'legacy-family',
    '--admin-uid',
    adminUid,
  ]);
  assert.equal(apply.code, 0, apply.stderr || apply.stdout);

  const family = await seededDb.collection('families').doc('legacy-family').get();
  const familyData = family.data();
  assert.equal(familyData.ownerUid, adminUid);
  assert.equal(familyData.members, undefined);
  assert.equal(familyData.legacyFlag, undefined);

  const child = await seededDb
    .collection('families')
    .doc('legacy-family')
    .collection('members')
    .doc('child-1')
    .get();
  assert.deepEqual(child.data(), {
    id: 'child-1',
    role: 'CHILD',
    displayName: 'Kid',
    roleLabel: 'Child',
    avatarColor: '#222222',
  });

  const admin = await seededDb
    .collection('families')
    .doc('legacy-family')
    .collection('members')
    .doc(adminUid)
    .get();
  assert.equal(admin.data().role, 'ADMIN');

  const legacyAdmin = await seededDb
    .collection('families')
    .doc('legacy-family')
    .collection('members')
    .doc('local-admin-id')
    .get();
  assert.equal(legacyAdmin.exists, false);
});
