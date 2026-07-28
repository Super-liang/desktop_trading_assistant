import { access, readFile } from "node:fs/promises";
import { resolve } from "node:path";

const root = resolve(import.meta.dirname, "..");
const requiredIcons = [
  "../../assets/branding/yinxian-app-icon-v1.png",
  "src-tauri/icons/icon.icns",
  "src-tauri/icons/icon.ico",
  "src-tauri/icons/32x32.png",
  "src-tauri/icons/128x128.png",
  "public/favicon.png",
  "public/app-icon-192.png",
];

await Promise.all(requiredIcons.map((path) => access(resolve(root, path))));

const index = await readFile(resolve(root, "index.html"), "utf8");
if (!index.includes('href="/favicon.png"')) {
  throw new Error("index.html 未引用 Web favicon");
}

console.log(`图标资源检查通过：${requiredIcons.length} 个文件可用`);
