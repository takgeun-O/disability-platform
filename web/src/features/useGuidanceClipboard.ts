"use client";

import { useEffect, useLayoutEffect, useRef, useState, type MouseEvent } from "react";
import { useNavigationRequest } from "@/components/NavigationGuard";
import { revealForReading } from "@/lib/reading-focus";

// Both guidance copy buttons use the same failure focus lifecycle. No delayed
// timer may focus a closed dialog, a former route, or a user's new control.
export function useGuidanceClipboard(copyText: string) {
  const [status, setStatus] = useState<"idle" | "success" | "fail">("idle");
  const [showFallback, setShowFallback] = useState(false);
  const [fallbackAttempt, setFallbackAttempt] = useState(0);
  const fallbackRef = useRef<HTMLTextAreaElement>(null);
  const pendingFocus = useRef<(() => boolean) | null>(null);
  const listeners = useRef<AbortController | null>(null);
  const scope = useNavigationRequest();
  useEffect(() => () => listeners.current?.abort(), []);

  useLayoutEffect(() => {
    const canFocus = pendingFocus.current;
    pendingFocus.current = null;
    if (!canFocus?.() || !fallbackRef.current) return;
    fallbackRef.current.focus({preventScroll: true});
    fallbackRef.current.select();
    revealForReading(fallbackRef.current);
  }, [showFallback, status, fallbackAttempt]);

  async function handleCopy(event: MouseEvent<HTMLButtonElement>) {
    const button = event.currentTarget;
    const focusOwner = document.activeElement;
    const dialog = button.closest('dialog');
    const isCurrent = scope.begin();
    listeners.current?.abort();
    const controller = new AbortController();
    listeners.current = controller;
    let moved = false, closed = false;
    const movedAway = () => { moved = true; };
    const options = {capture: true, signal: controller.signal};
    document.addEventListener('pointerdown', movedAway, options);
    document.addEventListener('keydown', movedAway, options);
    document.addEventListener('focusin', movedAway, options);
    dialog?.addEventListener('close', () => { closed = true; }, {signal: controller.signal});
    const valid = () => {
      const activeDialog = document.querySelector('dialog[open]');
      return isCurrent() && !closed && button.isConnected && button.getClientRects().length > 0
        && (!dialog || dialog.open) && (!activeDialog || activeDialog.contains(button));
    };
    pendingFocus.current = null;
    setStatus('idle');
    try {
      await navigator.clipboard.writeText(copyText);
      if (!valid()) return;
      setStatus('success'); setShowFallback(false);
    } catch {
      if (!valid()) return;
      pendingFocus.current = () => valid() && !moved && document.activeElement === focusOwner;
      setFallbackAttempt(attempt => attempt + 1);
      setStatus('fail'); setShowFallback(true);
    } finally {
      controller.abort();
    }
  }

  return {status, showFallback, fallbackRef, handleCopy};
}
