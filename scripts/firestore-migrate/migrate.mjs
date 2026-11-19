#!/usr/bin/env node

import { initializeApp, cert, getApps, deleteApp } from 'firebase-admin/app';
import { FieldValue, getFirestore } from 'firebase-admin/firestore';
import { parseArgs } from 'node:util';

const ALLOWED_MEMBER_FIELDS = new Set([
  'id',
  'role',
  'displayName',
  'roleLabel',
  'avatarColor',
  'birthDate',
  'age',
  'photoUrl',
]);

const ACTIVE_ROLES = new Set(['ADMIN', 'PARENT', 'CHILD', 'PENDING']);

const HELP = `Firestore family migration for hardened security rules.

Usage:
  node migrate.mjs audit  [--project <id>] [--emulator] [--family <familyId>]
  node migrate.mjs apply [--project <id>] [--emulator] [--family <familyId>] [--admin-uid <uid>] [--dry-run]

Modes:
  audit   Read-only report of migration blockers and planned fixes.
  apply   Normalize family documents and member subcollections.

Options:
  --project <id>     Firebase project id (required for production).
  --emulator         Use Firestore emulator at 127.0.0.1:8080.
  --family <id>      Process a single family instead of the whole database.
  --admin-uid <uid>  Force the ADMIN Firebase Auth UID when auto-detection fails.
  --dry-run          With apply, print planned writes without committing them.

Production credentials:
  Set GOOGLE_APPLICATION_CREDENTIALS to a service-account JSON file, or run:
  gcloud auth application-default login

Examples:
  node migrate.mjs audit --project my-familyhub
  node migrate.mjs apply --project my-familyhub --dry-run
  node migrate.mjs apply --project my-familyhub --family family-a --admin-uid aliceUid28chars0000000001
`;

function looksLikeFirebaseUid(value) {
  return typeof value === 'string' && /^[A-Za-z0-9]{20,128}$/.test(value);
}

function pickAllowedMemberFields(data, memberId) {
  const normalized = { id: memberId, role: data.role };
  for (const field of ALLOWED_MEMBER_FIELDS) {
    if (field === 'id' || field === 'role') continue;
    if (data[field] !== undefined) normalized[field] = data[field];
  }
  return normalized;
}

function memberIssues(memberId, data) {
  const issues = [];
  const fields = Object.keys(data ?? {});

  if (!data || typeof data !== 'object') {
    issues.push({ level: 'error', code: 'member-missing-data', message: `members/${memberId} has no data` });
    return issues;
  }

  if (typeof data.id !== 'string' || data.id !== memberId) {
    issues.push({
      level: 'warn',
      code: 'member-id-mismatch',
      message: `members/${memberId} has id=${JSON.stringify(data.id)}`,
    });
  }

  if (!ACTIVE_ROLES.has(data.role)) {
    issues.push({
      level: 'error',
      code: 'member-invalid-role',
      message: `members/${memberId} has invalid role=${JSON.stringify(data.role)}`,
    });
  }

  const extraFields = fields.filter((field) => !ALLOWED_MEMBER_FIELDS.has(field));
  if (extraFields.length > 0) {
    issues.push({
      level: 'warn',
      code: 'member-extra-fields',
      message: `members/${memberId} has unsupported fields: ${extraFields.join(', ')}`,
    });
  }

  const missingRequired = ['id', 'role'].filter((field) => data[field] === undefined);
  if (missingRequired.length > 0) {
    issues.push({
      level: 'error',
      code: 'member-missing-required',
      message: `members/${memberId} is missing required fields: ${missingRequired.join(', ')}`,
    });
  }

  return issues;
}

function chooseAdminUid(members, forcedAdminUid) {
  if (forcedAdminUid) {
    const forced = members.find((member) => member.id === forcedAdminUid);
    if (!forced) {
      return {
        uid: forcedAdminUid,
        issues: [{
          level: 'error',
          code: 'forced-admin-missing',
          message: `Forced --admin-uid ${forcedAdminUid} is not present under members/`,
        }],
      };
    }
    if (forced.data.role !== 'ADMIN') {
      return {
        uid: forcedAdminUid,
        issues: [{
          level: 'error',
          code: 'forced-admin-not-admin',
          message: `Forced --admin-uid ${forcedAdminUid} does not have role ADMIN`,
        }],
      };
    }
    return { uid: forcedAdminUid, issues: [] };
  }

  const admins = members.filter((member) => member.data.role === 'ADMIN');
  if (admins.length === 0) {
    return {
      uid: null,
      issues: [{
        level: 'error',
        code: 'no-admin',
        message: 'No member with role ADMIN found',
      }],
    };
  }

  if (admins.length > 1) {
    const authLike = admins.filter((member) => looksLikeFirebaseUid(member.id));
    if (authLike.length === 1) {
      return {
        uid: authLike[0].id,
        issues: [{
          level: 'warn',
          code: 'multiple-admins',
          message: `Multiple ADMIN members found; selected auth-like uid ${authLike[0].id}`,
        }],
      };
    }
    return {
      uid: null,
      issues: [{
        level: 'error',
        code: 'multiple-admins',
        message: `Multiple ADMIN members found: ${admins.map((member) => member.id).join(', ')}. Pass --admin-uid.`,
      }],
    };
  }

  const admin = admins[0];
  if (!looksLikeFirebaseUid(admin.id)) {
    return {
      uid: null,
      issues: [{
        level: 'error',
        code: 'admin-not-auth-uid',
        message: `ADMIN is stored at members/${admin.id}, which does not look like a Firebase Auth UID. Pass --admin-uid.`,
      }],
    };
  }

  return { uid: admin.id, issues: [] };
}

function buildFamilyPlan(familyId, familyData, members, options) {
  const issues = [];
  const actions = [];

  if (familyData?.members !== undefined) {
    issues.push({
      level: 'warn',
      code: 'legacy-root-members',
      message: 'Family document still has legacy root field "members"',
    });
    actions.push({ type: 'delete-family-field', field: 'members' });
  }

  for (const member of members) {
    issues.push(...memberIssues(member.id, member.data));
  }

  const adminSelection = chooseAdminUid(members, options.adminUid ?? null);
  issues.push(...adminSelection.issues);

  const ownerUid = familyData?.ownerUid;
  if (!ownerUid) {
    if (adminSelection.uid) {
      actions.push({ type: 'set-owner-uid', ownerUid: adminSelection.uid });
    }
  } else if (adminSelection.uid && ownerUid !== adminSelection.uid) {
    issues.push({
      level: 'error',
      code: 'owner-uid-mismatch',
      message: `ownerUid=${ownerUid} does not match ADMIN uid ${adminSelection.uid}`,
    });
  }

  const disallowedFamilyFields = Object.keys(familyData ?? {}).filter(
    (field) => field !== 'ownerUid',
  );
  for (const field of disallowedFamilyFields) {
    if (field === 'members') continue;
    issues.push({
      level: 'warn',
      code: 'family-extra-field',
      message: `Family document has unsupported field "${field}"`,
    });
    actions.push({ type: 'delete-family-field', field });
  }

  if (adminSelection.uid) {
    const adminMember = members.find((member) => member.id === adminSelection.uid);
    if (!adminMember) {
      actions.push({
        type: 'create-admin-member',
        memberId: adminSelection.uid,
        data: {
          id: adminSelection.uid,
          role: 'ADMIN',
          displayName: 'Admin',
          roleLabel: 'Admin',
          avatarColor: '#2196F3',
        },
      });
    } else {
      const normalized = pickAllowedMemberFields(adminMember.data, adminSelection.uid);
      normalized.role = 'ADMIN';
      if (JSON.stringify(normalized) !== JSON.stringify(adminMember.data)
        || adminMember.data.id !== adminSelection.uid) {
        actions.push({
          type: 'normalize-member',
          memberId: adminSelection.uid,
          data: normalized,
        });
      }
    }

    for (const member of members) {
      if (member.id === adminSelection.uid) continue;
      if (member.data.role === 'ADMIN') {
        if (looksLikeFirebaseUid(member.id)) {
          issues.push({
            level: 'error',
            code: 'extra-admin-member',
            message: `Non-canonical ADMIN member remains at members/${member.id}. Resolve manually.`,
          });
        } else {
          issues.push({
            level: 'warn',
            code: 'legacy-admin-member',
            message: `Removing legacy ADMIN member at members/${member.id}`,
          });
          actions.push({ type: 'delete-member', memberId: member.id });
        }
        continue;
      }
      const normalized = pickAllowedMemberFields(member.data, member.id);
      if (JSON.stringify(normalized) !== JSON.stringify(member.data)
        || member.data.id !== member.id) {
        actions.push({
          type: 'normalize-member',
          memberId: member.id,
          data: normalized,
        });
      }
    }
  }

  const blocking = issues.some((issue) => issue.level === 'error');
  return { familyId, issues, actions, blocking, adminUid: adminSelection.uid };
}

async function initFirestore(options) {
  if (getApps().length > 0) {
    await Promise.all(getApps().map((app) => deleteApp(app)));
  }

  if (options.emulator) {
    process.env.FIRESTORE_EMULATOR_HOST ??= '127.0.0.1:8080';
  }

  const projectId = options.project ?? (options.emulator ? 'demo-familyhub' : null);
  if (!projectId) {
    throw new Error('Pass --project <id> for production runs, or use --emulator.');
  }

  initializeApp({ projectId });
  return getFirestore();
}

async function loadFamilies(db, familyId) {
  if (familyId) {
    const snapshot = await db.collection('families').doc(familyId).get();
    if (!snapshot.exists) return [];
    const membersSnapshot = await snapshot.ref.collection('members').get();
    return [{
      id: snapshot.id,
      data: snapshot.data() ?? {},
      members: membersSnapshot.docs.map((doc) => ({ id: doc.id, data: doc.data() ?? {} })),
    }];
  }

  const familiesSnapshot = await db.collection('families').get();
  const families = [];
  for (const familyDoc of familiesSnapshot.docs) {
    const membersSnapshot = await familyDoc.ref.collection('members').get();
    families.push({
      id: familyDoc.id,
      data: familyDoc.data() ?? {},
      members: membersSnapshot.docs.map((doc) => ({ id: doc.id, data: doc.data() ?? {} })),
    });
  }
  return families;
}

function printPlan(plan) {
  console.log(`\nFamily: ${plan.familyId}`);
  if (plan.adminUid) {
    console.log(`  ADMIN uid: ${plan.adminUid}`);
  }
  if (plan.issues.length === 0) {
    console.log('  Issues: none');
  } else {
    console.log('  Issues:');
    for (const issue of plan.issues) {
      console.log(`    [${issue.level}] ${issue.code}: ${issue.message}`);
    }
  }
  if (plan.actions.length === 0) {
    console.log('  Actions: none');
  } else {
    console.log('  Actions:');
    for (const action of plan.actions) {
      console.log(`    - ${action.type} ${JSON.stringify(action)}`);
    }
  }
  console.log(`  Ready: ${plan.blocking ? 'NO' : 'YES'}`);
}

async function applyPlan(db, plan, dryRun) {
  if (plan.blocking) {
    throw new Error(`Family ${plan.familyId} has blocking issues; fix manually or pass --admin-uid.`);
  }
  if (plan.actions.length === 0) {
    return { familyId: plan.familyId, writes: 0 };
  }

  const familyRef = db.collection('families').doc(plan.familyId);
  let batch = db.batch();
  let writes = 0;

  const commitBatch = async () => {
    if (writes === 0) return;
    if (!dryRun) await batch.commit();
    batch = db.batch();
    writes = 0;
  };

  for (const action of plan.actions) {
    if (writes >= 450) {
      await commitBatch();
    }

    switch (action.type) {
      case 'set-owner-uid':
        batch.set(familyRef, { ownerUid: action.ownerUid }, { merge: true });
        writes += 1;
        break;
      case 'delete-family-field':
        batch.set(familyRef, { [action.field]: FieldValue.delete() }, { merge: true });
        writes += 1;
        break;
      case 'normalize-member':
      case 'create-admin-member':
        batch.set(
          familyRef.collection('members').doc(action.memberId),
          action.data,
          { merge: false },
        );
        writes += 1;
        break;
      case 'delete-member':
        batch.delete(familyRef.collection('members').doc(action.memberId));
        writes += 1;
        break;
      default:
        throw new Error(`Unknown action type: ${action.type}`);
    }
  }

  await commitBatch();
  return { familyId: plan.familyId, writes: plan.actions.length, dryRun };
}

async function main() {
  const { positionals, values } = parseArgs({
    allowPositionals: true,
    options: {
      project: { type: 'string' },
      emulator: { type: 'boolean', default: false },
      family: { type: 'string' },
      'admin-uid': { type: 'string' },
      'dry-run': { type: 'boolean', default: false },
      help: { type: 'boolean', short: 'h', default: false },
    },
  });

  if (values.help) {
    console.log(HELP);
    return;
  }

  const mode = positionals[0];
  if (mode !== 'audit' && mode !== 'apply') {
    console.error(HELP);
    process.exitCode = 1;
    return;
  }

  const db = await initFirestore(values);
  const families = await loadFamilies(db, values.family ?? null);
  const plans = families.map((family) => buildFamilyPlan(
    family.id,
    family.data,
    family.members,
    { adminUid: values['admin-uid'] ?? null },
  ));

  if (plans.length === 0) {
    console.log(values.family ? `Family not found: ${values.family}` : 'No families found.');
    return;
  }

  let readyCount = 0;
  let blockedCount = 0;

  for (const plan of plans) {
    printPlan(plan);
    if (plan.blocking) blockedCount += 1;
    else readyCount += 1;
  }

  console.log(`\nSummary: ${plans.length} families, ${readyCount} ready, ${blockedCount} blocked.`);

  if (mode === 'apply') {
    if (blockedCount > 0) {
      process.exitCode = 1;
      console.error('\nApply aborted because at least one family is blocked.');
      return;
    }

    console.log(values['dry-run'] ? '\nDry-run apply:' : '\nApplying migration:');
    for (const plan of plans) {
      const result = await applyPlan(db, plan, values['dry-run'] ?? false);
      console.log(`  ${result.familyId}: ${result.writes} write action(s)${result.dryRun ? ' (dry-run)' : ''}`);
    }
  } else if (blockedCount > 0) {
    process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
