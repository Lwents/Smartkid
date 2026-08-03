import {
  Clock3,
  FilePlus2,
  Files,
  FolderPlus,
  GitCompareArrows,
  History,
  LoaderCircle,
  Menu,
  PanelLeftClose,
  Pencil,
  RefreshCw,
  Search,
  Settings,
  Sparkles,
  Tags,
  Trash2,
  X,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type {
  AssistantMode,
  AssistantSettings,
  ChatMessage,
  ContextScope,
  Conversation,
  FileDocument,
  ModuleId,
  ModuleMapping,
  ModuleSummary,
  PatchProposal,
  ProjectInfo,
  SearchMode,
  SearchResult,
  TreeNode,
} from "@smartkid/shared";
import { api, errorMessage } from "./api";
import { ChatPane } from "./components/ChatPane";
import { EditorPane, type EditorSelection } from "./components/EditorPane";
import { ModulePicker } from "./components/ModulePicker";
import { PatchDialog } from "./components/PatchDialog";
import { ProjectTree } from "./components/ProjectTree";
import { SearchDialog } from "./components/SearchDialog";
import { SettingsDialog } from "./components/SettingsDialog";
import { XmlInspector } from "./components/XmlInspector";

type ModulePayload = { modules: ModuleSummary[]; mapping: ModuleMapping };
type FileMetadata = { recent: Array<{ path: string; openedAt: string }>; favorites: string[] };
type AiResult = {
  conversation: Conversation;
  message: ChatMessage;
  patch?: PatchProposal;
};
type Toast = { id: string; type: "success" | "error" | "info"; message: string };

export default function App() {
  const [booting, setBooting] = useState(true);
  const [fatalError, setFatalError] = useState<string>();
  const [info, setInfo] = useState<ProjectInfo>();
  const [tree, setTree] = useState<TreeNode[]>([]);
  const [modules, setModules] = useState<ModuleSummary[]>([]);
  const [settings, setSettings] = useState<AssistantSettings>();
  const [selectedModule, setSelectedModule] = useState<ModuleId>();
  const [favorites, setFavorites] = useState<string[]>([]);
  const [tabs, setTabs] = useState<FileDocument[]>([]);
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [activePath, setActivePath] = useState<string>();
  const [targetLine, setTargetLine] = useState<number>();
  const [readingFile, setReadingFile] = useState(false);
  const [selection, setSelection] = useState<EditorSelection>();
  const [attachedPaths, setAttachedPaths] = useState<string[]>([]);
  const [patches, setPatches] = useState<PatchProposal[]>([]);
  const [patchDialog, setPatchDialog] = useState<PatchProposal>();
  const [patchBusy, setPatchBusy] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [conversationId, setConversationId] = useState<string>();
  const [aiResponding, setAiResponding] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchResults, setSearchResults] = useState<SearchResult[]>([]);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [settingsSaving, setSettingsSaving] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [showXmlInspector, setShowXmlInspector] = useState(true);
  const [focusFileSearch, setFocusFileSearch] = useState(0);
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [toasts, setToasts] = useState<Toast[]>([]);
  const abortRef = useRef<AbortController | undefined>(undefined);

  const toast = useCallback((message: string, type: Toast["type"] = "info") => {
    const id = crypto.randomUUID();
    setToasts((current) => [...current, { id, message, type }]);
    window.setTimeout(() => setToasts((current) => current.filter((item) => item.id !== id)), 4200);
  }, []);

  const loadDashboard = useCallback(async () => {
    const [projectInfo, projectTree, moduleData, currentSettings, metadata, patchHistory, chatHistory] =
      await Promise.all([
        api<ProjectInfo>("/project/info"),
        api<TreeNode[]>("/project/tree"),
        api<ModulePayload>("/modules"),
        api<AssistantSettings>("/settings"),
        api<FileMetadata>("/files/metadata"),
        api<PatchProposal[]>("/patches"),
        api<Conversation[]>("/ai/conversations"),
      ]);
    setInfo(projectInfo);
    setTree(projectTree);
    setModules(moduleData.modules);
    setSettings(currentSettings);
    setFavorites(metadata.favorites);
    setPatches(patchHistory);
    setConversations(chatHistory);
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadDashboard()
        .catch((error) => setFatalError(errorMessage(error)))
        .finally(() => setBooting(false));
    }, 0);
    return () => window.clearTimeout(timer);
  }, [loadDashboard]);

  const activeDocument = tabs.find((tab) => tab.path === activePath);
  const activeDraft = activePath ? drafts[activePath] ?? activeDocument?.content ?? "" : "";
  const dirty = Boolean(activeDocument && activeDraft !== activeDocument.content);
  const latestPatch = useMemo(
    () => patches.find((patch) => activePath && patch.affectedPaths.includes(activePath)),
    [patches, activePath],
  );

  const openFile = useCallback(
    async (path: string, line = 1, force = false) => {
      setActivePath(path);
      setTargetLine(line);
      setSelection(undefined);
      const existing = tabs.find((tab) => tab.path === path);
      if (existing && !force) return;
      setReadingFile(true);
      try {
        const document = await api<FileDocument>(`/files/read?path=${encodeURIComponent(path)}`);
        setTabs((current) => {
          const found = current.some((tab) => tab.path === path);
          return found
            ? current.map((tab) => (tab.path === path ? document : tab))
            : [...current, document];
        });
        setDrafts((current) => ({ ...current, [path]: document.content }));
        if (document.xml) setShowXmlInspector(true);
      } catch (error) {
        toast(errorMessage(error), "error");
        if (force) {
          setTabs((current) => current.filter((tab) => tab.path !== path));
          setDrafts((current) => {
            const next = { ...current };
            delete next[path];
            return next;
          });
        }
      } finally {
        setReadingFile(false);
      }
    },
    [tabs, toast],
  );

  const refreshProject = useCallback(
    async (showMessage = true) => {
      try {
        const projectInfo = await api<ProjectInfo>("/project/scan", { method: "POST" });
        const [projectTree, moduleData, patchHistory] = await Promise.all([
          api<TreeNode[]>("/project/tree"),
          api<ModulePayload>("/modules"),
          api<PatchProposal[]>("/patches"),
        ]);
        setInfo(projectInfo);
        setTree(projectTree);
        setModules(moduleData.modules);
        setPatches(patchHistory);
        if (showMessage) toast(`Đã lập chỉ mục ${projectInfo.indexedFiles} file`, "success");
      } catch (error) {
        toast(errorMessage(error), "error");
      }
    },
    [toast],
  );

  const reloadAffectedTabs = useCallback(
    async (affectedPaths: string[]) => {
      for (const document of tabs) {
        if (affectedPaths.some((path) => document.path === path || document.path.startsWith(`${path}/`))) {
          await openFile(document.path, 1, true);
        }
      }
    },
    [tabs, openFile],
  );

  const createManualPatch = useCallback(
    async (endpoint: string, body: Record<string, unknown>) => {
      try {
        const patch = await api<PatchProposal>(endpoint, { method: "POST", body });
        setPatches((current) => [patch, ...current]);
        setPatchDialog(patch);
        toast("Đã tạo diff. Chưa có file nào được ghi.", "info");
      } catch (error) {
        toast(errorMessage(error), "error");
      }
    },
    [toast],
  );

  const saveActive = useCallback(() => {
    if (!activeDocument || !dirty) return;
    void createManualPatch("/files/update", {
      path: activeDocument.path,
      content: activeDraft,
      expectedVersion: activeDocument.version,
      title: `Lưu ${activeDocument.path}`,
      summary: "Thay đổi từ Monaco Editor; đang chờ xác nhận.",
    });
  }, [activeDocument, activeDraft, dirty, createManualPatch]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const modifier = event.ctrlKey || event.metaKey;
      if (!modifier) return;
      if (event.key.toLowerCase() === "p" && !event.shiftKey) {
        event.preventDefault();
        setFocusFileSearch((value) => value + 1);
      } else if (event.key.toLowerCase() === "f" && event.shiftKey) {
        event.preventDefault();
        setSearchOpen(true);
      } else if (event.key.toLowerCase() === "s") {
        event.preventDefault();
        saveActive();
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [saveActive]);

  const applyPatch = async () => {
    if (!patchDialog) return;
    setPatchBusy(true);
    try {
      const updated = await api<PatchProposal>("/patch/apply", {
        method: "POST",
        body: { id: patchDialog.id },
      });
      setPatchDialog(updated);
      setPatches((current) => current.map((patch) => (patch.id === updated.id ? updated : patch)));
      await Promise.all([refreshProject(false), reloadAffectedTabs(updated.affectedPaths)]);
      toast("Đã áp dụng bản vá và tạo backup", "success");
    } catch (error) {
      toast(errorMessage(error), "error");
    } finally {
      setPatchBusy(false);
    }
  };

  const undoPatch = async (patch = patchDialog) => {
    if (!patch) return;
    setPatchDialog(patch);
    setPatchBusy(true);
    try {
      const updated = await api<PatchProposal>("/patch/undo", {
        method: "POST",
        body: { id: patch.id },
      });
      setPatchDialog(updated);
      setPatches((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      await Promise.all([refreshProject(false), reloadAffectedTabs(updated.affectedPaths)]);
      toast("Đã hoàn tác về snapshot trước thay đổi", "success");
    } catch (error) {
      toast(errorMessage(error), "error");
    } finally {
      setPatchBusy(false);
    }
  };

  const rejectPatch = async () => {
    if (!patchDialog) return;
    setPatchBusy(true);
    try {
      const updated = await api<PatchProposal>("/patch/reject", {
        method: "POST",
        body: { id: patchDialog.id },
      });
      setPatches((current) => current.map((patch) => (patch.id === updated.id ? updated : patch)));
      setPatchDialog(undefined);
      toast("Đã từ chối bản vá; không file nào bị thay đổi", "info");
    } catch (error) {
      toast(errorMessage(error), "error");
    } finally {
      setPatchBusy(false);
    }
  };

  const sendToAi = async (question: string, mode: AssistantMode, scope: ContextScope) => {
    if (!selectedModule) return;
    const controller = new AbortController();
    abortRef.current = controller;
    setAiResponding(true);
    const temporaryUser: ChatMessage = {
      id: `temporary-${Date.now()}`,
      role: "user",
      content: question,
      createdAt: new Date().toISOString(),
    };
    setMessages((current) => [...current, temporaryUser]);
    try {
      const result = await api<AiResult>("/ai/chat", {
        method: "POST",
        signal: controller.signal,
        body: {
          question,
          mode,
          scope,
          module: selectedModule,
          conversationId,
          activeFile: activePath,
          attachedPaths,
          selection,
        },
      });
      setConversationId(result.conversation.id);
      setMessages(result.conversation.messages);
      if (result.patch) {
        setPatches((current) => [result.patch!, ...current.filter((item) => item.id !== result.patch!.id)]);
        setPatchDialog(result.patch);
      }
      setConversations(await api<Conversation[]>("/ai/conversations"));
    } catch (error) {
      if ((error as Error).name !== "AbortError") toast(errorMessage(error), "error");
      setMessages((current) => current.filter((message) => message.id !== temporaryUser.id));
    } finally {
      setAiResponding(false);
      abortRef.current = undefined;
    }
  };

  const newConversation = async () => {
    if (!selectedModule) return;
    try {
      const conversation = await api<Conversation>("/ai/conversations", {
        method: "POST",
        body: { module: selectedModule },
      });
      setConversationId(conversation.id);
      setMessages([]);
      setConversations((current) => [conversation, ...current]);
    } catch (error) {
      toast(errorMessage(error), "error");
    }
  };

  const openConversation = async (id: string) => {
    try {
      const conversation = await api<Conversation>(`/ai/conversations/${id}`);
      setConversationId(conversation.id);
      setMessages(conversation.messages);
      if (conversation.module) setSelectedModule(conversation.module);
    } catch (error) {
      toast(errorMessage(error), "error");
    }
  };

  const searchProject = async (query: string, mode: SearchMode, caseSensitive: boolean) => {
    setSearchLoading(true);
    try {
      setSearchResults(
        await api<SearchResult[]>("/files/search", {
          method: "POST",
          body: { query, mode, caseSensitive, limit: 500 },
        }),
      );
    } catch (error) {
      toast(errorMessage(error), "error");
    } finally {
      setSearchLoading(false);
    }
  };

  const favoriteFile = async (path: string, favorite: boolean) => {
    try {
      const result = await api<{ favorites: string[] }>("/files/favorite", {
        method: "PUT",
        body: { path, favorite },
      });
      setFavorites(result.favorites);
    } catch (error) {
      toast(errorMessage(error), "error");
    }
  };

  const saveSettings = async (next: AssistantSettings) => {
    setSettingsSaving(true);
    try {
      if (JSON.stringify(next.moduleMapping) !== JSON.stringify(settings?.moduleMapping)) {
        const result = await api<ModulePayload>("/modules/mapping", {
          method: "PUT",
          body: next.moduleMapping,
        });
        setModules(result.modules);
      }
      const saved = await api<AssistantSettings>("/settings", { method: "PUT", body: { ...next } });
      setSettings(saved);
      setSettingsOpen(false);
      await refreshProject(false);
      toast("Đã lưu cài đặt", "success");
    } catch (error) {
      toast(errorMessage(error), "error");
    } finally {
      setSettingsSaving(false);
    }
  };

  if (fatalError) {
    return (
      <main className="fatal-state">
        <Sparkles size={34} />
        <h1>Không thể khởi động SMARTKID AI</h1>
        <p>{fatalError}</p>
        <button onClick={() => window.location.reload()}>Thử lại</button>
      </main>
    );
  }
  if (!selectedModule) {
    return (
      <ModulePicker
        modules={modules}
        info={info}
        loading={booting}
        onSelect={(module) => setSelectedModule(module)}
      />
    );
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="topbar-brand">
          <button className="mobile-menu" onClick={() => setSidebarOpen((value) => !value)}><Menu size={18} /></button>
          <span><Sparkles size={17} /></span>
          <strong>SMARTKID <i>AI</i></strong>
          <small>PROJECT ASSISTANT</small>
        </div>
        <div className="project-title">
          <span className="status-dot online" />
          <strong>{info?.name}</strong>
          <small>{info?.android.detected ? `Android · ${info.android.languages.join(" + ") || "Gradle/XML"}` : "Chưa phát hiện Android"}</small>
        </div>
        <div className="topbar-actions">
          {readingFile && <span className="inline-loading"><LoaderCircle className="spin" size={14} /> Đang đọc file</span>}
          <button onClick={() => setSearchOpen(true)}><Search size={15} /><span>Tìm kiếm</span><kbd>⌘⇧F</kbd></button>
          <button onClick={() => setSettingsOpen(true)}><Settings size={16} /></button>
        </div>
      </header>

      <div className={`workspace ${sidebarOpen ? "" : "sidebar-collapsed"}`}>
        <nav className="activity-bar">
          <button className="is-active" title="Explorer" onClick={() => setSidebarOpen(true)}><Files size={20} /></button>
          <button title="Tìm trong dự án" onClick={() => setSearchOpen(true)}><Search size={20} /></button>
          <button title="Lịch sử thay đổi" onClick={() => setHistoryOpen(true)}><GitCompareArrows size={20} /></button>
          <button title="Settings" onClick={() => setSettingsOpen(true)}><Settings size={20} /></button>
          <span />
          <button title="Đóng sidebar" onClick={() => setSidebarOpen(false)}><PanelLeftClose size={19} /></button>
        </nav>

        <aside className="project-sidebar">
          <div className="sidebar-heading">
            <span>EXPLORER</span>
            <div>
              <button title="Tạo file" onClick={() => {
                const path = window.prompt("Đường dẫn file mới tính từ project root:");
                if (path) void createManualPatch("/files/create", { path, content: "" });
              }}><FilePlus2 size={15} /></button>
              <button title="Tạo thư mục" onClick={() => {
                const path = window.prompt("Đường dẫn thư mục mới tính từ project root:");
                if (path) void createManualPatch("/folders/create", { path });
              }}><FolderPlus size={15} /></button>
              <button title="Đổi tên thư mục" onClick={() => {
                const path = window.prompt("Đường dẫn thư mục cần đổi tên:");
                if (!path) return;
                const destination = window.prompt("Đường dẫn thư mục mới:", path);
                if (destination && destination !== path) {
                  void createManualPatch("/folders/rename", { path, destination });
                }
              }}><Pencil size={14} /></button>
              <button title="Xóa file hoặc thư mục theo đường dẫn" onClick={() => {
                const path = window.prompt("Đường dẫn file/thư mục cần xóa (sẽ xem diff trước):");
                if (path) void createManualPatch("/files/delete", { path });
              }}><Trash2 size={14} /></button>
              <button title="Refresh" onClick={() => void refreshProject()}><RefreshCw size={15} /></button>
            </div>
          </div>
          <div className="module-switcher">
            <span className={`module-icon module-icon--${selectedModule}`}><Tags size={15} /></span>
            <label>
              <small>MODULE ĐANG CHỌN</small>
              <select value={selectedModule} onChange={(event) => setSelectedModule(event.target.value as ModuleId)}>
                {modules.map((module) => <option key={module.id} value={module.id}>{module.name} · {module.fileCount} file</option>)}
              </select>
            </label>
          </div>
          {activePath && (
            <div className="file-operations">
              <button title="Đổi tên" onClick={() => {
                const destination = window.prompt("Đường dẫn mới:", activePath);
                if (destination && destination !== activePath) void createManualPatch("/files/rename", { path: activePath, destination });
              }}>Đổi tên</button>
              <button title="Di chuyển" onClick={() => {
                const destination = window.prompt("Di chuyển tới đường dẫn:", activePath);
                if (destination && destination !== activePath) void createManualPatch("/files/move", { path: activePath, destination });
              }}>Di chuyển</button>
              <button className="danger" title="Xóa (sẽ xem diff trước)" onClick={() => void createManualPatch("/files/delete", { path: activePath })}><Trash2 size={12} /> Xóa</button>
            </div>
          )}
          <ProjectTree
            tree={tree}
            activePath={activePath}
            module={selectedModule}
            favorites={favorites}
            focusSignal={focusFileSearch}
            onOpen={(path) => void openFile(path)}
            onFavorite={(path, value) => void favoriteFile(path, value)}
          />
          <div className="sidebar-footer">
            <span>{info?.indexedFiles ?? 0} indexed</span>
            <span>{info?.ignoredFiles ?? 0} ignored</span>
            <button onClick={() => void refreshProject()} title="Quét lại"><RefreshCw size={12} /></button>
          </div>
        </aside>

        <main className="code-workspace">
          <EditorPane
            tabs={tabs}
            activePath={activePath}
            draft={activeDraft}
            targetLine={targetLine}
            readOnly={settings?.readOnly ?? false}
            dirty={dirty}
            latestPatch={latestPatch}
            onActivate={(path) => { setActivePath(path); setSelection(undefined); }}
            onClose={(path) => {
              const document = tabs.find((tab) => tab.path === path);
              if (document && drafts[path] !== document.content && !window.confirm("Đóng tab và bỏ phần chỉnh sửa chưa tạo diff?")) return;
              setTabs((current) => current.filter((tab) => tab.path !== path));
              if (activePath === path) {
                const remaining = tabs.filter((tab) => tab.path !== path);
                setActivePath(remaining.at(-1)?.path);
              }
            }}
            onDraft={(content) => activePath && setDrafts((current) => ({ ...current, [activePath]: content }))}
            onSave={saveActive}
            onAskSelection={setSelection}
            onOpenPatch={setPatchDialog}
            onUndoPatch={(patch) => setPatchDialog(patch)}
          />
          {activeDocument?.xml && showXmlInspector && (
            <XmlInspector
              analysis={activeDocument.xml}
              onClose={() => setShowXmlInspector(false)}
              onOpen={(path, line) => void openFile(path, line)}
            />
          )}
          {activeDocument?.xml && !showXmlInspector && (
            <button className="xml-reopen" onClick={() => setShowXmlInspector(true)}>
              XML Inspector
            </button>
          )}
        </main>

        <ChatPane
          module={selectedModule}
          activeFile={activeDocument}
          selection={selection}
          attachedPaths={attachedPaths}
          messages={messages}
          conversations={conversations}
          responding={aiResponding}
          onAttach={(path) => setAttachedPaths((current) => current.includes(path) ? current : [...current, path])}
          onRemoveAttachment={(path) => setAttachedPaths((current) => current.filter((item) => item !== path))}
          onOpenFile={(path, line) => void openFile(path, line)}
          onSend={(question, mode, scope) => void sendToAi(question, mode, scope)}
          onStop={() => abortRef.current?.abort()}
          onNewChat={() => void newConversation()}
          onOpenConversation={(id) => void openConversation(id)}
        />
      </div>

      {patchDialog && (
        <PatchDialog
          patch={patchDialog}
          busy={patchBusy}
          onClose={() => setPatchDialog(undefined)}
          onApply={() => void applyPatch()}
          onReject={() => void rejectPatch()}
          onUndo={() => void undoPatch()}
        />
      )}
      <SearchDialog
        open={searchOpen}
        loading={searchLoading}
        results={searchResults}
        onClose={() => setSearchOpen(false)}
        onSearch={(query, mode, sensitive) => void searchProject(query, mode, sensitive)}
        onOpen={(path, line) => void openFile(path, line)}
      />
      {settingsOpen && settings && (
        <SettingsDialog
          settings={settings}
          saving={settingsSaving}
          onClose={() => setSettingsOpen(false)}
          onSave={(value) => void saveSettings(value)}
        />
      )}
      {historyOpen && (
        <HistoryDialog
          patches={patches}
          onClose={() => setHistoryOpen(false)}
          onOpen={(patch) => { setPatchDialog(patch); setHistoryOpen(false); }}
        />
      )}
      <div className="toast-stack" aria-live="polite">
        {toasts.map((item) => <div key={item.id} className={`toast toast--${item.type}`}>{item.message}<button onClick={() => setToasts((current) => current.filter((toast) => toast.id !== item.id))}><X size={13} /></button></div>)}
      </div>
    </div>
  );
}

function HistoryDialog({
  patches,
  onClose,
  onOpen,
}: {
  patches: PatchProposal[];
  onClose: () => void;
  onOpen: (patch: PatchProposal) => void;
}) {
  return (
    <div className="modal-backdrop">
      <section className="modal history-dialog">
        <header className="modal-header">
          <div><span className="modal-icon"><History size={19} /></span><div><h2>Lịch sử chỉnh sửa</h2><p>Patch, backup và trạng thái hoàn tác</p></div></div>
          <button onClick={onClose}><X size={19} /></button>
        </header>
        <div className="history-list">
          {patches.length ? patches.map((patch) => (
            <button key={patch.id} onClick={() => onOpen(patch)}>
              <span className={`history-status history-status--${patch.status}`} />
              <div><strong>{patch.title}</strong><small>{patch.affectedPaths.join(", ")}</small></div>
              <span><Clock3 size={12} /> {new Date(patch.createdAt).toLocaleString("vi-VN")}</span>
              <b>{patch.status}</b>
            </button>
          )) : <div className="state-center"><GitCompareArrows size={30} /><p>Chưa có lịch sử chỉnh sửa.</p></div>}
        </div>
      </section>
    </div>
  );
}
