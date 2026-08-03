import { AlertTriangle, Boxes, ExternalLink, Link2, Tags, X } from "lucide-react";
import type { XmlAnalysis } from "@smartkid/shared";

export function XmlInspector({
  analysis,
  onClose,
  onOpen,
}: {
  analysis: XmlAnalysis;
  onClose: () => void;
  onOpen: (path: string, line: number) => void;
}) {
  return (
    <aside className="xml-inspector">
      <div className="inspector-title">
        <span>
          <Tags size={16} /> Android XML Inspector
        </span>
        <button onClick={onClose} aria-label="Đóng XML Inspector">
          <X size={15} />
        </button>
      </div>
      {analysis.warnings.length > 0 && (
        <div className="xml-warning">
          <AlertTriangle size={15} />
          <div>{analysis.warnings.map((warning) => <p key={warning}>{warning}</p>)}</div>
        </div>
      )}
      <InspectorSection icon={<Boxes size={14} />} title={`Views (${analysis.views.length})`}>
        {analysis.views.map((view, index) => (
          <div className="inspector-row" key={`${view.tag}-${view.line}-${index}`}>
            <div>
              <strong>{shortTag(view.tag)}</strong>
              <small>{view.id ? `@id/${view.id}` : "không có ID"}</small>
            </div>
            <span>L{view.line}</span>
          </div>
        ))}
      </InspectorSection>
      <InspectorSection icon={<Link2 size={14} />} title={`Resources (${analysis.resources.length})`}>
        {analysis.resources.map((resource) => (
          <div className="inspector-row" key={`${resource.type}/${resource.name}`}>
            <div>
              <strong className={resource.exists ? "" : "resource-missing"}>
                @{resource.type}/{resource.name}
              </strong>
              <small>{resource.occurrences} reference</small>
            </div>
            <span className={resource.exists ? "resource-ok" : "resource-bad"}>
              {resource.exists ? "✓" : "!"}
            </span>
          </div>
        ))}
      </InspectorSection>
      <InspectorSection icon={<ExternalLink size={14} />} title={`Được inflate bởi (${analysis.inflaters.length})`}>
        {analysis.inflaters.length ? (
          analysis.inflaters.map((item) => (
            <button
              className="inspector-link"
              key={`${item.path}:${item.line}`}
              onClick={() => onOpen(item.path, item.line)}
            >
              <span>{item.path.split("/").at(-1)}</span>
              <small>L{item.line}</small>
            </button>
          ))
        ) : (
          <p className="inspector-empty">Chưa tìm thấy Activity, Fragment hoặc Dialog inflate layout này.</p>
        )}
      </InspectorSection>
    </aside>
  );
}

function InspectorSection({
  icon,
  title,
  children,
}: {
  icon: React.ReactNode;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <section className="inspector-section">
      <h3>{icon} {title}</h3>
      <div>{children}</div>
    </section>
  );
}

function shortTag(tag: string) {
  return tag.split(".").at(-1) ?? tag;
}
