import path from "node:path";
import { fileURLToPath } from "node:url";
import dotenv from "dotenv";

const runtimeDirectory = path.dirname(fileURLToPath(import.meta.url));
export const assistantRoot = path.resolve(runtimeDirectory, "../..");
dotenv.config({ path: path.join(assistantRoot, ".env"), quiet: true });

export type RuntimeConfig = {
  assistantRoot: string;
  projectRoot: string;
  projectIncludes: string[];
  projectName: string;
  dataRoot: string;
  port: number;
  aiApiKey: string;
  aiBaseUrl: string;
  aiModel: string;
  isTest: boolean;
};

export function getRuntimeConfig(overrides: Partial<RuntimeConfig> = {}): RuntimeConfig {
  const configuredProjectRoot = process.env.PROJECT_ROOT?.trim();
  const projectRoot = configuredProjectRoot
    ? path.resolve(assistantRoot, configuredProjectRoot)
    : path.resolve(assistantRoot, "../..");
  const projectIncludes = (process.env.PROJECT_INCLUDE || "")
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean);

  return {
    assistantRoot,
    projectRoot,
    projectIncludes,
    projectName: process.env.PROJECT_NAME?.trim() || "",
    dataRoot: path.join(assistantRoot, ".smartkid-data"),
    port: Number(process.env.PORT || 4310),
    aiApiKey: process.env.AI_API_KEY?.trim() || "",
    aiBaseUrl: process.env.AI_BASE_URL?.trim() || "https://api.openai.com/v1",
    aiModel: process.env.AI_MODEL?.trim() || "",
    isTest: process.env.NODE_ENV === "test",
    ...overrides,
  };
}
