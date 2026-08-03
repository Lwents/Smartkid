import {
  AlertTriangle,
  AudioLines,
  BookOpen,
  BrainCircuit,
  CheckCircle2,
  CircleStop,
  GraduationCap,
  History,
  Lightbulb,
  Mic,
  MicOff,
  RefreshCcw,
  RotateCcw,
  Send,
  Sparkles,
  Square,
  Target,
  Trophy,
  Volume2,
  X,
} from "lucide-react";
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent,
  type ReactNode,
} from "react";
import { api, errorMessage } from "../api";
import { useSpeechRecognition } from "../hooks/useSpeechRecognition";
import { useSpeechSynthesis } from "../hooks/useSpeechSynthesis";

const LEVELS = ["Tiểu học", "THCS", "THPT", "Đại học", "Tự do"] as const;
const QUESTION_COUNTS = [5, 8, 10, 15] as const;
const SUBJECTS = [
  "Toán",
  "Ngữ văn",
  "Tiếng Việt",
  "Tiếng Anh",
  "Vật lý",
  "Hóa học",
  "Sinh học",
  "Lịch sử",
  "Địa lý",
  "Tin học",
];

type AudienceLevel = (typeof LEVELS)[number];
type QuestionCount = (typeof QUESTION_COUNTS)[number];

type SessionConfig = {
  studentName: string;
  level: AudienceLevel;
  subject: string;
  topic: string;
  learningGoal: string;
  questionCount: QuestionCount;
};

type TurnAssessment = {
  correctness: number;
  reasoning: number;
  clarity: number;
  confidence: "low" | "medium" | "high";
  misconception?: string;
};

type OralTurn = {
  id: string;
  phase: string;
  teacherMessage: string;
  question: string;
  studentAnswer?: string;
  feedback?: string;
  assessment?: TurnAssessment;
  createdAt: string;
  answeredAt?: string;
};

type SessionSummary = {
  overallScore: number;
  correctness: number;
  reasoning: number;
  clarity: number;
  strengths: string[];
  improvements: string[];
  teacherClosing: string;
};

type OralSession = {
  id: string;
  config: SessionConfig;
  status: "active" | "completed";
  currentPhase: string;
  turns: OralTurn[];
  summary?: SessionSummary;
  createdAt: string;
  updatedAt: string;
};

type OralPublicConfig = {
  aiConfigured: boolean;
  demoMode: boolean;
  model: string;
};

type SetupErrors = Partial<Record<keyof SessionConfig, string>>;

function initialConfig(): SessionConfig {
  let studentName = "";
  try {
    studentName = window.localStorage.getItem("smartkid-oral-student") || "";
  } catch {
    // Tên chỉ là tiện ích; form vẫn hoạt động khi localStorage bị chặn.
  }
  return {
    studentName,
    level: "THCS",
    subject: "Toán",
    topic: "",
    learningGoal: "",
    questionCount: 5,
  };
}

function teacherSpeech(turn?: OralTurn): string {
  if (!turn) return "";
  const introduction = turn.teacherMessage.trim();
  const question = turn.question.trim();
  if (!introduction || introduction.includes(question)) return introduction || question;
  return `${introduction} ${question}`;
}

function score(value = 0): number {
  return Math.max(0, Math.min(100, Math.round(value)));
}

function phaseLabel(phase: string): string {
  return {
    warmup: "Khởi động",
    diagnose: "Kiểm tra nền tảng",
    probe: "Đào sâu lập luận",
    challenge: "Thử thách",
    reflect: "Tự tổng kết",
    complete: "Hoàn thành",
  }[phase] || "Vấn đáp";
}

export function OralPane({ active }: { active: boolean }) {
  const [config, setConfig] = useState<SessionConfig>(initialConfig);
  const [publicConfig, setPublicConfig] = useState<OralPublicConfig>();
  const [errors, setErrors] = useState<SetupErrors>({});
  const [session, setSession] = useState<OralSession>();
  const [history, setHistory] = useState<OralSession[]>([]);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [answer, setAnswer] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [speechError, setSpeechError] = useState("");
  const [showTranscript, setShowTranscript] = useState(false);
  const [showVoice, setShowVoice] = useState(false);
  const lastSpokenTurn = useRef("");
  const submitLock = useRef(false);
  const answerRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    const controller = new AbortController();
    void api<OralPublicConfig>("/oral/config", { signal: controller.signal })
      .then(setPublicConfig)
      .catch(() => {
        // Badge là thông tin phụ; lỗi API chính vẫn hiện khi bắt đầu phiên.
      });
    return () => controller.abort();
  }, []);

  const answeredTurns = useMemo(
    () => session?.turns.filter((turn) => Boolean(turn.studentAnswer)) || [],
    [session],
  );
  const currentTurn = useMemo(
    () =>
      session?.turns.find((turn) => !turn.studentAnswer) ||
      session?.turns.at(-1),
    [session],
  );
  const feedbackTurn = [...answeredTurns]
    .reverse()
    .find((turn) => turn.feedback || turn.assessment);
  const progress = session
    ? Math.min(100, Math.round((answeredTurns.length / session.config.questionCount) * 100))
    : 0;

  const {
    supported: synthesisSupported,
    voices,
    selectedVoice,
    preferences,
    isSpeaking,
    updatePreferences,
    speak,
    stop: stopSpeaking,
  } = useSpeechSynthesis();

  const appendSpeech = useCallback((transcript: string) => {
    setAnswer((current) =>
      `${current}${current.trim() ? " " : ""}${transcript}`.trim(),
    );
    setSpeechError("");
  }, []);
  const onSpeechError = useCallback((message: string) => setSpeechError(message), []);
  const {
    isSupported: recognitionSupported,
    isListening,
    interimTranscript,
    toggle: toggleListening,
    abort: abortListening,
  } = useSpeechRecognition({
    onFinalTranscript: appendSpeech,
    onError: onSpeechError,
  });

  useEffect(() => {
    if (!active) {
      abortListening();
      stopSpeaking();
      return;
    }
    if (
      !busy &&
      session?.status === "active" &&
      currentTurn &&
      currentTurn.id !== lastSpokenTurn.current
    ) {
      lastSpokenTurn.current = currentTurn.id;
      speak(teacherSpeech(currentTurn));
      window.setTimeout(() => answerRef.current?.focus(), 180);
    }
  }, [abortListening, active, busy, currentTurn, session?.status, speak, stopSpeaking]);

  useEffect(() => {
    if (busy) {
      abortListening();
      stopSpeaking();
    }
  }, [abortListening, busy, stopSpeaking]);

  function updateConfig<K extends keyof SessionConfig>(
    key: K,
    value: SessionConfig[K],
  ) {
    setConfig((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: undefined }));
  }

  function validateConfig(): SetupErrors {
    const next: SetupErrors = {};
    if (config.studentName.trim().length < 2) {
      next.studentName = "Tên cần có ít nhất 2 ký tự.";
    }
    if (!config.subject.trim()) next.subject = "Hãy chọn môn học.";
    if (config.topic.trim().length < 2) next.topic = "Hãy nhập chủ đề muốn luyện.";
    if (config.learningGoal.trim().length < 4) {
      next.learningGoal = "Hãy mô tả mục tiêu rõ hơn một chút.";
    }
    return next;
  }

  async function startSession(event: FormEvent) {
    event.preventDefault();
    const nextErrors = validateConfig();
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    setBusy(true);
    setNotice("");
    setSpeechError("");
    try {
      const payload = {
        ...config,
        studentName: config.studentName.trim(),
        subject: config.subject.trim(),
        topic: config.topic.trim(),
        learningGoal: config.learningGoal.trim(),
      };
      const created = await api<OralSession>("/oral/sessions", {
        method: "POST",
        body: payload,
      });
      try {
        window.localStorage.setItem("smartkid-oral-student", payload.studentName);
      } catch {
        // Không ảnh hưởng phiên học.
      }
      lastSpokenTurn.current = "";
      setSession(created);
      setAnswer("");
      setHistory((current) => [
        created,
        ...current.filter((item) => item.id !== created.id),
      ]);
    } catch (error) {
      setNotice(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  const submitAnswer = useCallback(
    async (value: string) => {
      const normalized = value.trim();
      if (!session || !normalized || busy || submitLock.current) return;
      submitLock.current = true;
      setBusy(true);
      setNotice("");
      setSpeechError("");
      abortListening();
      stopSpeaking();
      try {
        const updated = await api<OralSession>(
          `/oral/sessions/${encodeURIComponent(session.id)}/answers`,
          { method: "POST", body: { answer: normalized } },
        );
        setSession(updated);
        setAnswer("");
        setHistory((current) => [
          updated,
          ...current.filter((item) => item.id !== updated.id),
        ]);
      } catch (error) {
        setNotice(errorMessage(error));
      } finally {
        setBusy(false);
        submitLock.current = false;
      }
    },
    [abortListening, busy, session, stopSpeaking],
  );

  async function endSession() {
    if (!session || busy) return;
    if (!window.confirm("Kết thúc và tổng kết phiên vấn đáp này?")) return;
    setBusy(true);
    setNotice("");
    abortListening();
    stopSpeaking();
    try {
      const completed = await api<OralSession>(
        `/oral/sessions/${encodeURIComponent(session.id)}/end`,
        { method: "POST" },
      );
      setSession(completed);
      setHistory((current) => [
        completed,
        ...current.filter((item) => item.id !== completed.id),
      ]);
    } catch (error) {
      setNotice(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  async function openHistory() {
    setHistoryOpen(true);
    setHistoryLoading(true);
    setNotice("");
    try {
      setHistory(await api<OralSession[]>("/oral/sessions"));
    } catch (error) {
      setNotice(errorMessage(error));
    } finally {
      setHistoryLoading(false);
    }
  }

  async function selectHistory(id: string) {
    setHistoryLoading(true);
    try {
      const selected = await api<OralSession>(
        `/oral/sessions/${encodeURIComponent(id)}`,
      );
      setSession(selected);
      setHistoryOpen(false);
      setAnswer("");
      lastSpokenTurn.current = "";
    } catch (error) {
      setNotice(errorMessage(error));
    } finally {
      setHistoryLoading(false);
    }
  }

  function newSession() {
    abortListening();
    stopSpeaking();
    setSession(undefined);
    setAnswer("");
    setNotice("");
    setSpeechError("");
    lastSpokenTurn.current = "";
  }

  function repeatQuestion() {
    const text = teacherSpeech(currentTurn);
    if (text) {
      abortListening();
      speak(text, true);
    }
  }

  if (!session) {
    return (
      <div className="oral-pane" aria-label="Thiết lập AI vấn đáp">
        <div className="oral-setup-scroll">
          <div className="oral-intro">
            <span className="oral-teacher-orb"><GraduationCap size={22} /></span>
            <div>
              <span>
                NGƯỜI THẦY AI
                {publicConfig && (
                  <i className={publicConfig.aiConfigured ? "is-live" : "is-demo"}>
                    {publicConfig.aiConfigured ? "AI thật" : "Demo"}
                  </i>
                )}
              </span>
              <h3>Thầy hỏi, em tự tìm lời giải.</h3>
              <p>Mỗi lượt một câu, gợi mở vừa đủ và nhận xét cụ thể.</p>
            </div>
          </div>

          {notice && <OralNotice message={notice} />}

          <form className="oral-setup-form" onSubmit={(event) => void startSession(event)}>
            <label className="oral-field">
              <span>Tên của em</span>
              <div className="oral-input-icon">
                <GraduationCap size={14} />
                <input
                  value={config.studentName}
                  onChange={(event) => updateConfig("studentName", event.target.value)}
                  placeholder="Ví dụ: Minh Anh"
                  autoComplete="name"
                />
              </div>
              {errors.studentName && <small>{errors.studentName}</small>}
            </label>

            <div className="oral-field-row">
              <label className="oral-field">
                <span>Cấp học</span>
                <select
                  value={config.level}
                  onChange={(event) =>
                    updateConfig("level", event.target.value as AudienceLevel)
                  }
                >
                  {LEVELS.map((level) => <option key={level}>{level}</option>)}
                </select>
              </label>
              <label className="oral-field">
                <span>Số câu</span>
                <select
                  value={config.questionCount}
                  onChange={(event) =>
                    updateConfig("questionCount", Number(event.target.value) as QuestionCount)
                  }
                >
                  {QUESTION_COUNTS.map((count) => (
                    <option key={count} value={count}>{count} câu</option>
                  ))}
                </select>
              </label>
            </div>

            <label className="oral-field">
              <span>Môn học</span>
              <div className="oral-input-icon">
                <BookOpen size={14} />
                <input
                  list="oral-subjects"
                  value={config.subject}
                  onChange={(event) => updateConfig("subject", event.target.value)}
                  placeholder="Chọn hoặc nhập môn học"
                />
                <datalist id="oral-subjects">
                  {SUBJECTS.map((subject) => <option key={subject} value={subject} />)}
                </datalist>
              </div>
              {errors.subject && <small>{errors.subject}</small>}
            </label>

            <label className="oral-field">
              <span>Chủ đề muốn luyện</span>
              <div className="oral-input-icon">
                <BrainCircuit size={14} />
                <input
                  value={config.topic}
                  onChange={(event) => updateConfig("topic", event.target.value)}
                  placeholder="Ví dụ: Phân số, vòng lặp…"
                />
              </div>
              {errors.topic && <small>{errors.topic}</small>}
            </label>

            <label className="oral-field">
              <span>Mục tiêu buổi học</span>
              <div className="oral-input-icon oral-input-icon--textarea">
                <Target size={14} />
                <textarea
                  rows={2}
                  value={config.learningGoal}
                  onChange={(event) => updateConfig("learningGoal", event.target.value)}
                  placeholder="Em muốn hiểu hoặc làm được điều gì?"
                />
              </div>
              {errors.learningGoal && <small>{errors.learningGoal}</small>}
            </label>

            <button className="oral-start-button" type="submit" disabled={busy}>
              {busy ? <><i className="oral-spinner" /> Thầy đang chuẩn bị…</> : (
                <><Sparkles size={15} /> Bắt đầu vấn đáp</>
              )}
            </button>
          </form>

          <button className="oral-history-link" type="button" onClick={() => void openHistory()}>
            <History size={13} /> Xem lịch sử phiên học
          </button>
          <p className="oral-privacy">
            <Volume2 size={12} />
            Dùng giọng tổng hợp tiếng Việt của thiết bị, không sao chép giọng người thật.
          </p>
        </div>
        {historyOpen && (
          <OralHistory
            sessions={history}
            loading={historyLoading}
            onClose={() => setHistoryOpen(false)}
            onSelect={(id) => void selectHistory(id)}
          />
        )}
      </div>
    );
  }

  if (session.status === "completed") {
    const summary = session.summary;
    return (
      <div className="oral-pane">
        <div className="oral-summary">
          <span className="oral-summary-trophy"><Trophy size={28} /></span>
          <span className="oral-kicker">HOÀN THÀNH PHIÊN VẤN ĐÁP</span>
          <h3>Làm tốt lắm, {session.config.studentName.split(/\s+/).at(-1)}!</h3>
          <p>{summary?.teacherClosing || "Em đã kiên trì suy nghĩ và hoàn thành buổi học."}</p>

          <div className="oral-score-main">
            <strong>{score(summary?.overallScore)}</strong>
            <span>/ 100</span>
          </div>
          <div className="oral-score-grid">
            <Score label="Kiến thức" value={summary?.correctness} />
            <Score label="Lập luận" value={summary?.reasoning} />
            <Score label="Diễn đạt" value={summary?.clarity} />
          </div>

          <SummaryList
            title="Điểm em làm tốt"
            icon={<CheckCircle2 size={13} />}
            items={summary?.strengths || []}
            tone="good"
          />
          <SummaryList
            title="Điều nên luyện thêm"
            icon={<Target size={13} />}
            items={summary?.improvements || []}
            tone="focus"
          />

          <div className="oral-summary-actions">
            <button type="button" onClick={newSession}>
              <RefreshCcw size={14} /> Phiên mới
            </button>
            <button type="button" onClick={() => void openHistory()}>
              <History size={14} /> Lịch sử
            </button>
          </div>
        </div>
        {historyOpen && (
          <OralHistory
            sessions={history}
            loading={historyLoading}
            onClose={() => setHistoryOpen(false)}
            onSelect={(id) => void selectHistory(id)}
          />
        )}
      </div>
    );
  }

  return (
    <div className="oral-pane">
      <div className="oral-session-toolbar">
        <div>
          <span>{session.config.subject}</span>
          <strong title={session.config.topic}>{session.config.topic}</strong>
        </div>
        <button type="button" title="Lịch sử" onClick={() => void openHistory()}>
          <History size={14} />
        </button>
        <button type="button" title="Phiên mới" onClick={newSession}>
          <RefreshCcw size={14} />
        </button>
        <button className="oral-end" type="button" title="Kết thúc" onClick={() => void endSession()}>
          <CircleStop size={14} />
        </button>
      </div>

      <div className="oral-progress-head">
        <span>CÂU {Math.min(answeredTurns.length + 1, session.config.questionCount)}</span>
        <strong>{phaseLabel(session.currentPhase)}</strong>
        <small>{answeredTurns.length}/{session.config.questionCount}</small>
      </div>
      <div
        className="oral-progress"
        role="progressbar"
        aria-label={`Tiến độ ${progress}%`}
        aria-valuenow={progress}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <span style={{ width: `${progress}%` }} />
      </div>

      <div className="oral-dialogue">
        <article className={`oral-teacher-card ${isSpeaking ? "is-speaking" : ""}`}>
          <div className="oral-teacher-identity">
            <span className="oral-teacher-orb">
              <AudioLines size={20} />
              {isSpeaking && <i />}
            </span>
            <div>
              <span>THẦY AI</span>
              <small>{isSpeaking ? "Đang nói với em…" : "Em cứ nghĩ thật kỹ nhé"}</small>
            </div>
          </div>
          {currentTurn?.teacherMessage && <p>{currentTurn.teacherMessage}</p>}
          <h3>{currentTurn?.question || "Thầy đang chuẩn bị câu hỏi…"}</h3>
          <div className="oral-voice-actions">
            <button type="button" onClick={repeatQuestion} disabled={!synthesisSupported}>
              <RotateCcw size={12} /> Nghe lại
            </button>
            <button type="button" onClick={stopSpeaking} disabled={!isSpeaking}>
              <Square size={10} /> Dừng đọc
            </button>
            <button
              type="button"
              className={showVoice ? "is-active" : ""}
              onClick={() => setShowVoice((value) => !value)}
            >
              <Volume2 size={12} /> Giọng
            </button>
          </div>
          {showVoice && (
            <div className="oral-voice-settings">
              <label>
                <input
                  type="checkbox"
                  checked={preferences.enabled}
                  onChange={(event) => updatePreferences({ enabled: event.target.checked })}
                />
                Tự đọc câu mới
              </label>
              <select
                value={selectedVoice?.voiceURI || ""}
                onChange={(event) => updatePreferences({ voiceURI: event.target.value })}
                disabled={!voices.length}
                aria-label="Giọng đọc"
              >
                {voices.map((voice) => (
                  <option key={voice.voiceURI} value={voice.voiceURI}>
                    {voice.name} · {voice.lang}
                  </option>
                ))}
              </select>
              <label>
                Tốc độ {preferences.rate.toFixed(2)}
                <input
                  type="range"
                  min="0.7"
                  max="1.25"
                  step="0.05"
                  value={preferences.rate}
                  onChange={(event) => updatePreferences({ rate: Number(event.target.value) })}
                />
              </label>
            </div>
          )}
        </article>

        {feedbackTurn && (
          <article className="oral-feedback">
            <span><Sparkles size={12} /> NHẬN XÉT CỦA THẦY</span>
            {feedbackTurn.feedback && <p>{feedbackTurn.feedback}</p>}
            {feedbackTurn.assessment && (
              <div className="oral-mini-scores">
                <Score label="Đúng" value={feedbackTurn.assessment.correctness} />
                <Score label="Lý luận" value={feedbackTurn.assessment.reasoning} />
                <Score label="Rõ ràng" value={feedbackTurn.assessment.clarity} />
              </div>
            )}
            {feedbackTurn.assessment?.misconception && (
              <small><AlertTriangle size={11} /> {feedbackTurn.assessment.misconception}</small>
            )}
          </article>
        )}

        {(notice || speechError) && <OralNotice message={notice || speechError} />}

        <div className={`oral-state ${isListening ? "is-listening" : ""}`}>
          {busy ? (
            <><i className="oral-spinner" /> Thầy đang suy nghĩ về câu trả lời…</>
          ) : isListening ? (
            <><span className="oral-listening-dot" /> Thầy đang lắng nghe…</>
          ) : (
            <><Mic size={12} /> Đến lượt em trả lời</>
          )}
        </div>

        <form
          className="oral-answer"
          onSubmit={(event) => {
            event.preventDefault();
            void submitAnswer(answer);
          }}
        >
          <textarea
            ref={answerRef}
            value={answer}
            onChange={(event) => setAnswer(event.target.value)}
            placeholder="Nói hoặc nhập cách em đang suy nghĩ…"
            disabled={busy}
            onKeyDown={(event) => {
              if ((event.ctrlKey || event.metaKey) && event.key === "Enter") {
                event.preventDefault();
                void submitAnswer(answer);
              }
            }}
          />
          {interimTranscript && <p className="oral-interim">“{interimTranscript}”</p>}
          <div>
            <button
              className={`oral-mic ${isListening ? "is-listening" : ""}`}
              type="button"
              onClick={() => {
                if (isSpeaking) stopSpeaking();
                toggleListening();
              }}
              disabled={!recognitionSupported || busy}
              title={recognitionSupported ? "Bật hoặc tắt micro" : "Trình duyệt không hỗ trợ micro"}
            >
              {isListening ? <MicOff size={15} /> : <Mic size={15} />}
            </button>
            <button
              className="oral-hint"
              type="button"
              onClick={() => void submitAnswer("Em chưa biết. Thầy gợi ý cho em với ạ.")}
              disabled={busy}
            >
              <Lightbulb size={13} /> Em chưa biết, thầy gợi ý
            </button>
            <button className="oral-send" type="submit" disabled={!answer.trim() || busy}>
              <Send size={14} />
            </button>
          </div>
        </form>

        {answeredTurns.length > 0 && (
          <div className="oral-transcript">
            <button type="button" onClick={() => setShowTranscript((value) => !value)}>
              <span>Hội thoại đã qua ({answeredTurns.length})</span>
              <small>{showTranscript ? "Thu gọn" : "Xem lại"}</small>
            </button>
            {showTranscript && answeredTurns.map((turn, index) => (
              <article key={turn.id}>
                <span>Câu {index + 1}</span>
                <strong>{turn.question}</strong>
                <p><b>Em:</b> {turn.studentAnswer}</p>
                {turn.feedback && <p><b>Thầy:</b> {turn.feedback}</p>}
              </article>
            ))}
          </div>
        )}
      </div>

      {historyOpen && (
        <OralHistory
          sessions={history}
          loading={historyLoading}
          onClose={() => setHistoryOpen(false)}
          onSelect={(id) => void selectHistory(id)}
        />
      )}
    </div>
  );
}

function Score({ label, value }: { label: string; value?: number }) {
  const normalized = score(value);
  return (
    <div className="oral-score">
      <span>{label}</span>
      <strong>{normalized}</strong>
      <i><i style={{ width: `${normalized}%` }} /></i>
    </div>
  );
}

function SummaryList({
  title,
  icon,
  items,
  tone,
}: {
  title: string;
  icon: ReactNode;
  items: string[];
  tone: "good" | "focus";
}) {
  if (!items.length) return null;
  return (
    <section className={`oral-summary-list oral-summary-list--${tone}`}>
      <h4>{icon} {title}</h4>
      {items.map((item) => <p key={item}>{item}</p>)}
    </section>
  );
}

function OralNotice({ message }: { message: string }) {
  return (
    <div className="oral-notice" role="alert">
      <AlertTriangle size={13} />
      <span>{message}</span>
    </div>
  );
}

function OralHistory({
  sessions,
  loading,
  onClose,
  onSelect,
}: {
  sessions: OralSession[];
  loading: boolean;
  onClose: () => void;
  onSelect: (id: string) => void;
}) {
  return (
    <div className="oral-history">
      <div className="oral-history-head">
        <div><History size={14} /><strong>Lịch sử vấn đáp</strong></div>
        <button type="button" onClick={onClose} aria-label="Đóng lịch sử"><X size={14} /></button>
      </div>
      <div className="oral-history-list">
        {loading && <p><i className="oral-spinner" /> Đang tải lịch sử…</p>}
        {!loading && sessions.length === 0 && (
          <p>Chưa có phiên học nào. Em bắt đầu một phiên nhé.</p>
        )}
        {!loading && sessions.map((item) => {
          const answered = item.turns.filter((turn) => turn.studentAnswer).length;
          return (
            <button type="button" key={item.id} onClick={() => onSelect(item.id)}>
              <span className={item.status === "completed" ? "is-completed" : ""}>
                {item.status === "completed" ? <CheckCircle2 size={12} /> : <BrainCircuit size={12} />}
              </span>
              <div>
                <strong>{item.config.subject} · {item.config.topic}</strong>
                <small>{item.config.studentName} · {answered}/{item.config.questionCount} câu</small>
              </div>
              <time>{new Date(item.updatedAt).toLocaleDateString("vi-VN")}</time>
            </button>
          );
        })}
      </div>
    </div>
  );
}
