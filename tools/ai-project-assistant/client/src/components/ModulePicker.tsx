import { ArrowRight, Database, GraduationCap, ShieldCheck, Sparkles, UserRoundCheck } from "lucide-react";
import type { ModuleId, ModuleSummary, ProjectInfo } from "@smartkid/shared";

const icons = {
  student: GraduationCap,
  teacher: UserRoundCheck,
  admin: ShieldCheck,
  backend: Database,
};

export function ModulePicker({
  modules,
  info,
  loading,
  onSelect,
}: {
  modules: ModuleSummary[];
  info?: ProjectInfo;
  loading: boolean;
  onSelect: (module: ModuleId) => void;
}) {
  return (
    <main className="module-picker">
      <div className="module-ambient ambient-one" />
      <div className="module-ambient ambient-two" />
      <section className="module-picker__content">
        <div className="brand-mark">
          <Sparkles size={20} />
          SMARTKID AI
        </div>
        <p className="eyebrow">PROJECT ASSISTANT</p>
        <h1>Bạn muốn làm việc với phần nào của dự án?</h1>
        <p className="module-picker__lead">
          Chọn một module để AI ưu tiên đúng ngữ cảnh. Bạn có thể đổi module bất cứ lúc nào.
        </p>

        {loading ? (
          <div className="module-grid">
            {[0, 1, 2, 3].map((item) => (
              <div className="module-card skeleton-card" key={item}>
                <div className="skeleton skeleton-icon" />
                <div className="skeleton skeleton-title" />
                <div className="skeleton skeleton-line" />
                <div className="skeleton skeleton-button" />
              </div>
            ))}
          </div>
        ) : (
          <div className="module-grid">
            {modules.map((module) => {
              const Icon = icons[module.id];
              return (
                <article className={`module-card module-${module.id}`} key={module.id}>
                  <div className="module-card__top">
                    <span className="module-card__icon">
                      <Icon size={25} />
                    </span>
                    <span className="file-count">{module.fileCount.toLocaleString("vi-VN")} file</span>
                  </div>
                  <h2>{module.name}</h2>
                  <p>{module.description}</p>
                  {module.samplePaths.length > 0 && (
                    <span className="module-card__sample" title={module.samplePaths[0]}>
                      {module.samplePaths[0]}
                    </span>
                  )}
                  <button className="module-start" onClick={() => onSelect(module.id)}>
                    Bắt đầu <ArrowRight size={17} />
                  </button>
                </article>
              );
            })}
          </div>
        )}

        <div className="project-detection">
          <span className={`status-dot ${info?.android.detected ? "online" : "warning"}`} />
          {info?.android.detected ? (
            <>
              Đã nhận diện Android · {info.android.languages.join(" + ") || "XML/Gradle"} ·{" "}
              {info.indexedFiles} file được lập chỉ mục
            </>
          ) : (
            <>Chưa phát hiện source Android · bộ quét sẽ tự cập nhật khi source xuất hiện</>
          )}
        </div>
      </section>
    </main>
  );
}
