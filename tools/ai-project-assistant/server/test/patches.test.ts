import fs from "node:fs/promises";
import path from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { sha256 } from "../src/security.js";
import { createTestProject, type TestProject } from "./helpers.js";

let project: TestProject | undefined;
afterEach(async () => {
  await project?.cleanup();
  project = undefined;
});

describe("PatchService", () => {
  it("tạo patch Pending, hiển thị diff, apply có backup và undo", async () => {
    const original = "class Greeting {\n  val text = \"Xin chào\"\n}\n";
    const updated = "class Greeting {\n  val text = \"Xin chào SMARTKID\"\n}\n";
    project = await createTestProject({ "app/src/Greeting.kt": original });

    const patch = await project.services.patches.createProposal({
      title: "Đổi lời chào",
      summary: "Test lifecycle",
      source: "manual",
      operations: [
        {
          type: "update",
          path: "app/src/Greeting.kt",
          content: updated,
          expectedVersion: sha256(original),
        },
      ],
    });
    expect(patch.status).toBe("pending");
    expect(patch.files[0]?.diff).toContain("Xin chào SMARTKID");
    expect(await fs.readFile(path.join(project.root, "app/src/Greeting.kt"), "utf8")).toBe(original);

    const applied = await project.services.patches.apply(patch.id);
    expect(applied.status).toBe("applied");
    expect(await fs.readFile(path.join(project.root, "app/src/Greeting.kt"), "utf8")).toBe(updated);

    const undone = await project.services.patches.undo(patch.id);
    expect(undone.status).toBe("undone");
    expect(await fs.readFile(path.join(project.root, "app/src/Greeting.kt"), "utf8")).toBe(original);
  });

  it("chặn apply khi file đổi sau lúc preview", async () => {
    const original = "val state = 1\n";
    project = await createTestProject({ "State.kt": original });
    const patch = await project.services.patches.createProposal({
      title: "State",
      summary: "Conflict",
      source: "manual",
      operations: [
        {
          type: "update",
          path: "State.kt",
          content: "val state = 2\n",
          expectedVersion: sha256(original),
        },
      ],
    });
    await fs.writeFile(path.join(project.root, "State.kt"), "val state = 3\n");
    await expect(project.services.patches.apply(patch.id)).rejects.toMatchObject({
      code: "FILE_VERSION_CONFLICT",
    });
  });

  it("tự chụp version khi client không gửi expectedVersion", async () => {
    project = await createTestProject({ "Implicit.kt": "val version = 1\n" });
    const patch = await project.services.patches.createProposal({
      title: "Implicit version",
      summary: "Conflict guard",
      source: "manual",
      operations: [{ type: "update", path: "Implicit.kt", content: "val version = 2\n" }],
    });
    await fs.writeFile(path.join(project.root, "Implicit.kt"), "val version = 99\n");
    await expect(project.services.patches.apply(patch.id)).rejects.toMatchObject({
      code: "FILE_VERSION_CONFLICT",
    });
  });
});
