# SunEdu -> SmartKid Android -> Backend mapping

Tai lieu nay la nguon doi chieu chung de web SunEdu va Android dung cung endpoint, payload va quy tac nghiep vu.

## 1. Chuoi tao noi dung cua giao vien

| Chuc nang | Route SunEdu | Backend API | Android | Trang thai |
|---|---|---|---|---|
| Danh sach khoa hoc | `/teacher/courses` | `GET /content/courses/` | `ManagementFeatureActivity` voi `teacher_courses` | Da noi API |
| Tao khoa hoc | `/teacher/courses/new` | `POST /content/courses/` | `ManagementCreateActivity` voi `teacher_courses` | Da noi JSON/multipart, tu dong mo noi dung |
| Chi tiet/sua khoa hoc | `/teacher/courses/:id`, `/edit` | `GET/PATCH /content/courses/:id/` | Danh sach quan ly chung | Chua co form sua rieng |
| Xuat ban khoa hoc | Course detail/action | `POST /content/courses/:id/publish/` | Action trong danh sach khoa hoc | Da noi API |
| Quan ly noi dung | `/teacher/courses/:id/content` | Tong hop cac API module/lesson | `TeacherCourseContentActivity` | Da bo sung |
| Tao chuong | Course content | `POST /content/courses/:courseId/modules/` | `ManagementCreateActivity` voi `teacher_modules` | Da bo sung |
| Sua/xoa chuong | Course content | `PATCH/DELETE /content/modules/:moduleId/` | Chua co UI sua/xoa | API san sang |
| Sap xep chuong | Course content | `POST /content/courses/:courseId/modules/reorder/` | Chua co keo tha | API san sang |
| Tao bai hoc | Course content | `POST /content/modules/:moduleId/lessons/` | `ManagementCreateActivity` voi `teacher_lessons` | Video URL/file, text, PDF/document, exercise |
| Sua/xoa bai hoc | `/teacher/lessons/:id/edit` | `PATCH/DELETE /content/lessons/:lessonId/` | Chua co form sua/xoa | API san sang |
| Xuat ban bai hoc | Lesson edit | `POST /content/lessons/:lessonId/publish/` | Co truong `published` khi tao | Action rieng chua co |
| Tao transcript video | Lesson edit | `POST /content/lessons/:lessonId/transcribe/` | Chua co nut Android | API san sang |
| Tao bai luyen tap trong bai hoc | Lesson edit/content | `POST /activities/exercises/` voi `lesson` | `ManagementCreateActivity` voi `teacher_exercises` | Tao/sua va gan dung bai hoc |
| Tao bai kiem tra doc lap | `/teacher/exams/new` | `POST /activities/exercises/` khong co `lesson` | `ManagementCreateActivity` voi `teacher_exams` | Da noi |
| Them cau hoi | Exam/lesson edit | Nested `questions` trong `POST/PATCH /activities/exercises/` | Trinh soan `mcq`, `short_answer`, `matching` | Da noi |
| Sua de/cau hoi | `/teacher/exams/:id/edit` | `GET/PATCH /activities/exercises/:id/` | Nap lai day du de, cai dat va cau hoi | Da noi |
| Cham bai/thong ke | `/teacher/exams/:id/grading` | `GET /attempts/`, `POST /grade/`, `GET /stats/` | Danh sach quan ly chung | Mot phan da noi |
| AI tao cau hoi | Exam/Lesson edit | `POST /activities/ai/generate-questions/` | Dialog chu de/so cau/do kho, chen vao trinh soan | Da noi |
| Tao tro choi | `/teacher/games` | `POST /teacher/games/` | Chua co tren Android | API san sang |
| AI tao tro choi | `/teacher/games` | `POST /teacher/games/ai-generate/` | Chua co tren Android | API san sang |
| Tao su kien | Service su kien | `POST /events/teacher/` | Chua co tren Android | API san sang |
| Gui phan hoi hoc vien | `/teacher/students/feedback` | `POST /teacher/students/feedback/` | Action trong quan ly hoc vien | Da noi |
| Tra loi hoi dap bai hoc | `/teacher/lesson-qa` | `POST /teacher/lesson-questions/:id/reply/` | Action trong quan ly hoi dap | Da noi |

### Payload tao khoa hoc

```json
{
  "title": "Toan lop 5",
  "subject_slug": "math",
  "grade": "5",
  "price": 0,
  "description": "Mo ta ngan",
  "introduction": "Gioi thieu chi tiet",
  "video_url": "https://www.youtube.com/watch?v=..."
}
```

Upload `thumbnail` va `video_file` dung `multipart/form-data`. Android co bo chon nguon video `YouTube` gui `video_url` hoac `Tai file video` gui `video_file`; ca hai deu khong bat buoc nen co the de trong neu khoa hoc chua co video gioi thieu. Gia khoa hoc la so nguyen khong am. Anh toi da 5 MB, video toi da 500 MB va file duoc stream tu Content URI, khong doc toan bo vao RAM. Tao thanh cong se mo ngay man quan ly chuong/bai hoc, thay vi bao thanh cong khi noi dung con chua duoc luu nhu wizard web cu.

### Payload tao chuong

```json
{
  "course": "course_uuid",
  "title": "Chuong 1",
  "position": 0
}
```

### Payload tao bai hoc

```json
{
  "module": "module_uuid",
  "title": "Bai 1.1",
  "position": 0,
  "content_type": "video",
  "video_url": "https://youtu.be/...",
  "introduction": "Noi dung gioi thieu",
  "text_content": "Noi dung van ban",
  "requires_exercise_completion": true,
  "published": true
}
```

`content_type` backend chap nhan `lesson`, `exploration`, `exercise`, `quiz`, `video`, `pdf`, `text`, `document`. Android hien `video`, `text`, `exercise`, `pdf`, `document`. Video co the dung URL hoac file; PDF/tai lieu bat buoc chon file va dung multipart. Voi file bai hoc, Android tao metadata truoc roi `PATCH` file vao bai vua tao; neu upload hong thi bao ro bai hoc da tao nhung file chua tai len, khong nuot loi.

Android bat buoc URL hop le hoac file cho bai `video`, bat buoc `text_content` cho bai `text`, file dung dinh dang cho bai `pdf`/`document`, va tinh `position` moi bang `max(position) + 1` de tranh trung thu tu sau khi xoa/sap xep.

### Payload bai tap/bai kiem tra

```json
{
  "lesson": "lesson_uuid",
  "title": "Luyen tap bai 1",
  "type": "mcq",
  "published": true,
  "settings": {
    "duration_seconds": 1800,
    "pass_score": 50,
    "max_attempts": 1,
    "shuffle_questions": true,
    "shuffle_choices": true,
    "show_answers": "always",
    "course_id": "course_uuid"
  },
  "questions": [
    {
      "prompt": "2 + 2 bang bao nhieu?",
      "meta": { "type": "mcq", "points": 1 },
      "choices": [
        { "text": "3", "is_correct": false, "position": 0 },
        { "text": "4", "is_correct": true, "position": 1 }
      ]
    }
  ]
}
```

Bo truong `lesson` de tao bai kiem tra doc lap. Co `lesson` thi day la bai luyen tap nam trong bai hoc va khong nen hien trong danh sach bai thi doc lap cua hoc sinh.

`meta.type` phai dung enum domain `mcq`, `short_answer` hoac `matching`; diem tung cau nam o `meta.points`, khong phai `meta.score`. `settings.description` va `settings.level` khong co cot/model backend nen Android khong gui hai truong nay.

Trinh soan Android rang buoc mot dap an dung cho `mcq`, danh sach cau tra loi chap nhan va nguong tuong dong cho `short_answer`, va 2-10 cap cho `matching`. Man noi dung khoa hoc tai bai tap theo tung `lesson_id`, loc lai theo lesson o client, hien **Them** khi chua co va **Sua** khi da co de tranh tao trung. AI hien sinh cau hoi MCQ, sau do giao vien van co the sua truoc khi luu.

## 2. Chuoi hoc va xem video cua hoc sinh

| Chuc nang | Route SunEdu | Backend API | Android | Trang thai |
|---|---|---|---|---|
| Khoa hoc cua toi | `/student/courses` | `GET /student/courses/` | `CoursesFragment` | Da noi |
| Danh muc khoa hoc | `/student/courses/catalog` | `GET /student/catalog/` | `CatalogActivity` | Da noi |
| Chi tiet khoa hoc | `/student/courses/:id` | `GET /student/courses/:id/` | `CourseDetailActivity` | Da noi |
| Dang ky mien phi | Course detail | `POST /content/courses/:id/enroll/` | `CourseDetailActivity` | Da noi |
| Player bai hoc | `/student/courses/:id/learn` | `GET /student/courses/:id/player/:lessonId/` | `LessonPlayerActivity` | Da noi |
| YouTube/Vimeo | Player | `video_url` tu player API | `WebView` embed trong app | Da bo sung |
| Video file/URL truc tiep | Player | `video_file` hoac `video_url` | `VideoView` kem JWT header | Da noi |
| Tai lieu | Player | `document_file` | Mo bang ung dung ngoai | Da noi co ban |
| Van ban | Player | `text_content`/`introduction` | Card noi dung + TextToSpeech | Da noi |
| Kiem tra khoa bai | Player | `GET /content/lessons/:id/unlock-check/` | Chan player va hien ly do | Da bo sung |
| Danh dau xem xong | Player | `POST /content/lessons/:id/progress/` | Nut hoan thanh/video ended | Da noi |
| Tai bai tap cua bai hoc | Player | `GET /activities/exercises/?lesson_id=:id&status=published` | `LessonPlayerActivity` | Da bo sung |
| Bat dau bai tap | Player | `POST /activities/exercises/:id/start/` | `LessonExerciseActivity` | Da bo sung |
| Gui tung cau tra loi | Player | `POST /activities/attempts/:attemptId/answers/` | `LessonExerciseActivity` | Da bo sung |
| Chot bai tap | Player | `POST /activities/attempts/:attemptId/finalize/` | `LessonExerciseActivity` | Da bo sung |
| Ghi nhan hoan thanh bai tap | Player | `POST /content/lessons/:id/progress/` | `exercise_completed`, `exercise_score`, `completed` | Da bo sung |
| Hoi dap bai hoc | Player | `/student/lesson-questions/` va reply/react/report | `LessonDiscussionActivity` | Mot phan da noi |
| AI Tutor | Player | `/student/ai/tutor/*` | `AITutorActivity` | Da noi cac luong chinh |

Backend player khong tra san bai tap trong response. Ca web va Android phai goi them `GET /activities/exercises/?lesson_id=...` sau khi tai bai hoc.

Android hien co the lam ca ba dang giao vien tao: `mcq` gui `selected_choice_id`, `short_answer` gui `text`, va `matching` gui mang cap `left_id`/`right_id` theo contract backend.

## 3. Cac nhom SunEdu khac

| Nhom | Endpoint chinh | Android hien tai | Ghi chu |
|---|---|---|---|
| Bai thi doc lap hoc sinh | `/student/exams/*` hoac `/activities/*` | `ExamsFragment`, `ExamActivity` | Android dang dung wrapper student API |
| Chung chi/xep hang | `/student/exams/certificates/`, ranking | Mot phan | Can UI rieng neu muon dong bo web |
| Tro choi hoc sinh | `/student/games/*` | Chua co | Backend co list/start/submit/leaderboard |
| Lo trinh hoc | `/student/learning-path/` | Learning analysis hien co | Chua dong bo UI CoursePlayer |
| AI danh gia | `/student/ai/assessment/*` | `AssessmentActivity` | Da noi |
| Thanh toan | `/payments/plans/`, history, MoMo | Payment/Cart activities | Da noi cac luong chinh |
| Ho so/doi mat khau/phu huynh | `/account/*`, `/student/account/*` | Profile activities | Da noi |
| Su kien | `/events/upcoming/`, `/events/teacher/` | Chua co | Backend co CRUD giao vien |
| Bao cao/admin | `/admin/reports/*`, dashboard, users | Admin/Management activities | Mot phan da noi |

## 4. Route khong nen dua vao Android luc nay

- `/teacher/classes`, `/teacher/classes/:id`: frontend SunEdu chua co luong API lop hoc hoan chinh.
- `/teacher/classes/:id/assignments`: module assignment rieng dang bi tat/loi import; khong trung voi `activities/exercises` cua bai hoc.
- `/teacher/classes/:id/live`: frontend dung du lieu mock, backend chua co lich/phong hoc truc tuyen that.
- `/teacher/courses/content-library`: backend co CRUD nhung da duoc loai khoi Android theo yeu cau san pham truoc do.

## 5. Sai lech can sua tren web/backend

1. SunEdu goi `POST /content/modules/:moduleId/lessons/reorder/`, nhung `content/urls.py` hien khong khai bao endpoint nay. Can them backend route/view hoac bo nut reorder bai hoc tren web.
2. Upload video toi da 500 MB va tai lieu toi da 50 MB dung multipart. Android da dung request streaming tu Content URI; voi video rat lon van nen chuyen sang presigned upload truc tiep R2 de tranh timeout proxy/Gunicorn.
3. Player API chi tra noi dung bai hoc va progress, khong tra exercises. Moi frontend phai tai exercises rieng theo `lesson_id`.
4. Bai thi doc lap va bai tap trong bai hoc cung dung model Exercise. Quy tac phan loai bat buoc la `lesson == null` cho bai thi va `lesson != null` cho bai tap.
5. Khi hoan thanh bai tap trong bai hoc, frontend phai gui them progress voi `exercise_completed`, `exercise_score` va `completed`; finalize attempt mot minh khong mo khoa bai tiep theo.
6. YouTube/Vimeo khong phat duoc bang Android `VideoView`; phai chuyen sang URL embed trong WebView hoac SDK player chuyen dung.
7. Backend Exercise list loc trang thai bang query `status=published`; query `published=true` trong CoursePlayer web hien khong duoc backend doc.
8. `LessonUnlockCheckView` backend co nhanh dung `previous_lesson` truoc khi gan khi chuong truoc chua hoan thanh; can khoi tao bien hoac return som de tranh loi 500. Android hien fallback khi endpoint nay loi, nhung backend van can sua.
9. Khi chi danh dau da xem video, frontend chi gui `video_watched: true`. Khong gui som `completed: true`, vi backend se tu quyet dinh hoan thanh dua tren `requires_exercise_completion`.
10. `CourseCreateWizard.vue` giu cau hoi/cai dat bai tap o state cuc bo nhung submit khoa hoc khong POST/PATCH `activities/exercises`; luong Android luu bai tap qua endpoint activities sau khi bai hoc da ton tai.
11. UI gan bai cho lop cua frontend dang la mock/module assignment backend bi tat, nen khong dua vao luong tao Android cho den khi co API that.
12. Permission ghi cua `activities/exercises` chi kiem tra role giao vien/admin, chua kiem tra exercise co thuoc khoa hoc cua giao vien do hay khong; response list cung khong co owner. Android da rang buoc bai tap theo lesson dang mo, nhung backend van can bo sung object-level ownership de bao ve API va loc danh sach de doc lap theo giao vien.

## 6. Phan con lai ngoai pham vi "tao"

- Sua/xoa khoa hoc bang form rieng, sua/xoa chuong va bai hoc, keo tha sap xep.
- Transcribe video va quan ly thu vien noi dung.
- Lich mo/han nop de doc lap: backend co `scheduled_at`/`end_at`, nhung form tao Android chua mo hai truong nay; khi sua, Android giu nguyen gia tri server da co.

Nhung muc nay khong chan chuoi tao that tren Android: tao khoa hoc -> tao chuong -> tao bai hoc/media -> tao hoac sua bai tap -> hoc sinh lam bai.
