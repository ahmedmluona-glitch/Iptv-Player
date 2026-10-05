# دليل تثبيت حزمة Mluona IPTV (.ipk) لشاشات LG webOS Smart TV

تم بناء حزمة التثبيت الرسمية `com.mluona.iptv_1.0.1_all.ipk` باستخدام أداة webOS الرسمية `ares-package` لتكون متوافقة 100% مع كافة شاشات LG Smart TV العاملة بنظام webOS (webOS 4.0 فأحدث، Chromium 53+).

---

## مواصفات الحزمة
* **معرف التطبيق (App ID):** `com.mluona.iptv`
* **الإصدار:** `1.0.1`
* **الأداة المستخدمة في البناء:** `ares-package` (@webos-tools/cli)
* **الدقة المدعومة:** Full HD 1080p (1920x1080) و 4K
* **صيغ البث المدعومة:** HLS (.m3u8), MPEG-TS (.ts), MP4

---

## طرق التثبيت على شاشة LG webOS

### الطريقة الأولى: عبر برنامج webOS Dev Manager (الأسهل والأسرع)
1. حمّل برنامج [webOS Dev Manager](https://github.com/webosbrew/dev-manager/releases) على حاسوبك (متوفر لـ Windows و Mac و Linux).
2. على شاشة LG:
   * حمّل تطبيق **Developer Mode** من متجر تطبيقات LG.
   * افتح التطبيق وسجّل الدخول بحساب LG مجاني.
   * فعّل خيار **Enable Developer Mode** وخيار **Key Server**.
3. على الحاسوب:
   * افتح برنامج **webOS Dev Manager** وانقر على **Add Device**.
   * أدخل عنوان IP الخاص بالشاشة، وكود المرور (Passphrase) الظاهر على الشاشة.
   * بعد الاتصال، اسحب ملف `com.mluona.iptv_1.0.1_all.ipk` وأفلته داخل نافذة البرنامج (أو اضغط Install).
   * سيتم تثبيت التطبيق فوراً وظهوره في قائمة تطبيقات الشاشة.

---

### الطريقة الثانية: عبر سطر الأوامر (webOS CLI)
إذا كان لديك `@webos-tools/cli` مثبتاً:
```bash
# 1. إعداد الشاشة (لمرة واحدة)
ares-setup-device

# 2. تثبيت الحزمة على الشاشة مباشرة
ares-install dist/com.mluona.iptv_1.0.1_all.ipk -d <TV_NAME>

# 3. تشغيل التطبيق على الشاشة
ares-launch com.mluona.iptv -d <TV_NAME>
```

---

## الميزات والحلول التقنية المطبقة في تطبيق webOS:
1. **دعم كامل لريموت LG السحري (Magic Remote & D-pad):**
   * دعم صريح لزر العودة بالريموت (`KeyCode 461`) لمنع إغلاق التطبيق بطريق الخطأ.
   * تنقل كامل بأسهم الاتجاهات (Up/Down/Left/Right) وزر الإدخال (OK/Enter).
2. **مشغل مدمج Hls.js محلي دون الاعتماد على الإنترنت لتحميل السكربتات:**
   * تم تضمين مكتبة `hls.min.js` محلياً داخل الحزمة لتفادي مشاكل الحظر أو انقطاع الإنترنت.
3. **حفظ الجلسة تلقائياً (Persistence):**
   * يتم حفظ بيانات السيرفر (Xtream Codes أو M3U) في `localStorage` بحيث يفتح التطبيق مباشرة على القنوات عند تشغيل التلفاز.


> ملاحظة: webOS 3.x (Chromium 38) غير مدعوم. ملف الـ IPK يُبنى عبر GitHub Actions (تبويب Actions → Artifacts) أو `./build_ipk.sh`.
