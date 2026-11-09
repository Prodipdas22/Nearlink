const $ = (id) => document.getElementById(id);
const DB_NAME = "nearlink-mvp";
const STORE = "conversations";
let db;
let chats = [];
let activeId = null;

function openDB() {
  return new Promise((resolve, reject) => {
    if (!("indexedDB" in window)) return reject(new Error("IndexedDB unavailable"));
    const request = indexedDB.open(DB_NAME, 1);
    request.onupgradeneeded = () => {
      const database = request.result;
      if (!database.objectStoreNames.contains(STORE)) database.createObjectStore(STORE, {keyPath:"id"});
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}
function transaction(mode, action) {
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, mode);
    const store = tx.objectStore(STORE);
    const req = action(store);
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}
const getAll = () => transaction("readonly", s => s.getAll());
const saveChat = chat => transaction("readwrite", s => s.put(chat));

function toast(message) {
  const el = $("toast"); el.textContent = message; el.classList.add("show");
  clearTimeout(toast.timer); toast.timer = setTimeout(() => el.classList.remove("show"), 2300);
}
function initials(name) { return name.trim().split(/\s+/).slice(0,2).map(x=>x[0]).join("").toUpperCase() || "N"; }
function latest(chat) { return chat.messages.at(-1)?.text || "No messages yet"; }
function renderChats() {
  const query = $("searchInput").value.trim().toLowerCase();
  const list = $("chatList"); list.innerHTML = "";
  const visible = chats.filter(c => c.name.toLowerCase().includes(query) || latest(c).toLowerCase().includes(query));
  $("chatCount").textContent = `${chats.length} chat${chats.length===1?"":"s"}`;
  if (!visible.length) {
    list.innerHTML = `<p class="muted tiny" style="padding:12px">No conversations found.</p>`;
    return;
  }
  visible.sort((a,b)=>(b.updatedAt||0)-(a.updatedAt||0)).forEach(chat => {
    const button = document.createElement("button");
    button.className = "chat-item" + (chat.id===activeId?" active":"");
    const avatar = document.createElement("span"); avatar.className="avatar"; avatar.textContent=initials(chat.name);
    const summary = document.createElement("span"); summary.className="chat-summary";
    const name = document.createElement("strong"); name.textContent=chat.name;
    const preview = document.createElement("p"); preview.textContent=latest(chat);
    const time = document.createElement("span"); time.className="chat-time"; time.textContent=chat.messages.length ? "On device" : "";
    summary.append(name,preview); button.append(avatar,summary,time);
    button.addEventListener("click",()=>selectChat(chat.id)); list.append(button);
  });
}
function renderMessages() {
  const chat = chats.find(c=>c.id===activeId); if(!chat) return;
  $("activeName").textContent=chat.name; $("activeAvatar").textContent=initials(chat.name);
  const box=$("messages"); box.innerHTML="";
  const day=document.createElement("div"); day.className="day-label"; day.textContent="Stored on this device"; box.append(day);
  chat.messages.forEach(msg=>{
    const bubble=document.createElement("div"); bubble.className="message "+(msg.mine?"mine":"theirs");
    const body=document.createElement("div"); body.textContent=msg.text;
    const time=document.createElement("small"); time.textContent=new Date(msg.time).toLocaleTimeString([], {hour:"2-digit",minute:"2-digit"})+" · "+(msg.status||"saved");
    bubble.append(body,time); box.append(bubble);
  });
  box.scrollTop=box.scrollHeight;
}
function selectChat(id) {
  activeId=id; $("emptyState").classList.add("hidden"); $("conversation").classList.remove("hidden");
  $("layout").classList.add("show-chat"); renderChats(); renderMessages(); $("messageInput").focus();
}
async function createChat(name) {
  const chat={id:crypto.randomUUID(),name:name.trim(),messages:[],updatedAt:Date.now()};
  chats.push(chat); await saveChat(chat); renderChats(); selectChat(chat.id);
}
async function sendMessage(text) {
  const chat=chats.find(c=>c.id===activeId); if(!chat) return;
  const id=crypto.randomUUID();
  chat.messages.push({id,text,mine:true,time:Date.now(),status:"pending"});
  chat.updatedAt=Date.now(); await saveChat(chat); renderChats(); renderMessages();
  if (window.NearLinkNative) window.NearLinkNative.sendMessage(text,id);
  else toast("Native nearby messaging is available in the Android APK.");
}
function nativeEvent(raw) {
  let e; try { e=JSON.parse(raw); } catch { return; }
  const d=e.data||{};
  if(e.event==="device-found") {
    if(!document.getElementById("device-"+CSS.escape(d.endpointId))) {
      const yes=confirm("Nearby device found: "+d.name+". Connect?");
      if(yes) window.NearLinkNative.connect(d.endpointId);
    }
  } else if(e.event==="connected") {
    $("activeStatus").textContent="Connected · "+d.name; toast("Connected to "+d.name);
  } else if(e.event==="disconnected") {
    $("activeStatus").textContent="Disconnected"; toast("Device disconnected");
  } else if(e.event==="message-received") {
    receiveMessage(d);
  } else if(e.event==="message-delivered") {
    updateMessageStatus(d.id,"delivered");
  } else if(e.event==="message-failed") {
    updateMessageStatus(d.id,"failed"); toast(d.reason||"Message failed");
  } else if(e.event==="native-error") toast(d.message||"Nearby connection error");
  else if(e.event==="discovery-started") toast("Searching for nearby NearLink devices…");
  else if(e.event==="permission" && !d.granted) toast("Nearby permissions are required.");
}
async function updateMessageStatus(id,status) {
  const chat=chats.find(c=>c.messages.some(m=>m.id===id)); if(!chat)return;
  const msg=chat.messages.find(m=>m.id===id); msg.status=status;
  await saveChat(chat); renderMessages();
}
async function receiveMessage(d) {
  let chat=chats.find(c=>c.id===activeId);
  if(!chat) { await createChat("Nearby device"); chat=chats.find(c=>c.id===activeId); }
  if(chat.messages.some(m=>m.id===d.id)) return;
  chat.messages.push({id:d.id,text:d.text,mine:false,time:d.time||Date.now(),status:"delivered"});
  chat.updatedAt=Date.now(); await saveChat(chat); renderChats(); renderMessages();
  toast("Message received");
}
window.addEventListener("nearlink-native", e=>nativeEvent(e.detail));
function applyTheme(theme) {
  document.documentElement.dataset.theme=theme;
  localStorage.setItem("nearlink-theme",theme);
  $("themeBtn").textContent=theme==="dark"?"☼":"☾";
  document.querySelector('meta[name="theme-color"]').content=theme==="dark"?"#101827":"#f2f6f8";
}
async function init() {
  try { db=await openDB(); chats=await getAll(); }
  catch(e) { toast("Local storage is unavailable in this browser."); console.error(e); }
  renderChats();
  const saved=localStorage.getItem("nearlink-theme") || "dark"; applyTheme(saved);
  if("serviceWorker" in navigator && location.protocol!=="file:") {
    navigator.serviceWorker.register("./service-worker.js").catch(err=>console.warn("SW registration:",err));
  }
  $("connectionText").textContent="Local-first";
}
$("newChatBtn").addEventListener("click",()=>$("newChatDialog").showModal());
$("emptyNewBtn").addEventListener("click",()=>$("newChatDialog").showModal());
$("newChatForm").addEventListener("submit",async e=>{
  e.preventDefault(); const name=$("contactName").value.trim(); if(!name)return;
  $("newChatDialog").close(); $("contactName").value=""; await createChat(name); toast("Local conversation created.");
});
$("messageForm").addEventListener("submit",async e=>{
  e.preventDefault(); const input=$("messageInput"); const value=input.value.trim(); if(!value)return;
  await sendMessage(value); input.value=""; input.style.height="auto";
});
$("messageInput").addEventListener("keydown",e=>{
  if(e.key==="Enter"&&!e.shiftKey){e.preventDefault();$("messageForm").requestSubmit();}
});
$("messageInput").addEventListener("input",e=>{e.target.style.height="auto";e.target.style.height=Math.min(e.target.scrollHeight,110)+"px";});
$("searchInput").addEventListener("input",renderChats);
$("themeBtn").addEventListener("click",()=>applyTheme(document.documentElement.dataset.theme==="dark"?"light":"dark"));
$("infoBtn").addEventListener("click",()=>$("infoDialog").showModal());
$("closeInfo").addEventListener("click",()=>$("infoDialog").close());
$("nearbyTab").addEventListener("click",()=>{ if(window.NearLinkNative) window.NearLinkNative.startDiscovery(); else toast("Nearby discovery requires the Android app."); });
$("settingsTab").addEventListener("click",()=>toast("Use the sun/moon button to switch themes."));
$("attachBtn").addEventListener("click",()=>toast("File sharing is planned for a later release."));
window.addEventListener("online",()=>{$("connectionText").textContent="Offline-ready";});
window.addEventListener("offline",()=>{$("connectionText").textContent="Offline";});
init();
