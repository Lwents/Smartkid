import path from "node:path";
import type { AssistantSettings, ModuleMapping } from "@smartkid/shared";
import type { RuntimeConfig } from "./config.js";
import { JsonStore } from "./store.js";

export const DEFAULT_MODULE_MAPPING: ModuleMapping = {
  student: {
    keywords: [
      "student",
      "pupil",
      "learner",
      "hocvien",
      "hocsinh",
      "StudentActivity",
      "StudentFragment",
      "student_",
      "res-student",
    ],
    excludes: ["teacher", "admin"],
  },
  teacher: {
    keywords: [
      "teacher",
      "educator",
      "giaovien",
      "TeacherActivity",
      "TeacherFragment",
      "teacher_",
      "res-teacher",
    ],
    excludes: ["student", "admin"],
  },
  admin: {
    keywords: [
      "admin",
      "administrator",
      "AdminActivity",
      "AdminFragment",
      "admin_dialog",
      "admin_notification",
      "layoutAdmin",
      "inputAdmin",
      "res-admin",
    ],
    excludes: ["student", "teacher"],
  },
  backend: {
    keywords: [
      "backend",
      "server",
      "controller",
      "service",
      "repository",
      "database",
      "network",
      "retrofit",
      "firebase",
      "supabase",
      "endpoint",
      "api/",
      "besmartkid",
      "django",
      "manage.py",
      "RoomDatabase",
    ],
    excludes: ["androidTest", "testFixtures"],
  },
};

export class SettingsService {
  private readonly store: JsonStore<AssistantSettings>;
  private settings!: AssistantSettings;

  constructor(private readonly config: RuntimeConfig) {
    this.store = new JsonStore(path.join(config.dataRoot, "settings.json"), this.defaults());
  }

  async initialize() {
    const stored = await this.store.read();
    this.settings = {
      ...this.defaults(),
      ...stored,
      moduleMapping: {
        ...DEFAULT_MODULE_MAPPING,
        ...stored.moduleMapping,
      },
    };
    await this.store.write(this.settings);
  }

  get(): AssistantSettings {
    return structuredClone(this.settings);
  }

  async update(values: Partial<AssistantSettings>): Promise<AssistantSettings> {
    this.settings = {
      ...this.settings,
      ...values,
      moduleMapping: values.moduleMapping
        ? structuredClone(values.moduleMapping)
        : this.settings.moduleMapping,
    };
    await this.store.write(this.settings);
    return this.get();
  }

  private defaults(): AssistantSettings {
    return {
      aiModel: this.config.aiModel,
      aiBaseUrl: this.config.aiBaseUrl,
      temperature: 0.2,
      maxContextFiles: 12,
      maxFileSize: 1_000_000,
      defaultModule: "student",
      responseLanguage: "Tiếng Việt",
      readOnly: false,
      moduleMapping: DEFAULT_MODULE_MAPPING,
    };
  }
}
