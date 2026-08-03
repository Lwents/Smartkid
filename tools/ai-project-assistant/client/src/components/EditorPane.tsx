import Editor, { type Monaco, type OnMount } from "@monaco-editor/react";
import {
  Bot,
  Braces,
  ChevronDown,
  Code2,
  Eye,
  FileWarning,
  GitCompareArrows,
  Pencil,
  RotateCcw,
  Save,
  X,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import type { FileDocument, PatchProposal } from "@smartkid/shared";

export type EditorSelection = {
  path: string;
  content: string;
  startLine: number;
  endLine: number;
};

export function EditorPane({
  tabs,
  activePath,
  draft,
  targetLine,
  readOnly,
  dirty,
  latestPatch,
  onActivate,
  onClose,
  onDraft,
  onSave,
  onAskSelection,
  onOpenPatch,
  onUndoPatch,
}: {
  tabs: FileDocument[];
  activePath?: string;
  draft: string;
  targetLine?: number;
  readOnly: boolean;
  dirty: boolean;
  latestPatch?: PatchProposal;
  onActivate: (path: string) => void;
  onClose: (path: string) => void;
  onDraft: (content: string) => void;
  onSave: () => void;
  onAskSelection: (selection: EditorSelection) => void;
  onOpenPatch: (patch: PatchProposal) => void;
  onUndoPatch: (patch: PatchProposal) => void;
}) {
  const document = tabs.find((tab) => tab.path === activePath);
  const editorRef = useRef<Parameters<OnMount>[0] | undefined>(undefined);
  const [editablePath, setEditablePath] = useState<string>();
  const [selection, setSelection] = useState<EditorSelection>();

  useEffect(() => {
    if (!targetLine || !editorRef.current) return;
    editorRef.current.revealLineInCenter(targetLine);
    editorRef.current.setPosition({ lineNumber: targetLine, column: 1 });
    editorRef.current.focus();
  }, [targetLine, activePath]);

  if (!document) {
    return (
      <section className="editor-pane editor-empty">
        <div className="empty-illustration">
          <Code2 size={46} />
        </div>
        <h2>Mở một file để bắt đầu</h2>
        <p>Chọn file trong cây dự án hoặc nhấn Ctrl/Cmd + P để tìm nhanh.</p>
        <div className="shortcut-grid">
          <span>Tìm file</span>
          <kbd>⌘ P</kbd>
          <span>Tìm trong dự án</span>
          <kbd>⌘ ⇧ F</kbd>
          <span>Hỏi AI</span>
          <kbd>⌘ ↵</kbd>
        </div>
      </section>
    );
  }

  const activeSelection = selection?.path === document.path ? selection : undefined;
  const canEdit = editablePath === document.path && !readOnly && !document.readOnly;
  const handleMount: OnMount = (editor, monaco) => {
    editorRef.current = editor;
    defineTheme(monaco);
    monaco.editor.setTheme("smartkid-night");
    editor.onDidChangeCursorSelection(({ selection: range }) => {
      const value = editor.getModel()?.getValueInRange(range) ?? "";
      if (!value.trim()) {
        setSelection(undefined);
        return;
      }
      setSelection({
        path: document.path,
        content: value,
        startLine: range.startLineNumber,
        endLine: range.endLineNumber,
      });
    });
  };

  return (
    <section className="editor-pane">
      <div className="editor-tabs">
        <div className="tab-scroll">
          {tabs.map((tab) => (
            <button
              key={tab.path}
              className={`editor-tab ${tab.path === activePath ? "is-active" : ""}`}
              onClick={() => onActivate(tab.path)}
              title={tab.path}
            >
              <Braces size={14} />
              <span>{tab.path.split("/").at(-1)}</span>
              {tab.path === activePath && dirty && <i />}
              <X
                size={13}
                onClick={(event) => {
                  event.stopPropagation();
                  onClose(tab.path);
                }}
              />
            </button>
          ))}
        </div>
        <button className="tab-overflow" title="Danh sách tab">
          <ChevronDown size={15} />
        </button>
      </div>

      <div className="editor-toolbar">
        <div className="breadcrumb" title={document.path}>
          {document.path.split("/").map((part, index) => (
            <span key={`${part}-${index}`}>
              {index > 0 && <b>›</b>}
              {part}
            </span>
          ))}
        </div>
        <div className="editor-actions">
          {activeSelection && (
            <button className="button-accent-quiet" onClick={() => onAskSelection(activeSelection)}>
              <Bot size={15} /> Hỏi AI
            </button>
          )}
          <button
            onClick={() =>
              setEditablePath((value) => (value === document.path ? undefined : document.path))
            }
            disabled={readOnly || document.readOnly}
            title={document.readOnly ? "File bị khóa vì lý do bảo mật" : ""}
          >
            {canEdit ? <Eye size={15} /> : <Pencil size={15} />}
            {canEdit ? "Chỉ đọc" : "Chỉnh sửa"}
          </button>
          <button onClick={onSave} disabled={!dirty || !canEdit} title="Ctrl/Cmd + S">
            <Save size={15} /> Lưu
          </button>
          {latestPatch && (
            <>
              <button onClick={() => onOpenPatch(latestPatch)}>
                <GitCompareArrows size={15} /> Diff
              </button>
              {latestPatch.status === "applied" && (
                <button onClick={() => onUndoPatch(latestPatch)}>
                  <RotateCcw size={15} /> Hoàn tác
                </button>
              )}
            </>
          )}
        </div>
      </div>

      {(readOnly || document.readOnly) && (
        <div className="readonly-banner">
          <FileWarning size={14} />
          {document.redacted
            ? "File có nội dung nhạy cảm đã bị khóa."
            : "Đang ở chế độ chỉ đọc. Có thể thay đổi trong Settings."}
        </div>
      )}

      <div className="monaco-host">
        <Editor
          path={document.path}
          language={document.language}
          value={draft}
          onChange={(value) => onDraft(value ?? "")}
          onMount={handleMount}
          theme="smartkid-night"
          options={{
            readOnly: !canEdit,
            automaticLayout: true,
            fontSize: 13,
            fontFamily: "'JetBrains Mono', 'SFMono-Regular', Consolas, monospace",
            fontLigatures: true,
            lineHeight: 21,
            minimap: { enabled: true, scale: 0.8 },
            lineNumbers: "on",
            glyphMargin: true,
            folding: true,
            wordWrap: "off",
            renderLineHighlight: "all",
            smoothScrolling: true,
            padding: { top: 12, bottom: 12 },
            scrollbar: { verticalScrollbarSize: 10, horizontalScrollbarSize: 10 },
          }}
        />
      </div>
      <div className="editor-statusbar">
        <span>Ln {targetLine ?? 1}, Col 1</span>
        <span>{document.language.toUpperCase()}</span>
        <span>UTF-8</span>
        <span>{document.lineCount.toLocaleString("vi-VN")} dòng</span>
        {dirty && <span className="dirty-label">CHƯA LƯU</span>}
      </div>
    </section>
  );
}

function defineTheme(monaco: Monaco) {
  monaco.editor.defineTheme("smartkid-night", {
    base: "vs-dark",
    inherit: true,
    rules: [
      { token: "comment", foreground: "667085", fontStyle: "italic" },
      { token: "keyword", foreground: "C084FC" },
      { token: "string", foreground: "9DE2B3" },
      { token: "number", foreground: "F9B982" },
      { token: "type", foreground: "79C7FF" },
    ],
    colors: {
      "editor.background": "#0d111b",
      "editor.foreground": "#d7dce5",
      "editorLineNumber.foreground": "#4f586a",
      "editorLineNumber.activeForeground": "#a7b0c0",
      "editor.lineHighlightBackground": "#151b28",
      "editor.selectionBackground": "#314b7a88",
      "editor.inactiveSelectionBackground": "#26385766",
      "editorCursor.foreground": "#8b5cf6",
      "editorIndentGuide.background1": "#1c2433",
      "editorIndentGuide.activeBackground1": "#35405a",
    },
  });
}
