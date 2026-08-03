import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import type { Express } from "express";
import { createApplication, type ApplicationServices } from "../src/app.js";

export type TestProject = {
  base: string;
  root: string;
  dataRoot: string;
  app: Express;
  services: ApplicationServices;
  dispose: () => Promise<void>;
  cleanup: () => Promise<void>;
};

export async function createTestProject(
  files: Record<string, string | Buffer> = {},
): Promise<TestProject> {
  const base = await fs.mkdtemp(path.join(os.tmpdir(), "smartkid-assistant-test-"));
  const root = path.join(base, "project");
  const dataRoot = path.join(base, "data");
  await fs.mkdir(root, { recursive: true });
  for (const [relativePath, content] of Object.entries(files)) {
    const absolute = path.join(root, relativePath);
    await fs.mkdir(path.dirname(absolute), { recursive: true });
    await fs.writeFile(absolute, content);
  }
  const application = await createApplication({
    config: {
      projectRoot: root,
      projectIncludes: [],
      projectName: "",
      dataRoot,
      aiApiKey: "",
      aiModel: "",
      isTest: true,
    },
    watch: false,
  });
  return {
    base,
    root,
    dataRoot,
    ...application,
    cleanup: async () => {
      await application.dispose();
      await fs.rm(base, { recursive: true, force: true });
    },
  };
}
