// Repro: admin upload против текущих firestore.rules.
// Эмулирует uploadSnapshot клиента: Set полных документов (как Export-модели).
import { readFile } from 'node:fs/promises';
import {
  assertSucceeds,
  assertFails,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import { doc, setDoc, deleteDoc } from 'firebase/firestore';

const member = (id, role) => ({
  id, role,
  displayName: id,
  roleLabel: id,
  avatarColor: '#2196F3',
});

const fullTask = (id, overrides = {}) => ({
  id, title: `Task ${id}`, description: null,
  createdByUserId: 'alice', assignedToUserId: 'alice',
  dueDate: 1, dueDateHasTime: false, reminderMode: 'NONE', reminderMinutesOfDay: null,
  repeatRule: '', status: 'TODO', createdAt: 1, orderIndex: 0, taskType: 'ROUTINE',
  private: false, priority: 'MEDIUM', categoryId: null, rewardPoints: null,
  requiresApproval: false, attachments: [], comments: [], completionCycle: 0,
  ...overrides,
});

const fullShoppingList = (id) => ({
  id, title: 'Groceries', colorHex: '#2196F3', iconName: 'cart',
  createdAt: 1, archived: false, archivedAt: null, templateKey: null, participantIds: [],
});

const fullShoppingCategory = (id) => ({
  id, listId: 'list-1', title: 'Fruits', colorHex: '#4CAF50', iconName: 'apple', sortOrder: 0,
});

const fullShoppingItem = (id, overrides = {}) => ({
  id, listId: 'list-1', title: 'Apples', createdByUserId: 'alice', bought: false,
  createdAt: 1, categoryId: null, quantity: 2, quantityUnit: 'kg', note: null,
  private: false, boughtAt: null,
  ...overrides,
});

const fullShoppingActivity = (id) => ({
  id, listId: 'list-1', itemId: null, actorId: 'alice', actionType: 'ADD_ITEM',
  occurredAt: 1, payload: { itemId: 'item-1', title: 'Apples' },
});

const fullEvent = (id, overrides = {}) => ({
  id, title: 'Birthday', startDateTime: 1, createdByUserId: 'alice',
  durationMinutes: 60, recurrence: 'NONE', reminderMinutesBefore: null,
  ...overrides,
});

const fullScore = (id) => ({ userId: 'alice', weekStartDate: 1, score: 5, updatedAt: 1 });

const fullTaskCompletion = (id, overrides = {}) => ({
  id, taskId: 'task-1', userId: 'alice', taskType: 'ROUTINE', completionTime: 1,
  weekStartDate: 1, basePoints: 10, bonusPoints: 0, penaltyPoints: 0, totalPoints: 10,
  reversedAt: null, reversedByUserId: null, updatedAt: 1, generatedRepeatTaskId: null,
  completionCycle: 0,
  ...overrides,
});

const fullAchievement = () => ({ userId: 'alice', type: 'FIRST_TASK', earnedAt: 1 });

const fullWallet = () => ({ userId: 'alice', balance: 10, updatedAt: 1 });

const fullPenalty = () => ({
  id: 'penalty-1', userId: 'alice', issuedByUserId: 'alice', amount: -10, comment: null,
  createdAt: 1, canceled: false, canceledAt: null, originType: null, originId: null,
});

const fullReward = (id) => ({
  id, title: 'Ice cream', description: null, cost: 50, createdByUserId: 'alice',
  active: true, createdAt: 1,
});

const fullRewardRequest = (id) => ({
  id, rewardId: 'reward-1', rewardTitle: 'Ice cream', rewardCost: 50,
  requesterUserId: 'alice', note: null, status: 'PENDING', requestedAt: 1,
  processedAt: null, processedByUserId: null,
});

const fullTaskRotation = () => ({
  taskId: 'task-1', memberIds: ['alice'], currentIndex: 0, frequency: 'WEEKLY',
  startDateTime: 1, nextRotationAt: 2, lastRotationAt: null,
});

const fullHouseRule = (id) => ({
  id, title: 'Rule', description: null, createdByUserId: 'alice', createdAt: 1, updatedAt: 1,
});

const fullTaskTemplate = (id) => ({
  id, title: 'Template', description: null, repeatRule: '', defaultAssigneeId: null,
  private: false, taskType: 'ROUTINE', createdByUserId: 'alice', createdAt: 1,
  categoryId: null, priority: 'MEDIUM',
});

const fullTaskCategory = (id) => ({
  id, name: 'Home', color: 0, createdAt: 1, updatedAt: 1,
});

const fullAppreciation = (id, overrides = {}) => ({
  id, senderId: 'alice', recipientId: 'carol', message: 'Thanks!', createdAt: 1,
  ...overrides,
});

const fullFamilyGoal = (id) => ({
  id, title: 'Goal', description: null, targetPoints: 10, currentPoints: 0, deadlineAt: null,
  status: 'ACTIVE', createdByUserId: 'alice', createdAt: 1, updatedAt: 1, personal: false,
});

const fullGoalStage = (id, goalId = 'goal-1') => ({
  id, goalId, title: 'Stage', description: null, position: 0,
});

const fullGoalStep = (id, goalId = 'goal-1') => ({
  id, goalId, stageId: 'stage-1', title: 'Step', completed: false, position: 0,
});

const fullEmergencyContact = (id) => ({
  id, name: 'Mom', phoneNumber: '+1000', description: null, createdAt: 1,
});

const fullRecipe = (id) => ({
  id, title: 'Pasta', description: null, servings: 4, prepTimeMinutes: 10, cookTimeMinutes: 20,
  difficulty: 'MEDIUM', category: 'MAIN_DISH', isFavorite: false, imageUrl: null,
  notes: null, tags: [], createdByUserId: 'alice', createdAt: 1, updatedAt: 1,
  ingredients: [], steps: [],
});

let env;

const ops = [];

async function tryOp(label, fn) {
  try {
    await assertSucceeds(fn());
    ops.push(`PASS  ${label}`);
  } catch (e) {
    ops.push(`FAIL  ${label} :: ${String(e.message).split('\n')[0].slice(0, 160)}`);
  }
}

async function main() {
  env = await initializeTestEnvironment({
    projectId: 'demo-repro',
    firestore: { rules: await readFile(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, 'families/family-a'), { ownerUid: 'alice' });
    await setDoc(doc(db, 'families/family-a/members/alice'), member('alice', 'ADMIN'));
    await setDoc(doc(db, 'families/family-a/members/carol'), member('carol', 'CHILD'));
  });

  const alice = env.authenticatedContext('alice').firestore();
  const f = (coll, id) => doc(alice, `families/family-a/${coll}/${id}`);

  // ==== Полный апдейт существующих документов (клиент шлёт Set полного документа) ====
  await env.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    // Документы, которые потом будут ОБНОВЛЯТЬСЯ (созданы "старым клиентом")
    await setDoc(doc(db, 'families/family-a/events/event-legacy'), fullEvent('event-legacy'));
    await setDoc(doc(db, 'families/family-a/shopping_items/item-legacy'), fullShoppingItem('item-legacy'));
    await setDoc(doc(db, 'families/family-a/shopping_lists/list-1'), fullShoppingList('list-1'));
    await setDoc(doc(db, 'families/family-a/shopping_categories/cat-1'), fullShoppingCategory('cat-1'));
    await setDoc(doc(db, 'families/family-a/shopping_activity/act-1'), fullShoppingActivity('act-1'));
    await setDoc(doc(db, 'families/family-a/appreciations/appr-1'), fullAppreciation('appr-1'));
    await setDoc(doc(db, 'families/family-a/recipes/recipe-1'), fullRecipe('recipe-1'));
    await setDoc(doc(db, 'families/family-a/family_goals/goal-1'), fullFamilyGoal('goal-1'));
    await setDoc(doc(db, 'families/family-a/family_goal_stages/stage-1'), fullGoalStage('stage-1'));
    await setDoc(doc(db, 'families/family-a/family_goal_steps/step-1'), fullGoalStep('step-1'));
    await setDoc(doc(db, 'families/family-a/task_completions/completion-1'), fullTaskCompletion('completion-1'));
    await setDoc(doc(db, 'families/family-a/tasks/task-1'), fullTask('task-1'));
    await setDoc(doc(db, 'families/family-a/rewards/reward-1'), fullReward('reward-1'));
    await setDoc(doc(db, 'families/family-a/emergency_contacts/ec-1'), fullEmergencyContact('ec-1'));
    await setDoc(doc(db, 'families/family-a/house_rules/hr-1'), fullHouseRule('hr-1'));
    await setDoc(doc(db, 'families/family-a/task_templates/tt-1'), fullTaskTemplate('tt-1'));
    await setDoc(doc(db, 'families/family-a/task_categories/tc-1'), fullTaskCategory('tc-1'));
    await setDoc(doc(db, 'families/family-a/task_rotations/rot-1'), fullTaskRotation());
    await setDoc(doc(db, 'families/family-a/penalties/penalty-1'), fullPenalty());
    await setDoc(doc(db, 'families/family-a/scores/score-1'), fullScore('score-1'));
    await setDoc(doc(db, 'families/family-a/wallets/wallet-1'), fullWallet());
    await setDoc(doc(db, 'families/family-a/reward_requests/rr-1'), fullRewardRequest('rr-1'));
    await setDoc(doc(db, 'families/family-a/achievements/ach-1'), fullAchievement());
  });

  // ==== CREATE новых документов (полный набор полей как у клиента) ====
  await tryOp('create shopping_list', () => setDoc(f('shopping_lists', 'list-2'), fullShoppingList('list-2')));
  await tryOp('create shopping_category', () => setDoc(f('shopping_categories', 'cat-2'), fullShoppingCategory('cat-2')));
  await tryOp('create shopping_item', () => setDoc(f('shopping_items', 'item-2'), fullShoppingItem('item-2')));
  await tryOp('create shopping_activity', () => setDoc(f('shopping_activity', 'act-2'), fullShoppingActivity('act-2')));
  await tryOp('create event', () => setDoc(f('events', 'event-2'), fullEvent('event-2')));
  await tryOp('create appreciation', () => setDoc(f('appreciations', 'appr-2'), fullAppreciation('appr-2')));
  await tryOp('create recipe', () => setDoc(f('recipes', 'recipe-2'), fullRecipe('recipe-2')));
  await tryOp('create goal stage', () => setDoc(f('family_goal_stages', 'stage-2'), fullGoalStage('stage-2')));
  await tryOp('create goal step', () => setDoc(f('family_goal_steps', 'step-2'), fullGoalStep('step-2')));
  await tryOp('create task_completion', () => setDoc(f('task_completions', 'completion-2'), fullTaskCompletion('completion-2')));
  await tryOp('create task', () => setDoc(f('tasks', 'task-2'), fullTask('task-2')));
  await tryOp('create reward_request', () => setDoc(f('reward_requests', 'rr-2'), fullRewardRequest('rr-2')));
  await tryOp('create family_goal', () => setDoc(f('family_goals', 'goal-2'), fullFamilyGoal('goal-2')));
  await tryOp('create emergency_contact', () => setDoc(f('emergency_contacts', 'ec-2'), fullEmergencyContact('ec-2')));
  await tryOp('create house_rule', () => setDoc(f('house_rules', 'hr-2'), fullHouseRule('hr-2')));
  await tryOp('create task_template', () => setDoc(f('task_templates', 'tt-2'), fullTaskTemplate('tt-2')));
  await tryOp('create task_category', () => setDoc(f('task_categories', 'tc-2'), fullTaskCategory('tc-2')));
  await tryOp('create score', () => setDoc(f('scores', 'score-2'), fullScore('score-2')));
  await tryOp('create wallet', () => setDoc(f('wallets', 'wallet-2'), fullWallet()));

  // ==== UPDATE существующих (Set полного документа = как uploadSnapshot) ====
  await tryOp('update event (full doc)', () => setDoc(f('events', 'event-legacy'), fullEvent('event-legacy', { title: 'Edited' })));
  await tryOp('update shopping_item (full doc, bought)', () => setDoc(f('shopping_items', 'item-legacy'), fullShoppingItem('item-legacy', { bought: true, boughtAt: 2 })));
  await tryOp('update shopping_list', () => setDoc(f('shopping_lists', 'list-1'), fullShoppingList('list-1')));
  await tryOp('update shopping_category', () => setDoc(f('shopping_categories', 'cat-1'), fullShoppingCategory('cat-1')));
  await tryOp('update shopping_activity', () => setDoc(f('shopping_activity', 'act-1'), fullShoppingActivity('act-1')));
  await tryOp('update appreciation', () => setDoc(f('appreciations', 'appr-1'), fullAppreciation('appr-1')));
  await tryOp('update recipe', () => setDoc(f('recipes', 'recipe-1'), fullRecipe('recipe-1')));
  await tryOp('update goal stage', () => setDoc(f('family_goal_stages', 'stage-1'), fullGoalStage('stage-1', 'goal-1')));
  await tryOp('update goal step (completed)', () => setDoc(f('family_goal_steps', 'step-1'), fullGoalStep('step-1', 'goal-1')));
  await tryOp('update task_completion (reversal)', () => setDoc(f('task_completions', 'completion-1'), fullTaskCompletion('completion-1', { reversedAt: 5, reversedByUserId: 'alice', updatedAt: 5 })));
  await tryOp('update task', () => setDoc(f('tasks', 'task-1'), fullTask('task-1', { status: 'DONE' })));
  await tryOp('update house_rule', () => setDoc(f('house_rules', 'hr-1'), fullHouseRule('hr-1')));
  await tryOp('update task_template', () => setDoc(f('task_templates', 'tt-1'), fullTaskTemplate('tt-1')));
  await tryOp('update task_category', () => setDoc(f('task_categories', 'tc-1'), fullTaskCategory('tc-1')));
  await tryOp('update family_goal', () => setDoc(f('family_goals', 'goal-1'), fullFamilyGoal('goal-1')));
  await tryOp('update emergency_contact', () => setDoc(f('emergency_contacts', 'ec-1'), fullEmergencyContact('ec-1')));

  // ==== Сценарий пользователя: approve/reject/delete/edit задач (создание completion) ====
  await env.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, 'families/family-a/tasks/task-approve'), fullTask('task-approve', {
      createdByUserId: 'carol', assignedToUserId: 'carol',
      status: 'WAITING_FOR_APPROVAL', requiresApproval: true,
    }));
    await setDoc(doc(db, 'families/family-a/tasks/task-edit'), fullTask('task-edit'));
    await setDoc(doc(db, 'families/family-a/tasks/task-wait-edit'), fullTask('task-wait-edit', {
      createdByUserId: 'carol', assignedToUserId: 'carol',
      status: 'WAITING_FOR_APPROVAL', requiresApproval: true,
    }));
  });

  // approve: task -> DONE (полный Set)
  await tryOp('approve task (Set DONE)', () => setDoc(f('tasks', 'task-approve'), fullTask('task-approve', {
    createdByUserId: 'carol', assignedToUserId: 'carol',
    status: 'DONE', requiresApproval: false,
  })));
  // approve создаёт completion (полный Set)
  await tryOp('approve creates task_completion', () => setDoc(f('task_completions', 'completion-approve'), fullTaskCompletion('completion-approve', {
    taskId: 'task-approve', userId: 'carol', basePoints: 10, totalPoints: 10,
  })));
  // reject: task -> TODO (полный Set)
  await tryOp('reject task (Set TODO)', () => setDoc(f('tasks', 'task-approve'), fullTask('task-approve', {
    createdByUserId: 'carol', assignedToUserId: 'carol',
    status: 'TODO', requiresApproval: true,
  })));
  // Сценарий пользователя: редактирование WAITING + напоминания, потом reject
  await tryOp('edit WAITING task (change date + reminder)', () => setDoc(f('tasks', 'task-wait-edit'), fullTask('task-wait-edit', {
    createdByUserId: 'carol', assignedToUserId: 'carol',
    status: 'WAITING_FOR_APPROVAL', requiresApproval: true,
    dueDate: 9999, dueDateHasTime: true,
    reminderMode: 'AT_TIME', reminderMinutesOfDay: 480,
  })));
  await tryOp('reject after editing WAITING task', () => setDoc(f('tasks', 'task-wait-edit'), fullTask('task-wait-edit', {
    createdByUserId: 'carol', assignedToUserId: 'carol',
    status: 'TODO', requiresApproval: true,
    dueDate: 9999, dueDateHasTime: true,
    reminderMode: 'AT_TIME', reminderMinutesOfDay: 480,
  })));
  // delete task
  await tryOp('delete task', () => deleteDoc(f('tasks', 'task-approve')));
  // edit task (полный Set)
  await tryOp('edit task (Set)', () => setDoc(f('tasks', 'task-edit'), fullTask('task-edit', { title: 'Edited title' })));

  // ==== Крайние случаи: легаси-данные ====
  await tryOp('CREATE event with empty createdByUserId (built-in/legacy)', () => setDoc(f('events', 'event-legacy-created'), fullEvent('event-legacy-created', { createdByUserId: '' })));
  await tryOp('CREATE event длительностью 30 дней (multi-day)', () => setDoc(f('events', 'event-long'), fullEvent('event-long', { durationMinutes: 43200 })));
  await tryOp('CREATE shopping_item с пустым listId (легаси)', () => setDoc(f('shopping_items', 'item-nolist'), fullShoppingItem('item-nolist', { listId: '' })));
  await tryOp('CREATE shopping_item с длинной заметкой (5000)', () => setDoc(f('shopping_items', 'item-longnote'), fullShoppingItem('item-longnote', { note: 'x'.repeat(5000) })));
  await tryOp('CREATE recipe with empty createdByUserId (built-in)', () => setDoc(f('recipes', 'recipe-builtin'), fullRecipe('recipe-builtin')));
  await tryOp('UPDATE completion: merge меняет totalPoints (полный Set)', () => setDoc(f('task_completions', 'completion-1'), fullTaskCompletion('completion-1', { totalPoints: 12, updatedAt: 2 })));
  await tryOp('CREATE event с ЛИШНИМ полем (дрейф модели клиента)', () => setDoc(f('events', 'event-drift'), { ...fullEvent('event-drift'), description: 'новое поле клиента' }));
  await tryOp('CREATE task_completion с ЛИШНИМ полем (дрейф модели клиента)', () => setDoc(f('task_completions', 'completion-drift'), { ...fullTaskCompletion('completion-drift'), bonusLabel: 'новое поле' }));
  await tryOp('UPDATE shopping_item с ЛИШНИМ полем', () => setDoc(f('shopping_items', 'item-legacy'), { ...fullShoppingItem('item-legacy', { bought: true }), updatedAt: 9 }));
  await tryOp('UPDATE legacy completion keeping null cycle (no cycle key in remote)', () =>
    env.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, 'families/family-a/task_completions/completion-legacy-null'), {
        id: 'completion-legacy-null', taskId: 'task-1', userId: 'alice', taskType: 'ROUTINE',
        completionTime: 1, weekStartDate: 1, basePoints: 10, bonusPoints: 0, penaltyPoints: 0,
        totalPoints: 10, updatedAt: 1,
      });
    }).then(() => setDoc(f('task_completions', 'completion-legacy-null'), fullTaskCompletion('completion-legacy-null', { completionCycle: null }))));

  console.log('\n===== РЕЗУЛЬТАТЫ =====');
  ops.forEach((line) => console.log(line));
  const fails = ops.filter((l) => l.startsWith('FAIL'));
  console.log(`\nPASS: ${ops.length - fails.length}  FAIL: ${fails.length}`);
  await env.cleanup();
  process.exit(fails.length === 0 ? 0 : 1);
}

main().catch((e) => { console.error(e); process.exit(2); });
