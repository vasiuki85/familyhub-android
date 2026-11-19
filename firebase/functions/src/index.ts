import * as admin from 'firebase-admin';
import * as functions from 'firebase-functions';

type ShoppingActivityPushEvent = {
  readonly type?: string;
  readonly title?: string;
  readonly message?: string;
  readonly deviceId?: string;
  readonly activityId?: string;
  readonly listId?: string;
  readonly itemId?: string;
  readonly actorId?: string;
  readonly occurredAt?: number;
  readonly payload?: unknown;
};

const SHOPPING_ACTIVITY_TYPE = 'shopping_activity';
const SYNC_TYPE = 'sync';
const familiesCollectionPath = 'families/{familyId}/push_events/{eventId}';

admin.initializeApp();

const sanitizeTopicSegment = (segment: string): string =>
  segment.replace(/[^a-zA-Z0-9-_.~%]/g, '_');

const familyTopic = (familyId: string): string => `family_${sanitizeTopicSegment(familyId)}`;

const buildMessageData = (event: ShoppingActivityPushEvent): Record<string, string> => {
  const data: Record<string, string> = {
    type: SHOPPING_ACTIVITY_TYPE,
  };

  if (event.title) {
    data.title = event.title;
  }
  if (event.message) {
    data.message = event.message;
  }
  if (event.deviceId) {
    data.deviceId = event.deviceId;
  }
  if (event.activityId) {
    data.activityId = event.activityId;
  }
  if (event.listId) {
    data.listId = event.listId;
  }
  if (event.itemId) {
    data.itemId = event.itemId;
  }
  if (event.actorId) {
    data.actorId = event.actorId;
  }
  if (typeof event.occurredAt === 'number') {
    data.occurredAt = event.occurredAt.toString();
  }
  if (event.payload !== undefined) {
    data.payload = JSON.stringify(event.payload);
  }

  return data;
};

export const fanOutShoppingActivityPush = functions.firestore
  .document(familiesCollectionPath)
  .onCreate(async (snapshot, context) => {
    const event = snapshot.data() as ShoppingActivityPushEvent | undefined;
    if (!event || (event.type !== SHOPPING_ACTIVITY_TYPE && event.type !== SYNC_TYPE)) {
      return;
    }

    const topic = familyTopic(String(context.params.familyId));

    if (event.type === SHOPPING_ACTIVITY_TYPE) {
      const title = event.title?.trim();
      const message = event.message?.trim();
      if (!title || !message) {
        functions.logger.warn('Skip shopping push without title or message', {
          familyId: context.params.familyId,
          eventId: context.params.eventId,
        });
        await snapshot.ref.delete();
        return;
      }

      const data = buildMessageData(event);
      data.title = title;
      data.message = message;

      try {
        await admin.messaging().send({
          topic,
          data,
        });
        await snapshot.ref.delete();
        functions.logger.info('Shopping activity push delivered', {
          familyId: context.params.familyId,
          eventId: context.params.eventId,
        });
      } catch (error) {
        functions.logger.error('Failed to fan out shopping push', error, {
          familyId: context.params.familyId,
          eventId: context.params.eventId,
        });
        throw error;
      }
      return;
    }

    if (event.type === SYNC_TYPE) {
      const data: Record<string, string> = {
        type: SYNC_TYPE,
      };
      if (event.deviceId) {
        data.deviceId = event.deviceId;
      }
      try {
        await admin.messaging().send({
          topic,
          data,
        });
        await snapshot.ref.delete();
        functions.logger.info('Sync ping delivered', {
          familyId: context.params.familyId,
          eventId: context.params.eventId,
        });
      } catch (error) {
        functions.logger.error('Failed to fan out sync ping', error, {
          familyId: context.params.familyId,
          eventId: context.params.eventId,
        });
        throw error;
      }
    }
  });
