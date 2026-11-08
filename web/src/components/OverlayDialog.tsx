"use client";

import { useLayoutEffect, useRef, type ReactNode } from "react";
import { useNavigationGuard } from "./NavigationGuard";

/** Native modal/inert behaviour; one form, one focus scope, one scroll lock. */
export default function OverlayDialog({ children, titleId, descriptionId, className, onClose, initialFocus, returnFocus, mobileOnly = false, closeOnBackdropClick = true, closeOnNavigation = true, fallbackFocus }: {
  children: ReactNode; titleId: string; descriptionId?: string; className: string; onClose: () => void;
  initialFocus?: string; returnFocus?: string; mobileOnly?: boolean; closeOnBackdropClick?: boolean; closeOnNavigation?: boolean; fallbackFocus?: string;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const closeRef = useRef(onClose);
  const { subscribeNavigation } = useNavigationGuard();
  useLayoutEffect(() => { closeRef.current = onClose; });
  useLayoutEffect(() => {
    const dialog = dialogRef.current!;
    const previous = returnFocus ? document.querySelector<HTMLElement>(returnFocus) : document.activeElement as HTMLElement | null;
    const body = document.body, root = document.documentElement;
    const scroll = { x: window.scrollX, y: window.scrollY };
    const keys = ["position", "top", "left", "width", "paddingRight"] as const;
    const saved = Object.fromEntries(keys.map(key => [key, body.style[key]]));
    const overflow = root.style.overflow;
    const gutter = window.innerWidth - root.clientWidth;
    const padding = parseFloat(getComputedStyle(body).paddingRight);
    root.style.overflow = "hidden";
    Object.assign(body.style, { position: "fixed", top: `${-scroll.y}px`, left: `${-scroll.x}px`, width: "100%", paddingRight: `${padding + gutter}px` });
    let released = false;
    function release(restoreFocus: boolean) {
      if (released) return;
      released = true;
      dialog.close();
      for (const key of keys) body.style[key] = saved[key];
      root.style.overflow = overflow;
      // Release synchronously BEFORE an approved route/history action runs.
      window.scrollTo({ left: scroll.x, top: scroll.y, behavior: "instant" });
      if (restoreFocus) { const destination = previous?.isConnected && previous.getClientRects().length ? previous : fallbackFocus ? document.querySelector<HTMLElement>(fallbackFocus) : null; destination?.focus({ preventScroll: true }); }
    }
    function viewport() {
      const view = window.visualViewport;
      dialog.style.setProperty("--overlay-height", `${view?.height ?? window.innerHeight}px`);
      dialog.style.setProperty("--overlay-top", `${view?.offsetTop ?? 0}px`);
    }
    viewport();
    dialog.showModal();
    dialog.querySelector<HTMLElement>(initialFocus ?? "[data-initial-focus]")?.focus({ preventScroll: true });
    const unsubscribe = subscribeNavigation(() => { release(false); if (closeOnNavigation) closeRef.current(); });
    const media = window.matchMedia("(min-width: 901px)");
    const resize = () => { if (mobileOnly && media.matches) { release(false); closeRef.current(); } };
    media.addEventListener("change", resize);
    window.visualViewport?.addEventListener("resize", viewport);
    window.visualViewport?.addEventListener("scroll", viewport);
    return () => {
      unsubscribe(); media.removeEventListener("change", resize);
      window.visualViewport?.removeEventListener("resize", viewport);
      window.visualViewport?.removeEventListener("scroll", viewport);
      release(true);
    };
  }, [subscribeNavigation, initialFocus, returnFocus, mobileOnly, closeOnNavigation, fallbackFocus]);
  return <dialog ref={dialogRef} className={`iyum-overlay ${className}`} aria-labelledby={titleId} aria-describedby={descriptionId}
    onCancel={event => { event.preventDefault(); onClose(); }}
    onClick={event => {
      if (!closeOnBackdropClick || event.target !== event.currentTarget) return;
      const box = event.currentTarget.getBoundingClientRect();
      if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) onClose();
    }}
    onKeyDown={event => {
      if (event.key !== "Tab") return;
      const controls = Array.from(event.currentTarget.querySelectorAll<HTMLElement>(
        'a[href],button:not(:disabled),input:not(:disabled),textarea:not(:disabled),select:not(:disabled),summary,[tabindex]'
      )).filter(el => el.tabIndex >= 0 && el.getClientRects().length > 0);
      const first = controls[0], last = controls.at(-1);
      if (!first) { event.preventDefault(); return; }
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    }}>{children}</dialog>;
}
