import { Save, Settings2, Shield, SlidersHorizontal, Sparkles, Tags, X } from "lucide-react";
import { useState } from "react";
import { MODULE_IDS, type AssistantSettings, type ModuleId } from "@smartkid/shared";

export function SettingsDialog({
  settings,
  saving,
  onClose,
  onSave,
}: {
  settings: AssistantSettings;
  saving: boolean;
  onClose: () => void;
  onSave: (settings: AssistantSettings) => void;
}) {
  const [draft, setDraft] = useState(settings);
  const [section, setSection] = useState<"ai" | "context" | "modules" | "safety">("ai");
  return (
    <div className="modal-backdrop">
      <section className="modal settings-dialog" role="dialog" aria-modal="true">
        <header className="modal-header">
          <div><span className="modal-icon"><Settings2 size={19} /></span><div><h2>Settings</h2><p>Cấu hình local, không lưu API key vào trình duyệt</p></div></div>
          <button onClick={onClose}><X size={19} /></button>
        </header>
        <div className="settings-body">
          <nav className="settings-nav">
            <button className={section === "ai" ? "is-active" : ""} onClick={() => setSection("ai")}><Sparkles size={15} /> AI</button>
            <button className={section === "context" ? "is-active" : ""} onClick={() => setSection("context")}><SlidersHorizontal size={15} /> Context</button>
            <button className={section === "modules" ? "is-active" : ""} onClick={() => setSection("modules")}><Tags size={15} /> Module mapping</button>
            <button className={section === "safety" ? "is-active" : ""} onClick={() => setSection("safety")}><Shield size={15} /> An toàn</button>
          </nav>
          <div className="settings-content">
            {section === "ai" && (
              <section>
                <h3>Kết nối AI tương thích OpenAI</h3>
                <p className="setting-help">API key chỉ đọc từ <code>.env</code> phía server và không bao giờ gửi xuống UI.</p>
                <Field label="Model">
                  <input value={draft.aiModel} onChange={(event) => setDraft({ ...draft, aiModel: event.target.value })} placeholder="Tên model" />
                </Field>
                <Field label="Base URL">
                  <input value={draft.aiBaseUrl} onChange={(event) => setDraft({ ...draft, aiBaseUrl: event.target.value })} placeholder="https://api.openai.com/v1" />
                </Field>
                <Field label={`Nhiệt độ: ${draft.temperature.toFixed(1)}`}>
                  <input type="range" min="0" max="2" step="0.1" value={draft.temperature} onChange={(event) => setDraft({ ...draft, temperature: Number(event.target.value) })} />
                </Field>
                <Field label="Ngôn ngữ trả lời">
                  <input value={draft.responseLanguage} onChange={(event) => setDraft({ ...draft, responseLanguage: event.target.value })} />
                </Field>
              </section>
            )}
            {section === "context" && (
              <section>
                <h3>Giới hạn context và file</h3>
                <Field label="Số file context tối đa">
                  <input type="number" min="1" max="50" value={draft.maxContextFiles} onChange={(event) => setDraft({ ...draft, maxContextFiles: Number(event.target.value) })} />
                </Field>
                <Field label="Kích thước file tối đa (KB)">
                  <input type="number" min="10" max="10000" value={Math.round(draft.maxFileSize / 1000)} onChange={(event) => setDraft({ ...draft, maxFileSize: Number(event.target.value) * 1000 })} />
                </Field>
                <Field label="Module mặc định">
                  <select value={draft.defaultModule} onChange={(event) => setDraft({ ...draft, defaultModule: event.target.value as ModuleId })}>
                    {MODULE_IDS.map((module) => <option key={module} value={module}>{module.toUpperCase()}</option>)}
                  </select>
                </Field>
              </section>
            )}
            {section === "modules" && (
              <section>
                <h3>Mapping module tự động</h3>
                <p className="setting-help">Mỗi từ khóa một dòng. Bộ quét chấm điểm cả đường dẫn và nội dung file.</p>
                {MODULE_IDS.map((module) => (
                  <div className={`mapping-card mapping-card--${module}`} key={module}>
                    <h4>{module.toUpperCase()}</h4>
                    <label>Từ khóa<textarea value={draft.moduleMapping[module].keywords.join("\n")} onChange={(event) => setDraft({ ...draft, moduleMapping: { ...draft.moduleMapping, [module]: { ...draft.moduleMapping[module], keywords: lines(event.target.value) } } })} /></label>
                    <label>Loại trừ<textarea value={draft.moduleMapping[module].excludes.join("\n")} onChange={(event) => setDraft({ ...draft, moduleMapping: { ...draft.moduleMapping, [module]: { ...draft.moduleMapping[module], excludes: lines(event.target.value) } } })} /></label>
                  </div>
                ))}
              </section>
            )}
            {section === "safety" && (
              <section>
                <h3>Chế độ an toàn</h3>
                <label className="toggle-row">
                  <span><strong>Chỉ đọc toàn bộ dự án</strong><small>Vẫn có thể hỏi AI và xem code, nhưng Apply/Undo bị khóa.</small></span>
                  <input type="checkbox" checked={draft.readOnly} onChange={(event) => setDraft({ ...draft, readOnly: event.target.checked })} />
                </label>
                <div className="security-list">
                  <p>✓ Chặn path traversal và symlink escape</p>
                  <p>✓ Chặn local.properties, keystore và file secret</p>
                  <p>✓ Mọi thay đổi phải qua diff và xác nhận</p>
                  <p>✓ Snapshot phục vụ Undo có kiểm tra xung đột</p>
                </div>
              </section>
            )}
          </div>
        </div>
        <footer className="modal-footer">
          <span />
          <div>
            <button className="button-secondary" onClick={onClose}>Hủy</button>
            <button className="button-primary" disabled={saving} onClick={() => onSave(draft)}><Save size={15} /> {saving ? "Đang lưu…" : "Lưu cài đặt"}</button>
          </div>
        </footer>
      </section>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <label className="setting-field"><span>{label}</span>{children}</label>;
}

function lines(value: string) {
  return value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean);
}
