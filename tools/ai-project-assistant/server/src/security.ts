import fs from "node:fs/promises";
import path from "node:path";
import { createHash } from "node:crypto";
import { AppError } from "./errors.js";

const SENSITIVE_BASENAMES = new Set([
  "local.properties",
  "google-services.json",
  "googleservice-info.plist",
  ".npmrc",
  ".pypirc",
  "credentials.json",
  "service-account.json",
  "id_rsa",
  "id_ed25519",
]);

const SENSITIVE_EXTENSIONS = new Set([
  ".jks",
  ".keystore",
  ".p12",
  ".pfx",
  ".pem",
  ".key",
  ".crt",
  ".der",
]);

const SECRET_NAME_PATTERN = /(^|[-_.])(secret|secrets|credential|credentials)([-_.]|$)/i;
const PROTECTED_COMPONENTS = new Set([".git", ".smartkid-data"]);
const SECRET_LITERAL_PATTERN =
  /(?:api[_-]?key|access[_-]?token|client[_-]?secret|password|private[_-]?key)\s*[:=]\s*["']([^"'\r\n]{8,})["']/i;
const ENV_SECRET_PATTERN =
  /^(?:export\s+)?[A-Z0-9_]*(?:API_KEY|TOKEN|SECRET|PASSWORD|PRIVATE_KEY)[A-Z0-9_]*\s*=\s*(.{8,})$/im;
const PRIVATE_KEY_PATTERN = /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/;

const BINARY_EXTENSIONS = new Set([
  ".apk",
  ".aab",
  ".class",
  ".dex",
  ".so",
  ".jar",
  ".zip",
  ".gz",
  ".7z",
  ".pdf",
  ".woff",
  ".woff2",
  ".ttf",
  ".otf",
  ".png",
  ".jpg",
  ".jpeg",
  ".gif",
  ".webp",
  ".ico",
  ".mp3",
  ".mp4",
  ".wav",
]);

export class PathGuard {
  readonly root: string;
  readonly allowedPaths: string[];

  private constructor(
    root: string,
    allowedPaths: string[],
    private readonly allowedAbsoluteRoots: string[],
  ) {
    this.root = root;
    this.allowedPaths = allowedPaths;
  }

  static async create(projectRoot: string, includedPaths: string[] = []): Promise<PathGuard> {
    const resolved = await fs.realpath(path.resolve(projectRoot)).catch(() => null);
    if (!resolved) {
      throw new AppError("PROJECT_ROOT_NOT_FOUND", "Thư mục dự án không tồn tại", 500, {
        projectRoot,
      });
    }

    const allowedPaths: string[] = [];
    const allowedAbsoluteRoots: string[] = [];
    for (const value of includedPaths) {
      const normalized = normalizeIncludedPath(value);
      const candidate = path.resolve(resolved, normalized);
      assertWithinRoot(resolved, candidate);
      const realCandidate = await fs.realpath(candidate).catch(() => null);
      if (!realCandidate) {
        throw new AppError(
          "PROJECT_INCLUDE_NOT_FOUND",
          "Thư mục workspace được cấu hình không tồn tại",
          500,
          { path: normalized },
        );
      }
      assertWithinRoot(resolved, realCandidate);
      allowedPaths.push(toPosix(path.relative(resolved, realCandidate)));
      allowedAbsoluteRoots.push(realCandidate);
    }

    return new PathGuard(
      resolved,
      [...new Set(allowedPaths)],
      [...new Set(allowedAbsoluteRoots)],
    );
  }

  normalize(relativePath: string): string {
    if (
      typeof relativePath !== "string" ||
      relativePath.includes("\0") ||
      path.isAbsolute(relativePath) ||
      path.win32.isAbsolute(relativePath)
    ) {
      throw new AppError("INVALID_PATH", "Đường dẫn phải là đường dẫn tương đối hợp lệ");
    }
    const normalized = relativePath.replaceAll("\\", "/").replace(/^\.\/+/, "");
    if (!normalized || normalized === "." || normalized.split("/").includes("..")) {
      throw new AppError("PATH_TRAVERSAL", "Không được truy cập ngoài thư mục dự án", 403);
    }
    if (normalized.split("/").some((part) => PROTECTED_COMPONENTS.has(part))) {
      throw new AppError("PROTECTED_PATH", "Đường dẫn hệ thống của dự án đã được bảo vệ", 403);
    }
    if (
      this.allowedPaths.length > 0 &&
      !this.allowedPaths.some(
        (allowed) => normalized === allowed || normalized.startsWith(`${allowed}/`),
      )
    ) {
      throw new AppError(
        "WORKSPACE_PATH_NOT_ALLOWED",
        "Đường dẫn không thuộc project đã cấu hình",
        403,
        { path: normalized },
      );
    }
    return normalized;
  }

  async resolve(relativePath: string, options: { allowMissing?: boolean; allowSensitive?: boolean } = {}) {
    const normalized = this.normalize(relativePath);
    if (!options.allowSensitive && isSensitivePath(normalized)) {
      throw new AppError("SENSITIVE_FILE", "File nhạy cảm không được phép đọc hoặc chỉnh sửa", 403, {
        path: normalized,
      });
    }

    const absolute = path.resolve(this.root, normalized);
    this.assertContained(absolute);

    const existingRealPath = await fs.realpath(absolute).catch(() => null);
    if (existingRealPath) {
      this.assertContained(existingRealPath);
    } else if (!options.allowMissing) {
      throw new AppError("FILE_NOT_FOUND", "Không tìm thấy file", 404, { path: normalized });
    } else {
      let parent = path.dirname(absolute);
      while (parent !== this.root) {
        const realParent = await fs.realpath(parent).catch(() => null);
        if (realParent) {
          this.assertContained(realParent);
          break;
        }
        const next = path.dirname(parent);
        if (next === parent) break;
        parent = next;
      }
    }

    return { relative: normalized, absolute };
  }

  relative(absolutePath: string): string {
    this.assertContained(absolutePath);
    return toPosix(path.relative(this.root, absolutePath));
  }

  private assertContained(candidate: string) {
    const relation = path.relative(this.root, candidate);
    if (relation !== "" && (relation.startsWith("..") || path.isAbsolute(relation))) {
      throw new AppError("PATH_TRAVERSAL", "Không được truy cập ngoài thư mục dự án", 403);
    }
    if (
      this.allowedAbsoluteRoots.length > 0 &&
      !this.allowedAbsoluteRoots.some((allowedRoot) => {
        const allowedRelation = path.relative(allowedRoot, candidate);
        return (
          allowedRelation === "" ||
          (!allowedRelation.startsWith("..") && !path.isAbsolute(allowedRelation))
        );
      })
    ) {
      throw new AppError(
        "WORKSPACE_PATH_NOT_ALLOWED",
        "Đường dẫn không thuộc project đã cấu hình",
        403,
      );
    }
  }
}

function normalizeIncludedPath(value: string): string {
  if (
    typeof value !== "string" ||
    value.includes("\0") ||
    path.isAbsolute(value) ||
    path.win32.isAbsolute(value)
  ) {
    throw new AppError(
      "INVALID_PROJECT_INCLUDE",
      "PROJECT_INCLUDE chỉ chấp nhận đường dẫn tương đối",
      500,
      { path: value },
    );
  }
  const normalized = toPosix(path.normalize(value)).replace(/^\.\/+/, "").replace(/\/+$/, "");
  if (
    !normalized ||
    normalized === "." ||
    normalized.split("/").includes("..") ||
    normalized.split("/").some((part) => PROTECTED_COMPONENTS.has(part))
  ) {
    throw new AppError(
      "INVALID_PROJECT_INCLUDE",
      "PROJECT_INCLUDE chứa đường dẫn không hợp lệ",
      500,
      { path: value },
    );
  }
  return normalized;
}

function assertWithinRoot(root: string, candidate: string) {
  const relation = path.relative(root, candidate);
  if (relation === "" || (!relation.startsWith("..") && !path.isAbsolute(relation))) return;
  throw new AppError(
    "INVALID_PROJECT_INCLUDE",
    "PROJECT_INCLUDE không được trỏ ra ngoài PROJECT_ROOT",
    500,
  );
}

export function isSensitivePath(relativePath: string): boolean {
  const normalized = toPosix(relativePath).toLowerCase();
  const parts = normalized.split("/");
  const basename = parts.at(-1) || "";
  if (basename.startsWith(".env") && basename !== ".env.example") return true;
  if (SENSITIVE_BASENAMES.has(basename)) return true;
  if (SENSITIVE_EXTENSIONS.has(path.extname(basename))) return true;
  if (parts.some((part) => part === ".ssh" || part === ".gnupg")) return true;
  return SECRET_NAME_PATTERN.test(basename);
}

export function containsLikelySecret(content: string): boolean {
  if (PRIVATE_KEY_PATTERN.test(content)) return true;
  const literal = content.match(SECRET_LITERAL_PATTERN)?.[1]?.trim();
  if (literal && !isPlaceholder(literal) && !literal.includes("process.env") && !literal.includes("System.getenv")) {
    return true;
  }
  const environmentValue = content.match(ENV_SECRET_PATTERN)?.[1]?.trim();
  return Boolean(environmentValue && !isPlaceholder(environmentValue));
}

function isPlaceholder(value: string): boolean {
  return (
    value === "" ||
    /^(your|replace|changeme|example|test|dummy|xxx|<|\$\{)/i.test(value) ||
    value.includes("process.env") ||
    value.includes("System.getenv")
  );
}

export function redactSecrets(content: string): { content: string; redacted: boolean } {
  let redacted = false;
  const lines = content.split("\n").map((line) => {
    if (SECRET_LITERAL_PATTERN.test(line) || ENV_SECRET_PATTERN.test(line) || PRIVATE_KEY_PATTERN.test(line)) {
      redacted = true;
      return "[ĐÃ ẨN BÍ MẬT]";
    }
    return line;
  });
  return { content: lines.join("\n"), redacted };
}

export function isProbablyBinary(buffer: Buffer, filename: string): boolean {
  if (BINARY_EXTENSIONS.has(path.extname(filename).toLowerCase())) return true;
  const length = Math.min(buffer.length, 8_000);
  if (length === 0) return false;
  let suspicious = 0;
  for (let index = 0; index < length; index += 1) {
    const byte = buffer[index] ?? 0;
    if (byte === 0) return true;
    if (byte < 7 || (byte > 14 && byte < 32)) suspicious += 1;
  }
  return suspicious / length > 0.1;
}

export function sha256(value: string | Buffer): string {
  return createHash("sha256").update(value).digest("hex");
}

export function toPosix(value: string): string {
  return value.split(path.sep).join("/");
}
