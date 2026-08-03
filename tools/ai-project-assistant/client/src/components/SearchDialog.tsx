import { CaseSensitive, FileSearch, LoaderCircle, Regex, Search, X } from "lucide-react";
import { useState } from "react";
import type { SearchMode, SearchResult } from "@smartkid/shared";

export function SearchDialog({
  open,
  loading,
  results,
  onClose,
  onSearch,
  onOpen,
}: {
  open: boolean;
  loading: boolean;
  results: SearchResult[];
  onClose: () => void;
  onSearch: (query: string, mode: SearchMode, caseSensitive: boolean) => void;
  onOpen: (path: string, line: number) => void;
}) {
  const [query, setQuery] = useState("");
  const [mode, setMode] = useState<SearchMode>("text");
  const [caseSensitive, setCaseSensitive] = useState(false);

  if (!open) return null;
  return (
    <div className="modal-backdrop search-backdrop">
      <section className="modal search-dialog" role="dialog" aria-modal="true">
        <header className="modal-header">
          <div><span className="modal-icon"><FileSearch size={19} /></span><div><h2>Tìm trong dự án</h2><p>Exact, regex, reference, class, function và Android ID</p></div></div>
          <button onClick={onClose}><X size={19} /></button>
        </header>
        <form
          className="global-search-form"
          onSubmit={(event) => {
            event.preventDefault();
            if (query.trim()) onSearch(query, mode, caseSensitive);
          }}
        >
          <Search size={17} />
          <input autoFocus value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Nội dung cần tìm…" />
          <button type="button" className={caseSensitive ? "is-active" : ""} onClick={() => setCaseSensitive((value) => !value)} title="Phân biệt hoa thường"><CaseSensitive size={18} /></button>
          <button type="button" className={mode === "regex" ? "is-active" : ""} onClick={() => setMode(mode === "regex" ? "text" : "regex")} title="Regex"><Regex size={17} /></button>
          <button type="submit" className="search-submit">Tìm</button>
        </form>
        <div className="search-modes">
          {(["text", "exact", "file", "path", "class", "function", "android-id", "regex"] as SearchMode[]).map((value) => (
            <button key={value} className={mode === value ? "is-active" : ""} onClick={() => setMode(value)}>{modeLabel(value)}</button>
          ))}
        </div>
        <div className="search-results">
          {loading ? (
            <div className="state-center"><LoaderCircle className="spin" /> Đang tìm kiếm…</div>
          ) : results.length ? (
            <>
              <div className="result-count">{results.length} kết quả</div>
              {results.map((result, index) => (
                <button key={`${result.path}:${result.line}:${index}`} onClick={() => { onOpen(result.path, result.line); onClose(); }}>
                  <div><strong>{result.path}</strong><span>L{result.line}:{result.column}</span></div>
                  <code>{highlightPreview(result.preview, result.match)}</code>
                </button>
              ))}
            </>
          ) : (
            <div className="state-center"><Search size={28} /><p>Nhập từ khóa và nhấn Enter để tìm.</p></div>
          )}
        </div>
      </section>
    </div>
  );
}

function modeLabel(mode: SearchMode) {
  return { text: "Nội dung", exact: "Exact", file: "Tên file", path: "Đường dẫn", class: "Class", function: "Function", "android-id": "Android ID", regex: "Regex" }[mode];
}

function highlightPreview(preview: string, _match: string) {
  return preview;
}
