import fs from "node:fs/promises";
import path from "node:path";
import { EventEmitter } from "node:events";
import chokidar, { type FSWatcher } from "chokidar";
import createIgnore, { type Ignore } from "ignore";
import type {
  ModuleId,
  ModuleSummary,
  ProjectInfo,
  TreeNode,
} from "@smartkid/shared";
import type { RuntimeConfig } from "./config.js";
import type { SettingsService } from "./settings.js";
import {
  containsLikelySecret,
  isProbablyBinary,
  isSensitivePath,
  toPosix,
} from "./security.js";
import { PathGuard } from "./security.js";

const DEFAULT_IGNORES = [
  ".git/",
  ".gradle/",
  "**/.gradle/",
  "**/build/",
  "**/dist/",
  "**/node_modules/",
  "**/.idea/caches/",
  "**/.cache/",
  "**/.mypy_cache/",
  "**/.pytest_cache/",
  "**/coverage/",
  "**/htmlcov/",
  "**/.smartkid-data/",
  "**/.venv/",
  "**/venv/",
  "**/__pycache__/",
  "**/generated/",
  "**/tmp/",
  "**/*.apk",
  "**/*.aab",
  "**/*.dex",
  "**/*.class",
  "**/*.so",
  "**/*.jar",
  "**/*.zip",
];

const INDEXABLE_EXTENSIONS = new Set([
  ".kt",
  ".kts",
  ".java",
  ".xml",
  ".gradle",
  ".properties",
  ".toml",
  ".json",
  ".json5",
  ".js",
  ".jsx",
  ".ts",
  ".tsx",
  ".mjs",
  ".cjs",
  ".md",
  ".txt",
  ".yml",
  ".yaml",
  ".html",
  ".css",
  ".scss",
  ".sql",
  ".graphql",
  ".proto",
  ".py",
  ".sh",
]);

const MODULE_META: Record<ModuleId, Omit<ModuleSummary, "fileCount" | "samplePaths">> = {
  student: {
    id: "student",
    name: "STUDENT",
    icon: "🎓",
    description: "Ứng dụng và luồng trải nghiệm dành cho học sinh",
  },
  teacher: {
    id: "teacher",
    name: "TEACHER",
    icon: "🧑‍🏫",
    description: "Quản lý lớp học và nghiệp vụ giáo viên",
  },
  admin: {
    id: "admin",
    name: "ADMIN",
    icon: "🛡️",
    description: "Quản trị hệ thống, nội dung và thông báo",
  },
  backend: {
    id: "backend",
    name: "BACKEND",
    icon: "🗄️",
    description: "API, dữ liệu, repository và dịch vụ nền",
  },
};

export type IndexedFile = {
  path: string;
  absolutePath: string;
  name: string;
  extension: string;
  size: number;
  modifiedAt: number;
  content: string;
  lines: string[];
  module?: ModuleId;
};

type ScanStats = {
  ignored: number;
  oversized: number;
};

export class ProjectScanner extends EventEmitter {
  private entries = new Map<string, IndexedFile>();
  private tree: TreeNode[] = [];
  private stats: ScanStats = { ignored: 0, oversized: 0 };
  private lastScanAt?: string;
  private watcher?: FSWatcher;
  private scanPromise?: Promise<void>;
  private rescanTimer?: NodeJS.Timeout;
  private changedPaths = new Set<string>();
  scanning = false;

  constructor(
    private readonly config: RuntimeConfig,
    private readonly guard: PathGuard,
    private readonly settings: SettingsService,
  ) {
    super();
  }

  async initialize(options: { watch?: boolean } = {}) {
    await this.scan();
    if (options.watch !== false) await this.startWatcher();
  }

  async close() {
    if (this.rescanTimer) clearTimeout(this.rescanTimer);
    await this.watcher?.close();
  }

  async scan(): Promise<void> {
    if (this.scanPromise) return this.scanPromise;
    this.scanPromise = this.performScan().finally(() => {
      this.scanPromise = undefined;
    });
    return this.scanPromise;
  }

  getFiles(): IndexedFile[] {
    return [...this.entries.values()];
  }

  getFile(relativePath: string): IndexedFile | undefined {
    return this.entries.get(toPosix(relativePath));
  }

  getTree(): TreeNode[] {
    return structuredClone(this.tree);
  }

  getKnownFiles(): Array<{
    path: string;
    name: string;
    module?: ModuleId;
    readable: boolean;
  }> {
    const files: Array<{
      path: string;
      name: string;
      module?: ModuleId;
      readable: boolean;
    }> = [];
    const collect = (nodes: TreeNode[]) => {
      for (const node of nodes) {
        if (node.type === "directory") collect(node.children ?? []);
        else {
          files.push({
            path: node.path,
            name: node.name,
            module: node.module,
            readable: node.readable ?? false,
          });
        }
      }
    };
    collect(this.tree);
    return files;
  }

  hasAndroidResource(type: string, name: string): boolean {
    if (!/^[A-Za-z_][\w-]*$/.test(type) || !/^[A-Za-z_][\w.]*$/.test(name)) return false;
    const expression = new RegExp(
      `(?:^|/)res(?:[-_][^/]*)?/${escapeRegex(type)}(?:-[^/]*)?/${escapeRegex(name)}\\.[^/]+$`,
      "i",
    );
    return this.getKnownFiles().some((file) => expression.test(file.path));
  }

  getModules(): ModuleSummary[] {
    return (Object.keys(MODULE_META) as ModuleId[]).map((id) => {
      const matching = this.getKnownFiles().filter((file) => file.module === id);
      return {
        ...MODULE_META[id],
        fileCount: matching.length,
        samplePaths: matching.slice(0, 6).map((file) => file.path),
      };
    });
  }

  getInfo(): ProjectInfo {
    const files = this.getFiles();
    const kotlin = files.some((file) => file.extension === ".kt" || file.extension === ".kts");
    const java = files.some((file) => file.extension === ".java");
    const manifests = files.filter((file) => file.name === "AndroidManifest.xml");
    const gradleFiles = files
      .filter((file) => /(?:^|\/)(?:build|settings)\.gradle(?:\.kts)?$/.test(file.path))
      .map((file) => file.path);
    const specialResourceDirectories = new Set<string>();
    for (const file of files) {
      const match = file.path.match(/(?:^|\/)(res[-_][^/]+)\//i);
      if (match?.[1]) specialResourceDirectories.add(match[1]);
    }
    const hasAndroidPlugin = files.some(
      (file) =>
        /build\.gradle(?:\.kts)?$/.test(file.path) &&
        /com\.android\.(?:application|library)/.test(file.content),
    );

    return {
      name:
        this.config.projectName ||
        (this.config.projectIncludes.length === 1
          ? path.basename(this.config.projectIncludes[0] ?? this.guard.root)
          : this.config.projectIncludes.length > 1
            ? this.config.projectIncludes.map((value) => path.basename(value)).join(" + ")
            : path.basename(this.guard.root)),
      root: this.guard.root,
      android: {
        detected: manifests.length > 0 || hasAndroidPlugin,
        languages: [...(kotlin ? (["Kotlin"] as const) : []), ...(java ? (["Java"] as const) : [])],
        specialResourceDirectories: [...specialResourceDirectories].sort(),
        gradleFiles,
      },
      indexedFiles: files.length,
      ignoredFiles: this.stats.ignored,
      oversizedFiles: this.stats.oversized,
      lastScanAt: this.lastScanAt,
      scanning: this.scanning,
      modules: this.getModules(),
    };
  }

  private async performScan() {
    this.scanning = true;
    this.emit("scan:start");
    try {
      const entries = new Map<string, IndexedFile>();
      const stats: ScanStats = { ignored: 0, oversized: 0 };
      const tree: TreeNode[] = [];
      if (this.config.projectIncludes.length > 0) {
        for (const includedPath of this.config.projectIncludes) {
          const ignoreRules = await this.createIgnoreRules(includedPath);
          const children = await this.walk(
            includedPath,
            ignoreRules,
            entries,
            stats,
            includedPath,
          );
          tree.push({
            name: path.basename(includedPath),
            path: includedPath,
            type: "directory",
            children,
          });
        }
      } else {
        const ignoreRules = await this.createIgnoreRules();
        tree.push(...(await this.walk("", ignoreRules, entries, stats)));
      }
      this.assignModules(entries);
      this.decorateTree(tree, entries);
      this.entries = entries;
      this.tree = tree;
      this.stats = stats;
      this.lastScanAt = new Date().toISOString();
      this.emit("scan:complete", this.getInfo());
    } finally {
      this.scanning = false;
    }
  }

  private async walk(
    relativeDirectory: string,
    ignoreRules: Ignore,
    indexed: Map<string, IndexedFile>,
    stats: ScanStats,
    ignoreRoot = "",
  ): Promise<TreeNode[]> {
    const absoluteDirectory = relativeDirectory
      ? path.join(this.guard.root, relativeDirectory)
      : this.guard.root;
    const directoryEntries = await fs.readdir(absoluteDirectory, { withFileTypes: true }).catch(() => []);
    const nodes: TreeNode[] = [];

    for (const directoryEntry of directoryEntries.sort((left, right) => {
      if (left.isDirectory() !== right.isDirectory()) return left.isDirectory() ? -1 : 1;
      return left.name.localeCompare(right.name);
    })) {
      const relativePath = toPosix(path.join(relativeDirectory, directoryEntry.name));
      const ignoreRelative = ignoreRoot
        ? toPosix(path.relative(ignoreRoot, relativePath))
        : relativePath;
      const ignoreCandidate = directoryEntry.isDirectory() ? `${ignoreRelative}/` : ignoreRelative;
      if (ignoreRules.ignores(ignoreCandidate) || directoryEntry.isSymbolicLink()) {
        stats.ignored += 1;
        continue;
      }

      if (directoryEntry.isDirectory()) {
        const children = await this.walk(relativePath, ignoreRules, indexed, stats, ignoreRoot);
        nodes.push({
          name: directoryEntry.name,
          path: relativePath,
          type: "directory",
          children,
        });
        continue;
      }
      if (!directoryEntry.isFile()) continue;

      if (isSensitivePath(relativePath)) {
        stats.ignored += 1;
        continue;
      }

      const absolutePath = path.join(this.guard.root, relativePath);
      const fileStat = await fs.stat(absolutePath).catch(() => null);
      if (!fileStat) continue;
      const extension = path.extname(directoryEntry.name).toLowerCase();
      const tooLarge = fileStat.size > this.settings.get().maxFileSize;
      if (tooLarge) stats.oversized += 1;

      let readable = INDEXABLE_EXTENSIONS.has(extension) || extension === "";
      let content = "";
      if (readable && !tooLarge) {
        const buffer = await fs.readFile(absolutePath).catch(() => null);
        if (!buffer || isProbablyBinary(buffer, directoryEntry.name)) {
          readable = false;
        } else {
          content = buffer.toString("utf8");
          if (containsLikelySecret(content)) {
            stats.ignored += 1;
            continue;
          }
        }
      }

      nodes.push({
        name: directoryEntry.name,
        path: relativePath,
        type: "file",
        extension,
        size: fileStat.size,
        readable,
        tooLarge,
        changed: this.changedPaths.has(relativePath),
      });

      if (readable && !tooLarge) {
        indexed.set(relativePath, {
          path: relativePath,
          absolutePath,
          name: directoryEntry.name,
          extension,
          size: fileStat.size,
          modifiedAt: fileStat.mtimeMs,
          content,
          lines: content.split(/\r?\n/),
        });
      }
    }
    return nodes;
  }

  private assignModules(entries: Map<string, IndexedFile>) {
    for (const file of entries.values()) {
      file.module = this.classifyModule(file.path, file.content);
    }
  }

  private classifyModule(relativePath: string, content: string): ModuleId | undefined {
    const mapping = this.settings.get().moduleMapping;
    let winner: { id: ModuleId; score: number } | undefined;
    const pathValue = relativePath.toLowerCase();
    const pathSegments = pathValue.split(/[/_.-]+/).filter(Boolean);
    const contentSample = content.slice(0, 120_000).toLowerCase();
    for (const id of Object.keys(mapping) as ModuleId[]) {
      const rules = mapping[id];
      let score = 0;
      for (const keyword of rules.keywords) {
        const needle = keyword.toLowerCase();
        const segmentIndex = pathSegments.indexOf(needle);
        if (segmentIndex === 0) score += 1_000;
        else if (segmentIndex > 0) score += 40;
        else if (pathValue.includes(needle)) score += 6;
        if (contentSample.includes(needle)) score += 2;
      }
      for (const excluded of rules.excludes) {
        const needle = excluded.toLowerCase();
        if (pathValue.includes(needle)) score -= 5;
      }
      if (score > (winner?.score ?? 0)) winner = { id, score };
    }
    return winner && winner.score >= 2 ? winner.id : undefined;
  }

  private decorateTree(nodes: TreeNode[], entries: Map<string, IndexedFile>): ModuleId[] {
    const modules: ModuleId[] = [];
    for (const node of nodes) {
      if (node.type === "file") {
        node.module = entries.get(node.path)?.module ?? this.classifyModule(node.path, "");
        if (node.module) modules.push(node.module);
      } else if (node.children) {
        const childModules = this.decorateTree(node.children, entries);
        const unique = [...new Set(childModules)];
        if (unique.length === 1) node.module = unique[0];
        modules.push(...childModules);
      }
    }
    return modules;
  }

  private async createIgnoreRules(scanRoot = ""): Promise<Ignore> {
    const ignoreRules = createIgnore();
    const absoluteScanRoot = path.join(this.guard.root, scanRoot);
    const gitignore = await fs
      .readFile(path.join(absoluteScanRoot, ".gitignore"), "utf8")
      .catch(() => "");
    if (gitignore) ignoreRules.add(gitignore);
    // Các rule an toàn được thêm sau cùng để negation trong .gitignore không thể mở lại chúng.
    ignoreRules.add(DEFAULT_IGNORES);
    const assistantRelative = toPosix(path.relative(absoluteScanRoot, this.config.assistantRoot));
    if (
      assistantRelative &&
      !assistantRelative.startsWith("..") &&
      !path.isAbsolute(assistantRelative)
    ) {
      ignoreRules.add(`${assistantRelative}/`);
    }
    return ignoreRules;
  }

  private async startWatcher() {
    const watchedRoots =
      this.config.projectIncludes.length > 0
        ? this.config.projectIncludes.map((value) => path.join(this.guard.root, value))
        : [this.guard.root];
    this.watcher = chokidar.watch(watchedRoots, {
      ignoreInitial: true,
      persistent: true,
      ignored: (candidate) => {
        const relative = toPosix(path.relative(this.guard.root, candidate));
        return (
          relative === ".git" ||
          relative.startsWith(".git/") ||
          /(?:^|\/)(?:node_modules|build|dist|\.gradle|\.smartkid-data)(?:\/|$)/.test(relative)
        );
      },
    });
    const schedule = (absolutePath: string) => {
      const relative = toPosix(path.relative(this.guard.root, absolutePath));
      if (relative && !relative.startsWith("..")) this.changedPaths.add(relative);
      if (this.rescanTimer) clearTimeout(this.rescanTimer);
      this.rescanTimer = setTimeout(() => void this.scan(), 250);
    };
    this.watcher.on("add", schedule).on("change", schedule).on("unlink", schedule);
  }
}

function escapeRegex(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
