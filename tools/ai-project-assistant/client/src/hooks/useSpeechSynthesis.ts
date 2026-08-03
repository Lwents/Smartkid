import { useCallback, useEffect, useMemo, useState } from "react";

type VoicePreferences = {
  enabled: boolean;
  rate: number;
  voiceURI: string;
};

const STORAGE_KEY = "smartkid-oral-voice";
const DEFAULT_PREFERENCES: VoicePreferences = {
  enabled: true,
  rate: 0.94,
  voiceURI: "",
};

function loadPreferences(): VoicePreferences {
  try {
    const saved = window.localStorage.getItem(STORAGE_KEY);
    if (!saved) return DEFAULT_PREFERENCES;
    const parsed = JSON.parse(saved) as Partial<VoicePreferences>;
    return {
      enabled: typeof parsed.enabled === "boolean" ? parsed.enabled : true,
      rate:
        typeof parsed.rate === "number" && parsed.rate >= 0.7 && parsed.rate <= 1.3
          ? parsed.rate
          : DEFAULT_PREFERENCES.rate,
      voiceURI: typeof parsed.voiceURI === "string" ? parsed.voiceURI : "",
    };
  } catch {
    return DEFAULT_PREFERENCES;
  }
}

export function useSpeechSynthesis() {
  const supported =
    typeof window !== "undefined" && "speechSynthesis" in window;
  const [preferences, setPreferences] = useState<VoicePreferences>(loadPreferences);
  const [voices, setVoices] = useState<SpeechSynthesisVoice[]>([]);
  const [isSpeaking, setIsSpeaking] = useState(false);

  useEffect(() => {
    if (!supported) return;
    const loadVoices = () => setVoices(window.speechSynthesis.getVoices());
    loadVoices();
    window.speechSynthesis.addEventListener("voiceschanged", loadVoices);
    return () => {
      window.speechSynthesis.removeEventListener("voiceschanged", loadVoices);
      window.speechSynthesis.cancel();
    };
  }, [supported]);

  const availableVoices = useMemo(() => {
    const vietnamese = voices.filter((voice) =>
      voice.lang.toLowerCase().startsWith("vi"),
    );
    return vietnamese.length > 0 ? vietnamese : voices;
  }, [voices]);

  const selectedVoice = useMemo(
    () =>
      voices.find((voice) => voice.voiceURI === preferences.voiceURI) ||
      availableVoices[0] ||
      null,
    [availableVoices, preferences.voiceURI, voices],
  );

  const updatePreferences = useCallback((patch: Partial<VoicePreferences>) => {
    setPreferences((current) => {
      const next = { ...current, ...patch };
      try {
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
      } catch {
        // Trình duyệt có thể chặn localStorage; cài đặt vẫn dùng trong phiên hiện tại.
      }
      return next;
    });
  }, []);

  const stop = useCallback(() => {
    if (!supported) return;
    window.speechSynthesis.cancel();
    setIsSpeaking(false);
  }, [supported]);

  const speak = useCallback(
    (text: string, force = false) => {
      if (!supported || !text.trim() || (!preferences.enabled && !force)) return;
      window.speechSynthesis.cancel();
      const utterance = new SpeechSynthesisUtterance(text);
      utterance.lang = selectedVoice?.lang || "vi-VN";
      utterance.rate = preferences.rate;
      utterance.pitch = 1;
      if (selectedVoice) utterance.voice = selectedVoice;
      utterance.onstart = () => setIsSpeaking(true);
      utterance.onend = () => setIsSpeaking(false);
      utterance.onerror = () => setIsSpeaking(false);
      window.speechSynthesis.speak(utterance);
    },
    [preferences.enabled, preferences.rate, selectedVoice, supported],
  );

  return {
    supported,
    voices: availableVoices,
    selectedVoice,
    preferences,
    isSpeaking,
    updatePreferences,
    speak,
    stop,
  };
}
