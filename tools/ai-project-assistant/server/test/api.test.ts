import fs from "node:fs/promises";
import path from "node:path";
import request from "supertest";
import { afterEach, describe, expect, it } from "vitest";
import { createTestProject, type TestProject } from "./helpers.js";

let project: TestProject | undefined;
afterEach(async () => {
  await project?.cleanup();
  project = undefined;
});

describe("REST API validation và contract lỗi", () => {
  it("trả lỗi có cấu trúc cho input không hợp lệ và traversal", async () => {
    project = await createTestProject({ "Main.kt": "class Main\n" });

    const invalidSearch = await request(project.app)
      .post("/api/files/search")
      .send({ query: "", mode: "unknown" });
    expect(invalidSearch.status).toBe(422);
    expect(invalidSearch.body).toMatchObject({
      success: false,
      error: { code: "VALIDATION_ERROR", message: expect.any(String), details: expect.any(Object) },
    });

    const traversal = await request(project.app)
      .get("/api/files/read")
      .query({ path: "../outside.txt" });
    expect(traversal.status).toBe(403);
    expect(traversal.body).toMatchObject({
      success: false,
      error: { code: "PATH_TRAVERSAL" },
    });
  });

  it("endpoint update chỉ tạo Pending và chỉ ghi sau apply", async () => {
    project = await createTestProject({ "Main.kt": "class Main\n" });
    const preview = await request(project.app)
      .post("/api/files/update")
      .send({ path: "Main.kt", content: "class MainUpdated\n" });
    expect(preview.status).toBe(201);
    expect(preview.body.data.status).toBe("pending");
    expect(await fs.readFile(path.join(project.root, "Main.kt"), "utf8")).toBe("class Main\n");

    const applied = await request(project.app)
      .post("/api/patch/apply")
      .send({ id: preview.body.data.id });
    expect(applied.status).toBe(200);
    expect(applied.body.data.status).toBe("applied");
    expect(await fs.readFile(path.join(project.root, "Main.kt"), "utf8")).toBe(
      "class MainUpdated\n",
    );
  });

  it("AI không cấu hình trả lỗi rõ ràng và không lộ key", async () => {
    project = await createTestProject({ "Main.kt": "class Main\n" });
    const result = await request(project.app).post("/api/ai/chat").send({
      question: "File Main.kt làm gì?",
      mode: "ask",
      scope: "project",
      activeFile: "Main.kt",
    });
    expect(result.status).toBe(503);
    expect(result.body).toMatchObject({
      success: false,
      error: { code: "AI_NOT_CONFIGURED" },
    });
    expect(JSON.stringify(result.body)).not.toContain("Bearer");
  });
});
