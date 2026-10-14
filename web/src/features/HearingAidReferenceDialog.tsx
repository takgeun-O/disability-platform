"use client";

import { useLayoutEffect, useRef, type ReactNode } from "react";
import { useNavigationGuard } from "@/components/NavigationGuard";
import { focusForReading } from "@/lib/reading-focus";

/** P03 read-only guidance: keep the survey mounted, inert and at its original scroll. */
export default function HearingAidReferenceDialog({
  open, titleId, returnFocusId, onClose, onSectionNavigate, children,
}: {
  open: boolean;
  titleId: string;
  returnFocusId?: string;
  onClose: () => void;
  onSectionNavigate: (id: string) => void;
  children: ReactNode;
}) {
  const { getApprovalVersion } = useNavigationGuard();
  const dialogRef = useRef<HTMLDialogElement>(null);

  useLayoutEffect(() => {
    if (!open) return;
    const dialog = dialogRef.current!;
    const navigationVersion = getApprovalVersion();
    const previous = (returnFocusId ? document.getElementById(returnFocusId) : document.activeElement) as HTMLElement | null;
    const scroll = {x: window.scrollX, y: window.scrollY};
    const body = document.body;
    const root = document.documentElement;
    const keys = ["position", "top", "left", "width", "paddingRight"] as const;
    const saved = Object.fromEntries(keys.map(key => [key, body.style[key]]));
    const overflow = root.style.overflow;
    const gutter = window.innerWidth - root.clientWidth;
    const padding = getComputedStyle(body).paddingRight;
    // Fixed positioning also locks the page on touch devices. Preserve existing
    // inline styles and scrollbar space; do not overwrite unrelated body styles.
    root.style.overflow = "hidden";
    Object.assign(body.style, {
      position: "fixed", top: `${-scroll.y}px`, left: `${-scroll.x}px`, width: "100%",
      paddingRight: `${parseFloat(padding) + gutter}px`,
    });
    dialog.showModal();
    return () => {
      const keyboard = !!dialog.querySelector(":focus-visible");
      dialog.close();
      for (const key of keys) body.style[key] = saved[key];
      root.style.overflow = overflow;
      // A page/history transition owns its own restoration. Only closing this
      // dialog on the current screen restores the survey control and position.
      if (navigationVersion !== getApprovalVersion()) return;
      const target = previous?.isConnected && previous !== body
        ? previous : document.getElementById("p03-guided-heading");
      if (target) focusForReading(target, keyboard);
      window.scrollTo({left: scroll.x, top: scroll.y, behavior: "instant"});
    };
  }, [open, returnFocusId, getApprovalVersion]);

  return (
    <dialog
      ref={dialogRef}
      className="p03-reference-dialog"
      aria-labelledby={titleId}
      onCancel={event => { event.preventDefault(); onClose(); }}
      onKeyDown={event => {
        if (event.key !== "Tab") return;
        const controls = Array.from(event.currentTarget.querySelectorAll<HTMLElement>(
          'a[href],button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),summary,[tabindex]:not([tabindex="-1"])',
        )).filter(el => el.tabIndex >= 0 && el.getClientRects().length > 0);
        const first = controls[0], last = controls.at(-1);
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault(); last?.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault(); first?.focus();
        }
      }}
      onClick={event => {
        const link = (event.target as HTMLElement).closest<HTMLAnchorElement>('a[href^="#"]');
        if (!link || event.defaultPrevented || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
        const id = link.getAttribute("href")!.slice(1);
        if (!id || !event.currentTarget.contains(document.getElementById(id))) return;
        // The reference TOC scrolls this dialog, without adding history entries
        // or moving the background survey via a document-wide hash jump.
        event.preventDefault();
        onSectionNavigate(id);
      }}
    >
      <header className="p03-dialog-bar">
        <p>보청기 지원 안내</p>
        <button type="button" aria-label="안내 닫기" onClick={onClose}>
          <span aria-hidden="true">×</span> 닫기
        </button>
      </header>
      <div className="p03-dialog-body">{children}</div>
    </dialog>
  );
}
