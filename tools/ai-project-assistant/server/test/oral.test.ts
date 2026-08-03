import fs from "node:fs/promises";
import type { OralSession } from "@smartkid/shared";
import request from "supertest";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  OpenAiOrDemoOralEngine,
  parseOralModelResponse,
} from "../src/oral.js";
import { createTestProject, type TestProject } from "./helpers.js";

const validSession = {
  studentName: "Minh",
  level: "THCS",
  subject: "Toán",
  topic: "Phương trình bậc nhất",
  learningGoal: "Tự giải thích được vì sao chuyển vế đổi dấu",
  questionCount: 5,
} as const;

const projects: TestProject[] = [];

afterEach(async () => {
  await Promise.all(projects.splice(0).map((project) => project.cleanup()));
});

function unanswered(session: OralSession) {
  return session.turns.filter((turn) => turn.studentAnswer === undefined);
}

function questionMarks(session: OralSession): number {
  const current = session.turns.at(-1);
  return `${current?.teacherMessage ?? ""}${current?.question ?? ""}`
    .split("")
    .filter((character) => character === "?").length;
}

describe("oral API", () => {
  it("chạy demo khi key trống và mở đầu đúng một câu hỏi thầy–em", async () => {
    const project = await createTestProject();
    projects.push(project);

    const config = await request(project.app).get("/api/oral/config").expect(200);
    expect(config.body.data).toMatchObject({
      aiConfigured: false,
      demoMode: true,
      defaultLanguage: "vi-VN",
    });
    await request(project.app)
      .put("/api/settings")
      .send({ aiModel: "model-do-nguoi-dung-chon" })
      .expect(200);
    const updatedConfig = await request(project.app)
      .get("/api/oral/config")
      .expect(200);
    expect(updatedConfig.body.data).toMatchObject({
      aiConfigured: false,
      demoMode: true,
      model: "model-do-nguoi-dung-chon",
    });

    const response = await request(project.app)
      .post("/api/oral/sessions")
      .send(validSession)
      .expect(201);
    const session = response.body.data as OralSession;

    expect(session.status).toBe("active");
    expect(session.turns).toHaveLength(1);
    expect(unanswered(session)).toHaveLength(1);
    expect(session.turns[0]?.teacherMessage).toMatch(/thầy/iu);
    expect(session.turns[0]?.question).toMatch(/em/iu);
    expect(questionMarks(session)).toBe(1);
  });

  it("gợi một nấc rồi làm mẫu bước đầu khi em vẫn chưa biết", async () => {
    const project = await createTestProject();
    projects.push(project);
    const created = await request(project.app)
      .post("/api/oral/sessions")
      .send(validSession)
      .expect(201);
    const id = created.body.data.id as string;

    const first = await request(project.app)
      .post(`/api/oral/sessions/${id}/answers`)
      .send({ answer: "Em chưa biết" })
      .expect(200);
    const firstSession = first.body.data as OralSession;
    expect(firstSession.turns[0]?.feedback).toContain("chưa biết");
    expect(firstSession.turns.at(-1)?.teacherMessage).toContain("gợi em một nấc");
    expect(unanswered(firstSession)).toHaveLength(1);
    expect(questionMarks(firstSession)).toBe(1);

    const second = await request(project.app)
      .post(`/api/oral/sessions/${id}/answers`)
      .send({ answer: "Em không hiểu" })
      .expect(200);
    const secondSession = second.body.data as OralSession;
    expect(secondSession.turns.at(-1)?.teacherMessage).toContain("làm mẫu");
    expect(secondSession.turns.at(-1)?.question).toContain("phần đã biết");
    expect(unanswered(secondSession)).toHaveLength(1);
    expect(questionMarks(secondSession)).toBe(1);
  });

  it("hoàn thành đúng số câu, có điểm/tổng kết và lưu lịch sử local", async () => {
    const project = await createTestProject();
    projects.push(project);
    const created = await request(project.app)
      .post("/api/oral/sessions")
      .send(validSession)
      .expect(201);
    const id = created.body.data.id as string;
    let session = created.body.data as OralSession;

    for (let index = 0; index < validSession.questionCount; index += 1) {
      const response = await request(project.app)
        .post(`/api/oral/sessions/${id}/answers`)
        .send({
          answer:
            "Theo em, vì ta thực hiện phép biến đổi tương đương nên hai vế vẫn giữ quan hệ bằng nhau.",
        })
        .expect(200);
      session = response.body.data as OralSession;
    }

    expect(session.status).toBe("completed");
    expect(session.currentPhase).toBe("complete");
    expect(session.turns).toHaveLength(validSession.questionCount);
    expect(session.summary?.overallScore).toBeGreaterThanOrEqual(0);
    expect(session.summary?.overallScore).toBeLessThanOrEqual(100);
    expect(session.summary?.teacherClosing).toMatch(/thầy/iu);

    const history = await request(project.app)
      .get("/api/oral/sessions?status=completed")
      .expect(200);
    expect(history.body.data).toHaveLength(1);
    expect(history.body.data[0].id).toBe(id);

    const mode = (await fs.stat(`${project.dataRoot}/oral-sessions.json`)).mode & 0o777;
    expect(mode).toBe(0o600);
  });

  it("hỗ trợ kết thúc sớm, đọc lại và xóa phiên", async () => {
    const project = await createTestProject();
    projects.push(project);
    const created = await request(project.app)
      .post("/api/oral/sessions")
      .send(validSession)
      .expect(201);
    const id = created.body.data.id as string;

    const ended = await request(project.app)
      .post(`/api/oral/sessions/${id}/end`)
      .expect(200);
    expect(ended.body.data.status).toBe("completed");
    expect(ended.body.data.summary).toBeDefined();

    await request(project.app).get(`/api/oral/sessions/${id}`).expect(200);
    await request(project.app).delete(`/api/oral/sessions/${id}`).expect(200);
    const missing = await request(project.app)
      .get(`/api/oral/sessions/${id}`)
      .expect(404);
    expect(missing.body.error.code).toBe("ORAL_SESSION_NOT_FOUND");
  });

  it("validate toàn bộ input và trả lỗi có cấu trúc", async () => {
    const project = await createTestProject();
    projects.push(project);

    const invalid = await request(project.app)
      .post("/api/oral/sessions")
      .send({
        ...validSession,
        studentName: "",
        questionCount: 7,
        unexpected: true,
      })
      .expect(422);
    expect(invalid.body).toMatchObject({
      success: false,
      error: {
        code: "VALIDATION_ERROR",
        message: "Dữ liệu gửi lên không hợp lệ",
      },
    });

    const badId = await request(project.app)
      .post("/api/oral/sessions/not-an-id/answers")
      .send({ answer: "" })
      .expect(422);
    expect(badId.body.error.code).toBe("VALIDATION_ERROR");
  });

  it("giữ nhánh gợi ý cục bộ dù model cố đưa ngay đáp án", () => {
    const session: OralSession = {
      id: "00000000-0000-4000-8000-000000000001",
      config: validSession,
      status: "active",
      currentPhase: "warmup",
      turns: [
        {
          id: "turn-1",
          phase: "warmup",
          teacherMessage: "Thầy cùng em bắt đầu.",
          question: "Em nhớ gì về phương trình?",
          studentAnswer: "Em chưa biết",
          createdAt: "2026-07-29T00:00:00.000Z",
          answeredAt: "2026-07-29T00:01:00.000Z",
        },
      ],
      createdAt: "2026-07-29T00:00:00.000Z",
      updatedAt: "2026-07-29T00:01:00.000Z",
    };
    const result = parseOralModelResponse(
      JSON.stringify({
        teacherMessage: "Đáp án hoàn chỉnh là chuyển vế đổi dấu.",
        question: "Em chép lại đáp án này nhé?",
        phase: "challenge",
        assessment: {
          correctness: 100,
          reasoning: 100,
          clarity: 100,
          confidence: "high",
        },
      }),
      { session, answer: "Em chưa biết" },
    );

    expect(result.teacherMessage).toContain("gợi em một nấc");
    expect(result.teacherMessage).not.toContain("Đáp án hoàn chỉnh");
    expect(result.question).toMatch(/từ khóa|dữ kiện/iu);
    expect(result.assessment?.confidence).toBe("low");
  });

  it("gọi API OpenAI-compatible và chuẩn hóa output về thầy–em, một câu hỏi", async () => {
    const session: OralSession = {
      id: "00000000-0000-4000-8000-000000000002",
      config: validSession,
      status: "active",
      currentPhase: "warmup",
      turns: [],
      createdAt: "2026-07-29T00:00:00.000Z",
      updatedAt: "2026-07-29T00:00:00.000Z",
    };
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          choices: [
            {
              message: {
                content: JSON.stringify({
                  teacherMessage: "Tôi sẽ cùng bạn suy nghĩ từng bước.",
                  question: "Bạn nhớ gì về chuyển vế? Bạn có ví dụ không?",
                  phase: "warmup",
                }),
              },
            },
          ],
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );
    const engine = new OpenAiOrDemoOralEngine(
      {
        aiApiKey: "test-key",
        aiBaseUrl: "https://example.test/v1/",
        aiModel: "test-model",
      },
      fetchMock,
    );

    const result = await engine.generate({ session });

    expect(fetchMock).toHaveBeenCalledOnce();
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      "https://example.test/v1/chat/completions",
    );
    expect(result.teacherMessage).toMatch(/thầy/iu);
    expect(result.teacherMessage).toMatch(/em/iu);
    expect(result.question).toBe("Em nhớ gì về chuyển vế?");
  });
});
