# دليل بناء وتوقيع تطبيق Mluona IPTV عبر GitHub Actions

تم إعداد ملف الـ Workflow بالكامل في:
`.github/workflows/build-apk.yml`

---

## 1. المميزات التي تم تضمينها لمعالجة المشاكل الشائعة:
1. **استخدام Node.js 22**:
   - تم تثبيت وإعداد `node-version: 22` عبر `actions/setup-node@v4`.
2. **حل مشكلة عدم تثبيت التطبيق (Unsigned APK / "App not installed")**:
   - أجهزة Android و Android TV الحديثة ترفض تثبيت الـ APK غير الموقع أو الموقع بدون توقيع v2/v3.
   - يقوم الـ Workflow بتوليد Keystore معتمد تلقائياً وتوقيع التطبيق بـ `apksigner` مع تفعيل **v1 و v2 و v3 Schemes** والتحقق من التوقيع `verify --verbose` لضمان التثبيت السلس على كافة الهواتف والشاشات دون مشاكل.
   - يدعم الـ Workflow أيضاً إمكانية إضافة الـ Keystore الخاص بك كـ GitHub Secret إذا أردت.
3. **معالجة عدم ظهور الشعار (App Icon & TV Banner)**:
   - تم إعداد وتعيين `android:icon="@mipmap/ic_launcher"` و `android:roundIcon="@mipmap/ic_launcher_round"`.
   - إضافة شعار البانر التلفزيوني الرسمي `android:banner="@drawable/banner"` داخل الـ Manifest (لشاشات Android TV / Leanback Launcher).
4. **تضمين Gradle Wrapper (`gradlew`)**:
   - تم توليد ملفات `gradlew` و `gradle-wrapper.jar` في المستودع وإعطائها صلاحية التشغيل `chmod +x gradlew` لتجنب خطأ `gradlew: command not found`.

---

## 2. الأوامر لرفع المشروع وتشغيل الـ Action من GitHub:

إذا كنت ترفع المشروع لأول مرة إلى GitHub:

```bash
# 1. تهيئة المستودع وإضافة كل الملفات (مع ملفات git workflow و gradlew)
git init
git add .
git commit -m "Add GitHub Actions workflow with Node 22 and signed APK"

# 2. ربط المستودع بالـ Remote (استبدل الرابط برابط مستودعك على GitHub)
git remote add origin https://github.com/USERNAME/mluona-iptv.git
git branch -M main
git push -u origin main
```

---

## 3. كيفية تحميل الـ APK الجاهز من GitHub:
1. افتح مستودعك على **GitHub**.
2. اذهب إلى تبويب **Actions**.
3. ستجد الـ Workflow باسم **Build & Sign Android APK** يعمل تلقائياً عند كل push، أو يمكنك تشغيله يدوياً بالضغط على **Run workflow**.
4. عند اكتمال البناء باللون الأخضر، اضغط على العملية وستجد في الأسفل قسم **Artifacts** يحتوي على:
   - `mluona-iptv-apk`
5. حمّل الملف وفك الضغط عنه وستجد `mluona-iptv-signed.apk` جاهزاً للتثبيت الفوري على أي شاشة أو هاتف.
