import Editor from "@monaco-editor/react";
import {
  AlertTriangle,
  Check,
  CheckCircle2,
  Clock3,
  FileDiff,
  RotateCcw,
  ShieldCheck,
  X,
  XCircle,
} from "lucide-react";
import { useState } from "react";
import type { PatchProposal } from "@smartkid/shared";

export function PatchDialog({
  patch,
  busy,
  onClose,
  onApply,
  onReject,
  onUndo,
}: {
  patch: PatchProposal;
  busy: boolean;
  onClose: () => void;
  onApply: () => void;
  onReject: () => void;
  onUndo: () => void;
}) {
  const [activePath, setActivePath] = useState(patch.files[0]?.path);
  const [undoConfirm, setUndoConfirm] = useState(false);
  const active = patch.files.find((file) => file.path === activePath) ?? patch.files[0];

  return (
    <div className="modal-backdrop" role="presentation">
      <section className="modal patch-dialog" role="dialog" aria-modal="true" aria-label="Xem diff">
        <header className="modal-header">
          <div>
            <span className="modal-icon"><FileDiff size={20} /></span>
            <div>
              <h2>{patch.title}</h2>
              <p>
                <PatchStatus status={patch.status} /> · {patch.files.length} file bị ảnh hưởng
              </p>
            </div>
          </div>
          <button onClick={onClose} aria-label="Đóng"><X size={19} /></button>
        </header>
        <div className="patch-summary">
          <ShieldCheck size={18} />
          <div>
            <strong>{patch.status === "pending" ? "Chưa có file nào được ghi" : "Lịch sử thay đổi đã được lưu"}</strong>
            <p>{patch.summary || "Hãy kiểm tra toàn bộ diff trước khi xác nhận."}</p>
          </div>
        </div>
        {patch.plan && patch.plan.length > 0 && (
          <div className="patch-plan">
            <strong>Kế hoạch Agent</strong>
            <ol>{patch.plan.map((step) => <li key={step}>{step}</li>)}</ol>
          </div>
        )}
        <div className="patch-content">
          <nav className="patch-files">
            <div className="patch-files__title">FILE BỊ ẢNH HƯỞNG</div>
            {patch.files.map((file) => (
              <button
                key={`${file.path}:${file.action}`}
                className={active?.path === file.path ? "is-active" : ""}
                onClick={() => setActivePath(file.path)}
              >
                <ActionBadge action={file.action} />
                <span title={file.destination ? `${file.path} → ${file.destination}` : file.path}>
                  {file.path}
                  {file.destination && <small>→ {file.destination}</small>}
                </span>
              </button>
            ))}
          </nav>
          <div className="diff-view">
            {active ? (
              <Editor
                language="diff"
                value={active.diff}
                theme="vs-dark"
                options={{
                  readOnly: true,
                  automaticLayout: true,
                  minimap: { enabled: false },
                  lineNumbers: "off",
                  folding: false,
                  fontSize: 12,
                  renderLineHighlight: "none",
                  scrollBeyondLastLine: false,
                }}
              />
            ) : (
              <p>Không có diff.</p>
            )}
          </div>
        </div>
        <footer className="modal-footer">
          <div className="safety-note">
            <AlertTriangle size={14} /> Backup được tạo ngay trước khi áp dụng.
          </div>
          <div>
            <button className="button-secondary" onClick={onClose} disabled={busy}>Đóng</button>
            {patch.status === "pending" && (
              <>
                <button className="button-danger-quiet" onClick={onReject} disabled={busy}>
                  <XCircle size={15} /> Từ chối
                </button>
                <button className="button-primary" onClick={onApply} disabled={busy}>
                  <Check size={16} /> {busy ? "Đang áp dụng…" : "Xác nhận áp dụng"}
                </button>
              </>
            )}
            {patch.status === "applied" && (
              <button
                className={undoConfirm ? "button-danger" : "button-primary"}
                onClick={() => {
                  if (undoConfirm) onUndo();
                  else setUndoConfirm(true);
                }}
                disabled={busy}
              >
                <RotateCcw size={15} />
                {busy ? "Đang hoàn tác…" : undoConfirm ? "Xác nhận hoàn tác" : "Hoàn tác thay đổi"}
              </button>
            )}
          </div>
        </footer>
      </section>
    </div>
  );
}

function PatchStatus({ status }: { status: PatchProposal["status"] }) {
  const value = {
    pending: { label: "Đang chờ xác nhận", icon: <Clock3 size={12} /> },
    applied: { label: "Đã áp dụng", icon: <CheckCircle2 size={12} /> },
    undone: { label: "Đã hoàn tác", icon: <RotateCcw size={12} /> },
    rejected: { label: "Đã từ chối", icon: <XCircle size={12} /> },
  }[status];
  return <span className={`patch-status patch-status--${status}`}>{value.icon}{value.label}</span>;
}

function ActionBadge({ action }: { action: PatchProposal["files"][number]["action"] }) {
  const text = {
    update: "M",
    create: "A",
    delete: "D",
    rename: "R",
    move: "V",
    mkdir: "+",
    "rename-directory": "R",
  }[action];
  return <b className={`action-badge action-badge--${action}`}>{text}</b>;
}
