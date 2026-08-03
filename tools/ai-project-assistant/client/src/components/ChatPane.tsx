import {
  Bot,
  ChevronDown,
  CircleStop,
  Clock3,
  Code2,
  FileCode2,
  Globe2,
  GraduationCap,
  History,
  Layers3,
  MessageSquarePlus,
  Paperclip,
  Send,
  Sparkles,
  Trash2,
  X,
} from "lucide-react";
import { useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import type {
  AssistantMode,
  ChatMessage,
  ContextScope,
  Conversation,
  FileDocument,
  ModuleId,
} from "@smartkid/shared";
import type { EditorSelection } from "./EditorPane";
import { OralPane } from "./OralPane";

const suggestions = [
  "File này có chức năng gì?",
  "Đoạn code đang chọn làm gì?",
  "Phần giao diện này được xử lý ở đâu?",
  "ID này được khai báo ở file nào?",
  "Layout này được Activity hoặc Fragment nào sử dụng?",
  "String này nằm ở đâu?",
  "Style này được định nghĩa ở đâu?",
  "API này được gọi từ file nào?",
  "Code Admin gửi thông báo nằm ở đâu?",
  "Nếu muốn sửa chức năng này thì cần sửa những file nào?",
  "Tại sao đoạn code này đang báo lỗi?",
  "Hãy đề xuất cách sửa nhưng chưa áp dụng.",
  "Hãy tạo patch cho chức năng này.",
];

export function ChatPane({
  module,
  activeFile,
  selection,
  attachedPaths,
  messages,
  conversations,
  responding,
  onAttach,
  onRemoveAttachment,
  onOpenFile,
  onSend,
  onStop,
  onNewChat,
  onOpenConversation,
}: {
  module: ModuleId;
  activeFile?: FileDocument;
  selection?: EditorSelection;
  attachedPaths: string[];
  messages: ChatMessage[];
  conversations: Conversation[];
  responding: boolean;
  onAttach: (path: string) => void;
  onRemoveAttachment: (path: string) => void;
  onOpenFile: (path: string, line?: number) => void;
  onSend: (question: string, mode: AssistantMode, scope: ContextScope) => void;
  onStop: () => void;
  onNewChat: () => void;
  onOpenConversation: (id: string) => void;
}) {
  const [question, setQuestion] = useState("");
  const [mode, setMode] = useState<AssistantMode>("ask");
  const [scope, setScope] = useState<ContextScope>("files");
  const [showHistory, setShowHistory] = useState(false);
  const [surface, setSurface] = useState<"oral" | "code">("oral");

  const submit = () => {
    if (!question.trim() || responding) return;
    onSend(question, mode, scope);
    setQuestion("");
  };

  return (
    <aside
      className="chat-pane"
      onDragOver={(event) => {
        if (event.dataTransfer.types.includes("application/x-smartkid-file")) event.preventDefault();
      }}
      onDrop={(event) => {
        event.preventDefault();
        const path = event.dataTransfer.getData("application/x-smartkid-file");
        if (path) onAttach(path);
      }}
    >
      <div className="chat-header">
        <div>
          <span className="ai-avatar"><Sparkles size={16} /></span>
          <div>
            <h2>SMARTKID AI</h2>
            <p>
              <span className="online-dot" />
              {surface === "oral" ? " Thầy AI sẵn sàng" : ` Sẵn sàng · ${module.toUpperCase()}`}
            </p>
          </div>
        </div>
        {surface === "code" && (
          <div className="chat-header__actions">
            <button title="Lịch sử" onClick={() => setShowHistory((value) => !value)}>
              <History size={17} />
            </button>
            <button title="Cuộc trò chuyện mới" onClick={onNewChat}>
              <MessageSquarePlus size={17} />
            </button>
          </div>
        )}
        {surface === "code" && showHistory && (
          <div className="conversation-menu">
            <div className="conversation-menu__title">
              <span>Lịch sử trò chuyện</span>
              <button onClick={() => setShowHistory(false)}><X size={14} /></button>
            </div>
            {conversations.length ? conversations.map((conversation) => (
              <button
                key={conversation.id}
                onClick={() => {
                  onOpenConversation(conversation.id);
                  setShowHistory(false);
                }}
              >
                <Clock3 size={13} />
                <span>{conversation.title}</span>
                <small>{new Date(conversation.updatedAt).toLocaleDateString("vi-VN")}</small>
              </button>
            )) : <p>Chưa có cuộc trò chuyện.</p>}
          </div>
        )}
      </div>

      <div className="oral-mode-switch" aria-label="Chọn chế độ SMARTKID AI">
        <button
          type="button"
          className={surface === "oral" ? "is-active" : ""}
          onClick={() => setSurface("oral")}
        >
          <GraduationCap size={14} />
          AI VẤN ĐÁP
        </button>
        <button
          type="button"
          className={surface === "code" ? "is-active" : ""}
          onClick={() => setSurface("code")}
        >
          <Code2 size={13} />
          TRỢ LÝ CODE
        </button>
      </div>

      <div className="oral-surface" hidden={surface !== "oral"}>
        <OralPane active={surface === "oral"} />
      </div>

      <div className="chat-modebar oral-code-only" hidden={surface !== "code"}>
        <div className="segmented">
          {(["ask", "plan", "patch", "agent"] as AssistantMode[]).map((value) => (
            <button
              key={value}
              className={mode === value ? "is-active" : ""}
              onClick={() => setMode(value)}
              title={modeDescription(value)}
            >
              {value.charAt(0).toUpperCase() + value.slice(1)}
            </button>
          ))}
        </div>
        <label className="scope-select">
          {scope === "project" ? <Globe2 size={13} /> : <Layers3 size={13} />}
          <select value={scope} onChange={(event) => setScope(event.target.value as ContextScope)}>
            <option value="selection">Đoạn chọn</option>
            <option value="files">File context</option>
            <option value="module">Toàn module</option>
            <option value="project">Toàn dự án</option>
          </select>
          <ChevronDown size={12} />
        </label>
      </div>

      <div className="chat-context oral-code-only" hidden={surface !== "code"}>
        <span className="context-label">CONTEXT</span>
        <div className="context-chips">
          {activeFile && (
            <button className="context-chip context-chip--active" onClick={() => onOpenFile(activeFile.path)}>
              <FileCode2 size={12} />
              {activeFile.path.split("/").at(-1)}
            </button>
          )}
          {selection && (
            <span className="context-chip context-chip--selection">
              L{selection.startLine}–{selection.endLine}
            </span>
          )}
          {attachedPaths.map((path) => (
            <span className="context-chip" key={path} title={path}>
              <Paperclip size={11} />
              {path.split("/").at(-1)}
              <button onClick={() => onRemoveAttachment(path)}><X size={10} /></button>
            </span>
          ))}
          {!activeFile && attachedPaths.length === 0 && (
            <span className="context-empty">Kéo file vào đây để đính kèm</span>
          )}
        </div>
      </div>

      <div className="chat-messages oral-code-only" hidden={surface !== "code"}>
        {messages.length === 0 ? (
          <div className="chat-welcome">
            <span className="welcome-icon"><Bot size={25} /></span>
            <h3>Chào bạn, mình có thể giúp gì?</h3>
            <p>Mình chỉ dùng các file đã quét và luôn dẫn đúng đường dẫn, số dòng.</p>
            <div className="suggestion-list">
              {suggestions.map((suggestion) => (
                <button key={suggestion} onClick={() => setQuestion(suggestion)}>
                  {suggestion}
                </button>
              ))}
            </div>
          </div>
        ) : (
          messages.map((message) => (
            <article className={`chat-message chat-message--${message.role}`} key={message.id}>
              <div className="chat-message__role">
                {message.role === "assistant" ? <Sparkles size={13} /> : "BẠN"}
                <time>{new Date(message.createdAt).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })}</time>
              </div>
              <div className="markdown">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>{message.content}</ReactMarkdown>
              </div>
              {message.citations && message.citations.length > 0 && (
                <div className="citation-list">
                  {message.citations.slice(0, 12).map((citation) => (
                    <button
                      key={`${citation.path}:${citation.startLine}:${citation.endLine}`}
                      onClick={() => onOpenFile(citation.path, citation.startLine)}
                      title={citation.reason}
                    >
                      <FileCode2 size={12} />
                      <span>{citation.path}</span>
                      <small>L{citation.startLine}–{citation.endLine}</small>
                    </button>
                  ))}
                </div>
              )}
            </article>
          ))
        )}
        {responding && (
          <article className="chat-message chat-message--assistant">
            <div className="chat-message__role"><Sparkles size={13} /> AI ĐANG TRẢ LỜI</div>
            <div className="typing"><i /><i /><i /></div>
          </article>
        )}
      </div>

      <div className="chat-composer oral-code-only" hidden={surface !== "code"}>
        <textarea
          value={question}
          onChange={(event) => setQuestion(event.target.value)}
          placeholder={
            mode === "patch"
              ? "Mô tả bản vá cần tạo (chưa áp dụng)…"
              : mode === "agent"
                ? "Giao một nhiệm vụ nhiều bước…"
                : "Hỏi về code, file, resource hoặc lỗi…"
          }
          onKeyDown={(event) => {
            if ((event.ctrlKey || event.metaKey) && event.key === "Enter") {
              event.preventDefault();
              submit();
            }
          }}
        />
        <div className="composer-footer">
          <span>⌘ Enter để gửi</span>
          {attachedPaths.length > 0 && (
            <button
              className="clear-context"
              onClick={() => attachedPaths.forEach(onRemoveAttachment)}
              title="Xóa file context"
            >
              <Trash2 size={13} /> Xóa context
            </button>
          )}
          {responding ? (
            <button className="stop-button" onClick={onStop}>
              <CircleStop size={16} /> Dừng
            </button>
          ) : (
            <button className="send-button" onClick={submit} disabled={!question.trim()}>
              <Send size={16} />
            </button>
          )}
        </div>
      </div>
    </aside>
  );
}

function modeDescription(mode: AssistantMode) {
  return {
    ask: "Chỉ hỏi và giải thích",
    plan: "Lập kế hoạch, chưa sửa code",
    patch: "Tạo bản vá Pending",
    agent: "Phân tích nhiều bước, vẫn chờ xác nhận",
  }[mode];
}
