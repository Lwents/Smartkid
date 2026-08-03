import {
  ChevronDown,
  ChevronRight,
  CircleAlert,
  File,
  FileCode2,
  FileCog,
  FileJson,
  FileText,
  Folder,
  FolderOpen,
  Star,
} from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import type { ModuleId, TreeNode } from "@smartkid/shared";

export function ProjectTree({
  tree,
  activePath,
  module,
  favorites,
  focusSignal,
  onOpen,
  onFavorite,
}: {
  tree: TreeNode[];
  activePath?: string;
  module: ModuleId;
  favorites: string[];
  focusSignal: number;
  onOpen: (path: string, line?: number) => void;
  onFavorite: (path: string, value: boolean) => void;
}) {
  const [expanded, setExpanded] = useState<Set<string>>(() => new Set());
  const [query, setQuery] = useState("");
  const [extension, setExtension] = useState("all");
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (focusSignal > 0) inputRef.current?.focus();
  }, [focusSignal]);

  const effectiveExpanded = useMemo(() => {
    if (!activePath) return expanded;
    const segments = activePath.split("/");
    const parents = segments
      .slice(0, -1)
      .map((_, index) => segments.slice(0, index + 1).join("/"));
    return new Set([...expanded, ...parents]);
  }, [activePath, expanded]);

  const extensions = useMemo(() => {
    const values = new Set<string>();
    walkNodes(tree, (node) => {
      if (node.type === "file" && node.extension) values.add(node.extension);
    });
    return [...values].sort();
  }, [tree]);

  const visible = useMemo(
    () => filterTree(tree, query, extension),
    [tree, query, extension],
  );

  return (
    <div className="tree-wrap">
      <div className="tree-filters">
        <div className="input-with-key">
          <input
            ref={inputRef}
            value={query}
            onChange={(event) => onSearch(event.target.value, setQuery, setExpanded, tree)}
            placeholder="Tìm file theo tên…"
            aria-label="Tìm file theo tên"
          />
          <kbd>⌘P</kbd>
        </div>
        <select
          value={extension}
          onChange={(event) => setExtension(event.target.value)}
          aria-label="Lọc loại file"
        >
          <option value="all">Tất cả loại file</option>
          {extensions.map((value) => (
            <option key={value} value={value}>
              {value || "không phần mở rộng"}
            </option>
          ))}
        </select>
      </div>
      <div className="tree-module-hint">
        <span className={`module-dot module-dot--${module}`} />
        AI đang ưu tiên {module.toUpperCase()}
      </div>
      <div className="tree" role="tree">
        {visible.length ? (
          visible.map((node) => (
            <TreeItem
              key={node.path}
              node={node}
              depth={0}
              activePath={activePath}
              expanded={effectiveExpanded}
              favorites={favorites}
              onToggle={(path) =>
                setExpanded((current) => {
                  const next = new Set(current);
                  if (next.has(path)) next.delete(path);
                  else next.add(path);
                  return next;
                })
              }
              onOpen={onOpen}
              onFavorite={onFavorite}
            />
          ))
        ) : (
          <div className="tree-empty">
            <FileText size={26} />
            Không tìm thấy file phù hợp
          </div>
        )}
      </div>
    </div>
  );
}

function TreeItem({
  node,
  depth,
  activePath,
  expanded,
  favorites,
  onToggle,
  onOpen,
  onFavorite,
}: {
  node: TreeNode;
  depth: number;
  activePath?: string;
  expanded: Set<string>;
  favorites: string[];
  onToggle: (path: string) => void;
  onOpen: (path: string) => void;
  onFavorite: (path: string, value: boolean) => void;
}) {
  const isExpanded = expanded.has(node.path);
  const isFavorite = favorites.includes(node.path);
  if (node.type === "directory") {
    return (
      <>
        <button
          className="tree-row tree-folder"
          style={{ paddingLeft: 8 + depth * 14 }}
          onClick={() => onToggle(node.path)}
          role="treeitem"
          aria-expanded={isExpanded}
        >
          {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
          {isExpanded ? <FolderOpen size={16} /> : <Folder size={16} />}
          <span>{node.name}</span>
          {node.module && <span className={`module-mini module-mini--${node.module}`} />}
        </button>
        {isExpanded &&
          node.children?.map((child) => (
            <TreeItem
              key={child.path}
              node={child}
              depth={depth + 1}
              activePath={activePath}
              expanded={expanded}
              favorites={favorites}
              onToggle={onToggle}
              onOpen={onOpen}
              onFavorite={onFavorite}
            />
          ))}
      </>
    );
  }
  return (
    <div
      className={`tree-row tree-file ${activePath === node.path ? "is-active" : ""}`}
      style={{ paddingLeft: 23 + depth * 14 }}
      role="treeitem"
      draggable
      onDragStart={(event) => {
        event.dataTransfer.setData("application/x-smartkid-file", node.path);
        event.dataTransfer.setData("text/plain", node.path);
      }}
      onDoubleClick={() => onOpen(node.path)}
    >
      <button className="tree-file__open" onClick={() => onOpen(node.path)} title={node.path}>
        {iconForFile(node)}
        <span>{node.name}</span>
        {node.tooLarge && <span className="tree-badge">lớn</span>}
        {node.changed && <span className="changed-dot" title="Vừa thay đổi" />}
        {node.error && <CircleAlert className="file-error" size={13} />}
      </button>
      <button
        className={`favorite-button ${isFavorite ? "is-favorite" : ""}`}
        title={isFavorite ? "Bỏ yêu thích" : "Yêu thích"}
        onClick={() => onFavorite(node.path, !isFavorite)}
      >
        <Star size={13} fill={isFavorite ? "currentColor" : "none"} />
      </button>
    </div>
  );
}

function iconForFile(node: TreeNode) {
  const iconProperties = { size: 15 };
  if ([".kt", ".kts", ".java", ".py", ".js", ".jsx", ".ts", ".tsx"].includes(node.extension ?? "")) {
    return <FileCode2 {...iconProperties} />;
  }
  if ([".json", ".json5"].includes(node.extension ?? "")) return <FileJson {...iconProperties} />;
  if ([".gradle", ".properties", ".toml"].includes(node.extension ?? "")) return <FileCog {...iconProperties} />;
  if ([".md", ".txt"].includes(node.extension ?? "")) return <FileText {...iconProperties} />;
  return <File {...iconProperties} />;
}

function filterTree(nodes: TreeNode[], query: string, extension: string): TreeNode[] {
  const needle = query.trim().toLocaleLowerCase();
  const output: TreeNode[] = [];
  for (const node of nodes) {
    if (node.type === "directory") {
      const children = filterTree(node.children ?? [], query, extension);
      if (children.length || (!needle && extension === "all")) output.push({ ...node, children });
    } else {
      const matchesQuery = !needle || node.name.toLocaleLowerCase().includes(needle) || node.path.toLocaleLowerCase().includes(needle);
      const matchesExtension = extension === "all" || node.extension === extension;
      if (matchesQuery && matchesExtension) output.push(node);
    }
  }
  return output;
}

function walkNodes(nodes: TreeNode[], visit: (node: TreeNode) => void) {
  for (const node of nodes) {
    visit(node);
    if (node.children) walkNodes(node.children, visit);
  }
}

function onSearch(
  value: string,
  setQuery: (value: string) => void,
  setExpanded: React.Dispatch<React.SetStateAction<Set<string>>>,
  tree: TreeNode[],
) {
  setQuery(value);
  if (!value.trim()) return;
  const directories = new Set<string>();
  walkNodes(tree, (node) => {
    if (node.type !== "file" || !node.path.toLowerCase().includes(value.toLowerCase())) return;
    const parts = node.path.split("/");
    for (let index = 1; index < parts.length; index += 1) {
      directories.add(parts.slice(0, index).join("/"));
    }
  });
  setExpanded((current) => new Set([...current, ...directories]));
}
