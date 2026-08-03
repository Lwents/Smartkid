import { useCallback, useEffect, useRef, useState } from "react";

type SpeechRecognitionOptions = {
  onFinalTranscript: (transcript: string) => void;
  onError?: (message: string) => void;
};

const ERROR_MESSAGES: Record<string, string> = {
  "not-allowed": "Trình duyệt chưa được cấp quyền dùng micro.",
  "service-not-allowed": "Dịch vụ nhận dạng giọng nói đang bị chặn.",
  "audio-capture": "Không tìm thấy micro để thu âm.",
  network: "Nhận dạng giọng nói đang mất kết nối.",
  "no-speech": "Thầy chưa nghe rõ. Em thử nói lại nhé.",
};

export function useSpeechRecognition({
  onFinalTranscript,
  onError,
}: SpeechRecognitionOptions) {
  const recognitionRef = useRef<SpeechRecognition | null>(null);
  const [isListening, setIsListening] = useState(false);
  const [interimTranscript, setInterimTranscript] = useState("");
  const isSupported =
    typeof window !== "undefined" &&
    Boolean(window.SpeechRecognition || window.webkitSpeechRecognition);

  useEffect(() => {
    if (!isSupported) return;
    const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!Recognition) return;

    const recognition = new Recognition();
    recognition.lang = "vi-VN";
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.maxAlternatives = 1;
    recognition.onstart = () => setIsListening(true);
    recognition.onend = () => {
      setIsListening(false);
      setInterimTranscript("");
    };
    recognition.onerror = (event) => {
      setIsListening(false);
      setInterimTranscript("");
      if (event.error !== "aborted") {
        onError?.(
          ERROR_MESSAGES[event.error] ||
            `Không thể nhận dạng giọng nói (${event.error}).`,
        );
      }
    };
    recognition.onresult = (event) => {
      let finalText = "";
      let interimText = "";
      for (let index = event.resultIndex; index < event.results.length; index += 1) {
        const result = event.results[index];
        const transcript = result?.[0]?.transcript || "";
        if (result?.isFinal) finalText += transcript;
        else interimText += transcript;
      }
      setInterimTranscript(interimText.trim());
      if (finalText.trim()) onFinalTranscript(finalText.trim());
    };
    recognitionRef.current = recognition;

    return () => {
      recognition.abort();
      recognitionRef.current = null;
    };
  }, [isSupported, onError, onFinalTranscript]);

  const start = useCallback(() => {
    if (!recognitionRef.current || isListening) return;
    try {
      recognitionRef.current.start();
    } catch {
      onError?.("Micro đang khởi động. Em chờ một chút nhé.");
    }
  }, [isListening, onError]);

  const stop = useCallback(() => {
    recognitionRef.current?.stop();
  }, []);

  const abort = useCallback(() => {
    recognitionRef.current?.abort();
    setIsListening(false);
    setInterimTranscript("");
  }, []);

  const toggle = useCallback(() => {
    if (isListening) stop();
    else start();
  }, [isListening, start, stop]);

  return { isSupported, isListening, interimTranscript, start, stop, abort, toggle };
}
