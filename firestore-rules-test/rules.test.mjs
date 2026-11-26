import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { readFileSync } from 'fs';
import { doc, getDoc, setDoc, updateDoc, deleteDoc, serverTimestamp } from 'firebase/firestore';

const env = await initializeTestEnvironment({
  projectId: 'demo-rules',
  firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'), host: '127.0.0.1', port: 8089 },
});

const U = {
  adm:  { rol: 'admin', branchId: '' },
  m1:   { rol: 'mudur', branchId: 'b1' },
  g1:   { rol: 'garson', branchId: 'b1', roller: ['garson'] },
  g2:   { rol: 'garson', branchId: 'b2', roller: ['garson'] },
  k1:   { rol: 'mutfak', branchId: 'b1', roller: ['mutfak'] },
  br1:  { rol: 'bar', branchId: 'b1', roller: ['bar'] },
  gk:   { rol: 'garson', branchId: 'b1', roller: ['garson', 'mutfak'] },
  pas:  { rol: 'garson', branchId: 'b1', roller: ['garson'], aktif: false },
  adm2: { rol: 'garson', branchId: 'b1', roller: ['garson'] }, // e-postası admin içeriyor
};

async function seed() {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    for (const [uid, d] of Object.entries(U)) await setDoc(doc(db, 'users', uid), { ad: uid, ...d });
    await setDoc(doc(db, 'branches', 'b1'), { ad: 'B1', slug: 'b1', apiKey: 'k', siteUrl: 'https://x', mutfakAktif: true, barAktif: true });
    await setDoc(doc(db, 'branches', 'b2'), { ad: 'B2', slug: 'b2', apiKey: 'k2' });
    await setDoc(doc(db, 'calls', 'c1'), { branchId: 'b1', masaNo: '1', tip: 'siparis', durum: 'bekliyor', items: [] });
    await setDoc(doc(db, 'calls', 'c2'), { branchId: 'b2', masaNo: '1', tip: 'siparis', durum: 'bekliyor', items: [] });
    await setDoc(doc(db, 'calls', 'c3'), { branchId: 'b1', masaNo: '2', tip: 'siparis', durum: 'onaylandi',
      istasyonlar: { mutfak: { durum: 'bekliyor' }, bar: { durum: 'bekliyor' } } });
    await setDoc(doc(db, 'calls', 'c4'), { branchId: 'b1', masaNo: '3', tip: 'siparis', durum: 'hazirlaniyor',
      istasyonlar: { mutfak: { durum: 'hazirlaniyor', hazirlayanUid: 'k1' } } });
    await setDoc(doc(db, 'shifts', 's1'), { branchId: 'b1', garsonUid: 'g1', durum: 'acik' });
  });
}

const as = (uid, email) => env.authenticatedContext(uid, email ? { email } : {}).firestore();
let pass = 0, fail = 0;
async function t(ad, p) {
  try { await p; pass++; console.log('OK   ', ad); } catch (e) { fail++; console.log('FAIL ', ad, '-', String(e.message).split('\n')[0]); }
}

await seed();
// --- users
await t('garson kendi rolunu admin yapamaz', assertFails(updateDoc(doc(as('g1'), 'users', 'g1'), { rol: 'admin' })));
await t('garson kendi roller alanini degistiremez', assertFails(updateDoc(doc(as('g1'), 'users', 'g1'), { roller: ['garson', 'bar'] })));
await t('garson fcmToken yazabilir', assertSucceeds(updateDoc(doc(as('g1'), 'users', 'g1'), { fcmToken: 'x' })));
await t('garson kullanici olusturamaz', assertFails(setDoc(doc(as('g1'), 'users', 'yeni'), { rol: 'admin' })));
await t('mudur personeli mutfak yapabilir', assertSucceeds(updateDoc(doc(as('m1'), 'users', 'g1'), { rol: 'mutfak', roller: ['mutfak'] })));
await seed();
await t('mudur personele mudur rolu veremez', assertFails(updateDoc(doc(as('m1'), 'users', 'g1'), { rol: 'mudur', roller: [] })));
await t('mudur baska subenin personelini duzenleyemez', assertFails(updateDoc(doc(as('m1'), 'users', 'g2'), { ad: 'x' })));
await t('mudur personeli baska subeye tasiyamaz', assertFails(updateDoc(doc(as('m1'), 'users', 'g1'), { branchId: 'b2' })));
await t('email icinde admin gecen kullanici admin DEGIL', assertFails(updateDoc(doc(as('adm2', 'admin2@qrservis.local'), 'users', 'g2'), { rol: 'admin' })));
await t('admin kullaniciyi guncelleyebilir', assertSucceeds(updateDoc(doc(as('adm'), 'users', 'g2'), { ad: 'yeni' })));
await t('pasif kullanici okuyamaz', assertFails(getDoc(doc(as('pas'), 'calls', 'c1'))));
await t('baska subenin kullanicisini listeleme/okuma yok', assertFails(getDoc(doc(as('g1'), 'users', 'g2'))));
// --- branches
await t('mudur kendi subesinde mutfakAktif', assertSucceeds(updateDoc(doc(as('m1'), 'branches', 'b1'), { mutfakAktif: false })));
await t('mudur siteUrl degistiremez', assertFails(updateDoc(doc(as('m1'), 'branches', 'b1'), { siteUrl: 'https://kotu' })));
await t('mudur baska subeyi degistiremez', assertFails(updateDoc(doc(as('m1'), 'branches', 'b2'), { mutfakAktif: true })));
await t('garson subeyi degistiremez', assertFails(updateDoc(doc(as('g1'), 'branches', 'b1'), { mutfakAktif: true })));
await t('admin siteUrl yazabilir', assertSucceeds(updateDoc(doc(as('adm'), 'branches', 'b1'), { siteUrl: 'https://ok' })));
// --- calls
await t('garson kendi subesinin cagrisini okur', assertSucceeds(getDoc(doc(as('g1'), 'calls', 'c1'))));
await t('garson baska subenin cagrisini okuyamaz', assertFails(getDoc(doc(as('g1'), 'calls', 'c2'))));
await t('istemci cagri olusturamaz', assertFails(setDoc(doc(as('g1'), 'calls', 'yeni'), { branchId: 'b1', durum: 'bekliyor' })));
await t('garson siparisi istasyonlarla onaylar', assertSucceeds(updateDoc(doc(as('g1'), 'calls', 'c1'),
  { durum: 'onaylandi', onaylayanUid: 'g1', onaylayanAd: 'g1', onaylandiAt: serverTimestamp(), 'istasyonlar.mutfak': { durum: 'bekliyor' } })));
await seed();
await t('garson istasyonsuz onaylar (ozellik kapali akis)', assertSucceeds(updateDoc(doc(as('g1'), 'calls', 'c1'),
  { durum: 'onaylandi', onaylayanUid: 'g1', onaylayanAd: 'g1', onaylandiAt: serverTimestamp() })));
await seed();
await t('garson baskasi adina onaylayamaz', assertFails(updateDoc(doc(as('g1'), 'calls', 'c1'), { durum: 'onaylandi', onaylayanUid: 'g2' })));
await t('garson durumu hazir yapamaz', assertFails(updateDoc(doc(as('g1'), 'calls', 'c3'), { durum: 'hazir' })));
await t('garson istasyonu hazir olarak kuramaz', assertFails(updateDoc(doc(as('g1'), 'calls', 'c1'),
  { durum: 'onaylandi', onaylayanUid: 'g1', 'istasyonlar.mutfak': { durum: 'hazir' } })));
await t('garson hazirlanan siparisi iptal edemez', assertFails(updateDoc(doc(as('g1'), 'calls', 'c4'), { durum: 'iptal', iptalNedeni: 'x' })));
await t('mudur hazirlanan siparisi iptal edebilir', assertSucceeds(updateDoc(doc(as('m1'), 'calls', 'c4'), { durum: 'iptal', iptalNedeni: 'x', iptalEdenAd: 'm' })));
await seed();
await t('garson bekleyen siparisi iptal edebilir', assertSucceeds(updateDoc(doc(as('g1'), 'calls', 'c1'), { durum: 'iptal', iptalNedeni: 'x', iptalEdenAd: 'g' })));
await t('garson siparisi silemez', assertFails(deleteDoc(doc(as('g1'), 'calls', 'c1'))));
await t('baska sube garsonu siparisi guncelleyemez', assertFails(updateDoc(doc(as('g2'), 'calls', 'c1'), { durum: 'iptal' })));
// --- station
await t('mutfak kendi istasyonunu alir', assertSucceeds(updateDoc(doc(as('k1'), 'calls', 'c3'),
  { 'istasyonlar.mutfak.durum': 'hazirlaniyor', 'istasyonlar.mutfak.hazirlayanUid': 'k1', 'istasyonlar.mutfak.hazirlayanAd': 'k1', 'istasyonlar.mutfak.basladiAt': serverTimestamp(), durum: 'hazirlaniyor' })));
await seed();
await t('mutfak bar istasyonuna dokunamaz', assertFails(updateDoc(doc(as('k1'), 'calls', 'c3'),
  { 'istasyonlar.bar.durum': 'hazirlaniyor', 'istasyonlar.bar.hazirlayanUid': 'k1' })));
await t('bar mutfak istasyonunu alamaz', assertFails(updateDoc(doc(as('br1'), 'calls', 'c3'),
  { 'istasyonlar.mutfak.durum': 'hazirlaniyor', 'istasyonlar.mutfak.hazirlayanUid': 'br1' })));
await t('mutfak baska uid ile alamaz', assertFails(updateDoc(doc(as('k1'), 'calls', 'c3'),
  { 'istasyonlar.mutfak.durum': 'hazirlaniyor', 'istasyonlar.mutfak.hazirlayanUid': 'g1' })));
await t('mutfak bekliyor->hazir atlayamaz', assertFails(updateDoc(doc(as('k1'), 'calls', 'c3'), { 'istasyonlar.mutfak.durum': 'hazir' })));
await t('mutfak hazirlaniyor->hazir', assertSucceeds(updateDoc(doc(as('k1'), 'calls', 'c4'), { 'istasyonlar.mutfak.durum': 'hazir', 'istasyonlar.mutfak.hazirAt': serverTimestamp(), durum: 'hazir' })));
await seed();
await t('mutfak siparisi iptal edemez', assertFails(updateDoc(doc(as('k1'), 'calls', 'c3'), { durum: 'iptal' })));
await t('mutfak fiyat/kalem alanina yazamaz', assertFails(updateDoc(doc(as('k1'), 'calls', 'c3'), { items: [] })));
await t('garson+mutfak rollu kisi mutfak islemi yapar', assertSucceeds(updateDoc(doc(as('gk'), 'calls', 'c3'),
  { 'istasyonlar.mutfak.durum': 'hazirlaniyor', 'istasyonlar.mutfak.hazirlayanUid': 'gk', durum: 'hazirlaniyor' })));
await seed();
await t('mutfak (garson degil) onaylayamaz', assertFails(updateDoc(doc(as('k1'), 'calls', 'c1'), { durum: 'onaylandi', onaylayanUid: 'k1' })));
// --- shifts
await t('garson kendi vardiyasinin molasini yazar', assertSucceeds(setDoc(doc(as('g1'), 'shifts', 's1', 'molalar', 'm1'), { baslangic: serverTimestamp() })));
await t('baska sube garsonu mola yazamaz', assertFails(setDoc(doc(as('g2'), 'shifts', 's1', 'molalar', 'm2'), { baslangic: serverTimestamp() })));

console.log(`\n${pass} gecti, ${fail} kaldi`);
await env.cleanup();
process.exit(fail ? 1 : 0);
