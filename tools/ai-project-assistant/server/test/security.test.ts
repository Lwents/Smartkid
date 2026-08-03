import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import {
  containsLikelySecret,
  isSensitivePath,
  PathGuard,
  redactSecrets,
} from "../src/security.js";

const cleanup: string[] = [];
afterEach(async () => {
  await Promise.all(cleanup.splice(0).map((directory) => fs.rm(directory, { recursive: true, force: true })));
});

describe("PathGuard", () => {
  it("chặn path traversal, đường dẫn tuyệt đối và file nhạy cảm", async () => {
    const root = await fs.mkdtemp(path.join(os.tmpdir(), "smartkid-guard-"));
    cleanup.push(root);
    await fs.writeFile(path.join(root, "safe.kt"), "class Safe");
    await fs.writeFile(path.join(root, "local.properties"), "sdk.dir=/private");
    const guard = await PathGuard.create(root);

    await expect(guard.resolve("../outside.txt")).rejects.toMatchObject({
      code: "PATH_TRAVERSAL",
    });
    await expect(guard.resolve("/etc/passwd")).rejects.toMatchObject({
      code: "INVALID_PATH",
    });
    await expect(guard.resolve("C:\\Windows\\system.ini")).rejects.toMatchObject({
      code: "INVALID_PATH",
    });
    await expect(guard.resolve("local.properties")).rejects.toMatchObject({
      code: "SENSITIVE_FILE",
    });
    await expect(guard.resolve("safe.kt")).resolves.toMatchObject({ relative: "safe.kt" });
  });

  it("chặn symlink trỏ ra ngoài project", async () => {
    const base = await fs.mkdtemp(path.join(os.tmpdir(), "smartkid-symlink-"));
    cleanup.push(base);
    const root = path.join(base, "project");
    const outside = path.join(base, "outside.txt");
    await fs.mkdir(root);
    await fs.writeFile(outside, "outside");
    await fs.symlink(outside, path.join(root, "escape.txt"));
    const guard = await PathGuard.create(root);

    await expect(guard.resolve("escape.txt")).rejects.toMatchObject({
      code: "PATH_TRAVERSAL",
    });
  });

  it("chỉ cho phép các thư mục đã chọn trong workspace nhiều project", async () => {
    const root = await fs.mkdtemp(path.join(os.tmpdir(), "smartkid-workspace-"));
    cleanup.push(root);
    await fs.mkdir(path.join(root, "Smartkid"));
    await fs.mkdir(path.join(root, "BeSmartkid"));
    await fs.mkdir(path.join(root, "OtherProject"));
    await fs.writeFile(path.join(root, "Smartkid", "MainActivity.java"), "class MainActivity {}");
    await fs.writeFile(path.join(root, "BeSmartkid", "manage.py"), "# Django");
    await fs.writeFile(path.join(root, "OtherProject", "secret.txt"), "private");
    const guard = await PathGuard.create(root, ["Smartkid", "BeSmartkid"]);

    await expect(guard.resolve("Smartkid/MainActivity.java")).resolves.toMatchObject({
      relative: "Smartkid/MainActivity.java",
    });
    await expect(guard.resolve("BeSmartkid/manage.py")).resolves.toMatchObject({
      relative: "BeSmartkid/manage.py",
    });
    await expect(guard.resolve("OtherProject/secret.txt")).rejects.toMatchObject({
      code: "WORKSPACE_PATH_NOT_ALLOWED",
    });
  });
});

describe("lọc bí mật", () => {
  it("nhận diện tên file và literal bí mật, nhưng không chặn placeholder", () => {
    expect(isSensitivePath(".env")).toBe(true);
    expect(isSensitivePath("release/app.jks")).toBe(true);
    expect(isSensitivePath(".env.example")).toBe(false);
    expect(containsLikelySecret('const API_KEY = "sk-real-looking-value-123456";')).toBe(true);
    expect(containsLikelySecret("AI_API_KEY=")).toBe(false);
    expect(containsLikelySecret("const API_KEY = process.env.API_KEY;")).toBe(false);
    expect(redactSecrets('password = "a-very-secret-value"').redacted).toBe(true);
  });
});
