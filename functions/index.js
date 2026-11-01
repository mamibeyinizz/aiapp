const { onDocumentCreated } = require('firebase-functions/v2/firestore');
const admin = require('firebase-admin');

admin.initializeApp();

// ÖNEMLİ: Firestore veritabanınızın bölgesiyle aynı olmalı
// (Firebase Console > Firestore Database > Veri sekmesi, üstte "Konum").
// Örnek: Frankfurt için 'europe-west3', Belçika için 'europe-west1', ABD için 'us-central1'.
const REGION = 'europe-west3';

const MANAGER_ROLES = ['mudur', 'müdür', 'manager'];
const WAITER_ROLES = ['garson', 'waiter'];

function roleOf(u) {
  return String(u.rol || u.role || '').toLowerCase();
}

exports.notifyNewCall = onDocumentCreated(
  { document: 'calls/{callId}', region: REGION },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const call = snap.data();
    if (call.durum !== 'bekliyor' || !call.branchId) return;

    const db = admin.firestore();

    // 1) Şubenin kullanıcıları (aktif olanlar)
    const usersSnap = await db.collection('users')
      .where('branchId', '==', call.branchId)
      .get();

    // 2) Vardiyası açık garsonlar
    const shiftsSnap = await db.collection('shifts')
      .where('branchId', '==', call.branchId)
      .where('durum', '==', 'acik')
      .get();
    const onShift = new Set(shiftsSnap.docs.map((d) => d.get('garsonUid')));

    const tokens = [];
    const tokenOwner = {};
    usersSnap.forEach((doc) => {
      const u = doc.data();
      if (u.aktif === false || !u.fcmToken) return;
      const role = roleOf(u);
      const isManager = MANAGER_ROLES.includes(role);
      const isWaiterOnShift = WAITER_ROLES.includes(role) && onShift.has(doc.id);
      if (isManager || isWaiterOnShift) {
        tokens.push(u.fcmToken);
        tokenOwner[u.fcmToken] = doc.ref;
      }
    });

    if (tokens.length === 0) return;

    const itemCount = Array.isArray(call.items) ? call.items.length : 0;
    const message = {
      tokens: [...new Set(tokens)],
      // Yalnızca "data" mesajı: uygulama kendi sesini ve kanalını kullanır.
      data: {
        type: 'call',
        callId: event.params.callId,
        branchId: String(call.branchId),
        masaNo: String(call.masaNo || ''),
        tip: String(call.tip || ''),
        itemCount: String(itemCount),
      },
      android: { priority: 'high', ttl: 60 * 1000 },
    };

    const res = await admin.messaging().sendEachForMulticast(message);

    // Geçersiz token'ları temizle
    const cleanups = [];
    res.responses.forEach((r, i) => {
      if (r.success) return;
      const code = r.error && r.error.code;
      if (code === 'messaging/registration-token-not-registered' ||
          code === 'messaging/invalid-registration-token') {
        const ref = tokenOwner[message.tokens[i]];
        if (ref) cleanups.push(ref.update({ fcmToken: admin.firestore.FieldValue.delete() }));
      }
    });
    await Promise.all(cleanups);
  }
);
