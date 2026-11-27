let audioContext: AudioContext | null = null;

export function playIncomingCallSound(): void {
  try {
    const Context = window.AudioContext;
    if (!Context) {
      return;
    }
    audioContext ??= new Context();
    const context = audioContext;
    if (context.state === "suspended") {
      void context.resume();
    }

    const now = context.currentTime;
    [0, 0.45, 0.9].forEach((offset, index) => {
      const oscillator = context.createOscillator();
      const gain = context.createGain();
      const start = now + offset;
      oscillator.type = "sine";
      oscillator.frequency.setValueAtTime(index % 2 === 0 ? 880 : 660, start);
      gain.gain.setValueAtTime(0.0001, start);
      gain.gain.exponentialRampToValueAtTime(0.15, start + 0.03);
      gain.gain.exponentialRampToValueAtTime(0.0001, start + 0.35);
      oscillator.connect(gain).connect(context.destination);
      oscillator.start(start);
      oscillator.stop(start + 0.36);
    });
  } catch {
    
  }
}
