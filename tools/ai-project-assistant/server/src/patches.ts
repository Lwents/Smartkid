import fs from "node:fs/promises";
import path from "node:path";
import { createTwoFilesPatch } from "diff";
import type {
  PatchOperation,
  PatchProposal,
  PatchFile,
} from "@smartkid/shared";
import type { RuntimeConfig } from "./config.js";
import { AppError } from "./errors.js";
import { containsLikelySecret, isProbablyBinary, PathGuard, sha256, toPosix } from "./security.js";
import type { SettingsService } from "./settings.js";
import { JsonStore } from "./store.js";

type SnapshotEntry = {
  path: string;
  exists: boolean;
  type?: "file" | "directory";
  hash?: string;
  backupKey?: string;
};

type StoredProposal = PatchProposal & {
  operations: PatchOperation[];
  before?: SnapshotEntry[];
  after?: SnapshotEntry[];
};

type PatchDatabase = {
  proposals: StoredProposal[];
};

export class PatchService {
  private readonly store: JsonStore<PatchDatabase>;
  private readonly backupRoot: string;
  private mutationQueue: Promise<unknown> = Promise.resolve();

  constructor(
    config: RuntimeConfig,
    private readonly guard: PathGuard,
    private readonly settings: SettingsService,
  ) {
    this.store = new JsonStore(path.join(config.dataRoot, "patches.json"), { proposals: [] });
    this.backupRoot = path.join(config.dataRoot, "backups");
  }

  async createProposal(input: {
    title: string;
    summary: string;
    source: PatchProposal["source"];
    operations: PatchOperation[];
    plan?: string[];
  }): Promise<PatchProposal> {
    if (input.operations.length === 0) {
      throw new AppError("EMPTY_PATCH", "Bản vá không chứa thay đổi nào", 422);
    }
    if (input.operations.length > 50) {
      throw new AppError("PATCH_TOO_LARGE", "Bản vá có quá nhiều thao tác", 413);
    }

    const operations = structuredClone(input.operations);
    const id = crypto.randomUUID();
    const files: PatchFile[] = [];
    const affectedPaths = new Set<string>();
    for (const operation of operations) {
      const preview = await this.previewOperation(operation);
      files.push(...preview.files);
      for (const item of preview.affectedPaths) affectedPaths.add(item);
    }
    this.assertNoOverlappingDestinations(operations);

    const proposal: StoredProposal = {
      id,
      title: input.title.trim() || "Thay đổi chưa đặt tên",
      summary: input.summary.trim(),
      source: input.source,
      status: "pending",
      createdAt: new Date().toISOString(),
      files,
      affectedPaths: [...affectedPaths].sort(),
      plan: input.plan,
      operations,
    };
    await this.store.update((database) => ({
      proposals: [proposal, ...database.proposals].slice(0, 300),
    }));
    return publicProposal(proposal);
  }

  async get(id: string): Promise<PatchProposal> {
    return publicProposal(await this.getStored(id));
  }

  async list(): Promise<PatchProposal[]> {
    const database = await this.store.read();
    return database.proposals.map(publicProposal);
  }

  async apply(id: string): Promise<PatchProposal> {
    return this.enqueue(async () => {
      if (this.settings.get().readOnly) {
        throw new AppError("READ_ONLY", "Chế độ chỉ đọc đang bật; không thể áp dụng thay đổi", 403);
      }
      const proposal = await this.getStored(id);
      if (proposal.status !== "pending") {
        throw new AppError("PATCH_NOT_PENDING", "Bản vá không còn ở trạng thái chờ xác nhận", 409, {
          status: proposal.status,
        });
      }
      await this.revalidate(proposal.operations);
      const snapshotPaths = this.snapshotPaths(proposal.operations);
      const before = await this.captureSnapshot(id, "before", snapshotPaths);
      let after: SnapshotEntry[];
      try {
        for (const operation of proposal.operations) await this.execute(operation);
        after = await this.captureSnapshot(id, "after", snapshotPaths);
      } catch (error) {
        await this.restoreSnapshot(id, "before", before, snapshotPaths).catch(() => undefined);
        throw new AppError("PATCH_APPLY_FAILED", "Áp dụng bản vá thất bại; trạng thái cũ đã được phục hồi", 500, {
          reason: error instanceof Error ? error.message : String(error),
        });
      }
      return this.updateStored(id, (current) => ({
        ...current,
        status: "applied",
        appliedAt: new Date().toISOString(),
        before,
        after,
      }));
    });
  }

  async undo(id: string): Promise<PatchProposal> {
    return this.enqueue(async () => {
      if (this.settings.get().readOnly) {
        throw new AppError("READ_ONLY", "Chế độ chỉ đọc đang bật; không thể hoàn tác", 403);
      }
      const proposal = await this.getStored(id);
      if (proposal.status !== "applied" || !proposal.before || !proposal.after) {
        throw new AppError("PATCH_NOT_UNDOABLE", "Thay đổi này không thể hoàn tác", 409, {
          status: proposal.status,
        });
      }
      await this.assertSnapshotMatches(proposal.after);
      const snapshotPaths = this.snapshotPaths(proposal.operations);
      await this.restoreSnapshot(id, "before", proposal.before, snapshotPaths);
      return this.updateStored(id, (current) => ({
        ...current,
        status: "undone",
        undoneAt: new Date().toISOString(),
      }));
    });
  }

  async reject(id: string): Promise<PatchProposal> {
    return this.enqueue(async () => {
      const proposal = await this.getStored(id);
      if (proposal.status !== "pending") {
        throw new AppError("PATCH_NOT_PENDING", "Chỉ có thể từ chối bản vá đang chờ", 409);
      }
      return this.updateStored(id, (current) => ({ ...current, status: "rejected" }));
    });
  }

  private async previewOperation(
    operation: PatchOperation,
  ): Promise<{ files: PatchFile[]; affectedPaths: string[] }> {
    if (operation.type === "update") {
      const safe = await this.guard.resolve(operation.path);
      const oldContent = await this.readSafeText(safe.absolute, safe.relative);
      if (operation.expectedVersion && sha256(oldContent) !== operation.expectedVersion) {
        throw new AppError("FILE_VERSION_CONFLICT", "File đã thay đổi kể từ khi được mở", 409, {
          path: safe.relative,
        });
      }
      operation.expectedVersion ??= sha256(oldContent);
      this.validateNewContent(operation.content);
      return {
        files: [
          {
            path: safe.relative,
            action: operation.type,
            diff: createTwoFilesPatch(
              safe.relative,
              safe.relative,
              oldContent,
              operation.content,
              "hiện tại",
              "đề xuất",
              { context: 4 },
            ),
          },
        ],
        affectedPaths: [safe.relative],
      };
    }

    if (operation.type === "create") {
      const safe = await this.guard.resolve(operation.path, { allowMissing: true });
      if (await exists(safe.absolute)) {
        throw new AppError("FILE_EXISTS", "File đích đã tồn tại", 409, { path: safe.relative });
      }
      this.validateNewContent(operation.content);
      return {
        files: [
          {
            path: safe.relative,
            action: operation.type,
            diff: createTwoFilesPatch(
              "/dev/null",
              safe.relative,
              "",
              operation.content,
              "không tồn tại",
              "đề xuất",
              { context: 4 },
            ),
          },
        ],
        affectedPaths: [safe.relative],
      };
    }

    if (operation.type === "mkdir") {
      const safe = await this.guard.resolve(operation.path, { allowMissing: true });
      if (await exists(safe.absolute)) {
        throw new AppError("FILE_EXISTS", "Thư mục đã tồn tại", 409, { path: safe.relative });
      }
      return {
        files: [{ path: safe.relative, action: operation.type, diff: `+ Thư mục ${safe.relative}\n` }],
        affectedPaths: [safe.relative],
      };
    }

    if (operation.type === "delete") {
      const safe = await this.guard.resolve(operation.path);
      const stats = await fs.stat(safe.absolute);
      if (stats.isDirectory()) {
        const descendants = await this.listDescendantFiles(safe.absolute);
        const files: PatchFile[] = [];
        for (const absolute of descendants) {
          const relative = this.guard.relative(absolute);
          const content = await this.tryReadText(absolute, relative);
          files.push({
            path: relative,
            action: "delete",
            diff: content === null
              ? `--- ${relative}\n+++ /dev/null\n- [file nhị phân hoặc quá lớn]\n`
              : createTwoFilesPatch(relative, "/dev/null", content, "", "hiện tại", "đã xóa", {
                  context: 2,
                }),
          });
        }
        return {
          files: files.length
            ? files
            : [{ path: safe.relative, action: "delete", diff: `- Thư mục rỗng ${safe.relative}\n` }],
          affectedPaths: [safe.relative, ...descendants.map((item) => this.guard.relative(item))],
        };
      }
      const content = await this.tryReadText(safe.absolute, safe.relative);
      return {
        files: [
          {
            path: safe.relative,
            action: "delete",
            diff:
              content === null
                ? `--- ${safe.relative}\n+++ /dev/null\n- [file nhị phân hoặc quá lớn]\n`
                : createTwoFilesPatch(safe.relative, "/dev/null", content, "", "hiện tại", "đã xóa", {
                    context: 4,
                  }),
          },
        ],
        affectedPaths: [safe.relative],
      };
    }

    const source = await this.guard.resolve(operation.path);
    const destination = await this.guard.resolve(operation.destination, { allowMissing: true });
    if (await exists(destination.absolute)) {
      throw new AppError("DESTINATION_EXISTS", "Đường dẫn đích đã tồn tại", 409, {
        destination: destination.relative,
      });
    }
    const stats = await fs.stat(source.absolute);
    if (operation.type === "rename-directory" && !stats.isDirectory()) {
      throw new AppError("NOT_A_DIRECTORY", "Nguồn không phải là thư mục", 422);
    }
    if ((operation.type === "rename" || operation.type === "move") && !stats.isFile()) {
      throw new AppError("NOT_A_FILE", "Nguồn không phải là file", 422);
    }
    const descendants = stats.isDirectory() ? await this.listDescendantFiles(source.absolute) : [];
    return {
      files: [
        {
          path: source.relative,
          destination: destination.relative,
          action: operation.type,
          diff: `--- ${source.relative}\n+++ ${destination.relative}\n  [Nội dung giữ nguyên]\n`,
        },
      ],
      affectedPaths: [
        source.relative,
        destination.relative,
        ...descendants.map((item) => this.guard.relative(item)),
      ],
    };
  }

  private async revalidate(operations: PatchOperation[]) {
    for (const operation of operations) {
      if (operation.type === "update") {
        const safe = await this.guard.resolve(operation.path);
        if (operation.expectedVersion) {
          const content = await this.readSafeText(safe.absolute, safe.relative);
          if (sha256(content) !== operation.expectedVersion) {
            throw new AppError("FILE_VERSION_CONFLICT", "File đã thay đổi sau khi tạo diff", 409, {
              path: safe.relative,
            });
          }
        }
      } else if (operation.type === "create" || operation.type === "mkdir") {
        const safe = await this.guard.resolve(operation.path, { allowMissing: true });
        if (await exists(safe.absolute)) {
          throw new AppError("DESTINATION_EXISTS", "Đường dẫn đích đã tồn tại", 409, {
            path: safe.relative,
          });
        }
      } else if (operation.type === "delete") {
        await this.guard.resolve(operation.path);
      } else {
        await this.guard.resolve(operation.path);
        const destination = await this.guard.resolve(operation.destination, { allowMissing: true });
        if (await exists(destination.absolute)) {
          throw new AppError("DESTINATION_EXISTS", "Đường dẫn đích đã tồn tại", 409, {
            destination: destination.relative,
          });
        }
      }
    }
  }

  private async execute(operation: PatchOperation) {
    if (operation.type === "update" || operation.type === "create") {
      const safe = await this.guard.resolve(operation.path, {
        allowMissing: operation.type === "create",
      });
      await fs.mkdir(path.dirname(safe.absolute), { recursive: true });
      const existingMode =
        operation.type === "update"
          ? (await fs.stat(safe.absolute)).mode & 0o777
          : 0o644;
      await writeAtomic(safe.absolute, operation.content, existingMode);
      return;
    }
    if (operation.type === "mkdir") {
      const safe = await this.guard.resolve(operation.path, { allowMissing: true });
      await fs.mkdir(safe.absolute);
      return;
    }
    if (operation.type === "delete") {
      const safe = await this.guard.resolve(operation.path);
      await fs.rm(safe.absolute, { recursive: true, force: false });
      return;
    }
    const source = await this.guard.resolve(operation.path);
    const destination = await this.guard.resolve(operation.destination, { allowMissing: true });
    await fs.mkdir(path.dirname(destination.absolute), { recursive: true });
    await fs.rename(source.absolute, destination.absolute);
  }

  private snapshotPaths(operations: PatchOperation[]): string[] {
    const paths = new Set<string>();
    for (const operation of operations) {
      paths.add(toPosix(operation.path));
      if ("destination" in operation) paths.add(toPosix(operation.destination));
    }
    return removeNestedPaths([...paths]);
  }

  private async captureSnapshot(id: string, phase: "before" | "after", paths: string[]) {
    const entries: SnapshotEntry[] = [];
    for (const relativePath of paths) {
      const safe = await this.guard.resolve(relativePath, { allowMissing: true });
      const stats = await fs.stat(safe.absolute).catch(() => null);
      if (!stats) {
        entries.push({ path: safe.relative, exists: false });
        continue;
      }
      const type = stats.isDirectory() ? "directory" : "file";
      const backupKey = sha256(safe.relative);
      const backupPath = path.join(this.backupRoot, id, phase, backupKey);
      await fs.mkdir(path.dirname(backupPath), { recursive: true });
      await fs.cp(safe.absolute, backupPath, { recursive: true, force: false });
      entries.push({
        path: safe.relative,
        exists: true,
        type,
        hash: await hashPath(safe.absolute),
        backupKey,
      });
    }
    return entries;
  }

  private async restoreSnapshot(
    id: string,
    phase: "before" | "after",
    snapshot: SnapshotEntry[],
    paths: string[],
  ) {
    for (const relativePath of [...paths].sort((left, right) => right.length - left.length)) {
      const safe = await this.guard.resolve(relativePath, { allowMissing: true });
      if (await exists(safe.absolute)) {
        await fs.rm(safe.absolute, { recursive: true, force: false });
      }
    }
    for (const entry of snapshot.filter((item) => item.exists)) {
      const safe = await this.guard.resolve(entry.path, { allowMissing: true });
      const source = path.join(this.backupRoot, id, phase, entry.backupKey ?? "");
      await fs.mkdir(path.dirname(safe.absolute), { recursive: true });
      await fs.cp(source, safe.absolute, { recursive: true, force: false });
    }
  }

  private async assertSnapshotMatches(snapshot: SnapshotEntry[]) {
    for (const entry of snapshot) {
      const safe = await this.guard.resolve(entry.path, { allowMissing: true });
      const currentExists = await exists(safe.absolute);
      if (currentExists !== entry.exists) {
        throw new AppError("UNDO_CONFLICT", "File đã thay đổi sau khi áp dụng; hoàn tác bị chặn", 409, {
          path: entry.path,
        });
      }
      if (currentExists && entry.hash !== (await hashPath(safe.absolute))) {
        throw new AppError("UNDO_CONFLICT", "Nội dung đã thay đổi sau khi áp dụng; hoàn tác bị chặn", 409, {
          path: entry.path,
        });
      }
    }
  }

  private async getStored(id: string): Promise<StoredProposal> {
    const database = await this.store.read();
    const proposal = database.proposals.find((item) => item.id === id);
    if (!proposal) throw new AppError("PATCH_NOT_FOUND", "Không tìm thấy bản vá", 404, { id });
    return proposal;
  }

  private async updateStored(
    id: string,
    updater: (proposal: StoredProposal) => StoredProposal,
  ): Promise<PatchProposal> {
    let result: StoredProposal | undefined;
    await this.store.update((database) => ({
      proposals: database.proposals.map((proposal) => {
        if (proposal.id !== id) return proposal;
        result = updater(proposal);
        return result;
      }),
    }));
    if (!result) throw new AppError("PATCH_NOT_FOUND", "Không tìm thấy bản vá", 404);
    return publicProposal(result);
  }

  private enqueue<T>(work: () => Promise<T>): Promise<T> {
    const result = this.mutationQueue.then(work, work);
    this.mutationQueue = result.catch(() => undefined);
    return result;
  }

  private validateNewContent(content: string) {
    if (Buffer.byteLength(content, "utf8") > this.settings.get().maxFileSize) {
      throw new AppError("FILE_TOO_LARGE", "Nội dung mới vượt quá giới hạn kích thước", 413);
    }
    if (containsLikelySecret(content)) {
      throw new AppError("SECRET_DETECTED", "Nội dung mới có dấu hiệu chứa bí mật", 403);
    }
  }

  private async readSafeText(absolute: string, relative: string) {
    const buffer = await fs.readFile(absolute);
    if (buffer.length > this.settings.get().maxFileSize || isProbablyBinary(buffer, relative)) {
      throw new AppError("UNSUPPORTED_FILE", "Không thể chỉnh sửa file nhị phân hoặc quá lớn", 415, {
        path: relative,
      });
    }
    const content = buffer.toString("utf8");
    if (containsLikelySecret(content)) {
      throw new AppError("SENSITIVE_FILE", "File có dấu hiệu chứa bí mật và đã bị chặn", 403);
    }
    return content;
  }

  private async tryReadText(absolute: string, relative: string): Promise<string | null> {
    const buffer = await fs.readFile(absolute);
    if (
      buffer.length > this.settings.get().maxFileSize ||
      isProbablyBinary(buffer, relative) ||
      containsLikelySecret(buffer.toString("utf8"))
    ) {
      return null;
    }
    return buffer.toString("utf8");
  }

  private async listDescendantFiles(absoluteDirectory: string): Promise<string[]> {
    const output: string[] = [];
    const walk = async (directory: string) => {
      const entries = await fs.readdir(directory, { withFileTypes: true });
      for (const entry of entries) {
        const absolute = path.join(directory, entry.name);
        if (entry.isSymbolicLink()) continue;
        const relative = this.guard.relative(absolute);
        await this.guard.resolve(relative);
        if (entry.isDirectory()) await walk(absolute);
        else if (entry.isFile()) output.push(absolute);
      }
    };
    await walk(absoluteDirectory);
    return output;
  }

  private assertNoOverlappingDestinations(operations: PatchOperation[]) {
    const destinations = new Set<string>();
    const sources = new Set<string>();
    for (const operation of operations) {
      const source = toPosix(operation.path);
      if (sources.has(source)) {
        throw new AppError("DUPLICATE_SOURCE", "Nhiều thao tác cùng sửa một đường dẫn", 409, {
          path: source,
        });
      }
      sources.add(source);
      const target =
        operation.type === "create" || operation.type === "mkdir"
          ? toPosix(operation.path)
          : "destination" in operation
            ? toPosix(operation.destination)
            : undefined;
      if (!target) continue;
      if (destinations.has(target)) {
        throw new AppError("DUPLICATE_DESTINATION", "Nhiều thao tác cùng ghi vào một đường dẫn", 409, {
          path: target,
        });
      }
      destinations.add(target);
    }
  }
}

function publicProposal(proposal: StoredProposal): PatchProposal {
  const { operations: _operations, before: _before, after: _after, ...publicValue } = proposal;
  return structuredClone(publicValue);
}

async function exists(candidate: string) {
  return fs.access(candidate).then(
    () => true,
    () => false,
  );
}

async function writeAtomic(filename: string, content: string, mode: number) {
  const temporary = path.join(
    path.dirname(filename),
    `.${path.basename(filename)}.${process.pid}.${Date.now()}.smartkid-tmp`,
  );
  await fs.writeFile(temporary, content, { encoding: "utf8", mode });
  await fs.rename(temporary, filename);
}

async function hashPath(candidate: string): Promise<string> {
  const stats = await fs.stat(candidate);
  if (stats.isFile()) return sha256(await fs.readFile(candidate));
  const entries = await fs.readdir(candidate, { withFileTypes: true });
  const pieces: string[] = [];
  for (const entry of entries.sort((left, right) => left.name.localeCompare(right.name))) {
    if (entry.isSymbolicLink()) continue;
    pieces.push(`${entry.name}:${await hashPath(path.join(candidate, entry.name))}`);
  }
  return sha256(pieces.join("\n"));
}

function removeNestedPaths(paths: string[]) {
  const sorted = [...new Set(paths)].sort((left, right) => left.length - right.length);
  return sorted.filter(
    (candidate, index) =>
      !sorted.slice(0, index).some((parent) => candidate.startsWith(`${parent}/`)),
  );
}
