# Firestore kuralları testi

Emülatörle çalışır (Java gerekir):

    npm i firebase-tools @firebase/rules-unit-testing firebase
    npx firebase emulators:exec --config ../firebase.json --only firestore --project demo-rules "node rules.test.mjs"

Başarılı çıktı: `42 gecti, 0 kaldi`. Çıktıda "maximum of 1000 expressions" geçmemeli
(geçiyorsa kural yanlış sebeple reddediyordur).
