/** Images and full submissions live in IndexedDB, avoiding localStorage image quotas. */
import { useMockApi } from "./serviceMode";
function requireDemo() { if (!useMockApi) throw new Error("Bộ nhớ demo không được sử dụng trong chế độ API thật."); }
let database: Promise<IDBDatabase> | undefined;
function openDatabase() {
  return database ??= new Promise((resolve, reject) => {
    const request = indexedDB.open("mathio-learning-v1", 1);
    request.onupgradeneeded = () => request.result.createObjectStore("records", { keyPath: "id" });
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => { database = undefined; reject(new Error("Không thể mở bộ nhớ học tập.")); };
  });
}
export async function readRecords<T>(): Promise<T[]> {
  requireDemo();
  const db = await openDatabase();
  return new Promise((resolve, reject) => {
    const request = db.transaction("records").objectStore("records").getAll();
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(new Error("Không thể đọc lịch sử."));
  });
}
export async function writeRecord<T extends { id: string }>(record: T): Promise<void> {
  requireDemo();
  const db = await openDatabase();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction("records", "readwrite");
    transaction.objectStore("records").put(record);
    transaction.oncomplete = () => resolve();
    transaction.onerror = () => reject(new Error("Không thể lưu bài làm. Hãy kiểm tra dung lượng trình duyệt."));
    transaction.onabort = () => reject(new Error("Lưu bài làm bị gián đoạn."));
  });
}
export function readSetting<T>(key: string, fallback: T): T {
  requireDemo();
  try { const value = localStorage.getItem("mathio:" + key); return value ? JSON.parse(value) as T : fallback; }
  catch { return fallback; }
}
export function writeSetting<T>(key: string, value: T) { requireDemo(); localStorage.setItem("mathio:" + key, JSON.stringify(value)); }
