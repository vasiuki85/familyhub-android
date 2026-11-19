// Quick test: can child update parent's items?
const { initializeTestEnvironment } = require('@firebase/rules-unit-testing');
const { doc, setDoc } = require('firebase/firestore');
const fs = require('fs');

(async () => {
  const rules = fs.readFileSync('../firestore.rules', 'utf8');
  const env = await initializeTestEnvironment({ projectId: 'demo-repro', firestore: { rules } });
  
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'families/family-a/members/alice'), { id: 'alice', role: 'ADMIN' });
    await setDoc(doc(db, 'families/family-a/members/carol'), { id: 'carol', role: 'CHILD' });
    await setDoc(doc(db, 'families/family-a/shopping_items/item-parent'), { id: 'item-parent', listId: 'list-a', title: 'Parent item', createdByUserId: 'alice', bought: false, createdAt: 1 });
    await setDoc(doc(db, 'families/family-a/events/event-parent'), { id: 'event-parent', title: 'Parent event', startDateTime: 1, createdByUserId: 'alice', durationMinutes: 60 });
  });
  
  const carol = env.authenticatedContext('carol').firestore();
  
  try {
    await setDoc(doc(carol, 'families/family-a/shopping_items/item-parent'), { id: 'item-parent', listId: 'list-a', title: 'CHANGED', createdByUserId: 'alice', bought: false, createdAt: 1 });
    console.log('shop UPDATE: PASS');
  } catch(e) { console.log('shop UPDATE: DENIED'); }
  
  try {
    await setDoc(doc(carol, 'families/family-a/events/event-parent'), { id: 'event-parent', title: 'CHANGED', startDateTime: 1, createdByUserId: 'alice', durationMinutes: 60 });
    console.log('event UPDATE: PASS');
  } catch(e) { console.log('event UPDATE: DENIED'); }

  await env.cleanup();
})();
