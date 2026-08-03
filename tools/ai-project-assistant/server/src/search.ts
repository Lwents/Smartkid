import type {
  ModuleId,
  SearchMode,
  SearchResult,
} from "@smartkid/shared";
import { AppError } from "./errors.js";
import type { IndexedFile, ProjectScanner } from "./scanner.js";

export type SearchRequest = {
  query: string;
  mode: SearchMode;
  caseSensitive?: boolean;
  module?: ModuleId;
  paths?: string[];
  limit?: number;
};

export class SearchService {
  constructor(private readonly scanner: ProjectScanner) {}

  search(request: SearchRequest): SearchResult[] {
    const query = request.query.trim();
    if (!query) return [];
    const limit = Math.min(Math.max(request.limit ?? 200, 1), 500);
    const selectedPaths = request.paths ? new Set(request.paths) : undefined;
    const files = this.scanner.getFiles().filter((file) => {
      if (request.module && file.module !== request.module) return false;
      if (selectedPaths && !selectedPaths.has(file.path)) return false;
      return true;
    });

    if (request.mode === "file" || request.mode === "path") {
      return this.scanner
        .getKnownFiles()
        .filter((file) => {
          if (request.module && file.module !== request.module) return false;
          if (selectedPaths && !selectedPaths.has(file.path)) return false;
          return true;
        })
        .filter((file) => {
          const haystack = request.mode === "file" ? file.name : file.path;
          return includes(haystack, query, request.caseSensitive);
        })
        .slice(0, limit)
        .map((file) => this.fileResult(file, query, request.mode));
    }

    const matcher = this.createMatcher(request);
    const results: SearchResult[] = [];
    for (const file of files) {
      for (let lineIndex = 0; lineIndex < file.lines.length; lineIndex += 1) {
        const line = file.lines[lineIndex] ?? "";
        const match = matcher(line);
        if (!match) continue;
        results.push({
          path: file.path,
          line: lineIndex + 1,
          endLine: lineIndex + 1,
          column: match.index + 1,
          preview: line.trim().slice(0, 400),
          match: match.value,
          module: file.module,
          kind: request.mode,
        });
        if (results.length >= limit) return results;
      }
    }
    return results;
  }

  relatedByTerms(question: string, module?: ModuleId, limit = 30): SearchResult[] {
    const terms = [
      ...new Set(
        question
          .match(/[A-Za-z_][A-Za-z0-9_.-]{2,}/g)
          ?.filter((term) => !STOP_WORDS.has(term.toLowerCase()))
          .sort((left, right) => right.length - left.length) ?? [],
      ),
    ].slice(0, 8);
    const merged = new Map<string, SearchResult>();
    for (const term of terms) {
      const results = [
        ...this.search({ query: term, mode: "text", module, limit }),
        ...this.search({ query: term, mode: "file", module, limit }),
        ...this.search({ query: term, mode: "path", module, limit }),
      ];
      for (const result of results) {
        const key = `${result.path}:${result.line}`;
        if (!merged.has(key)) merged.set(key, result);
        if (merged.size >= limit) return [...merged.values()];
      }
    }
    return [...merged.values()];
  }

  private createMatcher(request: SearchRequest): (line: string) => { index: number; value: string } | null {
    const query = request.query;
    if (request.mode === "regex") {
      if (query.length > 300) {
        throw new AppError("REGEX_TOO_LONG", "Biểu thức chính quy dài quá giới hạn");
      }
      let expression: RegExp;
      try {
        expression = new RegExp(query, request.caseSensitive ? "" : "i");
      } catch (error) {
        throw new AppError("INVALID_REGEX", "Biểu thức chính quy không hợp lệ", 422, {
          reason: error instanceof Error ? error.message : String(error),
        });
      }
      return (line) => {
        expression.lastIndex = 0;
        const match = expression.exec(line);
        return match ? { index: match.index, value: match[0] } : null;
      };
    }

    let pattern: string;
    if (request.mode === "class") {
      pattern = `\\b(?:class|interface|object|enum\\s+class|record)\\s+${escapeRegex(query)}\\b`;
    } else if (request.mode === "function") {
      pattern = `\\b(?:fun\\s+${escapeRegex(query)}|function\\s+${escapeRegex(query)}|${escapeRegex(query)}\\s*\\()`;
    } else if (request.mode === "android-id") {
      const clean = query.replace(/^@\+?id\//, "").replace(/^R\.id\./, "");
      pattern = `(?:@\\+?id/${escapeRegex(clean)}\\b|R\\.id\\.${escapeRegex(clean)}\\b)`;
    } else if (request.mode === "exact") {
      pattern = escapeRegex(query);
    } else {
      pattern = escapeRegex(query);
    }
    const expression = new RegExp(pattern, request.caseSensitive || request.mode === "exact" ? "" : "i");
    return (line) => {
      const match = expression.exec(line);
      return match ? { index: match.index, value: match[0] } : null;
    };
  }

  private fileResult(
    file: Pick<IndexedFile, "path" | "name" | "module">,
    query: string,
    mode: SearchMode,
  ): SearchResult {
    const value = mode === "file" ? file.name : file.path;
    return {
      path: file.path,
      line: 1,
      endLine: 1,
      column: Math.max(value.toLowerCase().indexOf(query.toLowerCase()) + 1, 1),
      preview: file.path,
      match: query,
      module: file.module,
      kind: mode,
    };
  }
}

function includes(value: string, query: string, caseSensitive = false) {
  return caseSensitive
    ? value.includes(query)
    : value.toLocaleLowerCase().includes(query.toLocaleLowerCase());
}

function escapeRegex(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

const STOP_WORDS = new Set([
  "this",
  "that",
  "with",
  "from",
  "file",
  "code",
  "đoạn",
  "này",
  "được",
  "những",
  "trong",
  "muốn",
  "hãy",
  "please",
  "what",
  "where",
]);
