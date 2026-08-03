import path from "node:path";
import type {
  ReferenceOccurrence,
  SearchResult,
  XmlAnalysis,
  XmlView,
} from "@smartkid/shared";
import type { ProjectScanner } from "./scanner.js";

export class ReferenceService {
  constructor(private readonly scanner: ProjectScanner) {}

  find(identifier: string, kind = "auto", limit = 300): ReferenceOccurrence[] {
    const normalized = identifier.trim();
    if (!normalized) return [];
    const resource = parseResource(normalized, kind);
    const expressions = resource
      ? resourceExpressions(resource.type, resource.name)
      : [new RegExp(`\\b${escapeRegex(normalized)}\\b`)];
    const results: ReferenceOccurrence[] = [];

    for (const file of this.scanner.getFiles()) {
      for (let index = 0; index < file.lines.length; index += 1) {
        const line = file.lines[index] ?? "";
        const expression = expressions.find((candidate) => candidate.test(line));
        if (!expression) continue;
        expression.lastIndex = 0;
        const match = expression.exec(line);
        if (!match) continue;
        results.push({
          path: file.path,
          line: index + 1,
          endLine: index + 1,
          column: match.index + 1,
          preview: line.trim().slice(0, 400),
          match: match[0],
          module: file.module,
          kind: resource?.type ?? kind,
          relation: classifyRelation(line, normalized, resource),
        });
        if (results.length >= limit) return sortReferences(results);
      }
    }
    return sortReferences(results);
  }

  analyzeXml(relativePath: string, content: string): XmlAnalysis {
    const views: XmlView[] = [];
    const resources = new Map<
      string,
      { type: string; name: string; line: number; exists: boolean; occurrences: number }
    >();
    const tagExpression = /<([A-Za-z][\w.$:-]*)(\s[^<>]*?)?\/?>/gms;
    for (const match of content.matchAll(tagExpression)) {
      const tag = match[1];
      if (!tag || tag.startsWith("?") || tag.startsWith("!")) continue;
      const attributesText = match[2] ?? "";
      const line = lineAtOffset(content, match.index ?? 0);
      const attributes: Record<string, string> = {};
      for (const attribute of attributesText.matchAll(/([\w:.-]+)\s*=\s*["']([^"']*)["']/g)) {
        if (attribute[1]) attributes[attribute[1]] = attribute[2] ?? "";
      }
      views.push({
        tag,
        id: attributes["android:id"]?.replace(/^@\+?id\//, ""),
        line,
        attributes,
      });
    }

    for (const match of content.matchAll(/@([a-zA-Z_][\w-]*)\/([a-zA-Z_][\w.]*)/g)) {
      const type = match[1];
      const name = match[2];
      if (!type || !name) continue;
      const key = `${type}/${name}`;
      if (resources.has(key)) continue;
      const occurrences = this.find(`@${type}/${name}`, type);
      const exists =
        type === "id"
          ? occurrences.some((item) => item.relation === "definition") || content.includes(`@+id/${name}`)
          : occurrences.some((item) => item.relation === "definition") ||
            this.scanner.hasAndroidResource(type, name);
      resources.set(key, {
        type,
        name,
        line: lineAtOffset(content, match.index ?? 0),
        exists,
        occurrences: occurrences.length,
      });
    }

    const layoutName = path.basename(relativePath, ".xml");
    const bindingName = `${layoutName
      .split("_")
      .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
      .join("")}Binding`;
    const inflaters = [
      ...this.find(`@layout/${layoutName}`, "layout"),
      ...this.find(bindingName, "class"),
    ].filter((occurrence) => occurrence.path !== relativePath);
    const uniqueInflaters = uniqueBy(
      inflaters.filter((occurrence) =>
        /R\.layout\.|inflate|setContentView|DataBindingUtil|Binding/.test(occurrence.preview),
      ),
      (item) => `${item.path}:${item.line}`,
    );
    const warnings = [...resources.values()]
      .filter((resource) => resource.type !== "id" && !resource.exists)
      .map((resource) => `Không tìm thấy khai báo @${resource.type}/${resource.name}`);

    return {
      views,
      resources: [...resources.values()],
      inflaters: uniqueInflaters,
      warnings,
    };
  }

  directReferencesForFile(relativePath: string, content: string, limit = 20): SearchResult[] {
    const identifiers = [
      ...new Set([
        ...(content.match(/R\.(?:id|string|style|layout|drawable|color)\.([A-Za-z_]\w*)/g) ?? []),
        ...(content.match(/@[a-zA-Z_]+\/[A-Za-z_][\w.]*/g) ?? []),
        ...(content.match(/\b[A-Z][A-Za-z0-9_]*(?:Activity|Fragment|ViewModel|Repository|Service|Api)\b/g) ??
          []),
      ]),
    ].slice(0, 15);
    const results: SearchResult[] = [];
    for (const identifier of identifiers) {
      const references = this.find(identifier, "auto", 20);
      for (const reference of references) {
        if (reference.path === relativePath) continue;
        results.push(reference);
        if (results.length >= limit) return results;
      }
    }
    return uniqueBy(results, (item) => `${item.path}:${item.line}`);
  }
}

function parseResource(identifier: string, kind: string): { type: string; name: string } | undefined {
  const atResource = identifier.match(/^@\+?([a-zA-Z_]+)\/([A-Za-z_][\w.]*)$/);
  if (atResource?.[1] && atResource[2]) return { type: atResource[1], name: atResource[2] };
  const rResource = identifier.match(/^R\.([a-zA-Z_]+)\.([A-Za-z_][\w.]*)$/);
  if (rResource?.[1] && rResource[2]) return { type: rResource[1], name: rResource[2] };
  if (kind !== "auto" && /^(id|string|style|layout|drawable|color|menu|navigation|dimen|array)$/.test(kind)) {
    return { type: kind, name: identifier.replace(/^@\+?[a-zA-Z_]+\//, "") };
  }
  return undefined;
}

function resourceExpressions(type: string, name: string): RegExp[] {
  const escapedType = escapeRegex(type);
  const escapedName = escapeRegex(name);
  return [
    new RegExp(`@\\+?${escapedType}/${escapedName}\\b`),
    new RegExp(`R\\.${escapedType}\\.${escapedName}\\b`),
    new RegExp(`<${escapedType}\\s+[^>]*name=["']${escapedName}["']`),
    new RegExp(`<item\\s+[^>]*name=["']${escapedName}["'][^>]*type=["']${escapedType}["']`),
  ];
}

function classifyRelation(
  line: string,
  identifier: string,
  resource?: { type: string; name: string },
): ReferenceOccurrence["relation"] {
  if (
    /@\+id\//.test(line) ||
    (resource &&
      (new RegExp(`<${escapeRegex(resource.type)}\\s+[^>]*name=["']${escapeRegex(resource.name)}["']`).test(
        line,
      ) ||
        new RegExp(`<item\\s+[^>]*name=["']${escapeRegex(resource.name)}["']`).test(line)))
  ) {
    return "definition";
  }
  if (new RegExp(`\\b(?:class|interface|object|fun)\\s+${escapeRegex(identifier)}\\b`).test(line)) {
    return "definition";
  }
  if (/AndroidManifest|<activity|<service|<provider|<receiver/.test(line)) return "declaration";
  if (/\bimplementation\b|\bapi\b|\bkapt\b|\bksp\b/.test(line)) return "dependency";
  if (/\w+\s*\(/.test(line)) return "call";
  return "usage";
}

function lineAtOffset(content: string, offset: number): number {
  return content.slice(0, offset).split(/\r?\n/).length;
}

function escapeRegex(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function uniqueBy<T>(values: T[], key: (value: T) => string): T[] {
  const seen = new Set<string>();
  return values.filter((value) => {
    const id = key(value);
    if (seen.has(id)) return false;
    seen.add(id);
    return true;
  });
}

function sortReferences(values: ReferenceOccurrence[]) {
  const priority: Record<ReferenceOccurrence["relation"], number> = {
    definition: 0,
    declaration: 1,
    usage: 2,
    call: 3,
    dependency: 4,
  };
  return values.sort(
    (left, right) =>
      priority[left.relation] - priority[right.relation] ||
      left.path.localeCompare(right.path) ||
      left.line - right.line,
  );
}
