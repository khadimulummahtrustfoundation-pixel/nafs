/**
 * NafsGuard — অ্যাডমিন Push Command মডিউল (আপনার বিদ্যমান Apps Script প্রজেক্টে নতুন ফাইল হিসেবে যোগ করুন)
 *
 * শীট শুধু ডাটা রাখে (Users ট্যাব)। সব নিয়ন্ত্রণ যায় FCM Push দিয়ে।
 *
 * ১) Project Settings → Script Properties এ দুটি মান দিন:
 *      FCM_PROJECT_ID = Firebase প্রজেক্টের Project ID
 *      FCM_SA_JSON    = Firebase Console → Project settings → Service accounts → Generate new private key (পুরো JSON)
 * ২) আপনার বিদ্যমান doGet(e)-এর শুরুতে এই দুই লাইন বসান:
 *      if (e.parameter.action === 'register') return nafsRegister_(e);
 *      if (e.parameter.action === 'token')    return nafsToken_(e);
 *    (পুরোনো login/register শাখা আর লাগবে না। 'rules' আগের মতোই থাকবে; 'version' আর লাগবে না, ভার্সন চেক এখন Firebase Remote Config দিয়ে।)
 * ৩) Deploy → Manage deployments → নতুন ভার্সন (Anyone)।
 * ৪) শীট রিলোড করলে ওপরে "NafsGuard Push" মেনু আসবে।
 */

var NAFS_TZ = 'Asia/Dhaka';
var USERS_SHEET = 'Users';
var LOG_SHEET = 'PushLog';
// Users কলাম: A নাম | B ফোন | C রেজিস্ট্রেশনের তারিখ-সময় | D FCM token
var NAFS_HDR = ['নাম', 'ফোন', 'রেজিস্ট্রেশনের তারিখ-সময়', 'FCM token'];

function nafsJson_(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}
function last10_(s) { return String(s || '').replace(/\D/g, '').slice(-10); }

function usersSheet_() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sh = ss.getSheetByName(USERS_SHEET);
  if (!sh) { sh = ss.insertSheet(USERS_SHEET); sh.appendRow(NAFS_HDR); sh.setFrozenRows(1); }
  return sh;
}

function findRow_(sh, phone) {
  var vals = sh.getDataRange().getValues();
  var p = last10_(phone);
  for (var i = 1; i < vals.length; i++) if (last10_(vals[i][1]) === p) return i + 1;
  return 0;
}

// ---- অ্যাপ থেকে আসা: রেজিস্ট্রেশন (শুধু ডাটা সেভ) ----
function nafsRegister_(e) {
  var name = String(e.parameter.name || '').trim();
  var phone = String(e.parameter.phone || '').trim();
  var token = String(e.parameter.token || '').trim();
  if (!name || last10_(phone).length < 10 || !token) return nafsJson_({ ok: false, msg: 'তথ্য সম্পূর্ণ নয়' });
  var lock = LockService.getScriptLock(); lock.waitLock(20000);
  try {
    var sh = usersSheet_();
    var row = findRow_(sh, phone);
    if (row) {
      // একই নম্বরে আবার রেজিস্ট্রেশন (রিইন্সটল): নাম ও token হালনাগাদ, আগের তারিখ অপরিবর্তিত
      sh.getRange(row, 1).setValue(name);
      sh.getRange(row, 4).setValue(token);
    } else {
      sh.appendRow([name, "'" + phone, Utilities.formatDate(new Date(), NAFS_TZ, 'yyyy-MM-dd HH:mm:ss'), token]);
    }
  } finally { lock.releaseLock(); }
  return nafsJson_({ ok: true, msg: 'রেজিস্ট্রেশন সম্পন্ন' });
}

// ---- অ্যাপ থেকে আসা: নতুন FCM token ----
function nafsToken_(e) {
  var phone = String(e.parameter.phone || ''), token = String(e.parameter.token || '').trim();
  var sh = usersSheet_(), row = findRow_(sh, phone);
  if (!row || !token) return nafsJson_({ ok: false, msg: 'পাওয়া যায়নি' });
  sh.getRange(row, 4).setValue(token);
  return nafsJson_({ ok: true });
}

// ---- FCM HTTP v1 (Service Account) ----
function accessToken_() {
  var cache = CacheService.getScriptCache();
  var t = cache.get('fcm_at');
  if (t) return t;
  var sa = JSON.parse(PropertiesService.getScriptProperties().getProperty('FCM_SA_JSON'));
  var now = Math.floor(Date.now() / 1000);
  var b64 = function (o) { return Utilities.base64EncodeWebSafe(typeof o === 'string' ? o : JSON.stringify(o)).replace(/=+$/, ''); };
  var head = b64({ alg: 'RS256', typ: 'JWT' });
  var claim = b64({ iss: sa.client_email, scope: 'https://www.googleapis.com/auth/firebase.messaging',
                    aud: 'https://oauth2.googleapis.com/token', iat: now, exp: now + 3600 });
  var sig = Utilities.base64EncodeWebSafe(Utilities.computeRsaSha256Signature(head + '.' + claim, sa.private_key)).replace(/=+$/, '');
  var res = UrlFetchApp.fetch('https://oauth2.googleapis.com/token', {
    method: 'post', muteHttpExceptions: true,
    payload: { grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: head + '.' + claim + '.' + sig }
  });
  var j = JSON.parse(res.getContentText());
  if (!j.access_token) throw new Error('FCM auth ব্যর্থ: ' + res.getContentText());
  cache.put('fcm_at', j.access_token, 3000);
  return j.access_token;
}

// target: { token: '...' } অথবা { topic: 'all' }; data: স্ট্রিং মানের অবজেক্ট
function sendFcm_(target, data) {
  var props = PropertiesService.getScriptProperties();
  var pid = props.getProperty('FCM_PROJECT_ID');
  data.id = String(Date.now()) + Math.floor(Math.random() * 1000);   // পুনরাবৃত্তি ঠেকানোর id
  data.serverNow = String(Date.now());                                // ফোনের ঘড়ি ঠিক রাখতে সার্ভারের সময়
  var msg = { data: data, android: { priority: 'HIGH', ttl: '2419200s' } };   // অফলাইন ফোনের জন্য ৪ সপ্তাহ ধরে রাখে
  if (target.token) msg.token = target.token; else msg.topic = target.topic;
  var res = UrlFetchApp.fetch('https://fcm.googleapis.com/v1/projects/' + pid + '/messages:send', {
    method: 'post', contentType: 'application/json', muteHttpExceptions: true,
    headers: { Authorization: 'Bearer ' + accessToken_() },
    payload: JSON.stringify({ message: msg })
  });
  return { ok: res.getResponseCode() === 200, body: res.getContentText() };
}

function log_(who, cmd, detail, result) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sh = ss.getSheetByName(LOG_SHEET) || ss.insertSheet(LOG_SHEET);
  if (sh.getLastRow() === 0) sh.appendRow(['সময়', 'ইউজার/লক্ষ্য', 'কমান্ড', 'বিবরণ', 'ফলাফল']);
  sh.appendRow([Utilities.formatDate(new Date(), NAFS_TZ, 'yyyy-MM-dd HH:mm:ss'), who, cmd, detail, result]);
}

// ---- মেনু ----
function onOpen() {
  SpreadsheetApp.getUi().createMenu('NafsGuard Push')
    .addItem('✅ Approve (মেয়াদসহ)', 'mnApprove')
    .addItem('🗓 মেয়াদ বদলান', 'mnExpiry')
    .addItem('⛔ সুইচ OFF (fb/ig/yt/media)', 'mnSwitchOff')
    .addItem('🔓 Main Lock খুলুন', 'mnLockOff')
    .addItem('🚫 অ্যাক্সেস বন্ধ (Revoke)', 'mnRevoke')
    .addItem('🔑 অফলাইন পাসওয়ার্ড রিসেট', 'mnResetPin')
    .addSeparator()
    .addItem('📢 নির্বাচিত ইউজারকে বার্তা', 'mnNoticeOne')
    .addItem('📢 সবাইকে বার্তা', 'mnNoticeAll')
    .addToUi();
}

function ask_(title, text) {
  var ui = SpreadsheetApp.getUi();
  var r = ui.prompt(title, text, ui.ButtonSet.OK_CANCEL);
  return r.getSelectedButton() === ui.Button.OK ? r.getResponseText().trim() : null;
}

// Users ট্যাবে যে সারি নির্বাচিত (ক্লিক করা) সেই ইউজার
function selectedUser_() {
  var sh = SpreadsheetApp.getActiveSheet();
  if (sh.getName() !== USERS_SHEET) { SpreadsheetApp.getUi().alert('Users ট্যাবে ইউজারের সারি নির্বাচন করুন'); return null; }
  var row = sh.getActiveRange().getRow();
  if (row < 2) { SpreadsheetApp.getUi().alert('ইউজারের সারি নির্বাচন করুন'); return null; }
  var v = sh.getRange(row, 1, 1, 4).getValues()[0];
  if (!v[3]) { SpreadsheetApp.getUi().alert('এই ইউজারের FCM token নেই'); return null; }
  return { name: v[0], phone: String(v[1]).replace(/^'/, ''), token: v[3] };
}

function parseExpiry_(s) {   // "2026-12-15 22:00" (বাংলাদেশ সময়)
  var d = Utilities.parseDate(s, NAFS_TZ, 'yyyy-MM-dd HH:mm');
  return String(d.getTime());
}

function pushUser_(cmd, extra, detail) {
  var u = selectedUser_(); if (!u) return;
  var data = { cmd: cmd, phone: u.phone };
  for (var k in extra) data[k] = extra[k];
  var r = sendFcm_({ token: u.token }, data);
  log_(u.name + ' (' + u.phone + ')', cmd, detail || JSON.stringify(extra), r.ok ? 'পাঠানো হয়েছে' : r.body);
  SpreadsheetApp.getUi().alert(r.ok ? 'পাঠানো হয়েছে' : 'ব্যর্থ: ' + r.body);
}

function mnApprove() {
  var s = ask_('Approve', 'মেয়াদ শেষের তারিখ-সময় (yyyy-MM-dd HH:mm, বাংলাদেশ সময়)'); if (!s) return;
  try { pushUser_('APPROVE', { expiry: parseExpiry_(s) }, 'মেয়াদ ' + s); } catch (e) { SpreadsheetApp.getUi().alert('তারিখ ভুল: ' + e); }
}
function mnExpiry() {
  var s = ask_('মেয়াদ বদলান', 'নতুন মেয়াদ (yyyy-MM-dd HH:mm, বাংলাদেশ সময়)'); if (!s) return;
  try { pushUser_('SET_EXPIRY', { expiry: parseExpiry_(s) }, 'মেয়াদ ' + s); } catch (e) { SpreadsheetApp.getUi().alert('তারিখ ভুল: ' + e); }
}
function mnSwitchOff() {
  var t = ask_('সুইচ OFF', 'কোনটি? fb / ig / yt / media'); if (!t) return;
  t = t.toLowerCase();
  if (['fb', 'ig', 'yt', 'media'].indexOf(t) < 0) { SpreadsheetApp.getUi().alert('fb, ig, yt অথবা media লিখুন'); return; }
  pushUser_('SWITCH_OFF', { target: t });
}
function mnLockOff() { pushUser_('LOCK_OFF', {}); }
function mnRevoke() { pushUser_('REVOKE', {}); }
function mnResetPin() { pushUser_('RESET_PIN', {}); }
function mnNoticeOne() {
  var t = ask_('বার্তার শিরোনাম', ''); if (t === null) return;
  var b = ask_('বার্তা', ''); if (!b) return;
  pushUser_('NOTICE', { title: t || 'NafsGuard', body: b });
}
function mnNoticeAll() {
  var t = ask_('বার্তার শিরোনাম (সবার জন্য)', ''); if (t === null) return;
  var b = ask_('বার্তা', ''); if (!b) return;
  var r = sendFcm_({ topic: 'all' }, { cmd: 'NOTICE', title: t || 'NafsGuard', body: b });
  log_('সবাই', 'NOTICE', t + ': ' + b, r.ok ? 'পাঠানো হয়েছে' : r.body);
  SpreadsheetApp.getUi().alert(r.ok ? 'পাঠানো হয়েছে' : 'ব্যর্থ: ' + r.body);
}
