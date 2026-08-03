import fs from "node:fs/promises";
import path from "node:path";
import type { FileDocument } from "@smartkid/shared";
import type { RuntimeConfig } from "./config.js";
import { AppError } from "./errors.js";
import type { ReferenceService } from "./references.js";
import type { ProjectScanner } from "./scanner.js";
import {
  containsLikelySecret,
  isProbablyBinary,
  PathGuard,
  redactSecrets,
  sha256,
} from "./security.js";
import type { SettingsService } from "./settings.js";
import { JsonStore } from "./store.js";

type FileMetadata = {
  recent: Array<{ path: string; openedAt: string }>;
  favorites: string[];
};

export class FileService {
  private readonly metadata: JsonStore<FileMetadata>;

  constructor(
    config: RuntimeConfig,
    private readonly guard: PathGuard,
    private readonly scanner: ProjectScanner,
    private readonly references: ReferenceService,
    private readonly settings: SettingsService,
  ) {
    this.metadata = new JsonStore(path.join(config.dataRoot, "files.json"), {
      recent: [],
      favorites: [],
    });
  }

  async read(relativePath: string): Promise<FileDocument> {
    const safe = await this.guard.resolve(relativePath);
    const stats = await fs.stat(safe.absolute);
    if (!stats.isFile()) {
      throw new AppError("NOT_A_FILE", "Đường dẫn không phải là file", 422, { path: safe.relative });
    }
    if (stats.size > this.settings.get().maxFileSize) {
      throw new AppError("FILE_TOO_LARGE", "File vượt quá giới hạn kích thước cho phép", 413, {
        path: safe.relative,
        size: stats.size,
        maxFileSize: this.settings.get().maxFileSize,
      });
    }
    const buffer = await fs.readFile(safe.absolute);
    if (isProbablyBinary(buffer, safe.relative)) {
      throw new AppError("BINARY_FILE", "Không thể hiển thị file nhị phân", 415, { path: safe.relative });
    }
    const rawContent = buffer.toString("utf8");
    if (containsLikelySecret(rawContent)) {
      throw new AppError("SENSITIVE_FILE", "File có dấu hiệu chứa bí mật và đã bị chặn", 403, {
        path: safe.relative,
      });
    }
    const redaction = redactSecrets(rawContent);
    const extension = path.extname(safe.relative).toLowerCase();
    const indexed = this.scanner.getFile(safe.relative);
    await this.trackRecent(safe.relative);
    return {
      path: safe.relative,
      content: redaction.content,
      language: languageForPath(safe.relative),
      size: stats.size,
      lineCount: rawContent.split(/\r?\n/).length,
      version: sha256(rawContent),
      readOnly: this.settings.get().readOnly || redaction.redacted,
      redacted: redaction.redacted,
      module: indexed?.module,
      xml: extension === ".xml" ? this.references.analyzeXml(safe.relative, rawContent) : undefined,
    };
  }

  async metadataInfo() {
    return this.metadata.read();
  }

  async setFavorite(relativePath: string, favorite: boolean) {
    const safe = await this.guard.resolve(relativePath);
    const metadata = await this.metadata.update((current) => {
      const favorites = current.favorites.filter((item) => item !== safe.relative);
      if (favorite) favorites.unshift(safe.relative);
      return { ...current, favorites };
    });
    return metadata.favorites;
  }

  private async trackRecent(relativePath: string) {
    await this.metadata.update((current) => ({
      ...current,
      recent: [
        { path: relativePath, openedAt: new Date().toISOString() },
        ...current.recent.filter((item) => item.path !== relativePath),
      ].slice(0, 30),
    }));
  }
}

export function languageForPath(relativePath: string): string {
  const extension = path.extname(relativePath).toLowerCase();
  const basename = path.basename(relativePath).toLowerCase();
  if (extension === ".kt" || extension === ".kts") return "kotlin";
  if (extension === ".java") return "java";
  if (extension === ".py") return "python";
  if (extension === ".xml") return "xml";
  if (extension === ".js" || extension === ".jsx" || extension === ".mjs") return "javascript";
  if (extension === ".ts" || extension === ".tsx") return "typescript";
  if (extension === ".json" || extension === ".json5") return "json";
  if (extension === ".md") return "markdown";
  if (extension === ".css" || extension === ".scss") return "css";
  if (extension === ".html") return "html";
  if (extension === ".sql") return "sql";
  if (
    extension === ".gradle" ||
    basename === "gradle.properties" ||
    basename.endsWith(".gradle.kts")
  ) {
    return "groovy";
  }
  if (extension === ".yml" || extension === ".yaml") return "yaml";
  if (extension === ".sh") return "shell";
  return "plaintext";
}
