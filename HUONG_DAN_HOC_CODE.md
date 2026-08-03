# Huong dan hoc code SmartKid Android

Tai lieu nay la ban do de doc va hoc code cua ung dung Android SmartKid theo tung
role. Pham vi chinh la thu muc `app/`. Thu muc `tools/ai-project-assistant/` la
cong cu phu, khong thuoc luong chay cua ung dung Android nen khong can doc khi
dang hoc cac role Student, Teacher va Admin.

## Cach dung tai lieu

Voi moi chuc nang, hay doc theo thu tu:

1. File giao dien Java (`Activity` hoac `Fragment`) de biet nguoi dung thao tac gi.
2. File layout XML de biet cac view duoc hien thi o dau.
3. File `Repository` de biet du lieu den tu API nao.
4. `ApiClient` de biet request duoc gui, gan JWT va xu ly loi nhu the nao.
5. File model hoac `SafeJson` de biet JSON duoc chuyen thanh du lieu Java ra sao.

Luong tong quat cua hau het chuc nang:

```text
Nguoi dung thao tac
    -> Activity/Fragment
    -> Repository
    -> ApiClient
    -> REST API Django
    -> ApiCallback.onSuccess/onError
    -> cap nhat giao dien
```

---

# Phan 1 - Nen tang dung chung

Day la phan nen doc truoc khi hoc tung role, vi Student, Teacher va Admin deu dung
chung cac lop nay.

## 1.1 Ung dung bat dau o dau?

### `SmartKidApplication.java`

Duong dan:

```text
app/src/main/java/com/example/smartkid/SmartKidApplication.java
```

Doan can hoc: `onCreate()`.

No lam hai viec chinh:

- Khoi tao `ApiClient` dung chung cho toan app.
- Goi `ApiEnvironment.resolveAsync()` de chon backend local hoac VPS.

Luong:

```text
Android khoi dong app
    -> SmartKidApplication.onCreate()
    -> ApiClient.initialize()
    -> ApiEnvironment.resolveAsync()
```

### `MainActivity.java`

Duong dan:

```text
app/src/main/java/com/example/smartkid/MainActivity.java
```

Doan quan trong:

- `onCreate()`: hien splash va hen thoi diem dieu huong.
- `performNavigationSafely()`: kiem tra session va chon man hinh tiep theo.
- `onDestroy()`: huy cac callback cua splash.

Trong `performNavigationSafely()`:

```text
Neu SessionManager.hasSession() == false
    -> LoginActivity

Neu da co session
    -> RoleNavigation.destination()
    -> StudentHomeActivity / TeacherDashboardActivity / AdminDashboardActivity
```

## 1.2 Role duoc xac dinh o dau?

### `UserRole.java`

```text
app/src/main/java/com/example/smartkid/common/navigation/UserRole.java
```

File nay chuan hoa role server tra ve:

| Gia tri server | Enum trong Android |
|---|---|
| `student` | `UserRole.STUDENT` |
| `teacher`, `instructor` | `UserRole.TEACHER` |
| `admin` | `UserRole.ADMIN` |
| Gia tri la | `UserRole.UNKNOWN` |

Doan can hoc: `fromString(String role)`.

### `RoleNavigation.java`

```text
app/src/main/java/com/example/smartkid/common/navigation/RoleNavigation.java
```

Doan can hoc: `destinationForRole(UserRole role)`.

Anh xa role sang man hinh:

```text
STUDENT -> StudentHomeActivity
TEACHER -> TeacherDashboardActivity
ADMIN   -> AdminDashboardActivity
UNKNOWN -> UnsupportedRoleActivity
```

Role duoc lay tu `SessionManager.getUser().getRole()`. Thong tin nay duoc luu sau
khi dang nhap thanh cong.

## 1.3 Dang nhap va luu session

Doc theo thu tu:

1. `app/src/main/java/com/example/smartkid/feature/shared/auth/LoginActivity.java`
2. `app/src/main/res-auth/layout/auth_activity_login.xml`
3. `app/src/main/java/com/example/smartkid/data/repository/AuthRepository.java`
4. `app/src/main/java/com/example/smartkid/data/local/SessionManager.java`
5. `app/src/main/java/com/example/smartkid/data/model/AuthResult.java`
6. `app/src/main/java/com/example/smartkid/data/model/User.java`

Luong dang nhap:

```text
LoginActivity.submitLogin()
    -> AuthRepository.login(identifier, password, otp, callback)
    -> POST account/login/
    -> doc access token, refresh token va user
    -> SessionManager.saveSession()
    -> RoleNavigation.destination()
```

Trong `AuthRepository.login()` can chu y:

- Neu chuoi dang nhap co `@`, request gui truong `email`.
- Neu khong co `@`, request gui truong `username`.
- OTP chi duoc gui khi nguoi dung co nhap.
- API dang nhap dung `authenticated = false` vi luc nay chua co JWT.
- Sau khi thanh cong, access token va refresh token duoc luu vao session.

Nhung man hinh xac thuc khac:

| Chuc nang | File Java | Layout | API |
|---|---|---|---|
| Dang ky | `feature/shared/auth/RegisterActivity.java` | `res-auth/layout/auth_activity_register.xml` | `account/register/` |
| Quen mat khau | `feature/shared/auth/ForgotPasswordActivity.java` | `res-auth/layout/auth_activity_forgot_password.xml` | `account/password/reset/` |
| Dat lai mat khau | `feature/shared/auth/ResetPasswordActivity.java` | `res-auth/layout/auth_activity_reset_password.xml` | `account/password/reset/confirm/` |
| Dang xuat | `data/repository/AuthRepository.java` | Goi tu man hinh profile/dashboard | `account/logout/` |

## 1.4 Phan goi API dung chung

### `AppConstants.java`

```text
app/src/main/java/com/example/smartkid/common/util/AppConstants.java
```

Hoc cac noi dung:

- `API_LOCAL_URL`: backend local/emulator.
- `API_FALLBACK_URL`: backend VPS.
- Cac endpoint xac thuc va Student dung chung.
- Timeout request thuong va request AI.

### `ApiEnvironment.java`

```text
app/src/main/java/com/example/smartkid/data/remote/ApiEnvironment.java
```

Hoc cac ham:

- `resolveAsync()`: thu cac server va chon server dung duoc.
- `buildCandidates()`: tao danh sach URL can thu.
- `canReach()`: probe host va port bang TCP socket.

### `ApiClient.java`

```text
app/src/main/java/com/example/smartkid/data/remote/ApiClient.java
```

Nen doc theo thu tu sau:

1. `getInstance()` de hieu singleton.
2. `get()`, `getArray()`, `getValue()`.
3. `post()`, `put()`, `patch()`, `delete()`.
4. `request()` va `execute()` de xem Volley gui JSON.
5. `getHeaders()` de xem JWT duoc gan vao request.
6. `queueForTokenRefresh()` va `refreshAccessToken()` de xem xu ly HTTP 401.
7. `mapError()` de xem loi server duoc doi thanh thong bao cho nguoi dung.
8. `multipart()` va `executeMultipart()` de xem upload file/video.

Y nghia tham so `authenticated`:

```text
false -> khong gan Authorization header
true  -> gan Authorization: Bearer <access_token>
```

### Callback va loi API

```text
app/src/main/java/com/example/smartkid/data/remote/ApiCallback.java
app/src/main/java/com/example/smartkid/data/remote/ApiError.java
```

Moi request khong tra ket qua ngay. Noi goi truyen vao:

```java
new ApiCallback<JSONObject>() {
    @Override
    public void onSuccess(JSONObject data) {
        // Doc du lieu va cap nhat giao dien.
    }

    @Override
    public void onError(ApiError error) {
        // Hien thi loi cho nguoi dung.
    }
}
```

## 1.5 Repository, model va giao dien lien ket voi nhau

### Repository

Thu muc:

```text
app/src/main/java/com/example/smartkid/data/repository/
```

Repository co nhiem vu:

- Biet endpoint API.
- Tao JSON request body.
- Goi `ApiClient`.
- Doc JSON response.
- Tra model/ket qua cho Activity hoac Fragment.

Activity khong nen tu xu ly HTTP truc tiep.

### Model

Thu muc:

```text
app/src/main/java/com/example/smartkid/data/model/
```

Mot so model can hoc:

| Model | Y nghia |
|---|---|
| `User` | Nguoi dung dang nhap, role, email, lop |
| `Course` | Thong tin tom tat khoa hoc |
| `CourseDetail` | Chi tiet khoa hoc va danh sach section |
| `CourseSection` | Module/chuong cua khoa hoc |
| `Lesson` | Bai hoc trong module |
| `LessonContent` | Noi dung bai hoc/video/tai lieu |
| `DashboardSummary` | Du lieu dashboard Student |
| `FeatureItem` | Model tong quat cho cac danh sach quan ly |

### `SafeJson.java`

```text
app/src/main/java/com/example/smartkid/common/util/SafeJson.java
```

File nay doc JSON an toan va chap nhan nhieu ten field. Vi du server co the tra
`full_name`, `fullName` hoac `display_name`, code van co the tim duoc gia tri.

## 1.6 File giao dien Java va layout XML

Mot Activity thuong lien ket voi mot layout qua `setContentView()`:

```java
setContentView(R.layout.auth_activity_login);
```

Sau do lay view bang:

```java
identifierInput = findViewById(R.id.inputIdentifier);
```

Khi hoc mot man hinh, tim dong `setContentView` truoc, sau do mo file XML co cung
ten trong cac thu muc `res-*/layout/`.

---

# Phan 2 - Role Student

Thu muc chinh:

```text
app/src/main/java/com/example/smartkid/feature/student/
```

Entry point cua role Student:

```text
feature/student/shell/StudentHomeActivity.java
```

Layout:

```text
app/src/main/res-student-home/layout/home_activity_home.xml
```

`StudentHomeActivity` tao `ViewPager2` gom bon man hinh:

```text
Dashboard -> Courses -> Exams -> Profile
```

## 2.1 Ban do cac chuc nang Student

| Chuc nang | File giao dien | Layout | Repository/API |
|---|---|---|---|
| Dashboard | `feature/student/dashboard/StudentDashboardFragment.java` | `res-student-home/layout/home_fragment_dashboard.xml` | `DashboardRepository`, `student/dashboard/` |
| Khoa hoc cua toi | `feature/student/course/CoursesFragment.java` | `res-course/layout/course_fragment_courses.xml` | `CourseRepository.loadMyCourses()` |
| Danh muc khoa hoc | `feature/student/course/CatalogActivity.java` | `res-course/layout/course_activity_catalog.xml` | `CourseRepository.loadCatalog()` |
| Chi tiet khoa hoc | `feature/student/course/CourseDetailActivity.java` | `res-course/layout/course_activity_detail.xml` | `CourseRepository.loadCourseDetail()` |
| Hoc bai | `feature/student/course/LessonPlayerActivity.java` | `res-course/layout/course_activity_lesson_player.xml` | `CourseRepository.loadLesson()` |
| Bai tap bai hoc | `feature/student/course/LessonExerciseActivity.java` | `res-exam/layout/exam_activity_exam.xml` | `LessonExerciseRepository` |
| Hoi dap bai hoc | `feature/student/course/LessonDiscussionActivity.java` | `res-course/layout/course_activity_lesson_discussion.xml` | `StudentFeatureRepository` |
| Danh sach bai thi | `feature/student/exam/ExamsFragment.java` | Dung danh sach feature dung chung | `ExamRepository.loadExams()` |
| Lam bai thi | `feature/student/exam/ExamActivity.java` | `res-exam/layout/exam_activity_exam.xml` | `ExamRepository` |
| AI Tutor | `feature/student/ai/AITutorActivity.java` | `res-ai/layout/ai_activity_tutor.xml` | `StudentFeatureRepository.chatWithTutor()` |
| Phan tich hoc tap | `feature/student/ai/LearningAnalysisActivity.java` | `res-ai/layout/ai_activity_learning_analysis.xml` | `student/ai/learning-analyzer/` |
| Danh gia dau vao | `feature/student/ai/AssessmentActivity.java` | `res-ai/layout/ai_activity_assessment.xml` | `student/ai/assessment/` |
| Ho so | `feature/student/profile/StudentProfileFragment.java` | `res-profile/layout/profile_fragment_profile.xml` | `AuthRepository` |
| Phu huynh | `feature/student/profile/ParentActivity.java` | `res-profile/layout/profile_activity_parent.xml` | `StudentFeatureRepository` |

## 2.2 Nen hoc Student theo thu tu nao?

### Buoc 1: Dashboard

Doc:

```text
feature/student/dashboard/StudentDashboardFragment.java
data/repository/DashboardRepository.java
data/model/DashboardSummary.java
res-student-home/layout/home_fragment_dashboard.xml
```

Muc tieu hoc:

- Fragment lifecycle: `onCreateView`, `onViewCreated`.
- Khoi tao Repository bang `requireContext()`.
- Goi API khi man hinh duoc mo.
- Hien thi loading, thanh cong va loi.
- Gan du lieu model vao `TextView`, progress va danh sach.

Luong:

```text
StudentDashboardFragment
    -> DashboardRepository.loadDashboard()
    -> GET student/dashboard/
    -> parse DashboardSummary
    -> bind du lieu len giao dien
```

### Buoc 2: Danh sach khoa hoc

Doc:

```text
feature/student/course/CoursesFragment.java
feature/student/course/CourseAdapter.java
data/repository/CourseRepository.java
data/model/Course.java
data/model/CourseListResult.java
res-course/layout/course_fragment_courses.xml
res-course/layout/course_item_course.xml
```

Muc tieu hoc:

- Fragment quan ly danh sach khoa hoc.
- Adapter tao moi item va gan du lieu vao item.
- Repository xu ly phan trang/response va cache.
- Click item de mo `CourseDetailActivity` bang `Intent`.

### Buoc 3: Chi tiet va hoc bai

Doc:

```text
feature/student/course/CourseDetailActivity.java
feature/student/course/LessonPlayerActivity.java
feature/student/course/LessonAdapter.java
data/model/CourseDetail.java
data/model/CourseSection.java
data/model/Lesson.java
data/model/LessonContent.java
```

Muc tieu hoc:

- Truyen `courseId`, `lessonId` qua Intent extra.
- Tai cau truc khoa hoc: course -> section/module -> lesson.
- Kiem tra bai hoc duoc mo khoa.
- Phat video/tai lieu va ghi tien do.
- Goi `markLessonCompleted()` sau khi hoc xong.

Luong tai bai hoc:

```text
LessonPlayerActivity
    -> CourseRepository.loadLesson(courseId, lessonId)
    -> GET student/courses/{courseId}/player/{lessonId}/
    -> LessonContent
    -> hien video/noi dung bai hoc
```

### Buoc 4: Bai tap va bai thi

Doc bai tap:

```text
feature/student/course/LessonExerciseActivity.java
data/repository/LessonExerciseRepository.java
domain/AiQuestionPolicy.java
common/ui/form/QuestionRenderPolicy.java
```

Doc bai thi:

```text
feature/student/exam/ExamsFragment.java
feature/student/exam/ExamActivity.java
feature/student/exam/ExamTiming.java
feature/student/exam/ExamErrorMessages.java
data/repository/ExamRepository.java
```

Luong bai thi:

```text
GET  student/exams/                     -> danh sach
GET  student/exams/{examId}/            -> chi tiet
POST student/exams/{examId}/start/      -> bat dau attempt
POST student/exams/{examId}/submit/...  -> nop bai
GET  student/exams/{examId}/result/...  -> xem ket qua
GET  student/exams/{examId}/ranking/    -> bang xep hang
```

Muc tieu hoc:

- Quan ly trang thai bai lam.
- Dem thoi gian va xu ly het gio.
- Luu cau tra loi theo question ID.
- Tao JSON dap an de nop len server.
- Ngan nop trung va xu ly callback sau khi Activity dong.

### Buoc 5: Hoi dap va AI Student

Doc:

```text
feature/student/course/LessonDiscussionActivity.java
feature/student/course/LessonQuestionAdapter.java
feature/student/ai/AITutorActivity.java
feature/student/ai/LearningAnalysisActivity.java
feature/student/ai/AssessmentActivity.java
data/repository/StudentFeatureRepository.java
```

`StudentFeatureRepository` la file tong hop nhieu API Student:

```text
student/learning-path/
student/notifications/
student/account/parent/
student/ai/tutor/chat/
student/lesson-questions/
student/ai/learning-analyzer/
student/ai/assessment/
student/ai/assessment/result/
```

Khi doc file nay, hay chia theo tung public method, khong can doc mot lan toan bo.

---

# Phan 3 - Role Teacher

Thu muc chinh:

```text
app/src/main/java/com/example/smartkid/feature/teacher/
```

Entry point:

```text
feature/teacher/TeacherDashboardActivity.java
```

Layout:

```text
app/src/main/res-teacher/layout/teacher_activity_dashboard.xml
```

## 3.1 File dau tien nen doc

### `TeacherManagementSpec.java`

```text
feature/teacher/TeacherManagementSpec.java
```

Day la ban do chuc nang Teacher. Moi `FeatureSpec` gom:

- `key`: ma chuc nang.
- `title`: ten hien thi.
- `endpoint`: API tai du lieu.
- `actionKind`: loai hanh dong dac biet.
- `ownerRole`: role duoc phep dung.

Danh sach hien tai:

| Key | Chuc nang | Endpoint |
|---|---|---|
| `teacher_dashboard` | Dashboard | `teacher/dashboard/` |
| `teacher_qa` | Hoi dap bai hoc | `teacher/lesson-questions/` |
| `teacher_courses` | Quan ly khoa hoc | `content/courses/` |
| `teacher_exams` | Quan ly bai kiem tra | `activities/exercises/` |
| `teacher_exam_reports` | Bao cao bai kiem tra | `activities/exercises/` |
| `teacher_students` | Danh sach hoc vien | `teacher/students/` |
| `teacher_progress` | Tien do hoc vien | `teacher/students/` |
| `teacher_feedback` | Phan hoi da gui | `teacher/students/feedback/` |
| `teacher_notifications` | Thong bao | `teacher/notifications/` |

## 3.2 Ban do cac file Teacher

| Chuc nang | File giao dien | Layout/Adapter | Repository |
|---|---|---|---|
| Dashboard | `TeacherDashboardActivity.java` | `teacher_activity_dashboard.xml` | `TeacherDashboardRepository` |
| Danh sach quan ly chung | `TeacherManagementActivity.java` | `common_activity_feature_list.xml`, layout hoi dap hoac bai thi | `ManagementRepository` |
| Hoi dap bai hoc | `TeacherManagementActivity.java` | `TeacherQuestionAdapter.java` | `teacher/lesson-questions/` |
| Xem noi dung khoa hoc | `TeacherCourseContentActivity.java` | `teacher_activity_course_content.xml` | `ManagementRepository` |
| Tao khoa hoc | `course/TeacherCourseCreateActivity.java` | Form tao noi dung dung chung | `ManagementRepository` |
| Tao module | `course/TeacherModuleCreateActivity.java` | Form tao noi dung dung chung | `ManagementRepository` |
| Tao bai hoc | `course/TeacherLessonCreateActivity.java` | Form tao noi dung dung chung | `ManagementRepository` |
| Xay dung khoa hoc | `course/builder/TeacherCourseBuilderActivity.java` | `teacher_activity_course_builder.xml` | `ManagementRepository` |
| Sua bai hoc/upload file | `course/builder/LessonEditorBottomSheet.java` | `teacher_sheet_lesson_editor.xml` | `ManagementRepository.multipartAction()` |
| Tao/sua bai tap | `exercise/TeacherExerciseEditorActivity.java` | Form cau hoi dung chung | `ManagementRepository` |

## 3.3 Dashboard Teacher

Doc theo thu tu:

```text
feature/teacher/TeacherDashboardActivity.java
feature/teacher/data/TeacherDashboardRepository.java
feature/teacher/model/TeacherDashboardData.java
res-teacher/layout/teacher_activity_dashboard.xml
```

Luong:

```text
TeacherDashboardActivity
    -> TeacherDashboardRepository.load()
    -> GET teacher/dashboard/
    -> TeacherDashboardData
    -> hien KPI va cac hanh dong nhanh
```

## 3.4 Man hinh quan ly Teacher dung chung

File:

```text
feature/teacher/TeacherManagementActivity.java
data/repository/ManagementRepository.java
common/ui/FeatureSpec.java
data/model/FeatureItem.java
```

Luong:

```text
TeacherDashboardActivity mo mot feature key
    -> TeacherManagementSpec.get(key)
    -> TeacherManagementActivity nhan FeatureSpec
    -> ManagementRepository.load(spec.getEndpoint())
    -> hien danh sach FeatureItem
```

`TeacherManagementActivity` kha dai. Nen hoc theo tung nhom ham:

1. Tim `onCreate()` de xem Activity nhan `FeatureSpec` nhu the nao.
2. Tim `loadData()` hoac cho goi `repository.load()`.
3. Tim `actionKind` de xem moi loai feature co hanh dong gi.
4. Tim `repository.action()` de xem POST/PATCH/DELETE.
5. Tim cac endpoint bat dau bang `teacher/`, `content/`, `activities/`.

## 3.5 Xay dung khoa hoc Teacher

Doc theo thu tu:

```text
feature/teacher/course/builder/TeacherCourseBuilderActivity.java
feature/teacher/course/builder/BuilderModule.java
feature/teacher/course/builder/BuilderModuleAdapter.java
feature/teacher/course/builder/BuilderLessonAdapter.java
feature/teacher/course/builder/LessonEditorBottomSheet.java
res-teacher/layout/teacher_activity_course_builder.xml
res-teacher/layout/teacher_item_builder_module.xml
res-teacher/layout/teacher_item_builder_lesson.xml
```

API quan trong:

```text
GET    content/courses/{courseId}/modules/
POST   content/courses/{courseId}/modules/
PATCH  content/modules/{moduleId}/
DELETE content/modules/{moduleId}/
GET    content/modules/{moduleId}/lessons/
POST   content/modules/{moduleId}/lessons/
PATCH  content/lessons/{lessonId}/
DELETE content/lessons/{lessonId}/
POST   .../modules/reorder/
POST   .../lessons/reorder/
```

Muc tieu hoc:

- Danh sach long nhau: course -> module -> lesson.
- Adapter long nhau va cap nhat item.
- Tao/sua/xoa bang HTTP method khac nhau.
- Sap xep lai danh sach.
- Upload video/tai lieu bang multipart.

---

# Phan 4 - Role Admin

Thu muc chinh:

```text
app/src/main/java/com/example/smartkid/feature/admin/
```

Entry point:

```text
feature/admin/AdminDashboardActivity.java
```

Layout:

```text
app/src/main/res-admin/layout/admin_activity_dashboard.xml
```

## 4.1 File dau tien nen doc

### `AdminManagementSpec.java`

```text
feature/admin/AdminManagementSpec.java
```

File nay la ban do cac chuc nang Admin:

| Key | Chuc nang | Endpoint |
|---|---|---|
| `admin_dashboard` | Dashboard | `admin/dashboard/` |
| `admin_active_users` | Nguoi dung online | `admin/dashboard/active-users/` |
| `admin_users` | Quan ly tai khoan | `account/admin/users/` |
| `admin_courses` | Quan ly khoa hoc/video | `admin/courses/` |
| `admin_health` | Suc khoe he thong | `admin/system/health/` |
| `admin_activity` | Nhat ky hoat dong | `admin/activity-logs/` |
| `admin_security` | Chinh sach bao mat | `admin/security/policy/` |
| `admin_sessions` | Phien dang nhap | `admin/security/sessions/` |
| `admin_config` | Cau hinh he thong | `admin/system/config/` |
| `admin_backups` | Sao luu | `admin/system/backups/` |
| `admin_report_learning` | Bao cao hoc tap | `admin/reports/learning/` |
| `admin_report_content` | Bao cao noi dung | `admin/reports/content/` |
| `admin_notifications` | Thong bao | `admin/notifications/` |

## 4.2 Ban do cac file Admin

| Chuc nang | File giao dien | Layout | Repository/Helper |
|---|---|---|---|
| Dashboard | `AdminDashboardActivity.java` | `admin_activity_dashboard.xml` | `AdminDashboardRepository` |
| Danh sach quan ly | `AdminManagementActivity.java` | Cac `admin_page_*.xml` | `ManagementRepository` |
| Quan ly nguoi dung | `AdminManagementActivity.java` | `admin_page_users.xml` | `AdminUserActions` |
| Tao nguoi dung | `users/AdminUserCreateActivity.java` | Form tao noi dung dung chung | `ManagementRepository` |
| Khoa hoc va video | `AdminCourseVideosActivity.java` | `admin_activity_course_videos.xml` | `AdminCourseVideoActions` |
| Bao mat | `AdminSettingsActivity.java` | `admin_activity_security_settings.xml` | `AdminSettingsRules` |
| Cau hinh he thong | `AdminSettingsActivity.java` | `admin_activity_system_settings.xml` | `ManagementRepository` |
| Bao cao | `AdminManagementActivity.java` | Danh sach/card dung chung | `AdminManagementSpec` |
| Thong bao | `AdminManagementActivity.java` | `admin_dialog_send_notification.xml` | `admin/notifications/` |

## 4.3 Dashboard Admin

Doc:

```text
feature/admin/AdminDashboardActivity.java
feature/admin/data/AdminDashboardRepository.java
feature/admin/model/AdminDashboardData.java
common/ui/chart/ActivityChartView.java
res-admin/layout/admin_activity_dashboard.xml
```

Luong:

```text
AdminDashboardActivity
    -> AdminDashboardRepository.load()
    -> GET admin/dashboard/
    -> AdminDashboardData
    -> hien KPI, bieu do va hanh dong nhanh
```

Bao cao bieu do hoat dong:

```text
AdminDashboardActivity
    -> AdminDashboardRepository.loadActivityChart(from, to)
    -> GET admin/reports/users/?type=timeseries&from=...&to=...
    -> ActivityChartView
```

## 4.4 Quan ly nguoi dung

Doc:

```text
feature/admin/AdminManagementActivity.java
feature/admin/AdminUserActions.java
feature/admin/users/AdminUserCreateActivity.java
data/repository/ManagementRepository.java
```

`AdminUserActions` tao endpoint cho cac thao tac tren mot user. Vi du:

```text
account/admin/users/{userId}/
account/admin/password/set/{userId}/
```

Trong `AdminManagementActivity`, tim cac cho:

```text
repository.load(...)
repository.action(Request.Method.POST, ...)
repository.action(Request.Method.PATCH, ...)
repository.action(Request.Method.DELETE, ...)
```

Day la cach nhanh nhat de hieu Admin doc, tao, sua va xoa du lieu nhu the nao.

## 4.5 Cai dat va bao mat

Doc:

```text
feature/admin/AdminSettingsActivity.java
feature/admin/AdminSettingsRules.java
res-admin/layout/admin_activity_security_settings.xml
res-admin/layout/admin_activity_system_settings.xml
```

Endpoint chinh:

```text
admin/security/policy/
admin/system/config/
admin/system/test-email/
```

Muc tieu hoc:

- Tai cau hinh hien tai bang GET.
- Kiem tra du lieu form bang `AdminSettingsRules`.
- Tao JSON payload.
- Luu cau hinh bang PATCH.
- Chay hanh dong rieng bang POST.

## 4.6 Quan ly khoa hoc va video

Doc:

```text
feature/admin/AdminCourseVideosActivity.java
feature/admin/AdminCourseVideoActions.java
res-admin/layout/admin_activity_course_videos.xml
res-admin/layout/admin_item_course_video.xml
```

`AdminCourseVideoActions` chiu trach nhiem tao endpoint theo `courseId`, `moduleId`
va `lessonId`. Day la vi du tot de hoc cach tach logic tao endpoint ra khoi Activity.

---

# Bang tra cuu Repository

| Repository | Role su dung | Nhiem vu |
|---|---|---|
| `AuthRepository` | Tat ca | Dang nhap, dang ky, ho so, dang xuat |
| `DashboardRepository` | Student | Dashboard hoc vien |
| `CourseRepository` | Student | Khoa hoc, bai hoc, ghi danh, tien do |
| `ExamRepository` | Student | Danh sach, bat dau, nop bai, ket qua |
| `LessonExerciseRepository` | Student | Attempt bai tap va cau tra loi |
| `StudentFeatureRepository` | Student | AI, thong bao, hoi dap, phu huynh, lo trinh |
| `TeacherDashboardRepository` | Teacher | Dashboard giao vien |
| `AdminDashboardRepository` | Admin | Dashboard va bieu do Admin |
| `ManagementRepository` | Teacher/Admin | Danh sach quan ly va CRUD tong quat |
| `NotificationBadgeRepository` | Tat ca | Dem thong bao chua doc |

# Cach lan theo mot chuc nang bat ky

Vi du muon tim chuc nang Teacher tra loi cau hoi cua hoc vien:

1. Tim endpoint:

```bash
rg -n "teacher/lesson-questions" app/src/main/java
```

2. Ket qua se dan den:

```text
TeacherManagementSpec.java
TeacherManagementActivity.java
```

3. Trong Activity, tim noi goi Repository:

```bash
rg -n "repository\." app/src/main/java/com/example/smartkid/feature/teacher/TeacherManagementActivity.java
```

4. Mo `ManagementRepository` de xem request tiep tuc di dau.

5. Mo `ApiClient.execute()` de xem URL, header va callback.

Nhung lenh tim kiem huu ich:

```bash
# Tim noi su dung mot Activity hoac Repository
rg -n "CourseRepository" app/src/main/java

# Tim tat ca endpoint cua Student
rg -n '"student/' app/src/main/java

# Tim tat ca endpoint cua Teacher
rg -n '"teacher/' app/src/main/java

# Tim tat ca endpoint cua Admin
rg -n '"admin/' app/src/main/java

# Tim layout ma mot Activity dang dung
rg -n "setContentView" app/src/main/java/com/example/smartkid/feature

# Tim ID giao dien trong Java va XML
rg -n "buttonSubmit" app/src/main
```

# Lo trinh hoc de xuat

## Giai doan 1: Nen tang

- [ ] Doc `SmartKidApplication` va `MainActivity`.
- [ ] Hieu `SessionManager`, `UserRole`, `RoleNavigation`.
- [ ] Lan theo luong dang nhap tu `LoginActivity` den `AuthRepository`.
- [ ] Hieu `ApiCallback`, `ApiError` va `ApiClient.execute()`.

## Giai doan 2: Student

- [ ] Doc dashboard Student.
- [ ] Doc danh sach khoa hoc va Adapter.
- [ ] Doc chi tiet khoa hoc va Lesson Player.
- [ ] Doc luong bai tap/bai thi.
- [ ] Doc hoi dap va cac tinh nang Student trong `StudentFeatureRepository`.

## Giai doan 3: Teacher

- [ ] Doc `TeacherManagementSpec` de biet toan bo chuc nang.
- [ ] Doc dashboard va `TeacherDashboardRepository`.
- [ ] Lan theo mot feature qua `TeacherManagementActivity`.
- [ ] Doc Course Builder va `LessonEditorBottomSheet`.
- [ ] Hieu CRUD va multipart trong `ManagementRepository`.

## Giai doan 4: Admin

- [ ] Doc `AdminManagementSpec`.
- [ ] Doc dashboard va bieu do.
- [ ] Lan theo quan ly user.
- [ ] Lan theo cau hinh/bao mat.
- [ ] Lan theo quan ly khoa hoc/video.

# Bai tap tu hoc

1. Ve lai luong dang nhap tu nut bam den API va den man hinh theo role.
2. Tim endpoint tai dashboard cua ca Student, Teacher va Admin.
3. Chon mot chuc nang Student va ghi lai Activity, layout, Repository, model.
4. Chon mot chuc nang Teacher va tim tat ca HTTP method no su dung.
5. Chon mot chuc nang Admin va tim noi JSON request body duoc tao.
6. Dat breakpoint trong `ApiClient.execute()` va quan sat URL, body, header.
7. Dat breakpoint trong `onSuccess()` de xem JSON server tra ve.
8. Tim mot noi dung `FeatureItem` va theo doi cach no duoc hien thi qua Adapter.

# Ghi chu quan trong khi doc code

- Backend Django khong nam trong workspace Android nay; Android chi la phia goi API.
- Endpoint trong code la duong dan tuong doi. `ApiClient.buildUrl()` ghep endpoint
  voi base URL co san `/api/`.
- `authenticated = true` co nghia request can JWT.
- `SafeJson` duoc dung de tranh crash khi server thieu field hoac doi ten field.
- `ManagementRepository` la Repository tong quat, nen endpoint cua Teacher/Admin
  co the nam trong Activity hoac cac file `*ManagementSpec`, khong chi nam trong
  Repository.
- Khi muon sua giao dien, luon tim ca file Java va layout XML tuong ung.
- Khi muon sua du lieu/API, tim Repository va endpoint truoc khi sua Activity.
- Khong can hoc thu muc `tools/ai-project-assistant` de hieu ung dung Android.
