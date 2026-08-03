import { afterEach, describe, expect, it } from "vitest";
import { createTestProject, type TestProject } from "./helpers.js";

let project: TestProject | undefined;
afterEach(async () => {
  await project?.cleanup();
  project = undefined;
});

describe("scan, read, search và module mapping", () => {
  it("đọc file thật, tìm theo nội dung/class/Android ID và phân loại module", async () => {
    project = await createTestProject({
      "app/src/main/AndroidManifest.xml":
        '<manifest><application><activity android:name=".student.StudentActivity"/></application></manifest>',
      "app/src/main/java/com/smartkid/student/StudentActivity.kt": [
        "package com.smartkid.student",
        "class StudentActivity {",
        "  fun openLesson() = Unit",
        "}",
      ].join("\n"),
      "app/src/main/res/layout/student_home.xml":
        '<LinearLayout><TextView android:id="@+id/studentName" android:text="@string/student_name" /></LinearLayout>',
      "app/src/main/res/values/strings.xml":
        '<resources><string name="student_name">Học sinh</string></resources>',
      "app/src/main/java/com/smartkid/Keys.kt":
        'object Keys { const val API_KEY = "sk-sensitive-production-key-123456" }',
    });

    const document = await project.services.files.read(
      "app/src/main/java/com/smartkid/student/StudentActivity.kt",
    );
    expect(document.language).toBe("kotlin");
    expect(document.content).toContain("openLesson");
    expect(document.module).toBe("student");

    const classResults = project.services.search.search({
      query: "StudentActivity",
      mode: "class",
    });
    expect(classResults[0]).toMatchObject({
      path: "app/src/main/java/com/smartkid/student/StudentActivity.kt",
      line: 2,
    });

    const idResults = project.services.search.search({
      query: "studentName",
      mode: "android-id",
    });
    expect(idResults).toHaveLength(1);
    expect(idResults[0]?.line).toBe(1);

    const secretWasIndexed = project.services.scanner
      .getFiles()
      .some((file) => file.path.endsWith("Keys.kt"));
    expect(secretWasIndexed).toBe(false);
    await expect(
      project.services.files.read("app/src/main/java/com/smartkid/Keys.kt"),
    ).rejects.toMatchObject({ code: "SENSITIVE_FILE" });
  });

  it("cập nhật mapping tùy chỉnh và quét lại", async () => {
    project = await createTestProject({
      "feature/src/main/java/com/acme/GuardianPortal.kt": "class GuardianPortal",
    });
    expect(project.services.scanner.getFiles()[0]?.module).toBeUndefined();
    const current = project.services.settings.get();
    await project.services.settings.update({
      moduleMapping: {
        ...current.moduleMapping,
        admin: {
          ...current.moduleMapping.admin,
          keywords: [...current.moduleMapping.admin.keywords, "GuardianPortal"],
        },
      },
    });
    await project.services.scanner.scan();
    expect(project.services.scanner.getFiles()[0]?.module).toBe("admin");
  });

  it("phân tích XML và tìm resource/inflater", async () => {
    project = await createTestProject({
      "app/src/main/res/layout/admin_dialog.xml":
        '<LinearLayout android:background="@drawable/admin_panel"><TextView android:id="@+id/title" android:text="@string/admin_title" /></LinearLayout>',
      "app/src/main/res/drawable/admin_panel.png": Buffer.from([0x89, 0x50, 0x4e, 0x47]),
      "app/src/main/res/values/strings.xml":
        '<resources><string name="admin_title">Quản trị</string></resources>',
      "app/src/main/java/AdminDialog.kt":
        "class AdminDialog { fun show() = R.layout.admin_dialog }",
    });
    const document = await project.services.files.read("app/src/main/res/layout/admin_dialog.xml");
    expect(document.xml?.views.some((view) => view.id === "title")).toBe(true);
    expect(document.xml?.resources.find((item) => item.name === "admin_title")?.exists).toBe(true);
    expect(document.xml?.resources.find((item) => item.name === "admin_panel")?.exists).toBe(true);
    expect(document.xml?.inflaters[0]?.path).toBe("app/src/main/java/AdminDialog.kt");
    expect(
      project.services.search.search({ query: "admin_panel", mode: "file" })[0]?.path,
    ).toBe("app/src/main/res/drawable/admin_panel.png");
  });
});
