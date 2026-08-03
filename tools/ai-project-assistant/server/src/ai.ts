import path from "node:path";
import type {
  AssistantMode,
  ChatCitation,
  ChatMessage,
  ContextScope,
  Conversation,
  ModuleId,
  PatchOperation,
  PatchProposal,
} from "@smartkid/shared";
import type { RuntimeConfig } from "./config.js";
import { AppError } from "./errors.js";
import type { PatchService } from "./patches.js";
import type { ReferenceService } from "./references.js";
import type { IndexedFile, ProjectScanner } from "./scanner.js";
import type { SearchService } from "./search.js";
import { PathGuard, redactSecrets, sha256 } from "./security.js";
import type { SettingsService } from "./settings.js";
import { JsonStore } from "./store.js";

export type AiChatRequest = {
  question: string;
  mode: AssistantMode;
  scope: ContextScope;
  module?: ModuleId;
  conversationId?: string;
  activeFile?: string;
  attachedPaths?: string[];
  selection?: {
    path: string;
    content: string;
    startLine: number;
    endLine: number;
  };
};

type ContextChunk = ChatCitation & { content: string };
type ModelMessage = { role: "system" | "user" | "assistant"; content: string };

type AiPatchPayload = {
  summary?: string;
  plan?: string[];
  changes?: Array<{
    action: "update" | "create";
    path: string;
    content: string;
    reason?: string;
  }>;
};

export class AiService {
  private readonly conversations: JsonStore<{ conversations: Conversation[] }>;

  constructor(
    private readonly config: RuntimeConfig,
    private readonly guard: PathGuard,
    private readonly settings: SettingsService,
    private readonly scanner: ProjectScanner,
    private readonly search: SearchService,
    private readonly references: ReferenceService,
    private readonly patches: PatchService,
  ) {
    this.conversations = new JsonStore(path.join(config.dataRoot, "conversations.json"), {
      conversations: [],
    });
  }

  async chat(request: AiChatRequest, signal?: AbortSignal): Promise<{
    conversation: Conversation;
    message: ChatMessage;
    patch?: PatchProposal;
  }> {
    const question = request.question.trim();
    if (!question) throw new AppError("EMPTY_QUESTION", "Câu hỏi không được để trống", 422);
    const context = await this.buildContext({ ...request, question });
    if (context.length === 0 && looksLikeCodeLookup(question)) {
      const message = this.createMessage(
        "assistant",
        "Chưa tìm thấy file phù hợp trong phạm vi đã quét.\n\nBạn có thể chuyển phạm vi sang **Toàn bộ dự án** rồi tìm lại.",
        [],
      );
      const conversation = await this.appendConversation(request, question, message);
      return { conversation, message };
    }

    let patch: PatchProposal | undefined;
    let answer: string;
    if (request.mode === "patch" || request.mode === "agent") {
      const result = await this.generatePatch(question, context, request.mode, signal);
      patch = result.patch;
      answer = result.answer;
    } else {
      answer = await this.callModel(
        [
          { role: "system", content: this.systemPrompt(request.mode) },
          {
            role: "user",
            content: this.composeUserPrompt(question, context, request.scope, request.module),
          },
        ],
        signal,
      );
      answer = this.validatePaths(answer);
      answer = appendVerifiedSources(answer, context);
    }

    const citations = uniqueCitations(context.map(({ content: _content, ...citation }) => citation));
    const message = this.createMessage("assistant", answer, citations, patch?.id);
    const conversation = await this.appendConversation(request, question, message);
    return { conversation, message, patch };
  }

  async listConversations(): Promise<Conversation[]> {
    const database = await this.conversations.read();
    return database.conversations
      .map((conversation) => ({ ...conversation, messages: [] }))
      .sort((left, right) => right.updatedAt.localeCompare(left.updatedAt));
  }

  async getConversation(id: string): Promise<Conversation> {
    const database = await this.conversations.read();
    const conversation = database.conversations.find((item) => item.id === id);
    if (!conversation) {
      throw new AppError("CONVERSATION_NOT_FOUND", "Không tìm thấy cuộc trò chuyện", 404);
    }
    return structuredClone(conversation);
  }

  async createConversation(module?: ModuleId): Promise<Conversation> {
    const now = new Date().toISOString();
    const conversation: Conversation = {
      id: crypto.randomUUID(),
      title: "Cuộc trò chuyện mới",
      module,
      createdAt: now,
      updatedAt: now,
      messages: [],
    };
    await this.conversations.update((database) => ({
      conversations: [conversation, ...database.conversations].slice(0, 100),
    }));
    return conversation;
  }

  private async buildContext(request: AiChatRequest): Promise<ContextChunk[]> {
    const settings = this.settings.get();
    const chunks: ContextChunk[] = [];
    const usedPaths = new Set<string>();
    const maxCharacters = Math.min(settings.maxContextFiles * 18_000, 120_000);

    const addFile = (
      file: IndexedFile | undefined,
      reason: string,
      preferredStart?: number,
      preferredEnd?: number,
    ) => {
      if (!file || usedPaths.has(file.path) || chunks.length >= settings.maxContextFiles) return;
      const lineWindow = selectLineWindow(file, preferredStart, preferredEnd, 260);
      const redacted = redactSecrets(lineWindow.content);
      chunks.push({
        path: file.path,
        startLine: lineWindow.startLine,
        endLine: lineWindow.endLine,
        reason,
        content: redacted.content,
      });
      usedPaths.add(file.path);
    };

    if (request.selection) {
      const selectedPath = this.guard.normalize(request.selection.path);
      const file = this.scanner.getFile(selectedPath);
      if (file) {
        const startLine = clamp(request.selection.startLine, 1, file.lines.length);
        const endLine = clamp(request.selection.endLine, startLine, file.lines.length);
        const actualContent = file.lines.slice(startLine - 1, endLine).join("\n");
        if (actualContent.trim()) {
          chunks.push({
            path: file.path,
            startLine,
            endLine,
            reason: "Đoạn code người dùng đang chọn",
            content: redactSecrets(actualContent).content,
          });
          usedPaths.add(file.path);
        }
      }
    }

    const active = request.activeFile
      ? this.scanner.getFile(this.guard.normalize(request.activeFile))
      : undefined;
    addFile(active, "File đang mở", request.selection?.startLine, request.selection?.endLine);

    for (const attachedPath of request.attachedPaths ?? []) {
      addFile(
        this.scanner.getFile(this.guard.normalize(attachedPath)),
        "File được người dùng đính kèm",
      );
    }

    if (active) {
      for (const reference of this.references.directReferencesForFile(active.path, active.content, 30)) {
        addFile(
          this.scanner.getFile(reference.path),
          `Reference trực tiếp từ ${active.path}`,
          reference.line,
          reference.endLine,
        );
      }
    }

    const searchModule =
      request.scope === "project" || request.scope === "selection" || request.scope === "files"
        ? undefined
        : request.module;
    for (const result of this.search.relatedByTerms(request.question, searchModule, 50)) {
      addFile(
        this.scanner.getFile(result.path),
        `Kết quả tìm kiếm liên quan tới “${result.match}”`,
        result.line,
        result.endLine,
      );
    }

    if (request.scope === "module" && request.module) {
      for (const file of this.scanner.getFiles().filter((candidate) => candidate.module === request.module)) {
        addFile(file, `File thuộc module ${request.module.toUpperCase()}`);
      }
    }

    let currentSize = 0;
    return chunks.filter((chunk) => {
      if (currentSize + chunk.content.length > maxCharacters) return false;
      currentSize += chunk.content.length;
      return true;
    });
  }

  private async generatePatch(
    question: string,
    context: ContextChunk[],
    mode: "patch" | "agent",
    signal?: AbortSignal,
  ): Promise<{ patch: PatchProposal; answer: string }> {
    if (context.length === 0) {
      throw new AppError(
        "NO_PATCH_CONTEXT",
        "Chưa tìm thấy file phù hợp trong phạm vi đã quét để tạo bản vá",
        422,
      );
    }
    const system = `${this.systemPrompt(mode)}

Trả về DUY NHẤT một JSON object hợp lệ, không có markdown:
{
  "summary": "mô tả ngắn",
  "plan": ["bước 1", "bước 2"],
  "changes": [
    {
      "action": "update",
      "path": "đường/dẫn/thật/từ/project/root.ext",
      "content": "TOÀN BỘ nội dung file sau thay đổi",
      "reason": "lý do"
    }
  ]
}
Chỉ dùng action "update" cho file đã có trong context hoặc "create" cho file mới thực sự cần thiết.
Không xóa, đổi tên hay di chuyển file. Không dùng đường dẫn tuyệt đối.`;
    const raw = await this.callModel(
      [
        { role: "system", content: system },
        {
          role: "user",
          content: this.composeUserPrompt(question, context, "files"),
        },
      ],
      signal,
    );
    const payload = parseModelJson(raw);
    if (!payload.changes?.length) {
      throw new AppError("AI_PATCH_INVALID", "AI không trả về thay đổi hợp lệ", 502);
    }
    const operations: PatchOperation[] = [];
    for (const change of payload.changes) {
      if (!change.path || typeof change.content !== "string") {
        throw new AppError("AI_PATCH_INVALID", "AI trả về file thay đổi không hợp lệ", 502);
      }
      const relativePath = this.guard.normalize(change.path);
      if (change.action === "update") {
        const indexed = this.scanner.getFile(relativePath);
        if (!indexed) {
          throw new AppError(
            "AI_PATH_NOT_FOUND",
            "AI đề xuất sửa một đường dẫn không tồn tại; bản vá đã bị chặn",
            502,
            { path: relativePath },
          );
        }
        operations.push({
          type: "update",
          path: relativePath,
          content: change.content,
          expectedVersion: sha256(indexed.content),
        });
      } else if (change.action === "create") {
        operations.push({ type: "create", path: relativePath, content: change.content });
      } else {
        throw new AppError("AI_PATCH_INVALID", "AI đề xuất thao tác không được phép", 502);
      }
    }
    const patch = await this.patches.createProposal({
      title: mode === "agent" ? "Đề xuất từ Agent" : "Bản vá do AI đề xuất",
      summary: payload.summary || question,
      source: mode === "agent" ? "agent" : "ai",
      operations,
      plan: payload.plan,
    });
    const planText = payload.plan?.length
      ? `\n\nKế hoạch:\n${payload.plan.map((step, index) => `${index + 1}. ${step}`).join("\n")}`
      : "";
    return {
      patch,
      answer: `AI đã tạo bản vá **Pending** cho ${patch.files.length} file. Chưa có file nào được ghi.${planText}\n\nHãy xem diff và chỉ bấm **Xác nhận áp dụng** khi nội dung đúng.`,
    };
  }

  private async callModel(
    messages: ModelMessage[],
    signal?: AbortSignal,
  ): Promise<string> {
    const settings = this.settings.get();
    if (!this.config.aiApiKey) {
      throw new AppError(
        "AI_NOT_CONFIGURED",
        "Chưa cấu hình AI_API_KEY trong file .env của công cụ",
        503,
      );
    }
    if (!settings.aiModel) {
      throw new AppError("AI_MODEL_NOT_CONFIGURED", "Chưa cấu hình AI_MODEL", 503);
    }
    const base = settings.aiBaseUrl.replace(/\/+$/, "");
    const endpoint = base.endsWith("/chat/completions") ? base : `${base}/chat/completions`;
    let response: Response;
    try {
      response = await fetch(endpoint, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${this.config.aiApiKey}`,
        },
        body: JSON.stringify({
          model: settings.aiModel,
          temperature: settings.temperature,
          messages,
        }),
        signal,
      });
    } catch (error) {
      if ((error as Error).name === "AbortError") {
        throw new AppError("AI_REQUEST_ABORTED", "Đã dừng câu trả lời AI", 499);
      }
      throw new AppError("AI_CONNECTION_FAILED", "Không thể kết nối AI API", 502, {
        reason: error instanceof Error ? error.message : String(error),
      });
    }
    const responseText = await response.text();
    let body: any;
    try {
      body = JSON.parse(responseText);
    } catch {
      throw new AppError("AI_INVALID_RESPONSE", "AI API trả về dữ liệu không hợp lệ", 502, {
        status: response.status,
      });
    }
    if (!response.ok) {
      throw new AppError("AI_API_ERROR", "AI API trả về lỗi", 502, {
        status: response.status,
        message: body?.error?.message ?? "Không rõ nguyên nhân",
      });
    }
    const content = body?.choices?.[0]?.message?.content;
    if (typeof content !== "string" || !content.trim()) {
      throw new AppError("AI_EMPTY_RESPONSE", "AI không trả về nội dung", 502);
    }
    return content.trim();
  }

  private systemPrompt(mode: AssistantMode): string {
    const settings = this.settings.get();
    return `Bạn là SMARTKID AI PROJECT ASSISTANT, chuyên gia Android và full-stack.
Luôn trả lời bằng ${settings.responseLanguage}.
Chỉ khẳng định về code dựa trên các đoạn context được cung cấp.
Không bịa tên file, đường dẫn, class, resource hoặc số dòng.
Mọi đường dẫn phải tính từ project root và phải trùng chính xác nhãn FILE trong context.
Nếu chưa đủ bằng chứng, nói nguyên văn: “Chưa tìm thấy file phù hợp trong phạm vi đã quét.” và đề nghị tìm toàn dự án.

Định dạng câu trả lời khi phân tích code:
1. Giải thích ngắn gọn
2. File liên quan
3. Đường dẫn từ project root
4. Khoảng dòng
5. Luồng hoạt động
6. File gọi tới/được gọi bởi
7. Rủi ro khi sửa
8. Đề xuất chỉnh sửa

Chế độ hiện tại: ${mode.toUpperCase()}.
${mode === "ask" ? "Chỉ giải thích, không tạo thay đổi." : ""}
${mode === "plan" ? "Chỉ lập kế hoạch, tuyệt đối không tuyên bố đã sửa file." : ""}`;
  }

  private composeUserPrompt(
    question: string,
    context: ContextChunk[],
    scope: ContextScope,
    module?: ModuleId,
  ): string {
    const contextText = context
      .map(
        (chunk, index) => `--- CONTEXT ${index + 1} ---
FILE: ${chunk.path}
LINES: ${chunk.startLine}-${chunk.endLine}
REASON: ${chunk.reason}
\`\`\`
${chunk.content}
\`\`\``,
      )
      .join("\n\n");
    return `PHẠM VI: ${scope}${module ? ` / MODULE: ${module.toUpperCase()}` : ""}

CÂU HỎI:
${question}

CONTEXT ĐÃ XÁC MINH:
${contextText || "[Không có file phù hợp]"}`;
  }

  private validatePaths(answer: string): string {
    const pathPattern =
      /(?:[A-Za-z0-9_.-]+\/)+[A-Za-z0-9_.-]+\.(?:kt|kts|java|xml|gradle|properties|toml|json|js|jsx|ts|tsx|md|yml|yaml|sql)/g;
    return answer.replace(pathPattern, (candidate: string) => {
      const normalized = candidate.replaceAll("\\", "/");
      return this.scanner.getFile(normalized)
        ? normalized
        : "[đường dẫn chưa được xác minh]";
    });
  }

  private createMessage(
    role: ChatMessage["role"],
    content: string,
    citations?: ChatCitation[],
    patchId?: string,
  ): ChatMessage {
    return {
      id: crypto.randomUUID(),
      role,
      content,
      createdAt: new Date().toISOString(),
      citations,
      patchId,
    };
  }

  private async appendConversation(
    request: AiChatRequest,
    question: string,
    assistantMessage: ChatMessage,
  ): Promise<Conversation> {
    const userMessage = this.createMessage("user", question);
    const now = new Date().toISOString();
    let result: Conversation | undefined;
    await this.conversations.update((database) => {
      const existing = request.conversationId
        ? database.conversations.find((item) => item.id === request.conversationId)
        : undefined;
      if (existing) {
        result = {
          ...existing,
          module: request.module ?? existing.module,
          updatedAt: now,
          messages: [...existing.messages, userMessage, assistantMessage],
        };
        return {
          conversations: database.conversations.map((item) =>
            item.id === existing.id ? result! : item,
          ),
        };
      }
      result = {
        id: crypto.randomUUID(),
        title: question.length > 64 ? `${question.slice(0, 61)}…` : question,
        module: request.module,
        createdAt: now,
        updatedAt: now,
        messages: [userMessage, assistantMessage],
      };
      return { conversations: [result, ...database.conversations].slice(0, 100) };
    });
    return structuredClone(result!);
  }
}

function selectLineWindow(
  file: IndexedFile,
  preferredStart?: number,
  preferredEnd?: number,
  maxLines = 260,
) {
  const focusStart = clamp(preferredStart ?? 1, 1, Math.max(file.lines.length, 1));
  const focusEnd = clamp(preferredEnd ?? focusStart, focusStart, Math.max(file.lines.length, 1));
  let startLine = Math.max(1, focusStart - Math.floor(maxLines / 3));
  let endLine = Math.min(file.lines.length, Math.max(focusEnd + Math.floor(maxLines / 3), startLine + maxLines - 1));
  if (endLine - startLine + 1 > maxLines) endLine = startLine + maxLines - 1;
  if (endLine - startLine + 1 < maxLines) startLine = Math.max(1, endLine - maxLines + 1);
  return {
    startLine,
    endLine,
    content: file.lines.slice(startLine - 1, endLine).join("\n"),
  };
}

function appendVerifiedSources(answer: string, chunks: ContextChunk[]): string {
  const citations = uniqueCitations(chunks.map(({ content: _content, ...citation }) => citation)).slice(0, 12);
  if (citations.length === 0) return answer;
  const sources = citations
    .map(
      (citation) =>
        `- \`${citation.path}\` — dòng ${citation.startLine}–${citation.endLine} (${citation.reason})`,
    )
    .join("\n");
  return `${answer}\n\n### Nguồn đã xác minh\n${sources}`;
}

function uniqueCitations(citations: ChatCitation[]): ChatCitation[] {
  const seen = new Set<string>();
  return citations.filter((citation) => {
    const key = `${citation.path}:${citation.startLine}:${citation.endLine}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function looksLikeCodeLookup(question: string) {
  return /file|code|class|activity|fragment|layout|xml|id|resource|string|style|api|repository|viewmodel|dòng|ở đâu|where/i.test(
    question,
  );
}

function parseModelJson(value: string): AiPatchPayload {
  const cleaned = value.replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/, "");
  try {
    return JSON.parse(cleaned) as AiPatchPayload;
  } catch (error) {
    throw new AppError("AI_PATCH_INVALID", "AI trả về JSON bản vá không hợp lệ", 502, {
      reason: error instanceof Error ? error.message : String(error),
    });
  }
}

function clamp(value: number, minimum: number, maximum: number) {
  return Math.max(minimum, Math.min(maximum, value));
}
