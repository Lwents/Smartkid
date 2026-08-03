export const MODULE_IDS = ["student", "teacher", "admin", "backend"] as const;
export type ModuleId = (typeof MODULE_IDS)[number];

export type ApiSuccess<T> = { success: true; data: T };
export type ApiFailure = {
  success: false;
  error: { code: string; message: string; details: Record<string, unknown> };
};
export type ApiResponse<T> = ApiSuccess<T> | ApiFailure;

export type TreeNode = {
  name: string;
  path: string;
  type: "file" | "directory";
  extension?: string;
  size?: number;
  readable?: boolean;
  tooLarge?: boolean;
  module?: ModuleId;
  changed?: boolean;
  error?: boolean;
  children?: TreeNode[];
};

export type ModuleSummary = {
  id: ModuleId;
  name: string;
  description: string;
  icon: string;
  fileCount: number;
  samplePaths: string[];
};

export type ModuleMapping = Record<ModuleId, { keywords: string[]; excludes: string[] }>;

export type SearchMode =
  | "text"
  | "file"
  | "path"
  | "exact"
  | "regex"
  | "class"
  | "function"
  | "android-id";

export type SearchResult = {
  path: string;
  line: number;
  column: number;
  endLine: number;
  preview: string;
  match: string;
  module?: ModuleId;
  kind?: string;
};

export type FileDocument = {
  path: string;
  content: string;
  language: string;
  size: number;
  lineCount: number;
  version: string;
  readOnly: boolean;
  redacted: boolean;
  module?: ModuleId;
  xml?: XmlAnalysis;
};

export type ReferenceOccurrence = SearchResult & {
  relation: "definition" | "usage" | "declaration" | "call" | "dependency";
};

export type XmlView = {
  tag: string;
  id?: string;
  line: number;
  attributes: Record<string, string>;
};

export type XmlAnalysis = {
  views: XmlView[];
  resources: Array<{
    type: string;
    name: string;
    line: number;
    exists: boolean;
    occurrences: number;
  }>;
  inflaters: ReferenceOccurrence[];
  warnings: string[];
};

export type PatchOperation =
  | { type: "update"; path: string; content: string; expectedVersion?: string }
  | { type: "create"; path: string; content: string }
  | { type: "delete"; path: string }
  | { type: "rename" | "move"; path: string; destination: string }
  | { type: "mkdir"; path: string }
  | { type: "rename-directory"; path: string; destination: string };

export type PatchFile = {
  path: string;
  destination?: string;
  action: PatchOperation["type"];
  diff: string;
};

export type PatchProposal = {
  id: string;
  title: string;
  summary: string;
  source: "manual" | "ai" | "agent";
  status: "pending" | "applied" | "undone" | "rejected";
  createdAt: string;
  appliedAt?: string;
  undoneAt?: string;
  files: PatchFile[];
  affectedPaths: string[];
  plan?: string[];
};

export type AssistantMode = "ask" | "plan" | "patch" | "agent";
export type ContextScope = "selection" | "files" | "module" | "project";

export type ChatCitation = {
  path: string;
  startLine: number;
  endLine: number;
  reason: string;
};

export type ChatMessage = {
  id: string;
  role: "user" | "assistant";
  content: string;
  createdAt: string;
  citations?: ChatCitation[];
  patchId?: string;
};

export type Conversation = {
  id: string;
  title: string;
  module?: ModuleId;
  createdAt: string;
  updatedAt: string;
  messages: ChatMessage[];
};

export type AssistantSettings = {
  aiModel: string;
  aiBaseUrl: string;
  temperature: number;
  maxContextFiles: number;
  maxFileSize: number;
  defaultModule: ModuleId;
  responseLanguage: string;
  readOnly: boolean;
  moduleMapping: ModuleMapping;
};

export type ProjectInfo = {
  name: string;
  root: string;
  android: {
    detected: boolean;
    languages: Array<"Kotlin" | "Java">;
    specialResourceDirectories: string[];
    gradleFiles: string[];
  };
  indexedFiles: number;
  ignoredFiles: number;
  oversizedFiles: number;
  lastScanAt?: string;
  scanning: boolean;
  modules: ModuleSummary[];
};

export const ORAL_AUDIENCE_LEVELS = [
  "Tiểu học",
  "THCS",
  "THPT",
  "Đại học",
  "Tự do",
] as const;

export const ORAL_SESSION_LENGTHS = [5, 8, 10, 15] as const;

export type OralAudienceLevel = (typeof ORAL_AUDIENCE_LEVELS)[number];
export type OralSessionLength = (typeof ORAL_SESSION_LENGTHS)[number];
export type OralSessionStatus = "active" | "completed";
export type OralPedagogicalPhase =
  | "warmup"
  | "diagnose"
  | "probe"
  | "challenge"
  | "reflect"
  | "complete";

export type OralSessionConfig = {
  studentName: string;
  level: OralAudienceLevel;
  subject: string;
  topic: string;
  learningGoal: string;
  questionCount: OralSessionLength;
};

export type OralTurnAssessment = {
  correctness: number;
  reasoning: number;
  clarity: number;
  confidence: "low" | "medium" | "high";
  misconception?: string;
};

export type OralTurn = {
  id: string;
  phase: OralPedagogicalPhase;
  teacherMessage: string;
  question: string;
  studentAnswer?: string;
  feedback?: string;
  assessment?: OralTurnAssessment;
  createdAt: string;
  answeredAt?: string;
};

export type OralSessionSummary = {
  overallScore: number;
  correctness: number;
  reasoning: number;
  clarity: number;
  strengths: string[];
  improvements: string[];
  teacherClosing: string;
};

export type OralSession = {
  id: string;
  config: OralSessionConfig;
  status: OralSessionStatus;
  currentPhase: OralPedagogicalPhase;
  turns: OralTurn[];
  summary?: OralSessionSummary;
  createdAt: string;
  updatedAt: string;
};

export type CreateOralSessionRequest = OralSessionConfig;

export type SubmitOralAnswerRequest = {
  answer: string;
};

export type OralPublicConfig = {
  aiConfigured: boolean;
  model: string;
  demoMode: boolean;
  defaultLanguage: "vi-VN";
};
