import fs from "node:fs/promises";
import path from "node:path";
import compression from "compression";
import cors from "cors";
import express, { type Express, type RequestHandler } from "express";
import helmet from "helmet";
import { z } from "zod";
import type {
  AssistantSettings,
  ModuleMapping,
  PatchOperation,
} from "@smartkid/shared";
import {
  ORAL_AUDIENCE_LEVELS,
  ORAL_SESSION_LENGTHS,
  type CreateOralSessionRequest,
  type OralPublicConfig,
} from "@smartkid/shared";
import { AiService } from "./ai.js";
import { getRuntimeConfig, type RuntimeConfig } from "./config.js";
import { errorHandler, notFoundHandler } from "./errors.js";
import { FileService } from "./files.js";
import {
  JsonOralSessionStore,
  OpenAiOrDemoOralEngine,
  OralSessionService,
  type OralAiEngine,
} from "./oral.js";
import { PatchService } from "./patches.js";
import { ReferenceService } from "./references.js";
import { ProjectScanner } from "./scanner.js";
import { SearchService } from "./search.js";
import { PathGuard } from "./security.js";
import { SettingsService } from "./settings.js";

const MODULE_IDS = ["student", "teacher", "admin", "backend"] as const;
const moduleIdSchema = z.enum(MODULE_IDS);
const relativePathSchema = z.string().min(1).max(1_000);
const patchIdSchema = z.object({ id: z.string().uuid() });

export type ApplicationServices = {
  config: RuntimeConfig;
  guard: PathGuard;
  settings: SettingsService;
  scanner: ProjectScanner;
  search: SearchService;
  references: ReferenceService;
  files: FileService;
  patches: PatchService;
  ai: AiService;
  oral: OralSessionService;
};

export async function createApplication(options: {
  config?: Partial<RuntimeConfig>;
  watch?: boolean;
  oralAi?: OralAiEngine;
} = {}): Promise<{ app: Express; services: ApplicationServices; dispose: () => Promise<void> }> {
  const config = getRuntimeConfig(options.config);
  await fs.mkdir(config.dataRoot, { recursive: true, mode: 0o700 });
  const guard = await PathGuard.create(config.projectRoot, config.projectIncludes);
  const settings = new SettingsService(config);
  await settings.initialize();
  const scanner = new ProjectScanner(config, guard, settings);
  await scanner.initialize({ watch: options.watch ?? !config.isTest });
  const search = new SearchService(scanner);
  const references = new ReferenceService(scanner);
  const files = new FileService(config, guard, scanner, references, settings);
  const patches = new PatchService(config, guard, settings);
  const ai = new AiService(config, guard, settings, scanner, search, references, patches);
  const oralStore = new JsonOralSessionStore(
    path.join(config.dataRoot, "oral-sessions.json"),
  );
  await oralStore.initialize();
  const oral = new OralSessionService(
    oralStore,
    options.oralAi ??
      new OpenAiOrDemoOralEngine(() => {
        const current = settings.get();
        return {
          aiApiKey: config.aiApiKey,
          aiBaseUrl: current.aiBaseUrl,
          aiModel: current.aiModel,
          temperature: current.temperature,
        };
      }),
  );
  const services: ApplicationServices = {
    config,
    guard,
    settings,
    scanner,
    search,
    references,
    files,
    patches,
    ai,
    oral,
  };
  const app = buildExpressApp(services);
  return {
    app,
    services,
    dispose: () => scanner.close(),
  };
}

function buildExpressApp(services: ApplicationServices): Express {
  const app = express();
  app.disable("x-powered-by");
  app.use(
    helmet({
      crossOriginEmbedderPolicy: false,
      contentSecurityPolicy: false,
    }),
  );
  app.use(cors({ origin: localOriginOnly }));
  app.use(compression());
  app.use(express.json({ limit: "12mb" }));

  const api = express.Router();
  api.get("/health", (_request, response) => {
    response.json({ success: true, data: { status: "ok", scanning: services.scanner.scanning } });
  });
  api.get("/project/info", (_request, response) => {
    response.json({ success: true, data: services.scanner.getInfo() });
  });
  api.get("/project/tree", (_request, response) => {
    response.json({ success: true, data: services.scanner.getTree() });
  });
  api.post(
    "/project/scan",
    asyncRoute(async (_request, response) => {
      await services.scanner.scan();
      response.json({ success: true, data: services.scanner.getInfo() });
    }),
  );

  api.get("/modules", (_request, response) => {
    response.json({
      success: true,
      data: {
        modules: services.scanner.getModules(),
        mapping: services.settings.get().moduleMapping,
      },
    });
  });
  api.put(
    "/modules/mapping",
    asyncRoute(async (request, response) => {
      const mapping = moduleMappingSchema.parse(request.body) as ModuleMapping;
      await services.settings.update({ moduleMapping: mapping });
      await services.scanner.scan();
      response.json({
        success: true,
        data: { modules: services.scanner.getModules(), mapping },
      });
    }),
  );

  api.get("/settings", (_request, response) => {
    response.json({ success: true, data: services.settings.get() });
  });
  api.put(
    "/settings",
    asyncRoute(async (request, response) => {
      const update = settingsUpdateSchema.parse(request.body) as Partial<AssistantSettings>;
      const previousMaxSize = services.settings.get().maxFileSize;
      const result = await services.settings.update(update);
      if (update.maxFileSize && update.maxFileSize !== previousMaxSize) {
        await services.scanner.scan();
      }
      response.json({ success: true, data: result });
    }),
  );

  api.get(
    "/files/read",
    asyncRoute(async (request, response) => {
      const { path: relativePath } = z.object({ path: relativePathSchema }).parse(request.query);
      response.json({ success: true, data: await services.files.read(relativePath) });
    }),
  );
  api.get(
    "/files/metadata",
    asyncRoute(async (_request, response) => {
      response.json({ success: true, data: await services.files.metadataInfo() });
    }),
  );
  api.put(
    "/files/favorite",
    asyncRoute(async (request, response) => {
      const body = z
        .object({ path: relativePathSchema, favorite: z.boolean() })
        .parse(request.body);
      response.json({
        success: true,
        data: { favorites: await services.files.setFavorite(body.path, body.favorite) },
      });
    }),
  );
  api.post(
    "/files/search",
    asyncRoute(async (request, response) => {
      const body = searchSchema.parse(request.body);
      response.json({ success: true, data: services.search.search(body) });
    }),
  );
  api.post(
    "/files/references",
    asyncRoute(async (request, response) => {
      const body = z
        .object({
          identifier: z.string().min(1).max(300),
          kind: z.string().max(50).default("auto"),
          limit: z.number().int().min(1).max(500).default(300),
        })
        .parse(request.body);
      response.json({
        success: true,
        data: services.references.find(body.identifier, body.kind, body.limit),
      });
    }),
  );

  api.post("/files/create", proposalRoute(services, "create"));
  api.post("/files/update", proposalRoute(services, "update"));
  api.post("/files/delete", proposalRoute(services, "delete"));
  api.post("/files/rename", proposalRoute(services, "rename"));
  api.post("/files/move", proposalRoute(services, "move"));
  api.post("/folders/create", proposalRoute(services, "mkdir"));
  api.post("/folders/rename", proposalRoute(services, "rename-directory"));

  api.post(
    "/patch/preview",
    asyncRoute(async (request, response) => {
      const { id } = patchIdSchema.parse(request.body);
      response.json({ success: true, data: await services.patches.get(id) });
    }),
  );
  api.get(
    "/patches",
    asyncRoute(async (_request, response) => {
      response.json({ success: true, data: await services.patches.list() });
    }),
  );
  api.post(
    "/patch/apply",
    asyncRoute(async (request, response) => {
      const { id } = patchIdSchema.parse(request.body);
      const patch = await services.patches.apply(id);
      await services.scanner.scan();
      response.json({ success: true, data: patch });
    }),
  );
  api.post(
    "/patch/undo",
    asyncRoute(async (request, response) => {
      const { id } = patchIdSchema.parse(request.body);
      const patch = await services.patches.undo(id);
      await services.scanner.scan();
      response.json({ success: true, data: patch });
    }),
  );
  api.post(
    "/patch/reject",
    asyncRoute(async (request, response) => {
      const { id } = patchIdSchema.parse(request.body);
      response.json({ success: true, data: await services.patches.reject(id) });
    }),
  );

  api.post(
    "/ai/chat",
    asyncRoute(async (request, response) => {
      const body = aiChatSchema.parse(request.body);
      const controller = new AbortController();
      request.once("aborted", () => controller.abort());
      const result = await services.ai.chat(body, controller.signal);
      response.json({ success: true, data: result });
    }),
  );
  api.post(
    "/ai/explain",
    asyncRoute(async (request, response) => {
      const body = aiChatSchema.parse({ ...request.body, mode: "ask" });
      const result = await services.ai.chat(body);
      response.json({ success: true, data: result });
    }),
  );
  api.post(
    "/ai/create-patch",
    asyncRoute(async (request, response) => {
      const body = aiChatSchema.parse({ ...request.body, mode: request.body?.mode ?? "patch" });
      const result = await services.ai.chat(body);
      response.json({ success: true, data: result });
    }),
  );
  api.get(
    "/ai/conversations",
    asyncRoute(async (_request, response) => {
      response.json({ success: true, data: await services.ai.listConversations() });
    }),
  );
  api.post(
    "/ai/conversations",
    asyncRoute(async (request, response) => {
      const body = z.object({ module: moduleIdSchema.optional() }).parse(request.body);
      response.status(201).json({
        success: true,
        data: await services.ai.createConversation(body.module),
      });
    }),
  );
  api.get(
    "/ai/conversations/:id",
    asyncRoute(async (request, response) => {
      const id = z.string().uuid().parse(request.params.id);
      response.json({ success: true, data: await services.ai.getConversation(id) });
    }),
  );

  api.get("/oral/config", (_request, response) => {
    const current = services.settings.get();
    const aiConfigured = Boolean(
      services.config.aiApiKey && current.aiModel,
    );
    const config: OralPublicConfig = {
      aiConfigured,
      model: current.aiModel,
      demoMode: !aiConfigured,
      defaultLanguage: "vi-VN",
    };
    response.setHeader("Cache-Control", "no-store");
    response.json({ success: true, data: config });
  });
  api.get(
    "/oral/sessions",
    asyncRoute(async (request, response) => {
      const query = oralListSessionsSchema.parse(request.query);
      response.setHeader("Cache-Control", "no-store");
      response.json({
        success: true,
        data: await services.oral.list(query.status),
      });
    }),
  );
  api.post(
    "/oral/sessions",
    asyncRoute(async (request, response) => {
      const input = oralCreateSessionSchema.parse(
        request.body,
      ) as CreateOralSessionRequest;
      response.setHeader("Cache-Control", "no-store");
      response
        .status(201)
        .json({ success: true, data: await services.oral.create(input) });
    }),
  );
  api.get(
    "/oral/sessions/:id",
    asyncRoute(async (request, response) => {
      const id = oralSessionIdSchema.parse(request.params.id);
      response.setHeader("Cache-Control", "no-store");
      response.json({ success: true, data: await services.oral.get(id) });
    }),
  );
  api.post(
    "/oral/sessions/:id/answers",
    asyncRoute(async (request, response) => {
      const id = oralSessionIdSchema.parse(request.params.id);
      const input = oralSubmitAnswerSchema.parse(request.body);
      response.setHeader("Cache-Control", "no-store");
      response.json({
        success: true,
        data: await services.oral.answer(id, input.answer),
      });
    }),
  );
  api.post(
    "/oral/sessions/:id/end",
    asyncRoute(async (request, response) => {
      const id = oralSessionIdSchema.parse(request.params.id);
      response.setHeader("Cache-Control", "no-store");
      response.json({ success: true, data: await services.oral.end(id) });
    }),
  );
  api.delete(
    "/oral/sessions/:id",
    asyncRoute(async (request, response) => {
      const id = oralSessionIdSchema.parse(request.params.id);
      await services.oral.delete(id);
      response.setHeader("Cache-Control", "no-store");
      response.json({ success: true, data: { id } });
    }),
  );

  api.get(
    "/history",
    asyncRoute(async (_request, response) => {
      const [conversations, patches] = await Promise.all([
        services.ai.listConversations(),
        services.patches.list(),
      ]);
      response.json({ success: true, data: { conversations, patches } });
    }),
  );

  app.use("/api", api);
  const clientDist = path.join(services.config.assistantRoot, "client", "dist");
  app.use(express.static(clientDist));
  app.use((request, response, next) => {
    if (request.path.startsWith("/api/")) return next();
    if (!request.accepts("html")) return next();
    response.sendFile(path.join(clientDist, "index.html"), (error) => {
      if (error) next();
    });
  });
  app.use(notFoundHandler);
  app.use(errorHandler);
  return app;
}

function proposalRoute(
  services: ApplicationServices,
  type: PatchOperation["type"],
): RequestHandler {
  return asyncRoute(async (request, response) => {
    const common = z
      .object({
        path: relativePathSchema,
        title: z.string().max(200).optional(),
        summary: z.string().max(2_000).optional(),
      })
      .parse(request.body);
    let operation: PatchOperation;
    if (type === "update") {
      const values = z
        .object({ content: z.string(), expectedVersion: z.string().length(64).optional() })
        .parse(request.body);
      operation = { type, path: common.path, ...values };
    } else if (type === "create") {
      const values = z.object({ content: z.string() }).parse(request.body);
      operation = { type, path: common.path, content: values.content };
    } else if (type === "delete" || type === "mkdir") {
      operation = { type, path: common.path };
    } else {
      const values = z.object({ destination: relativePathSchema }).parse(request.body);
      operation = { type, path: common.path, destination: values.destination };
    }
    const patch = await services.patches.createProposal({
      title: common.title || defaultPatchTitle(type, common.path),
      summary: common.summary || "Thao tác do người dùng tạo; đang chờ xác nhận.",
      source: "manual",
      operations: [operation],
    });
    response.status(201).json({ success: true, data: patch });
  });
}

const searchSchema = z.object({
  query: z.string().min(1).max(500),
  mode: z
    .enum(["text", "file", "path", "exact", "regex", "class", "function", "android-id"])
    .default("text"),
  caseSensitive: z.boolean().optional(),
  module: moduleIdSchema.optional(),
  paths: z.array(relativePathSchema).max(100).optional(),
  limit: z.number().int().min(1).max(500).optional(),
});

const moduleRuleSchema = z.object({
  keywords: z.array(z.string().min(1).max(100)).max(100),
  excludes: z.array(z.string().min(1).max(100)).max(100),
});
const moduleMappingSchema = z.object({
  student: moduleRuleSchema,
  teacher: moduleRuleSchema,
  admin: moduleRuleSchema,
  backend: moduleRuleSchema,
});

const settingsUpdateSchema = z
  .object({
    aiModel: z.string().max(200),
    aiBaseUrl: z.string().url(),
    temperature: z.number().min(0).max(2),
    maxContextFiles: z.number().int().min(1).max(50),
    maxFileSize: z.number().int().min(10_000).max(10_000_000),
    defaultModule: moduleIdSchema,
    responseLanguage: z.string().min(2).max(100),
    readOnly: z.boolean(),
    moduleMapping: moduleMappingSchema,
  })
  .partial()
  .strict();

const aiChatSchema = z.object({
  question: z.string().min(1).max(20_000),
  mode: z.enum(["ask", "plan", "patch", "agent"]).default("ask"),
  scope: z.enum(["selection", "files", "module", "project"]).default("files"),
  module: moduleIdSchema.optional(),
  conversationId: z.string().uuid().optional(),
  activeFile: relativePathSchema.optional(),
  attachedPaths: z.array(relativePathSchema).max(20).optional(),
  selection: z
    .object({
      path: relativePathSchema,
      content: z.string().max(200_000),
      startLine: z.number().int().positive(),
      endLine: z.number().int().positive(),
    })
    .optional(),
});

const oralCreateSessionSchema = z
  .object({
    studentName: z.string().trim().min(1).max(60),
    level: z.enum(ORAL_AUDIENCE_LEVELS),
    subject: z.string().trim().min(1).max(100),
    topic: z.string().trim().min(1).max(200),
    learningGoal: z.string().trim().min(1).max(500),
    questionCount: z.union(
      ORAL_SESSION_LENGTHS.map((length) => z.literal(length)) as [
        z.ZodLiteral<5>,
        z.ZodLiteral<8>,
        z.ZodLiteral<10>,
        z.ZodLiteral<15>,
      ],
    ),
  })
  .strict();

const oralSubmitAnswerSchema = z
  .object({
    answer: z.string().trim().min(1).max(4_000),
  })
  .strict();

const oralSessionIdSchema = z.string().uuid();

const oralListSessionsSchema = z
  .object({
    status: z.enum(["active", "completed"]).optional(),
  })
  .strict();

function asyncRoute(
  handler: (
    request: express.Request,
    response: express.Response,
    next: express.NextFunction,
  ) => Promise<void>,
): RequestHandler {
  return (request, response, next) => {
    void handler(request, response, next).catch(next);
  };
}

function localOriginOnly(origin: string | undefined, callback: (error: Error | null, allow?: boolean) => void) {
  if (!origin || /^https?:\/\/(?:localhost|127\.0\.0\.1)(?::\d+)?$/.test(origin)) {
    callback(null, true);
  } else {
    callback(new Error("Origin không được phép"));
  }
}

function defaultPatchTitle(type: PatchOperation["type"], relativePath: string) {
  const labels: Record<PatchOperation["type"], string> = {
    update: "Cập nhật",
    create: "Tạo file",
    delete: "Xóa",
    rename: "Đổi tên",
    move: "Di chuyển",
    mkdir: "Tạo thư mục",
    "rename-directory": "Đổi tên thư mục",
  };
  return `${labels[type]} ${relativePath}`;
}
