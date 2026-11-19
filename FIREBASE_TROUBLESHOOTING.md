# Диагностика проблемы "Missing or insufficient permissions"

## Проблема
Ошибка `PERMISSION_DENIED: Missing or insufficient permissions` при попытке создать семью.

## Шаги диагностики

### 1. Проверьте авторизацию пользователя

В логах должно быть:
```
publishSelfMember: id=..., role=ADMIN
syncNow: Starting sync for accountId=..., userId=...
```

Если этих логов нет или `userId=null`, значит пользователь не авторизован.

**Решение:**
- Убедитесь, что пользователь залогинен через Firebase Auth
- Проверьте в Firebase Console → Authentication → Users, что пользователь существует

### 2. Проверьте правила Firestore

1. Откройте Firebase Console → Firestore Database → Rules
2. Убедитесь, что правила опубликованы (кнопка "Publish")
3. Проверьте, что правила содержат:
   ```javascript
   allow read, write: if isAuth();
   ```
   для документа семьи

### 3. Проверьте SHA-1/SHA-256 сертификаты

Для debug-сборки нужно добавить SHA-1 и SHA-256 в Firebase Console.

**Получить SHA-1 для debug:**
```bash
cd android
./gradlew signingReport
```

Или через Android Studio:
- Gradle → app → Tasks → android → signingReport

**Добавить в Firebase:**
1. Firebase Console → Project Settings → Your apps → Android app
2. Добавьте SHA-1 и SHA-256 из вывода команды
3. Скачайте обновлённый `google-services.json`
4. Замените файл в проекте

### 4. Проверьте google-services.json

Убедитесь, что файл `app/google-services.json`:
- Существует
- Содержит правильный `package_name` (должен совпадать с `applicationId` в `build.gradle.kts`)
- Не устарел (дата создания)

### 5. Проверьте логи приложения

Ищите в Logcat:
- `uploadSnapshot: accountId=..., isMemberWriteAllowed=..., userId=...`
- `syncCollection: ..., existing ids: ...`
- `Batch commit failed: code=PERMISSION_DENIED`

Если видите `userId=null` или ошибку авторизации - проблема в Firebase Auth.

### 6. Отладка правил

Не используйте открытые правила `allow read, write: if request.auth != null` даже временно.
Для локальной проверки запускайте `firestore-rules-tests/` против [`firestore.rules`](firestore.rules) и эмулятора Firestore.

```bash
cd firestore-rules-tests
npm test
```

### 7. Проверьте, что правила применены

После публикации правил:
1. Подождите 1-2 минуты (правила кэшируются)
2. Перезапустите приложение
3. Попробуйте снова создать семью

## Частые причины ошибки

1. **Пользователь не авторизован** - проверьте `FirebaseAuth.currentUser`
2. **SHA-1/SHA-256 не добавлены** - добавьте в Firebase Console
3. **Правила не опубликованы** - нажмите "Publish" в Firebase Console
4. **Неправильный package_name** - проверьте в `google-services.json` и `build.gradle.kts`
5. **Правила кэшируются** - подождите 1-2 минуты после публикации

## Что делать дальше

1. Проверьте логи приложения (ищите `uploadSnapshot`, `syncNow`)
2. Проверьте авторизацию (должен быть `userId` в логах)
3. Проверьте правила Firestore (должны быть опубликованы)
4. Проверьте SHA-1/SHA-256 (должны быть добавлены в Firebase Console)

Если проблема остаётся, пришлите:
- Логи из Logcat (фильтр: `FirestoreBackendApi`, `SyncRepository`)
- Скриншот правил Firestore из Firebase Console
- Информацию о том, авторизован ли пользователь
