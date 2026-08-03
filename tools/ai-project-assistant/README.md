# SMARTKID AI PROJECT ASSISTANT

SMARTKID AI Project Assistant là công cụ local để đọc, tìm kiếm, phân tích và chỉnh sửa dự án
Android một cách có kiểm soát. Công cụ nằm hoàn toàn trong
`tools/ai-project-assistant`, không di chuyển source Android và không tự chạy lệnh shell trong dự
án.

Giao diện gồm:

- màn hình chọn đủ bốn phạm vi **STUDENT**, **TEACHER**, **ADMIN**, **BACKEND**;
- cây thư mục thật, tìm file, lọc loại file, favorite và trạng thái file vừa thay đổi;
- Monaco Editor hỗ trợ Kotlin, Java, XML, JavaScript, TypeScript, JSON, Gradle và Markdown;
- **AI Vấn đáp** ngay trong cột phải: thầy chủ động hỏi từng câu, nghe câu trả lời và hỏi tiếp
  theo mức hiểu của học sinh;
- AI chat với Ask, Plan, Patch, Agent, context theo selection/file/module/project;
- XML Inspector cho view, ID, resource, resource bị thiếu và class inflate layout;
- diff bắt buộc trước mọi thay đổi, backup, lịch sử và Undo có kiểm tra xung đột.

## Workspace Smartkid

Công cụ được đặt trong repository Android `Smartkid`. Cấu hình local hiện tại có thể giới hạn
workspace ở đúng hai repository ngang hàng:

- `Smartkid`: ứng dụng Android;
- `BeSmartkid`: Django API, được ưu tiên nhận diện là module **BACKEND**.

Hai thư mục được hiển thị thành hai node gốc trong Explorer. Những thư mục ngang hàng khác không thể
được đọc hoặc chỉnh sửa qua API.

Source của chính assistant được tự động loại khỏi project index để không làm nhiễu kết quả AI và số
lượng file theo module.

## Kiến trúc

```text
tools/ai-project-assistant/
├── client/                 React + TypeScript + Vite + Monaco
├── server/                 Express + scanner/index + AI + patch engine
├── shared/                 Type và contract API dùng chung
├── .smartkid-data/         Settings/chat/oral session/patch/backup local (tự tạo, git-ignore)
├── .env.example
├── package.json
└── README.md
```

Luồng dữ liệu chính:

```text
Project root
    ↓ scan + .gitignore + secret/binary filter
In-memory index ──→ search/reference/XML/module detector
    ↓
Context builder ──→ OpenAI-compatible API
    ↓
Pending proposal ──→ Diff ──→ Người dùng xác nhận
                              ↓
                         Backup + Apply ──→ Undo
```

Backend chỉ bind vào `127.0.0.1`. Frontend development chạy ở `127.0.0.1:4311` và proxy `/api`
tới backend. Bản production được backend serve trực tiếp ở `127.0.0.1:4310`.

## Điều kiện

- Node.js 20.19 trở lên.
- npm 9 trở lên.
- Một dự án local mà tài khoản hiện tại có quyền đọc.
- API tương thích OpenAI nếu muốn dùng AI. Đọc/search/diff/patch/undo vẫn hoạt động khi chưa cấu
  hình AI.

Không cần cài database. Lịch sử dùng JSON và snapshot file trong `.smartkid-data`.

## Cài đặt

Từ thư mục gốc repository:

```bash
npm run assistant:install
```

Lệnh này chỉ cài package trong `tools/ai-project-assistant`.

## Cấu hình `.env`

Sao chép file mẫu:

```bash
cp tools/ai-project-assistant/.env.example tools/ai-project-assistant/.env
```

Ví dụ:

```dotenv
AI_API_KEY=your_api_key_here
AI_BASE_URL=https://api.openai.com/v1
AI_MODEL=your-model-name
PROJECT_ROOT=
PROJECT_INCLUDE=
PROJECT_NAME=
PORT=4310
```

| Biến | Ý nghĩa |
| --- | --- |
| `AI_API_KEY` | Khóa API, chỉ được đọc ở server. Không xuất hiện trong API settings/UI/log. |
| `AI_BASE_URL` | Base URL tương thích endpoint `POST /chat/completions`. |
| `AI_MODEL` | Tên model do nhà cung cấp AI hỗ trợ. |
| `PROJECT_ROOT` | Project root tuyệt đối, hoặc tương đối từ thư mục assistant. Bỏ trống để dùng root repository hiện tại. |
| `PROJECT_INCLUDE` | Danh sách thư mục con được phép quét, phân cách bằng dấu phẩy. Mọi đường dẫn khác đều bị API chặn. |
| `PROJECT_NAME` | Tên workspace hiển thị trên giao diện. |
| `PORT` | Cổng backend/production UI, mặc định `4310`. |

Không commit `.env`. `.gitignore` đã chặn `.env`, `.env.*` (trừ `.env.example`) và dữ liệu local.
API key không thể cấu hình trong trình duyệt nhằm tránh lưu khóa vào local storage hoặc chat
history.

## Chạy development

Từ root:

```bash
npm run assistant:dev
```

Mở:

```text
http://127.0.0.1:4311
```

Vite và Express chạy song song. Server tự quét lần đầu và theo dõi thay đổi bằng filesystem
watcher.

## Build và chạy production

```bash
npm run assistant:build
npm run assistant:start
```

Mở:

```text
http://127.0.0.1:4310
```

## Chọn thư mục dự án

Mặc định project root là hai cấp phía trên assistant, tức root repository chứa thư mục `tools`.
Để phân tích dự án khác, đặt `PROJECT_ROOT` trong `.env`:

```dotenv
PROJECT_ROOT=/duong/dan/tuyet/doi/toi/android-project
```

Khởi động lại server sau khi đổi `PROJECT_ROOT`. Server chuẩn hóa bằng `realpath`, từ chối project
root không tồn tại và không cho bất kỳ request nào thoát khỏi root này.

Nếu Android và backend là hai repository ngang hàng, đặt root tại thư mục cha và giới hạn chính xác
hai thư mục được phép:

```dotenv
PROJECT_ROOT=/home/user/projects
PROJECT_INCLUDE=Smartkid,BeSmartkid
PROJECT_NAME=Smartkid
```

Khi `PROJECT_INCLUDE` có giá trị, cả scanner lẫn mọi API file chỉ được truy cập các thư mục đã liệt
kê. Không nên đặt `PROJECT_ROOT` ở thư mục cha mà bỏ trống `PROJECT_INCLUDE`.

## Nhận diện module

Mỗi file được chấm điểm dựa trên:

- toàn bộ đường dẫn, tên folder/resource và tên file;
- package và nội dung khai báo class;
- Activity, Fragment, ViewModel, Service;
- API, controller, repository, database, Retrofit, Firebase, Supabase;
- các keyword và exclude do người dùng cấu hình.

Mapping mặc định chỉ là điểm khởi đầu, không phải danh sách đường dẫn hardcode. Vào
**Settings → Module mapping** để thêm/bớt keyword hoặc exclude cho từng module. Mỗi từ khóa đặt
trên một dòng. Lưu settings sẽ quét và chấm điểm lại toàn bộ index.

Chọn module không khóa cây thư mục. Nó làm AI ưu tiên context của module; người dùng vẫn có thể mở
mọi file hoặc chọn **Toàn dự án**.

## Sử dụng editor và tìm kiếm

- Nhấp file để mở trong tab Monaco.
- Bấm **Chỉnh sửa** để chuyển khỏi read-only ở editor.
- **Lưu** không ghi file ngay. Nó tạo một proposal Pending và mở diff.
- `Ctrl/Cmd + P`: focus tìm file theo tên.
- `Ctrl/Cmd + Shift + F`: tìm toàn dự án.
- `Ctrl/Cmd + S`: tạo diff cho nội dung editor.
- `Ctrl/Cmd + Enter`: gửi câu hỏi khi focus ở ô chat.
- `Ctrl/Cmd + Z`: undo cục bộ trong Monaco trước khi tạo patch.
- Monaco hỗ trợ `Ctrl/Cmd + F` tìm trong file và `Ctrl/Cmd + G` đi tới dòng.

Tìm kiếm toàn dự án hỗ trợ:

- tên file và đường dẫn;
- text không phân biệt hoa/thường;
- exact và regex;
- class, function;
- Android ID (`@+id/name`, `@id/name`, `R.id.name`).

API reference còn nhận diện resource Android (`string`, `style`, `layout`, `drawable`, `color`),
Manifest declaration, call và Gradle dependency.

## XML Android Inspector

Khi mở XML, server phân tích:

- tag/view và dòng bắt đầu;
- `android:id`;
- `@string`, `@style`, `@color`, `@drawable`, `@layout` và số reference;
- resource chưa tìm thấy khai báo;
- `R.layout.*`, ViewBinding/DataBinding class và vị trí inflate/setContentView.

Nhấp file trong mục “Được inflate bởi” để mở đúng file, đúng dòng. Đây là phân tích text/reference,
không thay thế Android compiler hoặc Lint.

## AI và context

Context không bao giờ là toàn bộ dự án gửi nguyên khối. Thứ tự ưu tiên:

1. đoạn code đang chọn;
2. file đang mở;
3. file kéo/đính kèm;
4. reference trực tiếp;
5. kết quả tìm kiếm theo câu hỏi;
6. file trong module đang chọn.

Mỗi đoạn có nhãn đường dẫn và khoảng dòng. Server giới hạn số file/ký tự, loại binary, file quá lớn,
file nhạy cảm và redact literal secret trước khi gọi AI.

AI được yêu cầu chỉ dùng đường dẫn có thật trong context. Đường dẫn giống file nhưng không tồn tại
trong index bị thay bằng `[đường dẫn chưa được xác minh]`. Citation phía dưới câu trả lời luôn được
tạo từ index thật và có thể nhấp để mở đúng dòng.

Nếu không có kết quả trong phạm vi hiện tại, công cụ trả:

> Chưa tìm thấy file phù hợp trong phạm vi đã quét.

Sau đó đề nghị chuyển sang toàn dự án.

## AI Vấn đáp trong cột phải

Ở đầu panel **SMARTKID AI**, chọn **AI VẤN ĐÁP**. Explorer, các tab Monaco và bản nháp editor
vẫn được giữ nguyên; chuyển qua **TRỢ LÝ CODE** rồi quay lại không làm mất phiên học.

Luồng sử dụng:

1. nhập tên, cấp học, môn, chủ đề, mục tiêu và số câu;
2. bấm **Bắt đầu vấn đáp** để thầy AI chủ động hỏi câu đầu;
3. trả lời bằng bàn phím hoặc micro tiếng Việt;
4. xem nhận xét về độ đúng, lập luận và cách diễn đạt;
5. thầy tự điều chỉnh câu tiếp theo qua các pha khởi động, chẩn đoán, đào sâu, thử thách và tự
   tổng kết;
6. nếu chưa biết, bấm **Em chưa biết, thầy gợi ý** — lần đầu thầy gợi một nấc, lần tiếp theo thầy
   làm mẫu đúng bước đầu;
7. hoàn thành phiên để xem điểm, điểm mạnh, nội dung cần luyện và transcript.

Giọng đọc sử dụng Speech Synthesis có sẵn trên trình duyệt/hệ điều hành; nhận dạng giọng nói dùng
Web Speech Recognition `vi-VN`. Nếu trình duyệt không hỗ trợ micro, ô nhập chữ vẫn hoạt động đầy
đủ. Đây là nhân vật gia sư AI nguyên bản lấy cảm hứng từ phương pháp hỏi gợi mở, không giả danh
hoặc sao chép giọng của một giáo viên có thật.

Khi thiếu `AI_API_KEY` hoặc `AI_MODEL`, badge **Demo** xuất hiện và toàn bộ lifecycle vẫn chạy bằng
bộ câu hỏi quy tắc local. Khi cấu hình đủ, badge chuyển thành **AI thật** và server gọi API tương
thích OpenAI. API key không bao giờ được gửi xuống frontend.

Lịch sử vấn đáp được ghi atomically vào `.smartkid-data/oral-sessions.json` với quyền file `0600`.

## Ask, Plan, Patch và Agent

### Ask

Chỉ giải thích code. AI được yêu cầu trả file, đường dẫn từ root, dòng, luồng gọi, rủi ro và đề xuất.
Không tạo thay đổi.

### Plan

Lập kế hoạch và liệt kê file có liên quan, chưa tạo hoặc áp dụng patch.

### Patch

AI trả JSON thay đổi có cấu trúc. Server:

1. xác minh mọi đường dẫn update tồn tại trong index;
2. chặn path nhạy cảm, binary, file lớn và secret;
3. dựng unified diff;
4. lưu proposal ở trạng thái **Pending**.

Không file nào được ghi trước nút **Xác nhận áp dụng**.

### Agent

Agent đọc context, lập kế hoạch và tạo patch nhiều file. Các giới hạn vẫn giống Patch:

- chỉ file trong proposal;
- không tự xóa/rename/move;
- không chạy shell;
- không tự apply;
- luôn chờ người dùng xem diff và xác nhận.

## Diff, apply và Undo

Mọi endpoint create/update/delete/rename/move/folder đều tạo proposal. Dialog diff hiển thị:

- hành động và danh sách file bị ảnh hưởng;
- unified diff hoặc đường dẫn nguồn/đích;
- kế hoạch Agent nếu có;
- trạng thái Pending/Applied/Undone/Rejected.

Khi xác nhận:

1. server kiểm tra lại version và destination;
2. snapshot mọi đường dẫn trước thay đổi;
3. ghi atomically hoặc thực hiện thao tác;
4. snapshot trạng thái sau;
5. lưu lịch sử.

Undo so sánh trạng thái hiện tại với snapshot “sau”. Nếu người dùng hoặc công cụ khác đã sửa file,
Undo trả `UNDO_CONFLICT` thay vì ghi đè. Snapshot nằm trong `.smartkid-data/backups`.

Chế độ read-only ở Settings chặn cả Apply và Undo.

## API chính

| Method | Endpoint | Chức năng |
| --- | --- | --- |
| GET | `/api/project/info` | Project root, Android detection, scan stats |
| GET | `/api/project/tree` | Cây file đã lọc an toàn |
| POST | `/api/project/scan` | Quét/index lại |
| GET | `/api/files/read?path=` | Đọc file text |
| POST | `/api/files/search` | File/content/exact/regex/class/function/ID |
| POST | `/api/files/references` | Tìm definition/usage/call/dependency |
| POST | `/api/files/create` | Tạo proposal create |
| POST | `/api/files/update` | Tạo proposal update |
| POST | `/api/files/delete` | Tạo proposal delete |
| POST | `/api/files/rename` | Tạo proposal rename |
| POST | `/api/files/move` | Tạo proposal move |
| POST | `/api/folders/create` | Tạo proposal folder |
| POST | `/api/folders/rename` | Tạo proposal rename folder |
| POST | `/api/ai/chat` | Ask/Plan/Patch/Agent |
| POST | `/api/ai/explain` | Ask shortcut |
| POST | `/api/ai/create-patch` | Patch shortcut |
| GET | `/api/oral/config` | Trạng thái AI thật/demo, không chứa API key |
| GET/POST | `/api/oral/sessions` | Lịch sử hoặc tạo phiên vấn đáp |
| GET/DELETE | `/api/oral/sessions/:id` | Đọc hoặc xóa một phiên |
| POST | `/api/oral/sessions/:id/answers` | Nộp câu trả lời và nhận câu hỏi tiếp |
| POST | `/api/oral/sessions/:id/end` | Kết thúc sớm và tạo tổng kết |
| POST | `/api/patch/preview` | Đọc proposal/diff |
| POST | `/api/patch/apply` | Xác nhận và apply |
| POST | `/api/patch/reject` | Từ chối Pending |
| POST | `/api/patch/undo` | Undo có conflict check |
| GET | `/api/history` | Chat và edit history |
| GET | `/api/modules` | Module count và mapping |
| PUT | `/api/modules/mapping` | Cập nhật mapping và scan lại |
| GET/PUT | `/api/settings` | Cấu hình không chứa API key |

Lỗi luôn có dạng:

```json
{
  "success": false,
  "error": {
    "code": "FILE_NOT_FOUND",
    "message": "Không tìm thấy file",
    "details": {}
  }
}
```

## Giới hạn bảo mật

Luôn bị bỏ qua/chặn:

- `.git`, `.gradle`, `build`, `dist`, `node_modules`, cache, generated thông thường;
- APK, AAB, archive và binary;
- ảnh/font/media khỏi index text;
- `local.properties`, `google-services.json`, `.env`, `.npmrc`;
- `.jks`, `.keystore`, private key/certificate và tên file credential/secret;
- file có literal API key/token/password/private key đáng ngờ;
- file vượt giới hạn cấu hình;
- symlink thoát root, đường dẫn tuyệt đối, `..`, NUL và path traversal.

`.env.example` được phép vì chỉ chứa placeholder. `gradle.properties` có thể được lập chỉ mục để tìm
dependency nhưng sẽ bị chặn nếu chứa literal secret.

Công cụ không tự chạy Gradle, Git hoặc bất kỳ shell command nào. “Build backend/frontend” trong tài
liệu này chỉ build chính assistant.

## Test và kiểm tra chất lượng

Chạy toàn bộ test:

```bash
npm run assistant:test
```

Chạy typecheck, lint, test và production build:

```bash
npm run assistant:check
```

Test hiện bao phủ:

- path traversal, absolute path và symlink escape;
- file nhạy cảm/literal secret;
- đọc file;
- text, class và Android ID search;
- XML view/resource/inflater;
- module mapping tùy chỉnh;
- tạo diff Pending, apply, backup, undo;
- version conflict;
- API validation và error contract;
- AI chưa cấu hình không làm lộ key.
- lifecycle vấn đáp, đúng một câu hỏi đang chờ, gợi ý khi chưa biết, tổng kết và lịch sử local.

## Xử lý lỗi thường gặp

### `AI_NOT_CONFIGURED`

Điền `AI_API_KEY`, `AI_BASE_URL`, `AI_MODEL` vào `.env` và khởi động lại server.
Riêng **AI Vấn đáp** vẫn có thể dùng ở chế độ Demo khi chưa cấu hình.

### `AI_API_ERROR` hoặc `AI_CONNECTION_FAILED`

Kiểm tra Base URL có hỗ trợ OpenAI chat completions, tên model, kết nối mạng và quyền của API key.
Chi tiết từ provider được trả trong `error.details` nhưng Authorization header không được log.

### `FILE_TOO_LARGE`

Tăng **Settings → Kích thước file tối đa** (tối đa 10 MB) nếu file chắc chắn là text và an toàn.

### `SENSITIVE_FILE` hoặc `SECRET_DETECTED`

File bị chặn do tên/extension hoặc literal bí mật. Không tắt cơ chế này qua UI. Di chuyển secret ra
khỏi source, dùng biến môi trường và quét lại.

### `FILE_VERSION_CONFLICT`

File đã đổi sau khi mở hoặc sau khi tạo diff. Refresh, mở lại file và tạo proposal mới.

### `UNDO_CONFLICT`

File đã đổi sau Apply. Kiểm tra thay đổi hiện tại; công cụ cố ý không ghi đè. Có thể tạo patch thủ
công mới sau khi đối chiếu.

### Không thấy file

Kiểm tra `.gitignore`, giới hạn kích thước, định dạng binary/secret và `PROJECT_ROOT`; sau đó nhấn
Refresh. Source của assistant cố ý không xuất hiện trong tree/index.

### Không dùng được micro hoặc không có giọng tiếng Việt

Ưu tiên Chrome/Edge, cho phép quyền micro tại `127.0.0.1` và kiểm tra input device của hệ điều
hành. Chất lượng giọng đọc phụ thuộc voice đã cài trên máy. Có thể nhập câu trả lời bằng bàn phím
và tắt tự động đọc trong phần **Giọng**.

### Cổng đã được sử dụng

Đổi `PORT` trong `.env`. Khi development, nếu đổi backend port thì cập nhật proxy trong
`client/vite.config.ts`.

## Các lệnh cần dùng

Từ đúng thư mục root repository:

```bash
npm run assistant:install
npm run assistant:dev
npm run assistant:test
npm run assistant:build
npm run assistant:start
npm run assistant:check
```
