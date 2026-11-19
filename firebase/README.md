# Firebase Cloud Functions для FamilyHub

**Статус: не используется.** Проект на тарифе Spark (без Blaze), поэтому Cloud Functions не деплоятся.

Исходники в `functions/` оставлены как заготовка на будущее. Пока они не задеплоены:

- нет fan-out push на другие устройства;
- клиент **не** пишет в `push_events` и **не** подписывается на FCM-топики;
- уведомления о покупках показываются **локально** на устройстве, которое получило изменения через sync;
- история покупок синхронизируется через Firestore как обычные данные.

## Если позже включите Blaze

1. Задеплойте Functions: `cd firebase/functions && npm install && npm run deploy`
2. Верните в приложении запись в `push_events` и подписку на топик `family_{familyId}` (см. git history).
3. Обработчик `fanOutShoppingActivityPush` рассылает FCM после создания документа в `families/{familyId}/push_events`.

## Структура заготовки

- `functions/src/index.ts` — `fanOutShoppingActivityPush`
- `functions/package.json` — скрипты `build`, `deploy`
